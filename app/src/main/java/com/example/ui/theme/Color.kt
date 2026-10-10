/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * NovaDial "Aurora" design language.
 *
 * Identity: a luminous indigo -> violet -> orchid gradient family on deep
 * indigo-black surfaces. This is NovaDial's own palette and shares no colors
 * with the Secure Dialer upstream theme (which used a blue/lime base).
 */

val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)

// ---- Aurora brand anchors -------------------------------------------------
val AuroraIndigo = Color(0xFF5A4BE0)
val AuroraViolet = Color(0xFF7C5CFF)
val AuroraOrchid = Color(0xFFB14BE0)
val AuroraGlassLight = Color(0xFFEDE9FF)
val AuroraGlassDark = Color(0xFF181428)

// ---- Material 3 tokens (NovaDial Aurora) ----------------------------------
val PrimaryLight = Color(0xFF5A4BE0)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE6E2FF)
val OnPrimaryContainerLight = Color(0xFF190766)

val PrimaryDark = Color(0xFFB9AEFF)
val OnPrimaryDark = Color(0xFF241A66)
val PrimaryContainerDark = Color(0xFF3B2FA8)
val OnPrimaryContainerDark = Color(0xFFE6E2FF)

val SecondaryLight = Color(0xFF7C5CFF)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFECE6FF)
val OnSecondaryContainerLight = Color(0xFF250C63)

val SecondaryDark = Color(0xFFCBBFFF)
val OnSecondaryDark = Color(0xFF33207A)
val SecondaryContainerDark = Color(0xFF4A38C2)
val OnSecondaryContainerDark = Color(0xFFECE6FF)

val TertiaryLight = Color(0xFFB14BE0)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFF6E3FF)
val OnTertiaryContainerLight = Color(0xFF3B0054)

val TertiaryDark = Color(0xFFE6A6FF)
val OnTertiaryDark = Color(0xFF4A0F63)
val TertiaryContainerDark = Color(0xFF6E1E93)
val OnTertiaryContainerDark = Color(0xFFF6E3FF)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// Aurora light surfaces (soft lavender-tinted glass whites)
val BackgroundLight = Color(0xFFFBFAFF)
val OnBackgroundLight = Color(0xFF1A1726)
val SurfaceLight = Color(0xFFFBFAFF)
val OnSurfaceLight = Color(0xFF1A1726)
val SurfaceVariantLight = Color(0xFFE9E6F5)
val OnSurfaceVariantLight = Color(0xFF474459)
val OutlineLight = Color(0xFF7A7690)

// Aurora dark surfaces (deep indigo-black glass)
val BackgroundDark = Color(0xFF0B0918)
val OnBackgroundDark = Color(0xFFEEEAFF)
val SurfaceDark = Color(0xFF0B0918)
val OnSurfaceDark = Color(0xFFEEEAFF)
val SurfaceVariantDark = Color(0xFF211D33)
val OnSurfaceVariantDark = Color(0xFFC9C4DD)
val OutlineDark = Color(0xFF928FA6)

// High-contrast avatar color pairs (kept from upstream for contact avatars)
val AvatarOrange = Color(0xFFFFDBCB)
val AvatarOrangeText = Color(0xFF311300)
val AvatarBlue = Color(0xFFD1E4FF)
val AvatarBlueText = Color(0xFF001D36)
val AvatarGreen = Color(0xFFD9E7CB)
val AvatarGreenText = Color(0xFF141E0D)
