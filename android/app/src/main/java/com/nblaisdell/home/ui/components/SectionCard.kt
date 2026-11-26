package com.nblaisdell.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nblaisdell.home.ui.theme.HomeMetric
import com.nblaisdell.home.ui.theme.HomeTheme

/**
 * A titled card used for dashboard tiles and grouped content. Pass [onClick] to make the whole
 * card tappable (e.g. a dashboard tile that opens its screen).
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(HomeTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(HomeTheme.spacing.small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HomeTheme.spacing.small),
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            content()
        }
    }
    if (onClick != null) {
        ElevatedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), content = body)
    } else {
        ElevatedCard(modifier = modifier.fillMaxWidth(), content = body)
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SectionCardPreview() {
    HomeTheme {
        SectionCard(title = "Weather", icon = Icons.Outlined.WbSunny, onClick = {}) {
            Text("41°", style = TextStyle.HomeMetric)
            Text("Partly cloudy · H 45° L 30°", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
