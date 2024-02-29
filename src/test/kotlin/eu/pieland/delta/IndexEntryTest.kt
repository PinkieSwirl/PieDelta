package eu.pieland.delta

import eu.pieland.delta.HashAlgorithm.SHA_1
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class IndexEntryTest {

    fun equalIndexEntries() = listOf(
        arguments(
            IndexEntry.Created(Path("a"), SHA_1, "b", "c"),
            IndexEntry.Created(Path("a"), SHA_1, "b", "c"),
            "IndexEntry[path=a, state=CREATED, oldSha1=b, newSha1=c]",
        ),
        arguments(
            IndexEntry.Deleted(Path("x"), SHA_1, "y", "z"),
            IndexEntry.Deleted(Path("x"), SHA_1, "y", "z"),
            "IndexEntry[path=x, state=DELETED, oldSha1=y, newSha1=z]",
        ),
        arguments(
            IndexEntry.Updated(Path("0"), SHA_1, "1", "2"),
            IndexEntry.Updated(Path("0"), SHA_1, "1", "2"),
            "IndexEntry[path=0, state=UPDATED, oldSha1=1, newSha1=2]",
        ),
        arguments(
            IndexEntry.Unchanged(Path("9"), SHA_1, "8"),
            IndexEntry.Unchanged(Path("9"), SHA_1, "8"),
            "IndexEntry[path=9, state=UNCHANGED, oldSha1=8, newSha1=8]",
        ),
    )

    @ParameterizedTest
    @MethodSource("equalIndexEntries")
    fun `two equal IndexEntry Instances are equal`(entry1: IndexEntry, entry2: IndexEntry, toStringValue: String) {
        assertAll(
            { assertNotSame(entry1, entry2) },
            { assertEquals(entry1, entry2) },
            { assertEquals(entry1.hashCode(), entry2.hashCode()) },
            { assertEquals(toStringValue, entry1.toString()) },
            { assertEquals(entry1.toString(), entry2.toString()) },
        )
    }

}
