package ru.inforion.lab403.utils.ytbot.youtrack.categories

import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Activity

abstract class ACategoryProcessor(val youtrack: Youtrack) {
    abstract fun stringify(activity: Activity): String
    abstract fun description(): String
}