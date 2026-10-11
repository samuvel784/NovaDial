/*
 * Copyright (C) 2026 MovStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt

val UnifiedSquircleMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = AppShapes.Chip,
    medium = AppShapes.Card,
    large = AppShapes.Dialog,
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun getMissedCallColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFFFF6B6B) else Color(0xFFD32F2F)
}

@Composable
fun getCallGreenColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF34D399) else Color(0xFF059669)
}

@Composable
fun getOnCallGreenColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF003822) else Color(0xFFFFFFFF)
}

@Composable
fun getDeclineRedColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFFF43F5E) else Color(0xFFE11D48)
}

@Composable
fun getOnDeclineRedColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF4C0519) else Color(0xFFFFFFFF)
}

@Composable
fun getDialedCallColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFFB0BEC5) else Color(0xFF546E7A)
}

@Composable
fun getReceivedCallColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) Color(0xFF4CAF50) else Color(0xFF2E7D32)
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF5A4BE0)): Color {
    return try {
        val clean = hex.removePrefix("#").trim()
        val colorInt = when (clean.length) {
            6 -> "#FF$clean".toColorInt()
            8 -> "#$clean".toColorInt()
            else -> return fallback
        }
        Color(colorInt)
    } catch (_: Exception) {
        fallback
    }
}

fun getCustomSeedColorScheme(hex: String, darkTheme: Boolean): ColorScheme {
    val seed = parseHexColor(hex)
    return if (darkTheme) {
        darkColorScheme(
            primary = seed,
            onPrimary = Color.Black,
            primaryContainer = seed.copy(alpha = 0.35f),
            onPrimaryContainer = Color.White,
            secondary = seed.copy(alpha = 0.80f),
            onSecondary = Color.Black,
            secondaryContainer = seed.copy(alpha = 0.25f),
            onSecondaryContainer = Color.White,
            tertiary = seed.copy(alpha = 0.90f),
            onTertiary = Color.Black,
            tertiaryContainer = seed.copy(alpha = 0.30f),
            onTertiaryContainer = Color.White,
            background = Color(0xFF0B0918),
            surface = Color(0xFF120F1F),
            surfaceVariant = seed.copy(alpha = 0.18f),
            onSurface = Color(0xFFEEEAFF),
            outline = seed.copy(alpha = 0.60f)
        )
    } else {
        lightColorScheme(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = seed.copy(alpha = 0.20f),
            onPrimaryContainer = Color.Black,
            secondary = seed.copy(alpha = 0.85f),
            onSecondary = Color.White,
            secondaryContainer = seed.copy(alpha = 0.15f),
            onSecondaryContainer = Color.Black,
            tertiary = seed.copy(alpha = 0.75f),
            onTertiary = Color.White,
            tertiaryContainer = seed.copy(alpha = 0.18f),
            onTertiaryContainer = Color.Black,
            background = Color(0xFFFBFAFF),
            surface = Color(0xFFFBFAFF),
            surfaceVariant = seed.copy(alpha = 0.12f),
            onSurface = Color(0xFF1A1726),
            outline = seed.copy(alpha = 0.50f)
        )
    }
}

/**
 * NovaDial "Aurora" theme set.
 *
 * Replaces the upstream Secure Dialer palettes. The default is [novaAurora]
 * (indigo -> violet -> orchid). Any legacy or unknown stored key falls back to
 * Aurora, so upgrading users always see a valid scheme.
 */
fun getColorSchemeForTheme(
    themeColor: String,
    darkTheme: Boolean,
    customColorHex: String = "#5A4BE0"
): ColorScheme {
    if (themeColor == "custom") {
        return getCustomSeedColorScheme(customColorHex, darkTheme)
    }

    return when (themeColor) {
        "nova_nebula" -> novaNebula(darkTheme)
        "nova_glacier" -> novaGlacier(darkTheme)
        "nova_ember" -> novaEmber(darkTheme)
        "nova_forest" -> novaForest(darkTheme)
        "nova_mono" -> novaMono(darkTheme)
        // "nova_aurora" and every legacy key (expressive_lime, ocean_sapphire, ...)
        else -> novaAurora(darkTheme)
    }
}

