package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class IssueWatcher(
    val id: String,
    val isStarred: Boolean,
    val issue: Issue,
    val user: User
)