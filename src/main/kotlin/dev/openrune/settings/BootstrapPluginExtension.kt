package dev.openrune.settings

import dev.openrune.upload.ftp.FtpUploadSettings
import dev.openrune.upload.github.GithubUploadSettings
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.File

/**
 * Extension class for configuring settings related to the OpenRune Bootstrap plugin.
 *
 * This class provides properties and methods to customize various aspects of the plugin's behavior.
 *
 * @property project The Gradle project instance.
 */
open class BootstrapPluginExtension(project: Project) {
    private val objects = project.objects
    private val providers = project.providers

    /**
     * Gets the appropriate application data path based on the current operating system.
     *
     * @return The application data path.
     */
    fun getAppDataPath(): String {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            "windows" in osName -> System.getenv("APPDATA") // Windows
            "mac" in osName -> "${System.getProperty("user.home")}/Library/Application Support" // macOS
            "linux" in osName -> "${System.getProperty("user.home")}/.local/share" // Linux
            else -> throw UnsupportedOperationException("Unsupported operating system: $osName")
        }
    }

    // Store Old Versions of your client based on version in your build config
    val storeOldVersions: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    // Assuming you are within a Gradle task or similar context
    val runeliteArtifacts: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())

    // Replace the default bootstrap template with a custom one
    val bootstrapTemplate: Property<File> = objects.property(File::class.java).convention(File("na"))

    // Change the default location where temp files are stored
    val storeLocation: Property<File> = objects.property(File::class.java).convention(File(getAppDataPath(), "${project.name}/"))

    // Change the build type naming of the client (e.g., live, beta, dev) to store files separately
    val buildType: Property<String> = objects.property(String::class.java).convention("live")

    // Link where your client files are stored (e.g., "https://openrune.com/client/")
    val downloadRoot: Property<String> = objects.property(String::class.java).convention("https://repo.example.com")

    // Configuration for GitHub upload settings
    val githubUpload: Property<GithubUploadSettings?> = objects.property(GithubUploadSettings::class.java)
        .convention(providers.provider<GithubUploadSettings?> { null })

    // Configuration for FTP upload settings
    val ftpUpload: Property<FtpUploadSettings?> = objects.property(FtpUploadSettings::class.java)
        .convention(providers.provider<FtpUploadSettings?> { null })

    /**
     * Configures GitHub upload settings.
     *
     * @param action The action to configure GitHub upload settings.
     */
    fun github(action: GithubUploadSettings.() -> Unit) {
        val githubUploadValue = githubUpload.orNull ?: GithubUploadSettings("", "")
        githubUploadValue.apply(action)
        githubUpload.set(githubUploadValue)
    }

    /**
     * Configures FTP upload settings.
     *
     * @param action The action to configure FTP upload settings.
     */
    fun ftp(action: FtpUploadSettings.() -> Unit) {
        val ftpUploadValue = ftpUpload.orNull ?: FtpUploadSettings("", 21, "", "")
        ftpUploadValue.apply(action)
        ftpUpload.set(ftpUploadValue)
    }
}