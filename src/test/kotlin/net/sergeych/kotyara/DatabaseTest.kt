package net.sergeych.kotyara

import net.sergeych.tools.DefaultLogger
import net.sergeych.tools.ResourceHandle
import net.sergeych.tools.iso8601
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate
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

    data class Simple(val foo: String, val bar: Int,val createdAt: ZonedDateTime)
    data class SimpleSnake(val foo: String, val bar: Int,val created_at: ZonedDateTime)

    data class Person(val id: Long,val name: String,val gender: String,
                      val birthDate: LocalDate?,val createdAt: ZonedDateTime)


    @Test
    fun asMany() {
        val db = testDb()
        db.inContext {

            var rows = query<SimpleSnake>("select 'bbar' as foo, 42 as bar, (now()::timestamp) as created_at")
            println(rows)
            println(rows[0].created_at.iso8601)

            assertEquals(1, rows.size)
            assertEquals("bbar", rows[0].foo)
            assertEquals(42, rows[0].bar)
            val rows2 = query<Simple>("select 'bbar' as foo, 42 as bar, (now()::timestamp) as created_at")
            println(rows2)
            println(rows2[0].createdAt.iso8601)

            assertEquals(1, rows2.size)
            assertEquals("bbar", rows2[0].foo)
            assertEquals(42, rows2[0].bar)
        }
    }

    @Test
    fun relationAll() {
        DefaultLogger.connectStdout()

        testDb().inContext {
            executeAll("""
                drop table if exists persons;
                
                create table persons(
                    id bigserial primary key,
                    name varchar not null,
                    gender varchar(1) not null,
                    birth_date date,
                    created_at timestamp not null default now()
                    );
                    
                insert into persons(name, gender) values('John Doe', 'M');    
                insert into persons(name, gender) values('Jane Doe', 'F');   
                insert into persons(name, gender, birth_date) values('Unix Geek', 'M', '06.05.1970'::date);   
                """.trimIndent())
            var all = select<Person>().all
            assertEquals(3, all.size)
            val last = all.last()
            assertEquals("Unix Geek", last.name)
            var first = select<Person>().first!!
            println(first)
            assertEquals("M",first.gender)
            assertEquals("John Doe",first.name)
            all = select<Person>().where("gender = 'F'").all
            assertEquals(1, all.size)
            assertEquals("Jane Doe", all[0].name)
            all = select<Person>().where("gender = ?", 'F').all
            assertEquals(1, all.size)
            assertEquals("Jane Doe", all[0].name)
        }
    }

    private fun testDb(): Database {
        val db = Database("jdbc:postgresql://localhost/kotyara-test")
        return db
    }

    @Test
    fun migrations1() {

//        val x = Thread.currentThread().getContextClassLoader().resources("migration_test1/*.sql").toList()
//        val x = javaClass.classLoader.getResources("migration_test1/*.sql").toList()
//        println(x)
        for( x in ResourceHandle.list( javaClass, "/migration_test1") ) {
            println("${x.name}: ${x.lines.size} lines, ${x.bytes.size} bytes")
        }

//        DefaultLogger.connectStdout()
//        val db = testDb()
//        val i: Int = db.inContext { queryOne("select 42")!! }
//        val s = PostgresSchema(db)
//        s.migrateFromResources("migration_test1")
//        Thread.sleep(300)
    }

}


