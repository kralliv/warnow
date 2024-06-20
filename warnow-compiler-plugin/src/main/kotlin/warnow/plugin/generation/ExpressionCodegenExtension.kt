package warnow.plugin.generation

import org.jetbrains.kotlin.codegen.CallBasedArgumentGenerator
import org.jetbrains.kotlin.codegen.CallableMethod
import org.jetbrains.kotlin.codegen.ExpressionCodegen
import org.jetbrains.kotlin.codegen.OwnerKind
import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.codegen.inline.MethodBodyVisitor
import org.jetbrains.kotlin.codegen.inline.PsiSourceCompilerForInline
import org.jetbrains.kotlin.codegen.inline.remove
import org.jetbrains.kotlin.codegen.optimization.common.InsnSequence
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.LocalVariableNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import org.jetbrains.org.objectweb.asm.tree.TypeInsnNode
import warnow.plugin.definition.synthetic.dumpCallableReceivers
import warnow.plugin.log.Logging
import warnow.plugin.marker.Kind
import warnow.plugin.marker.WarnowSyntheticFunction
import warnow.plugin.marker.WarnowSyntheticProperty
import warnow.plugin.resolution.CallResolutionContainer
import warnow.plugin.resolution.ResolvedPropertyCall
import java.io.File

abstract class WarnowExpressionCodegenExtension(
    private val callResolutionContainer: CallResolutionContainer,
    private val optimizeBytecode: Boolean
) : ExpressionCodegenExtension {

    override fun applyFunction(receiver: StackValue, resolvedCall: ResolvedCall<*>, c: ExpressionCodegenExtension.Context): StackValue? {
        val resultingDescriptor = resolvedCall.resultingDescriptor.original

        if (resultingDescriptor.name.identifier == "initially") {
            dumpCallableReceivers(resultingDescriptor)
        }

        if (resultingDescriptor is WarnowSyntheticFunction) {
            return when (val kind = resultingDescriptor.kind) {
                Kind.DefineFunction, Kind.ExpectFunction -> {
                    val returnType = resultingDescriptor.returnType ?: return null

                    val calleeExpression = resolvedCall.call.calleeExpression!!
                    val propertyCall = callResolutionContainer.resolveStatePropertyCall(calleeExpression, kind == Kind.DefineFunction)

                    if (resultingDescriptor.kind == Kind.DefineFunction && propertyCall.initializer != null) {
                        val (classDescriptor, functionDescriptor) = generateSyntheticInitializerDescriptor(
                            c.codegen.state.module,
                            LockBasedStorageManager.NO_LOCKS,
                            propertyCall.name
                        )

                        generateSyntheticInitializerClass(
                            propertyCall.identifier,
                            propertyCall.initializer,
                            functionDescriptor,
                            classDescriptor,
                            c.typeMapper,
                            c.codegen.state
                        )
                    }

                    StackValue.functionCall(c.typeMapper.mapType(returnType), returnType) { v ->
                        generatePropertyAccessCall(propertyCall, c.codegen, v)
                    }
                }
                Kind.AccessFunction, Kind.MutateFunction, Kind.PackageAccessWithBlockAndContext -> {
                    performIntrinsicInlining(resolvedCall, resultingDescriptor, c.codegen, c.typeMapper)
                }
                Kind.PackageAccessWithContext -> {
                    StackValue.none()
                }
                else -> null
            }
        }

        return super.applyFunction(receiver, resolvedCall, c)
    }

    private fun performIntrinsicInlining(
        resolvedCall: ResolvedCall<*>,
        resultingDescriptor: WarnowSyntheticFunction,
        codegen: ExpressionCodegen,
        typeMapper: KotlinTypeMapper
    ): StackValue? {
        val methodNode = MethodNode(Opcodes.API_VERSION, 0, "fake", "()V", null, null)
        val nestedCodegen = ExpressionCodegen(methodNode, codegen.frameMap, codegen.returnType, codegen.context, codegen.state, codegen.parentCodegen)

        val actualCodegen = if (optimizeBytecode) nestedCodegen else codegen

        val typeMappings = getTypeMapping(resolvedCall, resultingDescriptor, typeMapper)
        val delegateSourceCompiler = PsiSourceCompilerForInline(codegen, resolvedCall.call.callElement)
        val sourceCompiler = SyntheticMethodSourceCompilerForInline(codegen, delegateSourceCompiler)
        val callGenerator = SyntheticMethodInlineCodegen(actualCodegen, codegen.state, resultingDescriptor, typeMappings, sourceCompiler)

        val callable = typeMapper.mapToCallableMethod(resultingDescriptor)

        val returnType = resultingDescriptor.returnType ?: error("resulting descriptor $resultingDescriptor does not specify a return type")
        val type = typeMapper.mapType(returnType)

        val argumentGenerator = CallBasedArgumentGenerator(
            actualCodegen, callGenerator, resultingDescriptor.valueParameters,
            callable.valueParameterTypes
        )

        return StackValue.functionCall(type, returnType) {
            // Generate and put the method arguments onto the stack in order to
            // publish the lambda body argument needed later in the generation.
            // Arguments put onto the stack here must be cleaned up later.
            argumentGenerator.generate(resolvedCall.valueArgumentsByIndex!!, resolvedCall.valueArguments.values.toList(), resultingDescriptor)

            callGenerator.genCall(callable, resolvedCall, false, actualCodegen)

            if (optimizeBytecode) {
                val constructType = resultingDescriptor.valueParameters[1].type.arguments.first().type
                val asmConstructType = typeMapper.mapType(constructType)

                val deletedVariables = mutableSetOf<LocalVariableNode>()
                val delete = mutableSetOf<AbstractInsnNode>()

                methodNode.localVariables.forEach { variable ->
                    when {
                        // TODO figure out whether this should be done
                        // variable.name == "context\$iv" && variable.desc == "Lwarnow/Context;" -> deletedVariables.add(variable)
                        variable.name == "\$this\$${resultingDescriptor.name}" -> deletedVariables.add(variable)
                    }
                }

                InsnSequence(methodNode.instructions).forEach { current ->
                    when {
                        current.opcode == Opcodes.CHECKCAST && current is TypeInsnNode -> {
                            if (current.desc == asmConstructType.internalName) {
                                val previous = current.previous
                                if (previous?.opcode == Opcodes.ACONST_NULL) {
                                    delete.add(previous)
                                }

                                delete.add(current)

                                val next = current.next
                                if (next?.opcode == Opcodes.ASTORE) {
                                    delete.add(next)
                                }
                            }
                        }
                    }
                }

                methodNode.remove(delete)
                methodNode.localVariables.removeAll(deletedVariables)

                methodNode.accept(MethodBodyVisitor(codegen.v))
            }
        }
    }

    private fun KotlinTypeMapper.mapToCallableMethod(functionDescriptor: FunctionDescriptor): CallableMethod {
        val parentDescriptor = functionDescriptor.original.containingDeclaration

        val signature: JvmMethodSignature
        val returnKotlinType: KotlinType?
        val owner: Type
        val ownerForDefaultImpl: Type
        val invokeOpcode: Int
        val dispatcherType: Type?
        val dispatchReceiverKotlinType: KotlinType?
        val isInterfaceMember: Boolean
        val isDefaultMethodInInterface: Boolean

        if (parentDescriptor is ClassDescriptor) {
            val isInterface = parentDescriptor.kind == ClassKind.INTERFACE

            owner = mapClass(parentDescriptor)
            ownerForDefaultImpl = owner

            when {
                isInterface -> {
                    invokeOpcode = Opcodes.INVOKEINTERFACE
                    isInterfaceMember = true
                }
                else -> {
                    invokeOpcode = Opcodes.INVOKEVIRTUAL
                    isInterfaceMember = false
                }
            }

            val functionToCall = functionDescriptor.original

            signature = mapSignatureSkipGeneric(functionToCall)

            returnKotlinType = functionToCall.returnType

            dispatcherType = owner
            dispatchReceiverKotlinType = parentDescriptor.defaultType

            isDefaultMethodInInterface = false
        } else {
            owner = Type.getObjectType("warnow/runtime/SyntheticKt")
            ownerForDefaultImpl = owner

            returnKotlinType = functionDescriptor.returnType

            signature = mapSignatureSkipGeneric(functionDescriptor)
            invokeOpcode = Opcodes.INVOKESTATIC

            dispatcherType = null
            dispatchReceiverKotlinType = null

            isInterfaceMember = false
            isDefaultMethodInInterface = false
        }

        val receiverParameterType: Type?
        val extensionReceiverKotlinType: KotlinType?
        val receiverParameter = functionDescriptor.original.extensionReceiverParameter
        if (receiverParameter != null) {
            extensionReceiverKotlinType = receiverParameter.type
            receiverParameterType = mapType(extensionReceiverKotlinType)
        } else {
            extensionReceiverKotlinType = null
            receiverParameterType = null
        }

        return CallableMethod(
            owner, ownerForDefaultImpl,
            { mapDefaultMethod(functionDescriptor, OwnerKind.PACKAGE).descriptor },
            signature, invokeOpcode,
            dispatcherType, dispatchReceiverKotlinType,
            receiverParameterType, extensionReceiverKotlinType,
            null,
            returnKotlinType,
            isInterfaceMember, isDefaultMethodInInterface
        )
    }

    override fun applyProperty(receiver: StackValue, resolvedCall: ResolvedCall<*>, c: ExpressionCodegenExtension.Context): StackValue? {
        val resultingDescriptor = resolvedCall.resultingDescriptor
        if (resultingDescriptor is WarnowSyntheticProperty) {
            when (val kind = resultingDescriptor.kind) {
                Kind.ValueAccess -> {
                    val calleeExpression = resolvedCall.call.calleeExpression ?: return null
                    val propertyCall = callResolutionContainer.resolveValueAccess(calleeExpression, c.codegen.bindingContext)
                    val type = c.typeMapper.mapType(resultingDescriptor.type)

                    return WarnowPropertyStackValue(propertyCall, c.codegen, c.codegen.state.module, type, resultingDescriptor.type)
                }
                Kind.PackageAccess -> {
                    return StackValue.none()
                }
            }
        }

        return super.applyProperty(receiver, resolvedCall, c)
    }

    companion object {
        private val LOG = Logging.logger { }
    }
}

