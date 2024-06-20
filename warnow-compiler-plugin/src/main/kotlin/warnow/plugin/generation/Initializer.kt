package warnow.plugin.generation

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.codegen.ClassBuilder
import org.jetbrains.kotlin.codegen.FunctionCodegen
import org.jetbrains.kotlin.codegen.FunctionGenerationStrategy
import org.jetbrains.kotlin.codegen.MemberCodegen
import org.jetbrains.kotlin.codegen.OwnerKind
import org.jetbrains.kotlin.codegen.context.ClassContext
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.codegen.writeKotlinMetadata
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.resolve.jvm.diagnostics.OtherOrigin
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

class InitializerCodegen(
    val asmType: Type,
    expression: KtExpression,
    val descriptor: FunctionDescriptor,
    val strategy: FunctionGenerationStrategy,
    context: PropertyInitializerContext,
    state: GenerationState,
    builder: ClassBuilder
) : MemberCodegen<KtExpression>(state, null, context, expression, builder) {

    override fun generateDeclaration() {
        v.defineClass(
            element,
            state.classFileVersion,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC,
            asmType.internalName,
            null,
            "java/lang/Object",
            arrayOf("warnow/runtime/Initializer")
        )
    }

    override fun generateBody() {
        generateInitializationMethod()

        generateConstructor()

        generateConstInstance(asmType, asmType)
    }

    private fun generateConstructor() {
        val mv = v.newMethod(
            OtherOrigin(element, descriptor),
            Opcodes.ACC_PRIVATE or Opcodes.ACC_SYNTHETIC,
            "<init>",
            "()V",
            null,
            null
        )

        val iv = InstructionAdapter(mv)

        iv.visitCode()

        iv.visitVarInsn(Opcodes.ALOAD, 0)
        iv.invokespecial("java/lang/Object", "<init>", "()V", false)
        iv.visitInsn(Opcodes.RETURN)

        FunctionCodegen.endVisit(iv, "constructor", element)
    }

    private fun generateInitializationMethod() {
        functionCodegen.generateMethod(OtherOrigin(element, descriptor), descriptor, strategy)
    }

    override fun generateKotlinMetadataAnnotation() {
        writeKotlinMetadata(v, state, KotlinClassHeader.Kind.SYNTHETIC_CLASS, 0) {
            // TODO figure out if there should be anything generate here
        }
    }
}

class PropertyInitializerContext(
    typeMapper: KotlinTypeMapper,
    classDescriptor: ClassDescriptor
) : ClassContext(typeMapper, classDescriptor, OwnerKind.IMPLEMENTATION, null, null) {

    override fun toString(): String {
        return "Property Initializer: $contextDescriptor"
    }
}

class PropertyInitializerGenerationStrategy(state: GenerationState, declaration: KtDeclarationWithBody) :
    FunctionGenerationStrategy.FunctionDefault(state, declaration)

class PropertyInitializationBlock(private val expression: KtExpression) : KtDeclarationWithBody, KtExpression by expression, KtModifierListOwner {

    override fun getBodyExpression(): KtExpression? = expression

    override fun hasBody(): Boolean = true
    override fun hasBlockBody(): Boolean = false
    override fun hasDeclaredReturnType(): Boolean = true

    override fun getAnnotationEntries(): List<KtAnnotationEntry> = emptyList()
    override fun getAnnotations(): List<KtAnnotation> = emptyList()
    override fun addAnnotationEntry(annotationEntry: KtAnnotationEntry): KtAnnotationEntry = error("unsupported")

    override fun getEqualsToken(): PsiElement? = null
    override fun getDocComment(): KDoc? = null

    override fun getValueParameters(): List<KtParameter> = emptyList()

    override fun getModifierList(): KtModifierList? = null
    override fun hasModifier(modifier: KtModifierKeywordToken): Boolean = false
    override fun addModifier(modifier: KtModifierKeywordToken) = error("unsupported")
    override fun removeModifier(modifier: KtModifierKeywordToken) = error("unsupported")
}