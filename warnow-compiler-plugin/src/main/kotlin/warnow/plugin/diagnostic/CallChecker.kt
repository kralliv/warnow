package warnow.plugin.diagnostic

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.codegen.isJvmStaticInObjectOrClassOrInterface
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.reportFromPlugin
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import warnow.plugin.ast.qualifiedName
import warnow.plugin.ast.unparenthesize
import warnow.plugin.marker.Kind
import warnow.plugin.marker.WarnowSyntheticFunction

private data class PropertyDefinitionCheckerContext(
    val callContext: CallCheckerContext,
    val duplicatedPropertyNames: Set<String>,
    val clashingPropertyNames: Set<String>
) {

    fun <D : Diagnostic> reportWarnowError(diagnostic: D) {
        callContext.trace.reportFromPlugin(diagnostic, WarnowDefaultErrorMessages)
    }

    fun isDuplicatedPropertyName(name: String): Boolean {
        return duplicatedPropertyNames.contains(name)
    }

    fun isClashingPropertyName(name: String): Boolean {
        return clashingPropertyNames.contains(name)
    }
}

abstract class AbstractPropertyDefinitionCallChecker : CallChecker {

    protected abstract fun getDuplicatedPropertyNames(): Set<String>
    protected abstract fun getClashingPropertyNames(): Set<String>

    final override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {
        val resultingDescriptor = resolvedCall.resultingDescriptor.original

        if (resultingDescriptor is WarnowSyntheticFunction && resultingDescriptor.kind == Kind.DefineFunction) {
            val duplicatedPropertyNames = getDuplicatedPropertyNames()
            val clashingPropertyNames = getClashingPropertyNames()

            val checkerContext = PropertyDefinitionCheckerContext(context, duplicatedPropertyNames, clashingPropertyNames)

            val callExpression = resolvedCall.call.callElement as KtCallExpression
            val bodyExpression = callExpression.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression ?: return

            bodyExpression.children.filterIsInstance<KtExpression>().forEach { checkExpression(it, reportOn, checkerContext) }
        }
    }

    private fun checkExpression(expression: KtExpression, reportOn: PsiElement, context: PropertyDefinitionCheckerContext) {
        when (expression) {
            is KtParenthesizedExpression -> expression.unparenthesize()?.let { checkExpression(it, reportOn, context) }
            is KtNameReferenceExpression, is KtDotQualifiedExpression -> {
                checkIdentifierExpression(expression, reportOn, context)

                context.reportWarnowError(WarnowErrors.MISSING_TYPE_DECLARATION.on(reportOn))
                context.reportWarnowError(WarnowErrors.MISSING_INITIALIZER_EXPRESSION.on(reportOn))
            }
            is KtBinaryExpressionWithTypeRHS -> {
                context.reportWarnowError(WarnowErrors.MISSING_INITIALIZER_EXPRESSION.on(reportOn))

                if (expression.operationReference.getReferencedName() != "as") {
                    context.reportWarnowError(WarnowErrors.MISSING_TYPE_DECLARATION.on(reportOn))
                    context.reportWarnowError(WarnowErrors.ILLEGAL_OPERATOR.on(expression.operationReference))
                } else {
                    checkTypeDeclaration(expression, reportOn, context)
                }
            }
            is KtBinaryExpression -> {
                when (expression.operationReference.getReferencedName()) {
                    "within" -> checkContextExpression(expression, reportOn, context)
                    "initially" -> checkInitialisationExpression(expression, reportOn, context)
                    else -> {
                        context.reportWarnowError(WarnowErrors.MISSING_INITIALIZER_EXPRESSION.on(reportOn))
                        context.reportWarnowError(WarnowErrors.ILLEGAL_OPERATOR.on(expression.operationReference))
                    }
                }
            }
            else -> {
                context.reportWarnowError(WarnowErrors.MISSING_TYPE_DECLARATION.on(reportOn))
                context.reportWarnowError(WarnowErrors.MISSING_INITIALIZER_EXPRESSION.on(reportOn))
                context.reportWarnowError(WarnowErrors.ILLEGAL_PROPERTY_NAME.on(expression))
            }
        }
    }

