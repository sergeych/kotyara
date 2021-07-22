package net.sergeych.kotyara

import net.sergeych.kotyara.db.DbContext
import net.sergeych.tools.Loggable
import net.sergeych.tools.TaggedLogger
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeoutException

typealias ConnectionFactory = () -> Connection

class Database(
    private val writeConnectionFactory: ConnectionFactory,
    private val readConnectionFactory: ConnectionFactory = writeConnectionFactory,
//    var maxIdleConnections: Int = 5,
    private var _maxConnections: Int = 30
) : Loggable by TaggedLogger("DBSE") {

    constructor(writeUrl: String, readUrl: String = writeUrl, maxConnections: Int = 30) :
            this(
                { DriverManager.getConnection(writeUrl) },
                { DriverManager.getConnection(readUrl) },
                _maxConnections = maxConnections
            )

    private var pause = false
    var isClosed = false
        private set

    var maxConnections: Int
        get() = if (pause || isClosed) 0 else _maxConnections
        set(value) {
            _maxConnections = value
        }

    var activeConnections = 0
        private set

    var leakedConnections = 0
        private set

    val pooledConnections: Int
        get() = pool.size

    private val creationLock = Object()

    private var pool = ConcurrentLinkedQueue<DbContext>()

    private fun getContext(): DbContext {
        if (isClosed) throw DatabaseIsClosedException()
        if (pause) throw DatabasePausedException()
        do {
            pool.poll()?.let { return it }
            synchronized(creationLock) {
                if (activeConnections < maxConnections) {
                    pool.add(
                        DbContext(readConnectionFactory(), writeConnectionFactory())
                    )
                    activeConnections++
                    info("Connection added: $activeConnections / $maxConnections / $leakedConnections")
                } else {
                    warning("Connection pool is empty, active: $activeConnections")
                    throw NoMoreConnectionsException()
                }
            }
        } while (true)
    }

    private fun releaseContext(ct: DbContext) {
        try {
            ct.beforeRelease()
            pool.add(ct)
        } catch (e: Exception) {
            if (e is InterruptedException)
                throw e
            error("exception in releaseContext, this could leak connection $ct", e)
            activeConnections--
            leakedConnections++
            info("reset connections data: $activeConnections / $maxConnections / $leakedConnections")
            try {
                ct.close()
                info("leaked connection has been closed, it will cause error in its user(s)")
            } catch(e: Exception) {
                if( e is InterruptedException) throw e
                error("failed to close leaked connection $ct")
            }
        }
    }

    private fun destroyContext(ct: DbContext) {
        ct.beforeRelease()
        ct.close()
        synchronized(creationLock) { activeConnections-- }
    }

    fun <T> withContext(block: (DbContext) -> T): T {
        val ct = getContext()
        return try {
            block(ct)
        } finally {
            releaseContext(ct)
        }
    }

    fun <T> inContext(block: DbContext.() -> T): T {
        return withContext { it.block() }
    }

    /**
     * Close all connections, blocking until all connections are closed. When this methods blocks, no
     * new contexts are served by [inContext] or [withContext], which will throw [NoMoreConnectionsException].
     * When the method finishes it will allow creating new connections. To perform some operations on the database
     * exclusively, use a hook. It will get a separate copy of the [Database] if present, and could perform
     * operations as usual, then its [Database] copy will be deleted and normal operations restored.
     *
     * @param maxWaitTime how long to wait for all connections to close.
     * @throws InterruptedException if all connections could not be closed during the specified timeout
     */
    fun closeAllContexts(
        maxWaitTime: Duration = Duration.ofSeconds(3),
        exclusiveBlock: ((Database) -> Unit)? = null
    ) {
        synchronized(creationLock) {
            if (pause) throw IllegalStateException("pause is already in effect")
            pause = true
        }
        try {
            debug("closeAllContexts() started")
            synchronized(keeperLock) { keeperLock.notify() }
            synchronized(closeAllEvent) { closeAllEvent.wait(maxWaitTime.toMillis()) }
            if (activeConnections > 0)
                throw TimeoutException("closeAllConnections: some connections could not be closed")
            while (pool.isNotEmpty()) destroyContext(pool.remove())
            debug("closeAllConnection() performed successfully")
            exclusiveBlock?.let { handler ->
                val db = Database(writeConnectionFactory, readConnectionFactory)
                handler(db)
                db.close(maxWaitTime)
            }
        } finally {
            pause = false
        }
    }

    /**
     * Close Database (closing all contexts, e.g. connections) optionally waiting for it to complete. If waiting
     * timeout is not specified, releasing contexts will be performed in the background thread.
     *
     * @param waitTime if non-zero, if blocks until all contexts are freed and closed or timeout expires, throwing
     *      [TimeoutException]. Note that in this case existing contexts will not be immediately released.
     */
    fun close(waitTime: Duration = Duration.ZERO) {
        if (isClosed) throw DatabaseIsClosedException()
        isClosed = true
        if (!(waitTime.isNegative || waitTime.isZero)) {
            closeAllContexts(waitTime)
            debug("close(): all connections are closed")
        } else {
            // keeper thread will close it all
            keeperLock.notify()
        }
    }

    fun migrateWithResources(klass: Class<*>, schema: Schema, migrationPath: String = "db_migrations") {
        schema.migrateWithResources(klass, this, migrationPath)
    }

    private val keeperLock = Object()
    private val closeAllEvent = Object()

    init {
        Thread {
            debug("starting keeper thread for $this")
            try {
                while (!isClosed) {
                    synchronized(keeperLock) {
                        keeperLock.wait(if (maxConnections < activeConnections) 200 else 10000)
                    }
                    var counter = 0
//                    debug("keeper $this step: active=$activeConnections max=$maxConnections pool=${pool.size}")
                    while (activeConnections > maxConnections && pool.isNotEmpty())
                        pool.remove()?.let { destroyContext(it); counter++ }
                    if (counter > 0)
                        debug("Keeper has released $counter contexts, active $activeConnections, pool: ${pool.size}")
                    if (activeConnections == 0) synchronized(closeAllEvent) { closeAllEvent.notifyAll() }

                }
            } catch (x: Throwable) {
                if (x !is InterruptedException)
                    error("keeper thread $this aborted", x)
            } finally {
                debug("keeper thread for $this is finished")
            }
        }.also {
            it.isDaemon = true
            it.start()
            debug("keeper thread for $this was set to daemon mode")
        }
    }

}