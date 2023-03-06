package net.sergeych.kotyara

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import net.sergeych.kotyara.db.DbContext
import net.sergeych.kotyara.db.DbTypeConverter
import net.sergeych.kotyara.tools.UnitNotifier
import net.sergeych.kotyara.tools.withReentrantLock
import net.sergeych.mp_logger.debug
import net.sergeych.mp_logger.exception
import net.sergeych.mp_logger.info
import net.sergeych.mp_logger.warning
import net.sergeych.tools.TaggedLogger
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeoutException


typealias ConnectionFactory = () -> Connection

@Suppress("unused")
class Database(
    private val writeConnectionFactory: ConnectionFactory,
    _readConnectionFactory: ConnectionFactory? = null,
//    var maxIdleConnections: Int = 5,
    private var _maxConnections: Int = 30,
    private val converter: DbTypeConverter? = null,
) : TaggedLogger("DBSE") {

    data class Stats(
        val maxConnections: Int,
        val pooledConnections: Int,
        val leakedConenctions: Int,
    )

    constructor(writeUrl: String, readUrl: String, maxConnections: Int = 30, converter: DbTypeConverter? = null) :
            this(
                { DriverManager.getConnection(writeUrl) },
                { DriverManager.getConnection(readUrl) },
                _maxConnections = maxConnections,
                converter
            )

    constructor(writeUrl: String, maxConnections: Int = 30, converter: DbTypeConverter? = null) :
            this(
                { DriverManager.getConnection(writeUrl) },
                null,
                _maxConnections = maxConnections,
                converter
            )

    private val readConnectionFactory: ConnectionFactory = _readConnectionFactory ?: writeConnectionFactory

    private var pause = false
    var isClosed = false
        private set

    private var dispatcher = ScheduledThreadPoolExecutor(_maxConnections * 4 / 3)
        .asCoroutineDispatcher()

    var maxConnections: Int
        get() = if (pause || isClosed) 0 else _maxConnections
        set(value) {
            _maxConnections = value
            (dispatcher.executor as ScheduledThreadPoolExecutor).corePoolSize = _maxConnections * 4 / 3
        }

    var activeConnections = 0
        private set

    var leakedConnections = 0
        private set

    val pooledConnections: Int
        get() = pool.size


    private var pool = mutableListOf<DbContext>()
    private var mutex = Mutex()

    private suspend fun <T> inMutex(block: suspend () -> T): T =
        mutex.withReentrantLock {
            block()
        }

    private suspend fun getContext(): DbContext {
        if (isClosed) throw DatabaseIsClosedException()
        if (pause) throw DatabasePausedException()
        mutex.lock()
        try {
            if (pool.isNotEmpty()) return pool.removeLast()
            if (activeConnections < maxConnections * 2) {
                val dbc = if (readConnectionFactory == writeConnectionFactory) {
                    val connection = readConnectionFactory()
                    DbContext(connection, connection, converter)
                } else DbContext(readConnectionFactory(), writeConnectionFactory(), converter)
                activeConnections++
                (dispatcher.executor as ScheduledThreadPoolExecutor).corePoolSize++
                info { "Connection added: $activeConnections / $maxConnections / $leakedConnections" }
                return dbc
            } else {
                warning { "Connection pool is empty, active: $activeConnections" }
                throw NoMoreConnectionsException()
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun releaseContext(ct: DbContext) {
        try {
            ct.beforeRelease()
            inMutex { pool.add(ct) }
        } catch (t: Throwable) {
            exception { "in releaseContext, this could leak connection $ct" to t }
            inMutex {
                activeConnections--
                leakedConnections++
                maxConnections++
            }
            info { "trying to close leaked connnectoin $ct" }
            kotlin.runCatching { ct.close() }
        }
    }

    private suspend fun destroyContext(ct: DbContext) {
        ct.beforeRelease()
        inMutex { ct.close() }
        activeConnections--
        (dispatcher.executor as ScheduledThreadPoolExecutor).corePoolSize++
    }

    fun <T> withContext(block: (DbContext) -> T): T {
        return runBlocking { asyncContext { block(it) } }
    }

    /**
     * A safe bridge to blocking operations.
     *
     * Executes a coroutine block allocating [DbContext] fot it  in using special build-in dispatcher that is configured
     * to work well with current [maxConnections] value and prevent threads leaking under high load. This is
     * the preferred way iof using the database in high-load environment. This methoud should not be used to permanently
     * allocate context (e.g. not usable for `listen`-type operations.
     *
     * It means, that calling couroutine will suspend until the connection and the service thread will be available
     * (it normally happens in the same time), then suspends until the block will be executed.
     *
     * __Do not use the context argument outside the block!__.
     */
    suspend fun <T> asyncContext(block: suspend (DbContext) -> T): T =
        withContext(dispatcher) {
            val ct = getContext()
            try {
                block(ct)
            } catch (t: Throwable) {
                exception { "unexpected error in asyncContext, context $ct" to t }
                throw t
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
    suspend fun closeAllContexts(
        maxWaitTime: Duration = Duration.ofSeconds(3),
        exclusiveBlock: ((Database) -> Unit)? = null,
    ) {
        inMutex {
            if (pause) throw IllegalStateException("pause is already in effect")
            pause = true
        }
        try {
            debug { "closeAllContexts() started" }
            dispatcher.close()
            keeperLock.pulse(Unit)
            closeAllEvent.await(maxWaitTime.toMillis())
            if (activeConnections > 0)
                throw TimeoutException("closeAllConnections: some connections could not be closed")
            while (pool.isNotEmpty()) destroyContext(pool.removeLast())
            debug { "closeAllConnection() performed successfully" }
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
    suspend fun close(waitTime: Duration = Duration.ZERO) {
        if (isClosed) throw DatabaseIsClosedException()
        isClosed = true
        if (!(waitTime.isNegative || waitTime.isZero)) {
            closeAllContexts(waitTime)
            debug { "close(): all connections are closed" }
        } else {
            // keeper thread will close it all
            keeperLock.pulse()
        }
    }

    fun migrateWithResources(klass: Class<*>, schema: Schema, migrationPath: String = "/db_migrations") {
        schema.migrateWithResources(klass, this, migrationPath)
    }

    private val keeperLock = UnitNotifier()
    private val closeAllEvent = UnitNotifier()

    fun stats() = Stats(maxConnections, pooledConnections, leakedConnections)

    fun logStats() {
        info { "Stats: ${stats()}" }
    }

    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            debug { "starting keeper coroutine in scope $this" }
            try {
                while (!isClosed) {
                    keeperLock.await(if (maxConnections < activeConnections) 200 else 1000)
                    var counter = 0
//                    debug("keeper $this step: active=$activeConnections max=$maxConnections pool=${pool.size}")
                    inMutex {
                        while (activeConnections > maxConnections && pool.isNotEmpty())
                            pool.removeLastOrNull()?.let { ct ->
                                try {
                                    destroyContext(ct); counter++
                                } catch (x: Throwable) {
                                    exception { "Exception while destroying context by keeper" to x }
                                }
                            }
                    }
                    if (counter > 0)
                        debug { "Keeper has released $counter contexts, active $activeConnections, pool: ${pool.size}" }
//                    if (activeConnections == 0) closeAllEvent.pulse()

                }
            } catch (x: Throwable) {
                if (x !is InterruptedException)
                    exception { "keeper thread $this aborted" to x }
            } finally {
                debug { "keeper thread for $this is finished" }
            }
        }
    }

}