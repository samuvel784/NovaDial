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

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.DialerViewModel

@Composable
fun DisplayAndSoundSettings(
    viewModel: DialerViewModel,
    cardBgColor: Color
) {
    val themeMode by viewModel.themeMode
    val isM3Expressive by viewModel.isM3Expressive
    val dialpadTonesEnabled by viewModel.dialpadTonesEnabled
    val vibrateOnClickEnabled by viewModel.vibrateOnClickEnabled

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // [Header] THEME & STYLING
        item {
            PreferenceHeader(stringResource(R.string.header_theme_styling))
        }

        // Appearance Mode Card (Day / Default / Night)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
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
                            selected = themeMode == ThemeMode.DAY,
                            onClick = { viewModel.updateThemeMode(ThemeMode.DAY) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeOption(
                            label = stringResource(R.string.theme_default),
                            sublabel = stringResource(R.string.theme_default_sub),
                            accent = Color(0xFF4DA3FF),
                            surfaceColor = Color(0xFF060A12),
                            selected = themeMode == ThemeMode.DEFAULT,
                            onClick = { viewModel.updateThemeMode(ThemeMode.DEFAULT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeOption(
                            label = stringResource(R.string.theme_night),
                            sublabel = stringResource(R.string.theme_night_sub),
                            accent = Color(0xFF4DA3FF),
                            surfaceColor = Color.Black,
                            selected = themeMode == ThemeMode.NIGHT,
                            onClick = { viewModel.updateThemeMode(ThemeMode.NIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // M3 Expressive Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
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

        // [Header] SOUND & HAPTICS
        item {
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceHeader(stringResource(R.string.header_sound_haptics))
        }

        // Dialpad Tones Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = MaterialTheme.shapes.medium
            ) {
                SettingsRowToggle(
                    title = stringResource(R.string.settings_dialpad_tones),
                    subtitle = stringResource(R.string.settings_dialpad_tones_sub),
                    checked = dialpadTonesEnabled,
                    onCheckedChange = { viewModel.updateDialpadTonesEnabled(it) },
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    iconBgColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    iconTint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Vibrate on Click Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = MaterialTheme.shapes.medium
            ) {
                SettingsRowToggle(
                    title = stringResource(R.string.settings_vibrate),
                    subtitle = stringResource(R.string.settings_vibrate_sub),
                    checked = vibrateOnClickEnabled,
                    onCheckedChange = { viewModel.updateVibrateOnClickEnabled(it) },
                    icon = Icons.Default.Vibration,
                    iconBgColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // [Header] NAVIGATION & LAYOUT
        item {
            Spacer(modifier = Modifier.height(12.dp))
            PreferenceHeader(stringResource(R.string.header_navigation_layout))
            DefaultStartupTabCard(viewModel = viewModel, cardBgColor = cardBgColor)
        }
    }
}
