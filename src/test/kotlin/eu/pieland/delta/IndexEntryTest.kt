package eu.pieland.delta

import eu.pieland.delta.HashAlgorithm.CRC32
import eu.pieland.delta.HashAlgorithm.SHA_1
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.toPath
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

private val pathA = IndexEntryTest::class.java.getResource("/diff.zip")!!.toURI().toPath()
private val pathB = IndexEntryTest::class.java.getResource("/diff-corrupted-index.zip")!!.toURI().toPath()
private val fileName = pathA.parent.fileName

private const val SHA_1_PATH_A = "0d27a10f1819e7e2a84801e1e7f24a0ddc07eb7f"
private const val CRC32_PATH_A = "9f695a8b"
private const val SHA_1_PATH_B = "9e111d09767a612d410cd01762b68d785726d6fa"
private const val CRC32_PATH_B = "f8141cfb"

private fun created(alg: HashAlgorithm, newHash: String): List<String> {
    return if (alg == SHA_1) listOf(
        """{"state":"CREATED","path":"test","newHash":"$newHash"}""",
        """{"state":"CREATED","path":"test","newSha1":"$newHash"}""",
        """{"state":"CREATED","path":"test","oldHash":"","newHash":"$newHash"}""",
        """{"state":"CREATED","path":"test","oldHash":"","newHash":"$newHash","hashAlgorithm":"SHA_1"}""",
    )
    else listOf(
        """{"state":"CREATED","path":"test","hashAlgorithm":"$alg","newHash":"$newHash"}""",
        """{"state":"CREATED","hashAlgorithm":"$alg","path":"test","newSha1":"$newHash"}""",
        """{"hashAlgorithm":"$alg","state":"CREATED","path":"test","oldHash":"","newHash":"$newHash"}""",
        """{"state":"CREATED","path":"test","oldHash":"","newHash":"$newHash","hashAlgorithm":"$alg"}""",
    )
}

private fun createdConstructors(hashAlgorithm: HashAlgorithm, newHash: String) = listOf(
    { IndexEntry.Created(fileName, pathA, hashAlgorithm) },
    { IndexEntry.Created(fileName.invariantSeparatorsPathString, hashAlgorithm, newHash = newHash) },
)

private fun deleted(alg: HashAlgorithm, oldHash: String): List<String> {
    return if (alg == SHA_1) listOf(
        """{"state":"DELETED","path":"test","oldHash":"$oldHash"}""",
        """{"state":"DELETED","path":"test","oldSha1":"$oldHash"}""",
        """{"state":"DELETED","path":"test","newHash":"","oldHash":"$oldHash"}""",
        """{"state":"DELETED","path":"test","newHash":"","oldHash":"$oldHash","hashAlgorithm":"SHA_1"}""",
    )
    else listOf(
        """{"state":"DELETED","path":"test","hashAlgorithm":"$alg","oldHash":"$oldHash"}""",
        """{"state":"DELETED","path":"test","oldSha1":"$oldHash","hashAlgorithm":"$alg"}""",
        """{"state":"DELETED","path":"test","newHash":"","oldHash":"$oldHash","hashAlgorithm":"$alg"}""",
    )
}

private fun deletedConstructors(hashAlgorithm: HashAlgorithm, newHash: String) = listOf(
    { IndexEntry.Deleted(fileName, pathA, hashAlgorithm) },
    { IndexEntry.Deleted(fileName.invariantSeparatorsPathString, hashAlgorithm, oldHash = newHash) },
)

private fun updated(alg: HashAlgorithm, oldHash: String, newHash: String): List<String> {
    return if (alg == SHA_1) listOf(
        """{"state":"UPDATED","path":"test","oldHash":"$oldHash","newHash":"$newHash"}""",
        """{"state":"UPDATED","path":"test","oldSha1":"$oldHash","newSha1":"$newHash"}""",
        """{"state":"UPDATED","path":"test","oldHash":"$oldHash","newHash":"$newHash","hashAlgorithm":"SHA_1"}""",
    )
    else listOf(
        """{"state":"UPDATED","path":"test","hashAlgorithm":"$alg","oldHash":"$oldHash","newHash":"$newHash"}""",
        """{"state":"UPDATED","hashAlgorithm":"$alg","path":"test","oldSha1":"$oldHash","newSha1":"$newHash"}""",
        """{"state":"UPDATED","path":"test","oldHash":"$oldHash","newHash":"$newHash","hashAlgorithm":"$alg"}""",
    )
}

private fun updatedConstructors(hashAlgorithm: HashAlgorithm, oldHash: String, newHash: String) = listOf(
    { IndexEntry.Updated(fileName.invariantSeparatorsPathString, hashAlgorithm, oldHash, newHash) },
    { IndexEntry.UnchangedOrUpdated(fileName, pathA, pathB, hashAlgorithm) },
)

