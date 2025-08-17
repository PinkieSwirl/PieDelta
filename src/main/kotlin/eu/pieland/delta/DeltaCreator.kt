package eu.pieland.delta

import eu.pieland.delta.DeltaCreatorHashes.Companion.computeHashesFromByteSource
import eu.pieland.delta.IndexEntry.*
import eu.pieland.delta.IndexEntry.Companion.Created
import eu.pieland.delta.IndexEntry.Companion.Deleted
import eu.pieland.delta.IndexEntry.Companion.UnchangedOrUpdated
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*


internal class DeltaCreator @Suppress("LongParameterList") private constructor(
    private val source: Path,
    private val target: Path,
    private val patch: Path,
    private val created: List<Created>,
    private val deleted: List<Deleted>,
    private val updated: List<Updated>,
    private val unchanged: List<Unchanged>,
    private val chunkSize: Int,
    private val blockSize: Int,
) {

    fun create(): Path =
        patch.apply { parent.createDirectories() }.writeZip { writeIndex().writeCreated().writeUpdated() }

    private fun ZipOutputStream.writeIndex(): ZipOutputStream {
        putNextEntry(ZipEntry(".index_" + UUID.randomUUID().toString()))
        unchanged.forEach { writeIndexLine(it) }
        created.forEach { writeIndexLine(it) }
        updated.forEach { writeIndexLine(it) }
        deleted.forEach { writeIndexLine(it) }
        closeEntry()
        return this
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun ZipOutputStream.writeIndexLine(entry: IndexEntry) {
        Json.encodeToStream(entry, this)
        write('\n'.code)
    }

    private fun ZipOutputStream.writeCreated(): ZipOutputStream {
        created.forEach { createdEntry ->
            putNextEntry(ZipEntry(createdEntry.path))
            target.resolve(createdEntry.path).inputStream().buffered().use { it.copyTo(this) }
            closeEntry()
        }
        return this
    }

    private fun ZipOutputStream.writeUpdated(): Path {
        updated.forEach { updatedEntry ->
            putNextEntry(ZipEntry("${updatedEntry.path}.gdiff"))
            writeGDiff(updatedEntry)
            closeEntry()
        }
        return patch
    }

    private fun ZipOutputStream.writeGDiff(updatedEntry: Updated) {
        source.resolve(updatedEntry.path).inFileChannel().use { source ->
            target.resolve(updatedEntry.path).inFileChannel().use { target ->
                GDiffWriter(this).use { output ->
                    GDiffCreator(source, target, output, chunkSize, blockSize).create()
                }
            }
        }
    }

    private inline fun Path.writeZip(use: ZipOutputStream.() -> Path): Path =
        ZipOutputStream(outputStream().buffered()).use { it.use() }

    companion object {
        @JvmStatic
        @Suppress("LongParameterList")
        internal operator fun invoke(
            source: Path,
            target: Path,
            patch: Path,
            chunkSize: Int,
            blockSize: Int,
            pathFilter: (Path) -> Boolean,
        ): DeltaCreator {
            require(source.exists() && source.isDirectory()) { "'source' must be an existing directory" }
            require(target.exists() && target.isDirectory()) { "'target' must be an existing directory" }
            require(patch.notExists()) { "'patch' must not exist; parent directories may exist" }
            require(chunkSize > 0) { "'chunkSize' must be greater than 0" }
            require(blockSize > chunkSize) {
                "'blockSize' must be greater than 'chunkSize', optimally a multiple of it"
            }

            val sourceSet = source.relativePaths(pathFilter)
            val targetSet = target.relativePaths(pathFilter)

            val intersect = sourceSet.intersect(targetSet)
            val (updated, unchanged) = intersect.map { UnchangedOrUpdated(it, source.resolve(it), target.resolve(it)) }
                .partitionByType<Updated, Unchanged, UnchangedOrUpdated>()

            val created = targetSet.subtract(intersect).map { Created(it, target.resolve(it)) }
            val deleted = sourceSet.subtract(intersect).map { Deleted(it, resolvedPath = source.resolve(it)) }

            return DeltaCreator(
                source = source,
                target = target,
                patch = patch,
                created = created,
                deleted = deleted,
                updated = updated,
                unchanged = unchanged,
                chunkSize = chunkSize,
                blockSize = blockSize,
            )
        }

        private inline fun <reified F : T, reified S : T, T> Iterable<T>.partitionByType(): Pair<List<F>, List<S>> {
            val first = ArrayList<F>()
            val second = ArrayList<S>()
            forEach { element -> if (element is F) first.add(element) else second.add(element as S) }
            return Pair(first, second)
        }

        @OptIn(ExperimentalPathApi::class)
        private fun Path.relativePaths(filter: (Path) -> Boolean): Set<Path> {
            return walk().filter(filter).map { it.normalize().relativeTo(this) }.toSortedSet()
        }
    }
}

private class GDiffCreator(
    private val source: SeekableByteChannel,
    private val target: ReadableByteChannel,
    private val output: GDiffWriter,
    private val chunkSize: Int,
    blockSize: Int,
) {
    private val targetBuffer: ByteBuffer = ByteBuffer.allocate(blockSize).apply { limit(0) }
    private val sourceBuffer: ByteBuffer = ByteBuffer.allocate(blockSize)
    private val longestMatchFinder: LongestMatchFinder = LongestMatchFinder(source, target, targetBuffer, sourceBuffer)
    private val sourceHashes: DeltaCreatorHashes = source.computeHashesFromByteSource(chunkSize)
    private var targetHash: DeltaCreatorChecksum = DeltaCreatorChecksum()
    private var needsHashUpdate = true
    private var eof = false

    fun create() {
        while (!eof) {
            val possibleChecksumPositionInSource = tryFindTargetChecksumInSourceChecksums()
            if (possibleChecksumPositionInSource >= 0) {
                source.position(possibleChecksumPositionInSource)
                val matchLength = longestMatchFinder.find { eof = true }
                needsHashUpdate = matchLength > 0
                if (matchLength >= chunkSize) {
                    output.addCopy(possibleChecksumPositionInSource, matchLength)
                } else {
                    targetBuffer.movePositionToStartOfMatch(matchLength)
                    output.addData()
                }
            } else {
                output.addData()
            }
        }
    }

    private fun GDiffWriter.addData() {
        prepareAddData()
        if (eof) return
        val nextByte = targetBuffer.get()
        tryIncrementChecksum(nextByte)
        addData(nextByte)
    }

    private fun tryIncrementChecksum(it: Byte) {
        if (targetBuffer.remaining() < chunkSize) return
        targetHash = targetHash.increment(it, targetBuffer[targetBuffer.position() + chunkSize - 1], chunkSize)
    }

    private fun prepareAddData() {
        if (targetBuffer.remaining() > chunkSize) return
        target.fillBuffer(targetBuffer) { compact() }
        if (targetBuffer.hasRemaining()) return
        eof = true
    }

    private fun ByteBuffer.movePositionToStartOfMatch(matchLength: Int) {
        position(position() - matchLength)
    }

    private fun tryFindTargetChecksumInSourceChecksums(): Long {
        sourceBuffer.apply {
            clear()
            limit(0)
        }
        if (needsHashUpdate) {
            while (targetBuffer.remaining() < chunkSize) {
                val read = target.fillBuffer(targetBuffer) { compact() }
                if (read == -1) return -1
            }
            targetHash = targetBuffer.computeHash(chunkSize)
            needsHashUpdate = false
        }
        return sourceHashes.indexOf(targetHash)
    }
}

internal class LongestMatchFinder(
    private val source: SeekableByteChannel,
    private val target: ReadableByteChannel,
    private val targetBuffer: ByteBuffer,
    private val sourceBuffer: ByteBuffer,
) {
    inline fun find(reachedEof: () -> Unit): Int {
        var matchLength = 0
        while (ensureBuffersReadable(reachedEof) && isMatching() && matchLength < Int.MAX_VALUE) {
            matchLength++
        }
        return matchLength
    }

    private inline fun ensureBuffersReadable(reachedEof: () -> Unit): Boolean =
        ensureSourceBufferReadable() && ensureTargetBufferReadable(reachedEof)

    private fun ensureSourceBufferReadable(): Boolean {
        if (sourceBuffer.hasRemaining()) return true
        return source.fillBuffer(sourceBuffer) { clear() } != -1
    }

    private inline fun ensureTargetBufferReadable(reachedEof: () -> Unit): Boolean {
        if (targetBuffer.hasRemaining()) return true
        target.fillBuffer(targetBuffer) { compact() }
        return targetBuffer.hasRemaining().also { if (!it) reachedEof() }
    }

    private fun isMatching(): Boolean {
        if (sourceBuffer.get() == targetBuffer.get()) return true
        targetBuffer.position(targetBuffer.position() - 1)
        return false
    }
}

private inline fun ReadableByteChannel.fillBuffer(byteBuffer: ByteBuffer, setupBuffer: ByteBuffer.() -> Unit): Int {
    byteBuffer.setupBuffer()
    val read = read(byteBuffer)
    byteBuffer.flip()
    return read
}

/** GDIFF-Format as specified by https://www.w3.org/TR/NOTE-gdiff-19970901 */
private class GDiffWriter private constructor(private val output: DataOutputStream) : AutoCloseable {
    private val buffer: ByteArrayOutputStream = ByteArrayOutputStream()

    constructor(output: ZipOutputStream) : this(DataOutputStream(output))

    init {
        writeMagicBytesAndVersion()
    }

    @Suppress("MagicNumber") // Literally magic bytes
    private fun writeMagicBytesAndVersion() {
        output.writeByte(0xd1)
        output.writeByte(0xff)
        output.writeByte(0xd1)
        output.writeByte(0xff)
        output.writeByte(0x04)
    }

    fun addCopy(offset: Long, length: Int) {
        addDataFromBuffer()

        when {
            offset > Int.MAX_VALUE -> {
                output.writeByte(COPY_LONG_INT)
                output.writeLong(offset)
                output.writeInt(length)
            }

            offset > DATA_SHORT_MAX -> {
                when {
                    length > DATA_SHORT_MAX -> {
                        output.writeByte(COPY_INT_INT)
                        output.writeInt(offset.toInt())
                        output.writeInt(length)
                    }

                    length > DATA_BYTE_MAX -> {
                        output.writeByte(COPY_INT_USHORT)
                        output.writeInt(offset.toInt())
                        output.writeShort(length)
                    }

                    else -> {
                        output.writeByte(COPY_INT_UBYTE)
                        output.writeInt(offset.toInt())
                        output.writeByte(length)
                    }

                }
            }

            length > DATA_SHORT_MAX -> {
                output.writeByte(COPY_USHORT_INT)
                output.writeShort(offset.toInt())
                output.writeInt(length)
            }

            length > DATA_BYTE_MAX -> {
                output.writeByte(COPY_USHORT_USHORT)
                output.writeShort(offset.toInt())
                output.writeShort(length)
            }

            else -> {
                output.writeByte(COPY_USHORT_UBYTE)
                output.writeShort(offset.toInt())
                output.writeByte(length)
            }
        }
    }

    fun addData(b: Byte) {
        buffer.write(b.toInt())
        if (buffer.size() > DATA_SHORT_MAX) addDataFromBuffer()
    }

    fun flush() {
        addDataFromBuffer()
        output.flush()
    }

    private fun addDataFromBuffer() {
        val size = buffer.size()
        if (size == 0) return
        addDataHeader(size)
        buffer.writeTo(output)
        buffer.reset()
    }

    private fun addDataHeader(size: Int) {
        if (size <= DATA_MAX) {
            output.writeByte(size)
        } else if (size <= DATA_SHORT_MAX) {
            output.writeByte(DATA_USHORT)
            output.writeShort(size)
        } else {
            output.writeByte(DATA_INT)
            output.writeInt(size)
        }
    }

    override fun close() {
        flush()
        output.write(EOF.toInt())
        output.flush()
    }
}
