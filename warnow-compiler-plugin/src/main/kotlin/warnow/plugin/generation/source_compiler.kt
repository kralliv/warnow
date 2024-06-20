package warnow.plugin.generation

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.BaseExpressionCodegen
import org.jetbrains.kotlin.codegen.ExpressionCodegen
import org.jetbrains.kotlin.codegen.MemberCodegen
import org.jetbrains.kotlin.codegen.OwnerKind
import org.jetbrains.kotlin.codegen.context.CodegenContext
import org.jetbrains.kotlin.codegen.context.FieldOwnerContext
import org.jetbrains.kotlin.codegen.inline.DefaultSourceMapper
import org.jetbrains.kotlin.codegen.inline.ExpressionLambda
import org.jetbrains.kotlin.codegen.inline.FileMapping
import org.jetbrains.kotlin.codegen.inline.InlineCallSiteInfo
import org.jetbrains.kotlin.codegen.inline.NameGenerator
import org.jetbrains.kotlin.codegen.inline.PsiSourceCompilerForInline
import org.jetbrains.kotlin.codegen.inline.SMAP
import org.jetbrains.kotlin.codegen.inline.SMAPAndMethodNode
import org.jetbrains.kotlin.codegen.inline.SourceCompilerForInline
import org.jetbrains.kotlin.codegen.inline.wrapWithMaxLocalCalc
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPureElement
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import org.jetbrains.org.objectweb.asm.commons.Method
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import warnow.plugin.log.Logging

class SyntheticMethodSourceCompilerForInline(val codegen: ExpressionCodegen, val delegate: SourceCompilerForInline) : SourceCompilerForInline {
    override val callElement: Any
        get() = delegate.callElement
    override val callElementText: String
        get() = delegate.callElementText
    override val callsiteFile: PsiFile?
        get() = delegate.callsiteFile
    override val compilationContextDescriptor: DeclarationDescriptor
        get() = delegate.compilationContextDescriptor
    override val compilationContextFunctionDescriptor: FunctionDescriptor
        get() = delegate.compilationContextFunctionDescriptor
    override val contextKind: OwnerKind
        get() = delegate.contextKind
    override val inlineCallSiteInfo: InlineCallSiteInfo
        get() = delegate.inlineCallSiteInfo
    override val lazySourceMapper: DefaultSourceMapper
        get() = delegate.lazySourceMapper
    override val lookupLocation: LookupLocation
        get() = delegate.lookupLocation
    override val state: GenerationState
        get() = delegate.state

    private lateinit var context: CodegenContext<*>
    private val additionalInnerClasses = mutableListOf<ClassDescriptor>()

    override fun doCreateMethodNodeFromSource(
        callableDescriptor: FunctionDescriptor,
        jvmSignature: JvmMethodSignature,
        callDefault: Boolean,
        asmMethod: Method
    ): SMAPAndMethodNode {
       //val element = callableDescriptor.source.getPsi() as? KtDeclarationWithBody ?: error("missing body for $callableDescriptor")

        val node = MethodNode(
            Opcodes.API_VERSION,
            AsmUtil.getMethodAsmFlags(callableDescriptor, context.contextKind, state) or if (callDefault) Opcodes.ACC_STATIC else 0,
            asmMethod.name,
            asmMethod.descriptor, null, null
        )

        val maxCalcAdapter = wrapWithMaxLocalCalc(node)
//        val parentContext = context.parentContext ?: error("Context has no parent: " + context)
//        val methodContext = parentContext.intoFunction(callableDescriptor)

        generateMethodBody(callableDescriptor, InstructionAdapter(maxCalcAdapter))

        maxCalcAdapter.visitMaxs(-1, -1)
        maxCalcAdapter.visitEnd()

        return SMAPAndMethodNode(node, SMAP(listOf(FileMapping.SKIP)))
    }

