package warnow.plugin.resolution

internal class PackageRadixTree<E> {

    fun insert(key: String, value: E) {
        root.insert(key, value)
    }

    fun <T> map(block: (name: String, children: List<T>, values: List<E>) -> T): T {
        return root.map(block)
    }

    private val root = Bucket("")

    private inner class Bucket(val name: String, val buckets: MutableList<Bucket> = mutableListOf(), val values: MutableList<E> = mutableListOf()) {

        fun insert(key: String, value: E) {
            if (key.isEmpty()) {
                values.add(value)
            } else {
                val name = key.substringBefore('.')
                val remaining = key.substringAfter('.', missingDelimiterValue = "")

                for (bucket in buckets) {
                    if (bucket.name == name) {
                        return bucket.insert(remaining, value)
                    }
                }

                val bucket = Bucket(name)
                buckets += bucket

                bucket.insert(remaining, value)
            }
        }

        fun <T> map(block: (name: String, children: List<T>, values: List<E>) -> T): T {
            val children = buckets.map { bucket -> bucket.map(block) }

            return block(name, children, values)
        }
    }
}