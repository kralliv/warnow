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

internal fun generatePropertyExpectFunction(
    context: DeclarationGenerationContext,
    containingDeclaration: PackageFragmentDescriptor,
    propertyAccessBuilderType: SimpleType
): SimpleFunctionDescriptor {
    val function = object : WarnowSyntheticFunction, SimpleFunctionDescriptorImpl(
        containingDeclaration,
        null,
        Annotations.EMPTY,
        Name.identifier("expect"),
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        SourceElement.NO_SOURCE
    ) {
        override val kind: Kind = Kind.ExpectFunction
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
        context.createFunctionLiteralWithReceiverType(propertyAccessBuilderType, returnType),
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

internal fun generatePropertyExpectInterface(
    context: DeclarationGenerationContext,
    containingDeclaration: PackageFragmentDescriptor
): ClassDescriptor {
    return generateInterface(context.storageManager, containingDeclaration, "PropertyExpectInterface") { classDescriptor ->
        val functions = mutableListOf<SimpleFunctionDescriptor>()

        functions += generateWithinFunction(context, classDescriptor)

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

internal fun generatePropertyExpectDeclarations(
    context: DeclarationGenerationContext,
    classDescriptor: ClassDescriptor
): List<DeclarationDescriptor> {
    return listOf(
        generateWithinFunction(context, classDescriptor)
    )
}
