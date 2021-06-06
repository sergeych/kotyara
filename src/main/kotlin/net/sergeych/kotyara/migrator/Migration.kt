package net.sergeych.kotyara.migrator

import java.lang.NumberFormatException
import java.security.MessageDigest
import java.util.*

/**
 * Parse migration specified by name (e.g. filename) and contents (sql). Calculate its hash and
 * represent it as versionned ir repeatable.
 */
class Migration(val name: String, val sql: String) {

    val hash by lazy {
        Base64.getEncoder().encodeToString(hashDigest.digest(sql.toByteArray()))
    }

    val version: Int?

    val isRepeatable: Boolean

    override fun toString(): String {
        return name
    }

    init {
        var v: Int? = null
        try {
            if (!name.lowercase().startsWith("r__")) {
                reVersioned.find(name)?.let {
                    v = it.groupValues[0].toInt()
                } ?: throw MigrationException("illegal migration name: $name")
            }
        } catch (nf: NumberFormatException) {
            throw MigrationException("illegal migration number: $name")
        }
        version = v
        isRepeatable = version == null
    }

    companion object {
        val reVersioned = "v(\\d+)__".toRegex(RegexOption.IGNORE_CASE)
        val hashDigest = MessageDigest.getInstance("SHA-256")
    }
}