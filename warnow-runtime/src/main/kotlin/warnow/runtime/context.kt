package warnow.runtime

import warnow.GlobalContext
import warnow.Context as ApiContext
import kotlin.reflect.KClass


internal interface Context : ApiContext {

    fun <T> getProperty(identifier: String, initializer: Initializer<T>): WarnowPropertyImpl<T>

    fun <T> getValue(identifier: String, initializer: Initializer<T>?, nullable: Boolean): T
    fun <T> setValue(identifier: String, value: T)
}

internal class ContextImpl : Context {

    private val properties = mutableMapOf<String, WarnowPropertyImpl<*>>()
    private val state: MutableMap<String, Any?> = mutableMapOf()

    override fun <T> getProperty(identifier: String, initializer: Initializer<T>): WarnowPropertyImpl<T> {
        return properties.getOrPut(identifier) {
            WarnowPropertyImpl(identifier, initializer, true, this)
        } as WarnowPropertyImpl<T>
    }

    override fun <T> getValue(identifier: String, initializer: Initializer<T>?, nullable: Boolean): T {
        val value = if (state.containsKey(identifier)) {
            state[identifier]
        } else {
            val initially = when {
                initializer != null -> initializer.createInitialValue()
                nullable -> null
                else -> error("state of $identifier has not been initialized yet")
            }

            state[identifier] = initially

            initially
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    override fun <T> setValue(identifier: String, value: T) {
        state[identifier] = value
    }

    override fun <T : ApiContext> derive(type: KClass<T>): T = error("unsupported")
}

internal val ApiContext.runtime: Context
    get() = when (this) {
        is Context -> this
        is GlobalContext -> this.context
        else -> error("cannot find runtime context for ${this::class.java.name}")
    }

fun createContextImplementation(): ApiContext {
    return ContextImpl()
}