package com.rotiropi.pos_erpnext.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Central design tokens translated from the Stitch "Warm Commerce" design system.
 *
 * Batch 1 surfaces (OAuth sign-in and Opening Balance) consume these tokens so raw
 * colors and dimensions are never scattered through individual screens. The palette
 * is a warm, tactile Material 3 scheme built around the bakery amber brand color.
 */
object WarmCommerce {
    // Brand palette (light).
    val Amber = Color(0xFFFEBA11)
    val AmberDark = Color(0xFF7B5800)
    val AmberOnContainer = Color(0xFF6C4D00)
    val Cream = Color(0xFFFDF9F2)
    val WarmInk = Color(0xFF1C1C18)
    val WarmInkVariant = Color(0xFF504533)
    val Teal = Color(0xFF006B5F)
    val TealContainer = Color(0xFF9CEFDF)
    val TealOnContainer = Color(0xFF0B6F63)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF7B5800),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFEBA11),
    onPrimaryContainer = Color(0xFF6C4D00),
    secondary = Color(0xFF006B5F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF9CEFDF),
    onSecondaryContainer = Color(0xFF0B6F63),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEB7C7),
    onTertiaryContainer = Color(0xFF6F4654),
    background = Color(0xFFFDF9F2),
    onBackground = Color(0xFF1C1C18),
    surface = Color(0xFFFDF9F2),
    onSurface = Color(0xFF1C1C18),
    surfaceVariant = Color(0xFFE6E2DB),
    onSurfaceVariant = Color(0xFF504533),
    surfaceTint = Color(0xFF7B5800),
    inverseSurface = Color(0xFF32302C),
    inverseOnSurface = Color(0xFFF5F0E9),
    inversePrimary = Color(0xFFFFBB12),
    outline = Color(0xFF837560),
    outlineVariant = Color(0xFFD5C4AC),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F3EC),
    surfaceContainer = Color(0xFFF2EDE7),
    surfaceContainerHigh = Color(0xFFECE8E1),
    surfaceContainerHighest = Color(0xFFE6E2DB),
    surfaceBright = Color(0xFFFDF9F2),
    surfaceDim = Color(0xFFDED9D3),
    scrim = Color(0xFF000000),
)

// Dark mode follows Material 3 tonal mapping while retaining the high-contrast amber
// for active components, as described by the Warm Commerce design notes.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFBB12),
    onPrimary = Color(0xFF3A2C00),
    primaryContainer = Color(0xFFFEBA11),
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF83D5C6),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFF9FF2E2),
    tertiary = Color(0xFFEEB8C8),
    onTertiary = Color(0xFF462231),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF171511),
    onBackground = Color(0xFFECE8E1),
    surface = Color(0xFF171511),
    onSurface = Color(0xFFECE8E1),
    surfaceVariant = Color(0xFF504533),
    onSurfaceVariant = Color(0xFFD5C4AC),
    surfaceTint = Color(0xFFFFBB12),
    inverseSurface = Color(0xFFECE8E1),
    inverseOnSurface = Color(0xFF32302C),
    inversePrimary = Color(0xFF7B5800),
    outline = Color(0xFF9D8F78),
    outlineVariant = Color(0xFF504533),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF12100C),
    surfaceContainerLow = Color(0xFF1C1A14),
    surfaceContainer = Color(0xFF201E18),
    surfaceContainerHigh = Color(0xFF2A2822),
    surfaceContainerHighest = Color(0xFF35322B),
    surfaceBright = Color(0xFF3B3830),
    surfaceDim = Color(0xFF171511),
    scrim = Color(0xFF000000),
)

fun warmCommerceColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) DarkColors else LightColors

/**
 * Warm Commerce type scale. It uses the system default font (Roboto on Android), which
 * matches the design system requirement to prioritize performance and familiarity.
 */
val WarmCommerceTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Normal,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Normal,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
)

/** Transaction-total emphasis role from the Warm Commerce type scale. */
val WarmCommercePriceDisplay = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 36.sp,
    lineHeight = 44.sp,
    fontWeight = FontWeight.Bold,
)

/** Warm Commerce shape language: rounded, soft, approachable. */
val WarmCommerceShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Warm Commerce spacing, sizing, and elevation tokens. These mirror the design
 * system's 8dp grid, 48dp touch target, corner radii, and tonal elevation levels.
 */
object WarmCommerceDimensions {
    val touchTarget = 48.dp
    val screenMargin = 16.dp
    val containerPadding = 16.dp
    val stackGap = 12.dp
    val gutter = 8.dp
    val unit = 8.dp

    val cornerSmall = 4.dp
    val cornerDefault = 8.dp
    val cornerMedium = 12.dp
    val cornerLarge = 16.dp
    val cornerExtraLarge = 24.dp
    val sheetCorner = 28.dp

    val iconSize = 24.dp
    val inputFocusedBorder = 2.dp

    val elevationFlat = 0.dp
    val elevationSurface = 1.dp
    val elevationRaised = 3.dp
    val elevationOverlap = 6.dp
}

/**
 * Warm Commerce theme used by the Batch 1 surfaces (OAuth sign-in and Opening
 * Balance). It wraps Material 3 with the centralized Warm Commerce tokens.
 */
@Composable
fun WarmCommerceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = warmCommerceColorScheme(darkTheme),
        typography = WarmCommerceTypography,
        shapes = WarmCommerceShapes,
        content = content,
    )
}
