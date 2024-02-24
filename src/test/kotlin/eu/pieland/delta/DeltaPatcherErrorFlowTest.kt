package eu.pieland.delta

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DeltaPatcherErrorFlowTest {

    @TempDir
    private lateinit var tmpdir: Path
    private val existingFile = Path(".gitignore")
    private val existingDir = Path("src")
    private val nonExistingPath = Path("~")

    fun invalidConfigurations(): List<Arguments> {
        return listOf(
            arguments(
                { deltaPatcher(zipPatch = ZipInputStream(ByteArrayInputStream(byteArrayOf(1)))) },
                "'zipPatch' must be a valid zip"
            ),
            arguments(
                { deltaPatcher(zipPatch = ZipInputStream(inputStream("/diff-corrupted-index.zip"))) },
                "Unexpected index file name: '.xindex_da4b659c-7840-4137-853b-9cd9960e984d', must start with '.index'"
            ),
            arguments({ deltaPatcher(target = existingFile) }, "'target' must be an existing directory"),
            arguments({ deltaPatcher(target = nonExistingPath) }, "'target' must be an existing directory"),
        )
    }

    private fun inputStream(path: String) = javaClass.getResourceAsStream(path)!!

    private fun deltaPatcher(
        zipPatch: ZipInputStream = ZipInputStream(inputStream("/diff.zip")),
        target: Path = existingDir,
    ) = DeltaPatcher(zipPatch, target)


    @ParameterizedTest
    @MethodSource("invalidConfigurations")
    fun `creation of DeltaPatcher fails`(createDeltaPatcher: () -> DeltaPatcher, expectedMessage: String) {
        val exception = assertFailsWith<IllegalArgumentException> { createDeltaPatcher() }
        assertEquals(expectedMessage, exception.message)
    }

    @ParameterizedTest
    @CsvSource(
        textBlock = """
diff-corrupted-gdiff.zip    , Unexpected magic bytes or version
diff-wrong-index-updated.zip, 'Index and zip-stream un-synchronized, index: wrong, zip-stream: updated.gdiff'
diff-wrong-index-created.zip, 'Index and zip-stream un-synchronized, index: wrong, zip-stream: added'
"""
    )
    fun `unexpected magic bytes or version`(path: String, expectedMessage: String) {
        val target = targetPath()
        val patcher = deltaPatcher(zipPatch = ZipInputStream(inputStream("/$path")), target = target)
        val exception = assertFails { patcher.patch() }
        assertEquals(expectedMessage, exception.message)
    }

    fun invalidFiles() = listOf(
        arguments({ targetPath(deletedName = null) }, "DELETED file doesn't exist: deleted"),
        arguments({ targetPath(deletedName = "wrong") }, "DELETED file doesn't exist: deleted"),
        arguments({ targetPath(deletedContent = null) }, "DELETED file doesn't exist: deleted"),
        arguments({ targetPath(deletedContent = "barx") }, "DELETED file-check failed for old file: deleted"),
        arguments({ targetPath(unchangedName = null) }, "UNCHANGED file does not exists as regular file: unchanged"),
        arguments({ targetPath(unchangedName = "wrong") }, "UNCHANGED file does not exists as regular file: unchanged"),
        arguments({ targetPath(unchangedContent = null) }, "UNCHANGED file does not exists as regular file: unchanged"),
        arguments({ targetPath(unchangedContent = "foox") }, "UNCHANGED file-check failed for file: unchanged"),
        arguments({ targetPath(updatedName = null) }, "UPDATED file doesn't exist: updated"),
        arguments({ targetPath(updatedName = "wrong") }, "UPDATED file doesn't exist: updated"),
        arguments({ targetPath(updatedContent = null) }, "UPDATED file doesn't exist: updated"),
        arguments({ targetPath(updatedContent = "bazx") }, "UPDATED file-check failed for old file: updated"),
    )

    @Suppress("LongParameterList")
    private fun targetPath(
        deletedName: String? = "deleted",
        deletedContent: String? = "bar",
        unchangedName: String? = "unchanged",
        unchangedContent: String? = "foo",
        updatedName: String? = "updated",
        updatedContent: String? = "baz",
    ): Path {
        return tmpdir.resolve("target").apply {
            createDirectories()
            createFile(deletedName, deletedContent)
            createFile(unchangedName, unchangedContent)
            createFile(updatedName, updatedContent)
        }
    }

    private fun Path.createFile(name: String?, content: String?) {
        if (name == null) return
        resolve(name).apply { if (content != null) writeText(content) else createDirectories() }
    }

    @ParameterizedTest
    @MethodSource("invalidFiles")
    fun `invalid target path`(targetCreator: () -> Path, expectedMessage: String) {
        val patcher = deltaPatcher(zipPatch = ZipInputStream(inputStream("/diff.zip")), target = targetCreator())
        val exception = assertFailsWith<IllegalStateException> { patcher.patch() }
        assertEquals(expectedMessage, exception.message)
    }
}
