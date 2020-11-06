package ru.inforion.lab403.utils.ytbot.common

import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMessage
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy

class CachedMessage constructor(
    val bot: TelegramProxy,
    val data: String,
    val chat: Long,
    val issue: String,
    val timestamp: Long
) {
    companion object {
        val log = logger()
    }

    fun ready(delay: Long) = System.currentTimeMillis() - timestamp > delay

    private fun doSend(bot: TelegramProxy, message: SendMessage, number: Int): Boolean {
//      log.warning { "sending message try ${number}/${appConfig.telegramSendRetriesCount}" }

        val response = bot
            .runCatching { execute(message) }
            .getOrElse {
                log.severe { it }
                return false
            }

        if (response.message() == null) {
            log.severe { "Telegram response with error[${response.errorCode()}]:\n$data" }
            return false
        }

        return true
    }

    fun send(retries: Int, delay: Long) {
        val message = SendMessage(chat, data).parseMode(ParseMode.Markdown)

        log.finest { "Sending chatId = $chat message $message" }

        var errorOccurred = false
        // repeat wont break :(
        for (number in 0 until retries) {
            if (doSend(bot, message, number)) {
                if (errorOccurred)
                    log.info { "Message send after error OK at try number ${number + 1}!" }
                break
            }

            errorOccurred = true
            Thread.sleep(delay)
        }
    }
}