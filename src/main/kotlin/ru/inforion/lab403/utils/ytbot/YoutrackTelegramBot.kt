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

    private val projects = mutableMapOf<Int, Project>()
    private val issues = mutableMapOf<Int, Issue>()

    private fun getCommandTokens(string: String?, limit: Int = 0, delimiter: Char = ' ') =
        string?.split(delimiter, limit = limit)?.map { it.trim() } ?: emptyList()

    private fun sendTextMessage(bot: TelegramProxy, chatId: Long, message: String) {
        val msg = SendMessage(chatId, message)
        val response = bot.execute(msg)
        log.fine { response.toString() }
    }

    private fun setProjectTo(userId: Int, bot: TelegramProxy, chatId: Long, name: String): Boolean {
        val requestedProjects = youtrack
            .projects(fields(Project::id, Project::name, Project::shortName))
            .filter { it.name == name || it.shortName == name }

        if (requestedProjects.isEmpty()) {
            sendTextMessage(bot, chatId, "Project not found $name")
            return false
        }

//        sendTextMessage(bot, chatId, "Set project to ${projects[0].id} ${projects[0].name}")
        projects[userId] = requestedProjects[0]

        return true
    }

    private fun setIssueTo(userId: Int, bot: TelegramProxy, chatId: Long, issueID: String): Boolean {
        if (projects[userId] == null) {
            val shortName = issueID.takeWhile { it != '-' }
            if (!setProjectTo(userId, bot, chatId, shortName))
                return false
        }

        val requestedIssues = youtrack.issues(
            projects[userId]!!,
            fields(Issue::id, Issue::idReadable, Issue::summary),
            "issue id: $issueID"
        )

        if (requestedIssues.isEmpty()) {
            sendTextMessage(bot, chatId, "Issue not found $issueID")
            return false
        }

//        sendTextMessage(bot, chatId, "Set issue to ${issues[0].id} ${issues[0].idReadable} ${issues[0].summary}")

        issues[userId] = requestedIssues[0]

        return true
    }

    private fun setProjectAndIssue(userId: Int, bot: TelegramProxy, chatId: Long, params: String?): String? {
        val others: String?

        val tokens = getCommandTokens(params, 2)

        if (tokens.isEmpty()) {
            sendTextMessage(bot, chatId, "Wrong format, use /<cmd> [IssueID] <youtrack_cmd>")
            return null
        }

        val isFirstTokenIssueID = maybeIssueID(tokens[0])

        if (isFirstTokenIssueID) {
            if (tokens.size < 2) {
                sendTextMessage(bot, chatId, "Wrong format, use /<cmd> [IssueID] <youtrack_cmd>")
                return null
            }
            setIssueTo(userId, bot, chatId, tokens[0])
            others = tokens[1]
        } else {
            others = params
        }

        if (projects[userId] == null || issues[userId] == null) {
            sendTextMessage(bot, chatId, "Project or issue not set...")
            return null
        }

        return others
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
    private fun tryProcessMessage(defaultProject: String, bot: TelegramProxy, update: Update) {
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
        val userId: Int = user.id()

        val input = getCommandTokens(text, 2)
        val cmdAndName = getCommandTokens(input[0], 2, '@')
        val botCmd = cmdAndName[0]

        val params = input.getOrNull(1)

        if (projects[userId] == null)
            setProjectTo(userId, bot, chatId, defaultProject)

        when (botCmd) {
            "/project" -> getCommandTokens(params).also { tokens ->
                when (tokens.size) {
                    1 -> setProjectTo(userId, bot, chatId, tokens[0])
                    0 -> sendTextMessage(bot, chatId, "Current default project ${projects[userId]?.name}")
                    else -> sendTextMessage(bot, chatId, "Wrong number of parameters, use /project project")
                }
            }

            "/issue" -> getCommandTokens(params).also { tokens ->
                when (tokens.size) {
                    2 -> setProjectTo(userId, bot, chatId, tokens[0])
                            && setIssueTo(userId, bot, chatId, tokens[1])
                    1 -> setIssueTo(userId, bot, chatId, tokens[0])
                    0 -> sendTextMessage(bot, chatId, "Current default issue ${issues[userId]?.idReadable}")
                    else -> sendTextMessage(bot, chatId, "Wrong number of parameters, use /issue [project] issue")
                }
            }

            "/command" -> {
                val command = setProjectAndIssue(userId, bot, chatId, params)

                youtrack
                    .runCatching { command(issues[userId]!!.idReadable, command = command) }
                    .onSuccess {
                        sendTextMessage(bot, chatId, "Command done: $command")
                    }
                    .onFailure {
                        log.severe { "${it.stackTrace}" }
                        sendTextMessage(bot, chatId, "Exception during command: ${it.message}")
                    }
            }

            "/comment" -> {
                val comment = setProjectAndIssue(userId, bot, chatId, params)

                youtrack
                    .runCatching { command(issues[userId]!!.idReadable, comment = comment) }
                    .onSuccess {
                        sendTextMessage(bot, chatId, "Comment added: $comment")
                    }
                    .onFailure {
                        log.severe { "${it.stackTrace}" }
                        sendTextMessage(bot, chatId, "Exception during command: ${it.message}")
                    }
            }

            "/hello" -> sendTextMessage(bot, chatId, "Hello $fullname!")

            "/whoami" -> {
                val language = "speaks: ${user.languageCode()}"
                val username = "username: ${user.username()}"
                val isBot = "and you are " + if (user.isBot) "bot" else "not a bot"
                sendTextMessage(bot, chatId, "You are $fullname $username user ID: $userId $language $isBot")
            }

            else -> sendTextMessage(bot, chatId, "You send me command $botCmd ... ${responses.choice()}")
        }
    }

    private fun failProcessMessage(update: Update?, error: Throwable) {
        log.severe { "Error occurred when parsing $update: ${error.message}\n" }
        error.printStackTrace()
    }

    fun createCommandServices() {
        // for only unique project names avoid to create duplicates
        appConfig
            .projects
            .associateBy { it.name }
            .forEach { (name, config) ->
                val bot = createOrGetTelegramProxy(config)
                val listener = UpdatesListener { updates ->
                    updates.forEach { update ->
                        update
                            .runCatching { tryProcessMessage(name, bot, update) }
                            .onFailure { error -> failProcessMessage(update, error) }
                    }

                    log.warning { "Confirm all" }
                    UpdatesListener.CONFIRMED_UPDATES_ALL
                }
                bot.setUpdatesListener(listener)
            }
    }
}