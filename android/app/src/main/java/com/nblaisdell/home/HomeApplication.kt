package com.nblaisdell.home

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nblaisdell.home.data.sync.LiveUpdates
import com.nblaisdell.home.data.sync.SyncScheduler
import com.nblaisdell.home.ui.quickadd.QuickAddActivity
import com.nblaisdell.home.ui.widget.ShoppingWidgetUpdater
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
    @Inject lateinit var widgetUpdater: ShoppingWidgetUpdater

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate() // Hilt injects the fields above here.
        syncScheduler.schedulePeriodicSync()
        liveUpdates.start()
        widgetUpdater.start()
        addQuickAddShortcut()
    }

    /**
     * Long-press the launcher icon -> "Add shopping item" (HOME-68). Added at runtime rather than
     * in shortcuts.xml because static shortcuts need a fixed package name, and debug builds use
     * a ".debug" suffix.
     */
    private fun addQuickAddShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, "add_shopping_item")
            .setShortLabel(getString(R.string.shortcut_add_item_short))
            .setLongLabel(getString(R.string.shortcut_add_item_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_add_shopping))
            .setIntent(Intent(Intent.ACTION_VIEW, null, this, QuickAddActivity::class.java))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }
}