private fun novaAurora(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFFB9AEFF),
    onPrimary = Color(0xFF241A66),
    primaryContainer = Color(0xFF3B2FA8),
    onPrimaryContainer = Color(0xFFE6E2FF),
    secondary = Color(0xFFCBBFFF),
    onSecondary = Color(0xFF33207A),
    secondaryContainer = Color(0xFF4A38C2),
    onSecondaryContainer = Color(0xFFECE6FF),
    tertiary = Color(0xFFE6A6FF),
    onTertiary = Color(0xFF4A0F63),
    tertiaryContainer = Color(0xFF6E1E93),
    onTertiaryContainer = Color(0xFFF6E3FF),
    background = Color(0xFF0B0918),
    surface = Color(0xFF0B0918),
    surfaceVariant = Color(0xFF211D33),
    onSurface = Color(0xFFEEEAFF),
    outline = Color(0xFF928FA6)
) else lightColorScheme(
    primary = Color(0xFF5A4BE0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E2FF),
    onPrimaryContainer = Color(0xFF190766),
    secondary = Color(0xFF7C5CFF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECE6FF),
    onSecondaryContainer = Color(0xFF250C63),
    tertiary = Color(0xFFB14BE0),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6E3FF),
    onTertiaryContainer = Color(0xFF3B0054),
    background = Color(0xFFFBFAFF),
    surface = Color(0xFFFBFAFF),
    surfaceVariant = Color(0xFFE9E6F5),
    onSurface = Color(0xFF1A1726),
    outline = Color(0xFF7A7690)
)

private fun novaNebula(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFFD8B4FE),
    onPrimary = Color(0xFF3B0764),
    primaryContainer = Color(0xFF6B21A8),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Color(0xFFF0ABFC),
    onSecondary = Color(0xFF4A044E),
    secondaryContainer = Color(0xFF86198F),
    onSecondaryContainer = Color(0xFFFAE8FF),
    tertiary = Color(0xFFC4B5FD),
    onTertiary = Color(0xFF2E1065),
    tertiaryContainer = Color(0xFF5B21B6),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = Color(0xFF14001F),
    surface = Color(0xFF14001F),
    surfaceVariant = Color(0xFF26162E),
    onSurface = Color(0xFFF6EDFA),
    outline = Color(0xFFA192AF)
) else lightColorScheme(
    primary = Color(0xFF7E22CE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E8FF),
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = Color(0xFFA21CAF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFAE8FF),
    onSecondaryContainer = Color(0xFF4A044E),
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF2E1065),
    background = Color(0xFFFDFAFF),
    surface = Color(0xFFFDFAFF),
    surfaceVariant = Color(0xFFECE6F5),
    onSurface = Color(0xFF1E1630),
    outline = Color(0xFF7C6F8F)
)

private fun novaGlacier(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF0C4A6E),
    primaryContainer = Color(0xFF075985),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF67E8F9),
    onSecondary = Color(0xFF164E63),
    secondaryContainer = Color(0xFF155E75),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFF93C5FD),
    onTertiary = Color(0xFF172554),
    tertiaryContainer = Color(0xFF1E40AF),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF04121C),
    surface = Color(0xFF04121C),
    surfaceVariant = Color(0xFF12222F),
    onSurface = Color(0xFFE6F1FA),
    outline = Color(0xFF7C8D9C)
) else lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF0E7490),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFF1D4ED8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF172554),
    background = Color(0xFFF8FBFF),
    surface = Color(0xFFF8FBFF),
    surfaceVariant = Color(0xFFE2EDF5),
    onSurface = Color(0xFF14202B),
    outline = Color(0xFF6B7B8A)
)

private fun novaEmber(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFFFDBA74),
    onPrimary = Color(0xFF431407),
    primaryContainer = Color(0xFF9A3412),
    onPrimaryContainer = Color(0xFFFFEDD5),
    secondary = Color(0xFFFCD34D),
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFFB45309),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFFFDA4AF),
    onTertiary = Color(0xFF4C0519),
    tertiaryContainer = Color(0xFF9F1239),
    onTertiaryContainer = Color(0xFFFFE4E6),
    background = Color(0xFF1A0F06),
    surface = Color(0xFF1A0F06),
    surfaceVariant = Color(0xFF2E2014),
    onSurface = Color(0xFFFBEEDF),
    outline = Color(0xFFA9917C)
) else lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF431407),
    secondary = Color(0xFFB45309),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = Color(0xFFBE123C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE4E6),
    onTertiaryContainer = Color(0xFF4C0519),
    background = Color(0xFFFFFBF7),
    surface = Color(0xFFFFFBF7),
    surfaceVariant = Color(0xFFF5E7DA),
    onSurface = Color(0xFF2A1A10),
    outline = Color(0xFF8C7767)
)

