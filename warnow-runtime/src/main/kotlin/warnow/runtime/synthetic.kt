package warnow.runtime

import warnow.Context
import warnow.WarnowProperty

fun <T> obtainDelegateWithin(
    name: String,
    initializer: Initializer<T>,
    context: Context
): WarnowProperty<T> {
    return context.runtime.getProperty(name, initializer)
}

fun <T> getValueWithin(
    name: String,
    initializer: Initializer<T>,
    context: Context
): T {
    return context.runtime.getProperty(name, initializer).getValue()
}

fun <T> setValueWithin(
    name: String,
    value: T,
    initializer: Initializer<T>,
    context: Context
) {
    return context.runtime.getProperty(name, initializer).setValue(value)
}