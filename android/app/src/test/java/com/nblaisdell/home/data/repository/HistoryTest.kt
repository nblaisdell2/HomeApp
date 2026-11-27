package com.nblaisdell.home.data.repository

import com.nblaisdell.home.data.local.ShoppingItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    private fun bought(id: String, name: String, at: Long, storeId: String? = null) =
        ShoppingItemEntity(id, name, storeId = storeId, purchasedAt = at, modifiedAt = at)

    @Test
    fun `keeps the newest purchase of each name, ignoring case`() {
        val history = latestPerName(
            listOf(
                bought("3", "Milk", at = 300, storeId = "aldi"),
                bought("2", "Bread", at = 200),
                bought("1", "milk ", at = 100, storeId = "costco"),
            ),
        )
        assertEquals(listOf("3", "2"), history.map { it.itemId })
        assertEquals("aldi", history.first().storeId)
    }

    @Test
    fun `an edit is always newer than the version it replaces`() {
        val future = System.currentTimeMillis() + 60_000
        assertTrue(nextModifiedAt(future) > future)
        assertTrue(nextModifiedAt(0) >= System.currentTimeMillis() - 1_000)
    }
}
