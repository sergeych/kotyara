package net.sergeych.kotyara

import net.sergeych.kotyara.migrator.PostgresSchema
import net.sergeych.tools.DefaultLogger
import net.sergeych.tools.iso8601
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import java.time.ZonedDateTime

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

    data class Simple(val foo: String, val bar: Int,val created_at: ZonedDateTime)

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

            val rows = query<Simple>("select 'bbar' as foo, 42 as bar, (now()::timestamp) as created_at")
            println(rows)
            println(rows[0].created_at.iso8601)

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
        val db = testDb()
        val i: Int = db.inContext { queryOne("select 42")!! }
        val s = PostgresSchema(db)
        s.migrateFromResources("migration_test1")
        Thread.sleep(300)
    }

}


