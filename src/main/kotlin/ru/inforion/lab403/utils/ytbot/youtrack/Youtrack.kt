package ru.inforion.lab403.utils.ytbot.youtrack

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.ActivitiesPage
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.User
import java.util.logging.Level

class Youtrack(val baseUrl: String, val permToken: String) {
    companion object {
        val log = logger(Level.INFO)

        inline fun <reified T> token() = object : TypeToken<T>() { }
    }

    fun tagId(idReadable: String): String {
        val tagId = idReadable.replace("-", "")
        return "[#$tagId]($baseUrl/issue/$idReadable)"
    }

    fun tagUsername(login: String, name: String, email: String?, ringId: String?, map: Map<String, String>? = null): String {
//        val nm = if (email != null)
//            email.split("@")[0]
//        else
//            name.split(" ")[0]
//        val tagName = nm.replace(".", "")

        val tgTag = if (map != null) map[login] else null
        val tagName = login.replace(".", "")
        val nm = if (ringId == null) "#$tagName" else "[#$tagName]($baseUrl/users/$ringId)"

        return if (tgTag != null) "$nm @$tgTag" else nm
    }

    fun tagUsername(user: User, map: Map<String, String>? = null)
            = tagUsername(user.login, user.name, user.email, user.ringId, map)

    fun tagActivity(categoryId: CategoryId): String {
        return "#${categoryId.tag}"
    }

    fun queryRaw(
        url: String,
        query: String?,
        fields: String?,
        categories: Collection<CategoryId>?
    ): String {
        var queryError: FuelError? = null
        var queryBytes: ByteArray? = null

        val parameters = mutableListOf<Pair<String, String>>()

        if (fields != null)
            parameters.add("fields" to fields)

        if (query != null)
            parameters.add("query" to query)

        if (categories != null)
            parameters.add("categories" to categories.joinToString(",") { it.name })

        log.finer { "Requesting url=$url" }
        parameters.forEach { log.fine { "${it.first} -> ${it.second}" } }

        val request = "$baseUrl/api/$url"
            .httpGet(parameters)
            .authentication()
            .bearer(permToken)
            .response { request, response, (bytes, error) ->
                log.finest { request.toString() }
                log.finest { response.toString() }
                queryBytes = bytes
                queryError = error
            }

        request.join()

        if (queryError != null)
            throw queryError!!.exception

        val bytes = queryBytes ?: throw RuntimeException("Empty query result received for $url...")
        val result = bytes.toString(Charsets.UTF_8)
        log.finest { "json = $result" }
        return result
    }

    val mapper = Gson()

    inline fun <reified T> querySingle(
        token: TypeToken<T>,
        url: String,
        fields: String? = null,
        query: String? = null,
        categories: Collection<CategoryId>? = null
    ): T {
        val json = queryRaw(url, query, fields, categories)
        return mapper.fromJson<T>(json, token.type)
    }

    inline fun <reified T> queryMultiple(
        token: TypeToken<List<T>>,
        url: String,
        fields: String? = null,
        query: String? = null,
        categories: Collection<CategoryId>? = null
    ): List<T> {
        val json = queryRaw(url, query, fields, categories)
        return mapper.fromJson<List<T>>(json, token.type)
    }

    fun projects(fields: String) = queryMultiple<Project>(token(), "admin/projects", fields)

    fun issues(project: Project, fields: String)
            = queryMultiple<Issue>(token(), "issues", fields, "in: ${project.name}")

    fun activitiesPage(issue: Issue, fields: String, categories: Collection<CategoryId>)
            = querySingle<ActivitiesPage>(token(), "issues/${issue.id}/activitiesPage", fields, null, categories)

    fun project(projectID: String, fields: String)
            = querySingle<Project>(token(), "admin/projects/$projectID", fields)
}