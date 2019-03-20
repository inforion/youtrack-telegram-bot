package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class SimpleProjectCustomField(
    val canBeEmpty: Boolean,
    val condition: CustomFieldCondition,
    val emptyFieldText: String,
    val field: CustomField,
    val hasRunningJob: Boolean,
    val id: String,
    val isPublic: Boolean,
    val ordinal: Int,
    val project: Project
): IField