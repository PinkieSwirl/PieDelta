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

private val CREATED_JSON_CRC32 = createdJsons(CRC32, CRC32_PATH_A)
private val CREATED_JSON_SHA_1 = createdJsons(SHA_1, SHA_1_PATH_A)
private fun createdJsons(hashAlgorithm: HashAlgorithm, newHash: String): List<String> {
    return if (hashAlgorithm == SHA_1) listOf(
        """{"state":"CREATED","path":"test","newHash":"$newHash"}""",
        """{"state":"CREATED","path":"test","newSha1":"$newHash"}""", // Old naming
        """{"state":"CREATED","path":"test","oldHash":"","newHash":"$newHash"}""",
        """{"state":"CREATED","path":"test","oldHash":"","newHash":"$newHash","hashAlgorithm":"SHA_1"}""",
    )
    else listOf(
        """{"state":"CREATED","path":"test","hashAlgorithm":"$hashAlgorithm","newHash":"$newHash"}""",
        """{"state":"CREATED","hashAlgorithm":"$hashAlgorithm","path":"test","newSha1":"$newHash"}""", // Old naming
        """{"hashAlgorithm":"$hashAlgorithm","state":"CREATED","path":"test","oldHash":"","newHash":"$newHash"}""",
        """{"state":"CREATED","path":"test","oldHash":"","newHash":"$newHash","hashAlgorithm":"$hashAlgorithm"}""",
    )
}

private fun createdConstructors(hashAlgorithm: HashAlgorithm, newHash: String) = listOf(
    { IndexEntry.Created(fileName, pathA, hashAlgorithm) },
    { IndexEntry.Created(fileName, hashAlgorithm, newHash = newHash) },
)


private val DELETED_CRC32 = listOf(
    { IndexEntry.Deleted(fileName, pathA, CRC32) },
    { IndexEntry.Deleted(fileName, CRC32, oldHash = CRC32_PATH_A) },
)
private const val DELETED_MINIMAL_JSON_CRC32 =
    """{"state":"DELETED","path":"test","hashAlgorithm":"CRC32","oldHash":"$CRC32_PATH_A"}"""
private val DELETED_JSON_CRC32 = listOf(
    DELETED_MINIMAL_JSON_CRC32,
    """{"state":"DELETED","path":"test","oldSha1":"$CRC32_PATH_A","hashAlgorithm":"CRC32"}""", // Old naming
    """{"state":"DELETED","path":"test","newHash":"","oldHash":"$CRC32_PATH_A","hashAlgorithm":"CRC32"}""",
)

private val DELETED_SHA_1 = listOf(
    { IndexEntry.Deleted(fileName, pathA) },
    { IndexEntry.Deleted(fileName, SHA_1, oldHash = SHA_1_PATH_A) },
)
private const val DELETED_MINIMAL_JSON_SHA_1 = """{"state":"DELETED","path":"test","oldHash":"$SHA_1_PATH_A"}"""
private val DELETED_JSON_SHA_1 = listOf(
    DELETED_MINIMAL_JSON_SHA_1,
    """{"state":"DELETED","path":"test","oldSha1":"$SHA_1_PATH_A"}""", // Old naming
    """{"state":"DELETED","path":"test","newHash":"","oldHash":"$SHA_1_PATH_A"}""",
    """{"state":"DELETED","path":"test","newHash":"","oldHash":"$SHA_1_PATH_A","hashAlgorithm":"SHA_1"}""",
)

private val UPDATED_SHA_1 = listOf(
    { IndexEntry.Updated(fileName, SHA_1, SHA_1_PATH_A, SHA_1_PATH_B) },
    { IndexEntry.UnchangedOrUpdated(fileName, pathA, pathB) },
)
private const val UPDATED_MINIMAL_JSON_SHA_1 =
    """{"state":"UPDATED","path":"test","oldHash":"$SHA_1_PATH_A","newHash":"$SHA_1_PATH_B"}"""
