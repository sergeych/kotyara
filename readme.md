# KOTYARA library

__KOTlin-oriented Yet Another Relational-database Assistant__, e.g. __KOTYARA__ ;)

> this library is in production stage (postgres). Few interfaces could be changed. It is internally used in pbeta-production sites, with Postgres JDBC connections.

Kotyara is an attempt to provide simpler and more kotlin-style database interface than other systems with "battary included" principle. It was influenced by simplicity of scala's ANROM library. Pity kotlin has no language features to mimic it at a larger extent.

> Stable version: __1.0.5__ please use this or later

Especially if you get strange error like `java.lang.NoSuchMethodError: 'java.util.List java.util.stream.Stream.toList()`


## Installation

Add is a dependency, for example, to the build.gradle.kts is should be like:

~~~

dependencies {
    //...
    // do not forget to supply jdbc:
    implementation("org.postgresql:postgresql:42.3.1")
    implementation("net.sergeych:kotyara:1.0.5")
}
~~~

## In depth

The main principle is to let the same agility that SQL gives without any complications and difficult and/or boilerplate code, putting together database logic and the kotlin program logic. From our experience separate .sql files are provocating errors and require more attention than having all the code in the same place.

Some of our features:

- __Same API for coroutines and threads__: the API is the same: while it works slightly fater with coroutines, it is practically the same without it. The smart connection and coroutines dispatcher management prevents threads depleting and tries to free threads and connections as the load falls.

- __build-in support for almost all types__ including json/jsonb as strings amd kotlin enums as ordinals or names depending on column type

- __Built-in support for different read and write connections__ for heavy loaded data systems with write-master/read-slaves db setups.

- __Fast connection pool__ It has one simple and fast pool intended to detect some common pool usage errors (lake sharing pooled connections out of the usage context).

- __Built-in migrations support__ is being made by combining flyway and ActiveRecord approach, providing __versioned migrations__ and __repeating migrations__, also source code migrations and platofrm-agnostic recovery support for failed migrations, what means rolling back transactinos where DDL supports it (e.g. with postgres), and copying the whole database where postgres is not yet used. This, though, requires `Schema` implementations for particular platforms, though we will provide generic one.

## Migrations

The simplest way to include migrations is to add resource directory named `db_migrations` with sql scriptst with the usual naming convention:

| name               | meaning                    | order             |
|--------------------|----------------------------|-------------------|
| v1__<name>.sql     | first migration to perform | 1                 |
| v2__<name>.sql     | second migration...        | 2                 |
| r_<repeatable>.sql | repeatable migration       | afer all numbered |

E.g. migrations with file name like `v<int>__<any_file_name>.sql` are performed first and in or its number. Mogrations starting with `r__` are named _repeatable_. It neabs these are performed every time the scrip content is changed. Repeatable migrations are conenient to define test data or stored functions, triggers, etc.

Numbered migrations are also checked for changes and if some already performed migration is changed, the migrator throws exception.

Sample emgine initialization with migrations:

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

Simple database commands could be just written sequentially, as long as the actual command ends with `;\n`. The script parser is rather simple and does not analyse SQL dialect syntax, so in the complex cases, like defining stored procedures, you should put each complex command between `-- begin block --` and `-- end block ``` lines. The block inside is treated as a sinle SQL command. Here is the real world sample:

~~~sql
alter table purchases
    add column customer_id bigint references customers (id) on delete cascade;

-- begin block --

CREATE OR REPLACE FUNCTION random_id(
    length integer
)
    RETURNS text AS
$body$
WITH chars AS (
    SELECT unnest(string_to_array(
            'A B C D E F G H I J K L M N O P Q R S T U V W X Y Z 0 1 2 3 4 5 6 7 8 9 _ q w e r t y u i o p a s d f g h j k l z x c v b n m',
            ' ')) AS _char
),
     charlist AS
         (
             SELECT _char
             FROM chars
             ORDER BY random()
             LIMIT $1
         )
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

Our main goal was the postgres, and postgres is not threaded, it spawn a process per connection, and the connection naturally should be used by one thread at a time, so there is no big deal to loet coroutines share connections. Also, the number of connections determines number of threads to process them effectively using coroutines. Also, it;s a big problem in heavy loaded environments that limited connections could block all application threads stalling the whole system.

In spite of this, kotyara uses coroutines internally controlling by a dispatcher which threadpool is limited and its capacity automatically adjusted as number of active connections changes. Therefore, code calling `Database`'s method 

    suspend fun <T>asyncDb(block: suspend (DbContext)->T)` 

which is the preferred point to start all DB processing from, _will not block calling thread bu suspend it until kotyara performs requested tasks_.

There is also blocking variant of this interface:

    suspend fun <T>asyncDb(block: (DbContext)->T)`

But it just proxies to suspending one above.

Kotyara executes the `block` above using own coroutine dispatcher which has limited number of thread constantly adjusted depending on the number of active database connections, which are also allocated dynamically.

We recommend using suspend variant if your code uses coroutines.

Kotyara does not use threads internally since v0.3.0.

## Connection management

When creating database it is possible to specify maximum number of retained connections. Retained means that kotyara will not free allocated connections below this count.

Kotaya allocates new connections per-request basis and reuses them using fast coroutine-optimized pooling. It means, if there are no free connections in the pool, it will allocate new one and put it to the pool. Kotyara can have more connections than maximum number of retained connections on the peak load, and will reclaim them when the load drops.

Still, the number of connection can't grow infintely. When it gets twice as mach as maximum retained number, it will not allocate new one but await or throw exception.

When number of active connection changes, kotyara adjust number of threads in its coroutine dispatched pool to provide just enough parallelism processing all the connections in parallel. This means, after peak load kotyara will release both connection and OS threads.

We will add a separate parameter later to control maximum number of allocated connections as well.

## Latest changes

- support for most simple types, enums as integers or strings
- improved stability on errors and cancellations in coroutines (asynContext)

Enhancements

- added user conversions [DbTypeConverter] interface could be  can be added as an optional parameter of `Database()` constructor
- support for java.time.Instant
- few bugs fixed

## Nearest plans

As 0.3.+ already supports coroutines very well, our plans are:

- move references (hasOne) here from our other projects.

- create basic documentation

- add coroutine wraps for postgres `LISTEN` according to their documentation (really strange)

- add flows support to queries

We dropped plans to use serialization for fields as it provides more problem than profut and usage ease. 

- prepare to switch to kotlinx serialization in 1.0. It is not easy and will break compatibility, and we hope to see some important features in kotlinx first, as for now reflection though slow and big saves the day with converting data to and from database columns.

## Usage notes

Please be informed that using of the migrator as a separate part from the database is considered rewriting, as database pausing with real database drivers cause hangups, so we are moving to explicetely and mandatorlily migrate any Database instance pror to any usage.




