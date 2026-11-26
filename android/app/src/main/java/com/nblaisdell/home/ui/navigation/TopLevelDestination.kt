package com.nblaisdell.home.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/** The screens shown in the bottom bar (phone) / navigation rail (tablet), in order. */
enum class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home(HomeRoute, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Todo(TodoRoute, "To-Do", Icons.Filled.Checklist, Icons.Outlined.Checklist),
    Calendar(CalendarRoute, "Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    Weather(WeatherRoute, "Weather", Icons.Filled.WbSunny, Icons.Outlined.WbSunny),
    Shopping(ShoppingRoute, "Shopping", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
    Evercent(EvercentRoute, "Evercent", Icons.Filled.Savings, Icons.Outlined.Savings),
}
