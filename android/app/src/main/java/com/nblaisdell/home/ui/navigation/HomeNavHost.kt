package com.nblaisdell.home.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nblaisdell.home.ui.screens.home.HomeScreen
import com.nblaisdell.home.ui.screens.placeholder.PlaceholderScreen
import com.nblaisdell.home.ui.screens.settings.SettingsScreen
import com.nblaisdell.home.ui.screens.shopping.ShoppingScreen
import com.nblaisdell.home.ui.screens.stores.StoresScreen

/**
 * App shell. NavigationSuiteScaffold picks the navigation UI from the window size:
 * a bottom navigation bar on the phone (compact width) and a navigation rail on the tablet.
 */
@Composable
fun HomeNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any {
                    it.hasRoute(destination.route::class)
                } == true
                item(
                    selected = selected,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = {
                        Icon(
                            if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = HomeRoute) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenDestination = navController::navigateToTopLevel,
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<TodoRoute> {
                PlaceholderScreen(TopLevelDestination.Todo, "To-dos, alarms and the weekly plan.")
            }
            composable<CalendarRoute> {
                PlaceholderScreen(TopLevelDestination.Calendar, "This month and next, built from dated to-dos.")
            }
            composable<WeatherRoute> {
                PlaceholderScreen(TopLevelDestination.Weather, "Hourly and weekly forecast, with a snow indicator.")
            }
            composable<ShoppingRoute> { ShoppingScreen(onOpenStores = { navController.navigate(StoresRoute) }) }
            composable<StoresRoute> { StoresScreen(onBack = { navController.popBackStack() }) }
            composable<EvercentRoute> {
                PlaceholderScreen(TopLevelDestination.Evercent, "Upcoming automation run and key budget categories.")
            }
            composable<SettingsRoute> {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Switches tabs the standard way: keep one copy of each top-level screen, remember each
 * tab's state when leaving it, and pressing Back from any tab returns to Home.
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
