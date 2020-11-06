package ru.inforion.lab403.utils.ytbot.common

import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy

class Cache(
    private val appConfig: ApplicationConfig,
    private val timestampFile: TimestampFile
) {
    companion object {
        val log = logger()
    }

    private val cache = mutableMapOf<String, CachedMessage>()

    fun getMessage(project: String) = cache[project]

    fun newMessage(bot: TelegramProxy, project: String, chat: Long, issue: String, data: String, timestamp: Long) {
        log.fine { "[$timestamp] New message for project '$project' with data:\n$data" }

        require(project !in cache) {
            "Cache already has message for '${project}', please send it first or it will be lost!"
        }

        // because used grouping activities we can't use here System.now()
        cache[project] = CachedMessage(bot, data, chat, issue, timestamp)
    }

    fun replaceMessage(project: String, data: String, timestamp: Long) {
        log.fine { "[$timestamp] Replace message for project '$project' with data:\n${data}" }

        val message = cache.remove(project)
            ?: throw IllegalArgumentException("Message for id = '${project}' not exist in cache")

        cache[project] = CachedMessage(message.bot, data, message.chat, message.issue, timestamp)
    }

    fun sendMessage(project: String, dontSendMessage: Boolean) {
        val message = cache.remove(project)
            ?: throw IllegalArgumentException("Message for id = '$project' not exist in cache")
        log.fine { "[${message.timestamp}] Send message for project '$project': ${message.data.take(30)}" }
        if (!dontSendMessage)
            message.send(appConfig.telegramSendRetriesCount, appConfig.telegramSendRetriesTimeout)
        timestampFile.saveTimestamp(project, message.timestamp)
    }

    fun sendReadyMessages(dontSendMessage: Boolean) {
        cache.filterValues {
            it.ready(appConfig.messageWaitInterval * 1000L).apply {
                log.severe { "Message ${it.data.take(20)}... is ready to send timestamp = ${it.timestamp} ready = $this" }
            }
        }.forEach { (project, _) ->
            sendMessage(project, dontSendMessage)
        }
    }
}