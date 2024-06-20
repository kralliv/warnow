package warnow.gradle

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinGradleSubplugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@AutoService(KotlinGradleSubplugin::class) // don't forget!
class WarnowGradleSubplugin : KotlinGradleSubplugin<AbstractCompile> {

    override fun isApplicable(project: Project, task: AbstractCompile) =
        project.plugins.hasPlugin(WarnowGradlePlugin::class.java)

    override fun apply(
        project: Project,
        kotlinCompile: AbstractCompile,
        javaCompile: AbstractCompile?,
        variantData: Any?,
        androidProjectHandler: Any?,
        kotlinCompilation: KotlinCompilation<KotlinCommonOptions>?
    ): List<SubpluginOption> {
        val extension = project.extensions.findByType(WarnowGradleExtension::class.java)
            ?: WarnowGradleExtension()

        return listOf(SubpluginOption(key = "enabled", value = extension.enabled.toString()))
    }

    /**
     * Just needs to be consistent with the key for DebugLogCommandLineProcessor#pluginId
     */
    override fun getCompilerPluginId(): String = "warnow"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "warnow",
        artifactId = "warnow-compiler-plugin-embeddable",
        version = "0.0.1" // remember to bump this version before any release!
    )
}