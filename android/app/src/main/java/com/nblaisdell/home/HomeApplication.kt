package com.nblaisdell.home

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nblaisdell.home.data.sync.LiveUpdates
import com.nblaisdell.home.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * App entry point. `@HiltAndroidApp` generates the root Hilt component.
 *
 * Implementing [Configuration.Provider] lets WorkManager use [HiltWorkerFactory], so
 * `@HiltWorker` classes can have dependencies injected. The default WorkManager
 * initializer is removed in AndroidManifest.xml for this to take effect.
 */
@HiltAndroidApp
class HomeApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var liveUpdates: LiveUpdates

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate() // Hilt injects the fields above here.
        syncScheduler.schedulePeriodicSync()
        liveUpdates.start()
    }
}
