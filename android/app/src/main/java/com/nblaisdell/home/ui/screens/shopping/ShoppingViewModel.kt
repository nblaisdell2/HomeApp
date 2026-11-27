package com.nblaisdell.home.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nblaisdell.home.data.repository.ItemDraft
import com.nblaisdell.home.data.repository.ShoppingListItem
import com.nblaisdell.home.data.repository.ShoppingRepository
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
    val items: List<ShoppingListItem> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Syncing,
)

/**
 * Minimal shopping list used to prove out offline-first sync (HOME-13/64). The full Shopping
 * screen (stores, shopping mode, bulk delete) is built in its own epic.
 */
@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val storeRepository: StoreRepository,
    tracker: SyncStatusTracker,
) : ViewModel() {

    val uiState: StateFlow<ShoppingUiState> =
        combine(repository.items, tracker.status) { items, status ->
            ShoppingUiState(loading = false, items = items, syncStatus = status)
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