private fun generatePropertyAccessCall(
    propertyCall: ResolvedPropertyCall,
    codegen: ExpressionCodegen,
    v: InstructionAdapter
) {
    v.aconst(propertyCall.identifier)
    v.putInitializer(propertyCall.identifier)
    v.putContext(propertyCall.contextExpression, codegen)

    v.invokestatic(
        "warnow/runtime/SyntheticKt",
        "obtainDelegateWithin",
        "(Ljava/lang/String;Lwarnow/runtime/Initializer;Lwarnow/Context;)Lwarnow/WarnowProperty;",
        false
    )
}

class WarnowPropertyStackValue(
    private val propertyCall: ResolvedPropertyCall,
    private val codegen: ExpressionCodegen,
    private val module: ModuleDescriptor,
    type: Type,
    kotlinType: KotlinType?
) : StackValue(type, kotlinType) {

    override fun putSelector(topOfStackType: Type, kotlinType: KotlinType?, v: InstructionAdapter) {
        v.aconst(propertyCall.identifier)
        v.putInitializer(propertyCall.identifier)
        v.putContext(propertyCall.contextExpression, codegen)

        v.invokestatic(
            "warnow/runtime/SyntheticKt",
            "getValueWithin",
            "(Ljava/lang/String;Lwarnow/runtime/Initializer;Lwarnow/Context;)Ljava/lang/Object;",
            false
        )
        coerceFrom(Type.getObjectType("java/lang/Object"), module.builtIns.anyType, v)
    }

    override fun store(stackValue: StackValue, v: InstructionAdapter, skipReceiver: Boolean) {
        v.aconst(propertyCall.identifier)
        stackValue.put(stackValue.type, stackValue.kotlinType, v)
        coerceTo(Type.getObjectType("java/lang/Object"), module.builtIns.anyType, v)
        v.putInitializer(propertyCall.identifier)
        v.putContext(propertyCall.contextExpression, codegen)

        v.invokestatic(
            "warnow/runtime/SyntheticKt",
            "setValueWithin",
            "(Ljava/lang/String;Ljava/lang/Object;Lwarnow/runtime/Initializer;Lwarnow/Context;)V",
            false
        )
    }
}

