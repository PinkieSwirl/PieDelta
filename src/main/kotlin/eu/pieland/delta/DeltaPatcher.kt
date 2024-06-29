package eu.pieland.delta

import eu.pieland.delta.IndexEntry.*
import kotlinx.serialization.json.Json
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.io.path.*
import kotlin.math.min

class DeltaPatcher(private val zipPatch: ZipInputStream, private val target: Path) {
    private val created: List<Created>
    private val deleted: List<Deleted>
    private val updated: List<Updated>
    private val unchanged: List<Unchanged>

    init {
        val entry = requireNotNull(zipPatch.nextEntry) { "'zipPatch' must be a valid zip" }
        require(target.exists() && target.isDirectory()) { "'target' must be an existing directory" }
        require(entry.name.startsWith(".index")) {
            "Unexpected index file name: '${entry.name}', must start with '.index'"
        }
        unchanged = ArrayList<Unchanged>()
        created = ArrayList<Created>()
        updated = ArrayList<Updated>()
        deleted = ArrayList<Deleted>()
        zipPatch.bufferedReader().lineSequence().forEach { input ->
            when (val indexEntry = Json.decodeFromString<IndexEntry>(input)) {
                is Created -> created.add(indexEntry)
                is Deleted -> deleted.add(indexEntry)
                is Unchanged -> unchanged.add(indexEntry)
                is Updated -> updated.add(indexEntry)
            }
        }
    }

    fun patch(): Path {
        unchanged.check()
        created.create()
        updated.update()
        deleted.delete()
        return target
    }

    private fun List<Unchanged>.check() {
        forEach { unchangedEntry ->
            val path = target.resolve(unchangedEntry.path)
            check(path.exists() && path.isRegularFile()) {
                "UNCHANGED file does not exists as regular file: ${path.relativeTo(target)}"
            }
            val hash = with(unchangedEntry.hashAlgorithm) { path.computeHash() }
            check(hash == unchangedEntry.oldHash) {
                "UNCHANGED file-check failed for file: ${path.relativeTo(target)}"
            }
        }
    }

    private fun List<Created>.create() {
        forEach { createdEntry ->
            val entry = checkNotNull(zipPatch.nextEntry)
            check(createdEntry.path.invariantSeparatorsPathString == entry.name) {
                "Index and zip-stream un-synchronized, index: ${createdEntry.path}, zip-stream: ${entry.name}"
            }
            val path = target.resolve(createdEntry.path)
            check(path.notExists()) { "CREATED file already exists: ${path.relativeTo(target)}" }
            path.parent.createDirectories()
            val hash = path.computeSha1FromZipEntry()
            check(hash == createdEntry.newHash) { "CREATED file-check failed for file: ${path.relativeTo(target)}" }
            zipPatch.closeEntry()
        }
    }

