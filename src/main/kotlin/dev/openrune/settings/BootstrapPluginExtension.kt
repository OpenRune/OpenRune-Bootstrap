package dev.openrune.settings

import dev.openrune.upload.ftp.FtpUploadSettings
import dev.openrune.upload.github.GithubUploadSettings
import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.io.File

open class BootstrapPluginExtension(project: Project) {
    private val objects = project.objects
    private val providers = project.providers

    fun getAppDataPath(): String {
        val osName = System.getProperty("os.name").toLowerCase()
        return when {
            "windows" in osName -> System.getenv("APPDATA") // Windows
            "mac" in osName -> "${System.getProperty("user.home")}/Library/Application Support" // macOS
            "linux" in osName -> "${System.getProperty("user.home")}/.local/share" // Linux
            else -> throw UnsupportedOperationException("Unsupported operating system: $osName")
        }
    }

    val storeOldVersions: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    val bootstrapTemplate: Property<File> = objects.property(File::class.java).convention(File("na"))

    val storeLocation: Property<File> = objects.property(File::class.java).convention(File(getAppDataPath(), "${project.name}/"))

    val buildType: Property<String> = objects.property(String::class.java).convention("live")

    val downloadRoot: Property<String> = objects.property(String::class.java).convention("https://repo.example.com")

    val githubUpload: Property<GithubUploadSettings?> = objects.property(GithubUploadSettings::class.java).convention(providers.provider<GithubUploadSettings?> { null })

    val ftpUpload: Property<FtpUploadSettings?> = objects.property(FtpUploadSettings::class.java).convention(providers.provider<FtpUploadSettings?> { null })

    fun github(action: GithubUploadSettings.() -> Unit) {
        val githubUploadValue = githubUpload.orNull ?: GithubUploadSettings("", "")
        githubUploadValue.apply(action)
        githubUpload.set(githubUploadValue)
    }

    fun ftp(action: FtpUploadSettings.() -> Unit) {
        val ftpUploadValue = ftpUpload.orNull ?: FtpUploadSettings("", 21,"","")
        ftpUploadValue.apply(action)
        ftpUpload.set(ftpUploadValue)
    }


}
