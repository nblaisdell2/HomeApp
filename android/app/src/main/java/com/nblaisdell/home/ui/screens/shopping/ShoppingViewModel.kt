package com.nblaisdell.home.ui.screens.shopping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nblaisdell.home.data.repository.ItemDraft
import com.nblaisdell.home.data.repository.ShoppingRepository
import com.nblaisdell.home.data.repository.StoreOption
import com.nblaisdell.home.data.repository.StoreRepository
import com.nblaisdell.home.data.sync.SyncStatus
import com.nblaisdell.home.data.sync.SyncStatusTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A store filter chip in shopping mode, with how many items are left there. */
data class FilterChipState(val filter: StoreFilter, val label: String, val count: Int)

data class ShoppingUiState(
    val loading: Boolean = true,
    val groups: List<ShoppingGroup> = emptyList(),
    val stores: List<StoreOption> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Syncing,
    /** Shopping mode (HOME-18): check items off; [filter] narrows to one store (HOME-66). */
    val shopping: Boolean = false,
    val filter: StoreFilter = StoreFilter.All,
    val filterChips: List<FilterChipState> = emptyList(),
    /** Total items on the list, regardless of [filter]. */
    val totalCount: Int = 0,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

/** The Shopping screen (HOME-2): the list grouped by store, and shopping mode. */
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val storeRepository: StoreRepository,
    private val savedState: SavedStateHandle,
    tracker: SyncStatusTracker,
) : ViewModel() {

    private val shopping = savedState.getStateFlow(KEY_SHOPPING, false)
    private val filterKey = savedState.getStateFlow(KEY_FILTER, FILTER_ALL)

    val uiState: StateFlow<ShoppingUiState> =
        combine(repository.items, storeRepository.stores, tracker.status, shopping, filterKey) {
                items, stores, status, shopping, key ->
            val all = groupForDisplay(items, stores)
            val filter = if (shopping) key.toFilter() else StoreFilter.All
            ShoppingUiState(
                loading = false,
                groups = if (filter == StoreFilter.All) all else groupForDisplay(items, stores, filter),
                stores = stores,
                syncStatus = status,
                shopping = shopping,
                filter = filter,
                filterChips = filterChips(all),
                totalCount = items.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    fun add(form: ItemForm) {
        if (form.name.isBlank()) return
        viewModelScope.launch { repository.add(form.toDraft()) }
    }

    fun update(id: String, form: ItemForm) {
        if (form.name.isBlank()) return
        viewModelScope.launch { repository.update(id, form.toDraft()) }
    }

    /** Resolves a newly typed store into a real one (creating it), then builds the draft. */
    private suspend fun ItemForm.toDraft() = ItemDraft(
        name = name,
        storeId = newStoreName?.let { storeRepository.findOrCreate(it) } ?: storeId,
        aisle = aisle,
        note = note,
    )

    fun delete(ids: List<String>) {
        viewModelScope.launch { repository.delete(ids) }
    }

    /** Undo for [delete]. */
    fun restore(ids: List<String>) {
        viewModelScope.launch { repository.restore(ids) }
    }

    fun startShopping() {
        savedState[KEY_SHOPPING] = true
    }

    fun stopShopping() {
        savedState[KEY_SHOPPING] = false
        savedState[KEY_FILTER] = FILTER_ALL
    }

    fun setFilter(filter: StoreFilter) {
        savedState[KEY_FILTER] = when (filter) {
            StoreFilter.All -> FILTER_ALL
            StoreFilter.Anywhere -> FILTER_ANYWHERE
            is StoreFilter.OneStore -> filter.storeId
        }
    }

    /** Checks an item off: it leaves the list and goes into purchase history. */
    fun purchase(id: String) {
        viewModelScope.launch { repository.purchase(id) }
    }

    private fun String.toFilter(): StoreFilter = when (this) {
        FILTER_ALL -> StoreFilter.All
        FILTER_ANYWHERE -> StoreFilter.Anywhere
        else -> StoreFilter.OneStore(this)
    }

    private companion object {
        const val KEY_SHOPPING = "shopping"
        const val KEY_FILTER = "filter"
        const val FILTER_ALL = "all"
        const val FILTER_ANYWHERE = "anywhere"
    }
}

/** "All" plus one chip per group that has items, in list order. */
internal fun filterChips(groups: List<ShoppingGroup>): List<FilterChipState> =
    listOf(FilterChipState(StoreFilter.All, "All", groups.sumOf { it.items.size })) +
        groups.map { group ->
            val filter = group.storeId?.let { StoreFilter.OneStore(it) } ?: StoreFilter.Anywhere
            FilterChipState(filter, group.title, group.items.size)
        }
