package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class PredefinedFilterField(
    val id: String,
    val instant: Boolean,
    val presentation: String,
    val aggregateable: Boolean,
    val baseField: IField,
    val name: String,
    val sortable: Boolean
) : IField