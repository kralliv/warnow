package warnow.plugin.definition.synthetic

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.LazyClassReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertySetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.MemberScopeImpl
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.Printer
import warnow.plugin.computeIf
import warnow.plugin.log.Logging
import warnow.plugin.marker.Kind
import warnow.plugin.marker.WarnowSyntheticProperty

internal fun generateSyntheticProperty(
    containingDeclaration: DeclarationDescriptor,
    name: String,
    type: KotlinType,
    kind: Kind,
    mutable: Boolean = false
): PropertyDescriptor {
    val property = object : WarnowSyntheticProperty, PropertyDescriptorImpl(
        containingDeclaration,
        null,
        Annotations.EMPTY,
        Modality.FINAL,
        Visibilities.PUBLIC,
        mutable,
        Name.identifier(name),
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        SourceElement.NO_SOURCE,
        false,
        false,
        false,
        false,
        false,
        false
    ) {
        override val kind: Kind
            get() = kind
    }

    val dispatchReceiverParameter = if (containingDeclaration is ClassDescriptor) {
        LazyClassReceiverParameterDescriptor(containingDeclaration)
    } else {
        null
    }

    property.setType(type, emptyList(), dispatchReceiverParameter, null)

    val getter = PropertyGetterDescriptorImpl(
        property,
        Annotations.EMPTY,
        Modality.FINAL,
        Visibilities.PUBLIC,
        false,
        false,
        false,
        CallableMemberDescriptor.Kind.SYNTHESIZED,
        null,
        SourceElement.NO_SOURCE
    )
    getter.initialize(null)

    val setter = computeIf(mutable) {
        val setter = PropertySetterDescriptorImpl(
            property,
            Annotations.EMPTY,
            Modality.FINAL,
            Visibilities.PUBLIC,
            false,
            false,
            false,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            null,
            SourceElement.NO_SOURCE
        )

        val setterParameter = ValueParameterDescriptorImpl(
            setter,
            null,
            1,
            Annotations.EMPTY,
            Name.identifier("field"),
            type,
            false,
            false,
            false,
            null,
            SourceElement.NO_SOURCE
        )
        setter.initialize(setterParameter)

        setter
    }

    property.initialize(getter, setter)

    dumpCallableReceivers(property)

    return property
}

internal fun generateCombinedInterface(
    storageManager: StorageManager,
    containingDeclaration: PackageFragmentDescriptor,
    name: String,
    first: ClassDescriptor,
    second: ClassDescriptor,
    vararg others: ClassDescriptor
): ClassDescriptor {
    val interfaces = listOf(first, second, *others)

    interfaces.forEach { require(it.kind == ClassKind.INTERFACE) }

    val superTypes = interfaces.map(ClassDescriptor::getDefaultType)

    return generateInterface(storageManager, containingDeclaration, name, superTypes = superTypes) { classDescriptor ->
        //return@generateInterface ChainedMemberScope("ddd",scopes = interfaces.map { it.unsubstitutedMemberScope })

        val declarations = interfaces
            .asSequence()
            .map(ClassDescriptor::getUnsubstitutedMemberScope)
            .flatMap { it.getContributedDescriptors().asSequence() }
            .mapNotNull { it.tryCopy(classDescriptor) }
            .toList()

        SimpleMemberScope(declarations)
    }
}

internal class SimpleMemberScope(override val declarations: Collection<DeclarationDescriptor>) : MixedDeclarationsMemberScope()

internal abstract class MixedDeclarationsMemberScope : MemberScopeImpl() {
    protected abstract val declarations: Collection<DeclarationDescriptor>

    override fun getContributedDescriptors(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean): Collection<DeclarationDescriptor> {
        return declarations.filter { kindFilter.accepts(it) && nameFilter(it.name) }
    }

    override fun getContributedClassifier(name: Name, location: LookupLocation): ClassifierDescriptor? {
        return declarations.mapNotNull { it as? ClassDescriptor }.firstOrNull { it.name == name }
    }

    override fun getContributedFunctions(name: Name, location: LookupLocation): Collection<SimpleFunctionDescriptor> {
        return declarations.mapNotNull { it as? SimpleFunctionDescriptor }.filter { it.name == name }
    }

    override fun getContributedVariables(name: Name, location: LookupLocation): Collection<PropertyDescriptor> {
        return declarations.mapNotNull { it as? PropertyDescriptor }.filter { it.name == name }
    }

    override fun printScopeStructure(p: Printer) {
        p.println(this::class.java.simpleName)
    }
}

private fun DeclarationDescriptor.tryCopy(containingDeclaration: DeclarationDescriptor): DeclarationDescriptor? {
    return when (this) {
        is CallableMemberDescriptor -> {
            this.newCopyBuilder()
                .setOwner(containingDeclaration)
                .build()
        }
        is SimpleFunctionDescriptor -> {
            // this.substitute(DescriptorSubstitutor.substituteTypeParameters(listOf(), TypeSubstitution.EMPTY, containingDeclaration, mutableListOf()))
            this.copy(containingDeclaration, this.modality, this.visibility, this.kind, true)
        }
        is PropertyDescriptor -> {
            this.copy(containingDeclaration, this.modality, this.visibility, this.kind, true)
        }
        else -> null
    }
}

private val LOG = Logging.logger { }

internal inline fun generateInterface(
    storageManager: StorageManager,
    containingDeclaration: PackageFragmentDescriptor,
    name: String,
    superTypes: List<KotlinType> = emptyList(),
    memberScopeBuilder: (ClassDescriptor) -> MemberScope = { MemberScope.Empty }
): ClassDescriptor {

    val classDescriptor = ClassDescriptorImpl(
        containingDeclaration,
        Name.identifier(name),
        Modality.FINAL,
        ClassKind.INTERFACE,
        superTypes,
        SourceElement.NO_SOURCE,
        false,
        storageManager
    )

    val memberScope = memberScopeBuilder(classDescriptor)

    classDescriptor.initialize(
        memberScope,
        emptySet(),
        null
    )

    return classDescriptor
}
