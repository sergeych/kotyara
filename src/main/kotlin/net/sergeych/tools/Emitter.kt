package net.sergeych.tools

import java.util.*
import kotlin.collections.HashSet

typealias Listener<T> = (T) -> Unit

class Emitter<T> {

    private val listeners = HashSet<Listener<T>>()
    private val weakListeners = WeakHashMap<Any, Listener<T>>()

    fun addListener(ll: Listener<T>) {
        synchronized(listeners) {
            listeners.add(ll)
        }
    }

    fun addWeakListener(owner: Any, ll: Listener<T>) {
        synchronized(listeners) {
            weakListeners[owner] = ll
        }
    }

    /**
     * Fire event calling weak listeners, then strong listeners, if any of them will
     * throw the exception, the rest _will not be called and the exception
     * will be thrown.
     */
    fun breakableFire(event: T) {
        for (l in allListeners) l(event)
    }

    /**
     * Fire event calling weak listeners, then strong listeners, if any of them will
     * throw the exception, it will be ignored.
     * will be thrown.
     */
    fun fire(event: T) {
        for (l in allListeners) {
            try {
                l(event)
            } catch (e: Throwable) {
            }
        }
    }

    private val allListeners get() = synchronized(listeners) { weakListeners.values + listeners }

    val size: Int
        get() = listeners.size + weakListeners.size

}