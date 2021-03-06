package ru.inforion.lab403.utils.ytbot.common

import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.SendMessage
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.Options
import ru.inforion.lab403.utils.ytbot.config.ApplicationConfig
import ru.inforion.lab403.utils.ytbot.fields
import ru.inforion.lab403.utils.ytbot.telegram.TelegramProxy
import ru.inforion.lab403.utils.ytbot.youtrack.Processor
import ru.inforion.lab403.utils.ytbot.youtrack.Youtrack
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import java.text.DateFormat


class YoutrackTelegramBot constructor(
    private val appConfig: ApplicationConfig,
    private val timestampFile: TimestampFile,
    private val sdf: DateFormat
) {
    companion object {
        val log = logger()
    }

    private val bots = mutableMapOf<String, TelegramProxy>()

    private fun createOrGetTelegramProxy(token: String) =
        bots.getOrPut(token) { TelegramProxy(token, appConfig.proxy, appConfig.dns, appConfig.telegramMinimumMessageDelay) }

    private val youtrack by lazy { Youtrack(appConfig.youtrack.baseUrl, appConfig.youtrack.token) }

    private val cache = Cache(appConfig, timestampFile)

    private fun aggregationTimeExceeded(larger: Long, lower: Long) = larger - lower >= appConfig.minutesGroupInterval * 60_000L

    fun execute(options: Options) {
        val projects = youtrack.projects(
            fields(
                Project::id,
                Project::name,
                Project::shortName
            )
        )

        val processor = Processor(youtrack, appConfig, timestampFile, sdf)

        appConfig.projects.map { projectConfig ->
            val project = projects.first { it.name == projectConfig.name }

            val lastTimestamp = timestampFile.loadTimestamp(project.name)

            log.finer { "Parsing '${project.name}' activities for last timestamp=$lastTimestamp [${sdf.format(lastTimestamp)}]" }

            processor.processProject(project, lastTimestamp) { data, issue, timestamp ->
                val bot = createOrGetTelegramProxy(projectConfig.token)
                val chat = projectConfig.chatId

                val previous = cache.getMessage(project.name)

                if (previous != null) when {
                    // we should send previous group because aggregation interval was exceeded and
                    // new activities will never be aggregated with cached...
                    aggregationTimeExceeded(timestamp, previous.timestamp)
                            // we can't aggregate messages from different issue, so send it now
                            // to decouple message from different issues we need to store last timestamp
                            // for each issue separately
                            || previous.issue != issue.id -> {

                        val exceed = aggregationTimeExceeded(timestamp, previous.timestamp)

                        // force send message to telegram
                        log.severe { "Force '${project.name}': ${timestamp - previous.timestamp}, $exceed, ${previous.issue != issue.id}" }

                        cache.sendMessage(project.name, options.dontSendMessage)
                        cache.newMessage(bot, project.name, chat, issue.id, data, timestamp)
                    }

                    previous.data != data -> {
                        cache.replaceMessage(project.name, data, timestamp)
                    }
                } else {
                    cache.newMessage(bot, project.name, chat, issue.id, data, timestamp)
                }
            }
        }

        cache.sendReadyMessages(options.dontSendMessage)
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
        val shortName = issueID.takeWhile { it != '-' }
        val project = projects[userId]
        if (project == null || project.shortName != shortName) {
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
        val tokens = getCommandTokens(params, 2)

        if (tokens.isEmpty()) {
            sendTextMessage(bot, chatId, "Wrong format, use /<cmd> [IssueID] <youtrack_cmd>")
            return null
        }

        val isFirstTokenIssueID = maybeIssueID(tokens[0])

        val others = if (!isFirstTokenIssueID) params else {
            if (tokens.size < 2) {
                sendTextMessage(bot, chatId, "Wrong format, use /<cmd> [IssueID] <youtrack_cmd>")
                return null
            }
            setIssueTo(userId, bot, chatId, tokens[0])
            tokens[1]
        }

        if (projects[userId] == null || issues[userId] == null) {
            sendTextMessage(bot, chatId, "Project or issue not set...")
            return null
        }

        return others
    }

    private fun safeYoutrackCommand(
        bot: TelegramProxy,
        chatId: Long,
        userId: Int,
        command: String? = null,
        comment: String? = null
    ) {
        youtrack
            .runCatching { command(issues[userId]!!.idReadable, command, comment) }
            .onFailure {
                log.severe { it.stackTraceToString() }
                // may lead to vulnerability cus send back bearer
                // sendTextMessage(bot, chatId, "Can't execute command: ${it.message}")
            }
    }

    private fun safeYoutrackCreateIssue(
        bot: TelegramProxy,
        chatId: Long,
        userId: Int,
        summary: String,
        description: String?
    ) {
        youtrack
            .runCatching { issue(projects[userId]!!.shortName, summary, description) }
            .onFailure {
                log.severe { it.stackTraceToString() }
                sendTextMessage(bot, chatId, "Can't create issue: ${it.message}")
            }
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
        val userId: Int = user.id()

        val input = getCommandTokens(text, 2)
        val cmdAndName = getCommandTokens(input[0], 2, '@')
        val botCmd = cmdAndName[0]

        val params = input.getOrNull(1)

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

            "/state" -> {
                val state = setProjectAndIssue(userId, bot, chatId, params)
                if (state != null)
                    safeYoutrackCommand(bot, chatId, userId, command = "State: $state")
            }

            "/create" -> {
                if (params == null) {
                    sendTextMessage(
                        bot,
                        chatId,
                        "Wrong number of parameters, use /create <project> [<summary>] [<description>]"
                    )
                    return
                }

                // TODO: REWRITE THIS WITH STRING STREAM!!!

                val project = params.takeWhile { it != ' ' }

                if (!setProjectTo(userId, bot, chatId, project))
                    return

                var remains = params.removePrefix(project)
                val summaryToken = remains.takeWhile { it != ']' }

                if (summaryToken.isBlank()) {
                    sendTextMessage(
                        bot,
                        chatId,
                        "Wrong number of parameters, use /create <project> [<summary>] [<description>]"
                    )
                    return
                }

                val summary = summaryToken.dropWhile { it != '[' }.drop(1)

                remains = remains.removePrefix(summaryToken).drop(1)

                val descriptionToken = remains.takeWhile { it != ']' }
                val description =
                    if (descriptionToken.isNotBlank()) descriptionToken.dropWhile { it != '[' }.drop(1) else null

                safeYoutrackCreateIssue(bot, chatId, userId, summary, description)
            }

            "/command" -> {
                sendTextMessage(bot, chatId, "Due to security issues arbitrary command now disabled...")
            }

            "/comment" -> {
                val comment = setProjectAndIssue(userId, bot, chatId, params)
                if (comment != null)
                    safeYoutrackCommand(bot, chatId, userId, comment = comment)
            }

            "/hello" -> sendTextMessage(bot, chatId, "Hello $fullname!")

            "/whoami" -> {
                val language = "speaks: ${user.languageCode()}"
                val username = "username: ${user.username()}"
                val isBot = "and you are " + if (user.isBot) "bot" else "not a bot"
                sendTextMessage(bot, chatId, "You are $fullname $username user ID: $userId $language $isBot")
            }

            else -> {
                log.severe { "Unknown command: $botCmd" }
//                sendTextMessage(bot, chatId, "You send me command $botCmd ... ${responses.choice()}")
            }
        }
    }

    private fun failProcessMessage(update: Update?, error: Throwable) {
        val trace = error.stackTraceToString()
        log.severe { "Error occurred when parsing $update: ${error.message}\n$trace" }
    }

    fun createCommandServices() {
        // for only unique project names avoid to create duplicates
        appConfig
            .projects
            .map { createOrGetTelegramProxy(it.token) }
            .distinctBy { it.token }
            .forEach { bot ->
                log.info { "Starting listener for bot = ${bot.token}" }
                val listener = UpdatesListener { updates ->
                    updates.forEach { update ->
                        update
                            .runCatching { tryProcessMessage(bot, update) }
                            .onFailure { error -> failProcessMessage(update, error) }
                    }
                    log.finest { "Confirm all" }
                    UpdatesListener.CONFIRMED_UPDATES_ALL
                }
                bot.setUpdatesListener(listener)
            }
    }
}
