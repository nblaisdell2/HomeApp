package com.nblaisdell.home.data.sync

import com.nblaisdell.home.data.local.ShoppingItemEntity
import com.nblaisdell.home.data.local.StoreEntity
import com.nblaisdell.home.data.remote.generated.model.ShoppingItem
import com.nblaisdell.home.data.remote.generated.model.Store
import java.time.Instant

/**
 * Decides what to store locally when the server sends a row (HOME-13 conflict rule:
 * last write wins on `modifiedAt`).
 *
 * The local copy is kept only when it has an edit that hasn't been uploaded yet *and* that edit
 * is newer than the server's; it will win on the server too once it uploads.
 */
private fun keepLocal(pending: Boolean, localModifiedAt: Long, serverModifiedAt: String) =
    pending && localModifiedAt > parseInstant(serverModifiedAt)

/** Returns the entity to write, or null to keep the local copy. */
internal fun mergeServerItem(local: ShoppingItemEntity?, server: ShoppingItem): ShoppingItemEntity? =
    if (local != null && keepLocal(local.pendingSync, local.modifiedAt, server.modifiedAt)) null
    else server.toEntity()

/** Returns the entity to write, or null to keep the local copy. */
internal fun mergeServerStore(local: StoreEntity?, server: Store): StoreEntity? =
    if (local != null && keepLocal(local.pendingSync, local.modifiedAt, server.modifiedAt)) null
    else server.toEntity()

internal fun ShoppingItem.toEntity() = ShoppingItemEntity(
    id = id.toString(),
    name = name,
    storeId = storeId?.toString(),
    aisle = aisle,
    note = note,
    purchasedAt = purchasedAt?.let(::parseInstant),
    modifiedAt = parseInstant(modifiedAt),
    deleted = deleted,
    serverVersion = version,
    pendingSync = false,
)

internal fun Store.toEntity() = StoreEntity(
    id = id.toString(),
    name = name,
    sortOrder = sortOrder,
    modifiedAt = parseInstant(modifiedAt),
    deleted = deleted,
    serverVersion = version,
    pendingSync = false,
)

internal fun parseInstant(iso: String): Long = Instant.parse(iso).toEpochMilli()

internal fun formatInstant(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
