package net.sergeych.kotyara

import net.sergeych.kotyara.migrator.MigrationException
import net.sergeych.kotyara.migrator.Migrations
import net.sergeych.tools.Loggable
import net.sergeych.tools.TaggedLogger
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.time.Duration
import kotlin.collections.HashMap
import kotlin.io.path.Path
import kotlin.io.path.name

typealias MigrationHandler = (DbContext) -> Unit

abstract class Schema(private val externalDb: Database, private val useTransactions: Boolean) :
    Loggable by TaggedLogger("SCHM") {

    private val beforeHandlers = HashMap<Int, MigrationHandler>()
    private val afterHandlers = HashMap<Int, MigrationHandler>()

    fun before(version: Int, handler: MigrationHandler): Schema {
        if (version < 1) throw IllegalArgumentException("bad version number: $version")
        if (beforeHandlers.containsKey(version))
            throw IllegalArgumentException("beforeHandler for version $version is already set")
        beforeHandlers[version] = handler
        return this
    }

    fun after(version: Int, handler: MigrationHandler): Schema {
        if (version < 1) throw IllegalArgumentException("bad version number: $version")
        if (afterHandlers.containsKey(version))
            throw IllegalArgumentException("beforeHandler for version $version is already set")
        afterHandlers[version] = handler
        return this
    }

    open fun beforeAll() {}
    open fun onSuccess() {}

    open fun onFailure() {
        throw RuntimeException("migration rollback is not supported")
    }

    protected abstract fun prepareMigrationsTable(cxt: DbContext)

    var currentVersion = 0

    fun migrateFromResources(resourcePath: String = "db/migrations") {
        val rr = javaClass.classLoader.getResources(resourcePath+"/*.sql").toList()
        debug("Found migration resources: $rr")
        migrate(
            Migrations(rr.map {
                debug("found: ${Path(it.path).name}")
                Migrations.Source(Path(it.path).name, it.readText())
            })
        )
    }

    fun migrate(migrations: Migrations) {
        externalDb.closeAllContexts(Duration.ofMinutes(3)) { db ->
            debug("starting migrations")
            // note beforeall is called before any connection will be created
            // and afler all connections (contexts) will be closed
            beforeAll()
            try {
                db.withContext { performMigrations(it, migrations) }
                // migrations passed, just fine
                db.closeAllContexts(Duration.ofMinutes(3))
                // and again, after all is called when everything is done and all connections (contexts) are
                // closed
                onSuccess()
            } catch (x: Exception) {
                // migrations failed. now we might need to restore dabase from a file copy, if DDL transactions
                // are not supported. Cleanup and rethrow
                db.closeAllContexts(Duration.ofMinutes(3))
                onFailure()
                throw x
            }
        }
    }

    private fun performMigrations(cxt: DbContext, migrations: Migrations) {
        for (m in migrations.toPerform(cxt)) {
            try {
                cxt.transaction {
                    debug("performing $m")
                    m.version?.let { v ->
                        beforeHandlers[v]?.let {
                            debug("calling before-handler for version $v")
                            it(cxt)
                            debug("executed before-handler for version $v")
                        }
                    }
                    debug("executing migration sql")
                    cxt.executeAll(m.sql)
                    debug("executed migration sql")
                    m.version?.let { v ->
                        afterHandlers[v]?.let {
                            debug("calling after-handler for version $v")
                            it(cxt)
                            debug("executed after-handler for version $v")
                        }
                    }
                    cxt.update("drop if exists from ${migrationsTable} where name=?", m.name)
                    cxt.update(
                        "insert into ${migrationsTable}(name, version, hash) values(?,?,?)",
                        m.name, m.version, m.hash
                    )
                    debug("migration performed: $m")
                }
            } catch (x: Exception) {
                error("migration $m failed", x)
                throw MigrationException("migration failed: $m", x)
            }
        }
    }

    companion object {
        val migrationsTable = "__performed_migrations"
    }
}

