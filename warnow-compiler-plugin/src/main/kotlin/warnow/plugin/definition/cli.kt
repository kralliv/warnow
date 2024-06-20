package warnow.plugin.definition

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import warnow.plugin.resolution.AbstractPropertyDefinitionContainer

class CommandLinePackageFragmentProviderExtension(
    private val resolutionContainer: AbstractPropertyDefinitionContainer
) : AbstractPackageFragmentProviderExtension() {

    override fun getResolutionContainer(project: Project): AbstractPropertyDefinitionContainer {
        return resolutionContainer
    }

    override fun shouldIgnoreModule(module: ModuleDescriptor): Boolean {
        return false
    }
}