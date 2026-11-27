package com.nblaisdell.home.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HomeDatabase::class.java)

    @Test
    fun migrate1To2_turnsFreeTextStoresIntoStores() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """INSERT INTO shopping_items (id, name, store, modified_at, deleted, server_version, pending_sync)
                   VALUES ('a', 'Milk', 'Costco', 200, 0, 5, 0),
                          ('b', 'Eggs', ' costco ', 100, 0, 6, 1),
                          ('c', 'Bread', NULL, 100, 0, 7, 0),
                          ('d', 'Jam', '', 100, 1, 8, 0)""",
            )
            db.execSQL("INSERT INTO sync_state (`key`, value) VALUES ('shopping_items.cursor', '8')")
        }

        // Also checks the migrated schema matches what Room expects for v2.
        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        val costco = LegacyStoreIds.idFor("Costco")
        db.query("SELECT id, name, modified_at, pending_sync FROM stores").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(costco, c.getString(0))
            assertEquals("Costco", c.getString(1)) // spelling on the most recently edited item
            assertEquals(0L, c.getLong(2))
            assertEquals(1, c.getInt(3))
        }

        val rows = mutableMapOf<String, Pair<String?, Int>>()
        db.query("SELECT id, store_id, pending_sync, purchased_at FROM shopping_items").use { c ->
            while (c.moveToNext()) {
                rows[c.getString(0)] = (if (c.isNull(1)) null else c.getString(1)) to c.getInt(2)
                assertNull(if (c.isNull(3)) null else c.getLong(3))
            }
        }
        assertEquals(costco to 0, rows["a"])
        assertEquals(costco to 1, rows["b"]) // pending edit is kept
        assertEquals(null to 0, rows["c"])
        assertEquals(null to 0, rows["d"])

        db.query("SELECT COUNT(*) FROM sync_state WHERE `key` = 'shopping_items.cursor'").use { c ->
            c.moveToFirst()
            assertFalse(c.getInt(0) > 0)
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
