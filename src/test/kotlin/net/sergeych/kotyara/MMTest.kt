package net.sergeych.kotyara

import net.sergeych.tools.DefaultLogger
import net.sergeych.tools.Loggable
import net.sergeych.tools.TaggedLogger
import net.sergeych.tools.iso8601
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import java.time.Instant

internal class MMTest : Loggable  by TaggedLogger("TS0"){

    @Test
    fun bb() {
        DefaultLogger.connectStdout()
        info("message1")
        debug("message2")
        val x = IllegalAccessError("test error for access")
        error("message3", x)
        assertEquals("BB", MM().Bb())
        Thread.sleep(400)
    }

    @Test fun instantToIso() {
        println((Instant.now().iso8601))
        assertEquals(1,1)
    }
}