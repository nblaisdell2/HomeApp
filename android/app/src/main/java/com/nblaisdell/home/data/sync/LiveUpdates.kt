package com.nblaisdell.home.data.sync

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nblaisdell.home.data.remote.DeviceKeyStore
import com.nblaisdell.home.di.ApplicationScope
import com.nblaisdell.home.di.SseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Cross-device updates without a push service (HOME-64).
 *
 * While the app is on screen it keeps a Server-Sent Events connection to `/v1/events`. Every
 * `change` event triggers a sync, so an item added on the phone shows on the wall tablet within
 * about a second. Each time the app comes to the foreground it also syncs right away, and the
 * connection is dropped when the app goes to the background (the 15-minute WorkManager job
 * covers that case).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LiveUpdates @Inject constructor(
    @SseClient private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val keyStore: DeviceKeyStore,
    private val coordinator: SyncCoordinator,
    private val tracker: SyncStatusTracker,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    // All connection state is touched only from this single-threaded dispatcher.
    private val serial = Dispatchers.Default.limitedParallelism(1)
    private var inForeground = false
    private var eventSource: EventSource? = null
    private var reconnectJob: Job? = null
    private var failures = 0

    /** Sync requests from SSE events, coalesced so a burst of changes runs one sync. */
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch { for (request in syncRequests) coordinator.syncNow() }
        // Setting or replacing the device key: reconnect with the new key.
        scope.launch {
            keyStore.apiKey.drop(1).collect {
                withContext(serial) { if (inForeground) reconnect(immediately = true) }
                syncRequests.trySend(Unit)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch(serial) {
            inForeground = true
            syncRequests.trySend(Unit) // refresh on resume
            connect()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        scope.launch(serial) {
            inForeground = false
            disconnect()
        }
    }

    private fun connect() {
        if (eventSource != null || !inForeground) return
        if (keyStore.currentKeyBlocking() == null) return

        val request = Request.Builder()
            .url(baseUrl.resolve("v1/events")!!)
            .header("Accept", "text/event-stream")
            .build()
        eventSource = EventSources.createFactory(client).newEventSource(request, listener)
    }

    private fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        eventSource?.cancel()
        eventSource = null
        tracker.onLiveConnectionChanged(false)
    }

    private fun reconnect(immediately: Boolean) {
        disconnect()
        if (!inForeground) return
        val backoffMs = if (immediately) 0L else min(60_000L, 1_000L shl min(failures, 6))
        reconnectJob = scope.launch(serial) {
            delay(backoffMs)
            connect()
        }
    }

    private val listener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            scope.launch(serial) { failures = 0 }
            tracker.onLiveConnectionChanged(true)
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            // "ready" arrives on every (re)connect, so anything missed while disconnected is pulled.
            if (type == "change" || type == "ready") syncRequests.trySend(Unit)
        }

        override fun onClosed(eventSource: EventSource) {
            // Server closed the stream (e.g. a redeploy): reconnect shortly.
            scope.launch(serial) { if (this@LiveUpdates.eventSource === eventSource) reconnect(immediately = false) }
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            scope.launch(serial) {
                if (this@LiveUpdates.eventSource !== eventSource) return@launch // we cancelled it
                Log.i(TAG, "Live updates disconnected (${response?.code ?: t?.javaClass?.simpleName})")
                if (response?.code == 401) {
                    // Key revoked or wrong; wait until a new key is saved.
                    disconnect()
                    tracker.onSyncFinished(SyncResult.Unauthorized)
                } else {
                    failures++
                    reconnect(immediately = false)
                }
            }
        }
    }

    private companion object {
        const val TAG = "LiveUpdates"
    }
}
