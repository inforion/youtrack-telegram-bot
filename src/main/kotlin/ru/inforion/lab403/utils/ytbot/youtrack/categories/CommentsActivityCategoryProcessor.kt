package ru.inforion.lab403.utils.ytbot.youtrack.categories

import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Activity

class CommentsActivityCategoryProcessor(youtrack: Youtrack) : ACategoryProcessor(youtrack) {
    override fun description() = "comments activity"

    override fun stringify(activity: Activity): String {
        return "${youtrack.idHyperlink(activity.target.idReadable)} comment added: ${activity.added[0].text}"
    }
}