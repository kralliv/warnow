package warnow.plugin.generation

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.storage.StorageManager
import warnow.plugin.definition.synthetic.SimpleMemberScope

internal fun generateSyntheticInitializerDescriptor(
    module: ModuleDescriptor,
    storageManager: StorageManager,
    name: String
): Pair<ClassDescriptor, FunctionDescriptor> {
    val initializerClassName = FqName("warnow.runtime.Initializer")
    val initializerClassDescriptor = module.findClassAcrossModuleDependencies(ClassId.topLevel(initializerClassName))
        ?: error("missing initializer class: $initializerClassName")

    val initializeFunctionDescriptor = initializerClassDescriptor.unsubstitutedMemberScope.getContributedFunctions(
        Name.identifier("createInitialValue"),
        NoLookupLocation.FROM_SYNTHETIC_SCOPE
    ).firstOrNull() ?: error("missing createInitialValue() function: $initializerClassDescriptor")

    val classDescriptor = ClassDescriptorImpl(
        initializerClassDescriptor.containingDeclaration,
        Name.identifier(name + "Initializer"),
        Modality.FINAL,
        ClassKind.CLASS,
        listOf(initializerClassDescriptor.defaultType),
        SourceElement.NO_SOURCE,
        false,
        storageManager
    )

    val functionDescriptor = initializeFunctionDescriptor.newCopyBuilder()
        .setOwner(classDescriptor)
        .setModality(Modality.FINAL)
        .build() ?: error("cannot create appropriate function out of abstract descriptor: $initializeFunctionDescriptor")

    val memberScope = SimpleMemberScope(listOf(functionDescriptor))

    classDescriptor.initialize(
        memberScope,
        emptySet(),
        null
    )

    return classDescriptor to functionDescriptor
}