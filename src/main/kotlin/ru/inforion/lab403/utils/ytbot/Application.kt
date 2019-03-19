package ru.inforion.lab403.utils.ytbot

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.UpdatesListener.CONFIRMED_UPDATES_ALL
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendSticker
import okhttp3.OkHttpClient
import org.simplejavamail.mailer.internal.socks.socks5client.ProxyCredentials
import org.simplejavamail.mailer.internal.socks.socks5client.Socks5
import org.simplejavamail.mailer.internal.socks.socks5client.SocksSocket
import ru.inforion.lab403.common.extensions.hexAsULong
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.Config
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.logging.Level
import javax.net.SocketFactory


class Application {
    companion object {
        private val log = logger(Level.ALL)

        private inline fun <reified T : Any> loadConfig(path: String): T = jacksonObjectMapper().apply {
            configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
        }.readValue(File(path))

        private fun checkTelegram(config: Config) {
            val socketFactory = object : SocketFactory() {
                override fun createSocket(): Socket {
                    if (config.proxy != null) {
                        val proxyAuth = Socks5(InetSocketAddress(config.proxy.host, config.proxy.port))
                        if (config.proxy.auth != null)
                            proxyAuth.credentials = ProxyCredentials(
                                config.proxy.auth.username,
                                config.proxy.auth.password)
                        return SocksSocket(proxyAuth, proxyAuth.createProxySocket())
                    }
                    throw TODO("Now work only with proxy")
                }

                override fun createSocket(host: InetAddress, port: Int) =
                    throw NotImplementedError("Won't be implemented")
                override fun createSocket(address: String, port: Int, localAddress: InetAddress, localPort: Int) =
                    throw NotImplementedError("Won't be implemented")
                override fun createSocket(host: String, port: Int) =
                    throw NotImplementedError("Won't be implemented")
                override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int) =
                    throw NotImplementedError("Won't be implemented")
            }

            val client = OkHttpClient
                .Builder()
                .socketFactory(socketFactory)
                .build()

            val bot = TelegramBot
                .Builder(config.telegram.token)
                .okHttpClient(client)
                .build()

            val chatId = config.telegram.chatId

            val sticker = SendSticker(chatId, "CAADAgADphMAAulVBRire_9EQFdckwI")
            bot.execute(sticker)

            val message = SendMessage(chatId, "WORK?")
            val response = bot.execute(message)
            log.info { response.toString() }

            val listner = UpdatesListener { updates ->
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
            bot.setUpdatesListener(listner)

            System.`in`.read()
        }

        private fun checkYoutrack(config: Config) {
            val youtrack = Youtrack(config.youtrack.baseUrl, config.youtrack.token)

            val projects = youtrack.projects("id", "name")
            projects.forEach { println(it) }

            val kcIdOnly = projects.first { it.name == "Kopycat" }

            val kcAll = youtrack.project(kcIdOnly.id)
            println(kcAll)

            val issues = youtrack.issues(kcAll, "summary")
            issues.forEach { println(it) }
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val config = loadConfig<Config>("temp/config.json")
            checkYoutrack(config)
//            checkTelegram(config)
        }
    }
}