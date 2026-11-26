package com.nblaisdell.home.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nblaisdell.home.data.repository.DeviceSetupRepository
import com.nblaisdell.home.data.repository.KeyCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val isSetUp: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: DeviceSetupRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(repository.isSetUp, transient) { isSetUp, t ->
            t.copy(serverUrl = repository.serverUrl, isSetUp = isSetUp)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun saveKey(key: String) = run { repository.saveKey(key) }

    fun testKey() = run { repository.checkKey() }

    fun removeKey() {
        viewModelScope.launch {
            repository.removeKey()
            transient.value = SettingsUiState(message = "Key removed from this device.")
        }
    }

    private fun run(action: suspend () -> KeyCheck) {
        viewModelScope.launch {
            transient.value = transient.value.copy(busy = true, message = null)
            val (message, isError) = when (val result = action()) {
                is KeyCheck.Accepted -> "Connected as \"${result.deviceName}\"." to false
                KeyCheck.Rejected -> "The server rejected this key. Issue a new one with npm run issue-key." to true
                KeyCheck.NotFormattedLikeAKey -> "That doesn't look like a device key (it should start with home_)." to true
                is KeyCheck.Unreachable -> "Key saved, but the server couldn't be reached: ${result.reason}" to true
            }
            transient.value = SettingsUiState(message = message, messageIsError = isError)
        }
    }
}
