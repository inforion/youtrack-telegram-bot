package ru.inforion.lab403.utils.ytbot.youtrack

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.ActivityItem
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.ActivityItemField
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import java.lang.reflect.ParameterizedType
import java.util.logging.Level
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.javaType

class Youtrack(val baseUrl: String, val permToken: String) {
    companion object {
        val log = logger(Level.FINE)

        inline fun <reified T> Gson.fromJson(json: String) = fromJson<T>(json, object: TypeToken<T>() {}.type)
        inline fun <reified T> Array<out KProperty1<T, *>>.names() = map { it.name }.toTypedArray()
    }

    inline fun <reified T> getFieldsFrom(fields: Collection<String>?): Collection<String>? {
        if (fields == null)
            return null

        var result = T::class.declaredMembers
            .filter { it !is KFunction<*> }
            .map {
                val jtype = it.returnType.javaType
                val whatToCheck = if (jtype is ParameterizedType) jtype.actualTypeArguments.first() else jtype
                val typeName = whatToCheck.typeName
                val packageName = typeName.substringBeforeLast('.')
                val postfix = if (packageName.endsWith("youtrack.scheme")) "(id)" else ""
                it.name to postfix
            }
        if (fields.isNotEmpty())
            result = result.filter { it.first in fields }
        return result.map { "${it.first}${it.second}" }
    }

    fun queryRaw(
        url: String,
        query: String?,
        categories: Collection<Category>?,
        fields: Collection<String>?
    ): String {
        var queryError: FuelError? = null
        var queryBytes: ByteArray? = null

        val parameters = mutableListOf<Pair<String, String>>()

        if (fields != null)
            parameters.add("fields" to fields.joinToString())

        if (query != null)
            parameters.add("query" to query)

        if (categories != null)
            parameters.add("categories" to categories.joinToString { it.name })

        val request = "$baseUrl/api/$url"
            .httpGet(parameters)
            .authentication()
            .bearer(permToken)
            .response { request, response, (bytes, error) ->
                log.finer { request.toString() }
                log.finer { response.toString() }
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
        url: String,
        fields: List<String>? = null,
        query: String? = null
    ): T {
        val filter = getFieldsFrom<T>(fields)
        val json = queryRaw(url, query, null, filter)
        return Gson().fromJson<T>(json)
    }

    inline fun <reified T> queryMultiple(
        url: String,
        token: TypeToken<List<T>>,
        fields: Collection<String>? = null,
        categories: Collection<Category>? = null,
        query: String? = null
    ): List<T> {
        val filter = getFieldsFrom<T>(fields)
        val json = queryRaw(url, query, categories, filter)
        return mapper.fromJson<List<T>>(json, token.type)
    }

    private fun projects(vararg fields: String) =
        queryMultiple("admin/projects", object : TypeToken<List<Project>>() { }, fields.toList())

    fun projects(vararg fields: KProperty1<Project, *>) = projects(*fields.names())

    fun project(projectID: String, vararg fields: String): Project =
            querySingle("admin/projects/$projectID", fields.toList())

    private fun issues(project: Project, vararg fields: String) =
            queryMultiple("issues", object : TypeToken<List<Issue>>() { }, fields.toList(), query = "in: ${project.name}")

    fun issues(project: Project, vararg fields: KProperty1<Issue, *>) = issues(project, *fields.names())

//    private fun activities(issue: Issue, vararg fields: String, categories: Set<Category>): List<ActivityItem> {
//        val catString = categories.joinToString { it.name }
//        return queryMultiple("issues/${issue.id}/activitiesPage")
//    }
//
//    fun activities(issue: Issue, vararg fields: KProperty1<ActivityItemField, *>) = activities()
}