package warnow.runtime

import warnow.Context as ApiContext
import warnow.WarnowProperty
import kotlin.reflect.KProperty

internal class WarnowPropertyImpl<T>(
    private val identifier: String,
    private val initializer: Initializer<T>?,
    private val nullable: Boolean,
    private val _context: Context
) : WarnowProperty<T> {

    override val context: ApiContext
        get() = _context

    override fun getValue(receiver: Any?, property: KProperty<*>): T = getValue()
    override fun setValue(receiver: Any?, property: KProperty<*>, value: T) = setValue(value)

    fun getValue(): T {
        return _context.getValue(identifier, initializer, nullable)
    }

    fun setValue(value: T) {
        if (value != getValue()) {
            _context.setValue(identifier, value)

            invalidationListeners.forEach { it(value) }
        }
    }

    private val invalidationListeners = mutableListOf<(T) -> Unit>()

    override fun addInvalidationListener(listener: (T) -> Unit) {
        invalidationListeners += listener
    }

    override fun applyInvalidationListener(listener: (T) -> Unit) {
        addInvalidationListener(listener)

        listener(getValue())
    }

    override fun addChangeListener(listener: (WarnowProperty<T>, old: T, new: T) -> Unit) {
    }

    override fun applyChangeListener(listener: (WarnowProperty<T>, old: T, new: T) -> Unit) {
    }

    override fun bind(listener: (T) -> Unit) {
        invalidationListeners += listener
    }
}

interface Initializer<T> {

    fun createInitialValue(): T
}