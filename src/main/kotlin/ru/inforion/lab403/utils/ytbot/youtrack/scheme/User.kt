package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class User(
    val avatarUrl: String,
    val banned: Boolean,
    val email: String,
    val fullName: String,
    val guest: Boolean,
    val jabberAccountName: String,
    val login: String,
    val name: String,
    val online: Boolean,
    val profiles: UserProfiles,
    val ringId: String,
    val savedQueries: List<String>,
    val tags: MutableCollection<IssueTag>,
    val id: String
)