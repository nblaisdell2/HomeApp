package com.nblaisdell.home.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A shopping item as cached on this device.
 *
 * - [storeId] points at a [StoreEntity]; null (or a deleted/unknown store) means "Anywhere".
 * - [purchasedAt] is set when the item is checked off in shopping mode (HOME-67). The item then
 *   leaves the list and lives in "Recently bought" history; clearing it puts it back.
 *
 * Sync bookkeeping:
 * - [modifiedAt] is when the item was last edited on *any* device (epoch millis). The newest
 *   edit wins when two devices change the same item.
 * - [pendingSync] is true while a local edit hasn't been accepted by the server yet.
 * - [deleted] keeps a "tombstone" so a delete made offline still reaches the other device.
 */
@Entity(tableName = "shopping_items", indices = [Index("pending_sync")])
data class ShoppingItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "store_id") val storeId: String? = null,
    val aisle: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "purchased_at") val purchasedAt: Long? = null,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    @ColumnInfo(name = "pending_sync") val pendingSync: Boolean = false,
)

@Dao
interface ShoppingItemDao {
    /** Items on the list: not deleted and not yet bought. */
    @Query("SELECT * FROM shopping_items WHERE deleted = 0 AND purchased_at IS NULL")
    fun observeOnList(): Flow<List<ShoppingItemEntity>>

    /** Bought items since [sinceMillis], newest purchase first (may repeat a name). */
    @Query(
        """SELECT * FROM shopping_items
           WHERE deleted = 0 AND purchased_at IS NOT NULL AND purchased_at >= :sinceMillis
           ORDER BY purchased_at DESC""",
    )
    fun observePurchasedSince(sinceMillis: Long): Flow<List<ShoppingItemEntity>>

    @Query("SELECT COUNT(*) FROM shopping_items WHERE deleted = 0 AND purchased_at IS NULL")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM shopping_items WHERE pending_sync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getById(id: String): ShoppingItemEntity?

    @Query("SELECT * FROM shopping_items WHERE deleted = 0 AND store_id = :storeId")
    suspend fun getByStore(storeId: String): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE pending_sync = 1")
    suspend fun getPending(): List<ShoppingItemEntity>

    @Upsert
    suspend fun upsert(item: ShoppingItemEntity)

    /** Marks an uploaded edit as accepted, unless the item was edited again meanwhile. */
    @Query(
        """UPDATE shopping_items SET pending_sync = 0, server_version = :serverVersion
           WHERE id = :id AND modified_at = :uploadedModifiedAt""",
    )
    suspend fun markSynced(id: String, uploadedModifiedAt: Long, serverVersion: Long)

    @Query("DELETE FROM shopping_items")
    suspend fun clear()
}
