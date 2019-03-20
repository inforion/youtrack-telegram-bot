package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class IssueLink(
    val direction: String, // LinkDirection,
    val id: String,
    val issues: MutableCollection<Issue>,
    val linkType: IssueLinkType,
    val trimmedIssues: String // List
)