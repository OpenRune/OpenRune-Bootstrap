package dev.openrune

import dev.openrune.settings.BootstrapPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*


class BootstrapPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create the plugin extension
        val extension = project.extensions.create(
            "releaseSettings",
            BootstrapPluginExtension::class.java,
            project
        )

        project.afterEvaluate {
            registerPublishClientLiveTask(project, extension)
            registerGenerateKeysTask(project, extension)
            registerGenerateFilesTask(project, extension)
        }
    }

    private fun registerPublishClientLiveTask(project: Project, extension: BootstrapPluginExtension) {
        project.tasks.register("publishClientLive") {
            group = "client update"
            description = "Publishes Client to your ftp or aws"
            dependsOn("generateFiles")
            doLast {
                BootstrapTask(extension, project).init()
            }
        }
    }

    private fun registerGenerateKeysTask(project: Project, extension: BootstrapPluginExtension) {
        // Register the 'generateKeys' task
        project.tasks.register("generateKeys") {
            group = "client update"
            description = "Generates security keys for the client launcher (${extension.storeLocation.get().path})"

            doLast {
                Keys.generateKeys(extension.storeLocation.get())
            }
        }
    }

    private fun registerGenerateFilesTask(project: Project, extension: BootstrapPluginExtension) {
        // Register the 'generateFiles' task
        project.tasks.register("generateFiles") {
            group = "client update"
            description = "Generates Client Files for Uploading"
            val bootstrapDependencies by project.configurations.creating {
                isCanBeConsumed = false
                isCanBeResolved = true
                isTransitive = false
            }
            dependsOn(bootstrapDependencies)
            dependsOn("jar")

            doLast {
                BootstrapTask(extension, project).init(false)
            }
        }
    }
}
