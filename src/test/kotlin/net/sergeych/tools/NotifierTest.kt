package net.sergeych.tools

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.sergeych.kotyara.tools.AsyncNotifier
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class NotifierTest {

    @Test
    fun snakeToCamelCase() {
        runBlocking {
            val x = AsyncNotifier<Int>()
            x.withLock {  x.pulse(100) }
            assertEquals(null,x.await(10))
            launch {
                delay(100)
                x.pulse(300)
            }
            assertEquals(300,x.await(300))
        }
    }

    @Test
    fun reentarantLockInAsyncNotifier() {
        runBlocking {
            val x = AsyncNotifier<Unit>()
            launch {
                delay(20)
                x.pulse(Unit)
            }
            x.withLock {
                x.withLock {
                    x.withLock {
                        assertEquals(Unit,x.await(10_000))
                    }
                }
            }
        }
    }
}