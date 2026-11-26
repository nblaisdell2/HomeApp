package com.nblaisdell.home.data.sync

import com.nblaisdell.home.data.local.ShoppingItemDao
import com.nblaisdell.home.data.local.ShoppingItemEntity
import com.nblaisdell.home.data.local.SyncStateDao
import com.nblaisdell.home.data.local.SyncStateEntity
import com.nblaisdell.home.data.local.TransactionRunner
import com.nblaisdell.home.data.remote.generated.api.ShoppingApi
import com.nblaisdell.home.data.remote.generated.model.ShoppingItem
import com.nblaisdell.home.data.remote.generated.model.ShoppingItemChanges
import com.nblaisdell.home.data.remote.generated.model.ShoppingItemUpsert
import com.nblaisdell.home.data.remote.generated.model.ShoppingItemUpsertResult
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
    private val syncState = FakeSyncStateDao()
    private val server = FakeServer()
    private val engine = SyncEngine(server, dao, syncState, NoTransaction)

    private val idA = UUID.randomUUID()
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
        ShoppingItemEntity(id.toString(), name, null, modifiedAt, pendingSync = pending)

    private fun serverItem(id: UUID, name: String, modifiedAt: Long, version: Long, deleted: Boolean = false) =
        ShoppingItem(id, name, formatInstant(modifiedAt), deleted, version)

    private object NoTransaction : TransactionRunner {
        override suspend fun <T> invoke(block: suspend () -> T): T = block()
    }

    /** Mimics the API's last-write-wins rules in memory. */
    private class FakeServer : ShoppingApi {
        val items = linkedMapOf<UUID, ShoppingItem>()
        private var version = 0L
        var offline = false
        var unauthorized = false

        fun put(id: UUID, name: String, modifiedAt: Long, deleted: Boolean = false) {
            items[id] = ShoppingItem(id, name, formatInstant(modifiedAt), deleted, ++version)
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
            val current = items[id]
            val incoming = parseInstant(shoppingItemUpsert.modifiedAt)
            if (current != null && parseInstant(current.modifiedAt) >= incoming) {
                return Response.success(ShoppingItemUpsertResult(false, current))
            }
            put(id, shoppingItemUpsert.name, incoming, shoppingItemUpsert.deleted ?: false)
            return Response.success(ShoppingItemUpsertResult(true, items[id]!!))
        }
    }

    private class FakeShoppingItemDao : ShoppingItemDao {
        private val rows = MutableStateFlow<Map<String, ShoppingItemEntity>>(emptyMap())

        override fun observeActive(): Flow<List<ShoppingItemEntity>> =
            rows.map { m -> m.values.filter { !it.deleted }.sortedBy { it.name } }

        override fun observeActiveCount(): Flow<Int> = rows.map { m -> m.values.count { !it.deleted } }
        override fun observePendingCount(): Flow<Int> = rows.map { m -> m.values.count { it.pendingSync } }
        override suspend fun getById(id: String) = rows.value[id]
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

    private class FakeSyncStateDao : SyncStateDao {
        private val values = mutableMapOf<String, String>()
        override suspend fun get(key: String) = values[key]
        override suspend fun put(entity: SyncStateEntity) {
            values[entity.key] = entity.value
        }

        override suspend fun clear() = values.clear()
    }
}
