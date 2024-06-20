package warnow.plugin.definition

interface NamingStrategy {

    fun rename(name: String): String
}

internal class CountingNamingStrategy : NamingStrategy {

    private val usedNames = mutableMapOf<String, Int>()

    override fun rename(name: String): String {
        val count = usedNames.merge(name, 1, Int::plus)!!

        return if (count > 1) {
            name + count
        } else {
            name
        }
    }
}