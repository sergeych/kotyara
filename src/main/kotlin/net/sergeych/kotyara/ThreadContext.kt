package net.sergeych.kotyara

import java.time.Instant

class ThreadContext(
    val dbContext: DbContext,
    val thread: Thread
) {
    var lastUsed: Instant = Instant.now()
        private set;

    fun touch() { lastUsed = Instant.now() }

    private var transactionDepth: Int = 0

    fun <T>doTransaction(block: ()->T): T {
        try {
            transactionDepth++;
            return dbContext.savepoint { block() }
        }
        finally {
            transactionDepth--;
        }
    }
}

