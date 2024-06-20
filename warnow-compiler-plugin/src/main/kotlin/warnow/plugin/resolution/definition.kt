package warnow.plugin.resolution

data class IntermediatePropertyDefinition(
    val identifier: String,
    val type: IntermediateType
)

data class Initializer(val text: String)