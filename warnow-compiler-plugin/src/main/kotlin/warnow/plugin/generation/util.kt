package warnow.plugin.generation

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtConstructorDelegationReferenceExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.doNotAnalyze

fun getLineNumberForElement(statement: PsiElement, markEndOffset: Boolean): Int? {
    val file = statement.containingFile
    if (file is KtFile && file.doNotAnalyze != null) {
        return null
    }

    if (statement is KtConstructorDelegationReferenceExpression && statement.textLength == 0) {
        // PsiElement for constructor delegation reference is always generated, so we shouldn't mark it's line number if it's empty
        return null
    }

    val document = file.viewProvider.document
    return document?.getLineNumber(if (markEndOffset) statement.textRange.endOffset else statement.textOffset)?.plus(1)
}