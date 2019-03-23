package ru.inforion.lab403.utils.ytbot

import com.fasterxml.jackson.module.kotlin.readValue
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import java.io.File
import java.util.logging.Level

class CheckYoutrack {
    companion object {
        private val log = logger(Level.ALL)

        @JvmStatic
        fun main(args: Array<String>) {
            val config = jsonConfigLoader.readValue<ApplicationConfig>(File("temp/config.json"))

            val youtrack = Youtrack(config.youtrack.baseUrl, config.youtrack.token)

            val projects = youtrack.projects(
                fields(
                    Project::id,
                    Project::name,
                    Project::leader
                )
            )
            projects.forEach { println(it) }

            val kopycat = projects.first { it.name == "Kopycat" }

            val issues = youtrack.issues(kopycat, fields(Issue::idReadable, Issue::id))
            issues.forEach { println("${it.idReadable} ${it.id}") }
        }
    }
}