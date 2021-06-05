package net.sergeych.kotyara

class PostgresSchema(db: Database) : Schema(db, true) {

    override fun prepareMigrationsTable() {
        debug("preparing migrations table")
        db.inContext {
            executeAll(
                """
                create table if not exists $migrationsTable(
                    id bigserial not null primary key,
                    name varchar not null unique,
                    version int,
                    hash varchar,
                    performed_at timestamp not null default now()
                );
            """.trimIndent()
            )
        }
    }
}