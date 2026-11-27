package com.nblaisdell.home.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/**
 * Room database: the on-device cache that every screen reads from (HOME-13). The server's
 * Postgres database is the source of truth; this copy lets screens load instantly and edits
 * work offline. Bump [version] and add a migration (see Migrations.kt) whenever the schema
 * changes; schemas are exported to app/schemas.
 */
@Database(
    entities = [SyncStateEntity::class, ShoppingItemEntity::class, StoreEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class HomeDatabase : RoomDatabase() {
    abstract fun syncStateDao(): SyncStateDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun storeDao(): StoreDao

    companion object {
        const val NAME = "home.db"
    }
}

/** Small key/value table for sync bookkeeping, e.g. the last server cursor per entity. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entity: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    suspend fun clear()
}
