package com.nblaisdell.home.data.sync

import com.nblaisdell.home.data.remote.DeviceKeyStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point for running a sync, used by the background worker, the live update
 * stream and app resume. Runs one sync at a time and reports progress to [SyncStatusTracker].
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val engine: SyncEngine,
    private val keyStore: DeviceKeyStore,
    private val tracker: SyncStatusTracker,
) {
    private val mutex = Mutex()

    suspend fun syncNow(): SyncResult = mutex.withLock {
        // Not set up yet: nothing to talk to the server with. Edits stay queued locally.
        if (keyStore.apiKey.first() == null) return SyncResult.Unauthorized

        tracker.onSyncStarted()
        val result = engine.sync()
        tracker.onSyncFinished(result)
        result
    }
}
