package warnow.plugin.ast

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import warnow.plugin.log.Logging

internal fun qualifiedName(expression: KtExpression): String {
    return when (expression) {
        is KtNameReferenceExpression -> expression.getReferencedName()
        is KtDotQualifiedExpression -> qualifiedName(expression)
        else -> ""
    }
}

internal fun qualifiedName(expression: KtDotQualifiedExpression): String = buildString {
    when (val receiverExpression = expression.receiverExpression) {
        is KtDotQualifiedExpression -> append(qualifiedName(receiverExpression))
        is KtNameReferenceExpression -> append(receiverExpression.getReferencedName())
        else -> LOG.warn { "unhandled receiver: ${receiverExpression::class.java.name}" }
    }

    append('.')

    val selectorExpression = expression.selectorExpression
    when {
        selectorExpression is KtNameReferenceExpression -> append(selectorExpression.getReferencedName())
        selectorExpression is KtCallExpression -> {
            when (val calleeExpression = selectorExpression.calleeExpression) {
                is KtNameReferenceExpression -> append(calleeExpression.getReferencedName())
                null -> {; }
                else -> LOG.warn { "unhandled callee: ${calleeExpression::class.java.name}" }
            }
        }
        selectorExpression != null -> LOG.warn { "unhandled selector: ${selectorExpression::class.java.name}" }
    }
}

internal fun KtExpression.unparenthesize(): KtExpression? {
    return when (this) {
        is KtParenthesizedExpression -> this.expression?.unparenthesize()
        else -> this
    }
}

private val LOG = Logging.logger { }