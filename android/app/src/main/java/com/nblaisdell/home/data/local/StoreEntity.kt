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
 * A store items are bought at (HOME-66), synced like [ShoppingItemEntity] with the same
 * last-write-wins bookkeeping. Stores are ordered by [sortOrder], then name.
 */
@Entity(tableName = "stores", indices = [Index("pending_sync")])
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    @ColumnInfo(name = "pending_sync") val pendingSync: Boolean = false,
)

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores WHERE deleted = 0 ORDER BY sort_order, name COLLATE NOCASE")
    fun observeActive(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE deleted = 0 ORDER BY sort_order, name COLLATE NOCASE")
    suspend fun getActive(): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun getById(id: String): StoreEntity?

    @Query("SELECT * FROM stores WHERE pending_sync = 1")
    suspend fun getPending(): List<StoreEntity>

    @Upsert
    suspend fun upsert(store: StoreEntity)

    /** Marks an uploaded edit as accepted, unless the store was edited again meanwhile. */
    @Query(
        """UPDATE stores SET pending_sync = 0, server_version = :serverVersion
           WHERE id = :id AND modified_at = :uploadedModifiedAt""",
    )
    suspend fun markSynced(id: String, uploadedModifiedAt: Long, serverVersion: Long)
}