private fun novaForest(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF6EE7B7),
    onSecondary = Color(0xFF022C22),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFFBEF264),
    onTertiary = Color(0xFF1A2E05),
    tertiaryContainer = Color(0xFF3F6212),
    onTertiaryContainer = Color(0xFFECFCCB),
    background = Color(0xFF04140F),
    surface = Color(0xFF04140F),
    surfaceVariant = Color(0xFF11241D),
    onSurface = Color(0xFFE4F2EC),
    outline = Color(0xFF7E9188)
) else lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF047857),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF022C22),
    tertiary = Color(0xFF4D7C0F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFECFCCB),
    onTertiaryContainer = Color(0xFF1A2E05),
    background = Color(0xFFF7FCFA),
    surface = Color(0xFFF7FCFA),
    surfaceVariant = Color(0xFFDFEEE8),
    onSurface = Color(0xFF13211C),
    outline = Color(0xFF6C7B74)
)

private fun novaMono(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color(0xFFE4E3EA),
    onPrimary = Color(0xFF1A1A1E),
    primaryContainer = Color(0xFF3A3A42),
    onPrimaryContainer = Color(0xFFF2F1F7),
    secondary = Color(0xFFB9B9C2),
    onSecondary = Color(0xFF1A1A1E),
    secondaryContainer = Color(0xFF2C2C33),
    onSecondaryContainer = Color(0xFFE7E7EC),
    tertiary = Color(0xFFCBCBD4),
    onTertiary = Color(0xFF17171B),
    tertiaryContainer = Color(0xFF33333B),
    onTertiaryContainer = Color(0xFFE4E4E9),
    background = Color(0xFF0C0C0F),
    surface = Color(0xFF0C0C0F),
    surfaceVariant = Color(0xFF1D1D22),
    onSurface = Color(0xFFECECF1),
    outline = Color(0xFF8E8E97)
) else lightColorScheme(
    primary = Color(0xFF1F1F24),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E6EC),
    onPrimaryContainer = Color(0xFF111114),
    secondary = Color(0xFF45454D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEDF1),
    onSecondaryContainer = Color(0xFF1A1A1E),
    tertiary = Color(0xFF33333B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE4E4E9),
    onTertiaryContainer = Color(0xFF17171B),
    background = Color(0xFFFAFAFC),
    surface = Color(0xFFFAFAFC),
    surfaceVariant = Color(0xFFE9E9EF),
    onSurface = Color(0xFF1A1A1E),
    outline = Color(0xFF77777F)
)

val LocalM3Expressive = staticCompositionLocalOf { true }
val LocalAmoledMode = staticCompositionLocalOf { false }

fun getExpressiveColorScheme(colorScheme: ColorScheme, darkTheme: Boolean): ColorScheme {
    return if (darkTheme) {
        colorScheme.copy(
            primaryContainer = colorScheme.primary.copy(alpha = 0.35f),
            onPrimaryContainer = Color.White,
            secondaryContainer = colorScheme.secondary.copy(alpha = 0.30f),
            onSecondaryContainer = Color.White,
            surfaceVariant = colorScheme.primary.copy(alpha = 0.18f),
            tertiaryContainer = colorScheme.tertiary.copy(alpha = 0.30f)
        )
    } else {
        colorScheme.copy(
            primaryContainer = colorScheme.primary.copy(alpha = 0.22f),
            secondaryContainer = colorScheme.secondary.copy(alpha = 0.22f),
            surfaceVariant = colorScheme.primary.copy(alpha = 0.12f),
            tertiaryContainer = colorScheme.tertiary.copy(alpha = 0.20f)
        )
    }
}

/**
 * NovaDial appearance modes.
 *  - [DAY]     : clean white interface (light).
 *  - [DEFAULT] : the signature deep-blue interface (dark).
 *  - [NIGHT]   : true-black interface (dark, pure #000000).
 */
enum class ThemeMode(val key: String) {
    DAY("day"),
    DEFAULT("default"),
    NIGHT("night");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}

