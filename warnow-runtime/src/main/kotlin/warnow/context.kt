package warnow

import warnow.runtime.ContextImpl
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

interface Context {

    fun <T : Context> derive(type: KClass<T>): T
}

interface NestedContext : Context {

    val parent: Context
}

object GlobalContext : Context {

    internal val context = ContextImpl()

    override fun <T : Context> derive(type: KClass<T>): T = context.derive(type)
}

typealias global = GlobalContext

interface WarnowProperty<T> {

    val context: Context

    operator fun getValue(receiver: Any?, property: KProperty<*>): T
    operator fun setValue(receiver: Any?, property: KProperty<*>, value: T)

    fun addInvalidationListener(listener: (T) -> Unit)
    fun applyInvalidationListener(listener: (T) -> Unit)

    fun addChangeListener(listener: (WarnowProperty<T>, old: T, new: T) -> Unit)
    fun applyChangeListener(listener: (WarnowProperty<T>, old: T, new: T) -> Unit)

    infix fun bind(listener: (T) -> Unit)
}

infix fun <T> KMutableProperty0<T>.bind(listener: WarnowProperty<T>) {}

fun <T> KProperty0<T>.addInvalidationListener(listener: (T) -> Unit) {}
fun <T> KProperty0<T>.applyInvalidationListener(listener: (T) -> Unit) {}

fun <T> KProperty0<T>.addChangeListener(listener: (WarnowProperty<T>, old: T, new: T) -> Unit) {}
fun <T> KProperty0<T>.applyChangeListener(listener: (WarnowProperty<T>, old: T, new: T) -> Unit) {}