package net.sergeych.tools

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * UNDER DEVELOPMENT: sort of concurrent HashMap that should support "a in map" properly
 * [getOrPut].
 */
class SyncHashMap<K,V>(private val m: ConcurrentMap<K, V> = ConcurrentHashMap()): MutableMap<K,V> by m {

    override fun containsKey(key: K): Boolean {
        return m.containsKey(key)
    }
}