package warnow.plugin.log

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Exception
import java.lang.System

object LoggingSink {

    private val logFile = File(System.getProperty("java.io.tmpdir"), "warnow")
    private var first: Boolean = true

    fun log(level: Level, name: String, message: String, throwable: Throwable? = null) {
        val caller = name.takeLast(40).padEnd(40)

        val l = level.name.toUpperCase().padEnd(5)

        FileOutputStream(logFile, !first).bufferedWriter().use { writer ->
            writer.appendln("[$l] [$caller] : $message")

            if (throwable != null) {
                val stacktrace = StringWriter().also { stringWriter ->
                    PrintWriter(stringWriter).use { printWriter ->
                        throwable.printStackTrace(printWriter)
                    }
                }.toString()

                writer.appendln(stacktrace)
            }

            writer.flush()
        }
        first = false
    }
}

class Logger(val name: String) {
    inline fun trace(block: () -> Any?) {
        LoggingSink.log(Level.Trace, name, block().toString())
    }

    inline fun debug(block: () -> Any?) {
        LoggingSink.log(Level.Debug, name, block().toString())
    }

    inline fun log(block: () -> Any?) {
        LoggingSink.log(Level.Info, name, block().toString())
    }

    inline fun info(block: () -> Any?) {
        LoggingSink.log(Level.Info, name, block().toString())
    }

    inline fun warn(block: () -> Any?) {
        LoggingSink.log(Level.Warn, name, block().toString())
    }

    inline fun error(block: () -> Any?) {
        LoggingSink.log(Level.Error, name, block().toString())
    }

    inline fun error(throwable: Throwable, block: () -> Any?) {
        LoggingSink.log(Level.Error, name, block().toString(), throwable)
    }
}

object Logging {

    @Suppress("NOTHING_TO_INLINE")
    inline fun logger(noinline func: () -> Unit): Logger {
        val name = func.javaClass.name
        val slicedName = when {
            name.contains("Kt$") -> name.substringBefore("Kt$")
            name.contains("$") -> name.substringBefore("$")
            else -> name
        }
        return Logger(slicedName)
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun logger(name: String): Logger {
        return Logger(name)
    }
}

enum class Level {

    Trace,

    Debug,

    Info,

    Warn,

    Error,
}