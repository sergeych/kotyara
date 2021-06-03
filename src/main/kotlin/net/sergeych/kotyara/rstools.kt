package net.sergeych.kotyara

import java.sql.ResultSet

inline fun <reified T> ResultSet.getValue(colIndex: Int=1) = when (val cls = T::class) {
    String::class -> getString(colIndex) as T
    Int::class -> getInt(colIndex) as T
    Long::class -> getLong(colIndex) as T
    ByteArray::class -> getBytes(colIndex) as T
    else -> throw IllegalArgumentException("unsupported type: ${cls.qualifiedName}")
}

inline fun <reified T> ResultSet.getValue(colName: String) = getValue<T>(findColumn(colName))


