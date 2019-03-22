package ru.inforion.lab403.utils.ytbot.youtrack.categories

import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Activity

class IssueCreatedCategoryProcessor(youtrack: Youtrack) : ACategoryProcessor(youtrack) {
    override fun description() = "issues created activity"

    override fun stringify(activity: Activity): String {
//        val assigneeField = activity.target.fields.first { it.name == "Assignee" }
//        val user = "http://tg://user?id=${assigneeField.instances}"
        val url = youtrack.idHyperlink(activity.target.idReadable)
        return "Created $url ${activity.target.summary}"
    }
}