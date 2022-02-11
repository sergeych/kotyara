package net.sergeych.kotyara.db

import java.sql.ResultSet
import kotlin.reflect.KClass

/**
 * The interface to add optional type conversion in DbContext operations
 */
interface DbTypeConverter {
    /**
     * Convert before using it in a statement (e.g. in any positino in sql clause). Here you can convert
     * some unsupported type to one that DB supports.
     */
    fun toDatabaseType(t: Any): Any

    /**
     * Convert when the data are retreived from database, before deserializing it into
     * result structure (if any). Here you can convert some DB type to the type expected by the
     * caller.
     *
     * This one is more complex as implementor need to read from the ResultSet column and convert it,
     * or return null to use kotyara's default conversion.
     *
     * @param klass expected result class
     * @param T expected result type (we can't use inline + reified here)
     * @param rs resultset from which ti extract value
     * @param column index in the resultset to use, e.g. `rs.getString(column)`
     */
    fun <T: Any>fromDatabaseType(klass: KClass<T>, rs: ResultSet, column: Int): T?
}