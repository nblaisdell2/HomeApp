package com.nblaisdell.home.data.sync

import com.nblaisdell.home.data.local.ShoppingItemDao
import com.nblaisdell.home.data.local.ShoppingItemEntity
import com.nblaisdell.home.data.local.StoreDao
import com.nblaisdell.home.data.local.StoreEntity
import com.nblaisdell.home.data.local.SyncStateDao
import com.nblaisdell.home.data.local.SyncStateEntity
import com.nblaisdell.home.data.local.TransactionRunner
import com.nblaisdell.home.data.remote.generated.api.ShoppingApi
import com.nblaisdell.home.data.remote.generated.model.ShoppingItem
import com.nblaisdell.home.data.remote.generated.model.ShoppingItemChanges
import com.nblaisdell.home.data.remote.generated.model.ShoppingItemUpsert
import com.nblaisdell.home.data.remote.generated.model.ShoppingItemUpsertResult
import com.nblaisdell.home.data.remote.generated.model.Store
import com.nblaisdell.home.data.remote.generated.model.StoreChanges
import com.nblaisdell.home.data.remote.generated.model.StoreUpsert
import com.nblaisdell.home.data.remote.generated.model.StoreUpsertResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class SyncEngineTest {

    private val dao = FakeShoppingItemDao()
    private val storeDao = FakeStoreDao()
    private val syncState = FakeSyncStateDao()
    private val server = FakeServer()
    private val engine = SyncEngine(server, storeDao, dao, syncState, NoTransaction)

    private val idA = UUID.randomUUID()
    private val storeA = UUID.randomUUID()
    private val t0 = 1_764_158_400_000L // 2025-11-26T12:00:00Z

    @Test
    fun `uploads pending edits and marks them synced`() = runTest {
        dao.upsert(local(idA, "Milk", modifiedAt = t0, pending = true))

        assertEquals(SyncResult.Success, engine.sync())

        assertEquals("Milk", server.items[idA]!!.name)
        val row = dao.getById(idA.toString())!!
        assertFalse(row.pendingSync)
        assertEquals(server.items[idA]!!.version, row.serverVersion)
    }

    @Test
    fun `uploads every item field`() = runTest {
        dao.upsert(
            local(idA, "Milk", modifiedAt = t0, pending = true).copy(
                storeId = storeA.toString(), aisle = "4", note = "2 gallons", purchasedAt = t0,
            ),
        )

        engine.sync()

        val uploaded = server.items[idA]!!
        assertEquals(storeA, uploaded.storeId)
        assertEquals("4", uploaded.aisle)
        assertEquals("2 gallons", uploaded.note)
        assertEquals(formatInstant(t0), uploaded.purchasedAt)
    }

    @Test
    fun `pulls changes made on another device and advances the cursor`() = runTest {
        server.put(idA, "Bread", modifiedAt = t0)

        engine.sync()

        assertEquals("Bread", dao.getById(idA.toString())!!.name)
        assertEquals(server.items[idA]!!.version.toString(), syncState.get(SyncEngine.SHOPPING_CURSOR))
    }

    @Test
    fun `a newer server edit replaces an older pending local edit`() = runTest {
        server.put(idA, "Oat milk", modifiedAt = t0 + 5_000)
        dao.upsert(local(idA, "Milk", modifiedAt = t0, pending = true))

        engine.sync()

        val row = dao.getById(idA.toString())!!
        assertEquals("Oat milk", row.name)
        assertFalse(row.pendingSync)
    }

    @Test
    fun `a newer pending local edit is not overwritten by an older server copy`() {
        val local = local(idA, "Oat milk", modifiedAt = t0 + 5_000, pending = true)
        val serverCopy = serverItem(idA, "Milk", modifiedAt = t0, version = 3)
        assertNull(mergeServerItem(local, serverCopy))
    }

    @Test
    fun `a synced local row always takes the server copy`() {
        val local = local(idA, "Milk", modifiedAt = t0 + 5_000, pending = false)
        val merged = mergeServerItem(local, serverItem(idA, "Oat milk", modifiedAt = t0, version = 3))!!
        assertEquals("Oat milk", merged.name)
    }

    @Test
    fun `deletes travel as tombstones`() = runTest {
        server.put(idA, "Milk", modifiedAt = t0)
        engine.sync()
        server.put(idA, "Milk", modifiedAt = t0 + 1, deleted = true)

        engine.sync()

        assertTrue(dao.getById(idA.toString())!!.deleted)
    }

    @Test
    fun `stores sync both ways with their own cursor`() = runTest {
        storeDao.upsert(StoreEntity(storeA.toString(), "Costco", sortOrder = 0, modifiedAt = t0, pendingSync = true))
        val other = UUID.randomUUID()
        server.putStore(other, "Aldi", sortOrder = 1, modifiedAt = t0)

        engine.sync()

        assertEquals("Costco", server.stores[storeA]!!.name)
        assertFalse(storeDao.getById(storeA.toString())!!.pendingSync)
        assertEquals("Aldi", storeDao.getById(other.toString())!!.name)
        assertEquals(server.stores.values.maxOf { it.version }.toString(), syncState.get(SyncEngine.STORES_CURSOR))
    }

    @Test
    fun `stores are uploaded before the items that use them`() = runTest {
        dao.upsert(local(idA, "Milk", modifiedAt = t0, pending = true).copy(storeId = storeA.toString()))
        storeDao.upsert(StoreEntity(storeA.toString(), "Costco", modifiedAt = t0, pendingSync = true))

        engine.sync()

        assertEquals(listOf("store:$storeA", "item:$idA"), server.uploads)
    }

    @Test
    fun `a migrated legacy store adopts the server's copy`() = runTest {
        // The Room migration writes old stores with modifiedAt = 0; the server's is newer.
        server.putStore(storeA, "Costco", sortOrder = 3, modifiedAt = t0)
        storeDao.upsert(StoreEntity(storeA.toString(), "costco", modifiedAt = 0, pendingSync = true))

        engine.sync()

        val row = storeDao.getById(storeA.toString())!!
        assertEquals("Costco", row.name)
        assertEquals(3, row.sortOrder)
        assertFalse(row.pendingSync)
    }

    @Test
    fun `a newer pending store edit survives an older server copy`() {
        val local = StoreEntity(storeA.toString(), "Costco Wholesale", modifiedAt = t0 + 5, pendingSync = true)
        val serverCopy = Store(storeA, "Costco", 0, formatInstant(t0), false, 7)
        assertNull(mergeServerStore(local, serverCopy))
    }

    @Test
    fun `reports offline and keeps edits pending when the network is down`() = runTest {
        dao.upsert(local(idA, "Milk", modifiedAt = t0, pending = true))
        server.offline = true

        assertEquals(SyncResult.Offline, engine.sync())
        assertTrue(dao.getById(idA.toString())!!.pendingSync)
    }

    @Test
    fun `reports unauthorized on 401`() = runTest {
        server.unauthorized = true
        assertEquals(SyncResult.Unauthorized, engine.sync())
    }

    // --- helpers -------------------------------------------------------------------------

    private fun local(id: UUID, name: String, modifiedAt: Long, pending: Boolean) =
        ShoppingItemEntity(id.toString(), name, modifiedAt = modifiedAt, pendingSync = pending)

    private fun serverItem(id: UUID, name: String, modifiedAt: Long, version: Long, deleted: Boolean = false) =
        ShoppingItem(id, name, formatInstant(modifiedAt), deleted, version)

    private object NoTransaction : TransactionRunner {
        override suspend fun <T> invoke(block: suspend () -> T): T = block()
    }

    /** Mimics the API's last-write-wins rules in memory. */
    private class FakeServer : ShoppingApi {
        val items = linkedMapOf<UUID, ShoppingItem>()
        val stores = linkedMapOf<UUID, Store>()
        val uploads = mutableListOf<String>()
        private var version = 0L
        var offline = false
        var unauthorized = false

        fun put(id: UUID, name: String, modifiedAt: Long, deleted: Boolean = false) {
            items[id] = ShoppingItem(id, name, formatInstant(modifiedAt), deleted, ++version)
        }

        fun putStore(id: UUID, name: String, sortOrder: Int, modifiedAt: Long, deleted: Boolean = false) {
            stores[id] = Store(id, name, sortOrder, formatInstant(modifiedAt), deleted, ++version)
        }

        private fun check() {
            if (offline) throw IOException("offline")
        }

        private fun <T> unauthorizedResponse(): Response<T> =
            Response.error(401, """{"message":"nope"}""".toResponseBody())

        override suspend fun listShoppingItemChanges(since: Long, limit: Int): Response<ShoppingItemChanges> {
            check()
            if (unauthorized) return unauthorizedResponse()
            val changed = items.values.filter { it.version > since }.sortedBy { it.version }
            val page = changed.take(limit)
            return Response.success(
                ShoppingItemChanges(page, page.lastOrNull()?.version ?: since, changed.size > limit),
            )
        }

        override suspend fun upsertShoppingItem(
            id: UUID,
            shoppingItemUpsert: ShoppingItemUpsert,
        ): Response<ShoppingItemUpsertResult> {
            check()
            if (unauthorized) return unauthorizedResponse()
            uploads += "item:$id"
            val current = items[id]
            val incoming = parseInstant(shoppingItemUpsert.modifiedAt)
            if (current != null && parseInstant(current.modifiedAt) >= incoming) {
                return Response.success(ShoppingItemUpsertResult(false, current))
            }
            with(shoppingItemUpsert) {
                items[id] = ShoppingItem(
                    id, name, modifiedAt, deleted ?: false, ++version, storeId, aisle, note, purchasedAt,
                )
            }
            return Response.success(ShoppingItemUpsertResult(true, items[id]!!))
        }

        override suspend fun listStoreChanges(since: Long, limit: Int): Response<StoreChanges> {
            check()
            if (unauthorized) return unauthorizedResponse()
            val changed = stores.values.filter { it.version > since }.sortedBy { it.version }
            val page = changed.take(limit)
            return Response.success(StoreChanges(page, page.lastOrNull()?.version ?: since, changed.size > limit))
        }

        override suspend fun upsertStore(id: UUID, storeUpsert: StoreUpsert): Response<StoreUpsertResult> {
            check()
            if (unauthorized) return unauthorizedResponse()
            uploads += "store:$id"
            val current = stores[id]
            val incoming = parseInstant(storeUpsert.modifiedAt)
            if (current != null && parseInstant(current.modifiedAt) >= incoming) {
                return Response.success(StoreUpsertResult(false, current))
            }
            putStore(id, storeUpsert.name, storeUpsert.sortOrder, incoming, storeUpsert.deleted ?: false)
            return Response.success(StoreUpsertResult(true, stores[id]!!))
        }
    }

    private class FakeShoppingItemDao : ShoppingItemDao {
        private val rows = MutableStateFlow<Map<String, ShoppingItemEntity>>(emptyMap())

        private fun onList(e: ShoppingItemEntity) = !e.deleted && e.purchasedAt == null
        override fun observeOnList(): Flow<List<ShoppingItemEntity>> = rows.map { m -> m.values.filter(::onList) }
        override fun observePurchasedSince(sinceMillis: Long): Flow<List<ShoppingItemEntity>> = rows.map { m ->
            m.values.filter { !it.deleted && (it.purchasedAt ?: -1) >= sinceMillis }.sortedByDescending { it.purchasedAt }
        }

        override fun observeActiveCount(): Flow<Int> = rows.map { m -> m.values.count(::onList) }
        override fun observePendingCount(): Flow<Int> = rows.map { m -> m.values.count { it.pendingSync } }
        override suspend fun getById(id: String) = rows.value[id]
        override suspend fun getByStore(storeId: String) =
            rows.value.values.filter { !it.deleted && it.storeId == storeId }

        override suspend fun getPending() = rows.value.values.filter { it.pendingSync }
        override suspend fun upsert(item: ShoppingItemEntity) {
            rows.value = rows.value + (item.id to item)
        }

        override suspend fun markSynced(id: String, uploadedModifiedAt: Long, serverVersion: Long) {
            val row = rows.value[id] ?: return
            if (row.modifiedAt == uploadedModifiedAt) {
                upsert(row.copy(pendingSync = false, serverVersion = serverVersion))
            }
        }

        override suspend fun clear() {
            rows.value = emptyMap()
        }
    }

    private class FakeStoreDao : StoreDao {
        private val rows = MutableStateFlow<Map<String, StoreEntity>>(emptyMap())
        private fun active() = rows.value.values.filter { !it.deleted }.sortedWith(compareBy({ it.sortOrder }, { it.name }))

        override fun observeActive(): Flow<List<StoreEntity>> = rows.map { active() }
        override suspend fun getActive() = active()
        override suspend fun getById(id: String) = rows.value[id]
        override suspend fun getPending() = rows.value.values.filter { it.pendingSync }
        override suspend fun upsert(store: StoreEntity) {
            rows.value = rows.value + (store.id to store)
        }

        override suspend fun markSynced(id: String, uploadedModifiedAt: Long, serverVersion: Long) {
            val row = rows.value[id] ?: return
            if (row.modifiedAt == uploadedModifiedAt) {
                upsert(row.copy(pendingSync = false, serverVersion = serverVersion))
            }
        }
    }

    private class FakeSyncStateDao : SyncStateDao {
        private val values = mutableMapOf<String, String>()
        override suspend fun get(key: String) = values[key]
        override suspend fun put(entity: SyncStateEntity) {
            values[entity.key] = entity.value
        }

        override suspend fun clear() = values.clear()
    }
}
