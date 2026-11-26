package com.nblaisdell.home.data.sync

/** How current the local cache is compared to the server, shown in the UI. */
enum class SyncStatus {
    /** Connected to the live update stream; changes from other devices arrive within seconds. */
    Live,

    /** Last sync succeeded, but the live stream isn't connected (e.g. it's reconnecting). */
    UpToDate,

    /** Uploading local edits or pulling changes right now. */
    Syncing,

    /** No connection. Edits are saved locally and will upload when back online. */
    Offline,

    /** No API key has been set up on this device yet. */
    NotSetUp,

    /** The last sync failed for another reason, e.g. the server rejected the device key. */
    Error,
}
