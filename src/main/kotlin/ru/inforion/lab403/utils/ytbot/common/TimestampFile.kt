package ru.inforion.lab403.utils.ytbot.common

import ru.inforion.lab403.common.extensions.parseJson
import ru.inforion.lab403.common.extensions.toFile
import ru.inforion.lab403.common.extensions.writeJson
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.asDatetime
import ru.inforion.lab403.utils.ytbot.config.ProjectConfig

class TimestampFile(private val path: String) {
    companion object {
        val log = logger()
    }

    private val timestampFile = path.toFile()

    private fun save(map: Map<String, Long>) {
        val json = map.writeJson()
        timestampFile.writeText(json)
    }

    private fun load(): MutableMap<String, Long> {
        val text = timestampFile.readText()
        return text.parseJson()
    }

    private fun createTimestampFile(projects: List<ProjectConfig>, timestamp: Long) {
        save(projects.associate { it.name to timestamp })
        log.config { "Timestamp file '$timestampFile' reinitialized successfully!" }
    }

    private fun actualizeProjects(projects: List<ProjectConfig>, timestamp: Long) {
        val map = load()
        val notFound = map.keys - projects.map { it.name }
        if (notFound.isNotEmpty()) {
            log.config { "Actualizing last timestamp for projects: ${notFound.joinToString()}" }
            notFound.forEach { map[it] = timestamp }
            save(map)
        }
    }

    private fun upgradeTimestampFileIfRequired(projects: List<ProjectConfig>) {
        val text = timestampFile.readText()
        val parse = text.runCatching { parseJson<Map<String, Long>>() }
        if (parse.isFailure) {
            val value = text.toLongOrNull()
            requireNotNull(value) { "Something wrong with timestamp file: '$timestampFile' it isn't new or old version!\n$text" }
            log.config { "Upgrading last timestamp file for new version..." }
            createTimestampFile(projects, value)
        }
    }

    fun validateTimestampFile(projects: List<ProjectConfig>, startTimestamp: Long?) {
        if (startTimestamp == null) {
            log.config { "Option --timestamp was not specified -> using 0 if required" }
        }

        if (!timestampFile.exists()) {
            log.config { "Timestamp file '$timestampFile' not exists -> configuring" }

            val directory = timestampFile.parentFile
            if (!directory.exists()) {
                log.config { "Creating directory for timestamp file: '$directory'" }
                require(directory.mkdirs()) { "Can't create directory for timestamp file: '$directory'!" }
            }

            createTimestampFile(projects, startTimestamp ?: 0)
        } else {
            log.config { "Timestamp file '$timestampFile' exists -> validating" }
            if (startTimestamp != null) {
                log.config { "Option --timestamp specified -> redefine timestamp file: '$timestampFile'" }
                createTimestampFile(projects, startTimestamp)
            } else {
                upgradeTimestampFileIfRequired(projects)
                actualizeProjects(projects, startTimestamp ?: 0)
            }
        }

        log.info { load() }
    }

    fun saveTimestamp(project: String, timestamp: Long) = save(
        load().also {
            val previous = it.getValue(project)
            if (previous < timestamp) {
                log.info { "Updating '$project' timestamp to $timestamp [${timestamp.asDatetime}]" }
                it[project] = timestamp
            } else {
                log.info { "Previous timestamp value for project '$project' is $previous new value is $timestamp -> omit" }
            }
        }
    )

    fun loadTimestamp(project: String) = load().getValue(project)
}