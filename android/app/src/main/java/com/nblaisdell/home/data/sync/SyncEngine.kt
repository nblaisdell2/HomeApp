package com.nblaisdell.home.data.sync

import com.nblaisdell.home.data.local.ShoppingItemDao
import com.nblaisdell.home.data.local.StoreDao
import com.nblaisdell.home.data.local.SyncStateDao
import com.nblaisdell.home.data.local.TransactionRunner
import com.nblaisdell.home.data.remote.ApiException
import com.nblaisdell.home.data.remote.generated.api.ShoppingApi
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

sealed interface SyncResult {
    data object Success : SyncResult
    data object Offline : SyncResult
    data object Unauthorized : SyncResult
    data class Failed(val error: Throwable) : SyncResult
}

/**
 * One sync pass (HOME-13): upload pending local edits, then pull everything the server changed
 * since the last pull. Safe to run repeatedly; the server ignores stale uploads and re-pulled
 * rows merge to the same result.
 *
 * Tables are pushed in [syncs] order (stores before the items that point at them), and all
 * pushes happen before any pull, so a pull never overwrites an edit that was about to upload.
 * After pulling, stores with the same name are merged ([StoreMerger]).
 */
class SyncEngine internal constructor(
    private val syncs: List<EntitySync>,
    private val storeMerger: StoreMerger? = null,
) {

    @Inject
    constructor(
        api: ShoppingApi,
        storeDao: StoreDao,
        itemDao: ShoppingItemDao,
        syncStateDao: SyncStateDao,
        transaction: TransactionRunner,
    ) : this(
        listOf(
            StoreSync(api, storeDao, syncStateDao, transaction),
            ShoppingItemSync(api, itemDao, syncStateDao, transaction),
        ),
        StoreMerger(storeDao, itemDao, transaction),
    )

    suspend fun sync(): SyncResult = try {
        for (sync in syncs) sync.push()
        for (sync in syncs) sync.pull()
        // Pulling can reveal a store the other device created under the same name; merging
        // it makes new edits, which are uploaded right away.
        if (storeMerger?.mergeDuplicates() == true) {
            for (sync in syncs) sync.push()
        }
        SyncResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        SyncResult.Offline
    } catch (e: ApiException) {
        if (e.isUnauthorized) SyncResult.Unauthorized else SyncResult.Failed(e)
    } catch (e: Exception) {
        SyncResult.Failed(e)
    }

    companion object {
        const val SHOPPING_CURSOR = ShoppingItemSync.CURSOR
        const val STORES_CURSOR = StoreSync.CURSOR
    }
}
