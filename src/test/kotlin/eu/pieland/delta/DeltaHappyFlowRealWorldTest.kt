package eu.pieland.delta

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
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
        unpackZip(sourcePathString, source)
        val target = tmpdir.resolve("target").apply { createDirectories() }
        unpackZip(targetPathString, target)
        val expected = target.toComparableMap()
        assertNotEquals(expected, source.toComparableMap())

        // act
        val delta = DeltaCreator(source, target, tmpdir.resolve("patch.zip")).create()
        delta.inZip().use { DeltaPatcher(it, source).patch() }

        // assert
        assertEquals(expected, source.toComparableMap())
    }

    private fun unpackZip(pathString: String, path: Path) {
        val targetIn = ZipInputStream(javaClass.getResourceAsStream("/$pathString")!!)
        generateSequence { targetIn.nextEntry }.forEach {
            if (it.isDirectory) path.resolve(it.name).createDirectories()
            else path.resolve(it.name).writeBytes(targetIn.readBytes())
        }
    }
}
