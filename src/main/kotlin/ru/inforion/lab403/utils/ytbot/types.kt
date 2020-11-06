package ru.inforion.lab403.utils.ytbot

import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue

typealias ProcessActivityData = (data: String, issue: Issue, timestamp: Long) -> Unit