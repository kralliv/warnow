package warnow.plugin.generation

inline fun <T> mutate(block: String.() -> T): T {
    return "".block()
}

fun main() {
    mutate {
        System.out.println(this)
    }
}