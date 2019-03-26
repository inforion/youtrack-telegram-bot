package ru.inforion.lab403.utils.ytbot

import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.SendMessage
import ru.inforion.lab403.common.extensions.choice
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.config.ProjectConfig
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy
import ru.inforion.lab403.utils.ytbot.youtrack.Processor
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import java.util.logging.Level

class YoutrackTelegramBot(
    val startLastTimestamp: Long,
    private val appConfig: ApplicationConfig
) {
    companion object {
        private val log = logger(Level.FINE)
    }

    private val bots = mutableMapOf<String, TelegramProxy>()

    private fun createOrGetTelegramProxy(config: ProjectConfig) =
        bots.getOrPut(config.name) { TelegramProxy(config.token, appConfig.proxy) }

    private val youtrack by lazy { Youtrack(appConfig.youtrack.baseUrl, appConfig.youtrack.token) }

    fun loadCurrentLastTimestamp() = appConfig.loadTimestamp()

    fun execute(tgSendMessages: Boolean, lastTimestamp: Long = startLastTimestamp) {
        log.finer { "Parsing Youtrack activity timestamp=$lastTimestamp" }
        val projects = youtrack.projects(
            fields(
                Project::id,
                Project::name,
                Project::shortName
            )
        )

        log.finest { "Create Youtrack processor..." }
        val processor = Processor(youtrack, lastTimestamp, appConfig)

        appConfig.projects.map { projectConfig ->
            val bot = createOrGetTelegramProxy(projectConfig)
            val project = projects.first { it.name == projectConfig.name }
            processor.processProject(project) { data, activityTimestamp ->
                if (tgSendMessages) {
                    val message = SendMessage(projectConfig.chatId, data)
                        .parseMode(ParseMode.Markdown)
                    log.finest { "Sending chatId = ${projectConfig.chatId} message $message" }
                    val response = bot.execute(message)
                    log.finest { response.toString() }
                }
                if (appConfig.loadTimestamp() < activityTimestamp) {
                    log.info { "Updating timestamp = $activityTimestamp" }
                    appConfig.saveTimestamp(activityTimestamp)
                }
                log.fine(data)
            }
        }
    }

    private var project: Project? = null
    private var issue: Issue? = null

    private fun getCommandTokens(string: String, limit: Int = 0) =
        string
            .split(' ', limit = limit)
            .map { it.trim() }

    private fun sendTextMessage(bot: TelegramProxy, chatId: Long, message: String) {
        val msg = SendMessage(chatId, message)
        val response = bot.execute(msg)
        log.fine { response.toString() }
    }

    private fun setProjectTo(bot: TelegramProxy, chatId: Long, name: String): Boolean {
        val projects = youtrack
            .projects(fields(Project::id, Project::name, Project::shortName))
            .filter { it.name == name || it.shortName == name }

        if (projects.isEmpty()) {
            sendTextMessage(bot, chatId, "Project not found $name")
            return false
        }

        sendTextMessage(bot, chatId, "Set project to ${projects[0].id} ${projects[0].name}")

        project = projects[0]

        return true
    }

    private fun setIssueTo(bot: TelegramProxy, chatId: Long, issueID: String): Boolean {
        if (project == null) {
            val shortName = issueID.takeWhile { it != '-' }
            if (!setProjectTo(bot, chatId, shortName))
                return false
        }

        val issues = youtrack.issues(
            project!!,
            fields(Issue::id, Issue::idReadable, Issue::summary),
            "issue id: $issueID"
        )

        if (issues.isEmpty()) {
            sendTextMessage(bot, chatId, "Issue not found $issueID")
            return false
        }

        sendTextMessage(bot, chatId, "Set issue to ${issues[0].id} ${issues[0].idReadable} ${issues[0].summary}")

        issue = issues[0]

        return true
    }

    private fun maybeIssueID(string: String): Boolean {
        val tokens = string.split('-')
        return tokens.size == 2
                && tokens[0].all { it.isLetterOrDigit() }
                && tokens[1].all { it.isDigit() }
    }

    private val responses = setOf("Meow?", "And what?", "WTF?", "Easy-easy, pal!", "Oh, I'm sick of you!")

    /**
     * command - Executes a Youtrack command
     * comment - Add comment to ticket
     * whoami - Prints full user info
     * hello - Sends a hello message
     * project - Set current project
     * issue - Set current issue
     */
    private fun tryProcessMessage(bot: TelegramProxy, update: Update) {
        log.info { update.toString() }
        val message = update.message()
        if (message == null) {
            log.warning { "Message is null received: $update" }
            return
        }

        val text = message.text()
        if (text == null) {
            log.warning { "Text is null received: $update" }
            return
        }

        val chatId = message.chat().id()
        val user = message.from()

        val firstname = user.firstName() ?: ""
        val lastname = user.lastName() ?: ""
        val fullname = "$firstname $lastname".trim()

        val input = getCommandTokens(text, 2)
        if (input.size < 2) {
            sendTextMessage(bot, chatId, "Wrong format, use <cmd> <parameters>")
            return
        }

        val botCmd = input[0]
        val params = input[1]

        when (botCmd) {
            "/project" -> getCommandTokens(params).also { tokens ->
                when (tokens.size) {
                    1 -> setProjectTo(bot, chatId, tokens[0])
                    else -> sendTextMessage(bot, chatId, "Wrong number of parameters, use /project project")
                }
            }

            "/issue" -> getCommandTokens(params).also { tokens ->
                when (tokens.size) {
                    2 -> setProjectTo(bot, chatId, tokens[0])
                            && setIssueTo(bot, chatId, tokens[1])
                    1 -> setIssueTo(bot, chatId, tokens[0])
                    else -> sendTextMessage(bot, chatId, "Wrong number of parameters, use /issue [project] issue")
                }
            }

            "/command" -> getCommandTokens(params, 2).also { tokens ->
                if (project == null) {
                    sendTextMessage(bot, chatId, "Project not set, use project command")
                    return
                }

                if (tokens.size < 2) {
                    sendTextMessage(bot, chatId, "Wrong format, use /command command")
                    return
                }

                val issueID = tokens[0]
                val command = tokens[1]

                if (!setIssueTo(bot, chatId, issueID))
                    return

                youtrack
                    .runCatching { command(issue!!.idReadable, command) }
                    .onFailure {
                        log.severe { "${it.stackTrace}" }
                        sendTextMessage(bot, chatId, "Exception during command: ${it.message}")
                    }
            }

            "/comment" -> getCommandTokens(params, 2).also { tokens ->
                val comment: String
                val isFirstTokenIssueID = maybeIssueID(tokens[0])

                if (isFirstTokenIssueID) {
                    if (tokens.size < 2) {
                        sendTextMessage(bot, chatId, "Wrong format, use /comment [IssueID] comment")
                        return
                    }
                    setIssueTo(bot, chatId, tokens[0])
                    comment = tokens[1]
                } else {
                    comment = params
                }

                if (project == null || issue == null) {
                    sendTextMessage(bot, chatId, "Project or issue not set...")
                    return
                }

                youtrack
                    .runCatching { command(issue!!.idReadable, comment = comment) }
                    .onFailure {
                        log.severe { "${it.stackTrace}" }
                        sendTextMessage(bot, chatId, "Exception during command: ${it.message}")
                    }
            }

            "/hello" -> sendTextMessage(bot, chatId, "Hello $fullname!")

            "/whoami" -> {
                val language = "speaks: ${user.languageCode()}"
                val userId = "user ID: ${user.id()}"
                val username = "username: ${user.username()}"
                val isBot = "and you are " + if (user.isBot) "bot" else "not a bot"
                sendTextMessage(bot, chatId, "You are $fullname $username $userId $language $isBot")
            }

            else -> sendTextMessage(bot, chatId, responses.choice())
        }
    }

    private fun failProcessMessage(update: Update?, error: Throwable) {
        log.severe { "Error occurred when parsing $update: ${error.message}\n" }
        error.printStackTrace()
    }

    fun createCommandServices() {
        appConfig.projects.forEach { projectConfig ->
            val bot = createOrGetTelegramProxy(projectConfig)
            val listener = UpdatesListener { updates ->
                updates.forEach { update ->
                    update
                        .runCatching { tryProcessMessage(bot, update) }
                        .onFailure { error -> failProcessMessage(update, error) }
                }

                log.warning { "Confirm all" }
                UpdatesListener.CONFIRMED_UPDATES_ALL
            }
            bot.setUpdatesListener(listener)
        }
    }
}