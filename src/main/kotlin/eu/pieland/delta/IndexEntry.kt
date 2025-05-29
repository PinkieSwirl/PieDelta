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
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString

@Serializable(with = IndexEntrySerializer::class)
internal sealed class IndexEntry(
    val state: State,
    val path: String,
    val hashAlgorithm: HashAlgorithm,
    val oldHash: String,
    val newHash: String,
) {

    enum class State {
        UNCHANGED, CREATED, UPDATED, DELETED
    }

    class Created(path: String, hashAlgorithm: HashAlgorithm, newHash: String) :
        IndexEntry(CREATED, path, hashAlgorithm, "", newHash)

    class Deleted(path: String, hashAlgorithm: HashAlgorithm, oldHash: String) :
        IndexEntry(DELETED, path, hashAlgorithm, oldHash, "")

    sealed class UnchangedOrUpdated(
        path: String,
        hashAlgorithm: HashAlgorithm,
        oldHash: String,
        newHash: String,
        state: State,
    ) : IndexEntry(state, path, hashAlgorithm, oldHash, newHash)

    class Updated(path: String, hashAlgorithm: HashAlgorithm, oldHash: String, newHash: String) :
        UnchangedOrUpdated(path, hashAlgorithm, oldHash, newHash, UPDATED)

    class Unchanged(path: String, hashAlgorithm: HashAlgorithm, hash: String) :
        UnchangedOrUpdated(path, hashAlgorithm, hash, hash, UNCHANGED)

    companion object {
        fun Created(path: Path, resolvedPath: Path, hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1) =
            Created(path.invariantSeparatorsPathString, resolvedPath, hashAlgorithm)

        private fun Created(path: String, resolvedPath: Path, hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1) =
            Created(path, hashAlgorithm, with(hashAlgorithm) { resolvedPath.computeHash() })

        fun Deleted(path: Path, resolvedPath: Path, hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1) =
            Deleted(path.invariantSeparatorsPathString, resolvedPath, hashAlgorithm)

        private fun Deleted(path: String, resolvedPath: Path, hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1) =
            Deleted(path, hashAlgorithm, with(hashAlgorithm) { resolvedPath.computeHash() })

        fun UnchangedOrUpdated(
            path: Path,
            resolvedOldPath: Path,
            resolvedNewPath: Path,
            hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1,
        ): UnchangedOrUpdated {
            val oldHash = with(hashAlgorithm) { resolvedOldPath.computeHash() }
            val newHash = with(hashAlgorithm) { resolvedNewPath.computeHash() }
            return if (oldHash == newHash) Unchanged(path.invariantSeparatorsPathString, hashAlgorithm, oldHash)
            else Updated(path.invariantSeparatorsPathString, hashAlgorithm, oldHash, newHash)
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
        var result = hashAlgorithm.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + oldHash.hashCode()
        result = 31 * result + newHash.hashCode()
        return result
    }


}

enum class HashAlgorithm {
    SHA_1 {
        @OptIn(ExperimentalStdlibApi::class)
        override fun Path.computeHash(): String {
            val digest = MessageDigest.getInstance("SHA-1")
            inputStream().buffered().useAll { input, offset, length -> digest.update(input, offset, length) }
            return digest.digest().toHexString()
        }
    },
    CRC32 {
        @OptIn(ExperimentalStdlibApi::class)
        override fun Path.computeHash(): String {
            val crc32 = CRC32()
            inputStream().buffered().useAll { input, offset, length -> crc32.update(input, offset, length) }
            return crc32.value.toInt().toHexString()
        }
    },
    ;

    internal inline fun InputStream.useAll(
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        write: (ByteArray, Int, Int) -> Unit,
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

    abstract fun Path.computeHash(): String
}

@Serializable
@SerialName("IndexEntry")
@OptIn(ExperimentalSerializationApi::class)
private class IndexEntrySurrogate(
    val state: State,
    val path: String,
    val hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1,
    @JsonNames("oldSha1") val oldHash: String = "",
    @JsonNames("newSha1") val newHash: String = "",
)

internal object IndexEntrySerializer : KSerializer<IndexEntry> {
    override val descriptor: SerialDescriptor = IndexEntrySurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: IndexEntry) {
        val surrogate = IndexEntrySurrogate(value.state, value.path, value.hashAlgorithm, value.oldHash, value.newHash)
        encoder.encodeSerializableValue(IndexEntrySurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): IndexEntry {
        val surrogate = decoder.decodeSerializableValue(IndexEntrySurrogate.serializer())
        return when (surrogate.state) {
            UNCHANGED -> Unchanged(surrogate.path, surrogate.hashAlgorithm, surrogate.oldHash)
            CREATED -> Created(surrogate.path, surrogate.hashAlgorithm, surrogate.newHash)
            UPDATED -> Updated(surrogate.path, surrogate.hashAlgorithm, surrogate.oldHash, surrogate.newHash)
            DELETED -> Deleted(surrogate.path, surrogate.hashAlgorithm, surrogate.oldHash)
        }
    }
}