    private fun checkContextExpression(
        expression: KtBinaryExpression,
        reportOn: PsiElement,
        context: PropertyDefinitionCheckerContext
    ) {
        val left = expression.left
        when {
            left == null -> {
                context.reportWarnowError(WarnowErrors.MISSING_INITIALIZER_EXPRESSION.on(reportOn))
            }
            left !is KtBinaryExpression -> {
                context.reportWarnowError(WarnowErrors.MISSING_INITIALIZER_EXPRESSION.on(reportOn))
                context.reportWarnowError(WarnowErrors.ILLEGAL_EXPRESSION.on(left))
            }
            left.operationReference.getReferencedName() != "initially" -> {
                context.reportWarnowError(WarnowErrors.MISSING_INITIALIZER_EXPRESSION.on(reportOn))
                context.reportWarnowError(WarnowErrors.ILLEGAL_OPERATOR.on(expression.operationReference))
            }
            else -> checkInitialisationExpression(left, reportOn, context)
        }
    }

    private fun checkInitialisationExpression(
        expression: KtBinaryExpression,
        reportOn: PsiElement,
        context: PropertyDefinitionCheckerContext
    ) {
        val left = expression.left
        when {
            left == null -> {
                context.reportWarnowError(WarnowErrors.MISSING_TYPE_DECLARATION.on(reportOn))
            }
            left !is KtBinaryExpressionWithTypeRHS -> {
                context.reportWarnowError(WarnowErrors.MISSING_TYPE_DECLARATION.on(reportOn))
                context.reportWarnowError(WarnowErrors.ILLEGAL_EXPRESSION.on(left))
            }
            left.operationReference.getReferencedName() != "as" -> {
                context.reportWarnowError(WarnowErrors.MISSING_TYPE_DECLARATION.on(reportOn))
                context.reportWarnowError(WarnowErrors.ILLEGAL_OPERATOR.on(expression.operationReference))
            }
            else -> checkTypeDeclaration(left, reportOn, context)
        }

        val right = expression.right
        if (right != null) {
            val checker = InitialisationCapturingChecker(context)

            right.accept(checker)
        }
    }

    private fun checkTypeDeclaration(expression: KtBinaryExpressionWithTypeRHS, reportOn: PsiElement, context: PropertyDefinitionCheckerContext) {
        checkIdentifierExpression(expression.left, reportOn, context)
    }

    private fun checkIdentifierExpression(expression: KtExpression, reportOn: PsiElement, context: PropertyDefinitionCheckerContext) {
        if (!expression.isPureIdentifier()) {
            context.reportWarnowError(WarnowErrors.ILLEGAL_PROPERTY_NAME.on(expression))
        } else {
            val propertyName = qualifiedName(expression)
            when {
                context.isDuplicatedPropertyName(propertyName) -> context.reportWarnowError(WarnowErrors.DUPLICATED_PROPERTY_NAME.on(reportOn))
                context.isClashingPropertyName(propertyName) -> context.reportWarnowError(WarnowErrors.CLASHING_PROPERTY_NAME.on(reportOn))
            }
        }
    }

    private fun KtExpression.isPureIdentifier(): Boolean {
        return when (this) {
            is KtNameReferenceExpression -> true
            is KtDotQualifiedExpression -> {
                val receiver = this.receiverExpression
                val selector = this.selectorExpression

                receiver.isPureIdentifier() && (selector == null || selector.isPureIdentifier())
            }
            else -> false
        }
    }

    private class InitialisationCapturingChecker(private val context: PropertyDefinitionCheckerContext) : KtTreeVisitorVoid() {

        private val knownCalls = mutableSetOf<ResolvedCall<*>>()

        override fun visitExpression(expression: KtExpression) {
            super.visitExpression(expression)

            val resolvedCall = expression.getResolvedCall(context.callContext.trace.bindingContext)
            if (resolvedCall != null) {
                if (!knownCalls.add(resolvedCall)) {
                    return
                }

                val resultingDescriptor = resolvedCall.resultingDescriptor

                val containingDeclaration = resultingDescriptor.containingDeclaration
                if (!DescriptorUtils.isStaticDeclaration(resultingDescriptor)
                    && !isObjectOrCompanionObjectDescriptor(containingDeclaration)
                    && !resultingDescriptor.isJvmStaticInObjectOrClassOrInterface()
                ) {
                    context.reportWarnowError(WarnowErrors.CAPTURING_IN_INITIALIZER.on(expression))
                }

                if (resultingDescriptor.visibility != Visibilities.PUBLIC) {
                    context.reportWarnowError(WarnowErrors.NON_PUBLIC_CALL_IN_INITIALIZER.on(expression))
                }
            }
        }

        private fun isObjectOrCompanionObjectDescriptor(declarationDescriptor: DeclarationDescriptor): Boolean {
            return declarationDescriptor is ClassDescriptor && (declarationDescriptor.kind == ClassKind.OBJECT || declarationDescriptor.isCompanionObject)
        }
    }
}