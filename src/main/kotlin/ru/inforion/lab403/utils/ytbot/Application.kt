package ru.inforion.lab403.utils.ytbot

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.UpdatesListener.CONFIRMED_UPDATES_ALL
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendSticker
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.internal.HelpScreenException
import ru.inforion.lab403.common.extensions.argparser
import ru.inforion.lab403.common.extensions.hexAsULong
import ru.inforion.lab403.common.extensions.variable
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.config.YoutrackConfig
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy
import ru.inforion.lab403.utils.ytbot.youtrack.*
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.*
import java.io.File
import java.util.logging.Level
import kotlin.system.exitProcess


class Application {
    companion object {
        private val log = logger(Level.ALL)

        private val mapper = jacksonObjectMapper().apply {
            configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
        }

        private fun checkTelegram(config: ApplicationConfig) {
            if (config.telegram == null)
                throw RuntimeException("Telegram must not be null")

            val bot = TelegramProxy(config.telegram.token, proxy = config.proxy)

            val chatId = config.telegram.chatId

            val sticker = SendSticker(chatId, "CAADAgADphMAAulVBRire_9EQFdckwI")
            bot.execute(sticker)

            val message = SendMessage(chatId, "WORK?")
            val response = bot.execute(message)
            log.info { response.toString() }

            val listener = UpdatesListener { updates ->
                updates.forEach {
                    log.info { it.toString() }
                    val recv = it.channelPost().text()
                    if (recv.isBlank()) {
                        bot.execute(SendSticker(chatId, "CAADAgADphMAAulVBRire_9EQFdckwI")) // pouk
                    } else if (recv.startsWith("0x")) {
                        val data = recv.removePrefix("0x").hexAsULong
                        bot.execute(SendMessage(chatId, "Смотри как я считать умею: $data"))
                    } else {
                        bot.execute(SendMessage(chatId, "А ты знаешь, что ты с ботом разговариваешь!"))
                    }
                }
                CONFIRMED_UPDATES_ALL
            }
            bot.setUpdatesListener(listener)

            System.`in`.read()
        }

        private fun checkYoutrack(appConfig: YoutrackConfig) {
            val youtrack = Youtrack(appConfig.baseUrl, appConfig.token)

            val projects = youtrack.projects(fields(Project::id, Project::name, Project::leader))
            projects.forEach { println(it) }

            val kopycat = projects.first { it.name == "Kopycat" }

            val issues = youtrack.issues(kopycat, fields(Issue::idReadable, Issue::id))
            issues.forEach { println("${it.idReadable} ${it.id}") }
        }

        private fun execute(lastTimestamp: Long, appConfig: ApplicationConfig) {
            val youtrack = Youtrack(appConfig.youtrack.baseUrl, appConfig.youtrack.token)
            val projects = youtrack.projects(fields(Project::id, Project::name, Project::shortName))
            val processor = Processor(youtrack, lastTimestamp, appConfig)

            appConfig.projects.map { projectConfig ->
                val bot = TelegramProxy(projectConfig.token, proxy = appConfig.proxy)
                val project = projects.first { it.name == projectConfig.name }
                processor.processProject(project) { data, activityTimestamp ->
                    if (appConfig.telegram?.dry == false) {
                        val message = SendMessage(projectConfig.chatId, data)
                            .parseMode(ParseMode.Markdown)
                        bot.execute(message)
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
            val parser = argparser(
                "youtrack-telegram-bot",
                "Telegram bot to pass from Youtrack to Telegram channel").apply {
                variable<String>("-c", "--config", required = true, help = "Path to the configuration file")
                variable<Long>("-t", "--timestamp", required = false, help = "Last update timestamp")
            }

            val options: Namespace = try{
                parser.parseArgs(args)
            } catch (ex: HelpScreenException) {
                exitProcess(0)
            }

            val configPath: String = options["config"]
            val timestamp: Long? = options["timestamp"]

            val appConfig = mapper.readValue<ApplicationConfig>(File(configPath))

            val lastTimestamp = timestamp ?: appConfig.loadTimestamp()

            log.info { "Using timestamp = $lastTimestamp" }

            execute(lastTimestamp, appConfig)

//            checkYoutrack(appConfig.youtrack)
//            checkTelegram(appConfig)
        }
    }
}


