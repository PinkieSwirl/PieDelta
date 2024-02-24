package eu.pieland.delta

import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.arbitraries.IntegerArbitrary
import net.jqwik.kotlin.api.any
import net.jqwik.kotlin.api.array
import kotlin.test.assertSame

internal class InternalsPropertyTest {

    @Provide
    fun ints(): IntegerArbitrary = Int.any()

    @Provide
    fun byteArrays(): Arbitrary<ByteArray> = Byte.any().array()

    @Property
    fun `test NopOutputStream write Int returns Unit`(@ForAll("ints") int: Int) {
        assertSame(Unit, NopOutputStream.write(int))
    }

    @Property
    fun `test NopOutputStream write ByteArray returns Unit`(@ForAll("byteArrays") byteArray: ByteArray) {
        assertSame(Unit, NopOutputStream.write(byteArray))
    }

    @Property
    fun `test NopOutputStream write ByteArray with offset and length returns Unit`(
        @ForAll("byteArrays") byteArray: ByteArray,
        @ForAll("ints") off: Int,
        @ForAll("ints") len: Int
    ) {
        assertSame(Unit, NopOutputStream.write(byteArray, off, len))
    }
}
