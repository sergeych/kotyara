package net.sergeych.kotyara

import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import net.sergeych.bipack.BipackEncoder
import net.sergeych.kotyara.db.DbJson
import net.sergeych.kotyara.db.DbTypeConverter
import net.sergeych.kotyara.db.updateCheck
import net.sergeych.kotyara.migrator.PostgresSchema
import net.sergeych.mp_logger.Log
import net.sergeych.mptools.toDump
import net.sergeych.tools.iso8601
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.test.assertContentEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

enum class Enum1 {
    FOO, BAR;
}

internal data class Outer(val x: Int) {
    enum class Enum1 {
        FOO, BAR;
    }
}


@OptIn(ExperimentalSerializationApi::class)
internal class DatabaseTest {

    companion object {
        @BeforeAll
        @JvmStatic
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

    @Test
    fun convertInstant() {
        val db = testDb()
        db.inContext {
            transaction {
                val i = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                val i1: Instant? = queryOne("select ?", i)
                assertEquals(i, i1)
            }
        }

    }

    @Test
    fun convertDoubles() {
        val db = testDb()
        db.inContext {
            transaction {
                val i = 177.107
                val i1: Double? = queryOne("select ?", i)
                val i2: Double? = queryOne("select (177.107)::double precision")
                assertEquals(i, i1)
                assertEquals(i, i2)
            }
        }

    }


    @Serializable
    data class S1(val i: Int, val s: String)

    @Test
    fun convertBoss() {
        val db = testDb()
        val s1 = S1(42, "foobar")
        val x = BipackEncoder.encode(s1)
        println(x.toDump())
        db.inContext {
            val s2: S1? = queryOne("select ?", s1)
            assertEquals(s1, s2)
            println(queryOne<ByteArray>("select ?::bytea", s1)?.toDump())
            assertContentEquals(x, queryOne<ByteArray>("select ?::bytea", s1))
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun converter() {
//        val db = testDb()
        val db = testDb(converter = object : DbTypeConverter {
            override fun toDatabaseType(value: Any, statement: PreparedStatement, column: Int): Boolean {
                return if (value is BigInteger) {
                    statement.setString(column, value.toString())
                    true
                } else false
            }

            override fun <T : Any> fromDatabaseType(klass: KClass<T>, rs: ResultSet, column: Int): T? =
                if (klass == BigInteger::class) BigInteger(rs.getString(column)) as T? else null
        })
        db.inContext {
            transaction {
                val i: BigInteger? = queryOne("select ?", BigInteger("11"))
                assertEquals(BigInteger("11"), i)
            }
        }
    }

    @Test
    fun selectOne() {
        val db = testDb()
        val x: Int = db.withContext { dbc ->
            dbc.sql("drop table if exists foobars")
            dbc.sql("create table if not exists foobars(id bigserial not null primary key, text varchar)")
            dbc.queryOne("select count(*) from foobars where text=? or text = ?", "12", "11")!!
        }
        println(x)
    }

    @Test
    fun ubyteArray() {
        val db = testDb()
        db.withContext { dbc ->
            dbc.sql("drop table if exists foobars")
            dbc.sql("create table if not exists foobars(data bytea)")
            dbc.execute("insert into foobars(data) values(?)", byteArrayOf(1,2,3))
            assertContentEquals(byteArrayOf(1, 2, 3), dbc.queryOne<ByteArray>("select data from foobars limit 1")!!)
            assertContentEquals(ubyteArrayOf(1u, 2u, 3u), dbc.queryOne<UByteArray>("select data from foobars limit 1")!!)
            dbc.execute("delete from foobars")
            dbc.execute("insert into foobars(data) values(?)", ubyteArrayOf(4u,5u,6u))
            assertContentEquals(byteArrayOf(4, 5, 6), dbc.queryOne<ByteArray>("select data from foobars limit 1")!!)
            assertContentEquals(ubyteArrayOf(4u, 5u, 6u), dbc.queryOne<UByteArray>("select data from foobars limit 1")!!)
        }
    }

    @Test
    fun testFreeContext() {
        val db = testDb()
        val s0 = db.stats()
        println(s0)
        db.logStats()
        var ok = false
        try {
            runBlocking {
                db.asyncContext { dbc ->
                    println("got async context")
                    delay(10)
                    throw Throwable("the test")
//                    throw Throwable("the test")
                }
            }
        } catch (x: Throwable) {
            if (x.message != "the test") fail("wrong exception: $x")
            ok = true
        }
        if (!ok) fail("exception was not thrown")
        println(db.stats())
//        val sa = db.stats()
        assertEquals(0, db.leakedConnections)
//        val x: Int = db.withContext { dbc ->
//            dbc.sql("drop table if exists foobars")
//            dbc.sql("create table if not exists foobars(id bigserial not null primary key, text varchar)")
//            dbc.queryOne("select count(*) from foobars where text=? or text = ?", "12", "11")!!
//        }
//        println(x)
    }


    data class Simple(val foo: String, val bar: Int, val createdAt: ZonedDateTime)
    data class SimpleSnake(val foo: String, val bar: Int, val created_at: ZonedDateTime)

    data class Person(
        val id: Long, val name: String, val gender: String,
        val birthDate: LocalDate?, val createdAt: ZonedDateTime,
    )

    data class Present(
        val id: Long, val personId: Long, val name: String,
    )

    @Test
    fun enums() {
        data class Foobar1(val foo: Enum1, val bar: Enum1)

        val db = testDb()
        db.withContext {
            val x = it.queryOne<Enum1>("select ?", Enum1.FOO)
            assertEquals(Enum1.FOO, x)

            it.sql("drop table if exists foobars")
            it.sql("create table foobars(foo int,bar varchar)")
            val r = it.updateQueryRow<Foobar1>(
                "insert into foobars(foo,bar) values(?,?) returning *",
                Enum1.FOO, Enum1.BAR
            )
            println(":: $r")
            assertEquals(Enum1.FOO, r!!.foo)
            assertEquals(Enum1.BAR, r.bar)
        }
    }

    @Test
    fun enumsInside() {
        data class Foobar1(val foo: Outer.Enum1, val bar: Outer.Enum1)

        val db = testDb()
        db.withContext {
            it.sql("drop table if exists foobars")
            it.sql("create table foobars(foo int,bar varchar)")
            val r = it.updateQueryRow<Foobar1>(
                "insert into foobars(foo,bar) values(?,?) returning *",
                Outer.Enum1.FOO, Outer.Enum1.BAR
            )
            println(":: $r")
            assertEquals(Outer.Enum1.FOO, r!!.foo)
            assertEquals(Outer.Enum1.BAR, r.bar)
        }
    }

    @Test
    fun asMany() {
        val db = testDb()
        db.inContext {

            val rows = query<SimpleSnake>("select 'bbar' as foo, 42 as bar, (now()::timestamp) as created_at")
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
    fun returning() {
        testDb().inContext {
            executeAll(
                """
                drop table if exists persons cascade;
                
                create table persons(
                    id bigserial primary key,
                    name varchar not null,
                    gender varchar(1) not null,
                    birth_date date,
                    created_at timestamp not null default now()
                    );               
                """.trimIndent()
            )

            val i = updateQueryRow<Person>(
                """
                insert into persons(name,gender) values('Jimmy Gordon', 'M') returning *;
                """.trimIndent()
            )
            println(i)
        }
    }

    @Test
    fun selectByteArray() {
        testDb().inContext {
            executeAll(
                """
                drop table if exists binaries cascade;
                
                create table binaries(
                    id bigserial primary key,
                    name varchar not null,
                    login_id bytea,
                    data bytea,
                    created_at timestamp not null default now()
                    );               
                """.trimIndent()
            )
            val data = byteArrayOf(1,2,0,3)
            val loginId = byteArrayOf(5,6,0,7,8)
            updateCheck(1,
                """
                    insert into binaries(name,login_id,data) values(?,?,?);
                """.trimIndent(), "jdoe", loginId, data)
            val x = queryOne<ByteArray>("select data from binaries where name=? and login_id=?",
                "jdoe", loginId)
            assertContentEquals(data, x)
            null
        }
    }

    @Test
    fun selectUByteArray() {
        testDb().inContext {
            executeAll(
                """
                drop table if exists binaries cascade;
                
                create table binaries(
                    id bigserial primary key,
                    name varchar not null,
                    login_id bytea,
                    data bytea,
                    created_at timestamp not null default now()
                    );               
                """.trimIndent()
            )
            val data = byteArrayOf(1,2,0,3).asUByteArray()
            val loginId = byteArrayOf(5,6,0,7,8).asUByteArray()
            updateCheck(1,
                """
                    insert into binaries(name,login_id,data) values(?,?,?);
                """.trimIndent(), "jdoe", loginId, data)
            val x = queryOne<ByteArray>("select data from binaries where name=? and login_id=?",
                "jdoe", loginId)
            assertContentEquals(data, x?.asUByteArray())
            null
        }
    }

    @Test
    fun testProperByteArrayPassing() {
        val x = byteArrayOf(1,2,3)
        testDb().inContext {
            assertContentEquals(x, queryOne<ByteArray>("select ?",x))
            assertContentEquals(x, queryOne<ByteArray>("select ?",x.asUByteArray()))
            assertContentEquals(x.asUByteArray(), queryOne<UByteArray>("select ?",x.asUByteArray()))
        }
    }

    @Test
    fun relationAll() {
        Log.connectConsole(Log.Level.DEBUG)

        testDb().inContext {
            executeAll(
                """
                drop table if exists persons cascade;
                
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
                   
                drop table if exists presents;
                create table presents (
                    id bigserial not null primary key,
                    person_id bigint not null references persons(id) on delete cascade,
                    name varchar not null
                );
                
                insert into presents(person_id, name) values(1, 'toy car');
                insert into presents(person_id, name) values(1, 'toy pistol');
                insert into presents(person_id, name) values(2, 'teddy bear');
                insert into presents(person_id, name) values(2, 'doll');
                insert into presents(person_id, name) values(3, 'computer');
                   
                """.trimIndent()
            )


            var all = select<Person>().all
            assertEquals(3, all.size)
            val last = all.last()
            assertEquals("Unix Geek", last.name)
            kotlin.test.assertNotNull(last.birthDate)
            assertIs<LocalDate>(last.birthDate)
            val first = select<Person>().first!!
            println(first)
            assertEquals("M", first.gender)
            assertEquals("John Doe", first.name)
            all = select<Person>().where("gender = 'F'").all
            assertEquals(1, all.size)
            assertEquals("Jane Doe", all[0].name)
            all = select<Person>().where("gender = ?", 'F').all
            assertEquals(1, all.size)
            assertEquals("Jane Doe", all[0].name)

            var x = select<Person>().where("gender=?", "&").first
            kotlin.test.assertNull(x)

            x = select<Person>().where("birth_date=?", last.birthDate).first
            assertEquals(last.id, x?.id)
// postgres: NULL != NULL! this will not work
//            val xx = select<Person>().where("birth_date=?", null).all
//            assertEquals(2, xx.size)

            val xx = select<Person>().where("birth_date" to null).all
            assertEquals(2, xx.size)
            assertEquals(setOf("John Doe", "Jane Doe"), xx.map { it.name }.toSet())

            var y = select<Present>()
            assertEquals(
                setOf("toy car", "teddy bear", "computer", "doll", "toy pistol"),
                y.all.map { it.name }.toSet()
            )

            y = select<Present>().addJoin("inner join persons on persons.id=presents.person_id")
                .where("persons.gender = ?", "M")
            assertEquals(setOf("toy car", "toy pistol", "computer"), y.all.map { it.name }.toSet())
            println(y.toString())

            y = select<Present>().join<Person>()
                .where("persons.gender = ?", "F")
            assertEquals(setOf("teddy bear", "doll"), y.all.map { it.name }.toSet())

            val z = select<Person>().include<Present>()
                .where("presents.name = ?", "doll")
            println(z.toString())
            assertEquals(setOf("Jane Doe"), z.all.map { it.name }.toSet())
        }
    }

    @Test
    fun deleteAll() {
        Log.connectConsole(Log.Level.DEBUG)

        testDb().inContext {
            executeAll(
                """
                drop table if exists persons cascade;
                
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
                   
                drop table if exists presents;
                create table presents (
                    id bigserial not null primary key,
                    person_id bigint not null references persons(id) on delete cascade,
                    name varchar not null
                );
                
                insert into presents(person_id, name) values(1, 'toy car');
                insert into presents(person_id, name) values(1, 'toy pistol');
                insert into presents(person_id, name) values(2, 'teddy bear');
                insert into presents(person_id, name) values(2, 'doll');
                insert into presents(person_id, name) values(3, 'computer');
                   
                """.trimIndent()
            )


            var all = select<Person>().all
            assertEquals(3, all.size)

            val cnt = select<Person>().where("gender != ?", 'F').deleteAll()
            assertEquals(2, cnt)
            all = select<Person>().all
            assertEquals(1, all.size)
            val last = all.last()
            assertEquals("Jane Doe", last.name)
        }
    }

    private fun testDb(maxConnections: Int = 10, converter: DbTypeConverter? = null) =
        Database("jdbc:postgresql://localhost/kotyara-test", maxConnections, converter)

    @Test
    fun manyConnections() {
        val N = 5
        val db = testDb(3)
        val x = AtomicInteger(0)
        Log.connectConsole(Log.Level.DEBUG)
        runBlocking {
            for (rep in 1..1) {
                bm {
                    coroutineScope {
                        val all = mutableListOf<Job>()
                        for (i in 1..N) {
                            all.add(launch(start = CoroutineStart.DEFAULT) {
                                println("Launcin $i/$rep")
                                db.asyncContext {
                                    it.execute("select pg_sleep(0.31)")
//                                    it.execute("select 22")
                                    x.incrementAndGet()
                                }
                                println("finishing $i/$rep")
                            })
                        }
                        for (xx in all) xx.join()
                    }
                }
            }
            println("sequental time would be ${N * 300}")
            delay(3000)
            bm {
                coroutineScope {
                    val all = mutableListOf<Job>()
                    for (i in 1..N) {
                        all.add(launch(start = CoroutineStart.DEFAULT) {
                            db.asyncContext {
                                it.execute("select pg_sleep(0.31)")
//                                it.execute("select 22")
                                x.incrementAndGet()
                            }
                        })
                    }
                    for (xx in all) xx.join()
                }
            }
            println("done-1")
            delay(700)
        }
        println("done-2")
//        assertEquals(N, x.get())
    }

    @Test
    fun migrations1() {

//        val x = Thread.currentThread().getContextClassLoader().resources("migration_test1/*.sql").toList()
//        val x = javaClass.classLoader.getResources("migration_test1/*.sql").toList()
//        println(x)
//        for( x in ResourceHandle.list( javaClass, "/migration_test1") ) {
//            println("${x.name}: ${x.lines.size} lines, ${x.bytes.size} bytes")
//        }

//        DefaultLogger.connectStdout()
        val db = testDb()
        db.withContext { dbc ->
            dbc.sql("drop table if exists simple_types")
            dbc.sql("drop table if exists params")
            dbc.sql("drop table if exists __performed_migrations")
        }
        Log.connectConsole()
        db.migrateWithResources(this.javaClass, PostgresSchema(), "/migration_test1")

        db.withContext { ct ->
            println("see: ${ct.queryOne<Int>("select count(*) from params")}")
        }
//        val i: Int = db.inContext { queryOne("select 42")!! }
//        val s = PostgresSchema(db)
//        s.migrateFromResources("migration_test1")
//        Thread.sleep(300)
    }

    @Test
    fun freeSyncContextOnErrors() {
        val db = testDb()
//        fun stats(prefix: String = "") {
//            println("$prefix A:${db.activeConnections} P:${db.pooledConnections} !${db.leakedConnections} of ${db.maxConnections}")
//        }
//        stats()
        db.withContext { println("$it") }
//        stats()
        for (i in 1..5) {
            try {
                db.withContext {
//                    stats("in")
                    throw IllegalStateException()
                }
            } catch (x: java.lang.IllegalStateException) {
//                stats("out")
            }
        }
//        stats("result")
        assertEquals(db.activeConnections, db.pooledConnections)
    }

    @Test
    fun freeAsyncContextOnErrors() {
        val db = testDb()
//        fun stats(prefix: String = "") {
//            println("$prefix A:${db.activeConnections} P:${db.pooledConnections} !${db.leakedConnections} of ${db.maxConnections}")
//        }
//        stats()
        runBlocking {
            db.asyncContext { println("$it") }
//            stats()
            for (i in 1..5) {
                try {
                    db.asyncContext {
                        db.asyncContext {
                            db.asyncContext {
                                db.asyncContext {
                                    db.asyncContext {
                                        db.asyncContext {
//                                            stats("inAsync")
                                            throw IllegalStateException()
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (x: java.lang.IllegalStateException) {
//                    stats("out")
                }
            }
//            stats("result2")
            assertEquals(db.activeConnections, db.pooledConnections)
        }
    }
    
    @Test
    fun testLists() {
        val db = testDb()
        db.withContext { dbc ->
            assertContentEquals(arrayOf(1231, 22), dbc.queryOne<Array<Any>>("select '{1231,22}'::int[]"))
            assertContentEquals(listOf(1231, 221), dbc.queryOne<List<Any>>("select '{1231,221}'::int[]"))

            dbc.sql("drop table if exists foobars")
            dbc.sql("create table foobars(foo int,bar varchar)")
            for (i in 1..5) dbc.update("insert into foobars(foo,bar) values(?,?)", i, "val-$i")

            data class Foobar(val foo: Int, val bar: String)

            val fbs = dbc.query<Foobar>("select * from foobars where foo = any (?)", listOf(2, 5))
            assertEquals(listOf(2, 5), fbs.map { it.foo })
//            fbs = dbc.query<Foobar>("select * from foobars where foo = any (?)", listOf("123"))
//            fbs = dbc.query<Foobar>("select * from foobars where foo = any (?)", listOf<Double>())
//            assertEquals(0, fbs.size)
        }
    }

    @Test
    fun testKotlinxTime() {
        testDb().withContext { dbc ->
            val t: kotlinx.datetime.Instant = Clock.System.now()
            val t1: kotlinx.datetime.Instant? = dbc.queryOne<kotlinx.datetime.Instant>("select now()")
            println(t1!! - t)
            assertTrue { t1!! - t < 10.milliseconds }
            val t2: kotlinx.datetime.Instant? = dbc.queryOne<kotlinx.datetime.Instant>("select ?", t1)
            println(t2!! - t1)
            assertTrue { t2!! - t1!! < 1.milliseconds }

            val stz = TimeZone.currentSystemDefault()
            val dt: LocalDateTime = Clock.System.now().toLocalDateTime(stz)
            val dt1: LocalDateTime = dbc.queryOne<LocalDateTime>("select now()")!!
            val dt2: LocalDateTime = dbc.queryOne<LocalDateTime>("select ?", dt1)!!
            println("$dt | $dt1 || ${dt1!!.toInstant(stz) - dt.toInstant(stz)}")
            assertTrue { (dt1!!.toInstant(stz) - dt.toInstant(stz)).absoluteValue < 10.milliseconds }
            assertTrue { (dt2!!.toInstant(stz) - dt1.toInstant(stz)).absoluteValue < 1.milliseconds }

            val d: kotlinx.datetime.LocalDate = dt.date
            val d1: kotlinx.datetime.LocalDate = dbc.queryOne("select now()")!!
            val d2: kotlinx.datetime.LocalDate = dbc.queryOne("select ?", d)!!
            println(d1)
            println("---------- ${d1 - d}")
            val x: DatePeriod = d1 - d
            assertTrue { (d1.atStartOfDayIn(stz) - d.atStartOfDayIn(stz)).absoluteValue < 12.milliseconds }
            assertTrue { (d2.atStartOfDayIn(stz) - d1.atStartOfDayIn(stz)).absoluteValue < 12.milliseconds }
//            assertTrue {  - dt.toInstant(stz)).absoluteValue < 10.milliseconds }

        }
    }

    @Test
    fun testJsonSerializerAnnotation() {

        @Serializable
        @DbJson
        data class JSFoo(val foo: String, val bar: Int)

        val f1 = JSFoo("foobar", 42)
        testDb().withContext { dbc ->
            assertEquals("""{"foo":"foobar","bar":42}""", dbc.queryOne<String>("select ?", f1))
            val d = dbc.queryOne<JSFoo>("select ?", f1)
            assertEquals(f1, d)

            dbc.execute(
                """
                create table if not exists dbjson_test(
                    id serial not null primary key,
                    encoded json not null
                );
            """.trimIndent()
            )
            dbc.execute("delete from dbjson_test")
            val id = dbc.updateQueryOne<Int>("insert into dbjson_test(encoded) values(?) returning id", f1)!!
            val d2 = dbc.queryOne<JSFoo>("select encoded from dbjson_test where id=?", id)
            assertEquals(f1, d2)
        }
    }

    @Test
    fun customConverters() {

        @Serializable
        data class JSFoo(val foo: String, val bar: Int)

        val f1 = JSFoo("foobar", 42)
        testDb().apply { registry.asJson<JSFoo>() }
            .withContext { dbc ->
                assertEquals("""{"foo":"foobar","bar":42}""", dbc.queryOne<String>("select ?", f1))
                val d = dbc.queryOne<JSFoo>("select ?", f1)
                assertEquals(f1, d)

                dbc.execute(
                    """
                create table if not exists dbjson_test(
                    id serial not null primary key,
                    encoded json not null
                );
            """.trimIndent()
                )
                dbc.execute("delete from dbjson_test")
                val id = dbc.updateQueryOne<Int>("insert into dbjson_test(encoded) values(?) returning id", f1)!!
                val d2 = dbc.queryOne<JSFoo>("select encoded from dbjson_test where id=?", id)
                assertEquals(f1, d2)
            }

//        val db = testDb()
//        db.
//        db.withContext { dbc ->
//            dbc.
//        }
    }

    @Test
    fun closeOnSqlException() {
        runBlocking {
            Log.connectConsole(Log.Level.DEBUG)
            val db = testDb(2)
            for (i in 1..30) {
                try {
                    db.withContext { throw SQLException("SQL exception simulation") }
                    fail("it should not reach this line")
                } catch (_: SQLException) {
                }
            }
            assertEquals(17, db.withContext { it.queryOne<Int>("select 17") })
        }
    }

    @Test
    fun testRawDecode() {
        testDb().inContext {
            data class OneTwo(val one: Int, val two: Int)
            val x = queryRow<OneTwo>("select 1 as one,2 as two")
            println(x)
        }
    }

}

fun assertEmpty(collection: Collection<*>) {
    if (collection.isNotEmpty())
        fail("expected empty collection, but it has ${collection.size} elements")
}



