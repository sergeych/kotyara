/*
Snake_case (aka kebab_case) to CamelCase and back conversion String class extensions.

Initial idea by TER (https://stackoverflow.com/users/4215893/ter) with small fixes to
avoid deprexations, etc. Thanks TER!
 */
package net.sergeych.tools

private val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
private val snakeRegex = "_[a-zA-Z]".toRegex()

/**
 * Convert camelCase string to a snake-case (aka kebab-case) string, e.g. `"camelCase"` to `"camel_case"`
 */
fun String.camelToSnakeCase(): String {
    return camelRegex.replace(this) {
        "_${it.value}"
    }.lowercase()
}

/**
 * Convert snake-case (aka kebab-case) to camel-case string staring with lowercase character,
 * e.g. Convert `"snake_case"` to `"snakeCase"`
 */
fun String.snakeToLowerCamelCase(): String {
    return snakeRegex.replace(this) {
        it.value.replace("_","")
            .uppercase()
    }
}

/**
 * Convert snake-case (aka kebab-case) to camel-case string staring with uppercase character,
 * e.g. Convert `"snake_case"` to `"SnakeCase"`
 */
fun String.snakeToUpperCamelCase(): String {
    return this.snakeToLowerCamelCase().replaceFirstChar { it.uppercase() }
}
