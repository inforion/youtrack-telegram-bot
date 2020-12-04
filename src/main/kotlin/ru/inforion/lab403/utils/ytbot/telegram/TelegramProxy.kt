package ru.inforion.lab403.utils.ytbot.telegram

import com.pengrad.telegrambot.Callback
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.request.BaseRequest
import com.pengrad.telegrambot.request.GetUpdates
import com.pengrad.telegrambot.response.BaseResponse
import okhttp3.OkHttpClient
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ProxyConfig
import ru.inforion.lab403.utils.ytbot.config.DnsConfig
import java.util.concurrent.TimeUnit


class TelegramProxy constructor(
    val token: String,
    proxy: ProxyConfig? = null,
    dns: DnsConfig? = null,
    private val minimumMessageDelay: Long = 0
) {
    companion object {
        val log = logger()

        private const val TELEGRAM_API_DOMAIN = "api.telegram.org"

        private fun DnsConfig.resolve(hostname: String) = DomainNameResolver(ip, port).query(hostname).also {
            log.config { "Resolved ${it.joinToString()} for $hostname" }
        }

        private fun createTelegramBot(token: String, proxy: ProxyConfig?, dns: DnsConfig?): TelegramBot {
            // set parameter like original in telegram bot builder
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(75, TimeUnit.SECONDS)
                .writeTimeout(75, TimeUnit.SECONDS)
                .readTimeout(75, TimeUnit.SECONDS)

            if (dns != null) {
                clientBuilder.dns { dns.resolve(it) }
            }

            val telegramBotBuilder = TelegramBot.Builder(token)

            if (proxy != null) {
                clientBuilder.socketFactory(SSLSocks5Factory(proxy))

                if (dns != null) {
                    // FIXME: perhaps this code for IP determination is not needed because we set dns for OkHttp client
                    val ips = dns.resolve(TELEGRAM_API_DOMAIN).apply {
                        check(isNotEmpty()) { "Can't resolve $TELEGRAM_API_DOMAIN API URL" }
                        val hostAddress = first().hostAddress
                        telegramBotBuilder.apiUrl("https://$hostAddress/bot")
                    }

                    clientBuilder.hostnameVerifier { hostname, session ->
                        log.warning { "Approving certificate for $hostname session ${session.cipherSuite}" }
                        hostname in ips.map { it.hostAddress }
                    }
                }
            }

            val client = clientBuilder.build()

            telegramBotBuilder
                .okHttpClient(client)

            return telegramBotBuilder.build()
        }
    }

    private var lastMessageSendTime = -1L

    private fun waitIfRequired() {
        val now = System.currentTimeMillis()
        if (lastMessageSendTime != -1L) {
            val passed = now - lastMessageSendTime
            val waitAmount = minimumMessageDelay - passed
            if (waitAmount > 0) {
                log.warning { "Waiting for $waitAmount ms" }
                Thread.sleep(waitAmount)
            }
        }
        lastMessageSendTime = System.currentTimeMillis()
    }

    private val bot = createTelegramBot(token, proxy, dns)

    fun <T : BaseRequest<T, R>, R : BaseResponse> execute(request: BaseRequest<T, R>): R {
        waitIfRequired()
        return bot.execute(request)
    }

    fun <T : BaseRequest<T, R>, R : BaseResponse> execute(request: T, callback: Callback<T, R>) {
        waitIfRequired()
        bot.execute(request, callback)
    }

    fun setUpdatesListener(listener: UpdatesListener) = bot.setUpdatesListener(listener, GetUpdates())

    fun removeGetUpdatesListener() = bot.removeGetUpdatesListener()
}