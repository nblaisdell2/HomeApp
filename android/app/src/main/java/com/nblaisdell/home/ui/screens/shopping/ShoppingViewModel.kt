package com.nblaisdell.home.ui.screens.shopping

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

data class ShoppingUiState(
    val loading: Boolean = true,
    val groups: List<ShoppingGroup> = emptyList(),
    val stores: List<StoreOption> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Syncing,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

/** The Shopping screen (HOME-2): the list grouped by store. */
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val storeRepository: StoreRepository,
    tracker: SyncStatusTracker,
) : ViewModel() {

    val uiState: StateFlow<ShoppingUiState> =
        combine(repository.items, storeRepository.stores, tracker.status) { items, stores, status ->
            ShoppingUiState(
                loading = false,
                groups = groupForDisplay(items, stores),
                stores = stores,
                syncStatus = status,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    fun add(name: String, store: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val storeId = store?.takeIf { it.isNotBlank() }?.let { storeRepository.findOrCreate(it) }
            repository.add(ItemDraft(name, storeId))
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(listOf(id)) }
    }
}
