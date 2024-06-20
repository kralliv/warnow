package warnow.plugin.definition.synthetic

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.Variance
import warnow.plugin.definition.DeclarationGenerationContext
import warnow.plugin.marker.Kind
import warnow.plugin.marker.WarnowSyntheticFunction

internal fun generateValueUpdateFunction(
    context: DeclarationGenerationContext,
    containingDeclaration: PackageFragmentDescriptor,
    valueAccessConstructType: SimpleType
): SimpleFunctionDescriptor {
    val function = object : WarnowSyntheticFunction, SimpleFunctionDescriptorImpl(
        containingDeclaration,
        null,
        Annotations.EMPTY,
        Name.identifier("mutate"),
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        SourceElement.NO_SOURCE
    ) {
        override val kind: Kind = Kind.MutateFunction
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
        declaresDefaultValue = true,
        isCrossinline = false,
        isNoinline = false,
        varargElementType = null,
        source = SourceElement.NO_SOURCE
    )


    val blockParameter = ValueParameterDescriptorImpl(
        function,
        null,
        1,
        Annotations.EMPTY,
        Name.identifier("block"),
        context.createFunctionLiteralWithReceiverType(valueAccessConstructType, genericType),
        declaresDefaultValue = false,
        isCrossinline = false,
        isNoinline = false,
        varargElementType = null,
        source = SourceElement.NO_SOURCE
    )

    function.initialize(
        null,
        null,
        listOf(genericTypeParameter),
        listOf(contextParameter, blockParameter),
        genericType,
        Modality.FINAL,
        Visibilities.PUBLIC
    )

    return function
}