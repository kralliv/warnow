package warnow.plugin.analysis

import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import warnow.plugin.log.Logging
import warnow.plugin.manager.PropertyDefinitionBuilder
import warnow.plugin.resolution.IntermediatePropertyDefinition
import warnow.plugin.resolution.IntermediateType
import warnow.plugin.resolution.IntermediateTypes

class PropertyDefinitionBuilderImpl(
    private val typeResolutionContainer: IntermediateTypeResolutionContainer
) : PropertyDefinitionBuilder {

    private var identifier: String? = null
    private var type: IntermediateType? = null
    private var initializer: KtExpression? = null
    private var context: KtExpression? = null

    override fun identifier(identifier: String) {
        if (this.identifier == null) {
            this.identifier = identifier
        }
    }

    override fun type(type: KtTypeReference) {
        if (this.type == null) {
            this.type = typeResolutionContainer.resolveType(type)
        }
    }

    override fun initializer(expression: KtExpression) {
        if (this.initializer == null) {
            this.initializer = expression
        }
    }

    override fun context(expression: KtExpression) {
        if (this.context == null) {
            this.context = expression
        }
    }

    fun build(): IntermediatePropertyDefinition? {
        LOG.debug { "building property $identifier $type $initializer $context" }

        val identifier = identifier ?: return null
        val type = type ?: IntermediateTypes.Unit

        return IntermediatePropertyDefinition(
            identifier,
            type
        )
    }

    companion object {
        private val LOG = Logging.logger { }
    }
}