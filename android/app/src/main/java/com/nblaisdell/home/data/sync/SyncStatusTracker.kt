package com.nblaisdell.home.data.sync

import com.nblaisdell.home.data.remote.DeviceKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Collects what the sync pieces report and turns it into one [SyncStatus] for the UI. */
@Singleton
class SyncStatusTracker @Inject constructor(
    keyStore: DeviceKeyStore,
) {
    private val liveConnected = MutableStateFlow(false)
    private val syncing = MutableStateFlow(false)
    private val lastResult = MutableStateFlow<SyncResult?>(null)

    val status: Flow<SyncStatus> = combine(
        keyStore.apiKey.map { it != null },
        liveConnected,
        syncing,
        lastResult,
    ) { hasKey, live, isSyncing, result ->
        when {
            !hasKey -> SyncStatus.NotSetUp
            isSyncing -> SyncStatus.Syncing
            result is SyncResult.Unauthorized -> SyncStatus.Error
            live -> SyncStatus.Live
            result is SyncResult.Offline -> SyncStatus.Offline
            result is SyncResult.Failed -> SyncStatus.Error
            result is SyncResult.Success -> SyncStatus.UpToDate
            else -> SyncStatus.Syncing
        }
    }.distinctUntilChanged()

    fun onLiveConnectionChanged(connected: Boolean) {
        liveConnected.value = connected
    }

    fun onSyncStarted() {
        syncing.value = true
    }

    fun onSyncFinished(result: SyncResult) {
        lastResult.value = result
        syncing.value = false
    }
}
