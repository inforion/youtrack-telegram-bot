package ru.inforion.lab403.utils.ytbot.youtrack

import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.inforion.lab403.common.logging.INFO
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.concat
import ru.inforion.lab403.utils.ytbot.config.TelegramUserConfig
import ru.inforion.lab403.utils.ytbot.normalizeURL
import ru.inforion.lab403.utils.ytbot.removeChars
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.ActivitiesPage
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import java.text.SimpleDateFormat
import java.util.*

class Youtrack(val baseUrl: String, private val permToken: String) {
    companion object {
        val log = logger(INFO)

        /**
         * Function to short call for generate object type token for Gson library
         *
         * NOTE: Originally this function required only for List<T> object mapping
         */
        private inline fun <reified T> token() = object : TypeToken<T>() { }

        /**
         * Symbol ⇗
         */
        private const val ARROW_CHAR = "\u21D7"

        /**
         * Make markdown URL like: [⇗](url)
         *
         * @param url URL to make
         *
         * @return markdown URL
         */
        private fun markdownUrl(url: String, inlineString: String = ARROW_CHAR): String = "\\[[$inlineString]($url)]"
    }

    /**
     * Creates tagged issue ID with link to Youtrack
     *
     * @param idReadable readable ID of issue from Youtrack
     *
     * @return tagged issue ID
     */
    fun tagId(idReadable: String): String {
        val tagId = idReadable.removeChars('-')
        return "#$tagId${markdownUrl("$baseUrl/issue/$idReadable")}"
    }

    /**
     * Creates tagged user name from login and Youtrack-Telegram mapping
     *
     * @param tag text to show for before username for telegram tag
     * @param login user login in Youtrack
     * @param ringId user Hub connection (not used currently) perhaps make link to user in Youtrack
     * @param map Youtrack-Telegram mapping (from JSON-configuration)
     *
     * @return tagged user name with Telegram mention
     */
    fun tagUsername(
        tag: String,
        login: String,
        ringId: String?,
        map: Map<String, TelegramUserConfig>? = null
    ): String {
        val tagName = login.removeChars('_', '.')
        if (map != null) {
            val tgUser = map[login]
            if (tgUser?.id != null) {
                log.finest { "User with login = $login found -> telegram = $tgUser" }
                val tgUserString = "[$tag](tg://user?id=${tgUser.id}) #$tagName"

                return if (ringId == null) tgUserString else
                    "$tgUserString${markdownUrl("$baseUrl/users/$ringId")}"
            }

            log.finest { "User with login = $login not found in Youtrack-Telegram users mapping" }
        }
        val tgUserString = "#$tagName"
        return if (ringId == null) tgUserString else
            "$tgUserString${markdownUrl("$baseUrl/users/$ringId")}"
    }

    /**
     * Creates tagged activity category name
     *
     * @param categoryId activity category (enum)
     *
     * @return tagged activity category
     */
    fun tagCategory(categoryId: CategoryId) = "#${categoryId.tag}"

    /**
     * Creates activity link
     *
     * @param idReadable readable ID of issue activity
     * @param activityId internal activity ID
     *
     * @return activity link
     */
    fun activityPermlink(idReadable: String, activityId: String, inlineString: String) =
        markdownUrl("$baseUrl/issue/$idReadable#focus=streamItem-$activityId", inlineString)

    enum class API { api, rest }

    private fun httpGet(api: API, url: String, parameters: List<Pair<String, Any>>): String {
        val request = "$baseUrl/${api.name}/$url"
            .normalizeURL()
            .httpGet(parameters)
            // this is bug in [HttpClient.retrieveResponse] at line:
            // val contentStream = dataStream(request, connection)?.decode(transferEncoding) ?: ByteArrayInputStream(ByteArray(0))
            // decode has no case for charset=utf-8
            // but when decodeContent disable it's ok
            .also { it.executionOptions.decodeContent = false }
            .authentication()
            .bearer(permToken)
            .header(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "Cache-Control" to "no-cache")

        log.finest { request }

        val result = request.responseString()

        val response = result.second
        val (string, error) = result.third

        log.fine { "${response.statusCode} <- ${response.url}" }

        check(response.statusCode != -1) { "Internal error -> ${error!!.message}\n$request\n$response" }

        check(response.statusCode == 200) { "HTTP request error\n$request\n$response" }

        check(response.headers["Content-Type"].any { "application/json" in it }) {
            "Wrong Content-Type for request\n$request\n$response"
        }

        check(error == null) { "Critical Fuel error -> ${error!!.message}\n$request\n$response" }
        check(string != null) { "Critical Fuel error -> body is empty but should not be\n$request\n$response" }

        return string
    }

    /**
     * Make query and get response from Youtrack REST
     *
     * @param url request url
     * @param query filter query string
     * @param fields request fields (if not specified just IDs will be returned)
     * @param categories request categories (relevant for activities)
     *
     * @return JSON string response from Youtrack
     */
    fun queryRaw(
        url: String,
        query: String?,
        fields: String?,
        categories: Collection<CategoryId>?
    ): String {
        val parameters = mutableListOf<Pair<String, String>>().apply {
            fields?.let { add("fields" to it.trim()) }
            query?.let { add("query" to it.trim()) }
            categories?.let { add("categories" to it.concat()) }
        }

        return httpGet(API.api, url, parameters)
    }

