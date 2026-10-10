package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telecom.AnnouncerScript
import com.example.telecom.CallProtectionPrefs
import com.example.telecom.CallerAnnouncerManager
import com.example.telecom.FocusMode
import com.example.telecom.ProtectionLevel
import com.example.ui.theme.AppTheme

private enum class Dlg { NONE, FOCUS, PROTECTION, STYLE, CUSTOM_TEXT, USER_NAME, REPEAT, SPEED, PITCH, VOLUME, VIP }

/**
 * Settings for Focus mode, call protection, VIP numbers and the caller announcer.
 * Every value is saved to [CallProtectionPrefs] and used by the screening service and the announcer.
 */
@Composable
fun CallProtectionAndAnnouncerSection() {
    val context = LocalContext.current

    var focus by remember { mutableStateOf(CallProtectionPrefs.getFocusMode(context)) }
    var protection by remember { mutableStateOf(CallProtectionPrefs.getProtectionLevel(context)) }
    var repeatAllowed by remember { mutableStateOf(CallProtectionPrefs.isRepeatCallerAllowed(context)) }
    var vipNumbers by remember { mutableStateOf(CallProtectionPrefs.getVipNumbers(context)) }

    var annEnabled by remember { mutableStateOf(CallProtectionPrefs.isAnnouncerEnabled(context)) }
    var template by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerTemplate(context)) }
    var userName by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerUserName(context)) }
    var annRepeat by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerRepeat(context)) }
    var speed by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerSpeedPercent(context)) }
    var pitch by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerPitchPercent(context)) }
    var volume by remember { mutableStateOf(CallProtectionPrefs.getAnnouncerVolumePercent(context)) }
    var headphonesOnly by remember { mutableStateOf(CallProtectionPrefs.isAnnouncerHeadphonesOnly(context)) }
    var announceUnknown by remember { mutableStateOf(CallProtectionPrefs.isAnnounceUnknown(context)) }

    var dialog by remember { mutableStateOf(Dlg.NONE) }

    // the test button needs its own speech engine, because the call service only exists during a call
    val tester = remember { CallerAnnouncerManager(context) }
    DisposableEffect(Unit) { onDispose { tester.shutdown() } }

    Column {
        SettingsSectionHeader("Focus Mode, Protection & Caller Announcer")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                SettingsRow(
                    icon = Icons.Default.Notifications,
                    title = "Focus mode",
                    subtitle = focusText(focus),
                    action = { ChangeButton { dialog = Dlg.FOCUS } }
                )
                HorizontalDivider(color = AppTheme.colors.border)
                SettingsRow(
                    icon = Icons.Default.Shield,
                    title = "Call protection level",
                    subtitle = protectionText(protection),
                    action = { ChangeButton { dialog = Dlg.PROTECTION } }
                )
                HorizontalDivider(color = AppTheme.colors.border)
                SettingsRow(
                    icon = Icons.Default.Repeat,
                    title = "Let repeat callers through",
                    subtitle = "The same number twice within 3 minutes rings even when silenced",
                    action = {
                        Switch(checked = repeatAllowed, onCheckedChange = {
                            repeatAllowed = it
                            CallProtectionPrefs.setRepeatCallerAllowed(context, it)
                        })
                    }
                )
                HorizontalDivider(color = AppTheme.colors.border)
                SettingsRow(
                    icon = Icons.Default.Star,
                    title = "VIP numbers (${vipNumbers.size})",
                    subtitle = "Always ring, even in Focus mode. Silencing needs Android 10 or newer; silenced calls show as missed calls",
                    action = { ChangeButton("Manage") { dialog = Dlg.VIP } }
                )
                HorizontalDivider(color = AppTheme.colors.border)
                SettingsRow(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Speak the caller's name",
                    subtitle = "Read out loud while the phone rings. Silent mode and Do Not Disturb stay silent. Max 15 seconds",
                    action = {
                        Switch(checked = annEnabled, onCheckedChange = {
                            annEnabled = it
                            CallProtectionPrefs.setAnnouncerEnabled(context, it)
                        })
                    }
                )
                if (annEnabled) {
                    HorizontalDivider(color = AppTheme.colors.border)
                    SettingsRow(
                        icon = Icons.Default.Tune,
                        title = "Message",
                        subtitle = template,
                        action = { ChangeButton { dialog = Dlg.STYLE } }
                    )
                    HorizontalDivider(color = AppTheme.colors.border)
                    SettingsRow(
                        icon = Icons.Default.Person,
                        title = "Your name (for {user})",
                        subtitle = userName.ifBlank { "Not set" },
                        action = { ChangeButton { dialog = Dlg.USER_NAME } }
                    )
                    HorizontalDivider(color = AppTheme.colors.border)
                    SettingsRow(
                        icon = Icons.Default.Repeat,
                        title = "Repeat",
                        subtitle = if (annRepeat == 1) "Once" else "$annRepeat times (every 5 seconds)",
                        action = { ChangeButton { dialog = Dlg.REPEAT } }
                    )
                    HorizontalDivider(color = AppTheme.colors.border)
                    SettingsRow(
                        icon = Icons.Default.Speed,
                        title = "Speech speed",
                        subtitle = when {
                            speed < 95 -> "Slow"
                            speed > 110 -> "Fast"
                            else -> "Normal"
                        },
                        action = { ChangeButton { dialog = Dlg.SPEED } }
                    )
                    HorizontalDivider(color = AppTheme.colors.border)
                    SettingsRow(
                        icon = Icons.Default.Tune,
                        title = "Voice pitch",
                        subtitle = when {
                            pitch < 95 -> "Low"
                            pitch > 110 -> "High"
                            else -> "Normal"
                        },
                        action = { ChangeButton { dialog = Dlg.PITCH } }
                    )
                    HorizontalDivider(color = AppTheme.colors.border)
                    SettingsRow(
                        icon = Icons.Default.VolumeUp,
                        title = "Announcement volume",
                        subtitle = when {
                            volume <= 40 -> "Low (40%)"
                            volume <= 70 -> "Medium (70%)"
                            else -> "High (100%)"
                        },
                        action = { ChangeButton { dialog = Dlg.VOLUME } }
                    )
                    HorizontalDivider(color = AppTheme.colors.border)
                    SettingsRow(
                        icon = Icons.Default.Block,
                        title = "Also announce unknown numbers",
                        subtitle = "Says \"an unknown number\" for callers that are not in your contacts",
                        action = {
                            Switch(checked = announceUnknown, onCheckedChange = {
                                announceUnknown = it
                                CallProtectionPrefs.setAnnounceUnknown(context, it)
                            })
                        }
                    )
                    HorizontalDivider(color = AppTheme.colors.border)
                    SettingsRow(
                        icon = Icons.Default.Headphones,
                        title = "Private mode",
                        subtitle = "Announce only when headphones are connected",
                        action = {
                            Switch(checked = headphonesOnly, onCheckedChange = {
                                headphonesOnly = it
                                CallProtectionPrefs.setAnnouncerHeadphonesOnly(context, it)
                            })
                        }
                    )
                    HorizontalDivider(color = AppTheme.colors.border)
                    SettingsRow(
                        icon = Icons.Default.PlayArrow,
                        title = "Play a test announcement",
                        subtitle = "Uses the message, speed, pitch and volume above",
                        action = { ChangeButton("Play") { tester.testAnnouncement() } }
                    )
                }
            }
        }
    }

    when (dialog) {
        Dlg.FOCUS -> ChoiceDialog(
            title = "Focus mode",
            options = FocusMode.values().map { focusText(it) to it },
            selected = focus,
            onSelect = { focus = it; CallProtectionPrefs.setFocusMode(context, it) },
            onDismiss = { dialog = Dlg.NONE }
        )
        Dlg.PROTECTION -> ChoiceDialog(
            title = "Call protection level",
            options = ProtectionLevel.values().map { protectionText(it) to it },
            selected = protection,
            onSelect = { protection = it; CallProtectionPrefs.setProtectionLevel(context, it) },
            onDismiss = { dialog = Dlg.NONE }
        )
        Dlg.STYLE -> ChoiceDialog(
            title = "Message",
            options = AnnouncerScript.PRESETS.map { (name, text) -> "$name: $text" to text } +
                listOf("Custom text..." to "__custom__"),
            selected = AnnouncerScript.PRESETS.firstOrNull { it.second == template }?.second ?: "__custom__",
            onSelect = {
                if (it == "__custom__") {
                    dialog = Dlg.CUSTOM_TEXT
                } else {
                    template = it
                    CallProtectionPrefs.setAnnouncerTemplate(context, it)
                }
            },
            onDismiss = { if (dialog == Dlg.STYLE) dialog = Dlg.NONE }
        )
        Dlg.CUSTOM_TEXT -> TextDialog(
            title = "Custom message",
            help = "Use {name}, {user} and {type}. Example: Call from {name}",
            initial = template,
            maxLength = AnnouncerScript.MAX_TEMPLATE_LENGTH,
            onSave = {
                val value = it.ifBlank { AnnouncerScript.DEFAULT_TEMPLATE }
                template = value
                CallProtectionPrefs.setAnnouncerTemplate(context, value)
            },
            onDismiss = { dialog = Dlg.NONE }
        )
        Dlg.USER_NAME -> TextDialog(
            title = "Your name",
            help = "Spoken where the message contains {user}",
            initial = userName,
            maxLength = AnnouncerScript.MAX_USER_NAME_LENGTH,
            onSave = {
                userName = it
                CallProtectionPrefs.setAnnouncerUserName(context, it)
            },
            onDismiss = { dialog = Dlg.NONE }
        )
        Dlg.REPEAT -> ChoiceDialog(
            title = "Repeat",
            options = listOf("Once" to 1, "Twice" to 2, "Three times" to 3),
            selected = annRepeat,
            onSelect = { annRepeat = it; CallProtectionPrefs.setAnnouncerRepeat(context, it) },
            onDismiss = { dialog = Dlg.NONE }
        )
        Dlg.SPEED -> ChoiceDialog(
            title = "Speech speed",
            options = listOf("Slow" to 80, "Normal" to 100, "Fast" to 125),
            selected = speed,
            onSelect = { speed = it; CallProtectionPrefs.setAnnouncerSpeedPercent(context, it) },
            onDismiss = { dialog = Dlg.NONE }
        )
        Dlg.PITCH -> ChoiceDialog(
            title = "Voice pitch",
            options = listOf("Low" to 90, "Normal" to 100, "High" to 115),
            selected = pitch,
            onSelect = { pitch = it; CallProtectionPrefs.setAnnouncerPitchPercent(context, it) },
            onDismiss = { dialog = Dlg.NONE }
        )
        Dlg.VOLUME -> ChoiceDialog(
            title = "Announcement volume",
            options = listOf("Low (40%)" to 40, "Medium (70%)" to 70, "High (100%)" to 100),
            selected = volume,
            onSelect = { volume = it; CallProtectionPrefs.setAnnouncerVolumePercent(context, it) },
            onDismiss = { dialog = Dlg.NONE }
        )
        Dlg.VIP -> VipDialog(
            numbers = vipNumbers,
            onAdd = {
                CallProtectionPrefs.addVipNumber(context, it)
                vipNumbers = CallProtectionPrefs.getVipNumbers(context)
            },
            onRemove = {
                CallProtectionPrefs.removeVipNumber(context, it)
                vipNumbers = CallProtectionPrefs.getVipNumbers(context)
            },
            onDismiss = { dialog = Dlg.NONE }
        )
        Dlg.NONE -> Unit
    }
}

