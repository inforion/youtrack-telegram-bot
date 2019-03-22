package ru.inforion.lab403.utils.ytbot.config

import java.io.File

data class ApplicationConfig(
    val telegram: TelegramConfig?,
    val proxy: ProxyConfig?,
    val youtrack: YoutrackConfig,
    val projects: List<ProjectConfig>,
    val minutesGroupInterval: Int,
    val timestampFilePath: String,
    val descriptionMaxChars: Int,
    val taggedCustomFields: List<String>,
    val assigneeFieldName: String,
    val users: Map<String, String>?
) {
    fun saveTimestamp(timestamp: Long) {
        File(timestampFilePath).writeText(timestamp.toString())
    }

    fun loadTimestamp(): Long {
        val file = File(timestampFilePath)
        if (!file.canRead())
            return 0
        return file.readText().toLong()
    }
}