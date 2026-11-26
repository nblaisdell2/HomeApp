package com.nblaisdell.home.ui.screens.placeholder

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.nblaisdell.home.ui.components.EmptyState
import com.nblaisdell.home.ui.components.HomeScreenScaffold
import com.nblaisdell.home.ui.navigation.TopLevelDestination
import com.nblaisdell.home.ui.theme.HomeTheme

/** Stand-in for screens whose epics haven't been built yet. */
@Composable
fun PlaceholderScreen(destination: TopLevelDestination, description: String) {
    HomeScreenScaffold(title = destination.label) {
        EmptyState(
            title = "${destination.label} is coming soon",
            message = description,
            icon = destination.unselectedIcon,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    HomeTheme { PlaceholderScreen(TopLevelDestination.Weather, "Hourly and weekly forecast.") }
}
