package net.sergeych.kotyara

import java.sql.ResultSet
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmErasure


fun <T: Any> ResultSet.getValue(cls: KClass<T>, colName: String): T? {
    val value = when(cls) {
        String::class -> getString(colName)
        Int::class -> getInt(colName)
        else ->
            throw DbException("unknown param type $cls for column '$colName'")
    }
    return if (wasNull()) null else (value as T)
}

fun <T: Any> ResultSet.getValue(cls: KClass<T>, colIndex: Int): T? =
    getValue(cls, metaData.getColumnName(1))


inline fun <reified T: Any> ResultSet.getValue(colName: String): T? = getValue<T>(T::class,colName)
inline fun <reified T: Any> ResultSet.getValue(colIndex: Int): T? = getValue<T>(T::class,colIndex)

fun <T: Any> ResultSet.asOne(klass: KClass<T>): T? {
    if( isBeforeFirst ) next()
    if( isAfterLast ) return null
    val constructor = klass.constructors.first()
    val args = constructor.parameters.map { param -> getValue(
        param.type.jvmErasure,
        param.name!!)
    }
    return constructor.call(*args.toTypedArray())
}

fun <T: Any> ResultSet.asMany(klass: KClass<T>): List<T> {
    val result = arrayListOf<T>()
    while(next()) result.add(asOne(klass)!!)
    return result
}
