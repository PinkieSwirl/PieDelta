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
import java.nio.file.Path
import kotlin.io.path.Path
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

    class Created(
        path: Path,
        oldHash: String = "",
        newHash: String,
        hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1,
    ) : IndexEntry(path, oldHash, newHash, CREATED, hashAlgorithm)

    class Deleted(
        path: Path,
        oldHash: String,
        newHash: String = "",
        hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1,
    ) :
        IndexEntry(path, oldHash, newHash, DELETED, hashAlgorithm)

    sealed class UnchangedOrUpdated(
        path: Path,
        oldHash: String,
        newHash: String,
        state: State,
        hashAlgorithm: HashAlgorithm,
    ) :
        IndexEntry(path, oldHash, newHash, state, hashAlgorithm)

    class Updated(path: Path, oldSha1: String, newSha1: String, hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1) :
        UnchangedOrUpdated(path, oldSha1, newSha1, UPDATED, hashAlgorithm)

    class Unchanged(path: Path, sha1: String, hashAlgorithm: HashAlgorithm = HashAlgorithm.SHA_1) :
        UnchangedOrUpdated(path, sha1, sha1, UNCHANGED, hashAlgorithm)

    companion object {
        fun UnchangedOrUpdated(path: Path, oldHash: String, newHash: String): UnchangedOrUpdated {
            return if (oldHash == newHash) Unchanged(path, oldHash) else Updated(path, oldHash, newHash)
        }
    }

    override fun toString(): String = "IndexEntry[path=$path, state=$state, oldSha1=$oldHash, newSha1=$newHash]"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IndexEntry

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
    SHA_1
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
        val surrogate =
            IndexEntrySurrogate(value.state, value.pathString, value.hashAlgorithm, value.oldHash, value.newHash)
        encoder.encodeSerializableValue(IndexEntrySurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): IndexEntry {
        val surrogate = decoder.decodeSerializableValue(IndexEntrySurrogate.serializer())
        return when (surrogate.state) {
            UNCHANGED -> Unchanged(Path(surrogate.path), surrogate.oldHash, surrogate.hashAlgorithm)
            CREATED -> Created(Path(surrogate.path), surrogate.oldHash, surrogate.newHash, surrogate.hashAlgorithm)
            UPDATED -> Updated(Path(surrogate.path), surrogate.oldHash, surrogate.newHash, surrogate.hashAlgorithm)
            DELETED -> Deleted(Path(surrogate.path), surrogate.oldHash, surrogate.newHash, surrogate.hashAlgorithm)
        }
    }
}
