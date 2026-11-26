package com.nblaisdell.home.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nblaisdell.home.data.sync.SyncStatus
import com.nblaisdell.home.ui.components.HomeScreenScaffold
import com.nblaisdell.home.ui.components.SectionCard
import com.nblaisdell.home.ui.components.SyncStatusIndicator
import com.nblaisdell.home.ui.navigation.TopLevelDestination
import com.nblaisdell.home.ui.theme.HomeTheme

/** Stateful entry point: gets its ViewModel from Hilt. */
@Composable
fun HomeScreen(
    onOpenDestination: (TopLevelDestination) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(uiState, onOpenDestination, onOpenSettings)
}

/** Stateless UI: a dashboard tile per area of the app. Easy to preview and test. */
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onOpenDestination: (TopLevelDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeScreenScaffold(
        title = uiState.greeting,
        modifier = modifier,
        actions = {
            SyncStatusIndicator(uiState.syncStatus)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
            }
        },
    ) {
        val spacing = HomeTheme.spacing
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            if (uiState.needsSetup) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionCard(
                        title = "Set up this device",
                        icon = Icons.Outlined.Key,
                        onClick = onOpenSettings,
                    ) {
                        Text(
                            "Add this device's API key so it syncs with your other device.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(uiState.tiles, key = { it.destination.name }) { tile ->
                SectionCard(
                    title = tile.destination.label,
                    icon = tile.destination.unselectedIcon,
                    onClick = { onOpenDestination(tile.destination) },
                ) {
                    Text(
                        tile.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun HomePhonePreview() {
    HomeTheme { HomeContent(HomeUiState(syncStatus = SyncStatus.Live), {}, {}) }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun HomeTabletPreview() {
    HomeTheme { HomeContent(HomeUiState(syncStatus = SyncStatus.Offline), {}, {}) }
}
