package ru.inforion.lab403.utils.ytbot

import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendSticker
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy
import java.util.logging.Level
import kotlin.system.exitProcess

class TelegramChecker(val config: ApplicationConfig) {
    companion object {
        private val log = logger(Level.ALL)
    }

    private fun stupidServer(chatId: Long, bot: TelegramProxy, hello: String) {
        bot.execute(SendMessage(chatId, hello))

        val listener = UpdatesListener { updates ->
            updates.forEach {
                log.info { it.toString() }
                try {
                    val text = it.message().text()
                    when (text.toLowerCase()) {
                        "hello" -> {
                            val name = it.message().from().firstName()
                            val id = it.message().from().id()
                            bot.execute(SendMessage(chatId, "$name [$id]"))
                        }
                    }
                } catch (e: Throwable) {
                    bot.execute(SendMessage(chatId, e.message))
                }
            }

            log.warning { "Confirm all" }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }
        bot.setUpdatesListener(listener)

        System.`in`.read()
    }

    fun check(project: String, type: String, message: String) {
        val projectConfig = config.projects.first { it.name == project }

        val bot = TelegramProxy(projectConfig.token, config.proxy)

        val chatId = projectConfig.chatId

        val response = when (type) {
            "s" -> bot.execute(SendSticker(chatId, message))
            "m" -> bot.execute(SendMessage(chatId, message))
            else -> {
                log.warning { "Check type is unknown... start stupid server!" }
                stupidServer(chatId, bot, message)
                exitProcess(0)
            }
        }

        log.info { response.toString() }
    }
}