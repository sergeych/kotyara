package net.sergeych.kotyara

import net.sergeych.tools.camelToSnakeCase
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmErasure

private val UTC = ZoneId.of("UTC")

fun <T : Any> ResultSet.getValue(cls: KClass<T>, colName: String): T? {
    val value = when (cls) {
        String::class -> getString(colName)
        Int::class -> getInt(colName)
        Long::class -> getInt(colName)
        Date::class -> getTimestamp(colName)
        LocalDate::class -> getTimestamp(colName)?.let { it.toLocalDateTime().toLocalDate() }
        ZonedDateTime::class -> getTimestamp(colName)?.let { t ->
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(t.time), ZoneId.systemDefault())
        }
        Boolean::class -> getBoolean(colName)
        else ->
            throw DbException("unknown param type $cls for column '$colName'")
    }
    return if (wasNull()) null else (value as T)
}

fun <T : Any> ResultSet.getValue(cls: KClass<T>, colIndex: Int): T? =
    getValue(cls, metaData.getColumnName(1))


inline fun <reified T : Any> ResultSet.getValue(colName: String): T? = getValue<T>(T::class, colName)
inline fun <reified T : Any> ResultSet.getValue(colIndex: Int): T? = getValue<T>(T::class, colIndex)

fun <T : Any> ResultSet.asOne(klass: KClass<T>): T? {
    if (isBeforeFirst) next()
    if (isAfterLast) return null
    val constructor = klass.constructors.first()
    val args = constructor.parameters.map { param ->
        getValue(
            param.type.jvmErasure,
            param.name!!.camelToSnakeCase()
        )
    }
    return constructor.call(*args.toTypedArray())
}

fun <T : Any> ResultSet.asMany(klass: KClass<T>): List<T> {
    val result = arrayListOf<T>()
    while (next()) result.add(asOne(klass)!!)
    return result
}
