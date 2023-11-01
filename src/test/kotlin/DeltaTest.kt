package eu.pieland.delta

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.io.path.*
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DeltaTest {

    @TempDir
    private lateinit var tmpdir: Path

    @Test
    fun `round-trip single file update successfully`() {
        // setup
        val source = tmpdir.resolve("source").apply {
            createDirectories()
            resolve("unchanged").writeText("unchanged")
            resolve("updated").writeText("updated-1".repeat(100))
            resolve("deleted").writeText("deleted")
        }
        val target = tmpdir.resolve("target").apply {
            createDirectories()
            resolve("unchanged").writeText("unchanged")
            resolve("created").writeText("created")
            resolve("updated").writeText("updated-1".repeat(99))
        }
        val expected = target.toComparableMap()
        assertNotEquals(expected, source.toComparableMap())

        // act
        val delta = DeltaCreator(source, target, tmpdir.resolve("patch.zip")).create()
        delta.inZip().use { DeltaPatcher(it, source).patch() }

        // assert
        assertEquals(expected, source.toComparableMap())
    }

    @Suppress("UnusedPrivateMember") // Method source
    private fun `round-trip single file update success arguments`(): Iterator<Arguments> {
        val repeats = sequenceOf(2, 100, 10_000, 1_000_000, 2_000_000, 10_000_000)
        val chunkSizes = sequenceOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 16, 32, 128, 512, 1024, 10_000, 100_000)
        return repeats.flatMap { repeat ->
            chunkSizes.mapNotNull { chunkSize ->
                when {
                    repeat > 10_000 && chunkSize < 32 -> null
                    repeat > 1_000_000 && chunkSize < 512 -> null
                    else -> arguments(repeat, chunkSize)
                }
            }
        }.iterator()
    }

    @ParameterizedTest
    @MethodSource("round-trip single file update success arguments")
    fun `round-trip single file update success`(repeated: Int, chunkSize: Int) {
        // setup
        val source = tmpdir.resolve("source").apply {
            createDirectories()
            resolve("unchanged").writeText("unchanged")
            resolve("updated").apply {
                writeText("crap".repeat(repeated))
                repeat(100) {
                    writeText("crap".repeat(repeated), Charsets.UTF_8, StandardOpenOption.APPEND)
                }
                writeText("updated".repeat(repeated), Charsets.UTF_8, StandardOpenOption.APPEND)
            }
            resolve("deleted").writeText("deleted")
        }
        val target = tmpdir.resolve("target").apply {
            createDirectories()
            resolve("unchanged").writeText("unchanged")
            resolve("created").writeText("created")
            resolve("updated").writeText("updated".repeat(repeated - 1))
        }
        val expected = target.toComparableMap()
        assertNotEquals(expected, source.toComparableMap())

        // act
        val delta = DeltaCreator(source, target, tmpdir.resolve("patch.zip"), chunkSize = chunkSize).create()
        delta.inZip().use { DeltaPatcher(it, source).patch() }

        // assert
        assertEquals(expected, source.toComparableMap())
    }

    @Test
    fun `apply diff-zip successfully`() {
        // setup
        val source = tmpdir.resolve("source").apply {
            createDirectories()
            resolve("deleted").writeText("bar")
            resolve("unchanged").writeText("foo")
            resolve("updated").writeText("baz")
        }
        val expectedTarget = tmpdir.resolve("target").apply {
            createDirectories()
            resolve("added").writeText("42")
            resolve("unchanged").writeText("foo")
            resolve("updated").writeText("ba42")
        }
        val expected = expectedTarget.toComparableMap()
        assertNotEquals(expected, source.toComparableMap())

        // act
        val delta = tmpdir.resolve("diff.zip").also { it.writeBytes(javaClass.getResource("/diff.zip")!!.readBytes()) }
        delta.inZip().use { DeltaPatcher(it, source).patch() }

        // assert
        assertEquals(expected, source.toComparableMap())
    }

    @Test
    fun `create diff-zip successfully`() {
        // setup
        val source = tmpdir.resolve("source").apply {
            createDirectories()
            resolve("deleted").writeText("bar")
            resolve("unchanged").writeText("foo")
            resolve("updated").writeText("baz")
        }
        val target = tmpdir.resolve("target").apply {
            createDirectories()
            resolve("added").writeText("42")
            resolve("unchanged").writeText("foo")
            resolve("updated").writeText("ba42")
        }
        val expected =
            tmpdir.resolve("diff.zip").also { it.writeBytes(javaClass.getResource("/diff.zip")!!.readBytes()) }

        // act
        val patch = DeltaCreator(source, target, tmpdir.resolve("patch.zip")).create()

        // assert
        comparePathToZips(expected, patch)
    }

    private fun comparePathToZips(path1: Path, path2: Path) {
        ZipInputStream(path1.inputStream().buffered()).use { zip1 ->

            ZipInputStream(path2.inputStream().buffered()).use { zip2 ->

                generateSequence { zip1.nextEntry }
                    .zip(generateSequence { zip2.nextEntry })
                    .forEachIndexed { index, (e1, e2) ->
                        if (index == 0) {
                            assertTrue(e1.name.startsWith(".index_"))
                            assertTrue(e2.name.startsWith(".index_"))
                            assertEquals(e1.name.length, e2.name.length)
                        } else {
                            assertEquals(e1.name, e2.name)
                        }
                        assertEquals(e1.size, e2.size)
                        if (index == 0) {
                            val x = zip1.bufferedReader()
                                .lineSequence().map { Json.decodeFromString<IndexEntry>(it) }.toList()
                            val y = zip2.bufferedReader()
                                .lineSequence().map { Json.decodeFromString<IndexEntry>(it) }.toList()
                            assertIterableEquals(x, y)
                        } else {
                            assertEquals(
                                zip1.readBytes().toString(Charsets.UTF_8),
                                zip2.readBytes().toString(Charsets.UTF_8)
                            )
                        }
                    }
                assertNull(zip1.nextEntry)
                assertNull(zip2.nextEntry)
            }
        }
    }

}

@OptIn(ExperimentalPathApi::class)
internal fun Path.toComparableMap(): TreeMap<Path, String> {
    return TreeMap(walk(PathWalkOption.INCLUDE_DIRECTORIES).map { childPath ->
        childPath.relativeTo(this) to
                if (childPath.isRegularFile()) childPath.computeSha1()
                else childPath.relativeTo(this).invariantSeparatorsPathString.lowercase(Locale.ENGLISH)
    }.toMap())
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.inZip(): ZipInputStream {
    return ZipInputStream(inputStream().buffered())
}
