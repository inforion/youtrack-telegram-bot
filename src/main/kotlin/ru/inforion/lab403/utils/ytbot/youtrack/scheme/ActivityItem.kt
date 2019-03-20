package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class ActivityItem(
    val added: String,
    val author: User,
    val category: Category,
    val field: ActivityItemField,
    val id: String,
    val removed: String,
    val target: Issue,
    val targetMember: String,
    val timestamp: Long
)