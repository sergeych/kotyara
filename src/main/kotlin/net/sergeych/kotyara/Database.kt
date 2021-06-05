package net.sergeych.kotyara

import net.sergeych.tools.Loggable
import net.sergeych.tools.SyncHashMap
import net.sergeych.tools.TaggedLogger
import java.lang.Exception
import java.lang.IllegalStateException
import java.sql.*
import java.util.concurrent.ConcurrentLinkedQueue

typealias ConnectionFactory = () -> Connection

class Database(
    private val writeConnectionFactory: ConnectionFactory,
    private val readConnectionFactory: ConnectionFactory = writeConnectionFactory
) : Loggable by TaggedLogger("DBSE") {

    constructor(writeUrl: String, readUrl: String = writeUrl) :
            this(
                { DriverManager.getConnection(writeUrl) },
                { DriverManager.getConnection(readUrl) }
            )

    private var closeMode = false

    var maxIdleConnections = 5
    var maxConnections = 30

    var activeConnections = 0
        private set

    private val creationLock = Object()


    private var currentContext = ThreadLocal<ThreadContext?>()
    private var activeContexts = SyncHashMap<Thread, ThreadContext>()
    private var pool = ConcurrentLinkedQueue<DbContext>()

    private fun getContext(): DbContext {
        if( closeMode ) throw NoMoreConnectionsException("closeAllConnectins is in effect")
        do {
            pool.poll()?.let { return it }
            synchronized(creationLock) {
                if (activeConnections < maxConnections) {
                    pool.add(
                        DbContext(readConnectionFactory(), writeConnectionFactory())
                    )
                    activeConnections++
                } else
                    throw NoMoreConnectionsException()
            }
        } while (true)
    }

    private fun releaseContext(ct: DbContext) {
        try {
            ct.beforeRelease()
            pool.add(ct)
        }
        catch(e: Exception) {
            if( e !is InterruptedException )
                destroyContext(ct)
            throw e
        }
    }

    private fun destroyContext(ct: DbContext) {
        ct.beforeRelease()
        ct.close()
        synchronized(creationLock) { activeConnections-- }
    }

    fun <T> withContext(block: (DbContext) -> T): T {
        val ct = getContext()
        try {
            return block(ct)
        } catch (x: SQLException) {
            error("withContext failed with SQLException, we'll close connection", x)
            destroyContext(ct)
            throw x
        } catch (x: InterruptedException) {
            throw x
        } catch (x: Throwable) {
            warning("Unexpected exception in withContext:, releasong it ($x)")
            releaseContext(ct)
            throw x
            }
    }

    fun <T>inContext(block: DbContext.() -> T): T {
        return withContext { it.block() }
    }

    fun closeAllConnections() {
        if( closeMode ) throw IllegalStateException("closeMode is already in effect")
        closeMode = true
        try {
            debug("closeAllConnections() started")
            for (i in 1..10) {
                if (activeConnections > pool.size) Thread.sleep(250)
            }
            debug("removing ${pool.size} contexts")
            while (pool.isNotEmpty()) destroyContext(pool.remove())
            debug("closeAllConnection() performed successfully")
        }
        finally {
            closeMode = false
        }
    }

//    companion object : Loggable by TaggedLogger("DBROOT") {
//
//        fun context(): DbContext =
//            (currentContext.get() ?: run {
//                ThreadContext(getDbContext(), Thread.currentThread()).also {
//                    currentContext.set(it)
//                    activeContexts.put(it.thread, it)
//                }
//            }).dbContext
//
//        fun releaseContext() {
//            currentContext.get()?.let {
//                currentContext.set(null)
//                releaseContext(it)
//            }
//        }
//
//        private fun releaseContext(tct: ThreadContext) {
//            activeContexts.remove(tct.thread)
//            recycleDbContext(tct.dbContext)
//        }
//
//        private var pool = ConcurrentLinkedQueue<DbContext>()
//
//        private fun getDbContext(): DbContext {
//            while (activeConnections.get() < maxConnections) {
//                pool.poll()?.let { return it }
//                pool.add(createNewContext())
//            }
//            throw NoMoreConnectionsException()
//        }
//
//        private fun createNewContext(): DbContext {
//            activeConnections.incrementAndGet()
//            TODO()
//        }
//
//        private fun recycleDbContext(ct: DbContext) {
//            pool.add(ct)
//        }
//
//        private fun disposeContext(ct: DbContext) {
//            debug("disposing context $ct")
//            activeConnections.decrementAndGet()
//            ct.close()
//        }
//
//        private fun cleanupStep() {
//            debug("Cleanup step started")
//            var deadThreads = 0
//            activeContexts.entries.mapNotNull {
//                if (!it.key.isAlive) {
//                    debug("Thread is not live, releasing context: $it.key")
//                    deadThreads++
//                    it.value
//                } else
//                    null
//            }.forEach {
//                releaseContext(it)
//            }
//            debug("dead threads with context: $deadThreads")
//            while (pool.size > maxIdleConnections)
//                pool.poll()?.let { disposeContext(it) }
//            debug("pool cleaned. Contexts: idle: ${pool.size}, active: ${activeContexts.size}, connections: ${activeConnections.get()}")
//        }
//
}