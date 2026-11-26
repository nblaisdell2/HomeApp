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
    val store: String?,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    @ColumnInfo(name = "pending_sync") val pendingSync: Boolean = false,
)

@Dao
interface ShoppingItemDao {
    @Query("SELECT * FROM shopping_items WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT COUNT(*) FROM shopping_items WHERE deleted = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM shopping_items WHERE pending_sync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getById(id: String): ShoppingItemEntity?

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
