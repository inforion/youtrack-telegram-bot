package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class Activity(
    val added: Any,  // TODO: Make it right (works for LinkCategory)
    val author: User,
    val category: Category,
    val field: ActivityField,
    val id: String,
    val removed: Any,
    val target: Issue,
    val targetMember: String,
    val timestamp: Long
)