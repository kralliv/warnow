package warnow.plugin.diagnostic;

import com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0;
import org.jetbrains.kotlin.diagnostics.Errors;
import org.jetbrains.kotlin.diagnostics.Severity;

public interface WarnowErrors {

    DiagnosticFactory0<PsiElement> MISSING_TYPE_DECLARATION = DiagnosticFactory0.create(Severity.ERROR);
    DiagnosticFactory0<PsiElement> MISSING_INITIALIZER_EXPRESSION = DiagnosticFactory0.create(Severity.ERROR);

    DiagnosticFactory0<PsiElement> DUPLICATED_PROPERTY_NAME = DiagnosticFactory0.create(Severity.ERROR);
    DiagnosticFactory0<PsiElement> CLASHING_PROPERTY_NAME = DiagnosticFactory0.create(Severity.ERROR);

    DiagnosticFactory0<PsiElement> ILLEGAL_PROPERTY_NAME = DiagnosticFactory0.create(Severity.ERROR);
    DiagnosticFactory0<PsiElement> ILLEGAL_OPERATOR = DiagnosticFactory0.create(Severity.ERROR);
    DiagnosticFactory0<PsiElement> ILLEGAL_EXPRESSION = DiagnosticFactory0.create(Severity.ERROR);

    DiagnosticFactory0<PsiElement> CAPTURING_IN_INITIALIZER = DiagnosticFactory0.create(Severity.ERROR);
    DiagnosticFactory0<PsiElement> NON_PUBLIC_CALL_IN_INITIALIZER = DiagnosticFactory0.create(Severity.ERROR);

    @SuppressWarnings("UnusedDeclaration")
    Object _initializer = new Object() {
        {
            Errors.Initializer.initializeFactoryNames(WarnowErrors.class);
        }
    };
}
