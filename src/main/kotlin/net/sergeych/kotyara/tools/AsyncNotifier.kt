package net.sergeych.kotyara.tools

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

@Suppress("EXPERIMENTAL_API_USAGE")
open class AsyncNotifier<T : Any> {

    val channel = Channel<T>(Channel.CONFLATED)
    val mutex = Mutex()

    suspend fun await(millis: Long): T? {
        return if (!mutex.holdsLock(coroutineContext.job))
            withLock { await(millis) }
        else
            withTimeoutOrNull(millis) {
                while (!channel.isEmpty) channel.receive()
                mutex.unlock()
                channel.receive()
            }.also {
                mutex.lock(coroutineContext.job)
            }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun pulse(value: T=(Unit as T)) {
        if (!mutex.holdsLock(coroutineContext.job)) withLock { pulse(value) }
        else channel.send(value)
    }

    suspend fun <R> withLock(block: suspend () -> R): R =
        mutex.withReentrantLock(block)
}

class UnitNotifier : AsyncNotifier<Unit>()