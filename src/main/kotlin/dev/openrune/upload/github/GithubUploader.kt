package dev.openrune.upload.github

import dev.openrune.upload.ftp.FtpUploadSettings
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.gradle.api.logging.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

class GithubUploader(
    private val logger: Logger,
    private val settings : GithubUploadSettings,
    private val directoryPath: Path,
    private val storeInVersions: Boolean,
    private val clientVersion: String = "",
    private val buildType: String,
    private val subPaths: String = "client"
) {
    fun start() {
        try {
            if (settings.token.isEmpty()) {
                logger.error("The 'token' is empty. Please define a token.")
                exitProcess(0)
            }

            if (settings.repoURL.isEmpty()) {
                logger.error("The 'repositoryUrl' is empty. Please define a valid URL, e.g., 'https://github.com/ValamorePS/hosting.git'.")
                exitProcess(0)
            }

            logger.lifecycle("Step 1: Connecting to GitHub and cloning the repository.")
            val tmpDir = Files.createTempDirectory("git_").toFile().apply { deleteOnExit() }

            val git = Git.cloneRepository()
                .setURI(settings.repoURL)
                .setCredentialsProvider(UsernamePasswordCredentialsProvider(settings.token, ""))
                .setDirectory(tmpDir)
                .call()

            logger.lifecycle("Step 2: Gathering and copying files to the local clone.")
            val subPathDir = File(tmpDir, "$subPaths/$buildType/")

            directoryPath.toFile().walk().forEach { file ->
                if (file.isFile) {
                    val relativePath = directoryPath.relativize(file.toPath()).toString()
                    val destFile = File(subPathDir, relativePath)
                    destFile.parentFile.mkdirs()
                    Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }

            git.add().addFilepattern(".").call()

            logger.lifecycle("Step 3: Preparing commit.")
            val status = git.status().call()
            val files = (status.added + status.changed)

            if (files.isEmpty()) {
                logger.lifecycle("No changes detected. Exiting process.")
                exitProcess(0)
            }

            val commitDescription = buildString {
                append("Updated files:\n")
                files.forEach { append("- ${it.substringAfterLast("/")}\n") }
            }

            val currentDateTime = LocalDateTime.now()
            val formattedDateTime = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val storeInVersions = storeInVersions && clientVersion != "unspecified"
            val commitMessage = "Updated Client at $formattedDateTime ${if (storeInVersions) ": Version $clientVersion" else ""}"

            logger.lifecycle("Step 4: Committing changes.")
            git.commit()
                .setMessage("$commitMessage\n\n$commitDescription")
                .call()

            logger.lifecycle("Commit successful. Now pushing to remote repository.")

            git.push()
                .setCredentialsProvider(UsernamePasswordCredentialsProvider(settings.token, ""))
                .setProgressMonitor(TextProgressMonitor(logger))
                .call()

            logger.lifecycle("Push complete. Process finished successfully.")
        } catch (e: GitAPIException) {
            logger.error("Error during the GitHub operation: ${e.message}")
            exitProcess(0)
        }
    }

    private class TextProgressMonitor(private val logger: Logger) : ProgressMonitor {
        private var taskTitle: String? = null
        private var totalWork: Int = 0
        private var workDone: Int = 0
        private var lastWorkDone: Int = 0
        private var lastUpdateTime: Long = 0

        private val barWidth = 40  // Width of the progress bar

        override fun start(totalTasks: Int) {}

        override fun beginTask(title: String, totalWork: Int) {
            this.taskTitle = title
            this.totalWork = totalWork
            this.workDone = 0
            this.lastWorkDone = 0
            this.lastUpdateTime = System.currentTimeMillis()

            if (totalWork > 0) {
                printProgressBar(0, "$title - Starting...")
            }
        }

        override fun update(completed: Int) {
            workDone += completed
            if (totalWork > 0) {
                if (workDone - lastWorkDone >= totalWork / 20 || workDone == totalWork || System.currentTimeMillis() - lastUpdateTime > 1000) {
                    printProgressBar(workDone * 100 / totalWork, "$taskTitle - Progress...")
                    lastWorkDone = workDone
                    lastUpdateTime = System.currentTimeMillis()
                }
            }
        }

        override fun endTask() {
            if (totalWork > 0) {
                printProgressBar(100, "$taskTitle - Completed")
                println("")  // Finish the current line
            }
        }

        override fun isCancelled() = false
        override fun showDuration(enabled: Boolean) {}

        private fun printProgressBar(percent: Int, message: String) {
            val progress = (percent * barWidth) / 100
            val bar = "\r[" + "=".repeat(progress) + " ".repeat(barWidth - progress) + "] $percent% $message"
            println(bar)
        }
    }
}