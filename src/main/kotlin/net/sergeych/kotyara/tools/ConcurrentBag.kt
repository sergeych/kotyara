package net.sergeych.kotyara.tools

/**
 * The simple thread-safe map-like collection that can contain any number of elements associated with the same
 * key. It does not implement strict MutableMap protocol for the sake of speed.
 *
 * Elements are kept in order of appearance.
 */
class ConcurrentBag<K, V> {

    private var currentSize = 0
    private val access = Object()
    private val map = mutableMapOf<K, MutableList<V>>()

    val size: Int
        get() = currentSize

    fun containsKey(key: K): Boolean =
        synchronized(access) {
            map[key]?.isNotEmpty() ?: false
        }

    fun containsValue(value: V): Boolean =
        value in values

    /**
     * Get the latest element added with a specified key, or null.
     */
    operator fun get(key: K): V? = synchronized(access) { map[key]?.firstOrNull() }

    /**
     * Add element with specified key. Note that the last added element for a key will be returned
     * by get.
     */
    operator fun set(key: K, value: V): Unit = synchronized(access) {
        map.getOrPut(key) { mutableListOf() }.add(value)
        currentSize++
    }

    /**
     * Concurrent get or put implementation
      */
    fun getOrPut(key: K, closure: () -> V): V = synchronized(access) {
        this[key] ?: run {
            val v = closure()
            this[key] = v
            v
        }
    }

    fun isEmpty(): Boolean = currentSize == 0

    val keys: Set<K>
        get() = synchronized(access) { map.keys }

    val values: Collection<V>
        get() = synchronized(access) {
            map.values.flatten()
        }

    /**
     * Return all values associated with a key. The result could be an empty list.
     */
    fun valuesFor(key: K): List<V> = synchronized(access) {
        map[key] ?: listOf()
    }

    fun clear() {
        synchronized(access) {
            currentSize = 0
            map.clear()
        }
    }

    fun putAll(from: Map<out K, V>) {
        synchronized(access) {
            for( e in from ) this[e.key] = e.value
        }
    }

    fun remove(key: K): V? = synchronized(access) {
        map[key]?.let { it.removeLastOrNull()?.also { currentSize-- } }
    }


}