@file:Suppress("EXPERIMENTAL_API_USAGE")

package net.sergeych.kotyara

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types.*
import java.time.*

@OptIn(InternalSerializationApi::class)
fun PreparedStatement.setValue(n: Int, x: Any?, sql: String = "<not set>") {
    when (x) {
        is String -> {
            if (parameterMetaData?.getParameterTypeName(n)?.startsWith("json") == true)
                setObject(n, x, OTHER)
            else
                setString(n, x)
        }
        is Enum<*> -> {
            // support both string (name) and in (ordinal) columns for enums:
            when (parameterMetaData?.getParameterType(n)) {
                INTEGER, SMALLINT, TINYINT -> setInt(n, x.ordinal)
                else -> setString(n, x.name)
            }
        }
        is Int -> setInt(n, x)
        is Long -> setLong(n, x)
        is Float -> setFloat(n, x)
        is Double -> setDouble(n, x)
        is BigDecimal -> setBigDecimal(n, x)
        is Boolean -> setBoolean(n, x)
        is ByteArray -> setBytes(n, x)
        is Char -> setString(n, "$x")
        is JsonObject -> setObject(n, Json.encodeToString(x), OTHER)
        is LocalDateTime -> setTimestamp(n, Timestamp.valueOf(x))
        is LocalDate -> setTimestamp(n, Timestamp.valueOf(LocalDateTime.of(x, LocalTime.MIN)))
        is ZonedDateTime -> setTimestamp(n, Timestamp.valueOf(x.toLocalDateTime()))
        is List<*> -> {
            val array =
                if (x.size == 0) throw IllegalArgumentException("empty array can't be a parameter of a statement")
                else {
                    var convertToString = false
                    val type = when (x[0]) {
                        is Int -> "int"
                        is Long -> "bigint"
                        is ByteArray -> "bytea"
                        is Boolean -> "bool"
                        is Instant, is ZonedDateTime -> "timestamp"
                        null -> "int"
                        else -> {
                            convertToString = true
                            "varchar"
                        }
                    }
                    println("col type: ${type}: ${parameterMetaData?.getParameterType(n)}")
                    connection.createArrayOf(type,
                        (if (convertToString) x.map { it.toString() } else x).toTypedArray())
                }
            setArray(n, array)
        }
        is Instant -> setTimestamp(n,
            Timestamp.valueOf(ZonedDateTime.ofInstant(x, ZoneId.systemDefault()).toLocalDateTime()))
        null -> setObject(n, null)
        else -> {
            // all this need to save parameter class: leave for later
//            println("param type: ${parameterMetaData?.getParameterTypeName(n)}")
//            if (parameterMetaData?.getParameterTypeName(n)?.startsWith("json") == true) {
//                val serializer: KSerializer<out Any> = x::class.serializer()
//                println("setting ${Json.encodeToString(serializer,x as Type.CapturedType<Any>())}")
//                setObject(n, Json.encodeToString(x), java.sql.Types.OTHER)
//            }
//            else
            throw IllegalArgumentException("unknown param[$n]:$x type: ${x::class.qualifiedName} from $sql")
        }
    }
}

