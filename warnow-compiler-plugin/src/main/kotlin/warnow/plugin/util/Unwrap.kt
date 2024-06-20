package warnow.plugin.util

class UnwrapException(message: String) : Exception(message)

fun <T> Option<T>.unwrap(): T {
    return when (this) {
        is Some -> this.value
        is None -> throw UnwrapException("option is none")
    }
}

fun <T, E> Result<T, E>.unwrap(): T {
    return when (this) {
        is Ok -> this.value
        is Err -> throw UnwrapException("result was err: $value")
    }
}

inline fun <T, E> Result<T, E>.unwrapOrElse(closure: (E) -> T): T {
    return when (this) {
        is Ok -> this.value
        is Err -> closure(this.value)
    }
}

fun <T, E> Result<T, E>.unwrapErr(): E {
    return when (this) {
        is Ok -> throw UnwrapException("result was ok: $value")
        is Err -> this.value
    }
}

fun <T, E> Result<T, E>.unwrapOr(alternative: T): T {
    return when (this) {
        is Ok -> this.value
        is Err -> alternative
    }
}

fun <T> Option<T>.unwrapOr(default: T): T {
    return when (this) {
        is Some -> this.value
        is None -> default
    }
}

inline fun <T> Option<T>.unwrapOr(default: () -> T): T {
    return when (this) {
        is Some -> this.value
        is None -> default()
    }
}

inline fun <T, E> Option<T>.mapOr(mapper: (T) -> E, default: E): E {
    return when (this) {
        is Some -> mapper(this.value)
        is None -> default
    }
}