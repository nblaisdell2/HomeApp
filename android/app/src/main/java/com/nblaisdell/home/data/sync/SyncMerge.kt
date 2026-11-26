package com.nblaisdell.home.data.sync

import com.nblaisdell.home.data.local.ShoppingItemEntity
import com.nblaisdell.home.data.remote.generated.model.ShoppingItem
import java.time.Instant

/**
 * Decides what to store locally when the server sends an item (HOME-13 conflict rule:
 * last write wins on `modifiedAt`).
 *
 * Returns the entity to write, or null to keep the local copy. The local copy is kept only when
 * it has an edit that hasn't been uploaded yet *and* that edit is newer than the server's; it
 * will win on the server too once it uploads.
 */
internal fun mergeServerItem(local: ShoppingItemEntity?, server: ShoppingItem): ShoppingItemEntity? {
    val serverModifiedAt = parseInstant(server.modifiedAt)
    if (local != null && local.pendingSync && local.modifiedAt > serverModifiedAt) return null
    return server.toEntity()
}

internal fun ShoppingItem.toEntity() = ShoppingItemEntity(
    id = id.toString(),
    name = name,
    store = store,
    modifiedAt = parseInstant(modifiedAt),
    deleted = deleted,
    serverVersion = version,
    pendingSync = false,
)

internal fun parseInstant(iso: String): Long = Instant.parse(iso).toEpochMilli()

internal fun formatInstant(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
