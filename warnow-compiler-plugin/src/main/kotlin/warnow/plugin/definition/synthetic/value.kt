package warnow.plugin.definition.synthetic

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.LazyClassReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.record
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.Printer
import warnow.plugin.definition.DeclarationGenerationContext
import warnow.plugin.marker.Kind
import warnow.plugin.marker.WarnowSyntheticFunction
import warnow.plugin.resolution.StatePackage

private const val MUTABLE_PREFIX = "Mutable"
private const val VALUE_ACCESS_CONSTRUCT_SUFFIX = "ValueAccessConstruct"

internal fun generateValueAccessConstruct(
    context: DeclarationGenerationContext,
    containingDeclaration: PackageFragmentDescriptor,
    statePackage: StatePackage,
    mutable: Boolean
): ClassDescriptor {
    val nestedConstructs = statePackage.packages.map { nestedStatePackage ->
        generateValueAccessConstruct(context, containingDeclaration, nestedStatePackage, mutable)
    }

    val name = buildString {
        if (mutable) {
            append(MUTABLE_PREFIX)
        }
        append(statePackage.name.capitalize())
        append(VALUE_ACCESS_CONSTRUCT_SUFFIX)
    }

    return generateInterface(context.storageManager, containingDeclaration, context.namingStrategy.rename(name)) { classDescriptor ->
        val properties = mutableListOf<PropertyDescriptor>()
        val functions = mutableListOf<SimpleFunctionDescriptor>()

        for (property in statePackage.properties) {
            properties += generateSyntheticProperty(classDescriptor, property.name, property.type, Kind.ValueAccess, mutable = mutable)
        }

        for ((nestedStatePackage, construct) in statePackage.packages.zip(nestedConstructs)) {
            properties += generateSyntheticProperty(classDescriptor, nestedStatePackage.name, construct.defaultType, Kind.PackageAccess)
            functions += generatePackageAccessFunctionWithContext(context, classDescriptor, nestedStatePackage.name, construct.defaultType)
            functions += generatePackageBlockAccessFunctionWithContext(context, classDescriptor, nestedStatePackage.name, construct.defaultType)
        }

        object : MemberScopeImpl() {

            override fun getContributedDescriptors(
                kindFilter: DescriptorKindFilter,
                nameFilter: (Name) -> Boolean
            ): Collection<DeclarationDescriptor> {
                return functions.filter { kindFilter.acceptsKinds(DescriptorKindFilter.FUNCTIONS_MASK) && nameFilter(it.name) } +
                        properties.filter { kindFilter.acceptsKinds(DescriptorKindFilter.VARIABLES_MASK) && nameFilter(it.name) }
            }

            override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
                recordLookup(name, location)
                return functions.filter { it.name == name }
            }

            override fun getContributedVariables(name: Name, location: LookupLocation): Collection<PropertyDescriptor> {
                recordLookup(name, location)
                return properties.filter { it.name == name }
            }

            override fun recordLookup(name: Name, location: LookupLocation) {
                context.lookupTracker.record(location, classDescriptor, name)
            }

            override fun printScopeStructure(p: Printer) {
                p.println(this::class.java.simpleName)
            }
        }
    }
}

private fun generatePackageAccessFunctionWithContext(
    context: DeclarationGenerationContext,
    containingDeclaration: ClassDescriptor,
    name: String,
    type: SimpleType
): SimpleFunctionDescriptor {
    val function = object : WarnowSyntheticFunction, SimpleFunctionDescriptorImpl(
        containingDeclaration,
        null,
        Annotations.EMPTY,
        Name.identifier(name),
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        SourceElement.NO_SOURCE
    ) {
        override val kind: Kind = Kind.PackageAccessWithContext
    }

    val contextParameter = ValueParameterDescriptorImpl(
        function,
        null,
        0,
        Annotations.EMPTY,
        Name.identifier("context"),
        context.warnowContextType,
        true,
        false,
        false,
        null,
        SourceElement.NO_SOURCE
    )

    function.initialize(
        null,
        LazyClassReceiverParameterDescriptor(containingDeclaration),
        emptyList(),
        listOf(contextParameter),
        type,
        Modality.ABSTRACT,
        Visibilities.PUBLIC
    )

    return function
}

private fun generatePackageBlockAccessFunctionWithContext(
    context: DeclarationGenerationContext,
    containingDeclaration: ClassDescriptor,
    name: String,
    type: SimpleType
): SimpleFunctionDescriptor {
    val function = object : WarnowSyntheticFunction, SimpleFunctionDescriptorImpl(
        containingDeclaration,
        null,
        Annotations.EMPTY,
        Name.identifier(name),
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        SourceElement.NO_SOURCE
    ) {
        override val kind: Kind = Kind.PackageAccessWithBlockAndContext
    }

    function.isInline = true

    val genericTypeParameter = TypeParameterDescriptorImpl.createWithDefaultBound(
        function,
        Annotations.EMPTY,
        false,
        Variance.INVARIANT,
        Name.identifier("T"),
        0
    )

    val genericType = context.createGenericType(genericTypeParameter)

    val contextParameter = ValueParameterDescriptorImpl(
        function,
        null,
        0,
        Annotations.EMPTY,
        Name.identifier("context"),
        context.warnowContextType,
        true,
        false,
        false,
        null,
        SourceElement.NO_SOURCE
    )

    val blockParameter = ValueParameterDescriptorImpl(
        function,
        null,
        1,
        Annotations.EMPTY,
        Name.identifier("block"),
        context.createFunctionLiteralWithReceiverType(type, genericType),
        false,
        false,
        false,
        null,
        SourceElement.NO_SOURCE
    )

    function.initialize(
        null,
        LazyClassReceiverParameterDescriptor(containingDeclaration),
        listOf(genericTypeParameter),
        listOf(contextParameter, blockParameter),
        genericType,
        Modality.ABSTRACT,
        Visibilities.PUBLIC
    )

    return function
}
