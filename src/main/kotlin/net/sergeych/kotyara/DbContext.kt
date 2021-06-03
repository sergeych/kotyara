package net.sergeych.kotyara

import net.sergeych.tools.Loggable
import net.sergeych.tools.TaggedLogger
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class DbContext(
    private val readConnection: Connection,
    private val writeConnection: Connection = readConnection
) : Loggable by TaggedLogger("DBC") {

    inline fun <reified T> querySingle(sql: String, vararg params: Any): T? {
        return withResultSet(true, sql, *params) { rs -> rs.getValue<T>(1) }
    }

    fun executeUpdate(sql: String, vararg params: Any?): Int =
        withWriteStatement(sql, *params) { it.executeUpdate() }

    /**
     * Calls `f` with a ResultSet prepositioned to the first row (ready to use) or returns null without
     * calling the block. This allow immediate use of the resultset. To loop through it it is recommended
     * to use `do {} while(rs.next())` construction or anything else with advancing the resultset at the end.
     *
     * @return whatever block returns or null if the result set is empty.
     */
    fun <T> withResultSet(isRead: Boolean = true, sql: String, vararg params: Any?, f: (ResultSet) -> T): T? {
        return withStatement2(isRead, sql, false, *params) {
            val rs = it.executeQuery()
            try {
                if (rs.next()) f(rs) else null
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

    fun <T> savepoint(f: () -> T): T {
        val was = readConnection.autoCommit
        readConnection.autoCommit = false
        val sp = readConnection.setSavepoint()
        try {
            debug("running inside the savepoint $sp")
            val result = f()
            debug("performing savepoint commit $sp")
            readConnection.commit()
            return result
        } catch (x: Exception) {
            debug("exception in savepoint $sp will cause rollback: $x")
            readConnection.rollback(sp)
            throw x
        } finally {
            readConnection.autoCommit = was
        }
    }

    fun executeAllSql(sqls: String) {
        for (line in sqls.split(";\n").filter { it.isNotBlank() }) {
            debug("EXEC SQL: $line")
            readConnection.prepareStatement(line).use {
                val result = it.executeUpdate()
                debug("EXECUTED: $result")
            }
        }
    }

    val closed = AtomicBoolean(false)

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

    init {
        debug("DbContext $this is allocated")
    }

    companion object {
        val readStatementCache = ConcurrentHashMap<String, PreparedStatement>()
        val writeStatementCache = ConcurrentHashMap<String, PreparedStatement>()
    }

}
