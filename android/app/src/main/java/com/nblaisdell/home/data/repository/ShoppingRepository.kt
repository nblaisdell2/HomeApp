package com.nblaisdell.home.data.repository

import com.nblaisdell.home.data.local.ShoppingItemDao
import com.nblaisdell.home.data.local.ShoppingItemEntity
import com.nblaisdell.home.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class ShoppingListItem(val id: String, val name: String, val store: String?)

/**
 * Offline-first shopping list (HOME-13). Screens read from Room, so they never wait on the
 * network. Writes go to Room immediately, are marked pending, and a background sync uploads them.
 */
@Singleton
class ShoppingRepository @Inject constructor(
    private val dao: ShoppingItemDao,
    private val syncScheduler: SyncScheduler,
) {
    val items: Flow<List<ShoppingListItem>> = dao.observeActive().map { rows ->
        rows.map { ShoppingListItem(it.id, it.name, it.store) }
    }

    val activeCount: Flow<Int> = dao.observeActiveCount()

    suspend fun add(name: String, store: String?): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(
            ShoppingItemEntity(
                id = id,
                name = name.trim(),
                store = store?.trim()?.ifEmpty { null },
                modifiedAt = System.currentTimeMillis(),
                pendingSync = true,
            ),
        )
        syncScheduler.requestSync()
        return id
    }

    suspend fun update(id: String, name: String, store: String?) = edit(id) {
        it.copy(name = name.trim(), store = store?.trim()?.ifEmpty { null })
    }

    suspend fun delete(id: String) = edit(id) { it.copy(deleted = true) }

    private suspend fun edit(id: String, change: (ShoppingItemEntity) -> ShoppingItemEntity) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            change(existing).copy(
                // Always newer than the version being edited, even if this device's clock is a
                // little behind the device that made that version; otherwise this edit could lose.
                modifiedAt = max(System.currentTimeMillis(), existing.modifiedAt + 1),
                pendingSync = true,
            ),
        )
        syncScheduler.requestSync()
    }
}
