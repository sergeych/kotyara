package net.sergeych.kotyara

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import net.sergeych.boss_serialization.BossDecoder
import net.sergeych.kotyara.db.DbJson
import net.sergeych.kotyara.db.DbTypeConverter
import net.sergeych.tools.camelToSnakeCase
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.jvm.jvmErasure

fun <T : Any> ResultSet.getValue(cls: KClass<T>, colName: String): T? {
    val value = when (cls) {
        String::class -> getString(colName)
        Int::class -> getInt(colName)
        Long::class -> getLong(colName)
        Date::class -> getTimestamp(colName)
        Boolean::class -> getBoolean(colName)
        Double::class -> getDouble(colName)
        BigDecimal::class -> getBigDecimal(colName)
        com.ionspin.kotlin.bignum.decimal.BigDecimal::class ->
            com.ionspin.kotlin.bignum.decimal.BigDecimal.parseString(getBigDecimal(colName).toString())
        Float::class -> getFloat(colName)
        ByteArray::class -> getBytes(colName)
        LocalDate::class -> getTimestamp(colName)?.toLocalDateTime()?.toLocalDate()
        JsonObject::class -> getString(colName)?.let { Json.parseToJsonElement(it).jsonObject }
        ZonedDateTime::class -> getTimestamp(colName)?.let { t ->
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(t.time), ZoneId.systemDefault())
        }
        List::class -> (getArray(colName).array as Array<*>).toList()
        Array::class -> getArray(colName).array as Array<*>
        Instant::class -> getTimestamp(colName)?.let { t -> Instant.ofEpochMilli(t.time) }
        kotlinx.datetime.Instant::class -> kotlinx.datetime.Instant.fromEpochMilliseconds(getTimestamp(colName).time)
        kotlinx.datetime.LocalDateTime::class -> kotlinx.datetime.Instant.fromEpochMilliseconds(getTimestamp(colName).time)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        kotlinx.datetime.LocalDate::class -> kotlinx.datetime.Instant.fromEpochMilliseconds(getTimestamp(colName).time)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        else -> {
            if (cls.java.isEnum) {
                val x = getObject(colName)
                if (x is Number) {
                    val ordinal = x.toInt()
                    cls.java.enumConstants.filterIsInstance(Enum::class.java).first { it.ordinal == ordinal }
                } else {
                    cls.java.enumConstants.filterIsInstance(Enum::class.java).first { it.name == x.toString() }
                }
            } else {
                try {
                    if( cls.annotations.any { it is DbJson })
                        Json.decodeFromString<Any?>(serializer(cls.createType()),getString(colName))
                    else
                        BossDecoder.decodeFrom<Any?>(cls.createType(),getBytes(colName))
                } catch(x: Exception) {
                    throw DbException("unknown param type $cls for column '$colName'", x)
                }
            }
        }
    }
    @Suppress("UNCHECKED_CAST")
    return if (wasNull()) null else (value as T)
}

fun <T : Any> ResultSet.getValue(cls: KClass<T>, colIndex: Int): T?  {
    return getValue(cls, metaData.getColumnName(colIndex))
}

inline fun <reified T : Any> ResultSet.getValue(colName: String): T? = getValue<T>(T::class, colName)
inline fun <reified T : Any> ResultSet.getValue(colIndex: Int): T? = getValue<T>(T::class, colIndex)

fun <T : Any> ResultSet.asOne(klass: KClass<T>, converter: DbTypeConverter?): T? {
    if (!next()) return null
    val constructor = klass.constructors.first()
    val args = constructor.parameters.map { param ->
        val columnName = param.name!!.camelToSnakeCase()
        val paramType = param.type.jvmErasure
        converter?.fromDatabaseType(paramType, this, findColumn(columnName))
            ?: getValue(
                paramType,
                columnName
            )
    }
    try {
        return constructor.call(*args.toTypedArray())
    }
    catch(x: Exception) {
        throw IllegalArgumentException(
            "failed to create instance of ${klass.simpleName}(${args.joinToString(",") { "$it: ${it?.javaClass?.simpleName}" }}",
            x
        )
    }
}

fun <T : Any> ResultSet.asMany(klass: KClass<T>,converter: DbTypeConverter?): List<T> {
    val result = arrayListOf<T>()
    @Suppress("ControlFlowWithEmptyBody")
    while (null != asOne(klass, converter)?.also { result.add(it) });
    return result
}
