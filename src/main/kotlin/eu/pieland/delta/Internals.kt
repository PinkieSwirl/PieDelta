package eu.pieland.delta

import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlin.io.path.inputStream


internal const val EOF: UByte = 0u
internal const val DATA_MAX = 246
internal const val DATA_BYTE_MAX = 256
internal const val DATA_SHORT_MAX = 65535
internal const val DATA_USHORT = 247
internal const val DATA_INT = 248
internal const val COPY_USHORT_UBYTE = 249
internal const val COPY_USHORT_USHORT = 250
internal const val COPY_USHORT_INT = 251
internal const val COPY_INT_UBYTE = 252
internal const val COPY_INT_USHORT = 253
internal const val COPY_INT_INT = 254
internal const val COPY_LONG_INT = 255

internal inline fun <reified F : T, reified S : T, T> Iterable<T>.partitionByType(): Pair<List<F>, List<S>> {
    val first = ArrayList<F>()
    val second = ArrayList<S>()
    forEach { element ->
        if (element is F) {
            first.add(element)
        } else {
            second.add(element as S)
        }
    }
    return Pair(first, second)
}

internal fun Path.computeSha1(): String {
    val md = MessageDigest.getInstance("SHA-1")
    val output = DigestInputStream(inputStream().buffered(), md)
    output.use { it.copyTo(NopOutputStream) }
    return output.messageDigest.digest().hex()
}

private object NopOutputStream : OutputStream() {
    override fun write(b: Int) = Unit
    override fun write(b: ByteArray) = Unit
    override fun write(b: ByteArray, off: Int, len: Int) = Unit
}

@OptIn(ExperimentalUnsignedTypes::class)
internal fun ByteArray.hex(): String {
    return asUByteArray().joinToString("") { it.toString(radix = 16).padStart(2, '0') }
}

internal fun Path.inFileChannel(): SeekableByteChannel = FileChannel.open(this, StandardOpenOption.READ)
