package eu.pieland.delta

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class DeltaCreatorErrorFlowTest {

    private val existingFile = Path(".gitignore")
    private val existingDir = Path("src")
    private val nonExistingPath = Path("~")

    fun invalidConfigurations(): List<Arguments> {
        return listOf(
            arguments({ deltaCreator(source = existingFile) }, "'source' must be an existing directory"),
            arguments({ deltaCreator(source = nonExistingPath) }, "'source' must be an existing directory"),
            arguments({ deltaCreator(target = existingFile) }, "'target' must be an existing directory"),
            arguments({ deltaCreator(target = nonExistingPath) }, "'target' must be an existing directory"),
            arguments({ deltaCreator(patch = existingDir) }, "'patch' must not exist, parent directories may exist"),
            arguments({ deltaCreator(patch = existingFile) }, "'patch' must not exist, parent directories may exist"),
            arguments({ deltaCreator(chunkSize = -1) }, "'chunkSize' must be greater than 0"),
            arguments(
                { deltaCreator(blockSize = 7) },
                "'blockSize' must be greater than 'chunkSize', optimally a multiple of it"
            ),
        )
    }

    private fun deltaCreator(
        source: Path = existingDir,
        target: Path = existingDir,
        patch: Path = nonExistingPath,
        chunkSize: Int = 8,
        blockSize: Int = 2 * chunkSize
    ) = DeltaCreator(source, target, patch, chunkSize, blockSize)


    @ParameterizedTest
    @MethodSource("invalidConfigurations")
    fun `creation of DeltaCreator fails`(createDeltaCreator: () -> DeltaCreator, expectedMessage: String) {
        val exception = assertFailsWith(IllegalArgumentException::class) { createDeltaCreator() }
        assertEquals(expectedMessage, exception.message)
    }
}
