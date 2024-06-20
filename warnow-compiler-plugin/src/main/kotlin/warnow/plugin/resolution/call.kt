package warnow.plugin.resolution

import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import warnow.plugin.ast.qualifiedName
import warnow.plugin.ast.unparenthesize
import warnow.plugin.marker.Kind
import warnow.plugin.marker.WarnowSyntheticFunction
import warnow.plugin.marker.WarnowSyntheticMember

private val PROPERTY_IDENTIFIER_MEMBER_KINDS = listOf(Kind.PackageAccess, Kind.PackageAccessWithContext, Kind.PackageAccessWithBlockAndContext)

data class ResolvedPropertyCall(val identifier: String, val initializer: KtExpression?, val contextExpression: KtExpression?) {

    val packageName: String get() = identifier.substringAfterLast('.', missingDelimiterValue = "")
    val name: String get() = identifier.substringAfterLast('.').capitalize()
}

class CallResolutionContainer {

    fun resolveStatePropertyCall(callee: KtExpression, isPropertyDefinition: Boolean): ResolvedPropertyCall {
        var identifier: String? = null
        var initializerExpression: KtExpression? = null
        var contextExpression: KtExpression? = null

        val visitor = object : KtTreeVisitorVoid() {
            override fun visitBinaryExpression(expression: KtBinaryExpression) {

                when (expression.operationReference.getReferencedName()) {
                    "within" -> {
                        contextExpression = expression.right?.unparenthesize() ?: return

                        // If the expression is not a property definition the left hand
                        // side must be the identifier expression, otherwise this will
                        // handled by visitBinaryWithTypeRHSExpression().
                        val left = expression.left
                        if (left != null && !isPropertyDefinition) {
                            return visitIdentifierExpression(left)
                        }
                    }
                    "initially" -> {
                        if (isPropertyDefinition) {
                            initializerExpression = expression.right?.unparenthesize() ?: return
                        }
                    }
                }

                when (val left = expression.left) {
                    is KtBinaryExpression,
                    is KtBinaryExpressionWithTypeRHS -> if (isPropertyDefinition) left.accept(this)
                    is KtParenthesizedExpression -> left.unparenthesize()?.accept(this)
                    else -> return
                }
            }

            override fun visitBinaryWithTypeRHSExpression(expression: KtBinaryExpressionWithTypeRHS) {
                if (expression.operationReference.getReferencedName() == "as") {
                    visitIdentifierExpression(expression.left)
                }
            }

            // Trivial case of the property access syntax not allowed
            // for property definition syntax.
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                if (!isPropertyDefinition) {
                    visitIdentifierExpression(expression)
                }
            }

            // Trivial case of the property access syntax not allowed
            // for property definition syntax.
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                if (!isPropertyDefinition) {
                    visitIdentifierExpression(expression)
                }
            }

            private fun visitIdentifierExpression(expression: KtExpression) {
                identifier = when (expression) {
                    is KtNameReferenceExpression -> expression.getReferencedName()
                    is KtDotQualifiedExpression -> qualifiedName(expression)
                    is KtParenthesizedExpression -> return expression.unparenthesize()?.let(::visitIdentifierExpression) ?: Unit
                    else -> return
                }
            }
        }
        callee.parent.accept(visitor)

        return ResolvedPropertyCall(identifier ?: error("missing identifier"), initializerExpression, contextExpression)
    }

    fun resolveValueAccess(expression: KtExpression, bindingContext: BindingContext): ResolvedPropertyCall {
        val identifierBuilder = StringBuilder()
        var contextExpression: KtExpression? = null

        val prepend: StringBuilder.(String) -> StringBuilder = { text -> insert(0, text) }

        if (expression is KtNameReferenceExpression) {
            identifierBuilder.prepend(expression.getReferencedName())
        }

        var current = expression
        while (!current.isValueAccessCall(bindingContext)) {
            when (current) {
                is KtNameReferenceExpression -> {
                    if (current.isPropertyIdentifierMember(bindingContext)) {
                        identifierBuilder.prepend(".").prepend(current.getReferencedName())
                    }
                }
                is KtDotQualifiedExpression -> {
                    val receiverExpression = current.receiverExpression
                    if (receiverExpression.isPropertyIdentifierMember(bindingContext)) {
                        when (receiverExpression) {
                            is KtNameReferenceExpression -> identifierBuilder.prepend(".").prepend(receiverExpression.getReferencedName())
                            is KtCallExpression -> {
                                val calleeExpression = receiverExpression.calleeExpression
                                if (calleeExpression is KtNameReferenceExpression) {
                                    identifierBuilder.prepend(".").prepend(calleeExpression.getReferencedName())
                                }

                                val argumentExpression = receiverExpression.valueArguments.firstOrNull()?.getArgumentExpression()
                                if (argumentExpression !is KtLambdaExpression && contextExpression == null) {
                                    contextExpression = argumentExpression
                                }
                            }
                        }
                    }
                }
                is KtCallExpression -> {
                    if (current.isPropertyIdentifierMember(bindingContext)) {
                        val calleeExpression = current.calleeExpression
                        if (calleeExpression is KtNameReferenceExpression) {
                            identifierBuilder.prepend(".").prepend(calleeExpression.getReferencedName())
                        }

                        val argumentExpression = current.valueArguments.firstOrNull()?.getArgumentExpression()
                        if (argumentExpression !is KtLambdaExpression && contextExpression == null) {
                            contextExpression = argumentExpression
                        }
                    }
                }
            }

            current = current.parentExpression ?: error("cannot resolve context")
        }

        // The current expression will always be a value access call. If the
        // context expression is still null at this point, take context parameter
        // of the value access call into account, if present.
        if (contextExpression == null) {
            val callExpression = current as KtCallExpression

            val argumentExpression = callExpression.valueArguments.firstOrNull()?.getArgumentExpression()

            if (argumentExpression !is KtLambdaExpression) {
                contextExpression = callExpression.valueArguments.firstOrNull()?.getArgumentExpression()
            }
        }

        return ResolvedPropertyCall(identifierBuilder.toString(), /* always */ null, contextExpression)
    }

    private fun KtExpression.isPropertyIdentifierMember(bindingContext: BindingContext): Boolean {
        val syntheticMember = this.getWarnowSyntheticMember(bindingContext)
        return syntheticMember != null && PROPERTY_IDENTIFIER_MEMBER_KINDS.contains(syntheticMember.kind)
    }

    private val KtExpression.parentExpression: KtExpression?
        get() {
            var parent = this.parent
            while (parent != null && parent !is KtExpression) {
                parent = parent.parent
            }
            return parent as? KtExpression
        }

    private fun KtExpression.getWarnowSyntheticMember(bindingContext: BindingContext): WarnowSyntheticMember? {
        val resolvedCall = this.getResolvedCall(bindingContext)
        if (resolvedCall != null) {
            val resultingDescriptor = resolvedCall.resultingDescriptor.original
            if (resultingDescriptor is WarnowSyntheticMember) {
                return resultingDescriptor
            }
        }
        return null
    }

    private fun KtExpression.isValueAccessCall(bindingContext: BindingContext): Boolean {
        if (this !is KtCallExpression) return false

        val resolvedCall = this.getResolvedCall(bindingContext) ?: return false
        val resultingDescriptor = resolvedCall.resultingDescriptor.original

        return resultingDescriptor is WarnowSyntheticFunction
                && (resultingDescriptor.kind == Kind.AccessFunction || resultingDescriptor.kind == Kind.MutateFunction)
    }
}