package warnow.plugin.diagnostic

import com.intellij.openapi.project.Project
import warnow.plugin.resolution.AbstractPropertyDefinitionContainer

class CommandLinePropertyDefinitionCallChecker(
    lazyResolutionContainer: Lazy<AbstractPropertyDefinitionContainer>
) : AbstractPropertyDefinitionCallChecker() {

    private val resolutionContainer by lazyResolutionContainer

    override fun getDuplicatedPropertyNames(): Set<String> {
        return resolutionContainer.getDuplicatedPropertyNames()
    }

    override fun getClashingPropertyNames(): Set<String> {
        return resolutionContainer.getClashingPropertyNames()
    }
}

class CommandLineStorageComponentContainerContributor(
    private val resolutionContainer: AbstractPropertyDefinitionContainer
) : AbstractStorageComponentContainerContributor() {

    override fun getResolutionContainer(project: Project): AbstractPropertyDefinitionContainer = resolutionContainer
}
