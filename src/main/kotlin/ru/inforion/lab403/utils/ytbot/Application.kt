package ru.inforion.lab403.utils.ytbot

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.UpdatesListener.CONFIRMED_UPDATES_ALL
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendSticker
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.internal.HelpScreenException
import ru.inforion.lab403.common.extensions.argparser
import ru.inforion.lab403.common.extensions.hexAsULong
import ru.inforion.lab403.common.extensions.variable
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.Config
import ru.inforion.lab403.utils.ytbot.config.YoutrackConfig
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import java.io.File
import java.util.logging.Level
import kotlin.system.exitProcess


class Application {
    companion object {
        private val log = logger(Level.ALL)

        private inline fun <reified T : Any> loadConfig(path: String): T = jacksonObjectMapper().apply {
            configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
        }.readValue(File(path))

        private fun checkTelegram(config: Config) {
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

        private fun checkYoutrack(config: YoutrackConfig) {
            val youtrack = Youtrack(config.baseUrl, config.token)

            val projects = youtrack.projects(Project::id, Project::name, Project::leader)
            projects.forEach { println(it) }

            val kcIdOnly = projects.first { it.name == "Kopycat" }

            val kcAll = youtrack.project(kcIdOnly.id)
            println(kcAll)

            val issues = youtrack.issues(kcAll, Issue::idReadable, Issue::id)
            issues.forEach { println("${it.idReadable} ${it.id}") }
        }

        private fun processProject(youtrack: Youtrack, project: Project, chatId: Long, bot: TelegramProxy) {
            val issues = youtrack.issues(project)
        }

        private fun execute(config: Config) {
            val youtrack = Youtrack(config.youtrack.baseUrl, config.youtrack.token)
            val projects = youtrack.projects(Project::id, Project::name)

            config.projects.forEach {
                val bot = TelegramProxy(it.token)
                val project = projects.first { project -> project.name == it.name }
                processProject(youtrack, project, it.chatId, bot)
            }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = argparser(
                "youtrack-telegram-bot",
                "Telegram bot to pass from Youtrack to Telegram channel").apply {
                variable<String>("-c", "--config", required = true, help = "Path to the configuration file")
            }

            val options: Namespace = try{
                parser.parseArgs(args)
            } catch (ex: HelpScreenException) {
                exitProcess(0)
            }

            val configPath: String = options["config"]

            val config = loadConfig<Config>(configPath)


            checkYoutrack(config.youtrack)
//            checkTelegram(config.telegram, config.proxy)
        }
    }
}