@file:Suppress("EXPERIMENTAL_API_USAGE")

package net.sergeych.kotyara

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

fun PreparedStatement.setValue(n: Int, x: Any?, sql: String = "<not set>") {
    when (x) {
        is String -> {
            if (metaData?.getColumnTypeName(n)?.startsWith("json") == true)
                setObject(n, x, java.sql.Types.OTHER)
            else
                setString(n, x)
        }
        is Enum<*> -> {
            if (metaData?.getColumnTypeName(n)?.startsWith("int") == true)
                setInt(n, x.ordinal)
            else
                setString(n, x.name)

        }
        is Int -> setInt(n, x)
        is Long -> setLong(n, x)
        is Float -> setFloat(n, x)
        is Double -> setDouble(n, x)
        is BigDecimal -> setBigDecimal(n, x)
        is Boolean -> setBoolean(n, x)
        is ByteArray -> setBytes(n, x)
        is Char -> setString(n, "$x")
        is JsonObject -> setObject(n, Json.encodeToString(x), java.sql.Types.OTHER)
        is LocalDateTime -> setTimestamp(n, Timestamp.valueOf(x))
        is LocalDate -> setTimestamp(n, Timestamp.valueOf(LocalDateTime.of(x, LocalTime.MIN)))
        is ZonedDateTime -> setTimestamp(n, Timestamp.valueOf(x.toLocalDateTime()))
        null -> setObject(n, null)
        else -> {
            throw IllegalArgumentException("unknown param[$n]:$x type: ${x::class.qualifiedName} from $sql")
        }
    }
}

