package warnow.plugin.analysis

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProjectionKind
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.types.Variance
import warnow.plugin.resolution.DEFAULT_IMPORTS
import warnow.plugin.resolution.IntermediateType
import warnow.plugin.resolution.IntermediateTypeArgument
import warnow.plugin.resolution.TypeReference

/**
 * Handles the intermediate type resolution within a single kotlin file. It tries to return the most accurate
 * intermediate type it can resolve using the imports specified in the file. It also handles type to belong to
 * the default packages in kotlin. Those types can never be resolved completely and need to be resolved later
 * into the compilation.
 */
class IntermediateTypeResolutionContainer {

    private val packages = DEFAULT_IMPORTS.toMutableList()
    private val typeLookup = mutableMapOf<String, FqName>()

    fun registerPackage(type: FqName) {
        if (!packages.contains(type)) {
            packages.add(type)
        }
    }

    fun registerName(name: String, type: FqName) {
        typeLookup[name] = type
    }

    fun isPotentiallyResolvable(packageName: String, name: String): Boolean {
        return typeLookup.containsKey(name) || packages.contains(FqName(packageName))
    }

    fun resolveType(name: String): TypeReference {
        val type = typeLookup[name]

        return if (type != null) {
            TypeReference.Resolved(type)
        } else {
            TypeReference.Unresolved(name, packages.toList())
        }
    }

    fun resolveType(typeReference: KtTypeReference): IntermediateType? {
        val typeElement = typeReference.typeElement ?: return null
        return resolveType(typeElement)
    }

    fun resolveType(typeElement: KtTypeElement): IntermediateType? {
        return when (typeElement) {
            is KtUserType -> {
                val qualifier = buildString {
                    var qualifier = typeElement.qualifier
                    while (qualifier != null) {
                        insert(0, qualifier.referencedName + ".")
                        qualifier = qualifier.qualifier
                    }
                }

                val referencedName = typeElement.referencedName ?: return null

                val name = if (qualifier.isEmpty()) {
                    resolveType(referencedName)
                } else {
                    TypeReference.Resolved(FqName(qualifier + referencedName))
                }

                val typeArguments = typeElement.typeArguments.map { typeArgument ->
                    val variance = when (typeArgument.projectionKind) {
                        KtProjectionKind.NONE -> Variance.INVARIANT
                        KtProjectionKind.IN -> Variance.IN_VARIANCE
                        KtProjectionKind.OUT -> Variance.OUT_VARIANCE
                        KtProjectionKind.STAR -> Variance.INVARIANT
                    }

                    val resolutionType = typeArgument.typeReference?.let(::resolveType)

                    IntermediateTypeArgument(variance, resolutionType)
                }

                IntermediateType(name, false, typeArguments)
            }
            is KtNullableType -> {
                val innerType = typeElement.innerType ?: return null

                resolveType(innerType)?.copy(nullable = true)
            }
            else -> error("unsupported type reference: ${this::class.qualifiedName}")
        }
    }
}