private fun InstructionAdapter.putInitializer(identifier: String) {
    val asmType = getSyntheticInitializerType(identifier)
    getstatic(asmType.internalName, "INSTANCE", asmType.descriptor)
}

private fun InstructionAdapter.putContext(expression: KtExpression?, codegen: ExpressionCodegen) {
    if (expression != null) {
        val value = codegen.gen(expression)
        value.put(value.type, value.kotlinType, this)
    } else {
        getstatic("warnow/GlobalContext", "INSTANCE", "Lwarnow/GlobalContext;")
    }
}

private fun generateSyntheticInitializerClass(
    identifier: String,
    expression: KtExpression,
    descriptor: FunctionDescriptor,
    classContext: ClassDescriptor,
    typeMapper: KotlinTypeMapper,
    state: GenerationState
) {
    val asmType = getSyntheticInitializerType(identifier)

    val classBuilder = state.factory.newVisitor(
        JvmDeclarationOrigin.NO_ORIGIN,
        asmType,
        listOf<File>()
    )

    val element = PropertyInitializationBlock(expression)
    val strategy = PropertyInitializerGenerationStrategy(state, element)
    val context = PropertyInitializerContext(typeMapper, classContext)

    InitializerCodegen(asmType, expression, descriptor, strategy, context, state, classBuilder).generate()
}

private fun getSyntheticInitializerType(identifier: String): Type {
    val packageName = identifier.substringBeforeLast('.', missingDelimiterValue = "").replace('.', '/')
    val name = identifier.substringAfterLast('.').capitalize()

    return Type.getObjectType("warnow/synthetic/" + packageName.appendUnlessEmpty("/") + name + "Initializer")
}

private fun String.appendUnlessEmpty(string: String): String {
    return if (isEmpty()) {
        this
    } else {
        this + string
    }
}