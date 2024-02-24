package eu.pieland.delta

import eu.pieland.delta.IndexEntry.*
import eu.pieland.delta.IndexEntry.State.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString

@Serializable(with = IndexEntrySerializer::class)
internal sealed class IndexEntry(
    val path: Path,
    val oldHash: String,
    val newHash: String,
    private val state: State,
    val pathString: String = path.invariantSeparatorsPathString
) {
    enum class State {
        UNCHANGED, CREATED, UPDATED, DELETED
    }

    class Created(path: Path, oldHash: String = "", newHash: String) : IndexEntry(path, oldHash, newHash, CREATED)
    class Deleted(path: Path, oldHash: String, newHash: String = "") : IndexEntry(path, oldHash, newHash, DELETED)
    sealed class UnchangedOrUpdated(path: Path, oldHash: String, newHash: String, state: State) :
        IndexEntry(path, oldHash, newHash, state)

    class Updated(path: Path, oldSha1: String, newSha1: String) : UnchangedOrUpdated(path, oldSha1, newSha1, UPDATED)
    class Unchanged(path: Path, sha1: String) : UnchangedOrUpdated(path, sha1, sha1, UNCHANGED)

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

@Serializable
@SerialName("IndexEntry")
private class IndexEntrySurrogate(val state: State, val path: String, val oldSha1: String, val newSha1: String)

internal object IndexEntrySerializer : KSerializer<IndexEntry> {
    override val descriptor: SerialDescriptor = IndexEntrySurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: IndexEntry) {
        val state = when (value) {
            is Created -> CREATED
            is Deleted -> DELETED
            is Unchanged -> UNCHANGED
            is Updated -> UPDATED
        }
        val surrogate =
            IndexEntrySurrogate(state, value.path.invariantSeparatorsPathString, value.oldHash, value.newHash)
        encoder.encodeSerializableValue(IndexEntrySurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): IndexEntry {
        val surrogate = decoder.decodeSerializableValue(IndexEntrySurrogate.serializer())
        return when (surrogate.state) {
            UNCHANGED -> Unchanged(Path(surrogate.path), surrogate.oldSha1)
            CREATED -> Created(Path(surrogate.path), surrogate.oldSha1, surrogate.newSha1)
            UPDATED -> Updated(Path(surrogate.path), surrogate.oldSha1, surrogate.newSha1)
            DELETED -> Deleted(Path(surrogate.path), surrogate.oldSha1, surrogate.newSha1)
        }
    }
}
