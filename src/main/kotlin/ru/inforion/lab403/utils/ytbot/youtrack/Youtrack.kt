package ru.inforion.lab403.utils.ytbot.youtrack

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.inforion.lab403.common.logging.logger
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Issue
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project
import java.lang.RuntimeException
import java.lang.reflect.ParameterizedType
import java.util.logging.Level
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.javaType

class Youtrack(val baseUrl: String, val permToken: String) {
    val log = logger(Level.FINE)

    inline fun <reified T> getFieldsFrom(vararg fields: String): List<String> {
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

    fun String.httpGet(vararg parameters: Pair<String, String>) = this.httpGet(parameters.toList())

    fun queryRaw(url: String, query: String?, fields: List<String>): String {
        var queryError: FuelError? = null
        var queryBytes: ByteArray? = null

        val fieldsString = fields.joinToString(",")

        val parameters = mutableListOf("fields" to fieldsString)

        if (query != null)
            parameters.add("query" to query)

        log.fine { "Request fields: $fieldsString" }

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

    inline fun <reified T> Gson.fromJson(json: String) = fromJson<T>(json, object: TypeToken<T>() {}.type)

    inline fun <reified T> querySingle(url: String, vararg fields: String, query: String? = null): T {
        val filter = getFieldsFrom<T>(*fields)
        val json = queryRaw(url, query, filter)
        return Gson().fromJson<T>(json)
    }

    inline fun <reified T> queryMultiple(url: String, token: TypeToken<List<T>>, vararg fields: String, query: String? = null): List<T> {
        val filter = getFieldsFrom<T>(*fields)
        val json = queryRaw(url, query, filter)
        return mapper.fromJson<List<T>>(json, token.type)
    }

    fun projects(vararg fields: String): List<Project> =
        queryMultiple("admin/projects", object : TypeToken<List<Project>>() { }, *fields)

    fun project(projectID: String, vararg fields: String): Project =
            querySingle("admin/projects/$projectID", *fields)

    fun issues(project: Project, vararg fields: String): List<Issue> =
            queryMultiple("issues", object : TypeToken<List<Issue>>() { }, *fields, query = "in: ${project.name}")
}