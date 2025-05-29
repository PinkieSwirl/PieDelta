package eu.pieland.delta

import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.isRegularFile


private const val DEFAULT_CHUNK_SIZE = 16
private const val DEFAULT_BLOCK_SIZE_MULTIPLIER = 4

object Delta {

    @JvmStatic
    @JvmOverloads
    fun create(
        source: Path,
        target: Path,
        patch: Path,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        blockSize: Int = chunkSize * DEFAULT_BLOCK_SIZE_MULTIPLIER,
        pathFilter: (Path) -> Boolean = { it.isRegularFile() },
    ): Path {
        return DeltaCreator(source, target, patch, chunkSize, blockSize, pathFilter).create()
    }

    @JvmStatic
    fun patch(zipPatch: ZipInputStream, target: Path): Path {
        return DeltaPatcher(zipPatch, target).patch()
    }
}
