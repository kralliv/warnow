package warnow.plugin.definition

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptorImpl
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.TypeProjectionImpl

private val KOTLIN_EXTENSION_NAME = FqName("kotlin.ExtensionFunctionType")

private val WARNOW_PROPERTY_NAME = FqName("warnow.WarnowProperty")
private val WARNOW_CONTEXT_NAME = FqName("warnow.Context")

class DeclarationGenerationContext(
    val storageManager: StorageManager,
    val module: ModuleDescriptor,
    val lookupTracker: LookupTracker,
    val namingStrategy: NamingStrategy
) {

    val unitType: SimpleType
        get() = module.builtIns.unitType

    val warnowPropertyType: SimpleType by lazy { findTypeByQualifiedName(WARNOW_PROPERTY_NAME) }
    val warnowContextType: SimpleType by lazy { findTypeByQualifiedName(WARNOW_CONTEXT_NAME) }

    val extensionFunctionTypeType: SimpleType by lazy { findTypeByQualifiedName(KOTLIN_EXTENSION_NAME) }

    private fun findTypeByQualifiedName(name: FqName): SimpleType {
        val classId = ClassId.topLevel(name)

        return module.findClassAcrossModuleDependencies(classId)?.defaultType ?: error("cannot resolve class ${name.asString()} in module $module")
    }

    fun createDelegateType(type: SimpleType): SimpleType {
        val arguments = listOf<TypeProjection>(TypeProjectionImpl(type))

        return KotlinTypeFactory.simpleType(warnowPropertyType, arguments = arguments, nullable = false)
    }

    fun createGenericType(typeParameter: TypeParameterDescriptor): SimpleType {
        return KotlinTypeFactory.simpleType(Annotations.EMPTY, typeParameter.typeConstructor, listOf(), false)
    }

    fun createGenericDelegateType(typeParameter: TypeParameterDescriptor): SimpleType {
        val genericType = createGenericType(typeParameter)
        val arguments = listOf(TypeProjectionImpl(genericType))

        return KotlinTypeFactory.simpleType(warnowPropertyType, arguments = arguments, nullable = false)
    }

    fun createFunctionLiteralWithReceiverType(receiver: SimpleType, returnType: SimpleType): SimpleType {
        val functionClass = module.builtIns.getFunction(1)
        val functionType = functionClass.defaultType

        val annotationDescriptor = AnnotationDescriptorImpl(extensionFunctionTypeType, emptyMap(), SourceElement.NO_SOURCE)

        val arguments = listOf(TypeProjectionImpl(receiver), TypeProjectionImpl(returnType))

        return KotlinTypeFactory.simpleType(functionType, arguments = arguments, annotations = Annotations.create(listOf(annotationDescriptor)))
    }

    companion object {
        fun isModuleApplicable(module: ModuleDescriptor): Boolean {
            return module.findClassAcrossModuleDependencies(ClassId.topLevel(WARNOW_CONTEXT_NAME)) != null
        }
    }
}
