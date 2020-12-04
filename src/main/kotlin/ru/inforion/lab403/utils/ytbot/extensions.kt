@file:Suppress("EmptyRange")

package ru.inforion.lab403.utils.ytbot

import ru.inforion.lab403.common.extensions.*
import ru.inforion.lab403.utils.ytbot.youtrack.CategoryId
import java.net.InetAddress
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KProperty

fun Collection<CategoryId>.concat() = joinToString(",") { it.name }

fun String.normalizeURL() = URI(this).normalize().toString()

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
fun fields(vararg args: Any) = args.joinToString(",") {
    when (it) {
        is KProperty<*> -> it.name
        is String -> it
        else -> throw IllegalArgumentException("Wrong class of $it")
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
    if (isEmpty())
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

inline val Int.asInetAddress: InetAddress get() = InetAddress.getByAddress(byteArrayOf(
    this[31..24].asByte, this[23..16].asByte, this[15..8].asByte, this[7..0].asByte))

inline val Int.asIPAddress get() = "${this[31..24]}.${this[23..16]}.${this[15..8]}.${this[7..0]}"

inline val Long.asIPAddress get() = asInt.asIPAddress

fun String.escapeMarkdown() =
    replace("_", "\\_")
    .replace("*", "\\*")
    .replace("[", "\\[")  // escape ] not required

fun String.removeMarkdownCode() = replaceBetween("```", "```", "<multiline-code>")

fun String.crop(size: Int) = if (length <= size) this else "${stretch(size)}..."