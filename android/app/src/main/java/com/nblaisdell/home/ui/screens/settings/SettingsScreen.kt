package com.nblaisdell.home.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nblaisdell.home.ui.components.HomeScreenScaffold
import com.nblaisdell.home.ui.components.SectionCard
import com.nblaisdell.home.ui.theme.HomeTheme

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onBack = onBack,
        onSaveKey = viewModel::saveKey,
        onTestKey = viewModel::testKey,
        onRemoveKey = viewModel::removeKey,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSaveKey: (String) -> Unit,
    onTestKey: () -> Unit,
    onRemoveKey: () -> Unit,
) {
    var keyInput by rememberSaveable { mutableStateOf("") }
    val spacing = HomeTheme.spacing

    HomeScreenScaffold(
        title = "Settings",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(spacing.medium)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            SectionCard(title = "This device", icon = Icons.Outlined.Key) {
                Text(
                    if (state.isSetUp) "An API key is set up." else "No API key yet. Data stays on this device until one is added.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Server: ${state.serverUrl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(if (state.isSetUp) "Replace API key" else "API key") },
                    placeholder = { Text("home_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Issue a key on the server with: npm run issue-key -- --name tablet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Button(
                        onClick = { onSaveKey(keyInput); keyInput = "" },
                        enabled = keyInput.isNotBlank() && !state.busy,
                    ) { Text("Save key") }
                    if (state.isSetUp) {
                        OutlinedButton(onClick = onTestKey, enabled = !state.busy) { Text("Test connection") }
                        TextButton(onClick = onRemoveKey, enabled = !state.busy) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun SettingsPreview() {
    HomeTheme {
        SettingsContent(
            SettingsUiState(serverUrl = "http://10.0.2.2:3000/", isSetUp = true, message = "Connected as \"tablet\"."),
            {}, {}, {}, {},
        )
    }
}
