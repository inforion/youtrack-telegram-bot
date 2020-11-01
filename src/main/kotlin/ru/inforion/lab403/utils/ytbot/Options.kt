package ru.inforion.lab403.utils.ytbot

import ru.inforion.lab403.common.extensions.argparse.ApplicationOptions
import ru.inforion.lab403.common.extensions.argparse.file
import ru.inforion.lab403.common.extensions.argparse.flag
import ru.inforion.lab403.common.extensions.argparse.variable
import java.io.File

class Options : ApplicationOptions(
    "youtrack-telegram-bot",
    "Telegram bot to pass from Youtrack to Telegram channel"
) {
    val config: File by file("-c", "--config",
        "Path to the configuration file", required = true, exists = true, canRead = true)

    val timestamp: Long? by variable("-t", "--timestamp", "Redefine starting last update timestamp")

    val daemon: Int by variable(
        "-d", "--daemon",
        "Create daemon with specified update timeout in seconds (<= 86400)"
    ) { -1 }

    val certificate: File? by file("-cert", "--cert",
        "YouTrack server self-signed certificate", exists = true, canRead = true)

    val dontSendMessage by flag("-r", "--dont-send-messages", "Don't send messages to Telegram")

    val dontStartServices by flag(
        "-a", "--dont-start-services",
        "Don't start command services for Telegram when in daemon mode"
    )

    val checkTelegram: String? by variable(
        "-tgc", "--check-telegram",
        "Send message to telegram and exit. Format [chatProjectName:type:message], for message type=m"
    )

    val checkYoutrack: String? by variable(
        "-ytc", "--check-youtrack",
        "Get project info from Youtrack. Format [projectName]"
    )
}