    private fun List<Updated>.update() {
        forEach { updatedEntry ->
            val zipEntry = checkNotNull(zipPatch.nextEntry)
            check(updatedEntry.path.invariantSeparatorsPathString + ".gdiff" == zipEntry.name) {
                "Index and zip-stream un-synchronized, index: ${updatedEntry.path}, zip-stream: ${zipEntry.name}"
            }

            val path = target.resolve(updatedEntry.path)
            check(path.exists() && path.isRegularFile()) { "UPDATED file doesn't exist: ${path.relativeTo(target)}" }
            check(with(updatedEntry.hashAlgorithm) { path.computeHash() } == updatedEntry.oldHash) {
                "UPDATED file-check failed for old file: ${path.relativeTo(target)}"
            }

            val patched = target.resolve(updatedEntry.path.invariantSeparatorsPathString + UUID.randomUUID().toString())
            patch(path, patched)
            check(with(updatedEntry.hashAlgorithm) { patched.computeHash() } == updatedEntry.newHash) {
                "UPDATED file check failed for new file: $patched"
            }
            patched.moveTo(path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun List<Deleted>.delete() {
        forEach { deletedEntry ->
            val path = target.resolve(deletedEntry.path)
            check(path.exists() && path.isRegularFile()) { "DELETED file doesn't exist: ${path.relativeTo(target)}" }
            check(with(deletedEntry.hashAlgorithm) { path.computeHash() } == deletedEntry.oldHash) {
                "DELETED file-check failed for old file: ${path.relativeTo(target)}"
            }
            path.deleteExisting()
            var parent = path.parent
            while (parent != target && !Files.list(parent).findFirst().isPresent) {
                parent.deleteExisting()
                parent = parent.parent
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun Path.computeSha1FromZipEntry(): String {
        val md = MessageDigest.getInstance("SHA-1")
        val output = DigestOutputStream(outputStream().buffered(), md)
        output.use { zipPatch.copyTo(it) }
        return output.messageDigest.digest().toHexString()
    }

    private fun patch(file: Path, patched: Path) {
        file.inFileChannel().use { source ->
            patched.outputStream().buffered().use { patchedOut ->
                GDiffPatcher(source, patchedOut, zipPatch).patch()
            }
        }
    }
}

/** GDIFF-Format as specified by https://www.w3.org/TR/NOTE-gdiff-19970901 */
private class GDiffPatcher private constructor(
    private val source: SeekableByteChannel,
    private val patchedOut: DataOutputStream,
    private val deltaIn: DataInputStream,
    val bufferSize: Int = 1024
) {
    constructor(source: SeekableByteChannel, patchedOut: OutputStream, deltaIn: InputStream) : this(
        source,
        DataOutputStream(patchedOut),
        DataInputStream(deltaIn),
    )

    private val buffer: ByteBuffer = ByteBuffer.allocate(bufferSize)
    private val backingArray: ByteArray = buffer.array()


    init {
        @Suppress("MagicNumber") // Literally magic numbers
        require(
            deltaIn.readUnsignedByte() == 0xd1 &&
                    deltaIn.readUnsignedByte() == 0xff &&
                    deltaIn.readUnsignedByte() == 0xd1 &&
                    deltaIn.readUnsignedByte() == 0xff &&
                    deltaIn.readUnsignedByte() == 0x04
        ) { "Unexpected magic bytes or version" }
    }

    @Throws(IOException::class)
    fun patch() {
        var command = deltaIn.readUnsignedByte()
        while (command != EOF.toInt()) {
            when (command) {
                DATA_USHORT -> append(deltaIn.readUnsignedShort())
                DATA_INT -> append(deltaIn.readInt())
                COPY_USHORT_UBYTE -> copy(deltaIn.readByteOffset(), deltaIn.readUnsignedByte())
                COPY_USHORT_USHORT -> copy(deltaIn.readByteOffset(), deltaIn.readUnsignedShort())
                COPY_USHORT_INT -> copy(deltaIn.readByteOffset(), deltaIn.readInt())
                COPY_INT_UBYTE -> copy(deltaIn.readIntOffset(), deltaIn.readUnsignedByte())
                COPY_INT_USHORT -> copy(deltaIn.readIntOffset(), deltaIn.readUnsignedShort())
                COPY_INT_INT -> copy(deltaIn.readIntOffset(), deltaIn.readInt())
                COPY_LONG_INT -> copy(deltaIn.readLong(), deltaIn.readInt())
                else -> append(command)
            }
            command = deltaIn.readUnsignedByte()
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun DataInputStream.readByteOffset() = readUnsignedShort().toLong()

    @Suppress("NOTHING_TO_INLINE")
    private inline fun DataInputStream.readIntOffset() = readInt().toLong()

    @Throws(IOException::class)
    private fun append(length: Int) {
        var remainingLength = length
        while (remainingLength > 0) {
            val count = deltaIn.read(backingArray, 0, min(bufferSize, remainingLength))
            if (count == -1) throw EOFException("Cannot read length (remaining/expected): $remainingLength/$length")
            patchedOut.write(backingArray, 0, count)
            remainingLength -= count
        }
    }

    @Throws(IOException::class)
    private fun copy(offset: Long, length: Int) {
        source.position(offset)
        var remainingLength = length
        while (remainingLength > 0) {
            buffer.rewind().limit(min(bufferSize, remainingLength))
            val count = source.read(buffer)
            if (count == -1) throw EOFException("in copy $offset $remainingLength")
            patchedOut.write(backingArray, 0, count)
            remainingLength -= count
        }
    }
}
