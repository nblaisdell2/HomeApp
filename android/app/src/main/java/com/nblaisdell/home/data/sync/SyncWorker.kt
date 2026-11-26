package com.nblaisdell.home.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs a sync in the background via WorkManager: after every local edit (so it uploads even if
 * the app is closed right away) and every 15 minutes while the app is closed (HOME-64).
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: SyncCoordinator,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (coordinator.syncNow()) {
        SyncResult.Success, SyncResult.Unauthorized -> Result.success()
        SyncResult.Offline -> Result.retry()
        is SyncResult.Failed -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
