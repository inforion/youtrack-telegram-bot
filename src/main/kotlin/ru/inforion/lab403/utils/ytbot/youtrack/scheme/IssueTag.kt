package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class IssueTag(
    val color: FieldStyle,
    val issues: MutableCollection<Issue>,
    val owner: User,
    val untagOnResolve: Boolean,
    val id: String,
    val name: String,
    val updateableBy: UserGroup,
    val visibleFor: UserGroup
)