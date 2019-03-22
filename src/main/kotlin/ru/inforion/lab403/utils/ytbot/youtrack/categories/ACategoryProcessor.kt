package ru.inforion.lab403.utils.ytbot.youtrack.categories

import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Activity

interface ICategoryProcessor {
    fun process(activity: Activity): String
}