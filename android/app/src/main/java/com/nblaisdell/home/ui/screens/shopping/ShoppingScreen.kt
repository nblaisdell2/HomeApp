package com.nblaisdell.home.ui.screens.shopping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nblaisdell.home.data.repository.ShoppingListItem
import com.nblaisdell.home.data.repository.StoreOption
import com.nblaisdell.home.data.sync.SyncStatus
import com.nblaisdell.home.ui.components.EmptyState
import com.nblaisdell.home.ui.components.HomeScreenScaffold
import com.nblaisdell.home.ui.components.LoadingState
import com.nblaisdell.home.ui.components.SyncStatusIndicator
import com.nblaisdell.home.ui.theme.HomeTheme

@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShoppingContent(state, onAdd = viewModel::add, onDelete = viewModel::delete)
}

@Composable
fun ShoppingContent(
    state: ShoppingUiState,
    onAdd: (ItemForm) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    HomeScreenScaffold(
        title = "Shopping",
        actions = { SyncStatusIndicator(state.syncStatus, Modifier.padding(end = HomeTheme.spacing.medium)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add item") },
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.isEmpty -> EmptyState(
                    title = "Nothing on the list",
                    message = "Items added here show up on your other device too.",
                    icon = Icons.Outlined.ShoppingCart,
                    actionLabel = "Add item",
                    onAction = { adding = true },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    state.groups.forEach { group ->
                        stickyHeader(key = "header-${group.storeId}") { StoreHeader(group) }
                        items(group.items, key = { it.id }) { item ->
                            ListItem(
                                headlineContent = { Text(item.name) },
                                supportingContent = item.details()?.let { { Text(it) } },
                                trailingContent = {
                                    IconButton(onClick = { onDelete(item.id) }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Remove ${item.name}")
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                    item { Spacer(Modifier.height(FAB_CLEARANCE)) }
                }
            }
        }
    }

    if (adding) {
        ItemSheet(stores = state.stores, onDismiss = { adding = false }, onSave = onAdd)
    }
}

/** Room below the last row so the FAB never covers it. */
private val FAB_CLEARANCE = 88.dp

/** Sticky section header naming the store and how many items to get there. */
@Composable
internal fun StoreHeader(group: ShoppingGroup) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = HomeTheme.spacing.medium, vertical = HomeTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                group.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${group.items.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Second line of a row: quantity/notes and aisle, e.g. "2 gallons · Aisle 4". */
internal fun ShoppingListItem.details(): String? =
    listOfNotNull(note, aisle?.let { "Aisle $it" }).joinToString(" · ").ifEmpty { null }

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ShoppingPreview() {
    HomeTheme {
        ShoppingContent(
            ShoppingUiState(
                loading = false,
                groups = groupForDisplay(
                    listOf(
                        ShoppingListItem("1", "Milk", "aldi", aisle = "4", note = "2 gallons"),
                        ShoppingListItem("2", "Eggs", "aldi", aisle = "4"),
                        ShoppingListItem("3", "Coffee filters"),
                    ),
                    listOf(StoreOption("aldi", "Aldi")),
                ),
                syncStatus = SyncStatus.Live,
            ),
            onAdd = {},
            onDelete = {},
        )
    }
}
