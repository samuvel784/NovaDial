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

package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.RichHapticEngine

@Composable
fun AppearanceSettings(
    viewModel: DialerViewModel,
    cardBgColor: Color,
    highlightedTitle: String? = null
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode
    val isM3Expressive by viewModel.isM3Expressive

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // [Header] THEME & STYLING
        item {
            PreferenceHeader(stringResource(R.string.header_theme_styling))
        }

        // Appearance Mode Card (Day / Default / Night)
        item {
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("Appearance Mode", highlightedTitle) ||
                        isMatchTitle("Appearance Settings", highlightedTitle) ||
                        isMatchTitle("Appearance & Theme", highlightedTitle) ||
                        isMatchTitle("Dark Mode & Theme Mode", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    MaterialTheme.shapes.small
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_theme_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.settings_theme_mode_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeModeOption(
                            label = stringResource(R.string.theme_day),
                            sublabel = stringResource(R.string.theme_day_sub),
                            accent = Color(0xFF0A84FF),
                            surfaceColor = Color.White,
                            selected = themeMode == com.example.ui.theme.ThemeMode.DAY,
                            onClick = { viewModel.updateThemeMode(com.example.ui.theme.ThemeMode.DAY) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeOption(
                            label = stringResource(R.string.theme_default),
                            sublabel = stringResource(R.string.theme_default_sub),
                            accent = Color(0xFF4DA3FF),
                            surfaceColor = Color(0xFF060A12),
                            selected = themeMode == com.example.ui.theme.ThemeMode.DEFAULT,
                            onClick = { viewModel.updateThemeMode(com.example.ui.theme.ThemeMode.DEFAULT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeOption(
                            label = stringResource(R.string.theme_night),
                            sublabel = stringResource(R.string.theme_night_sub),
                            accent = Color(0xFF4DA3FF),
                            surfaceColor = Color.Black,
                            selected = themeMode == com.example.ui.theme.ThemeMode.NIGHT,
                            onClick = { viewModel.updateThemeMode(com.example.ui.theme.ThemeMode.NIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // M3 Expressive Card
        item {
            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("Expressive Material 3 Layout", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                SettingsRowToggle(
                    title = stringResource(R.string.settings_m3_expressive),
                    subtitle = stringResource(R.string.settings_m3_expressive_sub),
                    checked = isM3Expressive,
                    onCheckedChange = { viewModel.updateM3Expressive(it) },
                    icon = Icons.Default.AutoAwesome,
                    iconBgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // [Header] DYNAMIC ISLAND & CALL CAPSULE
        item {
            PreferenceHeader(stringResource(R.string.settings_dynamic_island_title))
        }

        // Dynamic Island Master Card
        item {
            val isDynamicIslandEnabled by viewModel.isDynamicIslandEnabled
            val isDynamicIslandSpeakerOnly by viewModel.isDynamicIslandSpeakerOnly

            HighlightableCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                cardBgColor = cardBgColor,
                isHighlighted = isMatchTitle("Dynamic Island", highlightedTitle) ||
                        isMatchTitle("Dynamic Island Call Capsule", highlightedTitle),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsRowToggle(
                        title = stringResource(R.string.settings_dynamic_island_title),
                        subtitle = stringResource(R.string.settings_dynamic_island_sub),
                        checked = isDynamicIslandEnabled,
                        onCheckedChange = { viewModel.updateDynamicIslandEnabled(it) },
                        icon = Icons.Default.CropPortrait,
                        iconBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        iconTint = MaterialTheme.colorScheme.primary
                    )

                    if (isDynamicIslandEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        SettingsRowToggle(
                            title = stringResource(R.string.settings_dynamic_island_speaker_only),
                            subtitle = stringResource(R.string.settings_dynamic_island_speaker_only_sub),
                            checked = isDynamicIslandSpeakerOnly,
                            onCheckedChange = { viewModel.updateDynamicIslandSpeakerOnly(it) },
                            icon = Icons.Default.VolumeUp,
                            iconBgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            iconTint = MaterialTheme.colorScheme.secondary
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val canOverlay = com.example.util.CallScreenPermissionHelper.canDrawOverlays(context)
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_dynamic_island_floating_permission),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_dynamic_island_floating_permission_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (!canOverlay) {
                                    FilledTonalButton(
                                        onClick = {
                                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                            com.example.util.CallScreenPermissionHelper.requestOverlayPermission(context)
                                        }
                                    ) {
                                        Text(stringResource(R.string.btn_grant))
                                    }
                                } else {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.status_granted),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeModeOption(
    label: String,
    sublabel: String,
    accent: Color,
    surfaceColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(surfaceColor, CircleShape)
                    .border(3.dp, accent, CircleShape)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}