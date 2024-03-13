package eu.pieland.delta

import eu.pieland.delta.HashAlgorithm.CRC32
import eu.pieland.delta.HashAlgorithm.SHA_1
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
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

private fun createdJsons(alg: HashAlgorithm, newHash: String): List<String> {
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
    { IndexEntry.Created(fileName, hashAlgorithm, newHash = newHash) },
)

private fun deletedJsons(alg: HashAlgorithm, oldHash: String): List<String> {
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
    { IndexEntry.Deleted(fileName, hashAlgorithm, oldHash = newHash) },
)

private fun updatedJsons(alg: HashAlgorithm, oldHash: String, newHash: String): List<String> {
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
    { IndexEntry.Updated(fileName, hashAlgorithm, oldHash, newHash) },
    { IndexEntry.UnchangedOrUpdated(fileName, pathA, pathB, hashAlgorithm) },
)

private fun unchangedJsons(alg: HashAlgorithm, hash: String): List<String> {
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
    { IndexEntry.Unchanged(fileName, hashAlgorithm, hash) },
    { IndexEntry.UnchangedOrUpdated(fileName, pathA, pathA, hashAlgorithm) },
)

private inline fun <reified T> List<T>.combine(args: (T, T) -> Arguments): List<Arguments> {
    return flatMap { a -> map { b -> args(a, b) } }
}

private val PATH_A_HASH_PAIRS = listOf(Pair(SHA_1, SHA_1_PATH_A), Pair(CRC32, CRC32_PATH_A))
private val PATH_B_HASHES = mapOf(Pair(SHA_1, SHA_1_PATH_B), Pair(CRC32, CRC32_PATH_B))

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class IndexEntryTest {

    private fun equalIndexEntries(): List<Arguments> {
        return PATH_A_HASH_PAIRS.flatMap { pair ->
            val alg = pair.first
            val hash = pair.second
            val createdJsons = createdJsons(alg, hash)
            val deletedJsons = deletedJsons(alg, hash)
            val unchangedJsons = unchangedJsons(alg, hash)
            val newHash = PATH_B_HASHES[alg]!!
            val updatedJsons = updatedJsons(alg, hash, newHash)
            createdJsons.flatMap { json ->
                createdConstructors(alg, hash).combine { createEntry1, createEntry2 ->
                    arguments(
                        "IndexEntry[path=test, state=CREATED, oldHash=, newHash=$hash]",
                        createEntry1,
                        createEntry2,
                        json,
                        createdJsons.first(),
                    )
                }
            } + deletedJsons.flatMap { json ->
                deletedConstructors(alg, hash).combine { createEntry1, createEntry2 ->
                    arguments(
                        "IndexEntry[path=test, state=DELETED, oldHash=$hash, newHash=]",
                        createEntry1,
                        createEntry2,
                        json,
                        deletedJsons.first(),
                    )
                }
            } + unchangedJsons.flatMap { json ->
                unchangedConstructors(alg, hash).combine { createEntry1, createEntry2 ->
                    arguments(
                        "IndexEntry[path=test, state=UNCHANGED, oldHash=$hash, newHash=$hash]",
                        createEntry1,
                        createEntry2,
                        json,
                        unchangedJsons.first(),
                    )
                }
            } + updatedJsons.flatMap { json ->
                updatedConstructors(alg, hash, newHash).combine { createEntry1, createEntry2 ->
                    arguments(
                        "IndexEntry[path=test, state=UPDATED, oldHash=$hash, newHash=$newHash]",
                        createEntry1,
                        createEntry2,
                        json,
                        updatedJsons.first(),
                    )
                }
            }
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
