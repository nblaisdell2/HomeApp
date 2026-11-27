package com.nblaisdell.home.data.sync

import com.nblaisdell.home.data.local.ShoppingItemDao
import com.nblaisdell.home.data.local.StoreDao
import com.nblaisdell.home.data.local.TransactionRunner
import com.nblaisdell.home.data.repository.nextModifiedAt
import java.util.Locale

/**
 * Merges stores that ended up with the same name (HOME-66: "Costco" and "costco" must not
 * split). That happens when two devices create the same store before either has synced.
 *
 * Every device applies the same rule after pulling: keep the store with the smallest id,
 * move the others' items (including history) onto it, and delete the others. Both devices
 * therefore make the same edits, and last-write-wins settles them to one result.
 */
class StoreMerger(
    private val storeDao: StoreDao,
    private val itemDao: ShoppingItemDao,
    private val transaction: TransactionRunner,
) {
    /** Returns true if anything changed (so there are new edits to upload). */
    suspend fun mergeDuplicates(): Boolean = transaction {
        val duplicates = storeDao.getActive()
            .groupBy { it.name.trim().lowercase(Locale.ROOT) }
            .values
            .filter { it.size > 1 }
        for (group in duplicates) {
            val keeper = group.minBy { it.id }
            for (duplicate in group - keeper) {
                storeDao.upsert(
                    duplicate.copy(
                        deleted = true,
                        modifiedAt = nextModifiedAt(duplicate.modifiedAt),
                        pendingSync = true,
                    ),
                )
                for (item in itemDao.getByStore(duplicate.id)) {
                    itemDao.upsert(
                        item.copy(
                            storeId = keeper.id,
                            modifiedAt = nextModifiedAt(item.modifiedAt),
                            pendingSync = true,
                        ),
                    )
                }
            }
        }
        duplicates.isNotEmpty()
    }
}
