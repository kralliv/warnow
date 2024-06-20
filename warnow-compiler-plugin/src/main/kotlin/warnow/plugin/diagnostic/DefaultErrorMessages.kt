package warnow.plugin.diagnostic

import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticFactoryToRendererMap

object WarnowDefaultErrorMessages : DefaultErrorMessages.Extension {

    private val MAP = DiagnosticFactoryToRendererMap("Warnow")
    override fun getMap() = MAP

    init {
        MAP.put(WarnowErrors.MISSING_TYPE_DECLARATION, "Missing type declaration")
        MAP.put(WarnowErrors.MISSING_INITIALIZER_EXPRESSION, "Missing initializer expression")

        MAP.put(WarnowErrors.DUPLICATED_PROPERTY_NAME, "Redefinition of property name: property cannot be declared more than once")
        MAP.put(WarnowErrors.CLASHING_PROPERTY_NAME, "Property name may not be an sub identifier of another property")

        MAP.put(WarnowErrors.ILLEGAL_PROPERTY_NAME, "Property name may only consist out of identifiers and dots")
        MAP.put(WarnowErrors.ILLEGAL_OPERATOR, "Illegal operator")
        MAP.put(WarnowErrors.ILLEGAL_EXPRESSION, "Illegal expression")

        MAP.put(WarnowErrors.CAPTURING_IN_INITIALIZER, "Cannot capture local/non-static variable or function in initializer")
        MAP.put(WarnowErrors.NON_PUBLIC_CALL_IN_INITIALIZER, "Cannot access non-public variable or function in initializer")
    }
}