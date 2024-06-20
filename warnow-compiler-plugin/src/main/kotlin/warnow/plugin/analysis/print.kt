package warnow.plugin.analysis

import org.jetbrains.kotlin.types.Variance
import warnow.plugin.resolution.IntermediateType
import warnow.plugin.resolution.IntermediateTypeArgument

internal fun IntermediateType.asString(): String = buildString {
    append(type.asString())

    if (typeArguments.isNotEmpty()) {
        append('<')
        append(typeArguments.joinToString { it.asString() })
        append('>')
    }

    if (nullable) {
        append('?')
    }
}

internal fun IntermediateTypeArgument.asString(): String {
    return if (type == null) {
        "*"
    } else {
        val projection = when (variance) {
            Variance.INVARIANT -> ""
            Variance.IN_VARIANCE -> "in "
            Variance.OUT_VARIANCE -> "out "
        }

        projection + type.asString()
    }
}