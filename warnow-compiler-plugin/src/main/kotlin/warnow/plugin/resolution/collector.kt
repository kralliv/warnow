package warnow.plugin.resolution

import warnow.plugin.analysis.IntermediateTypeResolutionContainer
import warnow.plugin.analysis.PropertyDefinitionBuilderImpl
import warnow.plugin.analysis.asString
import warnow.plugin.log.Logging
import warnow.plugin.manager.PropertyDefinitionBuilder
import warnow.plugin.manager.PropertyDefinitionCollector

class PropertyDefinitionCollectorImpl : PropertyDefinitionCollector {

    override val typeResolutionContainer = IntermediateTypeResolutionContainer()

    override fun define(block: (PropertyDefinitionBuilder) -> Unit) {
        val builder = PropertyDefinitionBuilderImpl(typeResolutionContainer)

        block(builder)

        val result = builder.build()
        if (result != null) {
            register(result)
        }
    }

    private val properties = mutableListOf<IntermediatePropertyDefinition>()

    fun register(propertyDefinition: IntermediatePropertyDefinition) {
        properties += propertyDefinition

        LOG.debug { "property ${propertyDefinition.identifier} as ${propertyDefinition.type.asString()}" }
    }

    fun getProperties(): List<IntermediatePropertyDefinition> = properties
    fun getCollectedProperties(): Map<String, IntermediatePropertyDefinition> = properties.associateBy { it.identifier }

    companion object {
        private val LOG = Logging.logger { }
    }
}