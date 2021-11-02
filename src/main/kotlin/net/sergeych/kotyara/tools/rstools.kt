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
        Long::class -> getLong(colName)
        Date::class -> getTimestamp(colName)
        Boolean::class -> getBoolean(colName)
        Double::class -> getDouble(colName)
        Float::class -> getFloat(colName)
        ByteArray::class -> getBytes(colName)
        LocalDate::class -> getTimestamp(colName)?.toLocalDateTime()?.toLocalDate()
        ZonedDateTime::class -> getTimestamp(colName)?.let { t ->
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(t.time), ZoneId.systemDefault())
        }
        else ->
            throw DbException("unknown param type $cls for column '$colName'")
    }
    @Suppress("UNCHECKED_CAST")
    return if (wasNull()) null else (value as T)
}

fun <T : Any> ResultSet.getValue(cls: KClass<T>, colIndex: Int): T? =
    getValue(cls, metaData.getColumnName(colIndex))


inline fun <reified T : Any> ResultSet.getValue(colName: String): T? = getValue<T>(T::class, colName)
inline fun <reified T : Any> ResultSet.getValue(colIndex: Int): T? = getValue<T>(T::class, colIndex)

fun <T : Any> ResultSet.asOne(klass: KClass<T>): T? {
    if( !next() ) return null
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
    @Suppress("ControlFlowWithEmptyBody")
    while (null != asOne(klass)?.also { result.add(it) });
    return result
}
