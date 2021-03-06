package ru.inforion.lab403.utils.ytbot.checkers

import ru.inforion.lab403.common.logging.ALL
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.fields
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project

class YoutrackChecker(val config: ApplicationConfig) {
    companion object {
        val log = logger(ALL)
    }

    fun check(project: String) {
        val youtrack = Youtrack(config.youtrack.baseUrl, config.youtrack.token)

        val version = youtrack.version()
        log.info { "Version = $version" }

        val projects = youtrack.projects(fields(Project::id, Project::name, Project::shortName))

        log.info { "======== Found projects ======== " }
        projects.forEach { println("${it.id} ${it.shortName} aka ${it.name}") }

        val prj = projects.first { it.name == project }
        val issues = youtrack.issues(prj, fields(Issue::idReadable, Issue::id, Issue::summary))

        log.info { "======== Issues of project $project ========" }
        issues.forEach { println("${it.id} ${it.idReadable} ${it.summary}") }
    }
}