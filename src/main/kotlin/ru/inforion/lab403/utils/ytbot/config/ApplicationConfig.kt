package ru.inforion.lab403.utils.ytbot.config

import ru.inforion.lab403.utils.ytbot.youtrack.CategoryId
import java.io.File

data class ApplicationConfig(
    val proxy: ProxyConfig?,
    val youtrack: YoutrackConfig,
    val projects: List<ProjectConfig>,
    val minutesGroupInterval: Int,
    val timestampFilePath: String,
    val descriptionMaxChars: Int,
    val taggedCustomFields: List<String>,
    val userCustomFields: List<String>,
    val users: Map<String, TelegramUserConfig>?,
    val activityCategories: List<CategoryId>?,
    val filterIssues: List<String>?
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