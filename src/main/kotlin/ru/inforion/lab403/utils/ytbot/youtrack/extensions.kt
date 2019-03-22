package ru.inforion.lab403.utils.ytbot.youtrack

import java.util.ArrayList
import kotlin.reflect.KProperty

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
 * A1, A2, A3, B1, B2, B3, A4, A5
 * A to (1, 2, 3)
 * B to (1, 2, 3)
 * A to (4, 5)
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