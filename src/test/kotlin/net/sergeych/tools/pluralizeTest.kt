package net.sergeych.tools

import net.sergeych.kotyara.db.pluralize
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.time.ZonedDateTime

internal class pluralizeTest {

    @Test
    fun englishWords() {
        assertEquals("buses", "bus".pluralize())
        assertEquals("knives", "knife".pluralize())
        assertEquals("wolves", "wolf".pluralize())
        assertEquals("wives", "wife".pluralize())
        assertEquals("cities", "city".pluralize())
        assertEquals("puppies", "puppy".pluralize())
        assertEquals("data", "data".pluralize())
    }
}