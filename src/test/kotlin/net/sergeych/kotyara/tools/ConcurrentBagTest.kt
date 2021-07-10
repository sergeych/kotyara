package net.sergeych.kotyara.tools

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import kotlin.test.assertContains

internal class ConcurrentBagTest {

    @Test
    fun getSize() {
        val x = ConcurrentBag<String, Int>()
        assertEquals(0, x.size)
        x["foo"] = 1
        assertEquals(1, x.size)
        x["foo"] = 1
        assertEquals(2, x.size)
        x["foo"] = 2
        assertEquals(3, x.size)
        x["bar"] = 42
        assertEquals(4, x.size)
    }

    @Test
    fun containsKey() {
        val x = ConcurrentBag<String, Int>()
        x["foo"] = 1
        x["foo"] = 1
        x["foo"] = 2
        x["bar"] = 42
        assertContains(x.keys, "foo")
        assertContains(x.keys, "bar")
        assert("bar" in x.keys)
        assert("barrage" !in x.keys)
    }

    @Test
    fun containsValue() {
        val x = ConcurrentBag<String, Int>()
        x["foo"] = 1
        x["foo"] = 1
        x["foo"] = 2
        x["bar"] = 42
        assertContains(x.values, 1)
        assertContains(x.values, 2)
        assertContains(x.values, 42)
        assert(3 !in x.values)
    }

    @Test
    fun getOrPut() {
        val x = ConcurrentBag<String, Int>()
        x["foo"] = 1
        x["foo"] = 2
        x.getOrPut("foo") { 3 }
        x.getOrPut("bar") { 42 }
        assertEquals(42, x["bar"])
        assert(3 !in x.values)
    }

    @Test
    fun isEmpty() {
        val x = ConcurrentBag<String, Int>()
        assertTrue(x.isEmpty())
        x["foo"] = 1
        x["foo"] = 2
        assertFalse(x.isEmpty())
        x.clear()
        assertTrue(x.isEmpty())
    }

    @Test
    fun getKeys() {
        val x = ConcurrentBag<String, Int>()
        x["foo"] = 1
        x["foo"] = 1
        x["foo"] = 2
        x["bar"] = 42
        assertContains(x.keys, "foo")
        assertContains(x.keys, "bar")
        assertEquals(2, x.keys.size)
    }

    @Test
    fun getValues() {
        val x = ConcurrentBag<String, Int>()
        x["foo"] = 1
        x["foo"] = 1
        x["foo"] = 2
        x["bar"] = 42
        assertContains(x.values, 1)
        assertContains(x.values, 2)
        assertContains(x.values, 42)
        assertEquals(4, x.size)
    }

    @Test
    fun putAll() {
        val x = ConcurrentBag<String, Int>()
        x.putAll(
            mapOf(
                "foo" to 1,
                "bar" to 42,
            )
        )
        assertEquals(x["foo"], 1)
        assertEquals(x["bar"], 42)
    }

    @Test
    fun putOrder() {
        val x = ConcurrentBag<String, Int>()
        x["foo"] = 10
        x["foo"] = 20
        x["foo"] = 30
        assertEquals(10, x["foo"])
    }

    @Test
    fun remove() {
        val x = ConcurrentBag<String, Int>()
        x["foo"] = 10
        x["foo"] = 20
        x["foo"] = 30
        x["bar"] = 101
        assertEquals(30, x.remove("foo"))
        assertEquals(3, x.size)
        assertEquals(20, x.remove("foo"))
        assertEquals(2, x.size)
        assertEquals(10, x.remove("foo"))
        assertEquals(1, x.size)
        assertEquals(null, x.remove("foo"))
        assertEquals(1, x.size)
        assertEquals(101, x.remove("bar"))
        assertEquals(0, x.size)
        assertTrue(x.isEmpty())
    }

    @Test
    fun valuesFor() {
        val x = ConcurrentBag<String, Int>()
        x["foo"] = 1
        x["foo"] = 1
        x["foo"] = 2
        x["bar"] = 42
        assertEquals(listOf(1, 1, 2), x.valuesFor("foo"))
    }
}
