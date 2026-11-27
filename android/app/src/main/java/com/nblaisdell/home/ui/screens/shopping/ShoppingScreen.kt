package com.nblaisdell.home.ui.screens.shopping

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShoppingContent(
        state,
        ShoppingActions(
            onAdd = viewModel::add,
            onUpdate = viewModel::update,
            onDelete = viewModel::delete,
            onRestore = viewModel::restore,
        ),
    )
}

/** Everything the Shopping screen can ask the ViewModel to do. */
data class ShoppingActions(
    val onAdd: (ItemForm) -> Unit = {},
    val onUpdate: (id: String, ItemForm) -> Unit = { _, _ -> },
    val onDelete: (ids: List<String>) -> Unit = {},
    val onRestore: (ids: List<String>) -> Unit = {},
)

@Composable
fun ShoppingContent(state: ShoppingUiState, actions: ShoppingActions) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    // Long-press multi-select (HOME-17). Ids of rows that have since disappeared drop out.
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val allItems = state.groups.flatMap { it.items }
    val selected = selectedIds.filter { id -> allItems.any { it.id == id } }.toSet()
    val selecting = selected.isNotEmpty()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /** Deletes, then offers Undo (deletes are tombstones, so Undo just restores them). */
    fun deleteWithUndo(ids: List<String>) {
        if (ids.isEmpty()) return
        actions.onDelete(ids)
        val label = if (ids.size == 1) {
            "Deleted ${allItems.firstOrNull { it.id == ids[0] }?.name ?: "item"}"
        } else {
            "Deleted ${ids.size} items"
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(label, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) actions.onRestore(ids)
        }
    }

    fun toggle(id: String) {
        selectedIds = if (id in selected) selected.toList() - id else selected.toList() + id
    }

    BackHandler(enabled = selecting) { selectedIds = emptyList() }

    HomeScreenScaffold(
        title = if (selecting) "${selected.size} selected" else "Shopping",
        navigationIcon = {
            if (selecting) {
                IconButton(onClick = { selectedIds = emptyList() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                }
            }
        },
        actions = {
            if (selecting) {
                IconButton(onClick = {
                    deleteWithUndo(selected.toList())
                    selectedIds = emptyList()
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete selected")
                }
            } else {
                SyncStatusIndicator(state.syncStatus, Modifier.padding(end = HomeTheme.spacing.medium))
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add item") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
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
                        ItemRow(
                            item = item,
                            selected = item.id in selected,
                            selecting = selecting,
                            onClick = { if (selecting) toggle(item.id) else editingId = item.id },
                            onLongClick = { toggle(item.id) },
                            onSwipeDelete = { deleteWithUndo(listOf(item.id)) },
                            modifier = Modifier.animateItem(),
                        )
                        HorizontalDivider()
                    }
                }
                item { Spacer(Modifier.height(FAB_CLEARANCE)) }
            }
        }
    }

    if (adding) {
        ItemSheet(stores = state.stores, onDismiss = { adding = false }, onSave = actions.onAdd)
    }
    val editing = editingId?.let { id -> allItems.firstOrNull { it.id == id } }
    if (editing != null) {
        ItemSheet(
            stores = state.stores,
            initial = ItemForm(editing.name, editing.storeId, aisle = editing.aisle ?: "", note = editing.note ?: ""),
            onDismiss = { editingId = null },
            onSave = { actions.onUpdate(editing.id, it) },
        )
    }
}

/**
 * One list row: tap to edit, long-press to start selecting, swipe left to delete (HOME-17).
 * Swiping is off while selecting so a stray swipe can't delete a different item.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(
    item: ShoppingListItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !selecting,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onSwipeDelete() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = HomeTheme.spacing.large),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        ListItem(
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Select",
            ),
            colors = if (selected) {
                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                ListItemDefaults.colors()
            },
            leadingContent = if (selected) {
                { Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
            headlineContent = { Text(item.name) },
            supportingContent = item.details()?.let { { Text(it) } },
        )
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
            ShoppingActions(),
        )
    }
}
