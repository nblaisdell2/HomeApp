package com.nblaisdell.home.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 (HOME-2): stores become their own synced table, and items gain store_id, aisle,
 * note and purchased_at in place of the free-text `store` column.
 *
 * Old store names are turned into stores with [LegacyStoreIds], the same ids the API's
 * migration gives them, so items keep their store. Those stores are written with
 * modified_at = 0 and pending: uploading them is harmless (the server's copy is newer and wins)
 * and covers a store that only ever existed on this device. The items cursor is reset so the
 * next sync re-pulls everything in the new shape; unsynced local edits survive that as usual.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `stores` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                `modified_at` INTEGER NOT NULL, `deleted` INTEGER NOT NULL,
                `server_version` INTEGER, `pending_sync` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stores_pending_sync` ON `stores` (`pending_sync`)")

        // Old store names, newest spelling first so it becomes the store's name.
        val storeIdByName = mutableMapOf<String, String>()
        db.query(
            "SELECT store FROM shopping_items WHERE store IS NOT NULL ORDER BY modified_at DESC",
        ).use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0).trim(' ')
                if (name.isEmpty() || name in storeIdByName) continue
                val id = LegacyStoreIds.idFor(name)
                storeIdByName[name] = id
                db.execSQL(
                    """INSERT OR IGNORE INTO stores
                       (id, name, sort_order, modified_at, deleted, server_version, pending_sync)
                       VALUES (?, ?, 0, 0, 0, NULL, 1)""",
                    arrayOf(id, name),
                )
            }
        }

        // SQLite before 3.35 (Android < 14) can't drop columns, so rebuild the table.
        db.execSQL(
            """CREATE TABLE `shopping_items_new` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `store_id` TEXT, `aisle` TEXT,
                `note` TEXT, `purchased_at` INTEGER, `modified_at` INTEGER NOT NULL,
                `deleted` INTEGER NOT NULL, `server_version` INTEGER,
                `pending_sync` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
        )
        db.query("SELECT id, store FROM shopping_items").use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val store = if (c.isNull(1)) null else c.getString(1).trim(' ').ifEmpty { null }
                db.execSQL(
                    """INSERT INTO shopping_items_new
                       (id, name, store_id, aisle, note, purchased_at, modified_at, deleted,
                        server_version, pending_sync)
                       SELECT id, name, ?, NULL, NULL, NULL, modified_at, deleted,
                              server_version, pending_sync
                       FROM shopping_items WHERE id = ?""",
                    arrayOf(store?.let { LegacyStoreIds.idFor(it) }, id),
                )
            }
        }
        db.execSQL("DROP TABLE shopping_items")
        db.execSQL("ALTER TABLE shopping_items_new RENAME TO shopping_items")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_items_pending_sync` ON `shopping_items` (`pending_sync`)",
        )

        db.execSQL("DELETE FROM sync_state WHERE `key` = 'shopping_items.cursor'")
    }
}
