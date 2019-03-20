package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class DraftIssueComment (
    val attachments: String, //MutableCollection,
    val created: Long,
    val id: String,
    val text: String,
    val textPreview: String,
    val updated: Long,
    val usesMarkdown: Boolean,
    val visibility: Visibility
)