    /**
     * JSON mapper that use Fuel library.
     * Gson can ignore omitted fields in JSON (Jackson can't)
     */
    private val mapper = Gson()

    /**
     * Query from Youtrack single object and map it to specified type
     *
     * NOTE: due to type erasure (even with reified) we can't pass type from upper functions
     *       like projects, issues and etc. so should use this quirk with token.
     *       May be it's Kotlin bug, may be I don't understand something...
     *
     * @param token token to determine type
     *
     * @param url request url
     * @param query filter query string
     * @param fields request fields (if not specified just IDs will be returned)
     * @param categories request categories (relevant for activities)
     *
     * @return single Youtrack object
     */
    private inline fun <reified T> querySingle(
        token: TypeToken<T>,
        url: String,
        fields: String? = null,
        query: String? = null,
        categories: Collection<CategoryId>? = null
    ): T {
        val json = queryRaw(url, query, fields, categories)
        return mapper.fromJson(json, token.type)
    }

    /**
     * Query from Youtrack multiple object and map it to specified type (i.e. list of issues)
     *
     * NOTE: due to type erasure (even with reified) we can't pass type from upper functions
     *       like projects, issues and etc. so should use this quirk with token.
     *       May be it's Kotlin bug, may be I don't understand something...
     *
     * @param token token to determine type
     *
     * @param url request url
     * @param query filter query string
     * @param fields request fields (if not specified just IDs will be returned)
     * @param categories request categories (relevant for activities)
     *
     * @return collection of Youtrack objects
     */
    private inline fun <reified T> queryMultiple(
        token: TypeToken<List<T>>,
        url: String,
        fields: String? = null,
        query: String? = null,
        categories: Collection<CategoryId>? = null
    ): List<T> {
        val json = queryRaw(url, query, fields, categories)
        return mapper.fromJson(json, token.type)
    }

    /**
     * Request from Youtrack all projects with specified [fields]
     *
     * NOTE: Functions fields and with in extensions.kt may be used to create [fields] string
     *
     * @param fields requested fields in Youtrack REST format
     * @param query additional query for request
     */
    fun projects(fields: String, query: String = "")
            = queryMultiple<Project>(token(), "admin/projects", fields, query)

    /**
     * Request from Youtrack issues for specified [project] and with specified [fields]
     *
     * NOTE: Functions fields and with in extensions.kt may be used to create [fields] string
     *
     * @param project Youtrack project name
     * @param fields requested fields in Youtrack REST format
     * @param query additional query for request
     */
    fun issues(project: String, fields: String, query: String = "")
            = queryMultiple<Issue>(token(), "issues", fields, "in: $project $query")

    /**
     * Request from Youtrack issues for specified [project] and with specified [fields]
     *
     * NOTE: Functions fields and with in extensions.kt may be used to create [fields] string
     *
     * @param project Youtrack project (Project name must not be null!)
     * @param fields requested fields in Youtrack REST format
     * @param query additional query for request
     */
    fun issues(project: Project, fields: String, query: String = "") = issues(project.name, fields, query)

    /**
     * Request from Youtrack activity page for specified [issue], [categories] and with specified [fields]
     *
     * NOTE: Functions fields and with in extensions.kt may be used to create [fields] string
     *
     * @param issue Youtrack issue to get activities (Issue ID must not be null!)
     * @param fields requested fields in Youtrack REST format
     * @param categories issue activity categories (see enum)
     */
    fun activitiesPage(issue: Issue, fields: String, categories: Collection<CategoryId>)
            = querySingle<ActivitiesPage>(token(), "issues/${issue.id}/activitiesPage", fields, null, categories)

    /**
     * Request from Youtrack project with [projectID] and specified [fields]
     *
     * NOTE: Functions fields and with in extensions.kt may be used to create [fields] string
     *
     * @param projectID project ID to request
     * @param fields requested fields in Youtrack REST format
     */
    fun project(projectID: String, fields: String)
            = querySingle<Project>(token(), "admin/projects/$projectID", fields)

    /**
     * Request Youtrack to execute command for issue with [issueID]
     *
     * WARNING: Using old REST API currently!
     */
    fun command(
        issueID: String,
        command: String? = null,
        comment: String? = null,
        group: String? = null,
        disableNotifications: Boolean = false,
        runAs: String? = null
    ) {
        require(command != null || comment != null) { "command or comment must be set!" }

        val parameters = mutableListOf<Pair<String, String>>().apply {
            command?.let { add("command" to it.trim()) }
            comment?.let { add("comment" to it.trim()) }
            group?.let { add("group" to it.trim()) }
            runAs?.let { add("runAs" to it.trim()) }

            if (disableNotifications)
                add("disableNotifications" to disableNotifications.toString())
        }

        httpGet(API.rest, "issue/$issueID/execute", parameters)
    }

    /**
     * Request Youtrack to create a new issue for [projectID] and with [summary] and [description]
     *
     * @param projectID project ID where issue to create
     * @param summary new issue summary
     * @param description new issue description
     */
    fun issue(
        projectID: String,
        summary: String,
        description: String? = null
    ) {
        val parameters = mutableListOf<Pair<String, String>>().apply {
            add("project" to projectID)
            add("summary" to summary)

            description?.let { add("description" to it) }
        }

        httpGet(API.rest, "issue", parameters)
    }
}