package eu.pieland.delta

import net.jqwik.api.*
import net.jqwik.api.lifecycle.AddLifecycleHook
import net.jqwik.api.lifecycle.PropagationMode
import net.jqwik.kotlin.api.any
import net.jqwik.kotlin.api.combine
import net.jqwik.kotlin.api.ofLength
import net.jqwik.kotlin.api.ofSize
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.assertEquals

internal const val SAME = "same"

@AddLifecycleHook(value = TemporaryFileHook::class, propagateTo = PropagationMode.ALL_DESCENDANTS)
internal object DeltaHappyFlowPropertyTest {

    @Provide
    fun filenameMap(): Arbitrary<Map<Path, ByteArray>> {
        val filenames =
            String.any().alpha().numeric().ofLength(1..2).list().ofSize(0..2).map { it.joinToString(File.separator) }
        val extensions = String.any().alpha().numeric().ofLength(1)
        val completeFilenames =
            combine(filenames, extensions) { filename, extension -> "$filename.$extension" }.map { Path(it) }
        val fileContents =
            combine(
                Int.any(0..100),
                Int.any(0..500),
                String.any().ofLength(1..100000).withoutEdgeCases(),
                Arbitraries.just(SAME)
            ) { randomCount, sameCount, random, same ->
                random.repeat(randomCount) + same.repeat(sameCount)
            }.map { it.toByteArray() }

        return Arbitraries.maps(completeFilenames, fileContents).uniqueKeys { it.invariantSeparatorsPathString }
            .ofSize(0..5).withSizeDistribution(RandomDistribution.biased())
    }

    @Provide
    fun chunkSizes(): Arbitrary<Int> {
        return Int.any().between(1, Int.MAX_VALUE / 8)
    }

    @Property(tries = 100)
    fun test(
        tempDir: Path,
        @ForAll("chunkSizes") chunkSize: Int,
        @ForAll("filenameMap") sourceMap: Map<Path, ByteArray>,
        @ForAll("filenameMap") targetMap: Map<Path, ByteArray>,
    ) {
        // setup
        val source = sourceMap.createOnFileSystem(tempDir, "source")
        val target = targetMap.createOnFileSystem(tempDir, "target")
        val expected = target.toComparableMap()

        // act
        act(source, target, tempDir, chunkSize)

        // assert
        assertEquals(expected, source.toComparableMap())
    }

    @Property(tries = 100)
    fun `test with source == target`(
        tmpDir: Path,
        @ForAll("chunkSizes") chunkSize: Int,
        @ForAll("filenameMap") sourceMap: Map<Path, ByteArray>,
    ) {
        // setup
        val source = sourceMap.createOnFileSystem(tmpDir, "source")
        val target = sourceMap.createOnFileSystem(tmpDir, "target")
        val expected = target.toComparableMap()
        assertEquals(expected, source.toComparableMap())

        // act
        act(source, target, tmpDir, chunkSize)

        // assert
        assertEquals(expected, source.toComparableMap())
    }
}


