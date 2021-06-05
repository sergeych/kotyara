package net.sergeych.kotyara

import net.sergeych.tools.Loggable
import net.sergeych.tools.TaggedLogger
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.RuntimeException

typealias MigrationHandler = (Database) -> Unit

abstract class Schema(protected val db: Database, private val useTransactions: Boolean)
    : Loggable by TaggedLogger("SCHM"){

    class MigrationException(text:String="failed to migrate the schema",reason: Throwable?=null)
        : DbException(text,reason)

    private val beforeHandlers =  HashMap<Int,MigrationHandler>()
    private val afterHandlers =  HashMap<Int,MigrationHandler>()

    fun before(version: Int, handler: MigrationHandler): Schema {
        if( version < 1) throw IllegalArgumentException("bad version number: $version")
        if( beforeHandlers.containsKey(version) )
            throw IllegalArgumentException("beforeHandler for version $version is already set")
        beforeHandlers[version] = handler
        return this
    }

    fun after(version: Int, handler: MigrationHandler): Schema {
        if( version < 1) throw IllegalArgumentException("bad version number: $version")
        if( afterHandlers.containsKey(version) )
            throw IllegalArgumentException("beforeHandler for version $version is already set")
        afterHandlers[version] = handler
        return this
    }

    open fun beforeAll() {}
    open fun afterAll() {}

    open fun rollbackAll() {
        throw RuntimeException("migration rollback is not supported")
    }

    protected abstract fun prepareMigrationsTable()

    var currentVersion = 0

    fun migrate() {
        info("Schame migrstion started: ${this::class.qualifiedName}")
        debug("preparing migrations")
        beforeAll()
        try {
            db.closeAllConnections()
            prepareMigrationsTable()
            db.inContext {
                currentVersion =
                    queryOne("select version from $migrationsTable order by version desc limit 1") ?: 0
                debug("current version is $currentVersion")
            }
            afterAll()
        }
        catch(x: Exception) {
            error("Error performing migrations", x)
            if( !useTransactions ) {
                warning("DDL transactions are not supported, trying to rollback All")
                rollbackAll()
            }
            throw MigrationException("migrations failed", x)
        }
    }

    companion object {
        val migrationsTable = "__performed_migrations"
    }

}