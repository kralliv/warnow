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
import org.jetbrains.kotlin.descriptors.impl.ReceiverParameterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.record
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.resolve.scopes.receivers.ExtensionReceiver
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.Printer
import warnow.plugin.definition.DeclarationGenerationContext
import warnow.plugin.marker.Kind
import warnow.plugin.resolution.StatePackage

internal fun generatePropertyAccessConstruct(
    context: DeclarationGenerationContext,
    containingDeclaration: PackageFragmentDescriptor,
    nameSuffix: String,
    statePackage: StatePackage
): ClassDescriptor {
    val name = context.namingStrategy.rename(statePackage.name.capitalize() + nameSuffix)

    val nestedConstructs = statePackage.packages.map { nestedStatePackage ->
        generatePropertyAccessConstruct(context, containingDeclaration, nameSuffix, nestedStatePackage)
    }

    return generateInterface(context.storageManager, containingDeclaration, name) { classDescriptor ->
        val properties = mutableListOf<PropertyDescriptor>()

        for (property in statePackage.properties) {
            val delegateType = context.createDelegateType(property.type)

            properties += generateSyntheticProperty(classDescriptor, property.name, delegateType, Kind.Unknown)
        }

        for ((nestedStatePackage, construct) in statePackage.packages.zip(nestedConstructs)) {
            properties += generateSyntheticProperty(classDescriptor, nestedStatePackage.name, construct.defaultType, Kind.Unknown)
        }

        object : MemberScopeImpl() {

            override fun getContributedDescriptors(
                kindFilter: DescriptorKindFilter,
                nameFilter: (Name) -> Boolean
            ): Collection<DeclarationDescriptor> {
                return properties.filter { kindFilter.acceptsKinds(DescriptorKindFilter.VARIABLES_MASK) && nameFilter(it.name) }
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

internal fun generatePropertyAccessDeclarations(
    context: DeclarationGenerationContext,
    containingDeclaration: PackageFragmentDescriptor,
    classDescriptor: ClassDescriptor,
    nameSuffix: String,
    statePackage: StatePackage
): List<DeclarationDescriptor> {
    val nestedConstructs = statePackage.packages.map { nestedStatePackage ->
        generatePropertyAccessConstruct(context, containingDeclaration, nameSuffix, nestedStatePackage)
    }

    val properties = mutableListOf<PropertyDescriptor>()

    for (property in statePackage.properties) {
        val delegateType = context.createDelegateType(property.type)

        properties += generateSyntheticProperty(classDescriptor, property.name, delegateType, Kind.Unknown)
    }

    for ((nestedStatePackage, construct) in statePackage.packages.zip(nestedConstructs)) {
        properties += generateSyntheticProperty(classDescriptor, nestedStatePackage.name, construct.defaultType, Kind.Unknown)
    }

    return properties
}

internal fun generateWithinFunction(
    context: DeclarationGenerationContext,
    containingDeclaration: ClassDescriptor
): SimpleFunctionDescriptor {
    val function = SimpleFunctionDescriptorImpl.create(
        containingDeclaration,
        Annotations.EMPTY,
        Name.identifier("within"),
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        SourceElement.NO_SOURCE
    )

    function.isInfix = true

    val genericTypeParameter = TypeParameterDescriptorImpl.createWithDefaultBound(
        function,
        Annotations.EMPTY,
        false,
        Variance.INVARIANT,
        Name.identifier("T"),
        0
    )

    val delegateType = context.createGenericDelegateType(genericTypeParameter)

    val receiverValue = ExtensionReceiver(
        function,
        delegateType,
        null
    )

    val extensionReceiverParameter = ReceiverParameterDescriptorImpl(
        function,
        receiverValue,
        Annotations.EMPTY
    )

    val contextParameter = ValueParameterDescriptorImpl(
        function,
        null,
        0,
        Annotations.EMPTY,
        Name.identifier("context"),
        context.warnowContextType,
        false,
        false,
        false,
        null,
        SourceElement.NO_SOURCE
    )

    function.initialize(
        extensionReceiverParameter,
        LazyClassReceiverParameterDescriptor(containingDeclaration),
        listOf(genericTypeParameter),
        listOf(contextParameter),
        delegateType,
        Modality.ABSTRACT,
        Visibilities.PUBLIC
    )

    return function
}