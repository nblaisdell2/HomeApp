package com.nblaisdell.home.data.sync

import com.nblaisdell.home.data.local.ShoppingItemDao
import com.nblaisdell.home.data.local.SyncStateDao
import com.nblaisdell.home.data.local.SyncStateEntity
import com.nblaisdell.home.data.local.TransactionRunner
import com.nblaisdell.home.data.remote.ApiException
import com.nblaisdell.home.data.remote.bodyOrThrow
import com.nblaisdell.home.data.remote.generated.api.ShoppingApi
import com.nblaisdell.home.data.remote.generated.model.ShoppingItemUpsert
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

sealed interface SyncResult {
    data object Success : SyncResult
    data object Offline : SyncResult
    data object Unauthorized : SyncResult
    data class Failed(val error: Throwable) : SyncResult
}

/**
 * One sync pass (HOME-13): upload pending local edits, then pull everything the server changed
 * since the last pull. Safe to run repeatedly; the server ignores stale uploads and re-pulled
 * items merge to the same result.
 */
class SyncEngine @Inject constructor(
    private val api: ShoppingApi,
    private val itemDao: ShoppingItemDao,
    private val syncStateDao: SyncStateDao,
    private val transaction: TransactionRunner,
) {
    suspend fun sync(): SyncResult = try {
        pushPending()
        pullChanges()
        SyncResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        SyncResult.Offline
    } catch (e: ApiException) {
        if (e.isUnauthorized) SyncResult.Unauthorized else SyncResult.Failed(e)
    } catch (e: Exception) {
        SyncResult.Failed(e)
    }

    private suspend fun pushPending() {
        for (pending in itemDao.getPending()) {
            val result = api.upsertShoppingItem(
                id = UUID.fromString(pending.id),
                shoppingItemUpsert = ShoppingItemUpsert(
                    name = pending.name,
                    store = pending.store,
                    modifiedAt = formatInstant(pending.modifiedAt),
                    deleted = pending.deleted,
                ),
            ).bodyOrThrow()

            if (result.applied) {
                itemDao.markSynced(pending.id, pending.modifiedAt, result.item.version)
            } else {
                // The server already had a newer (or the same) edit: take its version, unless
                // the item was edited again here while the upload was in flight.
                transaction {
                    val current = itemDao.getById(pending.id)
                    if (current == null || current.modifiedAt == pending.modifiedAt) {
                        itemDao.upsert(result.item.toEntity())
                    }
                }
            }
        }
    }

    private suspend fun pullChanges() {
        var cursor = syncStateDao.get(SHOPPING_CURSOR)?.toLongOrNull() ?: 0L
        do {
            val page = api.listShoppingItemChanges(since = cursor).bodyOrThrow()
            transaction {
                for (serverItem in page.items) {
                    val merged = mergeServerItem(itemDao.getById(serverItem.id.toString()), serverItem)
                    if (merged != null) itemDao.upsert(merged)
                }
                syncStateDao.put(SyncStateEntity(SHOPPING_CURSOR, page.cursor.toString()))
            }
            cursor = page.cursor
        } while (page.hasMore)
    }

    companion object {
        const val SHOPPING_CURSOR = "shopping_items.cursor"
    }
}
