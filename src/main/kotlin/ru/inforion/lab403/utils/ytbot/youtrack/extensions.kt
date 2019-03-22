package ru.inforion.lab403.utils.ytbot.youtrack

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
        println(it)
        when (it) {
            is KProperty<*> -> it.name
            is String -> it
            else -> throw IllegalArgumentException("Wrong class of $it")
        }
    }
}

fun KProperty<*>.with(vararg args: Any) = "$name(${fields(*args)})"