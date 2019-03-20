package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class ProjectCustomField(
    val canBeEmpty: Boolean,
    val condition: CustomFieldCondition,
    val emptyFieldText: String,
    val field: CustomField,
    val hasRunningJob: Boolean,
    val isPublic: Boolean,
    val ordinal: Int,
    val project: Project,
    val id: String
): IField