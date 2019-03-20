package ru.inforion.lab403.utils.ytbot.telegram

import com.pengrad.telegrambot.Callback
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.request.BaseRequest
import com.pengrad.telegrambot.request.GetUpdates
import com.pengrad.telegrambot.response.BaseResponse
import okhttp3.OkHttpClient
import ru.inforion.lab403.utils.ytbot.config.ProxyConfig

class TelegramProxy(token: String, proxy: ProxyConfig? = null) {
    private val socketFactory = SSLSocks5Factory(proxy)

    private val client = OkHttpClient
        .Builder()
        .socketFactory(socketFactory)
        .build()

    private val bot = TelegramBot
        .Builder(token)
        .okHttpClient(client)
        .build()

    fun <T : BaseRequest<*, *>, R : BaseResponse> execute(request: BaseRequest<T, R>): R = bot.execute(request)

    fun <T : BaseRequest<T, R>, R : BaseResponse> execute(request: T, callback: Callback<T, R>) = bot.execute(request, callback)

    fun setUpdatesListener(listener: UpdatesListener) = bot.setUpdatesListener(listener, GetUpdates())

    fun removeGetUpdatesListener() = bot.removeGetUpdatesListener()
}