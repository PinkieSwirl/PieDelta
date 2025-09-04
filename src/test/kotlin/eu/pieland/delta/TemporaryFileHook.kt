package eu.pieland.delta

import net.jqwik.api.Tuple
import net.jqwik.api.lifecycle.*
import net.jqwik.api.lifecycle.Store.CloseOnReset
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

internal class TemporaryFileHook : ResolveParameterHook {
    override fun resolve(
        parameterContext: ParameterResolutionContext, lifecycleContext: LifecycleContext,
    ): Optional<ResolveParameterHook.ParameterSupplier> {
        return if (!parameterContext.typeUsage().isOfType(Path::class.java)) Optional.empty()
        else Optional.of(ResolveParameterHook.ParameterSupplier { _ -> getTemporaryFileForTry() })
    }

    private fun getTemporaryFileForTry(): Path =
        Store.getOrCreate(STORE_IDENTIFIER, Lifespan.TRY) { ClosingFile(createTempFile()) }.get().file

    private fun createTempFile(): Path = Files.createTempDirectory("deltaPropertyTest")

    private class ClosingFile(val file: Path) : CloseOnReset {

        @OptIn(ExperimentalPathApi::class)
        override fun close() {
            try {
                file.deleteRecursively()
            } catch (@Suppress("SwallowedException") _: IOException) {
                // NOP, since we don't care for not deleted temporary files
            }
        }
    }

    companion object {
        private val STORE_IDENTIFIER: Tuple.Tuple2<Class<TemporaryFileHook>, String> =
            Tuple.of(TemporaryFileHook::class.java, "temporary directories")
    }
}
