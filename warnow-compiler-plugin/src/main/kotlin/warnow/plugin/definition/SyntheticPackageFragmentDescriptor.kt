package warnow.plugin.definition

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.PackageFragmentDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.recordPackageLookup
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.StorageManager
import warnow.plugin.definition.synthetic.MixedDeclarationsMemberScope
import warnow.plugin.definition.synthetic.SimpleMemberScope
import warnow.plugin.definition.synthetic.generateInterface
import warnow.plugin.definition.synthetic.generatePropertyAccessDeclarations
import warnow.plugin.definition.synthetic.generatePropertyDefinitionDeclarations
import warnow.plugin.definition.synthetic.generatePropertyDefinitionFunction
import warnow.plugin.definition.synthetic.generatePropertyExpectDeclarations
import warnow.plugin.definition.synthetic.generatePropertyExpectFunction
import warnow.plugin.definition.synthetic.generateValueAccessConstruct
import warnow.plugin.definition.synthetic.generateValueAccessFunction
import warnow.plugin.definition.synthetic.generateValueUpdateFunction
import warnow.plugin.log.Logging
import warnow.plugin.resolution.AbstractPropertyDefinitionContainer
import warnow.plugin.resolution.StatePackage
import java.util.concurrent.atomic.AtomicInteger

private val counter = AtomicInteger(0)

class SyntheticPackageFragmentDescriptor(
    val packageName: FqName,
    val module: ModuleDescriptor,
    val resolutionContainer: AbstractPropertyDefinitionContainer,
    val storageManager: StorageManager,
    private val lookupTracker: LookupTracker
) : PackageFragmentDescriptorImpl(module, packageName) {

    private val scope = SyntheticPropertiesScope()
    override fun getMemberScope(): MemberScope = scope

    private inner class SyntheticPropertiesScope : MixedDeclarationsMemberScope() {

        private val reference = counter.getAndIncrement()

        override val declarations: List<DeclarationDescriptor> by lazy {
            if (!DeclarationGenerationContext.isModuleApplicable(module)) {
                return@lazy listOf<DeclarationDescriptor>()
            }

            val resolver = TypeResolver(module, storageManager)

            val topLevelStatePackage = resolutionContainer.resolve(resolver)

            dumpStatePackageHierarchy(topLevelStatePackage)

            LOG.debug { "computing synthetic descriptors $reference" }

            val packageFragmentDescriptor = this@SyntheticPackageFragmentDescriptor
            val declarations = mutableListOf<DeclarationDescriptor>()

            val context = DeclarationGenerationContext(storageManager, module, lookupTracker, CountingNamingStrategy())

            /*
            val propertyAccessConstruct = generatePropertyAccessConstruct(
                context,
                packageFragmentDescriptor,
                "PropertyAccessConstruct",
                topLevelStatePackage
            )

            val propertyDefinitionInterface = generatePropertyDefinitionInterface(context, packageFragmentDescriptor)
            val propertyExpectInterface = generatePropertyExpectInterface(context, packageFragmentDescriptor)

            val propertyDefinitionBuilder = generateCombinedInterface(
                context.storageManager,
                packageFragmentDescriptor,
                "PropertyDefinitionBuilder",
                propertyAccessConstruct,
                propertyDefinitionInterface
            )


            val propertyExpectBuilder = generateCombinedInterface(
                context.storageManager,
                packageFragmentDescriptor,
                "PropertyExpectBuilder",
                propertyAccessConstruct,
                propertyExpectInterface
            )
            */


            val propertyDefinitionBuilder = generateInterface(
                storageManager,
                packageFragmentDescriptor,
                name = "PropertyDefinitionBuilder"
            ) { classDescriptor ->
                val declarations = mutableListOf<DeclarationDescriptor>()

                declarations += generatePropertyAccessDeclarations(
                    context,
                    packageFragmentDescriptor,
                    classDescriptor,
                    "PropertyAccessConstruct",
                    topLevelStatePackage
                )

                declarations += generatePropertyDefinitionDeclarations(context, classDescriptor)

                SimpleMemberScope(declarations)
            }

            val propertyExpectBuilder = generateInterface(
                storageManager,
                packageFragmentDescriptor,
                name = "PropertyExpectBuilder"
            ) { classDescriptor ->
                val declarations = mutableListOf<DeclarationDescriptor>()

                declarations += generatePropertyAccessDeclarations(
                    context,
                    packageFragmentDescriptor,
                    classDescriptor,
                    "PropertyAccessConstruct",
                    topLevelStatePackage
                )

                declarations += generatePropertyExpectDeclarations(context, classDescriptor)

                SimpleMemberScope(declarations)
            }

            declarations += propertyDefinitionBuilder
            declarations += propertyExpectBuilder

            val valueAccessConstruct = generateValueAccessConstruct(context, packageFragmentDescriptor, topLevelStatePackage, mutable = false)
            val mutableValueAccessConstruct = generateValueAccessConstruct(context, packageFragmentDescriptor, topLevelStatePackage, mutable = true)

            declarations += valueAccessConstruct
            declarations += mutableValueAccessConstruct

            val propertyDefinitionBuilderType = propertyDefinitionBuilder.defaultType
            val propertyAccessBuilderType = propertyExpectBuilder.defaultType

            declarations += generatePropertyDefinitionFunction(context, packageFragmentDescriptor, propertyDefinitionBuilderType)
            declarations += generatePropertyExpectFunction(context, packageFragmentDescriptor, propertyAccessBuilderType)

            val valueAccessConstructType = valueAccessConstruct.defaultType
            val mutableValueAccessConstructType = mutableValueAccessConstruct.defaultType

            declarations += generateValueAccessFunction(context, packageFragmentDescriptor, valueAccessConstructType)
            declarations += generateValueUpdateFunction(context, packageFragmentDescriptor, mutableValueAccessConstructType)

            declarations
        }

        override fun recordLookup(name: Name, location: LookupLocation) {
            lookupTracker.recordPackageLookup(location, packageName.asString(), "<STATE-PROPERTY>")
        }
    }

    companion object {
        private val LOG = Logging.logger { }
    }
}

private fun dumpStatePackageHierarchy(statePackage: StatePackage) {
    val logger = Logging.logger("dump")

    logger.debug { "package ${statePackage.name}" }

    for (stateProperty in statePackage.properties) {
        logger.debug { "  property ${stateProperty.name} uncomputed" }
    }

    for (nestedStatePackage in statePackage.packages) {
        dumpStatePackageHierarchy(nestedStatePackage)
    }
}
