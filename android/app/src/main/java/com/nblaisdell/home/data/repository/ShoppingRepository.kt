package com.nblaisdell.home.data.repository

import com.nblaisdell.home.data.local.ShoppingItemDao
import com.nblaisdell.home.data.local.ShoppingItemEntity
import com.nblaisdell.home.data.local.TransactionRunner
import com.nblaisdell.home.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** An item on the list. [storeId] null means "Anywhere". */
data class ShoppingListItem(
    val id: String,
    val name: String,
    val storeId: String? = null,
    val aisle: String? = null,
    val note: String? = null,
)

/** What the add/edit form produces. Blank optional fields are stored as null. */
data class ItemDraft(
    val name: String,
    val storeId: String? = null,
    val aisle: String? = null,
    val note: String? = null,
)

/** A recently bought item (HOME-67), one per name, newest purchase first. */
data class HistoryEntry(
    val itemId: String,
    val name: String,
    val storeId: String?,
    val aisle: String?,
    val note: String?,
    val purchasedAt: Long,
)

/**
 * Offline-first shopping list (HOME-13, HOME-2). Screens read from Room, so they never wait on
 * the network. Writes go to Room immediately, are marked pending, and a background sync uploads
 * them. Nothing is ever hard-deleted: deletes are tombstones and check-offs move items into
 * purchase history, so every action can be undone.
 */
@Singleton
class ShoppingRepository @Inject constructor(
    private val dao: ShoppingItemDao,
    private val transaction: TransactionRunner,
    private val syncScheduler: SyncScheduler,
) {
    val items: Flow<List<ShoppingListItem>> = dao.observeOnList().map { rows ->
        rows.map { ShoppingListItem(it.id, it.name, it.storeId, it.aisle, it.note) }
    }

    val activeCount: Flow<Int> = dao.observeActiveCount()

    /** Items bought in the last [HISTORY_DAYS] days, one entry per name (case-insensitive). */
    val history: Flow<List<HistoryEntry>> = flow {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(HISTORY_DAYS)
        emitAll(dao.observePurchasedSince(since).map(::latestPerName))
    }

    suspend fun add(draft: ItemDraft): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(
            ShoppingItemEntity(
                id = id,
                name = draft.name.trim(),
                storeId = draft.storeId,
                aisle = draft.aisle.clean(),
                note = draft.note.clean(),
                modifiedAt = System.currentTimeMillis(),
                pendingSync = true,
            ),
        )
        syncScheduler.requestSync()
        return id
    }

    suspend fun update(id: String, draft: ItemDraft) = edit(listOf(id)) {
        it.copy(
            name = draft.name.trim(),
            storeId = draft.storeId,
            aisle = draft.aisle.clean(),
            note = draft.note.clean(),
        )
    }

    suspend fun delete(ids: Collection<String>) = edit(ids) { it.copy(deleted = true) }

    /** Undoes [delete]. */
    suspend fun restore(ids: Collection<String>) = edit(ids) { it.copy(deleted = false) }

    /** Checks an item off in shopping mode: it leaves the list and goes into history. */
    suspend fun purchase(id: String) = edit(listOf(id)) { it.copy(purchasedAt = System.currentTimeMillis()) }

    /** Puts a bought item back on the list (Undo after check-off, or re-add from history). */
    suspend fun unpurchase(id: String) = edit(listOf(id)) { it.copy(purchasedAt = null, deleted = false) }

    private suspend fun edit(ids: Collection<String>, change: (ShoppingItemEntity) -> ShoppingItemEntity) {
        if (ids.isEmpty()) return
        transaction {
            for (id in ids) {
                val existing = dao.getById(id) ?: continue
                dao.upsert(change(existing).copy(modifiedAt = nextModifiedAt(existing.modifiedAt), pendingSync = true))
            }
        }
        syncScheduler.requestSync()
    }

    companion object {
        const val HISTORY_DAYS = 90L
    }
}

/**
 * Always newer than the version being edited, even if this device's clock is a little behind
 * the device that made that version; otherwise this edit could lose last-write-wins.
 */
internal fun nextModifiedAt(previous: Long) = max(System.currentTimeMillis(), previous + 1)

private fun String?.clean() = this?.trim()?.ifEmpty { null }

/** Keeps the newest purchase per name; input is already newest first. */
internal fun latestPerName(rows: List<ShoppingItemEntity>): List<HistoryEntry> =
    rows.distinctBy { it.name.trim().lowercase(Locale.ROOT) }.map {
        HistoryEntry(it.id, it.name, it.storeId, it.aisle, it.note, it.purchasedAt ?: 0L)
    }