    private fun generateMethodBody(callableDescriptor: FunctionDescriptor, v: InstructionAdapter) {
        val methodName = callableDescriptor.name

        val startLabel = Label()
        val methodStart = Label()
        val endLabel = Label()

        v.visitLocalVariable("context", "Lwarnow/Context;", null, startLabel, endLabel, 0)
        v.visitLocalVariable("block", "Lkotlin/jvm/functions/Function1;", null, startLabel, endLabel, 1)
        v.visitLocalVariable("\$i\$f\$$methodName", "I", null, methodStart, endLabel, 2)

        v.visitLabel(startLabel)

        v.iconst(0)
        v.store(2, Type.INT_TYPE)

        v.visitLabel(methodStart)

        v.visitLineNumber(-1, startLabel)
        v.load(1, Type.getObjectType("kotlin/jvm/functions/Function1"))
        v.aconst(null)

        v.invokeinterface("kotlin/jvm/functions/Function1", "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;")
        v.areturn(Type.getObjectType("java/lang/Object"))

        v.visitLabel(endLabel)
    }

    private fun createSMAPWithDefaultMapping(
        declaration: KtExpression,
        mappings: List<FileMapping>
    ): SMAP {
        val containingFile = declaration.containingFile
        getLineNumberForElement(containingFile, true) ?: error("Couldn't extract line count in $containingFile")

        return SMAP(mappings)
    }

    @Suppress("UNCHECKED_CAST")
    private class FakeMemberCodegen(
        internal val delegate: MemberCodegen<*>,
        declaration: KtElement,
        codegenContext: FieldOwnerContext<*>,
        private val className: String,
        private val parentAsInnerClasses: List<ClassDescriptor>,
        private val isInlineLambdaCodegen: Boolean
    ) : MemberCodegen<KtPureElement>(delegate as MemberCodegen<KtPureElement>, declaration, codegenContext) {

        override fun generateDeclaration() {
            throw IllegalStateException()
        }

        override fun generateBody() {
            throw IllegalStateException()
        }

        override fun generateKotlinMetadataAnnotation() {
            throw IllegalStateException()
        }

        override fun getInlineNameGenerator(): NameGenerator {
            return delegate.inlineNameGenerator
        }

        override //TODO: obtain name from context
        fun getClassName(): String {
            return className
        }

        override fun addParentsToInnerClassesIfNeeded(innerClasses: MutableCollection<ClassDescriptor>) {
            if (isInlineLambdaCodegen) {
                super.addParentsToInnerClassesIfNeeded(innerClasses)
            } else {
                innerClasses.addAll(parentAsInnerClasses)
            }
        }

        override fun generateAssertField() {
            delegate.generateAssertField()
        }
    }

    override fun generateLambdaBody(
        adapter: MethodVisitor,
        jvmMethodSignature: JvmMethodSignature,
        lambdaInfo: ExpressionLambda
    ): SMAP = delegate.generateLambdaBody(adapter, jvmMethodSignature, lambdaInfo)

    override fun getContextLabels(): Set<String> = delegate.getContextLabels()

    override fun initializeInlineFunctionContext(functionDescriptor: FunctionDescriptor) {
        delegate.initializeInlineFunctionContext(functionDescriptor)

        context = PsiSourceCompilerForInline.getContext(
            functionDescriptor,
            functionDescriptor,
            state,
            DescriptorToSourceUtils.descriptorToDeclaration(functionDescriptor)?.containingFile as? KtFile,
            additionalInnerClasses
        )
    }

    override fun isCallInsideSameModuleAsDeclared(functionDescriptor: FunctionDescriptor): Boolean =
        delegate.isCallInsideSameModuleAsDeclared(functionDescriptor)

    override fun isFinallyMarkerRequired(): Boolean = delegate.isFinallyMarkerRequired()

    override fun createCodegenForExternalFinallyBlockGenerationOnNonLocalReturn(finallyNode: MethodNode, curFinallyDepth: Int): BaseExpressionCodegen {
        return delegate.createCodegenForExternalFinallyBlockGenerationOnNonLocalReturn(finallyNode, curFinallyDepth)
    }

    override fun generateFinallyBlocksIfNeeded(finallyCodegen: BaseExpressionCodegen, returnType: Type, afterReturnLabel: Label) {
        return delegate.generateFinallyBlocksIfNeeded(finallyCodegen, returnType, afterReturnLabel)
    }

    override fun hasFinallyBlocks(): Boolean {
        return delegate.hasFinallyBlocks()
    }

    companion object {
        private val LOG = Logging.logger { }
    }
}