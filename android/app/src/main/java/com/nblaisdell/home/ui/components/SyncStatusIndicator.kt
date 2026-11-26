package com.nblaisdell.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nblaisdell.home.data.sync.SyncStatus
import com.nblaisdell.home.ui.theme.HomeTheme

/** Small icon + label showing whether local data is in sync with the server. */
@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val (icon, label, tint) = presentation(status)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HomeTheme.spacing.extraSmall),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        if (showLabel) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

private data class Presentation(
    val icon: ImageVector,
    val label: String,
    val tint: androidx.compose.ui.graphics.Color,
)

@Composable
private fun presentation(status: SyncStatus): Presentation {
    val colors = MaterialTheme.colorScheme
    return when (status) {
        SyncStatus.Live -> Presentation(Icons.Outlined.CloudDone, "Live", colors.primary)
        SyncStatus.UpToDate -> Presentation(Icons.Outlined.CloudDone, "Up to date", colors.onSurfaceVariant)
        SyncStatus.Syncing -> Presentation(Icons.Outlined.CloudSync, "Syncing", colors.onSurfaceVariant)
        SyncStatus.Offline -> Presentation(Icons.Outlined.CloudOff, "Offline", colors.onSurfaceVariant)
        SyncStatus.NotSetUp -> Presentation(Icons.Outlined.SyncProblem, "Not set up", colors.tertiary)
        SyncStatus.Error -> Presentation(Icons.Outlined.SyncProblem, "Sync error", colors.error)
    }
}

@Preview(showBackground = true)
@Composable
private fun SyncStatusIndicatorPreview() {
    HomeTheme {
        androidx.compose.foundation.layout.Column {
            SyncStatus.entries.forEach { SyncStatusIndicator(it) }
        }
    }
}
