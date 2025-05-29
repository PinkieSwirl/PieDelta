package eu.pieland.delta


import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

private const val MAX_TABLE_SIZE = 1 shl 30

internal class DeltaCreatorHashes(initialSize: Int = 256, private val loadFactor: Float = 0.8f) {
    private var size: Int = 0

    private var keyTable = IntArray(initialSize)
    private var valueTable = LongArray(initialSize)

    private var threshold = (initialSize * loadFactor).toInt()
    private var mask = initialSize - 1

    init {
        require(loadFactor > 0f && loadFactor < 1f) { "'loadFactor' must be > 0 and < 1: $loadFactor" }
        require(initialSize <= MAX_TABLE_SIZE && initialSize > 0 && ((initialSize and (initialSize - 1)) == 0)) {
            "'initialCapacity' must be a power of 2 > 0 and <= $MAX_TABLE_SIZE: $initialSize"
        }
    }

    fun indexOf(hash: DeltaCreatorChecksum): Long {
        val keyIndex = locateKey(hash.checksum)
        return if (keyIndex >= 0) valueTable[keyIndex] else -1L
    }

    internal fun putIfAbsent(key: Int, index: Long) {
        val keyIndex = locateKey(key)
        if (keyIndex >= 0) return
        val newKeyIndex = -(keyIndex + 1)
        keyTable[newKeyIndex] = key
        valueTable[newKeyIndex] = index
        if (++size == threshold) resize(keyTable.size shl 1)
    }

    private fun locateKey(key: Int): Int {
        var keyIndex = key and mask
        while (true) {
            val other = keyTable[keyIndex]
            if (other == 0) return -(keyIndex + 1)
            if (other == key) return keyIndex
            keyIndex = keyIndex + 1 and mask
        }
    }

    private fun resize(newSize: Int) {
        threshold = (newSize * loadFactor).toInt()
        mask = newSize - 1

        val oldKeyTable = keyTable
        val oldValueTable = valueTable

        keyTable = IntArray(newSize)
        valueTable = LongArray(newSize)

        oldKeyTable.forEachIndexed { i, key -> if (key != 0) putResize(key, oldValueTable[i]) }
    }

    private fun putResize(key: Int, value: Long) {
        var keyIndex = key and mask
        while (keyTable[keyIndex] != 0) {
            keyIndex = keyIndex + 1 and mask
        }
        keyTable[keyIndex] = key
        valueTable[keyIndex] = value
    }

    companion object {
        fun SeekableByteChannel.computeHashesFromByteSource(chunkSize: Int): DeltaCreatorHashes {
            val hashes = DeltaCreatorHashes()
            val buffer = ByteBuffer.allocate(chunkSize * 2)
            var checksumIndex = 0L
            while (read(buffer) != -1) {
                buffer.flip()
                if (buffer.remaining() < chunkSize) continue
                while (buffer.remaining() >= chunkSize) {
                    hashes.putIfAbsent(buffer.computeHashInternal(chunkSize).checksum, checksumIndex)
                    checksumIndex += chunkSize
                }
                buffer.compact()
            }
            return hashes
        }
    }
}

internal fun ByteBuffer.computeHash(chunkSize: Int): DeltaCreatorChecksum {
    mark()
    val checksum = computeHashInternal(chunkSize)
    reset()
    return checksum
}

