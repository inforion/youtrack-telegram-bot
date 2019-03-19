package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class CustomFieldDefaults(
    val canBeEmpty: Boolean,
    val emptyFieldText: String,
    val isPublic: Boolean,
    val parent: CustomField,
    val id: String
)