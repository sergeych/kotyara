package net.sergeych.kotyara.db

import net.sergeych.tools.camelToSnakeCase

private val exceptions = mapOf(
    "index" to "indices",
    "vertex" to "vertices",
    "man" to "men",
    "woman" to "wemen",
    "data" to "data",
    "people" to "people"
)

/**
 * Calculate pluralized snake case form of the "class name", e.g. RecentOrder -> recent_orders which is usually
 * used as table names in RDBMS where table names are almost always case-insensitive that requires using
 * underscore to effectively separate name parts.
 */
fun String.toTableName(): String {
    val s = this.camelToSnakeCase()
    val parts = s.split('_')
    val end = parts.last()
    var begin = parts.dropLast(1).joinToString("_")
    if( begin.isNotEmpty()) begin += "_"
    exceptions[end]?.let { return begin + it }
    return "$begin${end}s"
}