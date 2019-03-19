package ru.inforion.lab403.utils.ytbot.youtrack

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.inforion.lab403.common.extensions.convertToString
import ru.inforion.lab403.utils.ytbot.youtrack.scheme.Project

class Youtrack(val baseUrl: String, val permToken: String) {
    fun issues() {
        val request = Fuel
            .get("$baseUrl/api/issues")
            .authentication()
            .bearer(permToken)
//            .response()
//            .responseObject(Issue.Deserializer()) { req, res, result ->
//                val (issue, err) = result
//                println(issue!!.id)
//            }
            .response { request, response, result ->
                println(request)
                println(response)
                val (bytes, error) = result
                println(error)
                if (bytes != null) {
                    println("[response bytes] ${String(bytes)}")
                    val x = Gson().fromJson(bytes.toString(), List::class.java)
//                    val issue = Issue.Deserializer().deserialize(bytes)
                    println(x)
                }
            }
        request.join()
    }

    inline fun <reified T> Gson.fromJson(json: String) = this.fromJson<T>(json, object: TypeToken<T>() {}.type)

    fun projects() {
        val request = Fuel
            .get("$baseUrl/api/admin/projects?fields=createdBy,shortName,name,id")
            .authentication()
            .bearer(permToken)
            .response { request, response, result ->
                println(request)
                println(response)
                val (bytes, error) = result
                if (bytes != null) {
                    println("[response bytes] ${String(bytes)}")
                    val projects = Gson().fromJson<List<Project>>(bytes.convertToString())
//                    val issue = Issue.Deserializer().deserialize(bytes)
                    projects.forEach {
                        println(it.createdBy)
                    }
                }
            }
        request.join()
    }
}