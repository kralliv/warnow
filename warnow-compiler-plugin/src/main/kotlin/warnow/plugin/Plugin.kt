package warnow.plugin

import com.google.auto.service.AutoService
import com.intellij.mock.MockProject
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import warnow.plugin.analysis.CommandLineAnalysisHandlerExtension
import warnow.plugin.definition.CommandLinePackageFragmentProviderExtension
import warnow.plugin.diagnostic.AbstractStorageComponentContainerContributor
import warnow.plugin.diagnostic.CommandLineStorageComponentContainerContributor
import warnow.plugin.generation.CommandLineExpressionCodegenExtension
import warnow.plugin.log.Logging
import warnow.plugin.resolution.CallResolutionContainer
import warnow.plugin.resolution.CollectorBasedPropertyDefinitionContainer

@AutoService(CommandLineProcessor::class)
class WarnowCommandLineProcessor : CommandLineProcessor {
    /**
     * Just needs to be consistent with the key for DebugLogGradleSubplugin#getCompilerPluginId
     */
    override val pluginId: String = "warnow"

    /**
     * Should match up with the options we return from our DebugLogGradleSubplugin.
     * Should also have matching when branches for each name in the [processOption] function below
     */
    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = "enabled",
            valueDescription = "<true|false>",
            description = "whether to enable the warnow plugin or not"
        )
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        LOG.log { "processor" }
        return when (option.optionName) {
            "enabled" -> configuration.put(KEY_ENABLED, value.toBoolean())
            else -> error("Unexpected config option ${option.optionName}")
        }
    }

    companion object {
        val LOG = Logging.logger { }
    }
}

val KEY_ENABLED = CompilerConfigurationKey<Boolean>("whether the plugin is enabled")

@AutoService(ComponentRegistrar::class)
class WarnowComponentRegistrar : ComponentRegistrar {

    init {
        runSafe {
            val type = ComponentRegistrar::class.java
            LOG.debug { "expected (${type.protectionDomain.codeSource.location})" }
            type.declaredMethods.filter { it.name == "registerProjectComponents" }.forEach { LOG.debug { "  $it" } }
        }

        runSafe {
            val actual = WarnowComponentRegistrar::class.java
            LOG.debug { "actual (${actual.protectionDomain.codeSource.location})" }
            actual.declaredMethods.filter { it.name == "registerProjectComponents" }.forEach { LOG.debug { "  $it" } }
        }
    }

    private inline fun <T> runSafe(block: () -> T): T? {
        return try {
            block()
        } catch (t: Throwable) {
            LOG.error(t) { "failed to read signature" }
            null
        }
    }

    override fun registerProjectComponents(
        project: MockProject,
        configuration: CompilerConfiguration
    ) {
        LOG.log { "registar" }
        if (configuration[KEY_ENABLED] == false) {
            return
        }
        LOG.log { "enabled" }

        val definitionContainer = CollectorBasedPropertyDefinitionContainer()

        AnalysisHandlerExtension.registerExtension(project, CommandLineAnalysisHandlerExtension(definitionContainer))
        PackageFragmentProviderExtension.registerExtension(project, CommandLinePackageFragmentProviderExtension(definitionContainer))
        ExpressionCodegenExtension.registerExtension(project, CommandLineExpressionCodegenExtension(CallResolutionContainer(), optimizeBytecode = true))
        StorageComponentContainerContributor.registerExtension(project, CommandLineStorageComponentContainerContributor(definitionContainer))
    }

    companion object {
        val LOG = Logging.logger { }
    }
}

