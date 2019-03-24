package ru.inforion.lab403.utils.ytbot

import com.fasterxml.jackson.module.kotlin.readValue
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMessage
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.internal.HelpScreenException
import ru.inforion.lab403.common.extensions.argparser
import ru.inforion.lab403.common.extensions.flag
import ru.inforion.lab403.common.extensions.variable
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy
import ru.inforion.lab403.utils.ytbot.youtrack.Processor
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import java.io.File
import java.util.logging.Level
import kotlin.concurrent.thread
import kotlin.system.exitProcess


class Application {
    companion object {
        private val log = logger(Level.FINE)

        private val bots = mutableMapOf<String, TelegramProxy>()

        private fun execute(lastTimestamp: Long, dry: Boolean, appConfig: ApplicationConfig) {
            log.finer { "Parsing Youtrack activity timestamp=$lastTimestamp dry=$dry" }
            val youtrack = Youtrack(appConfig.youtrack.baseUrl, appConfig.youtrack.token)
            val projects = youtrack.projects(
                fields(
                    Project::id,
                    Project::name,
                    Project::shortName
                )
            )
            val processor = Processor(youtrack, lastTimestamp, appConfig)

            appConfig.projects.map { prjConf ->
                val bot = bots.getOrPut(prjConf.name) { TelegramProxy(prjConf.token, appConfig.proxy) }
                val project = projects.first { it.name == prjConf.name }
                processor.processProject(project) { data, activityTimestamp ->
                    if (!dry) {
                        val message = SendMessage(prjConf.chatId, data)
                            .parseMode(ParseMode.Markdown)
                        log.finest { "Sending chatId = ${prjConf.chatId} message $message" }
                        val response = bot.execute(message)
                        log.finest { response.toString() }
                    }
                    if (appConfig.loadTimestamp() < activityTimestamp) {
                        log.info { "Updating timestamp = $activityTimestamp" }
                        appConfig.saveTimestamp(activityTimestamp)
                    }
                    log.fine(data)
                }
            }
        }

        private fun daemonize(lastTimestamp: Long, daemon: Int, dry: Boolean, appConfig: ApplicationConfig) {
            log.info { "Starting daemon... press enter to stop daemon" }

            val lock = java.lang.Object()
            var working = true
            var currentLastTimestamp = lastTimestamp

            val worker = thread {
                synchronized(lock) {
                    while (working) {
                        execute(currentLastTimestamp, dry, appConfig)
                        currentLastTimestamp = appConfig.loadTimestamp()
                        lock.wait(daemon * 1000L)
                    }
                }
            }

            @Suppress("UNUSED_VARIABLE")
            val reader = thread {
                System.`in`.reader().read()
                working = false
                synchronized(lock) { lock.notifyAll() }
            }

            worker.join()

            log.info { "Stopping youtrack-telegram-bot..." }

            // If something goes wrong stop reader hardcore
            System.`in`.close()
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val parser = argparser(
                "youtrack-telegram-bot",
                "Telegram bot to pass from Youtrack to Telegram channel").apply {
                variable<String>("-c", "--config", required = true, help = "Path to the configuration file")
                variable<Long>("-t", "--timestamp", required = false, help = "Redefine starting last update timestamp")
                variable<Int>("-d", "--daemon", required = false, help = "Create daemon with specified update timeout in seconds (<= 86400)")
                flag("-r", "--dry", help = "Dry run (don't send to Telegram)")
                variable<String>("-tgc", "--check-telegram", required = false, help = "Send message to telegram and exit. Format [chatId:message]")
                variable<String>("-ytc", "--check-youtrack", required = false, help = "Get project info from Youtrack. Format [projectName]")
            }

            val options: Namespace = try {
                parser.parseArgs(args)
            } catch (ex: HelpScreenException) {
                exitProcess(0)
            }

            log.info { options.toString() }

            val configPath: String = options["config"]
            val timestamp: Long? = options["timestamp"]
            val dry: Boolean = options["dry"] ?: false
            val daemon: Int = options["daemon"] ?: -1
            val checkTelegram: String? = options["check_telegram"]
            val checkYoutrack: String? = options["check_youtrack"]

            val appConfig = jsonConfigLoader.readValue<ApplicationConfig>(File(configPath))

            var inCheckMode = false

            if (checkTelegram != null) {
                log.warning { "Starting Telegram connection check with $checkTelegram" }
                val telegramChecker = TelegramChecker(appConfig)
                val data = checkTelegram.split(":")
                // if unknown type -> start server
                telegramChecker.check(project = data[0], type = data[1], message = data[2])
                inCheckMode = true
            }

            if (checkYoutrack != null) {
                log.warning { "Starting Youtrack connection check with $checkTelegram" }
                val youtrackChecker = YoutrackChecker(appConfig)
                youtrackChecker.check(project = checkYoutrack)
                inCheckMode = true
            }

            if (inCheckMode) {
                log.info { "youtrack-telegram-bot was in check mode... exiting" }
                exitProcess(0)
            }

            if (daemon > 24 * 60 * 60) {
                log.info { "Update period to slow..." }
                exitProcess(-1)
            }

            log.info { "$appConfig" }

            val startingLastTimestamp = timestamp ?: appConfig.loadTimestamp()
            log.info { "Starting last timestamp = $startingLastTimestamp dry = $dry" }
            if (daemon > 0) {
                daemonize(startingLastTimestamp, daemon, dry, appConfig)
            } else {
                execute(startingLastTimestamp, dry, appConfig)
            }
        }
    }
}


