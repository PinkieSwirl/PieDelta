package eu.pieland.delta

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DeltaHappyFlowRealWorldTest {

    @TempDir
    private lateinit var tmpdir: Path

    @ParameterizedTest
    @CsvSource(
        textBlock = """
        delta-diff-1.1.jar, delta-diff-1.1.3.jar"""
    )
    fun `round-trip successfully`(sourcePathString: String, targetPathString: String) {
        // setup
        val source = tmpdir.resolve("source").apply { createDirectories() }
        unpackZip(javaClass,sourcePathString, source)
        val target = tmpdir.resolve("target").apply { createDirectories() }
        unpackZip(javaClass, targetPathString, target)
        val expected = target.toComparableMap()
        assertNotEquals(expected, source.toComparableMap())

        // act
        act(source, target, tmpdir)

        // assert
        assertEquals(expected, source.toComparableMap())
    }
}
