package ru.inforion.lab403.utils.ytbot

import com.fasterxml.jackson.module.kotlin.readValue
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMessage
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.internal.HelpScreenException
import ru.inforion.lab403.common.extensions.argparser
import ru.inforion.lab403.common.extensions.variable
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy
import ru.inforion.lab403.utils.ytbot.youtrack.Processor
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import java.io.File
import java.util.logging.Level
import kotlin.system.exitProcess


class Application {
    companion object {
        private val log = logger(Level.ALL)

        private fun execute(lastTimestamp: Long, appConfig: ApplicationConfig) {
            val youtrack = Youtrack(appConfig.youtrack.baseUrl, appConfig.youtrack.token)
            val projects = youtrack.projects(
                fields(
                    Project::id,
                    Project::name,
                    Project::shortName
                )
            )
            val processor = Processor(youtrack, lastTimestamp, appConfig)

            appConfig.projects.map { projectConfig ->
                val bot = TelegramProxy(projectConfig.token, proxy = appConfig.proxy)
                val project = projects.first { it.name == projectConfig.name }
                processor.processProject(project) { data, activityTimestamp ->
                    if (appConfig.telegram?.dry == false) {
                        val message = SendMessage(projectConfig.chatId, data)
                            .parseMode(ParseMode.Markdown)
                        log.info { "Sending chatId = ${projectConfig.chatId} message $message" }
                        val response = bot.execute(message)
                        log.warning { response.toString() }
                    }
                    if (appConfig.loadTimestamp() < activityTimestamp) {
                        log.info { "Updating timestamp = $activityTimestamp" }
                        appConfig.saveTimestamp(activityTimestamp)
                    }
                    log.fine(data)
                }
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
//            val dns = System.getProperty("sun.net.spi.nameservice.nameservers")
//            System.setProperty("sun.net.spi.nameservice.nameservers", "8.8.8.8")
//            System.setProperty("sun.net.spi.nameservice.provider.1", "dns,sun")

            val parser = argparser(
                "youtrack-telegram-bot",
                "Telegram bot to pass from Youtrack to Telegram channel").apply {
                variable<String>("-c", "--config", required = true, help = "Path to the configuration file")
                variable<Long>("-t", "--timestamp", required = false, help = "Last update timestamp")
            }

            val options: Namespace = try {
                parser.parseArgs(args)
            } catch (ex: HelpScreenException) {
                exitProcess(0)
            }

            val configPath: String = options["config"]
            val timestamp: Long? = options["timestamp"]

            val appConfig = jsonConfigLoader.readValue<ApplicationConfig>(File(configPath))

            val lastTimestamp = timestamp ?: appConfig.loadTimestamp()

            log.info { "Using timestamp = $lastTimestamp" }

            log.info { "$appConfig" }

            execute(lastTimestamp, appConfig)
        }
    }
}


