package eu.pieland.delta

import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption


internal const val EOF: UByte = 0u
internal const val DATA_MAX = 246
internal const val DATA_BYTE_MAX = 255
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

internal fun Path.inFileChannel(): SeekableByteChannel = FileChannel.open(this, StandardOpenOption.READ)
