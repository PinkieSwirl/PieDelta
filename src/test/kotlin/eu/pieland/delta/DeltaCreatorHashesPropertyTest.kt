package eu.pieland.delta

import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.arbitraries.FloatArbitrary
import net.jqwik.kotlin.api.any
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals


internal class DeltaCreatorHashesPropertyTest {

    private val powerOfTwos = IntArray(31) { 1 shl it }.toSet()

    @Provide
    fun loadFactor(): FloatArbitrary = Float.any().between(0f, false, 1f, false)

    @Provide
    fun invalidLoadFactor(): Arbitrary<Float> = Float.any().filter { it <= 0f || it >= 1f }

    @Provide
    fun powerOfTwo(): Arbitrary<Int> = Int.any(0..29).map { 1 shl it }

    @Provide
    fun notPowerOfTwo(): Arbitrary<Int> = Int.any().filter { it !in powerOfTwos }

    @Property
    fun `valid initialization`(@ForAll("powerOfTwo") initialSize: Int, @ForAll("loadFactor") loadFactor: Float) {
        DeltaCreatorHashes(initialSize = initialSize, loadFactor = loadFactor)
    }

    @Property
    fun `initialSize not a power of 2`(
        @ForAll("notPowerOfTwo") initialSize: Int,
        @ForAll("loadFactor") loadFactor: Float,
    ) {
        val e = assertThrows<IllegalArgumentException> {
            DeltaCreatorHashes(initialSize = initialSize, loadFactor = loadFactor)
        }
        assertEquals("'initialCapacity' must be a power of 2 > 0 and <= 536870912: $initialSize", e.message)
    }

    @Property
    fun `loadFactor not greater than 0f and lower than 1f`(
        @ForAll("powerOfTwo") initialSize: Int,
        @ForAll("invalidLoadFactor") loadFactor: Float,
    ) {
        val e = assertThrows<IllegalArgumentException> {
            DeltaCreatorHashes(initialSize = initialSize, loadFactor = loadFactor)
        }
        assertEquals("'loadFactor' must be > 0 and < 1: $loadFactor", e.message)
    }
}
