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
import ru.inforion.lab403.utils.ytbot.config.ProxyDnsConfig
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession



class TelegramProxy(token: String, proxy: ProxyConfig? = null) {
    companion object {
        private val log = logger()

        private const val TELEGRAM_API_DOMAIN = "api.telegram.org"

        private fun getTelegramIP(dnsConfig: ProxyDnsConfig): String {
            val dns = DomainNameResolver(dnsConfig.ip, dnsConfig.port)

            log.fine { "Querying address of domain $TELEGRAM_API_DOMAIN" }

            val ips = dns.query(TELEGRAM_API_DOMAIN)

            if (ips.isEmpty())
                throw RuntimeException("Google can't resolve Telegram API URL...")

            val ip = ips.first()

            log.fine { "Using $ip for $TELEGRAM_API_DOMAIN" }

            return ip
        }

        private fun createTelegramBot(token: String, proxy: ProxyConfig?): TelegramBot {
            val clientBuilder = OkHttpClient.Builder()
            val telegramBotBuilder = TelegramBot.Builder(token)

            if (proxy != null) {
                val socketFactory = SSLSocks5Factory(proxy)
                clientBuilder.socketFactory(socketFactory)
                if (proxy.dns != null) {
                    val ip = getTelegramIP(proxy.dns)
                    val url = "https://$ip/bot"
                    telegramBotBuilder.apiUrl(url)
                    clientBuilder.hostnameVerifier { hostname, session ->
                        log.warning { "Approving certificate for $hostname session ${session.cipherSuite} -> ${hostname == ip}" }
                        hostname == ip
                    }
                }
            }

            val client = clientBuilder.build()

            telegramBotBuilder
                .okHttpClient(client)

            return telegramBotBuilder.build()
        }
    }

    private val bot = createTelegramBot(token, proxy)

    fun <T : BaseRequest<*, *>, R : BaseResponse> execute(request: BaseRequest<T, R>): R = bot.execute(request)

    fun <T : BaseRequest<T, R>, R : BaseResponse> execute(request: T, callback: Callback<T, R>) = bot.execute(request, callback)

    fun setUpdatesListener(listener: UpdatesListener) = bot.setUpdatesListener(listener, GetUpdates())

    fun removeGetUpdatesListener() = bot.removeGetUpdatesListener()
}