package com.nblaisdell.home.ui.quickadd

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nblaisdell.home.data.repository.ItemDraft
import com.nblaisdell.home.data.repository.ShoppingRepository
import com.nblaisdell.home.data.repository.StoreOption
import com.nblaisdell.home.data.repository.StoreRepository
import com.nblaisdell.home.ui.screens.shopping.ANYWHERE
import com.nblaisdell.home.ui.screens.shopping.StorePicker
import com.nblaisdell.home.ui.theme.HomeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick add (HOME-68): a small dialog over the home screen, opened from the widget's "+" or the
 * launcher shortcut. Saves straight to Room like any other add, so it works offline and syncs.
 */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

    @Inject lateinit var shoppingRepository: ShoppingRepository
    @Inject lateinit var storeRepository: StoreRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeTheme {
                val stores by storeRepository.stores.collectAsStateWithLifecycle(emptyList())
                QuickAddDialog(stores = stores, onAdd = ::save, onCancel = ::finish)
            }
        }
    }

    private fun save(name: String, storeId: String?, newStoreName: String?) {
        lifecycleScope.launch {
            val store = newStoreName?.let { storeRepository.findOrCreate(it) } ?: storeId
            shoppingRepository.add(ItemDraft(name, store))
            Toast.makeText(this@QuickAddActivity, "Added $name", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

@Composable
private fun QuickAddDialog(
    stores: List<StoreOption>,
    onAdd: (name: String, storeId: String?, newStoreName: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var storeText by rememberSaveable { mutableStateOf("") }
    var storeId by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val submit = {
        if (name.isNotBlank()) {
            val typed = storeText.trim()
            val newStore = typed.takeIf { storeId == null && it.isNotEmpty() && !it.equals(ANYWHERE, true) }
            onAdd(name.trim(), storeId, newStore)
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add to shopping list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HomeTheme.spacing.medium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                StorePicker(
                    stores = stores,
                    text = storeText,
                    onTextChange = {
                        storeText = it
                        storeId = stores.firstOrNull { s -> s.name.equals(it.trim(), ignoreCase = true) }?.id
                    },
                    onPick = { picked ->
                        storeId = picked?.id
                        storeText = picked?.name ?: ""
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = submit, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = {
            Row { TextButton(onClick = onCancel) { Text("Cancel") } }
        },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
