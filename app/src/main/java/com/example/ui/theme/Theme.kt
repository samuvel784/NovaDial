package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    DAY,      // Day Mode: Light Theme
    DEFAULT,  // Default Mode: Signature Navy Theme
    DARK      // Dark Mode: Pitch Black Theme
}

data class AppColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val elevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val error: Color,
    val isLight: Boolean
)

val DayAppColors = AppColors(
    background = DayBackground,
    surface = DaySurface,
    card = DayCard,
    elevated = DayElevated,
    border = DayBorder,
    textPrimary = DayTextPrimary,
    textSecondary = DayTextSecondary,
    textMuted = DayTextMuted,
    primary = DayPrimary,
    secondary = DaySecondary,
    tertiary = DayTertiary,
    error = DayError,
    isLight = true
)

val DefaultNavyAppColors = AppColors(
    background = NovaNavyDeep,
    surface = NovaNavyDark,
    card = NovaNavyCard,
    elevated = NovaNavyElevated,
    border = NovaBorder,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    primary = NovaCyan,
    secondary = NovaEmerald,
    tertiary = NovaAmber,
    error = NovaCoral,
    isLight = false
)

val DarkBlackAppColors = AppColors(
    background = DarkBackground,
    surface = DarkSurface,
    card = DarkCard,
    elevated = DarkElevated,
    border = DarkBorder,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    error = DarkError,
    isLight = false
)

val LocalAppColors = staticCompositionLocalOf { DefaultNavyAppColors }

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

private val DayM3ColorScheme = lightColorScheme(
    primary = DayPrimary,
    onPrimary = Color.White,
    primaryContainer = DayElevated,
    onPrimaryContainer = DayPrimary,
    secondary = DaySecondary,
    onSecondary = Color.White,
    secondaryContainer = DayElevated,
    onSecondaryContainer = DaySecondary,
    tertiary = DayTertiary,
    onTertiary = Color.White,
    error = DayError,
    onError = Color.White,
    background = DayBackground,
    onBackground = DayTextPrimary,
    surface = DaySurface,
    onSurface = DayTextPrimary,
    surfaceVariant = DayCard,
    onSurfaceVariant = DayTextSecondary,
    outline = DayBorder
)

private val DefaultNavyM3ColorScheme = darkColorScheme(
    primary = NovaCyan,
    onPrimary = NovaNavyDeep,
    primaryContainer = NovaNavyElevated,
    onPrimaryContainer = NovaCyan,
    secondary = NovaEmerald,
    onSecondary = Color.White,
    secondaryContainer = NovaNavyCard,
    onSecondaryContainer = NovaEmerald,
    tertiary = NovaAmber,
    onTertiary = Color.Black,
    error = NovaCoral,
    onError = Color.White,
    background = NovaNavyDeep,
    onBackground = TextPrimary,
    surface = NovaNavyDark,
    onSurface = TextPrimary,
    surfaceVariant = NovaNavyCard,
    onSurfaceVariant = TextSecondary,
    outline = NovaBorder
)

private val DarkBlackM3ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.Black,
    primaryContainer = DarkElevated,
    onPrimaryContainer = DarkPrimary,
    secondary = DarkSecondary,
    onSecondary = Color.Black,
    secondaryContainer = DarkCard,
    onSecondaryContainer = DarkSecondary,
    tertiary = DarkTertiary,
    onTertiary = Color.Black,
    error = DarkError,
    onError = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder
)

@Composable
fun NovaDialTheme(
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit
) {
    val appColors = when (themeMode) {
        ThemeMode.DAY -> DayAppColors
        ThemeMode.DEFAULT -> DefaultNavyAppColors
        ThemeMode.DARK -> DarkBlackAppColors
    }

    val m3ColorScheme = when (themeMode) {
        ThemeMode.DAY -> DayM3ColorScheme
        ThemeMode.DEFAULT -> DefaultNavyM3ColorScheme
        ThemeMode.DARK -> DarkBlackM3ColorScheme
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = m3ColorScheme,
            typography = Typography,
            content = content
        )
    }
}
