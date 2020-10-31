package ru.inforion.lab403.utils.ytbot

import ru.inforion.lab403.common.extensions.BlockingValue
import ru.inforion.lab403.common.extensions.argparse.ApplicationOptions
import ru.inforion.lab403.common.extensions.argparse.flag
import ru.inforion.lab403.common.extensions.argparse.parseArguments
import ru.inforion.lab403.common.extensions.argparse.variable
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import kotlin.concurrent.thread
import kotlin.system.exitProcess


object Application {
    val log = logger()

    private fun daemonize(
        bot: YoutrackTelegramBot,
        daemon: Int,
        tgSendMessages: Boolean,
        tgStartServices: Boolean
    ) {
        log.info { "Starting daemon... press enter to stop daemon" }

        val stopNotify = BlockingValue<Int>()
        var currentLastTimestamp = bot.startLastTimestamp

        if (tgStartServices)
            bot.createCommandServices()

        val worker = thread {
            while (stopNotify.poll(daemon * 1000L) == null) {
                bot.execute(tgSendMessages, currentLastTimestamp)
                currentLastTimestamp = bot.loadCurrentLastTimestamp()
            }
        }

        thread {
            System.`in`.reader().read()
            stopNotify.offer(0)
        }

        worker.join()

        log.info { "Stopping youtrack-telegram-bot..." }

        // If something goes wrong stop reader hardcore
        System.`in`.close()
    }

    class Options : ApplicationOptions("youtrack-telegram-bot", "Telegram bot to pass from Youtrack to Telegram channel") {
        val config: String by variable("-c", "--config", "Path to the configuration file", required = true)

        val timestamp: Long? by variable("-t", "--timestamp", "Redefine starting last update timestamp")

        val daemon: Int by variable("-d", "--daemon",
            "Create daemon with specified update timeout in seconds (<= 86400)")

        val tgSendMessages by flag("-r", "--dont-send-messages", "Don't send messages to Telegram")

        val tgStartServices by flag("-a", "--dont-start-services",
            "Don't start command services for Telegram when in daemon mode")

        val checkTelegram: String? by variable("-tgc", "--check-telegram",
            "Send message to telegram and exit. Format [chatProjectName:type:message], for message type=m")

        val checkYoutrack: String? by variable("-ytc", "--check-youtrack",
            "Get project info from Youtrack. Format [projectName]")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val options = args.parseArguments<Options>()

        log.info { options.namespace }

        val appConfig = ApplicationConfig.load(options.config)

        var inCheckMode = false

        options.checkTelegram?.let {
            log.warning { "Starting Telegram connection check with $it" }
            val telegramChecker = TelegramChecker(appConfig)
            val data = it.split(":")
            // if unknown type -> start server
            telegramChecker.check(project = data[0], type = data[1], message = data[2])
            inCheckMode = true
        }

        options.checkYoutrack?.let {
            log.warning { "Starting Youtrack connection check with $it" }
            val youtrackChecker = YoutrackChecker(appConfig)
            youtrackChecker.check(project = it)
            inCheckMode = true
        }

        if (inCheckMode) {
            log.info { "youtrack-telegram-bot was in check mode... exiting" }
            exitProcess(0)
        }

        if (options.daemon > 24 * 60 * 60) {
            log.info { "Update period to slow..." }
            exitProcess(-1)
        }

        log.info { "$appConfig" }

        val lastTimestamp = options.timestamp ?: appConfig.loadTimestamp()

        log.info { "Checking ${appConfig.timestampFilePath} to writing..." }
        appConfig
            .runCatching { saveTimestamp(lastTimestamp) }
            .onFailure {
                log.severe { "File ${appConfig.timestampFilePath} can't be written" }
                exitProcess(-1)
            }

        val bot = YoutrackTelegramBot(lastTimestamp, appConfig)

        with (options) {
            val datetime = Youtrack.makeTimedate(lastTimestamp)
            log.info { "Starting last timestamp=$lastTimestamp [$datetime] send=$tgSendMessages services=$tgStartServices" }
            if (daemon > 0) daemonize(bot, daemon, tgSendMessages, tgStartServices) else bot.execute(tgSendMessages)
        }
    }
}


