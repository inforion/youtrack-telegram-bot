package ru.inforion.lab403.utils.ytbot.youtrack.scheme

data class IssueLinkType(
    val aggregation: Boolean,
    val directed: Boolean,
    val localizedName: String,
    val localizedSourceToTarget: String,
    val localizedTargetToSource: String,
    val name: String,
    val readOnly: Boolean,
    val sourceToTarget: String,
    val targetToSource: String,
    val uid: Long,
    val id: String
)