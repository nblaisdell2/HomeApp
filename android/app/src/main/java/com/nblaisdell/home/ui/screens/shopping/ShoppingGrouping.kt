package com.nblaisdell.home.ui.screens.shopping

import com.nblaisdell.home.data.repository.ShoppingListItem
import com.nblaisdell.home.data.repository.StoreOption

/** One section of the list: a store (or "Anywhere" when [storeId] is null) and its items. */
data class ShoppingGroup(val storeId: String?, val title: String, val items: List<ShoppingListItem>)

/** Which stores to show, used by shopping mode to shop one store at a time (HOME-66). */
sealed interface StoreFilter {
    data object All : StoreFilter
    data object Anywhere : StoreFilter
    data class OneStore(val storeId: String) : StoreFilter
}

const val ANYWHERE = "Anywhere"

/**
 * Groups items under their store, in the stores' display order with "Anywhere" last. Items
 * whose store was deleted (or hasn't synced yet) count as "Anywhere". Within a group items sort
 * by aisle (numbers numerically, items without an aisle last), then name. Empty groups are left
 * out.
 */
fun groupForDisplay(
    items: List<ShoppingListItem>,
    stores: List<StoreOption>,
    filter: StoreFilter = StoreFilter.All,
): List<ShoppingGroup> {
    val known = stores.associateBy { it.id }
    val byStore = items.groupBy { item -> item.storeId?.takeIf { it in known } }
    val groups = stores.mapNotNull { store ->
        byStore[store.id]?.let { ShoppingGroup(store.id, store.name, it.sortedWith(itemOrder)) }
    } + listOfNotNull(byStore[null]?.let { ShoppingGroup(null, ANYWHERE, it.sortedWith(itemOrder)) })

    return when (filter) {
        StoreFilter.All -> groups
        StoreFilter.Anywhere -> groups.filter { it.storeId == null }
        is StoreFilter.OneStore -> groups.filter { it.storeId == filter.storeId }
    }
}

private val itemOrder: Comparator<ShoppingListItem> =
    Comparator<ShoppingListItem> { a, b -> compareAisles(a.aisle, b.aisle) }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

/** "2" < "10" < "Bakery" < no aisle. */
internal fun compareAisles(a: String?, b: String?): Int {
    if (a == b) return 0
    if (a == null) return 1
    if (b == null) return -1
    val na = a.toIntOrNull()
    val nb = b.toIntOrNull()
    return when {
        na != null && nb != null -> na.compareTo(nb)
        na != null -> -1
        nb != null -> 1
        else -> a.compareTo(b, ignoreCase = true)
    }
}
