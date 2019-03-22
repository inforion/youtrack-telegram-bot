package ru.inforion.lab403.utils.ytbot.youtrack

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.youtrack.categories.CategoryId
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.ActivitiesPage
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import java.util.logging.Level

class Youtrack(val baseUrl: String, val permToken: String) {
    companion object {
        val log = logger(Level.FINER)

        private inline fun <reified T> token() = object : TypeToken<T>() { }
    }

    fun idHyperlink(idReadable: String?): String {
        if (idReadable == null) return "#UNKNOWN"
        return "[$idReadable]($baseUrl/issue/$idReadable) #T${idReadable.split('-')[1]}"
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
        return bytes.toString(Charsets.UTF_8)
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
        log.fine { "result = $json" }
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