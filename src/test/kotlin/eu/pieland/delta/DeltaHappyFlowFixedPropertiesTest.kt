package eu.pieland.delta

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DeltaHappyFlowFixedPropertiesTest {

    @TempDir
    private lateinit var tmpdir: Path

    @Test
    fun `test {=, A=A} to {=} = delete empty directories successfully`() {
        // setup
        val sourceMap: Map<Path, ByteArray> = mapOf(Path("A").resolve("A.A") to byteArrayOf(0))
        val targetMap: Map<Path, ByteArray> = mapOf()
        val source = sourceMap.createOnFileSystem(tmpdir, "source")
        val target = targetMap.createOnFileSystem(tmpdir, "target")

        test(target, source, chunkSize = 16)
    }

    @Test
    fun `test copy command does not abort creating gdiff to early`() {
        // setup
        val sourceMap: Map<Path, ByteArray> = mapOf(Path(".N") to "a".toByteArray())
        val targetMap: Map<Path, ByteArray> = mapOf(Path(".n") to "samesamesame".toByteArray())
        val source = sourceMap.createOnFileSystem(tmpdir, "source")
        val target = targetMap.createOnFileSystem(tmpdir, "target")

        test(target, source, chunkSize = 1)
    }

    private fun test(target: Path, source: Path, chunkSize: Int) {
        val expected = target.toComparableMap()
        assertNotEquals(expected, source.toComparableMap())

        // act
        val delta = DeltaCreator(source, target, tmpdir.resolve("patch.zip"), chunkSize).create()
        delta.inZip().use { DeltaPatcher(it, source).patch() }

        // assert
        assertEquals(expected, source.toComparableMap())
    }
}
