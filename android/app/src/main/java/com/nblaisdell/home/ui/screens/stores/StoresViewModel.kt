package com.nblaisdell.home.ui.screens.stores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nblaisdell.home.data.repository.StoreOption
import com.nblaisdell.home.data.repository.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoresUiState(val loading: Boolean = true, val stores: List<StoreOption> = emptyList())

/** Manage the store list (HOME-66): add, rename, reorder, delete. */
@HiltViewModel
class StoresViewModel @Inject constructor(
    private val repository: StoreRepository,
) : ViewModel() {

    val uiState: StateFlow<StoresUiState> = repository.stores
        .map { StoresUiState(loading = false, stores = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoresUiState())

    fun add(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.findOrCreate(name) }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { repository.rename(id, name) }
    }

    fun move(id: String, up: Boolean) {
        viewModelScope.launch { repository.move(id, up) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
