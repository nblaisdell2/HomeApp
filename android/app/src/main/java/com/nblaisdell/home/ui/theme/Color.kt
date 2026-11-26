package com.nblaisdell.home.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Home palette: a calm green-teal primary, muted sage secondary and a warm amber tertiary
// (used for "needs attention" accents such as alarms and overspending).

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2E6A5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4F0E1),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A635D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E0),
    onSecondaryContainer = Color(0xFF06201A),
    tertiary = Color(0xFF8A5A2B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF2E1500),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAF7),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FAF7),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9EFEB),
    surfaceContainerHigh = Color(0xFFE4EAE6),
    surfaceContainerHighest = Color(0xFFDEE4E0),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBEC9C4),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF98D4C5),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF115146),
    onPrimaryContainer = Color(0xFFB4F0E1),
    secondary = Color(0xFFB1CCC4),
    onSecondary = Color(0xFF1C352F),
    secondaryContainer = Color(0xFF334B45),
    onSecondaryContainer = Color(0xFFCCE8E0),
    tertiary = Color(0xFFF2BC8A),
    onTertiary = Color(0xFF4B2800),
    tertiaryContainer = Color(0xFF6D4316),
    onTertiaryContainer = Color(0xFFFFDCC0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBEC9C4),
    surfaceContainerLowest = Color(0xFF0A0F0E),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF303634),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4945),
)
