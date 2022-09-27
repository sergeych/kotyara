@file:Suppress("unused")

package net.sergeych.kotyara.db

import net.sergeych.kotyara.DbException
import net.sergeych.kotyara.asMany
import net.sergeych.kotyara.asOne
import net.sergeych.mp_logger.debug
import net.sergeych.tools.TaggedLogger
import net.sergeych.tools.camelToSnakeCase
import java.sql.ResultSet
import kotlin.reflect.KClass

class Relation<T : Any>(val context: DbContext, val klass: KClass<T>) : TaggedLogger("RELN") {

    private var _limit: Int? = null
    private var _offset: Int? = null

    private val selectClause = "SELECT * FROM ${getTableName()}"

    private fun getTableName(): String {
        // todo: scan for annotation in the klass that overrides table name
        return klass.simpleName?.camelToSnakeCase()?.toTableName()
            ?: throw DbException("relation needs a non-anonymous class")
    }

    private val whereClauses = mutableListOf<String>()
    private val statementParams = mutableListOf<Any?>()
    private val order = mutableListOf<String>()

    /**
     * Add specified where clause. All such clauses are combined with "AND". Updates
     * self state and return self.
     * @return self
     */
    fun where(whereClause: String,vararg params: Any): Relation<T> {
        whereClauses.add(whereClause)
        statementParams.addAll(params)
        return this
    }

    /**
     * Add specified where clauses of type {pair.first} = {pair.second} all compbined by AND.
     * Properly process null values in pair.second. Updates self state and return self.
     * @return self
     */
    fun where(vararg pairs: Pair<String,Any?>): Relation<T> {
        for( (name, value) in pairs) {
            if( value == null )
                whereClauses.add("$name is null")
            else {
                whereClauses.add("$name = ?")
                statementParams.add(value)
            }
        }
        return this
    }

    fun order(orderBy: String): Relation<T> {
        order.add(orderBy)
        return this
    }

    fun limit(value: Int): Relation<T> {
        dropCache(); _limit = value
        return this
    }

    fun offset(value: Int): Relation<T> {
        dropCache(); _offset = value
        return this
    }

    private fun dropCache() {}

    override fun toString(): String {
        return "Rel($selectClause)"
    }

    private var useForUpdate = false

    fun forUpdate(): Relation<T> {
        useForUpdate = true
        return this
    }

    fun buildSql(overrideLimit: Int? = null): String {
        val sql = StringBuilder(selectClause)
        // todo: possible joins
        if( whereClauses.isNotEmpty()) {
            sql.append(" where")
            sql.append(whereClauses.joinToString(" and") { " ($it)" })
        }
        if( order.isNotEmpty()) {
            sql.append(" order by ")
            sql.append(order.joinToString(","))
        }
        if( overrideLimit != null )
            sql.append(" limit 1")
        else
            _limit?.let { sql.append(" limit $it") }

        _offset?.let { sql.append(" offset $it") }

        if( useForUpdate ) sql.append(" for update")

        debug { "sql built: $sql" }
        return sql.toString()
    }


    fun <T> withResultSet(overrideLimit: Int? = null, block: (ResultSet) -> T): T =
        context.withResultSet(true, buildSql(overrideLimit), *statementParams.toTypedArray(), block = block)

    val all: List<T>
        get() =
            withResultSet { it.asMany(klass,context.converter) }

    val first: T?
        get() =
            withResultSet(1) { it.asOne(klass,context.converter) }

}