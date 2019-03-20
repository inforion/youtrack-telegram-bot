package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class IssueVoters(
    val duplicate: String, //List,
    val hasVote: Boolean,
    val original: String, // List,
    val id: String
)