private fun unchanged(alg: HashAlgorithm, hash: String): List<String> {
    return if (alg == SHA_1) listOf(
        """{"state":"UNCHANGED","path":"test","oldHash":"$hash","newHash":"$hash"}""",
        """{"state":"UNCHANGED","path":"test","oldSha1":"$hash","newSha1":"$hash"}""",
        """{"state":"UNCHANGED","path":"test","oldHash":"$hash","newHash":"$hash","hashAlgorithm":"SHA_1"}""",
    )
    else listOf(
        """{"state":"UNCHANGED","path":"test","hashAlgorithm":"$alg","oldHash":"$hash","newHash":"$hash"}""",
        """{"state":"UNCHANGED","hashAlgorithm":"$alg","path":"test","oldSha1":"$hash","newSha1":"$hash"}""",
        """{"state":"UNCHANGED","path":"test","oldHash":"$hash","newHash":"$hash","hashAlgorithm":"$alg"}""",
    )
}

private fun unchangedConstructors(hashAlgorithm: HashAlgorithm, hash: String) = listOf(
    { IndexEntry.Unchanged(fileName.invariantSeparatorsPathString, hashAlgorithm, hash) },
    { IndexEntry.UnchangedOrUpdated(fileName, pathA, pathA, hashAlgorithm) },
)

private inline fun <reified T> List<T>.combine(args: (T, T) -> Arguments): List<Arguments> {
    return flatMap { a -> map { b -> args(a, b) } }
}

private val PATH_A_HASH_PAIRS = listOf(Pair(SHA_1, SHA_1_PATH_A), Pair(CRC32, CRC32_PATH_A))
private val PATH_B_HASHES = mapOf(Pair(SHA_1, SHA_1_PATH_B), Pair(CRC32, CRC32_PATH_B))

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class IndexEntryTest {

     fun equalIndexEntries(): List<Arguments> {
        return PATH_A_HASH_PAIRS.flatMap { pair ->
            val alg = pair.first
            val hashA = pair.second
            val hashB = PATH_B_HASHES[alg]!!
            val created = created(alg, hashA).args("CREATED", createdConstructors(alg, hashA), newHash = hashA)
            val deleted = deleted(alg, hashA).args("DELETED", deletedConstructors(alg, hashA), oldHash = hashA)
            val unchanged = unchanged(alg, hashA).args("UNCHANGED", unchangedConstructors(alg, hashA), hashA, hashA)
            val updated =
                updated(alg, hashA, hashB).args("UPDATED", updatedConstructors(alg, hashA, hashB), hashA, hashB)

            return@flatMap created + deleted + unchanged + updated
        }
    }

    private fun List<String>.args(
        state: String,
        constructors: List<() -> IndexEntry>,
        oldHash: String = "",
        newHash: String = "",
    ) = flatMap { json ->
        constructors.combine { createEntry1, createEntry2 ->
            arguments(
                "IndexEntry[path=test, state=$state, oldHash=$oldHash, newHash=$newHash]",
                createEntry1,
                createEntry2,
                json,
                first(),
            )
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("equalIndexEntries")
    fun `two equal IndexEntry Instances are equal`(
        stringRepresentation: String,
        createEntry1: () -> IndexEntry,
        createEntry2: () -> IndexEntry,
        json: String,
        minimalJson: String,
    ) {
        val entry1 = createEntry1()
        val entry2 = createEntry2()
        val deserializedEntry = Json.decodeFromString<IndexEntry>(json)
        assertAll(
            { assertNotSame(entry1, entry2) },
            { assertEquals(entry1, entry2) },
            { assertEquals(entry1.hashCode(), entry2.hashCode()) },
            { assertEquals(stringRepresentation, entry1.toString()) },
            { assertEquals(stringRepresentation, entry2.toString()) },
            { assertEquals(deserializedEntry, entry1) },
            { assertEquals(deserializedEntry, entry2) },
            { assertEquals(minimalJson, Json.encodeToString(entry1)) },
            { assertEquals(minimalJson, Json.encodeToString(entry2)) },
            { assertEquals(minimalJson, Json.encodeToString(deserializedEntry)) },
            { assertEquals(entry1, Json.decodeFromString(Json.encodeToString(entry1))) },
            { assertEquals(entry1, Json.decodeFromString(Json.encodeToString(entry2))) },
            { assertEquals(entry1, Json.decodeFromString(Json.encodeToString(deserializedEntry))) },
            { assertEquals(entry2, Json.decodeFromString(Json.encodeToString(entry1))) },
            { assertEquals(entry2, Json.decodeFromString(Json.encodeToString(entry2))) },
            { assertEquals(entry2, Json.decodeFromString(Json.encodeToString(deserializedEntry))) },
        )
    }

}
