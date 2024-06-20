package warnow.plugin

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal inline fun <T> List<T>.forEachUntilLast(operation: (T) -> Unit) {
    val lastIndex = lastIndex
    forEachIndexed { i, t ->
        if (i < lastIndex) {
            operation(t)
        }
    }
}

internal inline fun <T> List<T>.forEachExceptFirstAndLast(operation: (T) -> Unit) {
    val lastIndex = lastIndex
    forEachIndexed { i, t ->
        if (i in 1 until lastIndex) {
            operation(t)
        }
    }
}

internal fun packageAndSubpackagesOf(path: String): List<String> {
    return path.split('.').fold(arrayListOf()) { list, segment ->
        val prevSegment = list.lastOrNull()?.let { "$it." } ?: ""
        list += "$prevSegment$segment"
        list
    }
}

internal fun subpackagesOf(path: String): List<String> {
    return packageAndSubpackagesOf(path) - path
}


@UseExperimental(ExperimentalContracts::class)
inline fun <T> computeIf(condition: Boolean, block: () -> T): T? {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }

    return if (condition) {
        block()
    } else {
        null
    }
}

fun main() {
    println(subpackagesOf("a.b.c"))
}