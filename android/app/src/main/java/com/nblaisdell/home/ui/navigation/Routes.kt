package com.nblaisdell.home.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes. Each screen is a @Serializable object (or a class when
// it takes arguments), so navigating is `navController.navigate(ShoppingRoute)` with no strings.

@Serializable data object HomeRoute
@Serializable data object TodoRoute
@Serializable data object CalendarRoute
@Serializable data object WeatherRoute
@Serializable data object ShoppingRoute
@Serializable data object EvercentRoute

/** Not in the nav bar: opened from the gear icon on the Home screen. */
@Serializable data object SettingsRoute

/** Not in the nav bar: opened from the Shopping screen's menu (HOME-66). */
@Serializable data object StoresRoute
