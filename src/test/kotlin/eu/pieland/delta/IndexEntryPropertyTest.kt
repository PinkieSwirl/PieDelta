package eu.pieland.delta

import eu.pieland.delta.HashAlgorithm.SHA_1
import eu.pieland.delta.IndexEntry.*
import eu.pieland.delta.IndexEntry.State.*
import kotlinx.serialization.json.Json
import net.jqwik.api.*
import net.jqwik.api.Tuple.Tuple2
import net.jqwik.kotlin.api.any
import net.jqwik.kotlin.api.combine
import net.jqwik.kotlin.api.orNull
import org.junit.jupiter.api.assertAll
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

internal class IndexEntryPropertyTest {

    @Provide
    fun indexEntry(): Arbitrary<IndexEntry> {
        val string = String.any()
        val path = string.alpha()
        return Enum.any<State>().flatMap {
            when (it) {
                UNCHANGED -> combine(path, string) { v1, v2 -> Unchanged(v1, SHA_1, v2) }
                CREATED -> combine(path, string) { v1, v2 -> Created(v1, SHA_1, v2) }
                UPDATED -> combine(path, string, string) { v1, v2, v3 -> Updated(v1, SHA_1, v2, v3) }
                DELETED -> combine(path, string) { v1, v2 -> Deleted(v1, SHA_1, v2) }
            }
        }
    }

    @Provide
    fun indexEntries(): Arbitrary<Tuple2<IndexEntry, IndexEntry>> {
        return combine(
            indexEntry(),
            indexEntry(),
            filter = { v1: IndexEntry, v2: IndexEntry -> v1 != v2 }) { v1: IndexEntry, v2: IndexEntry ->
            Tuple.of(v1, v2)
        }
    }

    @Provide
    fun anyNullableString(): Arbitrary<String?> = String.any().orNull(0.05)

    @Property
    fun `same IndexEntry Instances are same`(@ForAll("indexEntry") entry: IndexEntry) {
        assertAll(
            { assertSame(entry, entry) },
            { assertEquals(entry, entry) },
            { assertEquals(entry.hashCode(), entry.hashCode()) },
            { assertEquals(entry.toString(), entry.toString()) },
        )
    }

    @Property
    fun `two different IndexEntry Instances are not equal`(
        @ForAll("indexEntries") entries: Tuple2<IndexEntry, IndexEntry>,
    ) {
        assertEntriesNotEquals(entries.get1(), entries.get2())
    }

    @Property
    fun `IndexEntry and Any are not equal`(
        @ForAll("indexEntry") entry1: IndexEntry, @ForAll("anyNullableString") entry2: Any?,
    ) {
        assertEntriesNotEquals(entry1, entry2)
    }

    private fun assertEntriesNotEquals(entry1: Any?, entry2: Any?) {
        // Cannot test hash code, since the implementation is not "secure"
        assertAll(
            { assertNotSame(entry1, entry2) },
            { assertNotSame(entry2, entry1) },
            { assertNotEquals(entry1, entry2) },
            { assertNotEquals(entry2, entry1) },
            { assertNotEquals(entry1.toString(), entry2.toString()) },
            { assertNotEquals(entry2.toString(), entry1.toString()) },
        )
    }

    @Property
    fun `serialization self-test`(@ForAll("indexEntry") entry1: IndexEntry) {
        val entry2 = Json.decodeFromString<IndexEntry>(Json.encodeToString(entry1))
        assertAll(
            { assertNotSame(entry1, entry2) },
            { assertEquals(entry1, entry2) },
            { assertEquals(entry2, entry1) },
            { assertEquals(entry1.hashCode(), entry2.hashCode()) },
            { assertEquals(entry2.hashCode(), entry1.hashCode()) },
            { assertEquals(entry1.toString(), entry2.toString()) },
            { assertEquals(entry2.toString(), entry1.toString()) },
        )
    }

}
