package eu.pieland.delta

import java.io.IOException
import java.nio.file.Path
import java.security.NoSuchAlgorithmException
import java.util.zip.ZipInputStream
import kotlin.io.path.isRegularFile


private const val DEFAULT_CHUNK_SIZE = 16
private const val DEFAULT_BLOCK_SIZE_MULTIPLIER = 4

object Delta {

    /**
     * Creates a delta patch between source and target directories.
     *
     * @param source directory path
     * @param target directory path
     * @param patch output path
     * @param chunkSize for fine-tuning the comparison algorithm
     * @param blockSize for fine-tuning the comparison algorithm
     * @param pathFilter to determine which paths to include
     * @return the [patch] path for potential chaining
     */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class, NoSuchAlgorithmException::class)
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

    /**
     * Applies a delta patch from a ZIP stream to the target directory.
     *
     * @param zipPatch ZIP stream containing the patch data
     * @param target directory to apply the patch to
     * @return the target path after patching
     */
    @JvmStatic
    @Throws(IOException::class, NoSuchAlgorithmException::class)
    fun patch(zipPatch: ZipInputStream, target: Path): Path {
        return DeltaPatcher(zipPatch, target).patch()
    }
}
