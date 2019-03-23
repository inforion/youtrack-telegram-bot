package ru.inforion.lab403.utils.ytbot

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.ArrayList
import kotlin.reflect.KProperty

val jsonConfigLoader = jacksonObjectMapper().apply {
    configure(JsonParser.Feature.ALLOW_COMMENTS, true)
    configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
}

inline fun <reified T: Enum<T>>enumValuesCollection(collection: MutableCollection<T> = mutableListOf()) =
    enumValues<T>().toCollection(collection)

/**
 * fields(
 *      Project::createdBy.with(
 *          User::id,
 *          User::banned,
 *          User::tags.with(
 *              IssueTag::id
 *          )
 *      ),
 *      Project::id,
 *      Project::name
 * )
 */
fun fields(vararg args: Any): String {
    return args.joinToString(",") {
        when (it) {
            is KProperty<*> -> it.name
            is String -> it
            else -> throw IllegalArgumentException("Wrong class of $it")
        }
    }
}

fun KProperty<*>.with(vararg args: Any) = "$name(${fields(*args)})"

/**
 * Finding non-breaking sequences with [selector] in collection [this]
 *
 * input:
 *  [ A1, A2, A3, B1, B2, B3, A4, A5 ]
 * selector:
 *  { it.first() }
 * output:
 *  [ A to (A1, A2, A3)
 *    B to (B1, B2, B3)
 *    A to (B4, B5) ]
 *
 * @param selector lambda returning grouping "key" value
 * @return grouped list
 */
fun <S, T> Collection<T>.groupSeriesBy(selector: (T) -> S): List<Pair<S, Collection<T>>> {
    if (this.isEmpty())
        return emptyList()

    val result = ArrayList<Pair<S, Collection<T>>>()

    var list = toList()
    while (list.isNotEmpty()) {
        val item = selector(list.first())
        val taken = list.takeWhile { selector(it) == item }
        list = list.drop(taken.size)
        result.add(item to taken)
    }
    return result
}

/**
 * Removes all specified characters [chars] from string [this]
 *
 * @param chars characters to be removed
 * @return string without characters [chars]
 */
fun String.removeChars(vararg chars: Char, ignoreCase: Boolean = false) = chars.fold(this) { result, char ->
    result.replace("$char", "", ignoreCase)
}

/**
 * Removes all specified characters in string [chars] from string [this]
 *
 * @param chars characters string that to be removed from string [this]
 * @return string without characters [chars]
 */
fun String.removeChars(chars: String, ignoreCase: Boolean = false)
        = removeChars(*chars.toCharArray(), ignoreCase = ignoreCase)