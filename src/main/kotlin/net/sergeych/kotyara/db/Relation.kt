package net.sergeych.kotyara.db

import net.sergeych.kotyara.DbException
import net.sergeych.kotyara.asMany
import net.sergeych.kotyara.asOne
import net.sergeych.tools.Loggable
import net.sergeych.tools.TaggedLogger
import net.sergeych.tools.camelToSnakeCase
import java.sql.ResultSet
import kotlin.reflect.KClass

class Relation<T : Any>(val context: DbContext, val klass: KClass<T>) : Loggable by TaggedLogger("RELN") {

    private var _limit: Int? = null
    private var _offset: Int? = null

    private val selectClause = "SELECT * FROM ${getTableName()}"

    private fun getTableName(): String {
        // todo: scan for annotation in the klass that overrides table name
        return klass.simpleName?.camelToSnakeCase()?.toTableName()
            ?: throw DbException("relation needs a non-anonymous class")
    }

    private val whereClauses = mutableListOf<String>()
    private val statementParams = mutableListOf<Any>()

    fun where(whereClause: String,vararg params: Any): Relation<T> {
        whereClauses.add(whereClause)
        statementParams.addAll(params)
        return this
    }

    fun limit(value: Int) {
        dropCache(); _limit = value
    }

    fun offset(value: Int) {
        dropCache(); _offset = value
    }


    private fun dropCache() {}

    override fun toString(): String {
        return "Rel($selectClause)"
    }

    fun buildSql(overrideLimit: Int? = null): String {
        val sql = StringBuilder(selectClause)
        // todo: possible joins
        if( whereClauses.isNotEmpty()) {
            sql.append(" where")
            sql.append(whereClauses.joinToString(" and") { " ($it)" })
        }
        if( overrideLimit != null )
            sql.append(" limit 1")
        else
            _limit?.let { sql.append(" limit $it") }

        _offset?.let { sql.append(" offset $it") }
        debug("sql built: $sql")
        return sql.toString()
    }


    fun <T> withResultSet(overrideLimit: Int? = null, block: (ResultSet) -> T): T =
        context.withResultSet(true, buildSql(overrideLimit), *statementParams.toTypedArray(), f = block)

    val all: List<T>
        get() =
            withResultSet { it.asMany(klass) }

    val first: T?
        get() =
            withResultSet(1) { it.asOne(klass) }

}