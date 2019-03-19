package ru.inforion.lab403.utils.ytbot.youtrack.scheme

import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.google.gson.Gson

//"Issue" : {
//    "attachments" : "MutableCollection",
//    "comments" : "MutableCollection",
//    "commentsCount" : "Long",
//    "created" : "Long",
//    "customFields" : "List",
//    "description" : "String",
//    "draftComment" : "DraftIssueComment",
//    "draftOwner" : "User",
//    "externalIssue" : "ExternalIssue",
//    "fields" : "List",
//    "idReadable" : "String",
//    "isDraft" : "Boolean",
//    "links" : "List",
//    "numberInProject" : "Long",
//    "parent" : "IssueLink",
//    "project" : "Project",
//    "reporter" : "User",
//    "resolved" : "Long",
//    "subtasks" : "IssueLink",
//    "summary" : "String",
//    "tags" : "MutableCollection",
//    "updated" : "Long",
//    "updater" : "User",
//    "usesMarkdown" : "Boolean",
//    "visibility" : "Visibility",
//    "voters" : "IssueVoters",
//    "votes" : "Int",
//    "watchers" : "IssueWatchers",
//    "wikifiedDescription" : "String!",
//    "id" : "String"
//}

data class Issue(
    val summary: String,
    val commentsCount: Long,
    val created: Long,
    val description: String,
    val id: String
)