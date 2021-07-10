package net.sergeych.tools

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

class Iso8601Tools {
    companion object {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        val simpleDateParseFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")

        val instantFormat =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.from(ZoneOffset.UTC))

        init {
            simpleDateFormat.timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

/**
 * Format date to ISO8601 converting it to UTC TZ (e.g. it will always ends with Z)
 */
val Date.iso8601: String get() = Iso8601Tools.simpleDateFormat.format(this)

val ZonedDateTime.iso8601: String
    get() = Iso8601Tools.instantFormat.format(this.truncatedTo(ChronoUnit.SECONDS))

val Instant.iso8601: String get() = Iso8601Tools.instantFormat.format(this.truncatedTo(ChronoUnit.SECONDS))

/**
 * Parse ISO8601 date-time string, also ending with 'Z' to Date.
 * @throws java.text.ParseException on error
 */
fun String.iso8601ToDate(): Date {
    val str = if (this.endsWith('Z'))
        this.slice(0..(length - 2)) + "+0000"
    else
        this
    return Iso8601Tools.simpleDateParseFormat.parse(str)
}

fun String.iso8601ToZonedDateTime(): ZonedDateTime {
    val d = iso8601ToDate()
    return ZonedDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault())
}
