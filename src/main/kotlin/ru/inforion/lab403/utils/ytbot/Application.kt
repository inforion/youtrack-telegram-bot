package ru.inforion.lab403.utils.ytbot

import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.internal.HelpScreenException
import ru.inforion.lab403.common.extensions.argparser
import ru.inforion.lab403.common.extensions.flag
import ru.inforion.lab403.common.extensions.variable
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import java.util.logging.Level
import kotlin.concurrent.thread
import kotlin.system.exitProcess


class Application {
    companion object {
        private val log = logger(Level.FINE)

        private fun daemonize(
            bot: YoutrackTelegramBot,
            daemon: Int,
            tgSendMessages: Boolean,
            tgStartServices: Boolean
        ) {
            log.info { "Starting daemon... press enter to stop daemon" }

            val lock = java.lang.Object()
            var working = true
            var currentLastTimestamp = bot.startLastTimestamp

            if (tgStartServices)
                bot.createCommandServices()

            val worker = thread {
                synchronized(lock) {
                    while (working) {
                        bot.execute(tgSendMessages, currentLastTimestamp)
                        currentLastTimestamp = bot.loadCurrentLastTimestamp()
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
                flag("-r", "--dont-send-messages", help = "Don't send messages to Telegram")
                flag("-a", "--dont-start-services", help = "Don't start command services for Telegram when in daemon mode")
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
            val tgSendMessages: Boolean = !(options["dont_send_messages"] ?: false)
            val tgStartServices: Boolean = !(options["dont_start_services"] ?: false)
            val daemon: Int = options["daemon"] ?: -1
            val checkTelegram: String? = options["check_telegram"]
            val checkYoutrack: String? = options["check_youtrack"]

            val appConfig = ApplicationConfig.load(configPath)

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

            log.info { "Checking ${appConfig.timestampFilePath} to writing..." }
            appConfig
                .runCatching { saveTimestamp(startingLastTimestamp) }
                .onFailure {
                    log.severe { "File ${appConfig.timestampFilePath} can't be written" }
                    exitProcess(-1)
                }

            val bot = YoutrackTelegramBot(startingLastTimestamp, appConfig)

            log.info {
                val datetime = Youtrack.makeTimedate(startingLastTimestamp)
                "Starting last timestamp=$startingLastTimestamp [$datetime] send=$tgSendMessages services=$tgStartServices"
            }
            if (daemon > 0) {
                daemonize(bot, daemon, tgSendMessages, tgStartServices)
            } else {
                bot.execute(tgSendMessages)
            }
        }
    }
}


