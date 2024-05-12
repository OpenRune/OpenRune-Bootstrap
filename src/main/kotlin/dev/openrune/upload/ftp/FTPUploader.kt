package dev.openrune.upload.ftp

import dev.openrune.ProgressBar
import org.gradle.api.logging.Logger
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import kotlin.system.exitProcess
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply

class FTPUploader(
    private val logger: Logger,
    private val settings : FtpUploadSettings,
    private val directoryPath: Path,
    private val buildType: String,
    private val subPaths: String = "client"
) {

    fun start() {
        try {

            if (settings.ftpServer.isEmpty()) {
                logger.error("The 'ftpServer' is empty. Please define a valid FTP server address.")
                exitProcess(0)
            }


            if (settings.ftpUser.isEmpty() || settings.ftpPassword.isEmpty()) {
                logger.error("FTP username or password is empty. Please define a valid username and password.")
                exitProcess(0)
            }

            logger.lifecycle("Step 1: Connecting to FTP server and logging in.")
            val ftpClient = FTPClient()

            ftpClient.connect(settings.ftpServer, settings.ftpPort)
            if (configureFTPClient(ftpClient)) {
                ftpClient.login(settings.ftpUser, settings.ftpPassword)
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)

                if (ftpClient.isConnected) {
                    logger.lifecycle("Connected to FTP.")

                    val baseDir = "$subPaths/$buildType"
                    logger.lifecycle("Step 2: Gathering and uploading files to the FTP server.")
                    directoryPath.toFile().walk().forEach { file ->
                        if (file.isFile) {
                            val relativePath = directoryPath.relativize(file.toPath()).toString()
                            val remoteFilePath = "$baseDir/$relativePath"
                            uploadFile(ftpClient, file, remoteFilePath)
                        }
                    }

                    logger.lifecycle("Step 3: Uploading complete. Logging out.")
                    ftpClient.logout()
                    ftpClient.disconnect()

                    logger.lifecycle("Process finished successfully.")
                } else {
                    logger.error("Unable to Connect")
                    exitProcess(0)
                }
            } else {
                logger.error("Unable to configure")
                exitProcess(0)
            }
        } catch (e: Exception) {
            logger.error("Error during the FTP operation: ${e.message}")
            exitProcess(0)
        }
    }

    private fun uploadFile(ftpClient: FTPClient, localFile: File, remoteFilePath: String) {
        // Ensure we are in passive mode to avoid connection issues behind NAT
        ftpClient.enterLocalPassiveMode()

        // Normalize the remote file path to use forward slashes
        val normalizedRemotePath = remoteFilePath.replace("\\", "/")

        // Extract the remote directory path from the full file path
        val remoteDirPath = normalizedRemotePath.substringBeforeLast('/', "")

        ftpClient.makeDirectory(remoteDirPath)

        // Use a FileInputStream to read the local file
        FileInputStream(localFile).use { input ->
            // Try to store the file on the FTP server
            if (ftpClient.storeFile(normalizedRemotePath, input)) {
                logger.lifecycle("Uploaded ${localFile.name} to $normalizedRemotePath successfully.")
            } else {
                logger.error("Failed to upload ${localFile.name} to $normalizedRemotePath.")
            }
        }
    }

    private fun configureFTPClient(ftpClient: FTPClient): Boolean {
        // Initially try to use passive mode
        ftpClient.enterLocalPassiveMode()

        try {
            // Check if the connection is successful
            val replyCode = ftpClient.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                println("FTP server refused connection: $replyCode")
                return false
            }

            // Optionally, you can try a simple command that doesn't transfer much data
            ftpClient.noop() // Send NOOP command to see if the connection is alive

            return true
        } catch (e: Exception) {
            println("An error occurred: ${e.message}")
            return false
        }
    }

}