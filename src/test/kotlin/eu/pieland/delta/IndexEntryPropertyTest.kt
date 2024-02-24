package eu.pieland.delta

import eu.pieland.delta.IndexEntry.State
import eu.pieland.delta.IndexEntry.State.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.jqwik.api.*
import net.jqwik.api.Tuple.Tuple2
import net.jqwik.kotlin.api.any
import net.jqwik.kotlin.api.combine
import net.jqwik.kotlin.api.orNull
import org.junit.jupiter.api.assertAll
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

internal class IndexEntryPropertyTest {

    @Provide
    fun indexEntry(): Arbitrary<IndexEntry> {
        val anyString = String.any()
        val anyPath = anyString.alpha()
        return Enum.any<State>().flatMap {
            when (it!!) {
                UNCHANGED -> combine(anyPath, anyString) { v1, v2 -> IndexEntry.Unchanged(Path(v1), v2) }
                CREATED -> combine(anyPath, anyString, anyString) { v1, v2, v3 -> IndexEntry.Created(Path(v1), v2, v3) }
                UPDATED -> combine(anyPath, anyString, anyString) { v1, v2, v3 -> IndexEntry.Updated(Path(v1), v2, v3) }
                DELETED -> combine(anyPath, anyString, anyString) { v1, v2, v3 -> IndexEntry.Deleted(Path(v1), v2, v3) }
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
        @ForAll("indexEntries") entries: Tuple2<IndexEntry, IndexEntry>
    ) {
        assertNotEquals(entries.get1(), entries.get2())
    }

    @Property
    fun `IndexEntry and Any are not equal`(
        @ForAll("indexEntry") entry1: IndexEntry, @ForAll("anyNullableString") entry2: Any?
    ) {
        assertNotEquals(entry1, entry2)
    }

    private fun assertNotEquals(entry1: Any?, entry2: Any?) {
        // Cannot test hash code, since the implementation is not "secure"
        assertAll(
            { assertNotSame(entry1, entry2) },
            { assertNotSame(entry2, entry1) },
            { kotlin.test.assertNotEquals(entry1, entry2) },
            { kotlin.test.assertNotEquals(entry2, entry1) },
            { kotlin.test.assertNotEquals(entry1.toString(), entry2.toString()) },
            { kotlin.test.assertNotEquals(entry2.toString(), entry1.toString()) },
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
