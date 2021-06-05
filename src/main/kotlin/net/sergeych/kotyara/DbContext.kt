package net.sergeych.kotyara

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
        get() = if( inTransaction ) writeConnection else _readConnection

    inline fun <reified T: Any> queryOne(sql: String, vararg params: Any): T? {
        return withResultSet(true, sql, *params) { rs ->
            if( rs.next() )
                rs.getValue(1)
            else
                null
        }
    }

    inline fun <reified T: Any> updateQueryOne(sql: String, vararg params: Any): T? {
        return withResultSet(false, sql, *params) { rs ->
            if( rs.next() )
                rs.getValue(1)
            else
                null
        }
    }

    inline fun <reified T: Any>queryRow(sql: String,vararg params: Any): T? {
        return withResultSet(true, sql, *params) { it.asOne(T::class) }
    }

    inline fun <reified T: Any>updateQueryRow(sql: String,vararg params: Any): T? {
        return withResultSet(false, sql, *params) { it.asOne(T::class) }
    }

    inline fun <reified T: Any>query(sql: String,vararg params: Any): List<T>
        = withResultSet(true, sql, *params) { it.asMany(T::class) }

        fun update(sql: String, vararg params: Any?): Int =
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
            val rs = it.executeQuery()
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
        withReadStatement2(sql, true, *args, f = block)

    fun <T> withWriteStatement(sql: String, vararg args: Any?, block: (PreparedStatement) -> T): T =
        withWriteStatement2(sql, true, *args, f = block)

    fun <T> withStatement2(
        isRead: Boolean,
        sql: String,
        vararg args: Any?,
        f: (PreparedStatement) -> T
    ): T {
        return if (isRead)
            withReadStatement2(sql, args = args, f)
        else
            withWriteStatement(sql, args = args, f)
    }


    fun <T> withReadStatement2(
        sql: String,
        vararg args: Any?,
        f: (PreparedStatement) -> T
    ): T {
        val statement = readStatementCache.getOrPut(sql) {
            readConnection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
        }
        statement.clearParameters()
        args.forEachIndexed { i, x -> statement.setValue(i + 1, x, sql) }
        // important: we do not close statement (its cached), we should close resultsets instead
        return reportExceptions { f(statement) }
    }

    fun <T> withWriteStatement2(
        sql: String,
        vararg args: Any?,
        f: (PreparedStatement) -> T
    ): T {
        val statement = writeStatementCache.getOrPut(sql) {
            writeConnection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)
        }
        statement.clearParameters()
        args.forEachIndexed { i, x -> statement.setValue(i + 1, x, sql) }
        // important: we do not close statement (its cached), we should close resultsets instead
        return reportExceptions { f(statement) }
    }


    private var savepointLevel = AtomicInteger(0)

    val inTransaction: Boolean get() = savepointLevel.get() > 0

    /**
     * Check that we can release this context for recycling in the [[Database]] pool
     * This method _must throw exception_ if the connection is still somehow used!
     */
    internal fun beforeRelease() {
        if( savepointLevel.get() > 0 )
            throw IllegalStateException("DbContext is locked in savepoint, can't release")
    }

    fun <T> savepoint(f: () -> T): T {
        savepointLevel.incrementAndGet()
        val was = writeConnection.autoCommit
        writeConnection.autoCommit = false
        val sp = writeConnection.setSavepoint()
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
            writeConnection.autoCommit = was
            savepointLevel.decrementAndGet()
        }
    }

    fun executeAll(sqls: String) {
        for (line in sqls.split(";\n").filter { it.isNotBlank() }) {
            debug("EXEC SQL: $line")
            writeConnection.prepareStatement(line).use {
                val result = it.executeUpdate()
                debug("EXECUTED: $result")
            }
        }
    }

    private val closed = AtomicBoolean(false)

    val isClosed: Boolean
        get() = closed.get()

    fun close() {
        if( !closed.getAndSet(true) ) {
            if (writeConnection != readConnection)
                ignoreExceptions { writeConnection.close() }
            ignoreExceptions { readConnection.close() }
            debug("DbContext $this is closed")
        }
    }

    private var transactionLevel = 0U

    fun <T>transaction(block:()->T): T = savepoint(block)

    init {
        debug("DbContext $this is allocated")
    }

    companion object {
        val readStatementCache = ConcurrentHashMap<String, PreparedStatement>()
        val writeStatementCache = ConcurrentHashMap<String, PreparedStatement>()
    }

}
