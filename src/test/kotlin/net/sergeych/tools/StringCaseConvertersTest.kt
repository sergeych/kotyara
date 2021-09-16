package net.sergeych.tools

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.time.ZonedDateTime

internal class StringCaseConvertersTest {

    @Test
    fun snakeToCamelCase() {
        assertEquals("camelCaseString", "camel_case_string".snakeToLowerCamelCase());
        assertEquals("XCode", "x_code".snakeToUpperCamelCase());
    }
}