package net.sergeych.kotyara.tools

import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.coroutineContext

suspend fun <T>Mutex.withReentrantLock(block: suspend ()->T): T {
    val wasLockedByMe = if (!holdsLock(coroutineContext.job)) {
        lock(coroutineContext.job)
        true
    }
    else false
    return try {
        block()
    } finally {
        if (wasLockedByMe) unlock()
    }

}