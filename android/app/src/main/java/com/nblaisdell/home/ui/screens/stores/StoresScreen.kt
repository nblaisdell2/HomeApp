package com.nblaisdell.home.ui.screens.stores

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nblaisdell.home.data.repository.StoreOption
import com.nblaisdell.home.ui.components.EmptyState
import com.nblaisdell.home.ui.components.HomeScreenScaffold
import com.nblaisdell.home.ui.components.LoadingState
import com.nblaisdell.home.ui.theme.HomeTheme

@Composable
fun StoresScreen(onBack: () -> Unit, viewModel: StoresViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StoresContent(
        state = state,
        onBack = onBack,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onMove = viewModel::move,
        onDelete = viewModel::delete,
    )
}

/** What a dialog on this screen is editing; null [storeId] means a new store. */
private sealed interface StoreDialog {
    data class Name(val storeId: String?, val current: String) : StoreDialog
    data class ConfirmDelete(val store: StoreOption) : StoreDialog
}

@Composable
fun StoresContent(
    state: StoresUiState,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onMove: (id: String, up: Boolean) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    // Dialog state as plain values so it survives rotation.
    var nameDialogId by rememberSaveable { mutableStateOf<String?>(null) }
    var nameDialogOpen by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }

    HomeScreenScaffold(
        title = "Stores",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    nameDialogId = null
                    nameDialogOpen = true
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add store") },
            )
        },
    ) {
        when {
            state.loading -> LoadingState()
            state.stores.isEmpty() -> EmptyState(
                title = "No stores yet",
                message = "Stores you add here, or while adding an item, group the shopping list.",
                icon = Icons.Outlined.Storefront,
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(state.stores, key = { _, s -> s.id }) { index, store ->
                    ListItem(
                        modifier = Modifier.animateItem(),
                        headlineContent = { Text(store.name) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onMove(store.id, true) }, enabled = index > 0) {
                                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move ${store.name} up")
                                }
                                IconButton(
                                    onClick = { onMove(store.id, false) },
                                    enabled = index < state.stores.lastIndex,
                                ) {
                                    Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move ${store.name} down")
                                }
                                IconButton(onClick = {
                                    nameDialogId = store.id
                                    nameDialogOpen = true
                                }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Rename ${store.name}")
                                }
                                IconButton(onClick = { deleteId = store.id }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${store.name}")
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    val dialog: StoreDialog? = when {
        nameDialogOpen -> StoreDialog.Name(
            nameDialogId,
            state.stores.firstOrNull { it.id == nameDialogId }?.name ?: "",
        )
        deleteId != null -> state.stores.firstOrNull { it.id == deleteId }?.let { StoreDialog.ConfirmDelete(it) }
        else -> null
    }
    when (dialog) {
        is StoreDialog.Name -> StoreNameDialog(
            title = if (dialog.storeId == null) "Add store" else "Rename store",
            initial = dialog.current,
            onDismiss = { nameDialogOpen = false },
            onConfirm = { name ->
                if (dialog.storeId == null) onAdd(name) else onRename(dialog.storeId, name)
                nameDialogOpen = false
            },
        )
        is StoreDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete ${dialog.store.name}?") },
            text = { Text("Items at this store move to Anywhere.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(dialog.store.id)
                    deleteId = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
        )
        null -> Unit
    }
}

@Composable
private fun StoreNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun StoresPreview() {
    HomeTheme {
        StoresContent(
            state = StoresUiState(
                loading = false,
                stores = listOf(StoreOption("1", "Costco"), StoreOption("2", "Aldi"), StoreOption("3", "Target")),
            ),
            onBack = {},
            onAdd = {},
            onRename = { _, _ -> },
            onMove = { _, _ -> },
            onDelete = {},
        )
    }
}
