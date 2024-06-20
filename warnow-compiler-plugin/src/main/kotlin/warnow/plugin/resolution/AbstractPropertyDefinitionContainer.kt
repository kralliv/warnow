package warnow.plugin.resolution

import warnow.plugin.definition.TypeResolver
import warnow.plugin.log.Logging
import warnow.plugin.subpackagesOf

abstract class AbstractPropertyDefinitionContainer {

    protected abstract fun getProperties(): List<IntermediatePropertyDefinition>

    fun resolve(resolver: TypeResolver): StatePackage {
        val packageTree = PackageRadixTree<StateProperty>()

        for ((property, intermediateType) in getProperties()) {
            val packageName = property.substringBeforeLast(".", missingDelimiterValue = "")
            val name = property.substringAfterLast('.')

            LOG.debug { "level $packageName  name $name" }

            val type = resolver.storageManager.createLazyValue { resolver.createType(intermediateType) }

            val stateProperty = StateProperty(name, type)

            packageTree.insert(packageName, stateProperty)
        }

        return packageTree.map { name, children, values -> StatePackage(name, values, children) }
    }

    fun getDuplicatedPropertyNames(): Set<String> {
        val properties = getProperties()

        val known = mutableSetOf<String>()
        val duplicated = mutableSetOf<String>()

        properties.forEach { property ->
            if (!known.add(property.identifier)) {
                duplicated.add(property.identifier)
            }
        }

        return duplicated
    }

    fun getClashingPropertyNames(): Set<String> {
        val properties = getProperties()

        val packages = properties
            .flatMap { packageName -> subpackagesOf(packageName.identifier) }
            .toSet()

        return properties.filter { property -> packages.contains(property.identifier) }
            .map { it.identifier }
            .toSet()
    }

    companion object {
        private val LOG = Logging.logger { }
    }
}
