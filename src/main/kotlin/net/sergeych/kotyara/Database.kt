package net.sergeych.kotyara

import net.sergeych.tools.Loggable
import net.sergeych.tools.SyncHashMap
import net.sergeych.tools.TaggedLogger
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class Database {
    companion object : Loggable by TaggedLogger("DBROOT") {

        var maxIdleConnections = 5
        var maxConnections = 30


        private val activeConnections = AtomicInteger(0)

        private var currentContext = ThreadLocal.withInitial<ThreadContext?>(null)
        private var activeContexts = SyncHashMap<Thread,ThreadContext>()

        fun context(): DbContext =
            (currentContext.get() ?: run {
                ThreadContext(getDbContext(), Thread.currentThread()).also {
                    currentContext.set(it)
                    activeContexts.put(it.thread,it)
                }
            }).dbContext

        fun releaseContext() {
            currentContext.get()?.let {
                currentContext.set(null)
                releaseContext(it)
            }
        }

        private fun releaseContext(tct: ThreadContext) {
            activeContexts.remove(tct.thread)
            recycleDbContext(tct.dbContext)
        }

        private var pool = ConcurrentLinkedQueue<DbContext>()

        private fun getDbContext(): DbContext {
            while(activeConnections.get() < maxConnections) {
                pool.poll()?.let { return it }
                pool.add(createNewContext())
            }
            throw NoMoreConnectionsException()
        }

        private fun createNewContext(): DbContext {
            activeConnections.incrementAndGet()
            TODO()
        }

        private fun recycleDbContext(ct: DbContext) {
            pool.add(ct)
        }

        private fun disposeContext(ct: DbContext) {
            debug("disposing context $ct")
            activeConnections.decrementAndGet()
            ct.close()
        }

        private fun cleanupStep() {
            debug("Cleanup step started")
            var deadThreads = 0
            activeContexts.entries.mapNotNull {
                if( !it.key.isAlive ) {
                    debug("Thread is not live, releasing context: $it.key")
                    deadThreads++
                    it.value
                }
                else
                    null
            }.forEach {
                releaseContext(it)
            }
            debug("dead threads with context: $deadThreads")
            while( pool.size > maxIdleConnections)
                pool.poll()?.let { disposeContext(it) }
            debug("pool cleaned. Contexts: idle: ${pool.size}, active: ${activeContexts.size}, connections: ${activeConnections.get()}")
        }

//        fun createContext(): DbContext {
//            return DbContext()
//        }
    }
}