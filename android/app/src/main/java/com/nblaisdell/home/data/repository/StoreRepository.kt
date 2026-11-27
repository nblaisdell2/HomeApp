package com.nblaisdell.home.data.repository

import com.nblaisdell.home.data.local.ShoppingItemDao
import com.nblaisdell.home.data.local.StoreDao
import com.nblaisdell.home.data.local.StoreEntity
import com.nblaisdell.home.data.local.TransactionRunner
import com.nblaisdell.home.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoreOption(val id: String, val name: String)

/**
 * The managed list of stores (HOME-66). Picking a store by name reuses an existing one
 * case-insensitively, so "Costco" and "costco" don't split. Same offline-first rules as
 * [ShoppingRepository].
 */
@Singleton
class StoreRepository @Inject constructor(
    private val dao: StoreDao,
    private val itemDao: ShoppingItemDao,
    private val transaction: TransactionRunner,
    private val syncScheduler: SyncScheduler,
) {
    /** Active stores in display order. */
    val stores: Flow<List<StoreOption>> = dao.observeActive().map { rows -> rows.map { StoreOption(it.id, it.name) } }

    /** Returns the id of the store called [name] (ignoring case), creating it at the end if new. */
    suspend fun findOrCreate(name: String): String {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Store name is blank" }
        val id = transaction {
            val active = dao.getActive()
            active.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.id
                ?: UUID.randomUUID().toString().also { id ->
                    dao.upsert(
                        StoreEntity(
                            id = id,
                            name = clean,
                            sortOrder = (active.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                            modifiedAt = System.currentTimeMillis(),
                            pendingSync = true,
                        ),
                    )
                }
        }
        syncScheduler.requestSync()
        return id
    }

    suspend fun rename(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        transaction { dao.getById(id)?.let { save(it.copy(name = clean)) } }
        syncScheduler.requestSync()
    }

    /** Moves a store one place up or down, renumbering the list so the order is unambiguous. */
    suspend fun move(id: String, up: Boolean) {
        transaction {
            val ordered = dao.getActive().toMutableList()
            val from = ordered.indexOfFirst { it.id == id }
            val to = if (up) from - 1 else from + 1
            if (from >= 0 && to in ordered.indices) {
                ordered.add(to, ordered.removeAt(from))
                ordered.forEachIndexed { index, store ->
                    if (store.sortOrder != index) save(store.copy(sortOrder = index))
                }
            }
        }
        syncScheduler.requestSync()
    }

    /** Deletes a store; its items move to "Anywhere". */
    suspend fun delete(id: String) {
        transaction {
            dao.getById(id)?.let { save(it.copy(deleted = true)) }
            for (item in itemDao.getByStore(id)) {
                itemDao.upsert(
                    item.copy(storeId = null, modifiedAt = nextModifiedAt(item.modifiedAt), pendingSync = true),
                )
            }
        }
        syncScheduler.requestSync()
    }

    private suspend fun save(store: StoreEntity) =
        dao.upsert(store.copy(modifiedAt = nextModifiedAt(store.modifiedAt), pendingSync = true))
}
