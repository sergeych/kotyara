package net.sergeych.kotyara.migrator

import net.sergeych.kotyara.Database
import net.sergeych.kotyara.db.DbContext
import net.sergeych.kotyara.Schema

class PostgresSchema(db: Database) : Schema(db, true) {

    override fun prepareMigrationsTable(cxt: DbContext) {
        debug("preparing migrations table")
        cxt.executeAll(
            """
                    create table if not exists $migrationsTable(
                        name varchar not null primary index,
                        version int,
                        hash varchar,
                        performed_at timestamp not null default now()
                    );
                """.trimIndent()
        )
    }
}