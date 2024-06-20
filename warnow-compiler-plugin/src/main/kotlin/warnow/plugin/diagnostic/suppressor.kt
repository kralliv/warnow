package warnow.plugin.diagnostic

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.diagnostics.DiagnosticSuppressor

class SyntaxRelatedErrorsDiagnosticSuppressor : DiagnosticSuppressor {

    override fun isSuppressed(diagnostic: Diagnostic): Boolean {

        // TODO narrow down to casts within define calls
        return diagnostic.factory == Errors.CAST_NEVER_SUCCEEDS
    }

    private fun PsiElement.isWithinDefinitionFunction(): Boolean {
        var parentExpression = this.parent as? KtExpression ?: return false

        while (parentExpression !is KtCallExpression) {
            parentExpression = parentExpression.parent as? KtExpression ?: return false
        }

        return false
    }
}