@file:JvmName("DeltaTest")

package eu.pieland.delta

import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.io.path.*

fun act(source: Path, target: Path, rootPath: Path, chunkSize: Int) {
    Delta.create(source, target, rootPath.resolve("patch.zip"), chunkSize).inZip().use { Delta.patch(it, source) }
}

fun act(source: Path, target: Path, rootPath: Path) {
    Delta.create(source, target, rootPath.resolve("patch.zip")).inZip().use { Delta.patch(it, source) }
}

@OptIn(ExperimentalPathApi::class)
internal fun Path.toComparableMap(): TreeMap<Path, String> {
    return TreeMap(walk(PathWalkOption.INCLUDE_DIRECTORIES).map { childPath ->
        childPath.relativeTo(this) to if (childPath.isRegularFile()) with(HashAlgorithm.SHA_1) { childPath.computeHash() }
        else childPath.relativeTo(this).invariantSeparatorsPathString.lowercase(Locale.ENGLISH)
    }.toMap())
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.inZip(): ZipInputStream {
    return ZipInputStream(inputStream().buffered())
}

internal fun Map<Path, ByteArray>.createOnFileSystem(tempDir: Path, rootPathString: String): Path {
    return tempDir.resolve(rootPathString).apply {
        createDirectories()
        this@createOnFileSystem.forEach { (name, content) ->
            resolve(name).apply {
                parent.createDirectories()
                writeBytes(content)
            }
        }
    }
}

internal fun unpackZip(javaClass: Class<*>, pathString: String, path: Path) {
    val targetIn = ZipInputStream(javaClass.getResourceAsStream("/$pathString")!!)
    generateSequence { targetIn.nextEntry }.forEach {
        if (it.isDirectory) path.resolve(it.name).createDirectories()
        else path.resolve(it.name).writeBytes(targetIn.readBytes())
    }
}

internal fun Path.newBufferedWriter(): BufferedWriter =
    Files.newBufferedWriter(
        createParentDirectories(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    )
