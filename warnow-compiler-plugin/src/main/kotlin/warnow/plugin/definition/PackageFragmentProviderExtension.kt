package warnow.plugin.definition

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import org.jetbrains.kotlin.storage.StorageManager
import warnow.plugin.log.Logging
import warnow.plugin.resolution.AbstractPropertyDefinitionContainer

// TODO maybe move back to 'warnow'
private const val SYNTHETIC_PACKAGE = "warnow.functions"

abstract class AbstractPackageFragmentProviderExtension : PackageFragmentProviderExtension {

    abstract fun getResolutionContainer(project: Project): AbstractPropertyDefinitionContainer
    abstract fun shouldIgnoreModule(module: ModuleDescriptor): Boolean

    override fun getPackageFragmentProvider(
        project: Project,
        module: ModuleDescriptor,
        storageManager: StorageManager,
        trace: BindingTrace,
        moduleInfo: ModuleInfo?,
        lookupTracker: LookupTracker
    ): PackageFragmentProvider? {
        LOG.trace { "module source: $module" }

        if (shouldIgnoreModule(module)) {
            LOG.trace { "skipping module $module" }
            return null
        }

        val packages = mutableMapOf<FqName, PackageFragmentDescriptor>()

        // synthetic warnow package providing the following functions:
        //   define { }
        //   expect { }
        //   access { }
        //   mutate { }

        val fqName = FqName(SYNTHETIC_PACKAGE)

        val resolutionContainer = getResolutionContainer(project)

        packages[fqName] = SyntheticPackageFragmentDescriptor(
            fqName,
            module,
            resolutionContainer,
            storageManager,
            lookupTracker
        )

        dumpContributedPackages(packages)

        return TopLevelPackageFragmentProvider(packages)
    }

    companion object {
        private val LOG = Logging.logger { }
    }
}

class TopLevelPackageFragmentProvider(private val packages: Map<FqName, PackageFragmentDescriptor>) : PackageFragmentProvider {

    override fun getPackageFragments(fqName: FqName): List<PackageFragmentDescriptor> {
        val fragmentDescriptor = packages[fqName]

        return listOfNotNull(fragmentDescriptor)
    }

    override fun getSubPackagesOf(fqName: FqName, nameFilter: (Name) -> Boolean): Collection<FqName> {
        return packages.asSequence()
            .filter { (name, _) -> !name.isRoot && name.parent() == fqName }
            .map { (name, _) -> name }
            .toList()
    }

    companion object {
        val LOG = Logging.logger { }
    }
}

private fun dumpContributedPackages(packages: MutableMap<FqName, PackageFragmentDescriptor>) {
    val logger = Logging.logger("dump")

    logger.debug { "the following packages will be contributed to:" }
    for ((name) in packages) {
        logger.debug { "  $name" }
    }
}