private fun dayScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF00274D),
    secondary = Color(0xFF1A73E8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3F0FF),
    onSecondaryContainer = Color(0xFF00325C),
    tertiary = Color(0xFF0091C2),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F0FF),
    onTertiaryContainer = Color(0xFF00344A),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF3FA),
    onSurface = Color(0xFF101418),
    outline = Color(0xFF9AA4B2)
)

private fun defaultBlueScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF00274D),
    primaryContainer = Color(0xFF0A84FF),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFF7FB8FF),
    onSecondary = Color(0xFF002E52),
    secondaryContainer = Color(0xFF0E4E86),
    onSecondaryContainer = Color(0xFFD6E9FF),
    tertiary = Color(0xFF6ED0FF),
    onTertiary = Color(0xFF00344A),
    tertiaryContainer = Color(0xFF0A5A78),
    onTertiaryContainer = Color(0xFFC9ECFF),
    background = Color(0xFF060A12),
    surface = Color(0xFF060A12),
    surfaceVariant = Color(0xFF121B2E),
    onSurface = Color(0xFFE6EEFF),
    outline = Color(0xFF6B7A90)
)

private fun nightScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF00274D),
    primaryContainer = Color(0xFF0A3D66),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFF9CC7FF),
    onSecondary = Color(0xFF00325C),
    secondaryContainer = Color(0xFF123A63),
    onSecondaryContainer = Color(0xFFD6E9FF),
    tertiary = Color(0xFF6ED0FF),
    onTertiary = Color(0xFF00344A),
    tertiaryContainer = Color(0xFF0A4A63),
    onTertiaryContainer = Color(0xFFC9ECFF),
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF0E0E10),
    onSurface = Color(0xFFFFFFFF),
    outline = Color(0xFF3A3A3A)
)

fun themeModeScheme(mode: ThemeMode): ColorScheme = when (mode) {
    ThemeMode.DAY -> dayScheme()
    ThemeMode.NIGHT -> nightScheme()
    ThemeMode.DEFAULT -> defaultBlueScheme()
}

@Composable
fun MyApplicationTheme(
  themeMode: ThemeMode? = null,
  darkTheme: Boolean? = null,
  dynamicColor: Boolean = false,
  themeColor: String = "nova_aurora",
  customColorHex: String = "#5A4BE0",
  isAmoledMode: Boolean = false,
  isM3Expressive: Boolean = true,
  content: @Composable () -> Unit,
) {
  val mode = themeMode ?: when (darkTheme) {
      false -> ThemeMode.DAY
      true -> ThemeMode.DEFAULT
      null -> ThemeMode.DEFAULT
  }
  val dark = mode != ThemeMode.DAY

  var colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      else -> themeModeScheme(mode)
    }

  if (isM3Expressive) {
      colorScheme = getExpressiveColorScheme(colorScheme, dark)
  }

  // Glassy surface treatment: no tint wash, soft dividers, muted scrim.
  colorScheme = colorScheme.copy(
      surfaceTint = Color.Transparent,
      outlineVariant = colorScheme.outline.copy(alpha = 0.32f),
      scrim = Color(0x99000000)
  )

  val effectiveAmoled = mode == ThemeMode.NIGHT
  if (effectiveAmoled) {
      colorScheme = colorScheme.copy(
          background = Color(0xFF000000),
          surface = Color(0xFF000000),
          surfaceVariant = Color(0xFF0E0E10),
          surfaceTint = Color.Transparent,
          outline = Color(0xFF2E2E2E),
          outlineVariant = Color(0xFF1C1C1C),
          surfaceContainerLowest = Color(0xFF000000),
          surfaceContainerLow = Color(0xFF000000),
          surfaceContainer = Color(0xFF08080A),
          surfaceContainerHigh = Color(0xFF101012),
          surfaceContainerHighest = Color(0xFF18181A),
          onBackground = Color(0xFFFFFFFF),
          onSurface = Color(0xFFFFFFFF),
          onSurfaceVariant = Color(0xFFD4D4D8)
      )
  }

  CompositionLocalProvider(
      LocalM3Expressive provides isM3Expressive,
      LocalAmoledMode provides effectiveAmoled,
      LocalUnifiedShapes provides calculateUnifiedShapes(ShapeStyle.SQUIRCLE)
  ) {
      MaterialTheme(
          colorScheme = colorScheme,
          shapes = UnifiedSquircleMaterialShapes,
          typography = if (isM3Expressive) ExpressiveTypography else Typography,
          content = content
      )
  }
}
