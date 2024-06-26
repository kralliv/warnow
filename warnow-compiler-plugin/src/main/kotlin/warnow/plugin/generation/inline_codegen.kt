package warnow.plugin.generation

import org.jetbrains.kotlin.builtins.isSuspendFunctionTypeOrSubtype
import org.jetbrains.kotlin.codegen.ArgumentAndDeclIndex
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.BaseExpressionCodegen
import org.jetbrains.kotlin.codegen.CallGenerator
import org.jetbrains.kotlin.codegen.Callable
import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.codegen.ExpressionCodegen
import org.jetbrains.kotlin.codegen.JvmCodegenUtil
import org.jetbrains.kotlin.codegen.JvmKotlinType
import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.codegen.ValueKind
import org.jetbrains.kotlin.codegen.binding.CodegenBinding
import org.jetbrains.kotlin.codegen.inline.DefaultLambda
import org.jetbrains.kotlin.codegen.inline.InlineCodegen
import org.jetbrains.kotlin.codegen.inline.InlineException
import org.jetbrains.kotlin.codegen.inline.LambdaInfo
import org.jetbrains.kotlin.codegen.inline.PsiExpressionLambda
import org.jetbrains.kotlin.codegen.inline.RootInliningContext
import org.jetbrains.kotlin.codegen.inline.SMAPAndMethodNode
import org.jetbrains.kotlin.codegen.inline.SourceCompilerForInline
import org.jetbrains.kotlin.codegen.inline.TypeParameterMappings
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.resolve.ImportedFromObjectCallableDescriptor
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCallWithAssert
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodParameterKind
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type

