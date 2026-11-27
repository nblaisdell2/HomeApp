package com.nblaisdell.home.ui.screens.shopping

import com.nblaisdell.home.data.repository.ShoppingListItem
import com.nblaisdell.home.data.repository.StoreOption
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingGroupingTest {

    private val aldi = StoreOption("aldi", "Aldi")
    private val costco = StoreOption("costco", "Costco")
    private val stores = listOf(costco, aldi) // display order from the store list

    private fun item(name: String, storeId: String? = null, aisle: String? = null) =
        ShoppingListItem(name, name, storeId, aisle)

    @Test
    fun `groups by store in store order with Anywhere last`() {
        val groups = groupForDisplay(
            listOf(item("Bread"), item("Milk", "aldi"), item("Paper towels", "costco")),
            stores,
        )
        assertEquals(listOf("Costco", "Aldi", ANYWHERE), groups.map { it.title })
    }

    @Test
    fun `leaves out stores with nothing to buy`() {
        val groups = groupForDisplay(listOf(item("Milk", "aldi")), stores)
        assertEquals(listOf("Aldi"), groups.map { it.title })
    }

    @Test
    fun `items of a deleted or unknown store count as Anywhere`() {
        val groups = groupForDisplay(listOf(item("Milk", "gone")), stores)
        assertEquals(listOf(ANYWHERE), groups.map { it.title })
        assertEquals(null, groups.single().storeId)
    }

    @Test
    fun `sorts by aisle numerically, then text aisles, then no aisle, then name`() {
        val groups = groupForDisplay(
            listOf(
                item("Zucchini"),
                item("Apples"),
                item("Bagels", "aldi", aisle = "Bakery"),
                item("Rice", "aldi", aisle = "10"),
                item("Milk", "aldi", aisle = "2"),
            ).map { if (it.storeId == null) it.copy(storeId = "aldi") else it },
            stores,
        )
        assertEquals(listOf("Milk", "Rice", "Bagels", "Apples", "Zucchini"), groups.single().items.map { it.name })
    }

    @Test
    fun `filters to one store or to Anywhere`() {
        val items = listOf(item("Bread"), item("Milk", "aldi"), item("Paper towels", "costco"))
        assertEquals(listOf("Aldi"), groupForDisplay(items, stores, StoreFilter.OneStore("aldi")).map { it.title })
        assertEquals(listOf(ANYWHERE), groupForDisplay(items, stores, StoreFilter.Anywhere).map { it.title })
    }
}
