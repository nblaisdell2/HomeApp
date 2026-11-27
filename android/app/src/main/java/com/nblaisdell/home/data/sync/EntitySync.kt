package com.nblaisdell.home.data.sync

import com.nblaisdell.home.data.local.ShoppingItemDao
import com.nblaisdell.home.data.local.ShoppingItemEntity
import com.nblaisdell.home.data.local.StoreDao
import com.nblaisdell.home.data.local.StoreEntity
import com.nblaisdell.home.data.local.SyncStateDao
import com.nblaisdell.home.data.local.SyncStateEntity
import com.nblaisdell.home.data.local.TransactionRunner
import com.nblaisdell.home.data.remote.bodyOrThrow
import com.nblaisdell.home.data.remote.generated.api.ShoppingApi
import com.nblaisdell.home.data.remote.generated.model.ShoppingItem
import com.nblaisdell.home.data.remote.generated.model.ShoppingItemUpsert
import com.nblaisdell.home.data.remote.generated.model.Store
import com.nblaisdell.home.data.remote.generated.model.StoreUpsert
import java.util.UUID

/**
 * Sync for one table: [push] uploads pending local edits, [pull] fetches everything the server
 * changed since this table's cursor. [SyncEngine] runs every push before any pull.
 */
interface EntitySync {
    suspend fun push()
    suspend fun pull()
}

/** One uploaded row's outcome, and one page of server changes. */
internal data class Uploaded<R>(val applied: Boolean, val row: R)
internal data class ChangePage<R>(val rows: List<R>, val cursor: Long, val hasMore: Boolean)

/**
 * The push/pull loop shared by every synced table. Subclasses only map rows to and from the
 * API; the rules live here once:
 * - an accepted upload is marked synced only if the row wasn't edited again meanwhile;
 * - a rejected upload adopts the server's winning row, under the same condition;
 * - each pulled page is merged and its cursor saved in one transaction.
 */
internal abstract class CursorSync<L, R>(
    private val syncStateDao: SyncStateDao,
    private val transaction: TransactionRunner,
    private val cursorKey: String,
) : EntitySync {
    protected abstract suspend fun pending(): List<L>
    protected abstract fun L.key(): String
    protected abstract fun L.modified(): Long
    protected abstract suspend fun getLocal(id: String): L?
    protected abstract suspend fun upload(local: L): Uploaded<R>
    protected abstract suspend fun markSynced(local: L, server: R)
    protected abstract suspend fun adopt(server: R)
    protected abstract suspend fun merge(local: L?, server: R)
    protected abstract fun R.serverKey(): String
    protected abstract suspend fun fetch(since: Long): ChangePage<R>

    override suspend fun push() {
        for (row in pending()) {
            val result = upload(row)
            if (result.applied) {
                markSynced(row, result.row)
            } else {
                transaction {
                    val current = getLocal(row.key())
                    if (current == null || current.modified() == row.modified()) adopt(result.row)
                }
            }
        }
    }

    override suspend fun pull() {
        var cursor = syncStateDao.get(cursorKey)?.toLongOrNull() ?: 0L
        do {
            val page = fetch(cursor)
            transaction {
                for (server in page.rows) merge(getLocal(server.serverKey()), server)
                syncStateDao.put(SyncStateEntity(cursorKey, page.cursor.toString()))
            }
            cursor = page.cursor
        } while (page.hasMore)
    }
}

internal class StoreSync(
    private val api: ShoppingApi,
    private val dao: StoreDao,
    syncStateDao: SyncStateDao,
    transaction: TransactionRunner,
) : CursorSync<StoreEntity, Store>(syncStateDao, transaction, CURSOR) {
    override suspend fun pending() = dao.getPending()
    override fun StoreEntity.key() = id
    override fun StoreEntity.modified() = modifiedAt
    override suspend fun getLocal(id: String) = dao.getById(id)

    override suspend fun upload(local: StoreEntity): Uploaded<Store> {
        val body = StoreUpsert(
            name = local.name,
            sortOrder = local.sortOrder,
            modifiedAt = formatInstant(local.modifiedAt),
            deleted = local.deleted,
        )
        val result = api.upsertStore(UUID.fromString(local.id), body).bodyOrThrow()
        return Uploaded(result.applied, result.store)
    }

    override suspend fun markSynced(local: StoreEntity, server: Store) =
        dao.markSynced(local.id, local.modifiedAt, server.version)

    override suspend fun adopt(server: Store) = dao.upsert(server.toEntity())
    override suspend fun merge(local: StoreEntity?, server: Store) {
        mergeServerStore(local, server)?.let { dao.upsert(it) }
    }

    override fun Store.serverKey() = id.toString()
    override suspend fun fetch(since: Long) =
        api.listStoreChanges(since = since).bodyOrThrow().let { ChangePage(it.stores, it.cursor, it.hasMore) }

    companion object {
        const val CURSOR = "stores.cursor"
    }
}

internal class ShoppingItemSync(
    private val api: ShoppingApi,
    private val dao: ShoppingItemDao,
    syncStateDao: SyncStateDao,
    transaction: TransactionRunner,
) : CursorSync<ShoppingItemEntity, ShoppingItem>(syncStateDao, transaction, CURSOR) {
    override suspend fun pending() = dao.getPending()
    override fun ShoppingItemEntity.key() = id
    override fun ShoppingItemEntity.modified() = modifiedAt
    override suspend fun getLocal(id: String) = dao.getById(id)

    override suspend fun upload(local: ShoppingItemEntity): Uploaded<ShoppingItem> {
        val body = ShoppingItemUpsert(
            name = local.name,
            modifiedAt = formatInstant(local.modifiedAt),
            storeId = local.storeId?.let(UUID::fromString),
            aisle = local.aisle,
            note = local.note,
            purchasedAt = local.purchasedAt?.let(::formatInstant),
            deleted = local.deleted,
        )
        val result = api.upsertShoppingItem(UUID.fromString(local.id), body).bodyOrThrow()
        return Uploaded(result.applied, result.item)
    }

    override suspend fun markSynced(local: ShoppingItemEntity, server: ShoppingItem) =
        dao.markSynced(local.id, local.modifiedAt, server.version)

    override suspend fun adopt(server: ShoppingItem) = dao.upsert(server.toEntity())
    override suspend fun merge(local: ShoppingItemEntity?, server: ShoppingItem) {
        mergeServerItem(local, server)?.let { dao.upsert(it) }
    }

    override fun ShoppingItem.serverKey() = id.toString()
    override suspend fun fetch(since: Long) =
        api.listShoppingItemChanges(since = since).bodyOrThrow().let { ChangePage(it.items, it.cursor, it.hasMore) }

    companion object {
        const val CURSOR = "shopping_items.cursor"
    }
}
