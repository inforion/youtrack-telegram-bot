package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class CustomField(
    val value: Any,
    val aliases: String,
    val fieldDefaults: CustomFieldDefaults,
    val fieldType: String,
    val hasRunningJob: Boolean,
    val instances: MutableCollection<String>,
    val isAutoAttached: Boolean,
    val isDisplayedInIssueList: Boolean,
    val isUpdateable: Boolean,
    val localizedName: Boolean,
    val name: String,
    val ordinal: Int,
    val id: String
)