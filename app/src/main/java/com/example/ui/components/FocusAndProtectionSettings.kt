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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.telecom.AnnouncerScript
import com.example.telecom.CallProtectionPrefs
import com.example.telecom.CallerAnnouncerManager
import com.example.telecom.FocusMode
import com.example.telecom.ProtectionLevel
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.CallScreenPermissionHelper
import com.example.util.RichHapticEngine

/**
 * Self-contained "Focus & Protection" settings screen.
 *
 * Combines the two features ported from NovaDial on top of the Secure Dialer core:
 *  - Focus mode + call protection level (VIP / saved / repeat caller rules)
 *  - Caller announcer (text-to-speech that reads the caller's name while ringing)
 */
@Composable
fun FocusAndProtectionSettings(
    viewModel: DialerViewModel,
    cardBgColor: Color,
    highlightedTitle: String? = null
) {
    val context = LocalContext.current

    var focusMode by remember { mutableStateOf(CallProtectionPrefs.getFocusMode(context)) }
    var protectionLevel by remember { mutableStateOf(CallProtectionPrefs.getProtectionLevel(context)) }
    var allowRepeat by remember { mutableStateOf(CallProtectionPrefs.isRepeatCallerAllowed(context)) }
    var vipNumbers by remember { mutableStateOf(CallProtectionPrefs.getVipNumbers(context).sorted()) }

    var announcerEnabled by remember { mutableStateOf(CallProtectionPrefs.isAnnouncerEnabled(context)) }
    var announcerTemplate by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerTemplate(context)) }
    var announcerUserName by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerUserName(context)) }
    var announcerRepeat by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerRepeat(context)) }
    var announcerSpeed by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerSpeedPercent(context)) }
    var announcerPitch by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerPitchPercent(context)) }
    var announcerVolume by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerVolumePercent(context)) }
    var headphonesOnly by remember { mutableStateOf(CallProtectionPrefs.isAnnouncerHeadphonesOnly(context)) }
    var announceUnknown by remember { mutableStateOf(CallProtectionPrefs.isAnnounceUnknown(context)) }

    var showAddVipDialog by remember { mutableStateOf(false) }
    var vipInput by remember { mutableStateOf("") }

    var hasScreeningRole by remember { mutableStateOf(CallScreenPermissionHelper.hasCallScreeningRole(context)) }
    val screeningLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasScreeningRole = CallScreenPermissionHelper.hasCallScreeningRole(context)
    }

    // TTS engine used only for the preview button on this screen.
    val announcer = remember { CallerAnnouncerManager(context) }
    DisposableEffect(Unit) {
        onDispose { announcer.shutdown() }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ------------------------------------------------- CALL SCREENING ROLE STATUS
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasScreeningRole) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (hasScreeningRole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (hasScreeningRole) R.string.screening_role_granted else R.string.screening_role_needed
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.screening_role_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!hasScreeningRole) {
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = {
                        CallScreenPermissionHelper.requestCallScreeningRole(context, screeningLauncher)
                    }) {
                        Text(stringResource(R.string.btn_grant))
                    }
                }
            }
        }

        // ---------------------------------------------------------------- FOCUS MODE
        PreferenceHeader(stringResource(R.string.header_focus_mode))
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            isHighlighted = isMatchTitle("Focus Mode", highlightedTitle),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DoNotDisturbOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.focus_mode_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.focus_mode_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            FocusMode.entries.forEach { mode ->
                ProtectionChoiceRow(
                    title = focusModeLabel(mode),
                    subtitle = focusModeSubtitle(mode),
                    selected = mode == focusMode,
                    onSelect = {
                        focusMode = mode
                        CallProtectionPrefs.setFocusMode(context, mode)
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        // -------------------------------------------------------- PROTECTION LEVEL
        PreferenceHeader(stringResource(R.string.header_call_protection))
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            isHighlighted = isMatchTitle("Call Protection Level", highlightedTitle),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.protection_level_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.protection_level_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            ProtectionLevel.entries.forEach { level ->
                ProtectionChoiceRow(
                    title = protectionLevelLabel(level),
                    subtitle = protectionLevelSubtitle(level),
                    selected = level == protectionLevel,
                    onSelect = {
                        protectionLevel = level
                        CallProtectionPrefs.setProtectionLevel(context, level)
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                    }
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsRowToggle(
                title = stringResource(R.string.protection_allow_repeat_title),
                subtitle = stringResource(R.string.protection_allow_repeat_sub),
                checked = allowRepeat,
                onCheckedChange = {
                    allowRepeat = it
                    CallProtectionPrefs.setRepeatCallerAllowed(context, it)
                }
            )
            Text(
                text = stringResource(R.string.protection_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ---------------------------------------------------------------- VIP LIST
        PreferenceHeader(stringResource(R.string.header_vip_numbers))
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            isHighlighted = isMatchTitle("VIP Numbers", highlightedTitle),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vip_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.vip_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (vipNumbers.isEmpty()) {
                Text(
                    text = stringResource(R.string.vip_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            } else {
                vipNumbers.forEach { number ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = number,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                CallProtectionPrefs.removeVipNumber(context, number)
                                vipNumbers = CallProtectionPrefs.getVipNumbers(context).sorted()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.vip_remove),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsRowNav(
                title = stringResource(R.string.vip_add),
                subtitle = stringResource(R.string.vip_add_sub),
                onClick = {
                    vipInput = ""
                    showAddVipDialog = true
                },
                icon = Icons.Default.Add,
                iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
        }

        // ------------------------------------------------------- CALLER ANNOUNCER
        PreferenceHeader(stringResource(R.string.header_caller_announcer))
        HighlightableCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            cardBgColor = cardBgColor,
            isHighlighted = isMatchTitle("Caller Announcer", highlightedTitle) ||
                isMatchTitle("Announcer", highlightedTitle),
            shape = MaterialTheme.shapes.medium
        ) {
            SettingsRowToggle(
                title = stringResource(R.string.announcer_enable_title),
                subtitle = stringResource(R.string.announcer_enable_sub),
                checked = announcerEnabled,
                onCheckedChange = {
                    announcerEnabled = it
                    CallProtectionPrefs.setAnnouncerEnabled(context, it)
                },
                icon = Icons.Default.Campaign,
                iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Text(
                text = stringResource(R.string.announcer_message_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            AnnouncerScript.PRESETS.forEach { (label, template) ->
                ProtectionChoiceRow(
                    title = label,
                    subtitle = AnnouncerScript.render(template, "Alex Morgan", announcerUserName, "saved contact"),
                    selected = announcerTemplate == template,
                    onSelect = {
                        announcerTemplate = template
                        CallProtectionPrefs.setAnnouncerTemplate(context, template)
                    }
                )
            }
            OutlinedTextField(
                value = announcerTemplate,
                onValueChange = {
                    val capped = it.take(AnnouncerScript.MAX_TEMPLATE_LENGTH)
                    announcerTemplate = capped
                    CallProtectionPrefs.setAnnouncerTemplate(context, capped)
                },
                label = { Text(stringResource(R.string.announcer_template_label)) },
                supportingText = { Text(stringResource(R.string.announcer_template_help)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = announcerUserName,
                onValueChange = {
                    val capped = it.take(AnnouncerScript.MAX_USER_NAME_LENGTH)
                    announcerUserName = capped
                    CallProtectionPrefs.setAnnouncerUserName(context, capped)
                },
                label = { Text(stringResource(R.string.announcer_your_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            AnnouncerSlider(
                title = stringResource(R.string.announcer_repeat_title),
                value = announcerRepeat,
                valueLabel = "$announcerRepeat×",
                range = 1f..3f,
                steps = 1,
                onChange = {
                    announcerRepeat = it
                    CallProtectionPrefs.setAnnouncerRepeat(context, it)
                }
            )
            AnnouncerSlider(
                title = stringResource(R.string.announcer_speed_title),
                value = announcerSpeed,
                valueLabel = "$announcerSpeed%",
                range = 50f..150f,
                steps = 0,
                onChange = {
                    announcerSpeed = it
                    CallProtectionPrefs.setAnnouncerSpeedPercent(context, it)
                }
            )
            AnnouncerSlider(
                title = stringResource(R.string.announcer_pitch_title),
                value = announcerPitch,
                valueLabel = "$announcerPitch%",
                range = 50f..150f,
                steps = 0,
                onChange = {
                    announcerPitch = it
                    CallProtectionPrefs.setAnnouncerPitchPercent(context, it)
                }
            )
            AnnouncerSlider(
                title = stringResource(R.string.announcer_volume_title),
                value = announcerVolume,
                valueLabel = "$announcerVolume%",
                range = 10f..100f,
                steps = 0,
                onChange = {
                    announcerVolume = it
                    CallProtectionPrefs.setAnnouncerVolumePercent(context, it)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsRowToggle(
                title = stringResource(R.string.announcer_unknown_title),
                subtitle = stringResource(R.string.announcer_unknown_sub),
                checked = announceUnknown,
                onCheckedChange = {
                    announceUnknown = it
                    CallProtectionPrefs.setAnnounceUnknown(context, it)
                }
            )
            SettingsRowToggle(
                title = stringResource(R.string.announcer_headphones_title),
                subtitle = stringResource(R.string.announcer_headphones_sub),
                checked = headphonesOnly,
                onCheckedChange = {
                    headphonesOnly = it
                    CallProtectionPrefs.setAnnouncerHeadphonesOnly(context, it)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsRowNav(
                title = stringResource(R.string.announcer_test_title),
                subtitle = stringResource(R.string.announcer_test_sub),
                onClick = { announcer.testAnnouncement() },
                icon = Icons.Default.Campaign,
                iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showAddVipDialog) {
        AlertDialog(
            onDismissRequest = { showAddVipDialog = false },
            title = { Text(stringResource(R.string.vip_add)) },
            text = {
                OutlinedTextField(
                    value = vipInput,
                    onValueChange = { vipInput = it },
                    label = { Text(stringResource(R.string.vip_add_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (vipInput.isNotBlank()) {
                            CallProtectionPrefs.addVipNumber(context, vipInput)
                            vipNumbers = CallProtectionPrefs.getVipNumbers(context).sorted()
                        }
                        showAddVipDialog = false
                    }
                ) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddVipDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProtectionChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnnouncerSlider(
    title: String,
    value: Int,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun focusModeLabel(mode: FocusMode): String = when (mode) {
    FocusMode.OFF -> stringResource(R.string.focus_mode_off)
    FocusMode.SAVED_CONTACTS_ONLY -> stringResource(R.string.focus_mode_saved)
    FocusMode.VIP_ONLY -> stringResource(R.string.focus_mode_vip)
    FocusMode.SILENCE_ALL -> stringResource(R.string.focus_mode_silence_all)
}

@Composable
private fun focusModeSubtitle(mode: FocusMode): String = when (mode) {
    FocusMode.OFF -> stringResource(R.string.focus_mode_off_sub)
    FocusMode.SAVED_CONTACTS_ONLY -> stringResource(R.string.focus_mode_saved_sub)
    FocusMode.VIP_ONLY -> stringResource(R.string.focus_mode_vip_sub)
    FocusMode.SILENCE_ALL -> stringResource(R.string.focus_mode_silence_all_sub)
}

@Composable
private fun protectionLevelLabel(level: ProtectionLevel): String = when (level) {
    ProtectionLevel.STANDARD -> stringResource(R.string.protection_standard)
    ProtectionLevel.STRICT -> stringResource(R.string.protection_strict)
    ProtectionLevel.MAXIMUM -> stringResource(R.string.protection_maximum)
}

@Composable
private fun protectionLevelSubtitle(level: ProtectionLevel): String = when (level) {
    ProtectionLevel.STANDARD -> stringResource(R.string.protection_standard_sub)
    ProtectionLevel.STRICT -> stringResource(R.string.protection_strict_sub)
    ProtectionLevel.MAXIMUM -> stringResource(R.string.protection_maximum_sub)
}
