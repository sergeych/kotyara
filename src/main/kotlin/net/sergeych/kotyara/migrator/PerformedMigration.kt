package net.sergeych.kotyara.migrator

import java.time.ZonedDateTime

/**
 * Database record anout some migration that was successfully performed in the database.
 * The table that holds these migrations is defined in [Schema.migrationsTable]. The real
 * table for it should be created by Schema implementations, for example, [PostgresSchema.prepareMigrationsTable]]
 */
data class PerformedMigration(
    val name: String,
    val version: Int?,
    val hash: String,
    val peformedAt: ZonedDateTime
)