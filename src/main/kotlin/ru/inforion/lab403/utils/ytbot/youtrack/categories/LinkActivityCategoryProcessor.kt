package ru.inforion.lab403.utils.ytbot.youtrack.categories

import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Activity

class LinkActivityCategoryProcessor(youtrack: Youtrack) : ACategoryProcessor(youtrack) {
    override fun description() = "issues linked activity"

    override fun stringify(activity: Activity): String {
        val link = activity.added[0]
        val urlMe = youtrack.idHyperlink(activity.target.idReadable)
        val urlOther = youtrack.idHyperlink(link.idReadable)
        return "Make $urlMe ${activity.field.presentation}: $urlOther ${link.summary}"
    }
}