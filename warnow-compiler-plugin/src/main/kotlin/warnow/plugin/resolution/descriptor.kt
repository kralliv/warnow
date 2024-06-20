package warnow.plugin.resolution

import org.jetbrains.kotlin.storage.NotNullLazyValue
import org.jetbrains.kotlin.types.SimpleType

data class StateProperty(
    val name: String,
    val lazyType: NotNullLazyValue<SimpleType>
) {

    val type: SimpleType
        get() = lazyType()
}

data class StatePackage(
    val name: String,
    val properties: List<StateProperty>,

    val packages: List<StatePackage>
) {

    val isTopLevel: Boolean
        get() = name == ""
}

