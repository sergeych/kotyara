# KOTYARA library

__KOTlin-oriented Yet Another Relational-database Assistant__, e.g. __KOTYARA__ ;)

Se also [automatically generated documentation](https://code.sergeych.net/docs/kotyara/)

## Important information

- Current version is 1.5.4

- Since `1.5.1+` binary serialization is changed to [BiPack](https://code.sergeych.net/docs/mp_bintools/mp_bintools/net.sergeych.bipack/index.html) to reduce storage overhead in most cases. It is thus incompatible with existing serialized binary data. We will add support to old data soon, please add [a task here](https://gitea.sergeych.net/SergeychWorks/mp_bintools/issues) if you need it fast.

- Do not use `'x IN (?)'` condition, use `'x = any (?) '` instead!

- **Current stables are: 1.5.1 with new bnary packing and 1.4.4 for compatibility**: H2, postgres, any JDBC. Linux, Windows and Mac and like. Compatible with kotlin 1.9. Experimental is `1.5.1-SNPASHOT` which is (see above) not fully data-compatible

- for older kotlin versions, use 1.3.3. It works well with Linux and postgres and kotlin 1.7-1.8. Unfortunately, new
  features are not backport there.

## DB Compatibility

Kotyara works with both databases that provide `returning` in DML and also with more tradition systems returning last
inserted/processed keys. We use it with Postgres and H2 fairly lot.

## Installation

~~~

dependencies {
    //...
    // do not forget to supply jdbc:
    implementation("org.postgresql:postgresql:42.3.1") // use current version for the moment
    implementation("net.sergeych:kotyara:1.5.4")
}
~~~

## Usage

The main principle is to let the same agility that SQL gives without any complications and difficult and/or boilerplate
code, putting together database logic and the kotlin program logic. From our experience, separate .sql files are causing
errors and require more attention than having all the code in the same place. We still use .sql files for migrations
only.

The basic usage is as simple as:

```kotlin

val db: Database = TODO() // see samples below on how to

db.withConnection { dbc ->
    dbc.byId<SomeClassImplementingIdentifiable>(17)

    data class User(val id: Long, val name: String, val email: String)

    val user: User = dbc.updateQueryRow(
        "insert into users(name,email) values(?,?) returning *",
        "Sergeych", "somename@acme.com"
    )!!

    dbc.select<User>().where("name ilike ?", "serg*")
        .where("email != ?", "noreply@acme.com")
        .limit(10)
        .order("name")
        .all { user ->
            println(": $user")
        }
    dbc.update("delete from users where id = any(?)", setOf(1, 2, 3, 4, 5))
    val userId = dbc.queryOne("select id from users where name=?", "sergeych")
    val user1: User = dbc.queryRow("select * from users where id=?", userId)
    val userList1 = dbc.query<User>("select * from users where id < ?", 100)
    // or simpler
    val user2 = dbc.find<User>("name" to "sergeych")!!
    val user3 = dbc.find<User>("name = ?", "sergeych")

    // suppose you are ysing H2, mysql or whatever else where select can not return:
    val newId: Long = dbc.updateAndGetId(
        "insert into users(name,email) values(?,?)",
        "foo@bar.com", "Foobar"
    )
    // now we know it's id and can retreive it as usual:
    val newRecord: User = dbc.byId(newId)!!
}

```

See inline of DatabaseConnection and `select()` and more details below.

Some of our features:

- __Same API for coroutines and threads__: the API is the same: while it works slightly fater with coroutines, it is
  practically the same without it. The smart connection and coroutines dispatcher management prevents threads depleting
  and tries to free threads and connections as the load falls.

- __build-in support for almost all types__ including json/jsonb as strings amd kotlin enums as ordinals or names
  depending on column type

- __Built-in support for different read and write connections__ for heavy loaded data systems with
  write-master/read-slaves db setups.

- __Fast connection pool__ It has one simple and fast pool intended to detect some common pool usage errors (lake
  sharing pooled connections out of the usage context).

- __Built-in migrations support__ is being made by combining flyway and ActiveRecord approach, providing __versioned
  migrations__ and __repeating migrations__, also source code migrations and platofrm-agnostic recovery support for
  failed migrations, what means rolling back transactinos where DDL supports it (e.g. with postgres), and copying the
  whole database where postgres is not yet used. This, though, requires `Schema` implementations for particular
  platforms, though we will provide generic one.

- __out of box support for all basic types__ while creating objects from resultset

- __support for @Serializable types for JSON__ as fields using database JSON/JSONB columns

- __support for binary @Serializable__ for objects in fields of BYTEA oar like DB columns

- __Identifiable\<T>__ service class provides some support for references.

## Migrations

The simplest way to include migrations is to add resource directory named `db_migrations` with sql scriptst with the
usual naming convention:

| name               | meaning                    | order             |
|--------------------|----------------------------|-------------------|
| v1__<name>.sql     | first migration to perform | 1                 |
| v2__<name>.sql     | second migration...        | 2                 |
| r_<repeatable>.sql | repeatable migration       | afer all numbered |

E.g. migrations with file name like `v<int>__<any_file_name>.sql` are performed first and in or its number. Mogrations
starting with `r__` are named _repeatable_. It neabs these are performed every time the scrip content is changed.
Repeatable migrations are conenient to define test data or stored functions, triggers, etc.

Numbered migrations are also checked for changes and if some already performed migration is changed, the migrator throws
exception.

Sample engine initialization with migrations:

~~~kotlin
    val db = run {
    Class.forName("org.postgresql.Driver")
    Database(Config.dbConnectionUrl).also {
        it.migrateWithResources(Application::class.java, PostgresSchema())
    }
}
val five = db.withContext { it.queryOne<Int>("select 5") }
~~~ 

### Migration script structure

Simple database commands could be just written sequentially, as long as the actual command ends with `;\n`. The script
parser is rather simple and does not analyse SQL dialect syntax, so in the complex cases, like defining stored
procedures, you should put each complex command between `-- begin block --` and `-- end block ``` lines. The block
inside is treated as a sinle SQL command. Here is the real world sample:

~~~sql
alter table purchases
    add column customer_id bigint references customers (id) on delete cascade;

-- begin block --

CREATE OR REPLACE FUNCTION random_id(
    length integer
)
    RETURNS text AS
$body$
WITH chars AS (SELECT unnest(string_to_array(
        'A B C D E F G H I J K L M N O P Q R S T U V W X Y Z 0 1 2 3 4 5 6 7 8 9 _ q w e r t y u i o p a s d f g h j k l z x c v b n m',
        ' ')) AS _char),
     charlist AS
         (SELECT _char
          FROM chars
          ORDER BY random()
          LIMIT $1)
SELECT string_agg(_char, '')
FROM charlist
    ;
$body$
    LANGUAGE sql;

-- end block --

alter table binary_data
    add column public_id varchar not null default random_id(31);

~~~

Note that complex procedure code is inside the block, and simple SQL commands are just entered outside.

Such a simple parser allows kotara to remail small and fast.

### Separate migrations for tests

Just put a test set of migrations in the test resources.

### Kotlin migrations code

The `Schema` instance (in the sample above, `PostgresSchema`) has methods:

~~~
typealias MigrationHandler = (DbContext) -> Unit
class Schema {
    // ...
    fun before(version: Int, handler: (MigrationHandler)): Schema {...}
    fun after(version: Int, handler: MigrationHandler): Schema { ... }
~~~

allowing registering pre- and post-migration hooks. Just do it _before_ performing migrations.

## Coroutines

Our main goal was the postgres, and postgres is not threaded, it spawn a process per connection, and the connection
naturally should be used by one thread at a time, so there is no big deal to loet coroutines share connections. Also,
the number of connections determines number of threads to process them effectively using coroutines. Also, it;s a big
problem in heavy loaded environments that limited connections could block all application threads stalling the whole
system.

In spite of this, kotyara uses coroutines internally controlling by a dispatcher which threadpool is limited and its
capacity automatically adjusted as number of active connections changes. Therefore, code calling `Database`'s method

    suspend fun <T>asyncDb(block: suspend (DbContext)->T)` 

which is the preferred point to start all DB processing from, _will not block calling thread bu suspend it until kotyara
performs requested tasks_.

There is also blocking variant of this interface:

    suspend fun <T>asyncDb(block: (DbContext)->T)`

But it is just proxying to suspend one above.

Kotyara executes the `block` above using own coroutine dispatcher which has limited number of thread constantly adjusted
depending on the number of active database connections, which are also allocated dynamically.

We recommend using suspend variant if your code uses coroutines.

Kotyara does not use threads internally since v0.3.0.

## Connection management

When creating database it is possible to specify maximum number of retained connections. Retained means that kotyara
will not free allocated connections below this count.

Kotaya allocates new connections per-request basis and reuses them using fast coroutine-optimized pooling. It means, if
there are no free connections in the pool, it will allocate new one and put it to the pool. Kotyara can have more
connections than maximum number of retained connections on the peak load, and will reclaim them when the load drops.

Still, the number of connection can't grow infintely. When it gets twice as mach as maximum retained number, it will not
allocate new one but await or throw exception.

When the number of active connection changes, kotyara adjust number of threads in its coroutine dispatched pool to
provide just enough parallelism processing all the connections in parallel. This means, after the peak load, kotyara
will release both connection and OS threads.

We will add a separate parameter later to control the maximum number of allocated connections as well.

# Some history

## 1.3.3

You can easily add you own converters now, or mark existing classes for JSON serializations. It is as simple as that:

```kotlin
// custom converter, part od Db
inline fun <reified T : Any> asJson() {
    register({
        Json.decodeFromString<T>(getString(column))
    }, {
        setObject(column, Json.encodeToString(it), OTHER)
    })
}

// registry is a part of te Database class, and it also 
// allows marking existing classes for json:
@Serializable
data class JSFoo(val foo: String, val bar: Int)

val database: Database = openDatabase()
database.registry.asJson<JSFoo>()
```

Type registry is also available in `DbConnection` as `registry` or, for compatibility reasons, `converter`.

Note that the registry is per-database, converter added for the connection will be immediately avaiable in all existing
and future connections. Types registry is thread-safe.

## 1.3.1-SNAPSHOT

- Added support for automatic JSON de/serialization of fields using `DbJson` annotation. Just mark your serializable
  class with it and use `varchar`, `json`, `jsonb` or like columnt in your table. Here is the sample:

```kotlin
@Serializable
@DbJson // important!
data class JSFoo(val foo: String, val bar: Int)

val f1 = JSFoo("foobar", 42)

testDb().withContext { dbc ->
    assertEquals(
        """{"foo":"foobar","bar":42}""",
        dbc.queryOne<String>("select ?", f1)
    )
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
    val id = dbc.updateQueryOne<Int>("insert into dbjson_test(encoded) values(?) returning id", f1)!!
    val d2 = dbc.queryOne<JSFoo>("select encoded from dbjson_test where id=?", id)
    assertEquals(f1, d2)
}

```

Serialization to JSON with JSON column type let us use encoded data fields in database queries (where DB supports json).

Note that unmarked classes will be serialized with BOSS as before and require `bytea` column.

## 1.2.10

- Support for kotlinx.datetime types: `Instant`, `LocalDateTime` and `LocalDate`

## 1.2.7 release

This release is supposed to be productino stable with many new features. It is compiled with __java 1.8__ to get best
bytecode compatibility and contain amny syntax sugar additions and better types compatibility. Some of the changelog:

- `Relation.join` and `.addJoin` to simple and convenient add joined tables to `dbc.select()`
- `Relation.include` as syntax sugar for join for one-to-namy case
- Relation now has tracing `toString()` implementation that contain generated SQL and list of parameters

## 1.2 experimental:

This is a 1.2 branch, main points:

- support of most scalar adn popular java date/time types
- Boss encoding if unknown parameter types (bytea is expected)
- Added `Identifiable<T>` record type with untility functions like `byId(), reload() and destroy()`
- Added `hasMany` and `hasOne` tools to work with `Identifiable` records.

Kotyara is an attempt to provide simpler and more kotlin-style database interface than other systems with "battary
included" principle. It was influenced by simplicity of scala's ANROM library. Pity kotlin has no language features to
mimic it at a larger extent.

Especially if you get strange error like `java.lang.NoSuchMethodError: 'java.util.List java.util.stream.Stream.toList()`

## Incompatible changes in `1.1.*`

- Logging support is migrated to effective async logger from [mp_stools](https://github.com/sergeych/mp_stools). It is
  fatser, more robust especially due to some known problems with systemd journal, and more feature rich. Old logging
  tools are mostly removed.

## Extensions

- support for most simple types, enums as integers or strings
- improved stability on errors and cancellations in coroutines (asynContext)

Enhancements

- added user conversions [DbTypeConverter] interface could be can be added as an optional parameter of `Database()`
  constructor
- support for java.time.Instant
- few bugs fixed

## Nearest plans

We completed our roadmap with a great success, kotyara purrs in many commercials projects and looks great. Will improve
speed and add more syntax sugar for kotlinx.serialized types. Write me a feature request in issues!

## Usage notes

Please be informed that using of the migrator as a separate part from the database is considered rewriting, as database
pausing with real database drivers cause hangups, so we are moving to explicetely and mandatorlily migrate any Database
instance pror to any usage.




