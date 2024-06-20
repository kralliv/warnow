package warnow.plugin.diagnostic

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.resolve.TargetPlatform
import warnow.plugin.resolution.AbstractPropertyDefinitionContainer

abstract class AbstractStorageComponentContainerContributor : StorageComponentContainerContributor {

    protected abstract fun getResolutionContainer(project: Project): AbstractPropertyDefinitionContainer

    final override fun registerModuleComponents(container: StorageComponentContainer, platform: TargetPlatform, moduleDescriptor: ModuleDescriptor) {
        val resolutionContainer = lazy {
            val project = container.get<Project>()
            getResolutionContainer(project)
        }

        container.useInstance(CommandLinePropertyDefinitionCallChecker(resolutionContainer))
    }
}