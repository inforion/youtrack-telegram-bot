package ru.inforion.lab403.utils.ytbot

import com.fasterxml.jackson.module.kotlin.readValue
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendSticker
import ru.inforion.lab403.common.extensions.hexAsULong
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy
import java.io.File
import java.util.logging.Level

class CheckTelegram {
    companion object {
        private val log = logger(Level.ALL)

        @JvmStatic
        fun main(args: Array<String>) {
            val config = jsonConfigLoader.readValue<ApplicationConfig>(File("temp/config.json"))

            val projectConfig = config.projects.first()

            val bot = TelegramProxy(projectConfig.token, proxy = config.proxy)

            val chatId = projectConfig.chatId

//            val sticker = SendSticker(chatId, "CAADAgADphMAAulVBRire_9EQFdckwI")
//            bot.execute(sticker)

            val saved = mutableSetOf<String>()

            val listener = UpdatesListener { updates ->
                updates.forEach {
                    log.info { it.toString() }
                    val recv = it.message().text()
                    if (recv.isBlank()) {
                        bot.execute(SendSticker(chatId, "CAADAgADphMAAulVBRire_9EQFdckwI")) // pouk
                    } else if (recv.startsWith("0x")) {
                        val data = recv.removePrefix("0x").hexAsULong
                        bot.execute(SendMessage(chatId, "Смотри как я считать умею: $data"))
                    } else if (recv.toLowerCase() == "что со мной такое?") {
                        bot.execute(SendMessage(chatId, "С тобой всё хорошо, просто ..."))
                        bot.execute(SendSticker(chatId, "CAADAgADphMAAulVBRire_9EQFdckwI")) // pouk
                    } else if (recv.toLowerCase() == "привет") {
                        bot.execute(SendMessage(chatId, "Ну привет. Я умею переводить числа из hex в dec и материться..."))
                        val name = it.message().from().firstName()
                        if (name !in saved) {
                            saved.add(name)
                            bot.execute(SendMessage(chatId, "$name Я тебя запомнил :)"))
                        }
                    } else {
                        bot.execute(SendMessage(chatId, "А ты знаешь, что ты с ботом разговариваешь!"))
                    }
                }
                UpdatesListener.CONFIRMED_UPDATES_ALL
            }
            bot.setUpdatesListener(listener)

            System.`in`.read()
        }
    }
}