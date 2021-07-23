# KOTYARA library

> Incoming changes wadning.

Pleas be informed that using of the migrator as a separate part from the database is considered rewriting, as database pausing with real database drivers
cause hangups, so we are moving to explicetely and mandatorlily migrate any Database instance pror to any usage.

Following a known naming tradition:

__KOTlin-oriented Yet Another Relational-database Assistant__, e.g. __KOTYARA__ ;)

> this library is in alfa stage. Interfaces could be changed. It is internally used in pbeta-production sites, using Postgres JDBC connections.

Kotyara is an attempt to provide simpler and more kotlin-style database interface than other systems with "battary included" principle.

The idea is to let the same agility that provides scala anorm library without precompiling SQL as sqldelight does, lieaving all database logic conveniently put together in the kotlin source files.

__Built-in support for different read and write connections__ for heavy loaded data systems with write-master/read-slaves db setups.

__Fast connection pool__ It has one simple and fast pool intended to detect some common pool usage errors (lake sharing pooled connections out of the usage context).

__Built-in migrations support__ is being made by combining flyway and ActiveRecord approach, providing __versioned migrations__ and __repeating migrations__, also source code migrations and platofrm-agnostic recovery support for failed migrations, what means rolling back transactinos where DDL supports it (e.g. with postgres), and copying the whole database where postgres is not yet used. This, though, requires `Schema` implementations for particular platforms, though we will provide generic one.



