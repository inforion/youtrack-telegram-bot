package ru.inforion.lab403.utils.ytbot.youtrack.categories

import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Activity

class UnknownActivityCategoryProcessor(youtrack: Youtrack) : ACategoryProcessor(youtrack) {
    override fun description() = "unknown activity"

    override fun stringify(activity: Activity): String {
        return activity.id
    }
}