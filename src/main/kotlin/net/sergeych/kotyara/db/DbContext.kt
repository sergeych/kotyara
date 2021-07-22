package net.sergeych.kotyara.db

import net.sergeych.kotyara.*
import net.sergeych.kotyara.tools.ConcurrentBag
import net.sergeych.tools.Loggable
import net.sergeych.tools.TaggedLogger
import java.lang.IllegalStateException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DbContext(
    private val _readConnection: Connection,
    private val writeConnection: Connection = _readConnection
) : Loggable by TaggedLogger("DBC") {

    private val readConnection: Connection
        get() = if (inTransaction) writeConnection else _readConnection

    inline fun <reified T : Any> queryOne(sql: String, vararg params: Any): T? {
        return withResultSet(true, sql, *params) { rs ->
            if (rs.next())
                rs.getValue(1)
            else
                null
        }
    }

    inline fun <reified T : Any> updateQueryOne(sql: String, vararg params: Any): T? {
        return withResultSet(false, sql, *params) { rs ->
            if (rs.next())
                rs.getValue(1)
            else
                null
        }
    }

    inline fun <reified T : Any> queryRow(sql: String, vararg params: Any?): T? {
        return withResultSet(true, sql, *params) { it.asOne(T::class) }
    }

    inline fun <reified T : Any> updateQueryRow(sql: String, vararg params: Any?): T? {
        return withResultSet(false, sql, *params) { it.asOne(T::class) }
    }

    inline fun <reified T : Any> query(sql: String, vararg params: Any): List<T> =
        withResultSet(true, sql, *params) { it.asMany(T::class) }

    fun <T : Any> queryWith(sql: String, vararg params: Any, transformer: (ResultSet) -> T): List<T> =
        withResultSet(true, sql, *params) { rs ->
            val result = mutableListOf<T>()
            while (rs.next()) result.add(transformer(rs))
            result
        }

    inline fun <reified T : Any> queryColumn(sql: String, vararg params: Any): List<T> =
        withResultSet(true, sql, *params) { rs ->
            val result = mutableListOf<T?>()
            while (rs.next()) result.add(rs.getValue(T::class, 1))
            result.filterNotNull()
        }

    fun sql(sql: String, vararg params: Any?): Int =
        withWriteStatement(sql, *params) { it.executeUpdate() }

    /**
     * Calls `f` with a ResultSet prepositioned to the first row (ready to use) or returns null without
     * calling the block. This allow immediate use of the resultset. To loop through it it is recommended
     * to use `do {} while(rs.next())` construction or anything else with advancing the resultset at the end.
     *
     * @return whatever block returns or null if the result set is empty.
     */
    fun <T> withResultSet(isRead: Boolean = true, sql: String, vararg params: Any?, f: (ResultSet) -> T): T {
        return withStatement2(isRead, sql, *params) {
            val rs = if (isRead) it.executeQuery() else {
                it.execute()
                it.resultSet
            }
            try {
                f(rs)
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
    fun <T> withResultSetOrThrow(isRead: Boolean, sql: String, vararg params: Any?, f: (ResultSet) -> T): T {
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

    fun <T> withReadStatement(sql: String, vararg args: Any?, block: (PreparedStatement) -> T): T =
        withReadStatement2(sql, *args, f = block)

    fun <T> withWriteStatement(sql: String, vararg args: Any?, block: (PreparedStatement) -> T): T =
        withWriteStatement2(sql, *args, f = block)

    fun <T> withStatement2(
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


    fun <T> withReadStatement2(
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
        args.forEachIndexed { i, x -> statement.setValue(i + 1, x, sql) }
        // important: we do not close statement, we do cache it, so we should close result sets instead
        return f(statement).also { readStatementCache[sql] = statement }
    }

    fun <T> withWriteStatement2(
        sql: String,
        vararg args: Any?,
        f: (PreparedStatement) -> T
    ): T {
        debug("WWS $sql [${args.joinToString(",")}]")
        val isInTransaction = inTransaction
        val statement = if( isInTransaction ) {
            debug("in transaction, cache will not be used")
            writeConnection.prepareStatement(sql)
        }
        else
            writeStatementCache.remove(sql) ?: writeConnection.prepareStatement(sql)
        statement.clearParameters()
        args.forEachIndexed { i, x -> statement.setValue(i + 1, x, sql) }
        // important: we do not close statement (its cached), we should close resultsets instead
        return f(statement).also {
            if( isInTransaction ) statement.close()
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

    fun executeAll(sqls: String) {
        for (line in sqls.split(";\n").filter { it.isNotBlank() }) {
            debug("EXEC SQL: ${line.split("\n").joinToString("\n\t")}")
            writeConnection.prepareStatement(line).use {
                val result = it.executeUpdate()
                debug("EXECUTED: $result")
            }
        }
    }

    inline fun <reified T : Any> select(): Relation<T> =
        Relation(this, T::class)

    inline fun <reified T : Any> byId(id: Any): T? =
        select<T>().where("id = ?", id).first

    inline fun <reified T : Any> byIdForUpdate(id: Any): T? =
        select<T>().where("id = ?", id).forUpdate().first

    inline fun <reified T : Any> findBy(fieldName: String, value: Any?): T? =
        select<T>().where("$fieldName = ?", value).first

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
            if (writeConnection != readConnection)
                ignoreExceptions { writeConnection.close() }
            ignoreExceptions { readConnection.close() }
            debug("DbContext $this is closed")
        }
    }

    private var transactionLevel = 0U

    fun <T> transaction(block: () -> T): T = savepoint(block)

    init {
        debug("DbContext $this is allocated")
    }

    companion object {
        val readStatementCache = ConcurrentBag<String, PreparedStatement>()
        val writeStatementCache = ConcurrentBag<String, PreparedStatement>()
    }

}
