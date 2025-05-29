package eu.pieland.delta

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// No 'A' and 'Z'
val pool: List<Char> = ('a'..'z') + ('B'..'Y') + ('0'..'9')


internal class DeltaHappyFlowOutputTest {

    private val rootPath = Jimfs.newFileSystem(Configuration.unix()).getPath("root")
    private val source = rootPath.resolve("source")
    private val target = rootPath.resolve("target")

    @ParameterizedTest
    @CsvFileSource(
        resources = ["/DeltaCreator/addCopy.csv"],
        useHeadersInDisplayName = true,
        ignoreLeadingAndTrailingWhitespace = true,
    )
    fun `test DeltaCreator#addCopy`(length: Int, offset: Long, chunkSize: Int, uniqueTargetData: Int) {
        // setup
        source.resolve(".txt").newBufferedWriter().use { writer ->
            repeat((offset / Int.MAX_VALUE).toInt()) { repeat(Int.MAX_VALUE) { writer.append(pool.random()) } }
            repeat((offset % Int.MAX_VALUE).toInt()) { writer.append(pool.random()) }

            repeat(length) { writer.append('A') }
        }
        target.resolve(".txt").newBufferedWriter().use { writer ->
            repeat(length) { writer.append('A') }
            repeat(uniqueTargetData) { writer.append('Z') }
        }
        val expected = target.toComparableMap()
        assertNotEquals(expected, source.toComparableMap())

        // act
        act(source, target, rootPath, chunkSize)

        // assert
        assertEquals(expected, source.toComparableMap())
    }

    @ParameterizedTest
    @CsvSource(
        textBlock = """
123  , NoN , PIP  , 3
1234 ,abaE , dX4V , 4
1234 ,abba , baab , 4
12345,aaaaK, NdJve, 5
12345,aaaaK, 7tTtM, 5"""
    )
    fun `test DeltaCreator#addData with collisions`(
        repeated: String,
        sourceUnique: String,
        targetUnique: String,
        chunkSize: Int,
    ) {
        // setup
        source.resolve(".txt").newBufferedWriter().use { writer ->
            writer.append(repeated)
            writer.append(sourceUnique)
            writer.append(repeated)
        }
        target.resolve(".txt").newBufferedWriter().use { writer ->
            writer.append(repeated)
            writer.append(targetUnique)
            writer.append(repeated)
        }
        val expected = target.toComparableMap()
        assertNotEquals(expected, source.toComparableMap())

        // act
        act(source, target, rootPath, chunkSize)

        // assert
        assertEquals(expected, source.toComparableMap())
    }
}
