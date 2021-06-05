# KOTYARA library

Following a known naming tradition:

__KOTlin-oriented Yet Another Relational-database Assistant__, e.g. __KOTYARA__ ;)

> this library is under initial development, not ready for evaluation.

Kotyara is an attempt to provide simpler and more kotlin-style database interface than other systems.

The idea is to let the same agility that provides scala anorm library without precompiling SQL as sqldelight does, lieaving all database logic conveniently put together in the kotlin source files.

It also has built-in support for different read and write connections for heavy loaded data systems with write-master/read-slaves db setups.

It has owne simple and fast pool intended to detect some common pool usage errors (lake sharing pooled connections out of the usage context).

Build-in migrations support is being made by combining flyway and ActiveRecord approach, providinv versioned migrations, repeating migrations, source code migrations and platofrm-agnostic recovery support for failed migrations, what means rolling back transactinos where DDL supports it (e.g. with postgres), and copying the whole database where postgres is not yet used. This, though, requires `Schema` implementations for particular platforms, though we will provide generic one.


