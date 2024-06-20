package warnow.plugin.util

fun Err(): Result<Nothing, Unit> {
    return Err(Unit)
}

fun Ok(): Result<Unit, Nothing> {
    return Ok(Unit)
}

/**
 * Immutable monad representing the outcome of an operation by bearing either a value or an error.
 * The positive outcome is represented by [Ok] and a negative outcome by [Err].
 */
sealed class Result<out T, out E> {

    /**
     * Expects the outcome to be [Ok], otherwise throws an [IllegalStateException] bearing the
     * specified [message].
     */
    abstract fun expect(message: String): T

    /**
     * Maps the value of this Result using the specified [mapper] function and returns a Result
     * containing it. If the Result is [Err], the result of this function will also be [Err].
     */
    abstract fun <U> map(mapper: (T) -> U): Result<U, E>

    /**
     * Maps the error of this Result using the specified [mapper] function and returns a Result
     * containing it. If the Result is [Ok], the result of this function will also be [Ok].
     */
    abstract fun <F> mapErr(mapper: (E) -> F): Result<T, F>

    /**
     * Turns the Result into an [Option] under the premise that [Ok] is expected. If the Result
     * is [Err], the function will return [None] instead.
     */
    abstract fun ok(): Option<T>

    /**
     * Turns the Result into an [Option] under the premise that [Err] is expected. If the Result
     * is [Ok], the function will return [None] instead.
     */
    abstract fun err(): Option<E>

    /**
     * Returns true if this Result is of type [Ok].
     */
    abstract fun isOk(): Boolean

    /**
     * Returns true if this Result is of type [Err].
     */
    abstract fun isErr(): Boolean
}

/**
 * Concrete representation of a positive outcome of a [Result] bearing the value of the operation.
 */
data class Ok<out T>(val value: T) : Result<T, Nothing>() {

    override fun <U> map(mapper: (T) -> U): Result<U, Nothing> {
        return Ok(mapper(value))
    }

    override fun expect(message: String): T {
        return value
    }

    override fun <F> mapErr(mapper: (Nothing) -> F): Result<T, F> {
        return Ok(value)
    }

    override fun ok(): Option<T> {
        return Some(value)
    }

    override fun err(): Option<Nothing> {
        return None
    }

    override fun isOk(): Boolean {
        return true
    }

    override fun isErr(): Boolean {
        return false
    }
}

/**
 * Concrete representation of a negative outcome of a [Result] bearing the error of the operation.
 */
data class Err<out E>(val value: E) : Result<Nothing, E>() {

    override fun expect(message: String): Nothing {
        throw IllegalStateException("result is err")
    }

    override fun <U> map(mapper: (Nothing) -> U): Result<U, E> {
        return Err(value)
    }

    override fun <F> mapErr(mapper: (E) -> F): Result<Nothing, F> {
        return Err(mapper(value))
    }

    override fun ok(): Option<Nothing> {
        return None
    }

    override fun err(): Option<E> {
        return Some(value)
    }

    override fun isOk(): Boolean {
        return false
    }

    override fun isErr(): Boolean {
        return true
    }
}