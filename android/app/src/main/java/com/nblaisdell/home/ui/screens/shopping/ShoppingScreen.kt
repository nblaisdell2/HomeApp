package com.nblaisdell.home.ui.screens.shopping

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ShoppingCartCheckout
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
fun ShoppingScreen(onOpenStores: () -> Unit = {}, viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShoppingContent(
        state,
        ShoppingActions(
            onAdd = viewModel::add,
            onUpdate = viewModel::update,
            onDelete = viewModel::delete,
            onRestore = viewModel::restore,
            onOpenStores = onOpenStores,
            onStartShopping = viewModel::startShopping,
            onStopShopping = viewModel::stopShopping,
            onFilter = viewModel::setFilter,
            onPurchase = viewModel::purchase,
            onUnpurchase = viewModel::unpurchase,
        ),
    )
}

/** Everything the Shopping screen can ask the ViewModel to do. */
data class ShoppingActions(
    val onAdd: (ItemForm) -> Unit = {},
    val onUpdate: (id: String, ItemForm) -> Unit = { _, _ -> },
    val onDelete: (ids: List<String>) -> Unit = {},
    val onRestore: (ids: List<String>) -> Unit = {},
    val onOpenStores: () -> Unit = {},
    val onStartShopping: () -> Unit = {},
    val onStopShopping: () -> Unit = {},
    val onFilter: (StoreFilter) -> Unit = {},
    val onPurchase: (id: String) -> Unit = {},
    val onUnpurchase: (id: String) -> Unit = {},
)

@Composable
fun ShoppingContent(state: ShoppingUiState, actions: ShoppingActions) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    // Long-press multi-select (HOME-17). Ids of rows that have since disappeared drop out.
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val allItems = state.groups.flatMap { it.items }
    val selected = selectedIds.filter { id -> allItems.any { it.id == id } }.toSet()
    val selecting = selected.isNotEmpty() && !state.shopping

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /** Shows [message] with Undo; [undo] runs if it's tapped. Replaces any snackbar showing. */
    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    /** Deletes, then offers Undo (deletes are tombstones, so Undo just restores them). */
    fun deleteWithUndo(ids: List<String>) {
        if (ids.isEmpty()) return
        actions.onDelete(ids)
        val label = if (ids.size == 1) {
            "Deleted ${allItems.firstOrNull { it.id == ids[0] }?.name ?: "item"}"
        } else {
            "Deleted ${ids.size} items"
        }
        offerUndo(label) { actions.onRestore(ids) }
    }

    /** Check-off in shopping mode (HOME-67): the item goes to history, and a mis-tap can be undone. */
    fun purchaseWithUndo(item: ShoppingListItem) {
        actions.onPurchase(item.id)
        offerUndo("Got ${item.name}") { actions.onUnpurchase(item.id) }
    }

    fun toggle(id: String) {
        selectedIds = if (id in selected) selected.toList() - id else selected.toList() + id
    }

    BackHandler(enabled = selecting) { selectedIds = emptyList() }
    BackHandler(enabled = state.shopping) { actions.onStopShopping() }
    if (state.shopping) KeepScreenOn()

    HomeScreenScaffold(
        title = when {
            selecting -> "${selected.size} selected"
            state.shopping -> "Shopping mode"
            else -> "Shopping"
        },
        navigationIcon = {
            if (selecting) {
                IconButton(onClick = { selectedIds = emptyList() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                }
            } else if (state.shopping) {
                IconButton(onClick = actions.onStopShopping) {
                    Icon(Icons.Filled.Close, contentDescription = "Exit shopping mode")
                }
            }
        },
        actions = {
            if (state.shopping) {
                SyncStatusIndicator(state.syncStatus, Modifier.padding(end = HomeTheme.spacing.medium))
            } else if (selecting) {
                IconButton(onClick = {
                    deleteWithUndo(selected.toList())
                    selectedIds = emptyList()
                }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete selected")
                }
            } else {
                SyncStatusIndicator(state.syncStatus)
                if (state.totalCount > 0) {
                    IconButton(onClick = {
                        selectedIds = emptyList()
                        actions.onStartShopping()
                    }) {
                        Icon(Icons.Outlined.ShoppingCartCheckout, contentDescription = "Start shopping")
                    }
                }
                IconButton(onClick = { showHistory = true }) {
                    Icon(Icons.Outlined.History, contentDescription = "Recently bought")
                }
                OverflowMenu(onOpenStores = actions.onOpenStores)
            }
        },
        floatingActionButton = {
            if (!selecting && !state.shopping) {
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
            state.shopping -> ShoppingModeList(state, actions, onCheck = ::purchaseWithUndo)
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
        ItemSheet(
            stores = state.stores,
            onDismiss = { adding = false },
            onSave = actions.onAdd,
            suggestions = state.history,
        )
    }
    if (showHistory) {
        HistorySheet(
            history = state.history,
            stores = state.stores,
            onListNames = state.onListNames,
            onReAdd = { entry ->
                actions.onUnpurchase(entry.itemId)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Added ${entry.name} back")
                }
            },
            onDismiss = { showHistory = false },
        )
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
 * Shopping mode (HOME-18): big check-off rows, optionally narrowed to one store with the chips
 * (HOME-66). Checking an item moves it to purchase history, so it leaves the list (with Undo).
 */
@Composable
private fun ShoppingModeList(
    state: ShoppingUiState,
    actions: ShoppingActions,
    onCheck: (ShoppingListItem) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = HomeTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(HomeTheme.spacing.small),
        ) {
            items(state.filterChips, key = { it.label + it.filter }) { chip ->
                FilterChip(
                    selected = chip.filter == state.filter,
                    onClick = { actions.onFilter(chip.filter) },
                    label = { Text("${chip.label} · ${chip.count}") },
                )
            }
        }
        if (state.isEmpty) {
            val allDone = state.totalCount == 0
            EmptyState(
                title = if (allDone) "All done" else "Nothing left here",
                message = if (allDone) "Everything on the list is checked off." else "Other stores still have items.",
                icon = Icons.Outlined.ShoppingCartCheckout,
                actionLabel = if (allDone) "Exit shopping mode" else "Show all",
                onAction = if (allDone) actions.onStopShopping else ({ actions.onFilter(StoreFilter.All) }),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                state.groups.forEach { group ->
                    stickyHeader(key = "header-${group.storeId}") { StoreHeader(group) }
                    items(group.items, key = { it.id }) { item ->
                        CheckOffRow(item, onCheck = { onCheck(item) }, Modifier.animateItem())
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** A large, whole-row tap target: in the store, one hand is usually holding something. */
@Composable
private fun CheckOffRow(item: ShoppingListItem, onCheck: () -> Unit, modifier: Modifier = Modifier) {
    var checked by remember(item.id) { mutableStateOf(false) }
    ListItem(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(enabled = !checked, onClickLabel = "Check off ${item.name}") {
                checked = true
                onCheck()
            },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        headlineContent = { Text(item.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = item.details()?.let { { Text(it) } },
    )
}

/** Keeps the display on while this is in the composition (shopping mode). */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
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

@Composable
private fun OverflowMenu(onOpenStores: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Manage stores") },
                leadingIcon = { Icon(Icons.Outlined.Storefront, contentDescription = null) },
                onClick = {
                    open = false
                    onOpenStores()
                },
            )
        }
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
