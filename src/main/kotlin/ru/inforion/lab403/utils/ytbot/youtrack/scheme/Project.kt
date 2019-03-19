package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class Project(
    val archived: Boolean,
    val createdBy: User,
    val customFields: MutableCollection<CustomField>,
    val description: String,
    val fields: MutableCollection<CustomField>,
    val fromEmail: String,
    val iconUrl: String,
    val issues: MutableCollection<Issue>,
    val leader: User,
    val replyToEmail: String,
    val shortName: String,
    val template: Boolean,
    val id: String,
    val name: String
)