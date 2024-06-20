package warnow.plugin.definition

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.StarProjectionImpl
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.TypeProjectionImpl
import org.jetbrains.kotlin.types.asSimpleType
import warnow.plugin.resolution.IntermediateType
import warnow.plugin.resolution.IntermediateTypeArgument
import warnow.plugin.resolution.TypeReference

class TypeResolver(
    private val module: ModuleDescriptor,
    val storageManager: StorageManager
) {

    private val defaultTypeCache = mutableMapOf<FqName, KotlinType?>()
    private val lazyDefaultTypeLookup: (FqName) -> KotlinType? = { name ->
        defaultTypeCache.getOrPut(name) { module.findClassAcrossModuleDependencies(ClassId.topLevel(name))?.defaultType }
    }

    private val classDescriptorByName = storageManager.createMemoizedFunctionWithNullableValues<FqName, ClassDescriptor> { name ->
        module.findClassAcrossModuleDependencies(ClassId.topLevel(name))
    }

    fun findByFqName(name: FqName): ClassDescriptor? {
        return classDescriptorByName(name)
    }

    fun findTypeByFqName(name: FqName): KotlinType? {
        return lazyDefaultTypeLookup(name)
    }

    fun createType(resolutionType: IntermediateType): SimpleType {
        val classDescriptor = findClassDescriptor(resolutionType.type) ?: return ErrorUtils.createErrorType("unresolved")

        val defaultType = classDescriptor.defaultType

        val arguments = resolutionType.typeArguments.zip(classDescriptor.declaredTypeParameters)
            .map { (argument, descriptor) -> createTypeProjection(argument, descriptor) }

        return KotlinTypeFactory.simpleType(defaultType.asSimpleType(), arguments = arguments, nullable = resolutionType.nullable)
    }

    private fun findClassDescriptor(typeReference: TypeReference): ClassDescriptor? {
        return when (typeReference) {
            is TypeReference.Resolved -> findByFqName(typeReference.name)
            is TypeReference.Unresolved -> {
                for (basePackage in typeReference.packages) {
                    val potentialPackage = basePackage.child(Name.identifier(typeReference.name))
                    val type = findByFqName(potentialPackage)

                    if (type != null) {
                        return type
                    }
                }

                null
            }
        }
    }

    private fun createTypeProjection(typeArgument: IntermediateTypeArgument, descriptor: TypeParameterDescriptor): TypeProjection {
        return if (typeArgument.type == null) {
            StarProjectionImpl(descriptor)
        } else {
            val type = createType(typeArgument.type)

            TypeProjectionImpl(typeArgument.variance, type)
        }
    }

    val anyType: SimpleType
        get() = module.builtIns.anyType
}
