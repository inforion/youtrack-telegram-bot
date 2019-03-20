package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class IssueComment(
    val author: User,
    val deleted: Boolean,
    val issue: Issue,
    val attachments: MutableCollection<IssueAttachment>,
    val created: Long,
    val id: String,
    val text: String,
    val textPreview: String,
    val updated: Long,
    val usesMarkdown: Boolean,
    val visibility: Visibility
)