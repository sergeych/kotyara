package net.sergeych.kotyara.db

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types.OTHER
import kotlin.reflect.KClass


@Suppress("UNCHECKED_CAST")
class TypeRegistry(private val converter: DbTypeConverter? = null) : DbTypeConverter {

    class FromDbContext(rs: ResultSet, val column: Int): ResultSet by rs

    class ToDbContext(stmt: PreparedStatement, val column: Int) : PreparedStatement by stmt
    data class Converter<T : Any>(val fromDb: FromDbContext.() -> T, val toDb: ToDbContext.(T) -> Unit)

    private val registry = mutableMapOf<KClass<*>, Converter<*>>()
    private val access = Object()

    fun <T : Any> register(cls: KClass<*>, fromDb: FromDbContext.() -> T, toDb: ToDbContext.(T) -> Unit) {
        registry[cls] = Converter(fromDb, toDb)
    }

    inline fun <reified T : Any> register(
        noinline fromDb: FromDbContext.() -> T,
        noinline toDb: ToDbContext.(T) -> Unit
    ) = register(T::class, fromDb, toDb)

    private fun <T : Any>getConverter(cls: KClass<*>): Converter<T>? = synchronized(access) { registry[cls] as? Converter<T> }
    override fun toDatabaseType(value: Any, statement: PreparedStatement, column: Int): Boolean =
        getConverter<Any>(value::class)?.let {
            it.toDb.invoke(ToDbContext(statement, column), value)
            true
        } ?: converter?.toDatabaseType(value, statement, column) ?: false

    override fun <T : Any> fromDatabaseType(klass: KClass<T>, rs: ResultSet, column: Int): T? =
        getConverter<T>(klass)?.fromDb?.invoke(FromDbContext(rs, column))
            ?: converter?.fromDatabaseType(klass, rs, column)

    inline fun <reified T : Any> asJson() {
        register({
            var src = getString(column)
            if( src[0] == '"') {
                // The H2-specific featurebug: it returns json-encoded string for JSON type
                // when reading from DB, so we decode it twice:
                src = Json.decodeFromString<String>(src)
            }
            Json.decodeFromString<T>(src)
        }, {
            setObject(column, Json.encodeToString(it),OTHER)
        })
    }

}