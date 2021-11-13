# KOTYARA library

__KOTlin-oriented Yet Another Relational-database Assistant__, e.g. __KOTYARA__ ;)

> this library is in alfa stage. Interfaces could be changed. It is internally used in pbeta-production sites, using Postgres JDBC connections.

Kotyara is an attempt to provide simpler and more kotlin-style database interface than other systems with "battary included" principle.

The idea is to let the same agility that provides scala anorm library without precompiling SQL as sqldelight does, lieaving all database logic conveniently put together in the kotlin source files.

__build-in support for almost all types__ including json/jsonb as strings amd kotlin enums as ordinals or names depending on column type

__Built-in support for different read and write connections__ for heavy loaded data systems with write-master/read-slaves db setups.

__Fast connection pool__ It has one simple and fast pool intended to detect some common pool usage errors (lake sharing pooled connections out of the usage context).

__Built-in migrations support__ is being made by combining flyway and ActiveRecord approach, providing __versioned migrations__ and __repeating migrations__, also source code migrations and platofrm-agnostic recovery support for failed migrations, what means rolling back transactinos where DDL supports it (e.g. with postgres), and copying the whole database where postgres is not yet used. This, though, requires `Schema` implementations for particular platforms, though we will provide generic one.

## Migrations

The simplest way to include migrations is to add resource directory named `db_migrations` with sql scriptst with the usual naming convention:

| name | meaning | order |
|------|---------|-------|
|v1__<name>.sql| first migration to perform | 1 |
|v2__<name>.sql| second migration...| 2 |
|r_<repeatable>.sql| repeatable migration | afer all numbered |

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

## Nearest plans

A new major version will be a reqrite to async db connection (no more JDBC) and kotlinx serialization library (no more reflect). Should be much faster but source-incompatible. The interface will be kept almost the same, but with suspend functions and flows instead lists where appropriate.

## Usage notes

Please be informed that using of the migrator as a separate part from the database is considered rewriting, as database pausing with real database drivers cause hangups, so we are moving to explicetely and mandatorlily migrate any Database instance pror to any usage.

Following a known naming tradition:



