package net.sergeych.kotyara

import net.sergeych.tools.DefaultLogger
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

internal class DatabaseTest {

    companion object {
        @BeforeAll
        fun initDriver() {
            Class.forName("org.postgresql.Driver")
        }
    }

    @Test
    fun withContext() {
        val db = testDb()
        db.inContext {
            transaction {
                val i: Int? = queryOne("select ?", 11)
                assertEquals(11, i)
            }
        }
    }

    data class Simple(val foo: String, val bar: Int)

    @Test
    fun asOne() {
        val db = testDb()
        db.inContext {
            val s: Simple? = queryRow("select 'bbar' as foo, 42 as bar")
            println(s)
            assertEquals("bbar", s?.foo)
            assertEquals(42, s?.bar)
        }

    }

    @Test
    fun asMany() {
        val db = testDb()
        db.inContext {
            val rows = query<Simple>("select 'bbar' as foo, 42 as bar")
            assertEquals(1, rows.size)
            assertEquals("bbar", rows[0].foo)
            assertEquals(42, rows[0].bar)
        }
    }

    private fun testDb(): Database {
        val db = Database("jdbc:postgresql://localhost/kotyara-test")
        return db
    }

    @Test
    fun migrations() {
        DefaultLogger.connectStdout()
        val s = PostgresSchema(testDb())
        s.migrate()
        Thread.sleep(300)
    }

}


