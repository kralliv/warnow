package warnow.plugin.resolution

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.Variance

/**
 * Represents a type before types are supported during compilation. The resolution of the required properties
 * happens so early in the compilation that types have not been resolved yet. Intermediate types might not
 * be resolved completely. If a type was imported using the star-operator, there is no way of resolving the
 * actual type this early into the compilation, instead a intermediate type must be resolved later once the
 * required information is available.
 */
data class IntermediateType(
    val type: TypeReference,
    val nullable: Boolean = false,
    val typeArguments: List<IntermediateTypeArgument> = emptyList()
)

data class IntermediateTypeArgument(
    val variance: Variance,
    val type: IntermediateType?
)

sealed class TypeReference {

    data class Resolved(val name: FqName) : TypeReference()

    data class Unresolved(val name: String, val packages: List<FqName>) : TypeReference()

    fun asString(): String? {
        return when (this) {
            is Resolved -> name.asString()
            is Unresolved -> {
                val packages = packages - DEFAULT_IMPORTS

                if (packages.isNotEmpty()) {
                    "{" + packages.joinToString { it.asString() } + "}."
                } else {
                    ""
                } + name
            }
        }
    }
}

object IntermediateTypes {

    val Unit = IntermediateType(TypeReference.Resolved(FqName("kotlin.Unit")))
}

// ordered by precedence
//
// precedence goes:
//   1. default package
//   2. kotlin.* / kotlin.**
//   3. java.lang.*
//   4. local packages
/**
 * List of default kotlin packages that are imported implicitly. This list is ordered by precedence of the
 * individual package.
 */
val DEFAULT_IMPORTS = listOf(
    FqName(""),

    FqName("kotlin"),
    FqName("kotlin.annotation"),
    FqName("kotlin.collections"),
    FqName("kotlin.io"),
    FqName("kotlin.ranges"),
    FqName("kotlin.sequences"),
    FqName("kotlin.text"),
    FqName("kotlin.jvm"),

    FqName("java.lang")
)
