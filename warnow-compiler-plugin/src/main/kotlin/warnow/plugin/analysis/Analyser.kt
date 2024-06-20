package warnow.plugin.analysis

import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import warnow.plugin.ast.qualifiedName
import warnow.plugin.ast.unparenthesize
import warnow.plugin.log.Logging
import warnow.plugin.manager.PropertyDefinitionBuilder
import warnow.plugin.manager.PropertyDefinitionCollector

fun KtFile.analyse(propertyDefinitionCollector: PropertyDefinitionCollector) {
    val visitor = WarnowPropertyDefinitionCallVisitor(propertyDefinitionCollector)
    acceptChildren(visitor)
}

private class WarnowPropertyDefinitionCallVisitor(private val propertyDefinitionCollector: PropertyDefinitionCollector) : KtTreeVisitorVoid() {

    override fun visitImportList(importList: KtImportList) {
        importList.imports.forEach { import ->
            val path = import.importPath ?: return@forEach

            val importedName = path.importedName
            if (importedName != null) {
                val type = importedName.identifier

                propertyDefinitionCollector.typeResolutionContainer.registerName(type, path.fqName)
            } else {
                propertyDefinitionCollector.typeResolutionContainer.registerPackage(path.fqName)
            }
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        if (expression.isPropertyDefinition) {
            val lambdaArguments = expression.lambdaArguments

            for (lambdaArgument in lambdaArguments) {
                val lambda = lambdaArgument.getLambdaExpression() ?: continue

                propertyDefinitionCollector.define { builder ->
                    val visitor = WarnowPropertyDefinitionVisitor(builder)
                    lambda.bodyExpression?.acceptChildren(visitor)
                }
            }
        }
    }

    private val KtCallExpression.isPropertyDefinition: Boolean
        get() = when (val calleeExpression = calleeExpression) {
            is KtNameReferenceExpression -> {
                val parent = parent
                if (parent is KtDotQualifiedExpression) {
                    qualifiedName(parent) == "warnow.define"
                } else {
                    calleeExpression.getReferencedName() == "define"
                            && propertyDefinitionCollector.typeResolutionContainer.isPotentiallyResolvable("warnow", "define")
                }
            }
            null -> false
            else -> false.also { LOG.trace { "unhandled kind of callee expression" + calleeExpression::class.qualifiedName } }
        }
}

private class WarnowPropertyDefinitionVisitor(
    private val builder: PropertyDefinitionBuilder
) : KtTreeVisitorVoid() {

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
        visitIdentifierExpression(expression)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        visitIdentifierExpression(expression)
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        expression.left?.accept(this)

        val right = expression.right?.unparenthesize() ?: return

        when (expression.operationReference.getReferencedName()) {
            "initially" -> builder.initializer(right)
            "within" -> builder.context(right)
        }
    }

    override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
        if (expression.operationReference.getReferencedName() == "as") {
            visitIdentifierExpression(expression.left)

            val typeReference = expression.right ?: return

            builder.type(typeReference)
        }
    }

    private fun visitIdentifierExpression(expression: KtExpression) {
        val identifier = when (expression) {
            is KtNameReferenceExpression -> expression.getReferencedName()
            is KtDotQualifiedExpression -> qualifiedName(expression)
            is KtParenthesizedExpression -> return expression.unparenthesize()?.let(::visitIdentifierExpression) ?: Unit
            else -> return
        }

        builder.identifier(identifier)
    }
}

private val LOG = Logging.logger { }