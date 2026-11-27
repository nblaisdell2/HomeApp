package com.nblaisdell.home.ui.screens.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
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
    onAdd: (name: String, store: String?) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    HomeScreenScaffold(
        title = "Shopping",
        actions = { SyncStatusIndicator(state.syncStatus, Modifier.padding(end = HomeTheme.spacing.medium)) },
    ) {
        Column(Modifier.fillMaxSize()) {
            AddItemRow(onAdd)
            when {
                state.loading -> LoadingState()
                state.isEmpty -> EmptyState(
                    title = "Nothing on the list",
                    message = "Items added here show up on your other device too.",
                    icon = Icons.Outlined.ShoppingCart,
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
                }
            }
        }
    }
}

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

@Composable
private fun AddItemRow(onAdd: (String, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var store by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (name.isNotBlank()) {
            onAdd(name, store)
            name = ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HomeTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(HomeTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item") },
            singleLine = true,
            modifier = Modifier.weight(2f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        OutlinedTextField(
            value = store,
            onValueChange = { store = it },
            label = { Text("Store") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = submit, enabled = name.isNotBlank()) {
            Icon(Icons.Filled.Add, contentDescription = "Add item")
        }
    }
}

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
            onAdd = { _, _ -> },
            onDelete = {},
        )
    }
}
