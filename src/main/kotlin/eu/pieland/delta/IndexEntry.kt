package eu.pieland.delta

import eu.pieland.delta.IndexEntry.*
import eu.pieland.delta.IndexEntry.State.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonNames
import java.io.InputStream
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString

@Serializable(with = IndexEntrySerializer::class)
internal sealed class IndexEntry(
    val path: Path,
    val oldHash: String,
    val newHash: String,
    internal val state: State,
    internal val hashAlgorithm: HashAlgorithm,
) {
    val pathString: String = path.invariantSeparatorsPathString

    enum class State {
        UNCHANGED, CREATED, UPDATED, DELETED
    }

    class Created(path: Path, hashAlgorithm: HashAlgorithm, oldHash: String = "", newHash: String) :
        IndexEntry(path, oldHash, newHash, CREATED, hashAlgorithm) {
        constructor(path: Path, resolvedPath: Path, hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1) : this(
            path,
            hashAlgorithm,
            newHash = with(hashAlgorithm) { resolvedPath.computeHash() })
    }

    class Deleted(path: Path, hashAlgorithm: HashAlgorithm, oldHash: String, newHash: String = "") :
        IndexEntry(path, oldHash, newHash, DELETED, hashAlgorithm) {
        constructor(path: Path, resolvedPath: Path, hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1) : this(
            path,
            hashAlgorithm,
            oldHash = with(hashAlgorithm) { resolvedPath.computeHash() })
    }

    sealed class UnchangedOrUpdated(
        path: Path,
        hashAlgorithm: HashAlgorithm,
        oldHash: String,
        newHash: String,
        state: State,
    ) :
        IndexEntry(path, oldHash, newHash, state, hashAlgorithm)

    class Updated(path: Path, hashAlgorithm: HashAlgorithm, oldHash: String, newHash: String) :
        UnchangedOrUpdated(path, hashAlgorithm, oldHash, newHash, UPDATED)

    class Unchanged(path: Path, hashAlgorithm: HashAlgorithm, hash: String) :
        UnchangedOrUpdated(path, hashAlgorithm, hash, hash, UNCHANGED)

    companion object {
        fun UnchangedOrUpdated(
            path: Path,
            resolvedOldPath: Path,
            resolvedNewPath: Path,
            hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1,
        ): UnchangedOrUpdated {
            val oldHash = with(hashAlgorithm) { resolvedOldPath.computeHash() }
            val newHash = with(hashAlgorithm) { resolvedNewPath.computeHash() }
            return if (oldHash == newHash) Unchanged(path, hashAlgorithm, oldHash)
            else Updated(path, hashAlgorithm, oldHash, newHash)
        }
    }

    override fun toString(): String = "IndexEntry[path=$path, state=$state, oldHash=$oldHash, newHash=$newHash]"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IndexEntry

        if (hashAlgorithm != other.hashAlgorithm) return false
        if (path != other.path) return false
        if (oldHash != other.oldHash) return false
        return newHash == other.newHash
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + oldHash.hashCode()
        result = 31 * result + newHash.hashCode()
        result = 31 * result + state.hashCode()
        return result
    }
}

public enum class HashAlgorithm {
    SHA_1 {
        @OptIn(ExperimentalStdlibApi::class)
        override fun Path.computeHash(): String {
            val digestStream = DigestInputStream(inputStream().buffered(), MessageDigest.getInstance("SHA-1"))
            digestStream.useAll()
            return digestStream.messageDigest.digest().toHexString()
        }
    },
    CRC32 {
        @OptIn(ExperimentalStdlibApi::class)
        override fun Path.computeHash(): String {
            val crc32 = CRC32()
            inputStream().buffered().useAll { b, off, len -> crc32.update(b, off, len) }
            return crc32.value.toInt().toHexString()
        }
    },
    ;

    protected fun InputStream.useAll(
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        write: (ByteArray, Int, Int) -> Unit = { _, _, _ -> },
    ) {
        use {
            val buffer = ByteArray(bufferSize)
            var bytes = read(buffer)
            while (bytes >= 0) {
                write(buffer, 0, bytes)
                bytes = read(buffer)
            }
        }
    }

    public abstract fun Path.computeHash(): String
}

@Serializable
@SerialName("IndexEntry")
@OptIn(ExperimentalSerializationApi::class)
private class IndexEntrySurrogate(
    val state: State,
    val path: String,
    val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1,
    @JsonNames("oldSha1") val oldHash: String = "",
    @JsonNames("newSha1") val newHash: String = "",)

internal object IndexEntrySerializer : KSerializer<IndexEntry> {
    override val descriptor: SerialDescriptor = IndexEntrySurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: IndexEntry) {
        val surrogate =
            IndexEntrySurrogate(value.state, value.pathString, value.hashAlgorithm, value.oldHash, value.newHash)
        encoder.encodeSerializableValue(IndexEntrySurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): IndexEntry {
        val surrogate = decoder.decodeSerializableValue(IndexEntrySurrogate.serializer())
        return when (surrogate.state) {
            UNCHANGED -> Unchanged(Path(surrogate.path), surrogate.hashAlgorithm, surrogate.oldHash)
            CREATED -> Created(Path(surrogate.path), surrogate.hashAlgorithm, surrogate.oldHash, surrogate.newHash)
            UPDATED -> Updated(Path(surrogate.path), surrogate.hashAlgorithm, surrogate.oldHash, surrogate.newHash)
            DELETED -> Deleted(Path(surrogate.path), surrogate.hashAlgorithm, surrogate.oldHash, surrogate.newHash)
        }
    }
}
