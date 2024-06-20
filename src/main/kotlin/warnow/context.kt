package warnow

import kotlin.properties.ReadWriteProperty

interface Context {

}

interface NestedContext : Context {

    val parent: Context
}

object GlobalContext : Context

infix fun <T> ReadWriteProperty<Any?, T>.within(context: Context): ReadWriteProperty<Any?, T> {
    TODO()
}