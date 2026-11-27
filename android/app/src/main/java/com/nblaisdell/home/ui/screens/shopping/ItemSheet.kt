package com.nblaisdell.home.ui.screens.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.PopupProperties
import com.nblaisdell.home.data.repository.HistoryEntry
import com.nblaisdell.home.data.repository.StoreOption
import com.nblaisdell.home.ui.theme.HomeTheme

/**
 * What the item form submits. The store is either an existing one ([storeId]), a new one to
 * create from what was typed ([newStoreName]), or neither ("Anywhere").
 */
data class ItemForm(
    val name: String,
    val storeId: String? = null,
    val newStoreName: String? = null,
    val aisle: String = "",
    val note: String = "",
)

/**
 * Add/edit form in a bottom sheet (HOME-15, HOME-16). In add mode, saving clears the name but
 * keeps the store and the sheet open, so several items for one store go in quickly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSheet(
    stores: List<StoreOption>,
    onDismiss: () -> Unit,
    onSave: (ItemForm) -> Unit,
    initial: ItemForm? = null,
    suggestions: List<HistoryEntry> = emptyList(),
) {
    val editing = initial != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var storeId by rememberSaveable { mutableStateOf(initial?.storeId) }
    var storeText by rememberSaveable {
        mutableStateOf(initial?.storeId?.let { id -> stores.firstOrNull { it.id == id }?.name } ?: "")
    }
    var aisle by rememberSaveable { mutableStateOf(initial?.aisle ?: "") }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    val nameFocus = remember { FocusRequester() }

    val submit = {
        if (name.isNotBlank()) {
            val typed = storeText.trim()
            val newStore = typed.takeIf { storeId == null && it.isNotEmpty() && !it.equals(ANYWHERE, true) }
            onSave(ItemForm(name.trim(), storeId, newStore, aisle, note))
            if (editing) {
                onDismiss()
            } else {
                name = ""
                aisle = ""
                note = ""
                nameFocus.requestFocus()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = HomeTheme.spacing.large)
                .padding(bottom = HomeTheme.spacing.large)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(HomeTheme.spacing.medium),
        ) {
            Text(if (editing) "Edit item" else "Add item", style = MaterialTheme.typography.titleLarge)
            NameField(
                value = name,
                onValueChange = { name = it },
                // Autocomplete from purchase history (HOME-67), only when adding.
                suggestions = if (editing) emptyList() else matchHistory(name, suggestions),
                onPick = { entry ->
                    name = entry.name
                    storeId = entry.storeId?.takeIf { id -> stores.any { it.id == id } }
                    storeText = storeId?.let { id -> stores.first { it.id == id }.name } ?: ""
                    aisle = entry.aisle ?: ""
                    note = entry.note ?: ""
                },
                onDone = submit,
                modifier = Modifier.focusRequester(nameFocus),
            )
            StorePicker(
                stores = stores,
                text = storeText,
                onTextChange = {
                    storeText = it
                    // Typing an existing store's name selects it; anything else will be created.
                    storeId = stores.firstOrNull { s -> s.name.equals(it.trim(), ignoreCase = true) }?.id
                },
                onPick = { picked ->
                    storeId = picked?.id
                    storeText = picked?.name ?: ""
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HomeTheme.spacing.small)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Quantity / notes") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = aisle,
                    onValueChange = { aisle = it },
                    label = { Text("Aisle") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!editing) TextButton(onClick = onDismiss) { Text("Done") }
                Button(onClick = submit, enabled = name.isNotBlank()) { Text(if (editing) "Save" else "Add") }
            }
        }
    }

    LaunchedEffect(Unit) { if (!editing) nameFocus.requestFocus() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<HistoryEntry>,
    onPick: (HistoryEntry) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Item") },
            singleLine = true,
            modifier = modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                expanded = false
                onDone()
            }),
        )
        DropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
            properties = PopupProperties(focusable = false),
        ) {
            suggestions.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.name) },
                    leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    onClick = {
                        onPick(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * History entries whose name starts with what's typed (then ones containing it), excluding an
 * exact match, at most [limit]. Nothing until at least one character is typed.
 */
internal fun matchHistory(query: String, history: List<HistoryEntry>, limit: Int = 5): List<HistoryEntry> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val candidates = history.filter { it.name.contains(q, ignoreCase = true) && !it.name.equals(q, ignoreCase = true) }
    val (prefix, rest) = candidates.partition { it.name.startsWith(q, ignoreCase = true) }
    return (prefix + rest).take(limit)
}

/**
 * Store field: pick from the managed list, "Anywhere", or type a new name and pick "Add …"
 * (HOME-66: stores are a managed list, so "Costco" and "costco" don't split).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorePicker(
    stores: List<StoreOption>,
    text: String,
    onTextChange: (String) -> Unit,
    onPick: (StoreOption?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val typed = text.trim()
    val exact = stores.any { it.name.equals(typed, ignoreCase = true) }
    val matches = if (typed.isEmpty() || exact) stores else stores.filter { it.name.contains(typed, ignoreCase = true) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                onTextChange(it)
                expanded = true
            },
            label = { Text("Store") },
            placeholder = { Text(ANYWHERE) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
            // Not focusable, so the keyboard stays on the text field while the list filters.
            properties = PopupProperties(focusable = false),
        ) {
            DropdownMenuItem(
                text = { Text(ANYWHERE) },
                onClick = {
                    onPick(null)
                    expanded = false
                },
            )
            matches.forEach { store ->
                DropdownMenuItem(
                    text = { Text(store.name) },
                    onClick = {
                        onPick(store)
                        expanded = false
                    },
                )
            }
            if (typed.isNotEmpty() && !exact && !typed.equals(ANYWHERE, ignoreCase = true)) {
                DropdownMenuItem(
                    text = { Text("Add \"$typed\"") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = { expanded = false }, // kept as typed; created when the item is saved
                )
            }
        }
    }
}
