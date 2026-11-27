package com.nblaisdell.home.ui.screens.shopping

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nblaisdell.home.data.repository.HistoryEntry
import com.nblaisdell.home.data.repository.StoreOption
import com.nblaisdell.home.ui.components.EmptyState
import com.nblaisdell.home.ui.theme.HomeTheme
import java.util.Locale

/**
 * "Recently bought" (HOME-67): one entry per item name, newest first. Tapping one puts it back
 * on the list with its store, aisle and notes; names already on the list can't be added twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    history: List<HistoryEntry>,
    stores: List<StoreOption>,
    onListNames: Set<String>,
    onReAdd: (HistoryEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val storeNames = stores.associate { it.id to it.name }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Recently bought",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = HomeTheme.spacing.large, vertical = HomeTheme.spacing.small),
            )
            if (history.isEmpty()) {
                EmptyState(
                    title = "Nothing bought yet",
                    message = "Items you check off in shopping mode show up here for quick re-adding.",
                    icon = Icons.Outlined.History,
                )
            } else {
                LazyColumn {
                    items(history, key = { it.itemId }) { entry ->
                        val onList = entry.name.normalized() in onListNames
                        val where = entry.storeId?.let { storeNames[it] } ?: ANYWHERE
                        val ago = DateUtils.getRelativeTimeSpanString(
                            entry.purchasedAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        )
                        ListItem(
                            modifier = Modifier.clickable(enabled = !onList, onClickLabel = "Add ${entry.name} back") {
                                onReAdd(entry)
                            },
                            headlineContent = { Text(entry.name) },
                            supportingContent = { Text("$where · bought $ago") },
                            trailingContent = {
                                if (onList) {
                                    Text("On list", style = MaterialTheme.typography.labelLarge)
                                } else {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun String.normalized() = trim().lowercase(Locale.ROOT)
