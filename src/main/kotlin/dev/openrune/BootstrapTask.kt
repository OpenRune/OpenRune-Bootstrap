package dev.openrune

import com.beust.klaxon.Klaxon
import com.google.gson.GsonBuilder
import dev.openrune.settings.BootstrapPluginExtension
import dev.openrune.upload.ftp.FTPUploader
import dev.openrune.upload.github.GithubUploader
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.http.isSuccess
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.system.measureTimeMillis


data class ArtifactsData(
    val localArtifacts: List<ResolvedArtifact> = mutableListOf(),
    val onlineArtifacts: List<ResolvedArtifact> = mutableListOf(),
    val fileArtifacts: List<File> = mutableListOf()
)

class BootstrapTask(
    private val extension: BootstrapPluginExtension,
    private val project: Project
) {
    private val httpClient = HttpClient(CIO)

    fun init(upload: Boolean = true) = runBlocking {
        project.logger.lifecycle("Initialization started with upload set to $upload")

        if (extension.downloadRoot.get() == "https://repo.example.com") {
            project.logger.error("Download root not defined. Please define it, for example: https://repo.example.com/live/bootstrap.json")
            return@runBlocking
        }

        val saveLocation = extension.storeLocation.get()
        val keyLocation = File(saveLocation, "key-private.pem")

        if (!keyLocation.exists()) {
            project.logger.warn("Key not found. Generating new keys at $saveLocation")
            Keys.generateKeys(saveLocation)
        }

        val defaultBootstrap = getDefaultBootstrap()
        project.logger.lifecycle("Collecting artifacts")
        val allArtifacts = getArtifacts()

        val projectVersion = project.version.toString()
        val storeInVersions = extension.storeOldVersions.get() && projectVersion != "unspecified"
        val customLibURL = "${extension.downloadRoot.get()}${extension.buildType.get()}/repo${if (storeInVersions) "/$projectVersion" else ""}/"

        val totalArtifacts = allArtifacts.localArtifacts.size + allArtifacts.fileArtifacts.size + allArtifacts.onlineArtifacts.size
        var progressBar = ProgressBar(totalArtifacts)
        val artifacts = ConcurrentLinkedQueue<BootstrapManifest.Artifacts>()

        val timeMillis = measureTimeMillis {
            coroutineScope {
                allArtifacts.localArtifacts.forEach { artifact ->
                    launch {
                        artifacts += processArtifact(artifact, customLibURL)
                        progressBar.update()
                    }
                }

                allArtifacts.fileArtifacts.forEach { artifact ->
                    launch {
                        artifacts += processArtifactFile(artifact, customLibURL)
                        progressBar.update()
                    }
                }

                allArtifacts.onlineArtifacts.forEach { artifact ->
                    launch {
                        artifacts += processArtifact(artifact)
                        progressBar.update()
                    }
                }
            }
        }
        println()

        project.logger.lifecycle("Processing completed in $timeMillis ms. Total artifacts processed: ${artifacts.size}")

        defaultBootstrap.artifacts = artifacts.toTypedArray()
        defaultBootstrap.dependencyHashes = artifacts.associate { it.name to it.hash }

        val filesToUpload = mutableListOf<File>()

        val saveLocationRoot = File(saveLocation, extension.buildType.get())
        saveLocationRoot.mkdirs()

        val bootstrapLocation = File(saveLocationRoot, "bootstrap.json")
        val bootstrapLocationSha256 = File(saveLocationRoot, "bootstrap.json.sha256")

        bootstrapLocation.writeText(GsonBuilder().setPrettyPrinting().create().toJson(defaultBootstrap))
        Keys.sha256(keyLocation, bootstrapLocation, bootstrapLocationSha256)

        filesToUpload += listOf(bootstrapLocation, bootstrapLocationSha256)

        var repoLocation = File(saveLocationRoot, "repo")
        repoLocation.mkdirs()

        if (storeInVersions) {
            project.logger.lifecycle("Creating versioned backups")
            repoLocation = File(repoLocation, projectVersion)
            repoLocation.mkdirs()

            val bootstrapLocationBackUp = File(repoLocation, "bootstrap-backup-$projectVersion.json")
            val bootstrapLocationSha256BackUp = File(repoLocation, "bootstrap-backup-$projectVersion.json.sha256")

            bootstrapLocation.copyTo(bootstrapLocationBackUp, true)
            bootstrapLocationSha256.copyTo(bootstrapLocationSha256BackUp, true)

            filesToUpload += listOf(bootstrapLocationBackUp, bootstrapLocationSha256BackUp)
        }

        allArtifacts.localArtifacts.map { it.file }.plus(allArtifacts.fileArtifacts).forEach { file ->
            val destination = File(repoLocation, file.name)
            file.copyTo(destination, overwrite = true)
            filesToUpload += destination
        }

        if (upload) {
            with(extension) {
                githubUpload.orNull?.let { github ->
                    project.logger.lifecycle("Starting Upload: Github")
                    GithubUploader(project.logger, github, saveLocationRoot.toPath(), storeInVersions, projectVersion, buildType.get()).start()
                } ?: ftpUpload.orNull?.let { ftp ->
                    project.logger.lifecycle("Starting Upload: Ftp")
                    FTPUploader(project.logger, ftp, saveLocationRoot.toPath(), buildType.get()).start()
                } ?: project.logger.lifecycle("No Upload Method found")
            }
        }
    }

    private fun processArtifactFile(artifact: File, urlPath: String): BootstrapManifest.Artifacts {
        return BootstrapManifest.Artifacts(hash(artifact.readBytes()), artifact.name, urlPath + artifact.name, artifact.length(), mutableListOf())
    }

    private suspend fun processArtifact(artifact: ResolvedArtifact, customPath: String = ""): BootstrapManifest.Artifacts {
        val (group, name, version) = artifact.moduleVersion.id.toString().split(":")
        var path = if (customPath.isNotEmpty()) customPath + artifact.file.name else getArtifactURL(artifact,name)
        val file = artifact.file
        var platform: MutableList<BootstrapManifest.Platform>? = null
        if (artifact.classifier != null && group == "runelite") {
            platform = emptyList<BootstrapManifest.Platform>().toMutableList()

            if (artifact.classifier!!.contains("linux")) {
                platform.add(BootstrapManifest.Platform(null,"linux"))
            } else if (artifact.classifier!!.contains("windows")) {

                val arch = if (artifact.classifier!!.contains("amd64")) {
                    "amd64"
                } else {
                    "x86"
                }
                platform.add(BootstrapManifest.Platform(arch,"windows"))
            } else if (artifact.classifier!!.contains("macos")) {

                val arch = if (artifact.classifier!!.contains("x64")) {
                    "x86_64"
                } else if (artifact.classifier!!.contains("arm64")) {
                    "aarch64"
                } else {
                    null
                }
                platform.add(BootstrapManifest.Platform(arch,"macos"))
            }
        } else {
            artifact.classifier?.let {
                if (it != "no_aop") {
                    path = path.replace(".jar", "-${it}.jar")
                }
            }
        }
        return BootstrapManifest.Artifacts(hash(file.readBytes()), file.name, path, file.length(), platform)
    }

    private fun hash(file: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(file).joinToString("") { "%02x".format(it) }

    private fun getDefaultBootstrap(): BootstrapManifest {
        val fileStream: InputStream? = extension.bootstrapTemplate.get().takeIf { it != File("na") }?.inputStream()
            ?: javaClass.classLoader.getResourceAsStream("bootstrap.template")

        return fileStream?.let { Klaxon().parse<BootstrapManifest>(it) } ?: error("Bootstrap template not found. Please add the file.")
    }

    private fun getArtifacts(): ArtifactsData {
        val localArtifacts = mutableListOf<ResolvedArtifact>()
        val onlineArtifacts = mutableListOf<ResolvedArtifact>()
        val fileArtifacts = mutableListOf<File>()

        project.configurations.getByName("runtimeClasspath").files.forEach { file ->
            if (file.name.endsWith(".jar") && isLocalJarArtifact(file)) {
                fileArtifacts += file
            }
        }

        project.configurations.getByName("runtimeClasspath").resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
            val componentIdentifier = artifact.id.componentIdentifier
            if (componentIdentifier is ModuleComponentIdentifier) {
                if (artifact.file.absolutePath.startsWith(project.rootProject.rootDir.absolutePath)) {
                    localArtifacts += artifact
                } else {
                    onlineArtifacts += artifact
                }
            } else {
                localArtifacts += artifact
            }
        }

        return ArtifactsData(localArtifacts, onlineArtifacts, fileArtifacts)
    }

    private suspend fun getArtifactURL(artifact: ResolvedArtifact, name : String): String {
        val module = artifact.moduleVersion.id.toString()
        val (group, name, version) = module.split(":")
        val groupPath = group.replace('.', '/')
        val artifactFileName = "$name-$version.jar"
        val filePath = "$groupPath/$name/$version/$artifactFileName"
        return findFileInRepos(filePath,name) ?: ""
    }

    private suspend fun findFileInRepos(filePath: String, name: String): String? = coroutineScope {
        if ("lwjgl" in name) {
            return@coroutineScope "https://repo.maven.apache.org/maven2/$filePath"
        }

        // List of repositories to check
        val repositories = listOf(
            "https://repo.runelite.net/",
            "https://repo.maven.apache.org/maven2/"
        )

        // Asynchronously check each repository and return the first that contains the file
        repositories.map { repo ->
            async {
                val fullPath = "$repo$filePath"
                fullPath.takeIf { fileExists(it) }
            }
        }.awaitAll().firstOrNull { it != null }
    }

    private suspend fun fileExists(url: String): Boolean {
        return try {
            httpClient.head { url(url) }.status.isSuccess()
        } catch (e: Exception) {
            println("Error checking URL $url: ${e.message}")
            false
        }
    }

    private fun isLocalJarArtifact(file: File): Boolean {
        return listOf("libs", "lib").any { dir ->
            file.absolutePath.startsWith(project.rootProject.file(dir).absolutePath)
        }
    }
}