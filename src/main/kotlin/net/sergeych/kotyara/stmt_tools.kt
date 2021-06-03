package net.sergeych.kotyara

import java.lang.IllegalArgumentException
import java.sql.PreparedStatement

fun PreparedStatement.setValue(n: Int, x: Any?, sql: String = "<not set>") {
    when (x) {
        is String -> setString(n, x)
        is Int -> setInt(n, x)
        is Long -> setLong(n, x)
        is ByteArray -> setBytes(n, x)
        null -> setObject(n, null)
        else -> {
            throw IllegalArgumentException("unknown param[$n]:$x type: ${x::class.qualifiedName} from $sql")
        }
    }
}