// This class mostly consist out of JetBrains' code. Only genCallInner()
// has been modified.
class SyntheticMethodInlineCodegen(
    codegen: ExpressionCodegen,
    state: GenerationState,
    function: FunctionDescriptor,
    typeParameterMappings: TypeParameterMappings,
    sourceCompiler: SourceCompilerForInline
) : InlineCodegen<ExpressionCodegen>(codegen, state, function, typeParameterMappings, sourceCompiler), CallGenerator {

    override fun generateAssertFieldIfNeeded(info: RootInliningContext) {
        if (info.generateAssertField) {
            codegen.parentCodegen.generateAssertField()
        }
    }

    private fun performInline0(
        typeArguments: Map<TypeParameterDescriptor, KotlinType>?,
        callDefault: Boolean,
        codegen: BaseExpressionCodegen
    ) {
        var nodeAndSmap: SMAPAndMethodNode? = null
        try {
            nodeAndSmap = createInlineMethodNode(functionDescriptor, jvmSignature, callDefault, sourceCompiler)
            endCall(inlineCall(nodeAndSmap, callDefault))
        } catch (e: CompilationException) {
            throw e
        } catch (e: InlineException) {
            throw throwCompilationException(nodeAndSmap, e, false)
        } catch (e: Exception) {
            throw throwCompilationException(nodeAndSmap, e, true)
        }
    }

    override fun genCallInner(
        callableMethod: Callable,
        resolvedCall: ResolvedCall<*>?,
        callDefault: Boolean,
        codegen: ExpressionCodegen
    ) {
        if (!state.globalInlineContext.enterIntoInlining(resolvedCall)) {
            generateStub(resolvedCall, codegen)
            return
        }
        try {
            performInline0(resolvedCall?.typeArguments, callDefault, codegen)
        } finally {
            state.globalInlineContext.exitFromInliningOf(resolvedCall)
        }
    }

    override fun processAndPutHiddenParameters(justProcess: Boolean) {
        if (AsmUtil.getMethodAsmFlags(functionDescriptor, sourceCompiler.contextKind, state) and Opcodes.ACC_STATIC == 0) {
            invocationParamBuilder.addNextParameter(AsmTypes.OBJECT_TYPE, false)
        }

        for (param in jvmSignature.valueParameters) {
            if (param.kind == JvmMethodParameterKind.VALUE) {
                break
            }
            invocationParamBuilder.addNextParameter(param.asmType, false)
        }

        invocationParamBuilder.markValueParametersStart()
        val hiddenParameters = invocationParamBuilder.buildParameters().parameters

        delayedHiddenWriting = recordParameterValueInLocalVal(justProcess, false, *hiddenParameters.toTypedArray())
    }

    override fun putClosureParametersOnStack(next: LambdaInfo, functionReferenceReceiver: StackValue?) {
        activeLambda = next
        when (next) {
            is PsiExpressionLambda -> codegen.pushClosureOnStack(next.classDescriptor, true, this, functionReferenceReceiver)
            is DefaultLambda -> rememberCapturedForDefaultLambda(next)
            else -> throw RuntimeException("Unknown lambda: $next")
        }
        activeLambda = null
    }

    private fun getBoundCallableReferenceReceiver(argumentExpression: KtExpression): ReceiverValue? {
        val deparenthesized = KtPsiUtil.deparenthesize(argumentExpression) as? KtCallableReferenceExpression ?: return null
        val resolvedCall = deparenthesized.callableReference.getResolvedCallWithAssert(state.bindingContext)
        return JvmCodegenUtil.getBoundCallableReferenceReceiver(resolvedCall)
    }

    /*lambda or callable reference*/
    private fun isInliningParameter(expression: KtExpression, valueParameterDescriptor: ValueParameterDescriptor): Boolean {
        //TODO deparenthisise typed
        val deparenthesized = KtPsiUtil.deparenthesize(expression)

        return InlineUtil.isInlineParameter(valueParameterDescriptor) && InlineUtil.isInlinableParameterExpression(deparenthesized)
    }

    override fun genValueAndPut(
        valueParameterDescriptor: ValueParameterDescriptor?,
        argumentExpression: KtExpression,
        parameterType: JvmKotlinType,
        parameterIndex: Int
    ) {
        requireNotNull(valueParameterDescriptor) {
            "Parameter descriptor can only be null in case a @PolymorphicSignature function is called, " +
                    "which cannot be declared in Kotlin and thus be inline: $codegen"
        }

        if (isInliningParameter(argumentExpression, valueParameterDescriptor)) {
            val lambdaInfo = rememberClosure(argumentExpression, parameterType.type, valueParameterDescriptor)

            val receiverValue = getBoundCallableReferenceReceiver(argumentExpression)
            if (receiverValue != null) {
                val receiver = codegen.generateReceiverValue(receiverValue, false)
                val receiverKotlinType = receiver.kotlinType
                val boxedReceiver =
                    if (receiverKotlinType != null)
                        receiver.type.boxReceiverForBoundReference(receiverKotlinType, state.typeMapper)
                    else
                        receiver.type.boxReceiverForBoundReference()

                putClosureParametersOnStack(
                    lambdaInfo,
                    StackValue.coercion(receiver, boxedReceiver, receiverKotlinType)
                )
            }
        } else {
            val value = codegen.gen(argumentExpression)
            val kind = when {
                isCallSiteIsSuspend(valueParameterDescriptor) -> ValueKind.NON_INLINEABLE_ARGUMENT_FOR_INLINE_PARAMETER_CALLED_IN_SUSPEND
                isInlineSuspendParameter(valueParameterDescriptor) -> ValueKind.NON_INLINEABLE_ARGUMENT_FOR_INLINE_SUSPEND_PARAMETER
                else -> ValueKind.GENERAL
            }
            putValueIfNeeded(parameterType, value, kind, parameterIndex)
        }
    }

    private fun isInlineSuspendParameter(descriptor: ValueParameterDescriptor): Boolean =
        functionDescriptor.isInline && !descriptor.isNoinline && descriptor.type.isSuspendFunctionTypeOrSubtype

    private fun isCallSiteIsSuspend(descriptor: ValueParameterDescriptor): Boolean =
        state.bindingContext[CodegenBinding.CALL_SITE_IS_SUSPEND_FOR_CROSSINLINE_LAMBDA, descriptor] == true

    private fun rememberClosure(expression: KtExpression, type: Type, parameter: ValueParameterDescriptor): LambdaInfo {
        val ktLambda = KtPsiUtil.deparenthesize(expression)
        assert(InlineUtil.isInlinableParameterExpression(ktLambda)) { "Couldn't find inline expression in ${expression.text}" }

        return PsiExpressionLambda(
            ktLambda!!, typeMapper, state.languageVersionSettings,
            parameter.isCrossinline, getBoundCallableReferenceReceiver(expression) != null
        ).also { lambda ->
            val closureInfo = invocationParamBuilder.addNextValueParameter(type, true, null, parameter.index)
            closureInfo.functionalArgument = lambda
            expressionMap.put(closureInfo.index, lambda)
        }
    }

    override fun putValueIfNeeded(parameterType: JvmKotlinType, value: StackValue, kind: ValueKind, parameterIndex: Int) {
        if (processDefaultMaskOrMethodHandler(value, kind)) return

        assert(maskValues.isEmpty()) { "Additional default call arguments should be last ones, but " + value }

        putArgumentOrCapturedToLocalVal(parameterType, value, -1, parameterIndex, kind)
    }

    override fun putCapturedValueOnStack(stackValue: StackValue, valueType: Type, paramIndex: Int) {
        putArgumentOrCapturedToLocalVal(
            JvmKotlinType(stackValue.type, stackValue.kotlinType), stackValue, paramIndex, paramIndex, ValueKind.CAPTURED
        )
    }

    override fun reorderArgumentsIfNeeded(actualArgsWithDeclIndex: List<ArgumentAndDeclIndex>, valueParameterTypes: List<Type>) = Unit

    override fun putHiddenParamsIntoLocals() {
        assert(delayedHiddenWriting != null) { "processAndPutHiddenParameters(true) should be called before putHiddenParamsIntoLocals" }
        delayedHiddenWriting!!.invoke()
        delayedHiddenWriting = null
    }

    companion object {
        internal fun createInlineMethodNode(
            functionDescriptor: FunctionDescriptor,
            jvmSignature: JvmMethodSignature,
            callDefault: Boolean,
            sourceCompilerForInline: SourceCompilerForInline
        ): SMAPAndMethodNode {
            val asmMethod = jvmSignature.asmMethod

            return sourceCompilerForInline.doCreateMethodNodeFromSource(functionDescriptor, jvmSignature, callDefault, asmMethod)
        }
    }
}

internal fun Type.boxReceiverForBoundReference() =
    AsmUtil.boxType(this)

internal fun Type.boxReceiverForBoundReference(kotlinType: KotlinType, typeMapper: KotlinTypeMapper) =
    AsmUtil.boxType(this, kotlinType, typeMapper)