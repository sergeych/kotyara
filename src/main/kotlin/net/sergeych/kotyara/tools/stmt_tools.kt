@file:Suppress("EXPERIMENTAL_API_USAGE")

package net.sergeych.kotyara

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.*

fun PreparedStatement.setValue(n: Int, x: Any?, sql: String = "<not set>") {
    when (x) {
        is String -> {
            if (parameterMetaData?.getParameterTypeName(n)?.startsWith("json") == true)
                setObject(n, x, java.sql.Types.OTHER)
            else
                setString(n, x)
        }
        is Enum<*> -> {
            // we have no idea what is the expected type of nth argument (to which column it will be bound)
            // so the problem: for ordinal-encoded enums we can't decode it proper;u
            if (parameterMetaData?.getParameterType(n) == java.sql.Types.INTEGER)
                setObject(n, x, java.sql.Types.OTHER)
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
        is Instant -> setTimestamp(n, Timestamp.valueOf(ZonedDateTime.ofInstant(x, ZoneId.systemDefault()).toLocalDateTime()))
        null -> setObject(n, null)
        else -> {
            throw IllegalArgumentException("unknown param[$n]:$x type: ${x::class.qualifiedName} from $sql")
        }
    }
}

