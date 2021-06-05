package net.sergeych.kotyara.migrator

import net.sergeych.kotyara.DbException

class MigrationException(text: String = "failed to migrate the schema", reason: Throwable? = null) :
    DbException(text, reason)