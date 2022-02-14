@file:Suppress("unused")

package net.sergeych.kotyara.db

import net.sergeych.kotyara.*
import net.sergeych.kotyara.tools.ConcurrentBag
import net.sergeych.tools.Loggable
import net.sergeych.tools.TaggedLogger
import java.lang.IllegalStateException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

class DbContext(
    private val _readConnection: Connection,
    private val writeConnection: Connection = _readConnection,
    val converter: DbTypeConverter?
) : Loggable by TaggedLogger("DBC") {

    private val readConnection: Connection
        get() = if (inTransaction) writeConnection else _readConnection

    override fun toString(): String {
        return "DBC($_readConnection,$writeConnection)"
    }

    inline fun <reified T : Any> queryOne(sql: String, vararg params: Any?): T? {
        return withResultSet(true, sql, *params) { rs ->
            if (rs.next())
                convertFromDb(rs, 1) as T?
            else
                null
        }
    }

    inline fun <reified T : Any> updateQueryOne(sql: String, vararg params: Any?): T? {
        return withResultSet(false, sql, *params) { rs ->
            if (rs.next())
                convertFromDb(rs,1)
            else
                null
        }
    }

    /**
     * Perform select type statement (no DML allowed) returning a row. If you need to execute DML this way,
     * use [updateQueryRow] instead.
     *
     * @return row deserialized to the `T` type using its primary constructor or null if returned ResultSet is empty.
     */
    inline fun <reified T : Any> queryRow(sql: String, vararg params: Any?): T? {
        return withResultSet(true, sql, *params) { it.asOne(T::class,converter) }
    }

    /**
     * Perform update statement returning a row, for example postgres' gorgeous `create ... returning *` statements.
     * Works just like [queryRow] but uses write connection and proper JDBC method to allow update.
     *
     * @return row deserialized to the `T` type using its primary constructor or null if returned resultset is empty.
     */
    inline fun <reified T : Any> updateQueryRow(sql: String, vararg params: Any?): T? {
        return withResultSet(false, sql, *params) { it.asOne(T::class, converter) }
    }

    inline fun <reified T : Any> query(sql: String, vararg params: Any): List<T> =
        withResultSet(true, sql, *params) { it.asMany(T::class, converter) }

    /**
     * Perform a query, iterate the resultset passing each row to the transformer and return collected result.
     */
    fun <T : Any> queryWith(sql: String, vararg params: Any, transformer: (ResultSet) -> T): List<T> =
        withResultSet(true, sql, *params) { rs ->
            val result = mutableListOf<T>()
            while (rs.next()) result.add(transformer(rs))
            result
        }

    /**
     * Perform sql select (not usable for update statements!) returning array of deserialized first column of the
     * ResultSet. Additional columns are ignored.
     */
    inline fun <reified T : Any> queryColumn(sql: String, vararg params: Any): List<T> =
        withResultSet(true, sql, *params) { rs ->
            val result = mutableListOf<T?>()
            while (rs.next()) result.add(convertFromDb(rs, 1))
            result.filterNotNull()
        }

    /**
     * Performs update query and return number of affected rows. Same as [update]
     */
    fun sql(sql: String, vararg params: Any?): Int =
        withWriteStatement(sql, *params) { it.executeUpdate() }

    /**
     * Performs update query and return number of affected rows. Same as [sql]
     */
    fun update(sql: String, vararg params: Any?): Int = sql(sql, *params)


    /**
     * Perfprms JDBC execute on statement, and returns its result: true if the first result is a ResultSet object;
     * false if the first result is an update count or there is no result. Use it when you need to execute
     * a statement that returns nothing, like `SELECT pg_some_fun()` which has not return value, as [query], [sql]
     * and others may fail having no return value. See [JDBC docs](https://docs.oracle.com/javase/7/docs/api/java/sql/PreparedStatement.html#execute())
     * for details on that difference.
     */
    fun execute(sql: String, vararg params: Any?): Boolean =
        withWriteStatement(sql, *params) { it.execute() }

    /**
     * Calls the block with a ResultSet by either executing a query (`isRead==true`) or update otherwise selecting
     * proper connection for it. Uses statement caching. Could be used to execute DDL/DML statements in which case
     * resultset contains number or affected strings
     * calling the block. This allow immediate use of the resultset. To loop through it it is recommended
     * to use `do {} while(rs.next())` construction or anything else with advancing the resultset at the end.
     *
     * @return whatever block returns or null if the result set is empty.
     */
    fun <T> withResultSet(isRead: Boolean = true, sql: String, vararg params: Any?, block: (ResultSet) -> T): T {
        return withStatement2(isRead, sql, *params) {
            val rs = if (isRead) it.executeQuery() else {
                it.execute()
                it.resultSet
            }
            try {
                block(rs)
            } catch (x: Exception) {
                error("withResultSet crashed", x)
                throw x
            } finally {
                rs.close()
            }
        }
    }

    /**
     * Calls `f` with a ResultSet prepositioned to the first row (ready to use) or throw [NotFoundException].
     * @return whatever block returns
     * @throws NotFoundException if the resultset is empty
     */
    internal fun <T> withResultSetOrThrow(isRead: Boolean, sql: String, vararg params: Any?, f: (ResultSet) -> T): T {
        return withStatement2(isRead, sql, false, *params) {
            val rs = it.executeQuery()
            try {
                if (rs.next()) f(rs) else throw NotFoundException("database record not found")
            } catch (x: Exception) {
                error("withResultSet crashed", x)
                throw x
            } finally {
                rs.close()
            }
        }
    }

    internal fun <T> withReadStatement(sql: String, vararg args: Any?, block: (PreparedStatement) -> T): T =
        withReadStatement2(sql, *args, f = block)

    internal fun <T> withWriteStatement(sql: String, vararg args: Any?, block: (PreparedStatement) -> T): T =
        withWriteStatement2(sql, *args, f = block)

    internal fun <T> withStatement2(
        isRead: Boolean,
        sql: String,
        vararg args: Any?,
        f: (PreparedStatement) -> T
    ): T {
        return if (isRead && !inTransaction)
            withReadStatement2(sql, args = args, f)
        else
            withWriteStatement(sql, args = args, f)
    }


    internal fun <T> withReadStatement2(
        sql: String,
        vararg args: Any?,
        f: (PreparedStatement) -> T
    ): T {
        debug("WRS $sql {${args.joinToString(",")}}")
        val statement = readStatementCache.remove(sql) ?: readConnection.prepareStatement(
            sql,
            PreparedStatement.RETURN_GENERATED_KEYS
        )
        statement.clearParameters()
        args.forEachIndexed { i, x ->
            setValue(x, statement,i + 1, sql)
        }
        // important: we do not close statement, we do cache it, so we should close result sets instead
        return f(statement).also { readStatementCache[sql] = statement }
    }

    private fun setValue(value: Any?,statement: PreparedStatement,column: Int,sql: String) {
        if( value == null || converter == null || !converter.toDatabaseType(value, statement,column) )
            statement.setValue(column, value, sql)
    }

    inline fun <reified T: Any>convertFromDb(rs: ResultSet,column: Int): T? =
        converter?.fromDatabaseType(T::class,rs, column) ?: rs.getValue(column)

    fun <T: Any>convertFromDb(klass: KClass<T>,rs: ResultSet,column: Int): T? =
        converter?.fromDatabaseType(klass,rs, column) ?: rs.getValue(klass,column)

    fun <T> withWriteStatement2(
        sql: String,
        vararg args: Any?,
        f: (PreparedStatement) -> T
    ): T {
        debug("WWS $sql [${args.joinToString(",")}]")
        val isInTransaction = inTransaction
        val statement = if (isInTransaction) {
            debug("in transaction, cache will not be used")
            writeConnection.prepareStatement(sql)
        } else
            writeStatementCache.remove(sql) ?: writeConnection.prepareStatement(sql)
        statement.clearParameters()
        args.forEachIndexed { i, x -> setValue(x, statement,i + 1, sql) }
        // important: we do not close statement (its cached), we should close resultsets instead
        return f(statement).also {
            if (isInTransaction) statement.close()
            else writeStatementCache[sql] = statement
        }
    }

    private var savepointLevel = AtomicInteger(0)
    /**
     * @return true if this context is already inside some savepoint or transaction
     */
    val inTransaction: Boolean get() = savepointLevel.get() > 0

    /**
     * Check that we can release this context for recycling in the [[Database]] pool
     * This method _must throw exception_ if the connection is still somehow used!
     */
    internal fun beforeRelease() {
        if (savepointLevel.get() > 0)
            throw IllegalStateException("DbContext is locked in savepoint, can't release")
    }

    /**
     * Creates savepoint and executes the block in it, rolling back on any exception throwing from it.
     * Just like a transactions, but there could as many nested savepoints and possibility pf partial
     * rollback (just catch the exception inside the block).
     */
    fun <T> savepoint(f: () -> T): T {
        val was = writeConnection.autoCommit
        writeConnection.autoCommit = false
        val sp = writeConnection.setSavepoint()
        savepointLevel.incrementAndGet()
        try {
            debug("running inside the savepoint $sp")
            val result = f()
            debug("performing savepoint commit $sp")
            writeConnection.commit()
            return result
        } catch (x: Exception) {
            debug("exception in savepoint $sp will cause rollback: $x")
            writeConnection.rollback(sp)
            throw x
        } finally {
            savepointLevel.decrementAndGet()
            debug("cleaning savepoint $sp ot level ${savepointLevel.get()}")
            writeConnection.autoCommit = was
        }
    }

    suspend fun <T> asyncSavepoint(f: suspend () -> T): T {
        val was = writeConnection.autoCommit
        writeConnection.autoCommit = false
        val sp = writeConnection.setSavepoint()
        savepointLevel.incrementAndGet()
        try {
            debug("running inside the savepoint $sp")
            val result = f()
            debug("performing savepoint commit $sp")
            writeConnection.commit()
            return result
        } catch (x: Exception) {
            debug("exception in savepoint $sp will cause rollback: $x")
            writeConnection.rollback(sp)
            throw x
        } finally {
            savepointLevel.decrementAndGet()
            debug("cleaning savepoint $sp ot level ${savepointLevel.get()}")
            writeConnection.autoCommit = was
        }
    }

    /**
     * Execute several SQL statements in one string. Note that there is _no SQL dialect parser here_ so to won't be
     * able tp rpoperly parse the commands, so caller should adhere to the following convention:
     *
     * - if the SQL statement is rather simple enough to ends with ";\n" and having no such squeences inside its body,
     *   just write it as is (regular command),
     *
     * - if the statement is multiline and may contain such string inside (for example, stored procedure, trigger, etc)
     *   put in in the block delimited like:
     *   ~~~sql
     *   -- begin block --
     *
     *   CREATE OR REPLACE FUNCTION f_random_text(
     *      param integer
     *      -- code of your multiline statement
     *      -- ...
     *
     *   -- end block --
     *   ~~~
     *   the `-- begin block --` and `--end block --` delimiters must not be idented. There could be as many
     *   such blocks as need, and every block would be treated as a single statement.
     *
     *   Anyn non-empty text before, after or betweem such blocks will be treated as simple commands delimited by
     *   "\n;" ending.
     */
    fun executeAll(sqls: String) {
        for (block in parseBlocks(sqls)) {
            val commands = if( block.isBlock ) listOf(block.value) else {
                block.value.split(";\n").filter { it.isNotBlank() }
            }
            for (line in commands) {
                writeConnection.prepareStatement(line).use {
                    val result = it.executeUpdate()
                    debug("EXECUTED: $result")
                }
            }
        }
    }

    /**
     * Start building a simple type-bound relation. Type bound relation is a statement builder that allows dynamic
     * building of parameter-bound SQL statement that could be deserialized to Kotlin objects using their
     * _primary constructors_. It could look like:
     * ~~~kotlin
     * val customer = select<Customer>().where("salary < ?", livingWage).limit(10).all
     * ~~~
     * The builder is very effective when the where clause _structure_ is not known at compile time, like depending
     * from http request parameters, but have a common part in it, letting make your code DRY.
     *
     * The weak part of it is that it always deserializes to the instance of type provided ny creation. We plan to
     * add more complex `kotlinx.serialization` based solution for more complex objects.
     */
    inline fun <reified T : Any> select(): Relation<T> =
        Relation(this, T::class)

    inline fun <reified T : Any> byId(id: Any): T? =
        select<T>().where("id = ?", id).first

    inline fun <reified T : Any> byIdForUpdate(id: Any): T? =
        select<T>().where("id = ?", id).forUpdate().first

    inline fun <reified T : Any> findBy(fieldName: String, value: Any?): T? =
        select<T>().where(fieldName to value).first

    /**
     * Execute a closure in the transaction block with an instance if T-type record loaded using specified
     * index column, like in [byId].
     */
    inline fun <reified T : Any, R> lockBy(column: String, value: Any, crossinline closure: (T) -> R): R {
        val row = select<T>().where("$column = ?", value).forUpdate().first
            ?: throw NotFoundException()
        return transaction { closure(row) }
    }

    private val closed = AtomicBoolean(false)

    val isClosed: Boolean
        get() = closed.get()

    fun close() {
        if (!closed.getAndSet(true)) {
            if (writeConnection !== readConnection) {
                ignoreExceptions { writeConnection.close() }
            }
            ignoreExceptions {
                readConnection.close()
            }
            debug("DbContext $this is closed")
        }
    }

    private var transactionLevel = 0U

    fun <T> transaction(block: () -> T): T = savepoint(block)
    suspend fun <T> asyncTransaction(block: suspend () -> T): T = asyncSavepoint(block)

//    init {
//        debug("DbContext $this is allocated")
//    }

//    companion object {
        val readStatementCache = ConcurrentBag<String, PreparedStatement>()
        val writeStatementCache = ConcurrentBag<String, PreparedStatement>()
//    }

}

private val blockRe =
    Regex("-- begin block --\\s*\\n(.*?)\\n-- end block --", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))

/**
 * Result of parsing block-containting sql script, see [parseBlocks]
 */
data class ScriptBlock(val value: String,val isBlock: Boolean)

/**
 * Parse multiline text to pieces delimited by "-- start block -- and -- end block -- strings.
 * resulting array contains all the text before, inside and after such delimiters as strings in array. Blocks are
 * represented be [ScriptBlock] structure to keep a flag that the blokc value should be treated as whole (isBlock)
 */
fun parseBlocks(blocksString: String): List<ScriptBlock> {
    var index = 0
    val result = mutableListOf<ScriptBlock>()
    do {
        val match = blockRe.find(blocksString, index)
        if (match == null) break
        val r = match.range
        result.add(ScriptBlock(blocksString.substring(index, r.first).trim(), false))
        result.add(ScriptBlock(match.groupValues[1], true))
        index = r.endInclusive + 1
    } while (true)
    if (index < blocksString.length)
        result.add(ScriptBlock(blocksString.substring(index).trim(), false))
    return result.filter { it.value.trim() != "" }
}