package ru.inforion.lab403.utils.ytbot.config

data class Config(
    val telegram: TelegramConfig?,
    val proxy: ProxyConfig?,
    val youtrack: YoutrackConfig,
    val projects: List<ProjectConfig>
)