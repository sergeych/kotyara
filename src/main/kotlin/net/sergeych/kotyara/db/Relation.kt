@file:Suppress("unused")

package net.sergeych.kotyara.db

import net.sergeych.kotyara.DbException
import net.sergeych.kotyara.asMany
import net.sergeych.kotyara.asOne
import net.sergeych.mp_logger.debug
import net.sergeych.tools.TaggedLogger
import java.sql.ResultSet
import kotlin.reflect.KClass

class Relation<T : Any>(
    val context: DbContext,
    val klass: KClass<T>,
    overrideTableName: String? = null,
    overrideFieldName: String? = null,
) :
    TaggedLogger("RELN") {

    private var _limit: Int? = null
    private var _offset: Int? = null


    val tableName: String
    val fieldName: String

    init {
        when {
            overrideFieldName == null && overrideTableName != null -> {
                //
                //warning { "table name is overridden table name but field name id not, could cause problem with include()" }
                tableName = overrideTableName
                fieldName = tableName
            }

            overrideFieldName != null && overrideTableName == null -> {
                tableName = overrideFieldName.toTableName()
                fieldName = overrideFieldName
            }

            overrideFieldName != null && overrideTableName != null -> {
                tableName = overrideTableName
                fieldName = overrideFieldName
            }

            else -> {
                val className = klass.simpleName
                    ?: throw DbException("relation needs either a non-anonymous class or overridden names")
                tableName = className.toTableName()
                fieldName = className.toFieldName()
            }
        }
    }

    private val selectClause = "SELECT * FROM $tableName"
    private val deleteClause = "DELETE FROM $tableName"

    private val joins = mutableListOf<String>()

    private val whereClauses = mutableListOf<String>()
    private val statementParams = mutableListOf<Any?>()
    private val order = mutableListOf<String>()


    /**
     * Add specified where clause. All such clauses are combined with "AND". Updates
     * self state and return self.
     * @return self
     */
    fun where(whereClause: String, vararg params: Any): Relation<T> {
        whereClauses.add(whereClause)
        statementParams.addAll(params)
        return this
    }

    /**
     * Add join expression, like `join("left join presents by persons.id=presents.person_id)`
     * don't forget to use full table name for the main table
     */
    fun addJoin(clause: String): Relation<T> {
        joins.add(clause)
        return this
    }

    /**
     * Simple inner join in __many to one scenario__ another table, using default naming convention or overridden field values.
     * note that it only works when this table field matches to foreign table key, like in this example:
     * ~~~
     * println(select<Present>().join<Person>().toString())
     * ~~~
     * will produce
     * ~~~
     * SELECT * FROM presents
     * inner join persons on persons.id=presents.person_id
     * ~~~
     * __If you need reverse case, e.g. include all presents for a person, use__ [include].
     *
     * Arguments allows to override default naming. It is it not enough, join tables using [addJoin]
     * which gives maximum flexibility.
     *
     * @param fieldName our field name that points to joined table name, if null the U class name will
     *        be used to generate proper name, e.g. converted to snake case and `"_id"` added, as in
     *        the example above
     * @param foreignKey default `"id"` could be overriden here
     */
    inline fun <reified U> join(
        fieldName: String? = null,
        foreignKey: String = "id",
    ): Relation<T> {
        val clsName = U::class.simpleName!!
        val otherTableName = clsName.toTableName()
        val ourField = fieldName ?: "${clsName.toFieldName()}_id"
        return addJoin(
            "inner join $otherTableName on ${otherTableName}.${foreignKey}=$tableName.$ourField"
        )
    }

    /**
     * Join to include all `U` instances, like [join] nut for one-to-many cases, for example:
     * ~~~
     * var z = select<Person>().include<Present>()
     *            .where("presents.name = ?", "doll")
     * println(z.toString())
     * ~~~
     * produces
     * ~~~
     * SELECT * FROM persons
     * inner join presents on presents.person_id=persons.id
     * where (presents.name = ?)
     * ~~~
     */
    inline fun <reified U> include(
        theirFieldName: String? = null,
        localKeyName: String = "id",
    ): Relation<T> {
        val clsName = U::class.simpleName!!
        val otherTableName = clsName.toTableName()
        val name = theirFieldName ?: "${fieldName}_id"
        return addJoin(
            "inner join $otherTableName on ${otherTableName}.${name}=$tableName.$localKeyName"
        )

    }

    /**
     * Add specified where clauses of type {pair.first} = {pair.second} all compbined by AND.
     * Properly process null values in pair.second. Updates self state and return self.
     * @return self
     */
    fun where(vararg pairs: Pair<String, Any?>): Relation<T> {
        for ((name, value) in pairs) {
            if (value == null)
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
        return "${buildSql()}\n: $statementParams"
    }

    private var useForUpdate = false

    fun forUpdate(): Relation<T> {
        useForUpdate = true
        return this
    }

    fun buildSql(overrideLimit: Int? = null,doDelete: Boolean = false): String {
        val sql = StringBuilder(if( doDelete ) deleteClause else selectClause)
        for (j in joins) sql.append("\n$j")
        // todo: possible joins
        if (whereClauses.isNotEmpty()) {
            sql.append("\nwhere")
            sql.append(whereClauses.joinToString(" and") { " ($it)" })
        }
        if (order.isNotEmpty() && !doDelete) {
            sql.append("\norder by ")
            sql.append(order.joinToString(","))
        }
        if (overrideLimit != null || _limit != null || _offset != null) sql.append("\n")
        if (overrideLimit != null)
            sql.append(" limit $overrideLimit")
        else
            _limit?.let { sql.append(" limit $it") }

        _offset?.let { sql.append(" offset $it") }

        if (useForUpdate && !doDelete) sql.append(" for update")

        debug { "Build sql: $this" }
        return sql.toString()
    }


    fun <T> withResultSet(overrideLimit: Int? = null, block: (ResultSet) -> T): T =
        context.withResultSet(true, buildSql(overrideLimit), *statementParams.toTypedArray(), block = block)

    val all: List<T>
        get() =
            withResultSet { it.asMany(klass, context.converter) }

    fun deleteAll(): Int =
        context.withUpdateCount(buildSql(doDelete = true), *statementParams.toTypedArray())

    val first: T?
        get() =
            withResultSet(1) { it.asOne(klass, context.converter) }

}