package ru.inforion.lab403.utils.ytbot.youtrack.scheme


//"Project" : {
//    "archived" : "Boolean",
//    "createdBy" : "User",
//    "customFields" : "MutableCollection",
//    "description" : "String",
//    "fields" : "MutableCollection",
//    "fromEmail" : "String",
//    "iconUrl" : "String!",
//    "issues" : "MutableCollection",
//    "leader" : "User",
//    "replyToEmail" : "String",
//    "shortName" : "String",
//    "startingNumber" : "Long",
//    "template" : "Boolean",
//    "id" : "String",
//    "name" : "String"
//}

data class Project(
    val archived: Boolean,
    val createdBy: User,
    val customFields: MutableCollection<CustomField>,
    val description: String,
    val fields: MutableCollection<CustomField>,
    val fromEmail: String,
    val iconUrl: String,
    val issues: MutableCollection<Issue>,
    val leader: User,
    val replyToEmail: String,
    val shortName: String,
    val startingNumber: Long,
    val template: Boolean,
    val id: String,
    val name: String
)