private val UPDATED_JSON_SHA_1 = listOf(
    UPDATED_MINIMAL_JSON_SHA_1,
    """{"state":"UPDATED","path":"test","oldSha1":"$SHA_1_PATH_A","newSha1":"$SHA_1_PATH_B"}""", // Old naming
    """{"state":"UPDATED","path":"test","oldHash":"$SHA_1_PATH_A","newHash":"$SHA_1_PATH_B","hashAlgorithm":"SHA_1"}""",
)

private val UNCHANGED_SHA_1 = listOf(
    { IndexEntry.Unchanged(fileName, SHA_1, SHA_1_PATH_A) },
    { IndexEntry.UnchangedOrUpdated(fileName, pathA, pathA) },
)
private const val UNCHANGED_MINIMAL_JSON_SHA_1 =
    """{"state":"UNCHANGED","path":"test","oldHash":"$SHA_1_PATH_A","newHash":"$SHA_1_PATH_A"}"""
private val UNCHANGED_JSON_SHA_1 = listOf(
    UNCHANGED_MINIMAL_JSON_SHA_1,
    """{"state":"UNCHANGED","path":"test","oldSha1":"$SHA_1_PATH_A","newSha1":"$SHA_1_PATH_A"}""", // Old naming
    """{"state":"UNCHANGED","path":"test","oldHash":"$SHA_1_PATH_A","newHash":"$SHA_1_PATH_A","hashAlgorithm":"SHA_1"}""",
)

private val HASH_ALGORITHMS = listOf(SHA_1, CRC32)

private inline fun <reified T> List<T>.combine(args: (T, T) -> Arguments): List<Arguments> {
    return flatMap { a -> map { b -> args(a, b) } }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class IndexEntryTest {

    private fun equalIndexEntries(): List<Arguments> {
        return CREATED_JSON_SHA_1.flatMap { json ->
            createdConstructors(SHA_1, SHA_1_PATH_A).combine { createEntry1, createEntry2 ->
                arguments(
                    "IndexEntry[path=test, state=CREATED, oldHash=, newHash=$SHA_1_PATH_A]",
                    createEntry1,
                    createEntry2,
                    json,
                    CREATED_JSON_SHA_1.first(),
                )
            }
        } + CREATED_JSON_CRC32.flatMap { json ->
            createdConstructors(CRC32, CRC32_PATH_A).combine { createEntry1, createEntry2 ->
                arguments(
                    "IndexEntry[path=test, state=CREATED, oldHash=, newHash=$CRC32_PATH_A]",
                    createEntry1,
                    createEntry2,
                    json,
                    CREATED_JSON_CRC32.first(),
                )
            }
        } + DELETED_JSON_SHA_1.flatMap { json ->
            DELETED_SHA_1.combine { createEntry1, createEntry2 ->
                arguments(
                    "IndexEntry[path=test, state=DELETED, oldHash=$SHA_1_PATH_A, newHash=]",
                    createEntry1,
                    createEntry2,
                    json,
                    DELETED_MINIMAL_JSON_SHA_1,
                )
            }
        } + DELETED_JSON_CRC32.flatMap { json ->
            DELETED_CRC32.combine { createEntry1, createEntry2 ->
                arguments(
                    "IndexEntry[path=test, state=DELETED, oldHash=$CRC32_PATH_A, newHash=]",
                    createEntry1,
                    createEntry2,
                    json,
                    DELETED_MINIMAL_JSON_CRC32,
                )
            }
        } + UPDATED_JSON_SHA_1.flatMap { json ->
            UPDATED_SHA_1.combine { createEntry1, createEntry2 ->
                arguments(
                    "IndexEntry[path=test, state=UPDATED, oldHash=$SHA_1_PATH_A, newHash=$SHA_1_PATH_B]",
                    createEntry1,
                    createEntry2,
                    json,
                    UPDATED_MINIMAL_JSON_SHA_1,
                )
            }
        } + UNCHANGED_JSON_SHA_1.flatMap { json ->
            UNCHANGED_SHA_1.combine { createEntry1, createEntry2 ->
                arguments(
                    "IndexEntry[path=test, state=UNCHANGED, oldHash=$SHA_1_PATH_A, newHash=$SHA_1_PATH_A]",
                    createEntry1,
                    createEntry2,
                    json,
                    UNCHANGED_MINIMAL_JSON_SHA_1,
                )
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
