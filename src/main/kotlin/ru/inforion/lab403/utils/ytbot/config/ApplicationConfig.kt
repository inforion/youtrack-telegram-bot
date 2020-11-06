package ru.inforion.lab403.utils.ytbot.config

import ru.inforion.lab403.common.extensions.parseJson
import ru.inforion.lab403.common.extensions.toFile
import ru.inforion.lab403.utils.ytbot.youtrack.CategoryId
import java.io.File


data class ApplicationConfig constructor(
    val proxy: ProxyConfig?,
    val youtrack: YoutrackConfig,
    val projects: List<ProjectConfig>,
    val minutesGroupInterval: Int,
    val timestampFilePath: String,
    val descriptionMaxChars: Int,
    val omitEmptyFields: Boolean,
    val telegramMinimumMessageDelay: Long,
    val telegramSendRetriesCount: Int,
    val telegramSendRetriesTimeout: Long,
    val taggedCustomFields: List<String>,
    val commitFirstLineOnly: Boolean,
    val showActivityAuthor: Boolean,
    val userCustomFields: List<String>,
    val users: Map<String, TelegramUserConfig>?,
    val activityCategories: List<CategoryId>?,
    val filterIssues: List<String>?
) {
    companion object {
        fun load(file: File): ApplicationConfig = file.inputStream().parseJson()
    }

    fun isCategoryActive(categoryId: CategoryId) =
        if (activityCategories == null) true else categoryId in activityCategories

    private val timestampFile = timestampFilePath.toFile()

    fun saveTimestamp(timestamp: Long) {
        timestampFile.writeText(timestamp.toString())
    }

    fun loadTimestamp(): Long {
        if (!timestampFile.canRead())
            return 0
        return timestampFile.readText().toLong()
    }

    fun createTimestampDirectories() = timestampFile.parentFile.mkdirs()
}