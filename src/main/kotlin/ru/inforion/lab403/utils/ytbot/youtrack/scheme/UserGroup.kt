package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class UserGroup(
    val allUsersGroup: Boolean,
    val icon: String,
    val name: String,
    val ringId: String,
    val teamForProject: Project,
    val usersCount: Long,
    val id: String
)