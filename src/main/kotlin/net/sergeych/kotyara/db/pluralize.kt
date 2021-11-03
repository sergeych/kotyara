package net.sergeych.kotyara.db

import net.sergeych.tools.camelToSnakeCase

object EnglishPluralizer {

    private val exceptions = mutableMapOf(
        "index" to "indices",
        "vertex" to "vertices",
        "vortex" to "vortices",
        "appendix" to "appendices",
        "man" to "men",
        "woman" to "women",
        "foot" to "feet",
        "tooth" to "teeth",
        "goose" to "geese",
        "piano" to "pianos",
        "canto" to "cantos",
        "photo" to "photos",
        "zero" to "zeros",
        "roof" to "roofs",
        "proof" to "proofs",
        "belief" to "beliefs",
        "chef" to "chefs",
        "chief" to "chiefs",
        "phenomenon" to "phenomena",
        "criterion" to "criteria",
        "gas" to "gasses",
        "fez" to "fezzes",
        "fungus" to "fungi",
        "nucleus" to "nuclei",
        "cactus" to "cacti",
        "alumnus" to "alumni"
    )

    init {
        for (word in arrayOf(
            "sheep",
            "fish",
            "deer",
            "moose",
            "swine",
            "buffalo",
            "shrimp",
            "trout",
            "data",
            "people"
        )) {
            exceptions.put(word, word)
        }
    }

    val vowels = setOf('a', 'e', 'i', 'o', 'u')

    /**
     * Pluralize english words. Note: returned word is always in lower case, the argument case
     * is ignored.
     */
    fun transform(_word: String): String {
        val word = _word.lowercase()
        exceptions.get(word)?.let { return it }

        fun rule(ending: String, replace: String): String? {
            if (word.endsWith(ending))
                return word.dropLast(ending.length) + replace
            return null
        }

        if( word[word.length-1] == 'y') {
            if ( vowels.contains(word[word.length - 2]) )
                return word + 's'
            else
                return word.dropLast(1) + "ies"
        }

        rule("f", "ves")?.let { return it }
        rule("fe", "ves")?.let { return it }
        rule("o", "oes")?.let { return it }
        rule("um", "a")?.let { return it }
        rule("ss", "sses")?.let { return it }
        rule("sh", "shes")?.let { return it }
        rule("ch", "ches")?.let { return it }
        rule("x", "xes")?.let { return it }
        rule("z", "zes")?.let { return it }
        rule("s", "ses")?.let { return it }

        return word + 's'
    }

}

/**
 * Pluralize english word. Note: returned word is always in lower case, this word case
 * is ignored. requires singolar form of english word. If it is already plural except for
 * very usual like 'data', could return unexpected result.
 */
fun String.pluralize(): String = EnglishPluralizer.transform(this)

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
    if (begin.isNotEmpty()) begin += "_"
    return "$begin${end.pluralize()}"
}