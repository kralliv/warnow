package warnow.plugin.generation

import com.google.common.collect.Maps
import org.jetbrains.kotlin.codegen.extractReificationArgument
import org.jetbrains.kotlin.codegen.inline.TypeParameterMappings
import org.jetbrains.kotlin.codegen.signature.BothSignatureWriter
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typesApproximation.approximateCapturedTypes


fun getTypeMapping(resolvedCall: ResolvedCall<*>, descriptor: CallableDescriptor, typeMapper: KotlinTypeMapper): TypeParameterMappings {
    val typeArguments = getTypeArgumentsForResolvedCall(resolvedCall, descriptor)

    val mappings = TypeParameterMappings()
    for ((key, type) in typeArguments) {

        val isReified = key.isReified || InlineUtil.isArrayConstructorWithLambda(resolvedCall.resultingDescriptor)

        val typeParameterAndReificationArgument = extractReificationArgument(type)
        if (typeParameterAndReificationArgument == null) {
            val approximatedType = approximateCapturedTypes(type).upper
            // type is not generic
            val signatureWriter = BothSignatureWriter(BothSignatureWriter.Mode.TYPE)
            val asmType = typeMapper.mapTypeParameter(approximatedType, signatureWriter)

            mappings.addParameterMappingToType(
                key.name.identifier, approximatedType, asmType, signatureWriter.toString(), isReified
            )
        } else {
            mappings.addParameterMappingForFurtherReification(
                key.name.identifier, type, typeParameterAndReificationArgument.second, isReified
            )
        }
    }

    return mappings
}

private fun getTypeArgumentsForResolvedCall(
    resolvedCall: ResolvedCall<*>,
    descriptor: CallableDescriptor
): Map<TypeParameterDescriptor, KotlinType> {
    if (descriptor !is TypeAliasConstructorDescriptor) {
        return resolvedCall.typeArguments
    }

    val underlyingConstructorDescriptor = descriptor.underlyingConstructorDescriptor
    val resultingType = descriptor.returnType
    val typeArgumentsForReturnType = resultingType.arguments
    val typeParameters = underlyingConstructorDescriptor.typeParameters

    assert(typeParameters.size == typeArgumentsForReturnType.size) {
        "Type parameters of the underlying constructor " + underlyingConstructorDescriptor +
                "should correspond to type arguments for the resulting type " + resultingType
    }

    val typeArgumentsMap = Maps.newHashMapWithExpectedSize<TypeParameterDescriptor, KotlinType>(typeParameters.size)
    for (typeParameter in typeParameters) {
        val typeArgument = typeArgumentsForReturnType[typeParameter.index].type
        typeArgumentsMap[typeParameter] = typeArgument
    }

    return typeArgumentsMap
}