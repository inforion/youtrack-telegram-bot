package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class ExternalIssue(
    val commentsKey: String,
    val commentsUrl: String,
    val historyKey: String,
    val historyUrl: String,
    val id: String,
    val key: String,
    val name: String,
    val url: String
)