package warnow.plugin.definition.synthetic

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
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
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.Printer
import warnow.plugin.definition.DeclarationGenerationContext
import warnow.plugin.marker.Kind
import warnow.plugin.marker.WarnowSyntheticFunction

internal fun generatePropertyDefinitionFunction(
    context: DeclarationGenerationContext,
    containingDeclaration: PackageFragmentDescriptor,
    propertyDefinitionBuilderType: SimpleType
): SimpleFunctionDescriptor {
    val function = object : WarnowSyntheticFunction, SimpleFunctionDescriptorImpl(
        containingDeclaration,
        null,
        Annotations.EMPTY,
        Name.identifier("define"),
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        SourceElement.NO_SOURCE
    ) {
        override val kind: Kind = Kind.DefineFunction
    }

    val genericTypeParameter = TypeParameterDescriptorImpl.createWithDefaultBound(
        function,
        Annotations.EMPTY,
        false,
        Variance.INVARIANT,
        Name.identifier("T"),
        0
    )

    val returnType = context.createGenericDelegateType(genericTypeParameter)

    val parameter = ValueParameterDescriptorImpl(
        function,
        null,
        0,
        Annotations.EMPTY,
        Name.identifier("block"),
        context.createFunctionLiteralWithReceiverType(propertyDefinitionBuilderType, returnType),
        false,
        false,
        false,
        null,
        SourceElement.NO_SOURCE
    )

    function.initialize(
        null,
        null,
        listOf(genericTypeParameter),
        listOf(parameter),
        returnType,
        Modality.FINAL,
        Visibilities.PUBLIC
    )

    return function
}

internal fun generatePropertyDefinitionInterface(
    context: DeclarationGenerationContext,
    containingDeclaration: PackageFragmentDescriptor
): ClassDescriptor {
    return generateInterface(context.storageManager, containingDeclaration, "PropertyDefinitionInterface") { classDescriptor ->
        val functions = mutableListOf<SimpleFunctionDescriptor>()

        functions += generateWithinFunction(context, classDescriptor)
        functions += generateInitiallyFunction(context, classDescriptor)

        object : MemberScopeImpl() {

            override fun getContributedDescriptors(
                kindFilter: DescriptorKindFilter,
                nameFilter: (Name) -> Boolean
            ): Collection<DeclarationDescriptor> {
                return functions.filter { kindFilter.acceptsKinds(DescriptorKindFilter.FUNCTIONS_MASK) && nameFilter(it.name) }
            }

            override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
                recordLookup(name, location)
                return functions.filter { it.name == name }
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

internal fun generatePropertyDefinitionDeclarations(
    context: DeclarationGenerationContext,
    classDescriptor: ClassDescriptor
): List<DeclarationDescriptor> {
    return listOf(
        generateWithinFunction(context, classDescriptor),
        generateInitiallyFunction(context, classDescriptor)
    )
}

private fun generateInitiallyFunction(
    context: DeclarationGenerationContext,
    containingDeclaration: ClassDescriptor
): SimpleFunctionDescriptor {
    val function = SimpleFunctionDescriptorImpl.create(
        containingDeclaration,
        Annotations.EMPTY,
        Name.identifier("initially"),
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

    val genericType = context.createGenericType(genericTypeParameter)
    val delegateType = context.createDelegateType(genericType)

    val receiverValue = ExtensionReceiver(
        function,
        genericType,
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
        Name.identifier("value"),
        genericType,
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

    dumpCallableReceivers(function)

    return function
}