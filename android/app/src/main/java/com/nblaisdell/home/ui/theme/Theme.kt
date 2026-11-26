package com.nblaisdell.home.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The app theme. Dynamic (wallpaper-based) color is deliberately not used, so the phone and
 * the wall tablet always look the same. Light/dark follows the system setting.
 */
@Composable
fun HomeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = HomeTypography,
            shapes = HomeShapes,
            content = content,
        )
    }
}

/** App-specific theme values that MaterialTheme does not cover. */
object HomeTheme {
    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current
}
