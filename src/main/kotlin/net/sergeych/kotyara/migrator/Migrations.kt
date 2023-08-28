package net.sergeych.kotyara.migrator

import net.sergeych.kotyara.Schema
import net.sergeych.kotyara.db.DbContext
import net.sergeych.mp_logger.LogTag
import net.sergeych.mp_logger.info

/**
 * Set of migrations from some source, like list of files or embedded resources.
 */
class Migrations(source: Iterable<Source>) : LogTag("MSRC") {

    data class Source(val name: String, val sql: String)

    val versions: Map<Int, Migration>
    val repeatables: List<Migration>
    val lastVersion: Int

    /**
     * Check that performed versionaed migrations saved in the database were not altered in the current
     * set of migrations. Note that it does not check repeting migrations.
     *
     * @throws MigrationException if already performed versioned migrations were altered
     */
    @Suppress("unused")
    fun checkIntegrity(cxt: DbContext) {
        cxt.query<PerformedMigration>("select * from ${Schema.migrationsTable} order by name")
            .forEach { m1 ->
                m1.version?.let { v ->
                    val m2 = versions[v]
                    if (m2 == null)
                        throw MigrationException("migration ${m1.name} does not exists anymore")
                    if (m1.hash != m2.hash)
                        throw MigrationException("migration ${m2.name} has been altered")
                }
            }
    }

    /**
     * Build a properly ordered list of migrations to be executed to synchronize database. It consists of version
     * migrations that are above current, then changed and new repeating migrations sorted ny name.
     * @param cxt context of the database to be migrated
     * @return possibly empty list of migrations to apply
     */
    fun toPerform(cxt: DbContext): List<Migration> {
        // first, versioned migrations:
        val dbVersion =
            cxt.queryOne("select version from ${Schema.migrationsTable} where version is not null order by version desc limit 1") ?: 0
        if (dbVersion < 0 || dbVersion > lastVersion)
            throw MigrationException("versions inconsistency: db version is $dbVersion and migration version is $lastVersion")
        val migrationsToPerform = mutableListOf<Migration>()
        for (v in (dbVersion + 1)..lastVersion) {
            migrationsToPerform.add(versions[v] ?: throw MigrationException("missing required migration version=$v"))
        }

        val repetableHashes = cxt.queryWith(
            "select hash from ${Schema.migrationsTable} where version is null") {
            it.getString(1)
        }
            .toSet()
        migrationsToPerform.addAll(
            repeatables.sortedBy { it.name }.filter { it.hash !in repetableHashes }
        )
        if( migrationsToPerform.size == 0)
            info { "no migrations needed" }
        else
            info { "${migrationsToPerform.size} migration(s) to perform" }
        return migrationsToPerform
    }

    init {
        val rr = mutableListOf<Migration>()
        val vv = mutableMapOf<Int, Migration>()
        for (s in source) {
            val m = Migration(s.name, s.sql)
            if (m.version == null) rr.add(m)
            else vv[m.version] = m
        }
        versions = vv
        repeatables = rr.sortedBy { it.name }
        lastVersion = vv.keys.maxOrNull() ?: 0
    }
}