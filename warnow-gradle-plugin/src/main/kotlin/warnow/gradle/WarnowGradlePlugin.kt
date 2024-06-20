package warnow.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class WarnowGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create(
            "warnow",
            WarnowGradleExtension::class.java
        )
    }
}

open class WarnowGradleExtension {

    val enabled: Boolean = true
}