internal fun ByteBuffer.computeHashInternal(chunkSize: Int): DeltaCreatorChecksum {
    var high = 0
    var low = 0
    repeat(chunkSize) {
        low += get().substitute()
        high += low
    }
    return DeltaCreatorChecksum((high and MASK_I) shl SHIFT or (low and MASK_I))
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Byte.substitute() = SBOX[this + OFFSET]

/** Random numbers generated using SLIB's pseudo-random number generator. */
@JvmField
@Suppress("MagicNumber") // Array of constants
internal val SBOX: IntArray = intArrayOf(
    0xbcd1, 0xbb65, 0x42c2, 0xdffe, 0x9666, 0x431b, 0x8504, 0xeb46, 0x6379, 0xd460, 0xcf14, 0x53cf, 0xdb51, 0xdb08,
    0x12c8, 0xf602, 0xe766, 0x2394, 0x250d, 0xdcbb, 0xa678, 0x02af, 0xa5c6, 0x7ea6, 0xb645, 0xcb4d, 0xc44b, 0xe5dc,
    0x9fe6, 0x5b5c, 0x35f5, 0x701a, 0x220f, 0x6c38, 0x1a56, 0x4ca3, 0xffc6, 0xb152, 0x8d61, 0x7a58, 0x9025, 0x8b3d,
    0xbf0f, 0x95a3, 0xe5f4, 0xc127, 0x3bed, 0x320b, 0xb7f3, 0x6054, 0x333c, 0xd383, 0x8154, 0x5242, 0x4e0d, 0x0a94,
    0x7028, 0x8689, 0x3a22, 0x0980, 0x1847, 0xb0f1, 0x9b5c, 0x4176, 0xb858, 0xd542, 0x1f6c, 0x2497, 0x6a5a, 0x9fa9,
    0x8c5a, 0x7743, 0xa8a9, 0x9a02, 0x4918, 0x438c, 0xc388, 0x9e2b, 0x4cad, 0x01b6, 0xab19, 0xf777, 0x365f, 0x1eb2,
    0x091e, 0x7bf8, 0x7a8e, 0x5227, 0xeab1, 0x2074, 0x4523, 0xe781, 0x01a3, 0x163d, 0x3b2e, 0x287d, 0x5e7f, 0xa063,
    0xb134, 0x8fae, 0x5e8e, 0xb7b7, 0x4548, 0x1f5a, 0xfa56, 0x7a24, 0x900f, 0x42dc, 0xcc69, 0x02a0, 0x0b22, 0xdb31,
    0x71fe, 0x0c7d, 0x1732, 0x1159, 0xcb09, 0xe1d2, 0x1351, 0x52e9, 0xf536, 0x5a4f, 0xc316, 0x6bf9, 0x8994, 0xb774,
    0x5f3e, 0xf6d6, 0x3a61, 0xf82c, 0xcc22, 0x9d06, 0x299c, 0x09e5, 0x1eec, 0x514f, 0x8d53, 0xa650, 0x5c6e, 0xc577,
    0x7958, 0x71ac, 0x8916, 0x9b4f, 0x2c09, 0x5211, 0xf6d8, 0xcaaa, 0xf7ef, 0x287f, 0x7a94, 0xab49, 0xfa2c, 0x7222,
    0xe457, 0xd71a, 0x00c3, 0x1a76, 0xe98c, 0xc037, 0x8208, 0x5c2d, 0xdfda, 0xe5f5, 0x0b45, 0x15ce, 0x8a7e, 0xfcad,
    0xaa2d, 0x4b5c, 0xd42e, 0xb251, 0x907e, 0x9a47, 0xc9a6, 0xd93f, 0x085e, 0x35ce, 0xa153, 0x7e7b, 0x9f0b, 0x25aa,
    0x5d9f, 0xc04d, 0x8a0e, 0x2875, 0x4a1c, 0x295f, 0x1393, 0xf760, 0x9178, 0x0f5b, 0xfa7d, 0x83b4, 0x2082, 0x721d,
    0x6462, 0x0368, 0x67e2, 0x8624, 0x194d, 0x22f6, 0x78fb, 0x6791, 0xb238, 0xb332, 0x7276, 0xf272, 0x47ec, 0x4504,
    0xa961, 0x9fc8, 0x3fdc, 0xb413, 0x007a, 0x0806, 0x7458, 0x95c6, 0xccaa, 0x18d6, 0xe2ae, 0x1b06, 0xf3f6, 0x5050,
    0xc8e8, 0xf4ac, 0xc04c, 0xf41c, 0x992f, 0xae44, 0x5f1b, 0x1113, 0x1738, 0xd9a8, 0x19ea, 0x2d33, 0x9698, 0x2fe9,
    0x323f, 0xcde2, 0x6d71, 0xe37d, 0xb697, 0x2c4f, 0x4373, 0x9102, 0x075d, 0x8e25, 0x1672, 0xec28, 0x6acb, 0x86cc,
    0x186e, 0x9414, 0xd674, 0xd1a5,
)

private const val MASK_I = 0xffff
private const val SHIFT = 16
private const val OFFSET = 128

@JvmInline
internal value class DeltaCreatorChecksum(internal val checksum: Int = 0) {

    internal fun increment(old: Byte, new: Byte, chunkSize: Int): DeltaCreatorChecksum {
        val oldSubstituted = old.substitute()
        val low = (((checksum and MASK_I) - oldSubstituted) + new.substitute()) and MASK_I
        val high = (((checksum shr SHIFT) - (oldSubstituted * chunkSize)) + low) and MASK_I
        return DeltaCreatorChecksum(high shl SHIFT or low)
    }

}
