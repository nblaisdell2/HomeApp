package com.nblaisdell.home.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

/**
 * Material 3 type scale, nudged slightly larger and bolder in the body/title styles so text
 * stays readable on the wall tablet from across the room. Headings use a medium weight.
 */
internal val HomeTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Medium),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Medium),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Medium),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
    titleSmall = Base.titleSmall.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 15.sp),
    labelMedium = Base.labelMedium,
    labelSmall = Base.labelSmall,
)

/** Big numbers on dashboard cards (temperature, amount to post, etc.). */
val TextStyle.Companion.HomeMetric: TextStyle
    get() = TextStyle(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold)
