package warnow.plugin.resolution


internal class CollectorBasedPropertyDefinitionContainer : AbstractPropertyDefinitionContainer() {

    val propertyDefinitionCollector = PropertyDefinitionCollectorImpl()

    override fun getProperties(): List<IntermediatePropertyDefinition> = propertyDefinitionCollector.getProperties()
}
