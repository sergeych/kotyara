package net.sergeych.tools

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.time.ZonedDateTime

internal class Iso8601KtTest {

    @Test
    fun iso8601ToZonedDateTime() {
//        val x = ZonedDateTime.now().iso8601.iso8601ToZonedDateTime()
        println(ZonedDateTime.now().iso8601)
        val x = "2017-07-24T12:01:00Z".iso8601ToZonedDateTime()
        val y = "2021-07-15T15:30:31Z".iso8601ToZonedDateTime()
        println(x)
//        assertEquals(1, x)
    }
}