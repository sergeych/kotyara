@file:Suppress("unused")

package net.sergeych.kotyara.db

import net.sergeych.kotyara.DbException
import net.sergeych.kotyara.NotFoundException
import net.sergeych.tools.camelToSnakeCase
import kotlin.reflect.KClass

interface Identifiable<T> {
    val id: T
}

typealias IdentifiableRecord = Identifiable<Long>

inline fun <reified T : IdentifiableRecord>T.reload(dbc: DbContext): T = dbc.byId<T>(id) ?: throw NotFoundException()

inline fun <reified T : Identifiable<*>>T.destroy(dbc: DbContext) {
    val name = T::class.simpleName?.camelToSnakeCase()?.toTableName()
    dbc.update("delete from $name where id = ?", id)
}

inline fun <reified T : Identifiable<*>>T.forUpdate(dbc: DbContext): T =
    dbc.byIdForUpdate<T>(id as Any) ?: throw NotFoundException()

class HasOne<I,T: Identifiable<I>>(private val cls: KClass<T>, private val idProvider: () -> I?) {

    private var cachedValue: T? = null
    private var cachedId: I? = null

    private fun relation(dbc: DbContext, forUpdate: Boolean): T? {
        val id = idProvider()
        if (id == null) return null
        if (id == cachedId && cachedValue != null) return cachedValue
        var rel = Relation(dbc, cls).where("id = ?", id)
        if (forUpdate) rel = rel.forUpdate()
        return rel.first.also { cachedValue = it; cachedId = id }
    }

    fun get(dbc: DbContext): T? =
        relation(dbc, false)

    fun getOrThrow(dbc: DbContext): T =
        relation(dbc, false) ?: throw NotFoundException()

    fun forUpdate(dbc: DbContext): T? =
        relation(dbc, true)

    fun clearCache(): HasOne<I,T> {
        cachedId = null
        cachedValue = null
        return this
    }

    fun override(value: T) {
        cachedValue = value
        cachedId = value.id
    }
}

class HasMany<T : IdentifiableRecord>(private val cls: KClass<T>, private val idProvider: Relation<T>.() -> Unit) {

    private var cachedValue: List<T>? = null
    private var cachedSql: String? = null

    fun all(dbc: DbContext, forUpdate: Boolean = false, builder: (Relation<T>.() -> Unit)? = null): List<T> {
        cachedValue?.let { return it }
        return relation(dbc, forUpdate, builder).all.also { cachedValue = it }
    }

    fun relation(dbc: DbContext, forUpdate: Boolean = false, builder: (Relation<T>.() -> Unit)? = null): Relation<T> {
        var rel = Relation(dbc, cls)
        rel.idProvider()
        builder?.let { rel.it() }
        if (forUpdate) rel = rel.forUpdate()
        return rel
    }

    fun get(dbc: DbContext): List<T> =
        all(dbc, false)

    fun filter(dbc: DbContext,forUpdate: Boolean = false,builder: Relation<T>.()->Unit): List<T> =
        all(dbc, forUpdate, builder)

    fun forUpdate(dbc: DbContext): List<T> =
        all(dbc, true)

    fun clearCache(): HasMany<T> {
        cachedValue = null
        return this
    }
}

inline fun <reified T: Identifiable<Long>> hasOne(noinline idProvider: () -> Long?) =
    HasOne(T::class, idProvider)

inline fun <reified T: Identifiable<Int>> hasOneInt(noinline idProvider: () -> Int?) =
    HasOne(T::class, idProvider)

inline fun <reified T : IdentifiableRecord> hasMany(noinline idProvider: Relation<T>.() -> Unit) =
    HasMany(T::class, idProvider)

/**
 * Perform sqlClause as update clause using `args` and check that affected rows count exatcly
 * matches expected
 * @param sqlClause like in [DbContext.update]
 * @param args parameters for sql clasue like in [DbContext.update]
 * @parame expectedCount expected number of affected rows
 * @throws DbException if affected rows is different, or if update operation otherwise fails.
 */

fun DbContext.updateCheck(expectedCount: Int,sqlClause: String,vararg args: Any) {
    val count = update(sqlClause, *args)
    if( expectedCount != count )
        throw DbException("expected $expectedCount affected records, got $count")
}
