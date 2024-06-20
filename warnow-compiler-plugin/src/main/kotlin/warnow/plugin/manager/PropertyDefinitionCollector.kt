package warnow.plugin.manager

import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import warnow.plugin.analysis.IntermediateTypeResolutionContainer

interface PropertyDefinitionCollector {

    val typeResolutionContainer: IntermediateTypeResolutionContainer

    fun define(block: (PropertyDefinitionBuilder) -> Unit)
}

interface PropertyDefinitionBuilder {

    fun identifier(identifier: String)
    fun type(type: KtTypeReference)

    fun initializer(expression: KtExpression)
    fun context(expression: KtExpression)
}