private fun focusText(mode: FocusMode): String = when (mode) {
    FocusMode.OFF -> "Off: all calls ring normally"
    FocusMode.SAVED_CONTACTS_ONLY -> "Saved contacts only: everyone else is silenced"
    FocusMode.VIP_ONLY -> "VIP only: only your VIP numbers ring"
    FocusMode.SILENCE_ALL -> "Silence all: only VIP and favorites ring"
}

private fun protectionText(level: ProtectionLevel): String = when (level) {
    ProtectionLevel.STANDARD -> "Standard: only your blocklist and spam rules"
    ProtectionLevel.STRICT -> "Strict: numbers not in your contacts are silenced"
    ProtectionLevel.MAXIMUM -> "Maximum: only VIP, favorites and repeat callers ring"
}

@Composable
private fun ChangeButton(label: String = "Change", onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                if (value != "__custom__") onDismiss()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = {
                                onSelect(value)
                                if (value != "__custom__") onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TextDialog(
    title: String,
    help: String,
    initial: String,
    maxLength: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= maxLength) text = it },
                    singleLine = false
                )
                Text(help, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun VipDialog(
    numbers: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newNumber by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("VIP numbers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newNumber,
                    onValueChange = { newNumber = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' } },
                    label = { Text("Phone number, with country code if possible") },
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        if (newNumber.any { it.isDigit() }) {
                            onAdd(newNumber.trim())
                            newNumber = ""
                        }
                    }
                ) { Text("Add VIP number") }

                if (numbers.isEmpty()) {
                    Text("No VIP numbers yet", fontSize = 12.sp)
                } else {
                    numbers.sorted().forEach { number ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(number, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            IconButton(onClick = { onRemove(number) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove $number")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
