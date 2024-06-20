package warnow.plugin.util

/**
 * A immutable monad for nullable values, that either is [Some] containing the non-null value or [None]
 * bearing no value as it would be null.
 */
sealed class Option<out T> {

    /**
     * Returns if the option is of type [Some].
     */
    abstract fun isSome(): Boolean

    /**
     * Returns if the option is of type [None]
     */
    abstract fun isNone(): Boolean
}

/**
 * Singleton representation of a absent value.
 */
object None : Option<Nothing>() {

    override fun isSome(): Boolean {
        return false
    }

    override fun isNone(): Boolean {
        return true
    }

    override fun toString(): String {
        return "None"
    }
}

/**
 * Immutable representation of a present non-null value.
 */
data class Some<T>(val value: T) : Option<T>() {

    override fun isSome(): Boolean {
        return true
    }

    override fun isNone(): Boolean {
        return false
    }

    override fun toString(): String {
        return "Some($value)"
    }
}

/**
 * Maps the value of this Option using the specified [mapper] function and returns an Option
 * containing it. If the Option is [None] the result of this function will also be [None].
 */
inline fun <T, R> Option<T>.map(mapper: (T) -> R): Option<R> {
    return when (this) {
        is Some -> Some(mapper(this.value))
        is None -> None
    }
}

/**
 * Maps the value of this Option using the specified [mapper] function and returns the result.
 * If the Option is [None] the result of this function will also be [None].
 */
inline fun <T, R> Option<T>.andThen(mapper: (T) -> Option<R>): Option<R> {
    return when (this) {
        is Some -> mapper(this.value)
        is None -> None
    }
}

/**
 * Expects the Option to be [Some]. If the Option is [None] an [IllegalStateException] is thrown
 * bearing the specified [message].
 */
fun <T> Option<T>.expect(message: String): T {
    return when (this) {
        is Some -> this.value
        is None -> throw IllegalStateException(message)
    }
}

/**
 * Executes the specified [block] if the Option is [Some], otherwise does nothing.
 */
inline fun <T> Option<T>.let(block: (T) -> Unit) {
    if (this is Some) {
        block(this.value)
    }
}

/**
 * Executes the specified [block] if the Option is [Some], otherwise does nothing.
 */
inline fun <T> Option<T>.ifLet(block: (T) -> Unit) {
    if (this is Some) {
        block(this.value)
    }
}

/**
 * Turns a nullable Kotlin type into an [Option]. Returns [Some] bearing the value if it's non-null,
 * otherwise [None]
 */
fun <T> T?.into(): Option<T> {
    return if (this != null) {
        Some(this)
    } else {
        None
    }
}