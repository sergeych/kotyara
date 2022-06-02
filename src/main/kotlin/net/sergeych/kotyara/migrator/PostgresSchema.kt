package net.sergeych.kotyara.migrator

import net.sergeych.kotyara.Schema
import net.sergeych.kotyara.db.DbContext
import net.sergeych.mp_logger.debug

class PostgresSchema : Schema(true) {

    override fun prepareMigrationsTable(cxt: DbContext) {
        debug { "preparing migrations table" }
        cxt.executeAll(
            """
                    create table if not exists $migrationsTable(
                        name varchar not null primary key,
                        version int,
                        hash varchar,
                        performed_at timestamp not null default now()
                    );
                """.trimIndent()
        )
    }

    override fun onFailure() {
        // its ok should be rolled back
    }
}