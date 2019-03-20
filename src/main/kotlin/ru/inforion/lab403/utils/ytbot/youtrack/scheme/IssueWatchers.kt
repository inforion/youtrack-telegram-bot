package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class IssueWatchers(
    val duplicateWatchers: List<IssueWatcher>,
    val hasStar: Boolean,
    val id: String,
    val issueWatchers: MutableCollection<IssueWatcher>
)