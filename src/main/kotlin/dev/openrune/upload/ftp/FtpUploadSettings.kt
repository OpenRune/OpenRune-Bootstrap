package dev.openrune.upload.ftp

data class FtpUploadSettings(
    var ftpServer: String,
    var ftpPort: Int = 21,
    var ftpUser: String,
    var ftpPassword: String,
)