package ru.inforion.lab403.utils.ytbot

import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendSticker
import ru.inforion.lab403.common.logging.ALL
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy
import java.util.logging.Level
import kotlin.system.exitProcess

class TelegramChecker(val config: ApplicationConfig) {
    companion object {
        val log = logger(ALL)
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

//        val message = """
//05.04.2019 #FC16\[[⇗](https://hp-403.inforion.ru:8440/issue/FC-16)] Починить генерацию по posedge nand_nwe #CustomField
//- Author: [u](tg://user?id=322510998)\#agladkikh\[[⇗](https://hp-403.inforion.ru:8440/users/a4b77014-4fea-434f-8d4f-df5fae614258)]
//- Assignee: [u](tg://user?id=207866443)\#mkomakhin\[[⇗](https://hp-403.inforion.ru:8440/users/ba2bc66f-48c0-45e5-876b-93e4c46c41b0)]
//- \[[11:05:03](https://hp-403.inforion.ru:8440/issue/FC-16#focus=streamItem-0-0.88-22203)] Added Type: #Epic
//        """.trimIndent()

        val response = when (type) {
            "s" -> bot.execute(SendSticker(chatId, message))
            "m" -> bot.execute(SendMessage(chatId, message).parseMode(ParseMode.Markdown))
            else -> {
                log.warning { "Check type is unknown... start stupid server!" }
                stupidServer(chatId, bot, message)
                exitProcess(0)
            }
        }

        log.info { "isOk=${response.isOk} error=${response.errorCode()}" }

        if (response.message() == null) {
            log.severe { "Message send failed!" }
        } else {
            log.info { response.toString() }
        }
    }
}