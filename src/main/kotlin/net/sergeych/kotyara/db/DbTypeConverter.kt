package net.sergeych.kotyara.db

import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.reflect.KClass

/**
 * The interface to add optional type conversion in DbContext operations
 */
interface DbTypeConverter {

    /**
     * Perform necessary conversion and store value in a prepared statement at a given column index.
     * @return true if the value is stored (converted) and false to use default kotyara conversion.
     */
    fun toDatabaseType(value: Any,statement: PreparedStatement,column: Int): Boolean

    /**
     * Convert when the data are retreived from database to some type. If the expected type [klass] requires conversion,
     * implementation should read data from a resultset [rs] at a given column, and conver it to T. Return null
     * otherwise.
     *
     * @param klass expected result's class
     * @param T expected result type (we can't use inline + reified here)
     * @param rs resultset from which ti extract value
     * @param column index in the resultset to use, e.g. `rs.getString(column)`
     * @return converted instance or null to fallback to default kotyara's conversion
     */
    fun <T: Any>fromDatabaseType(klass: KClass<T>, rs: ResultSet, column: Int): T?
}