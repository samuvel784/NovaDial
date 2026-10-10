package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FlashAlertSettings
import com.example.data.model.FlashPattern
import com.example.data.model.GestureSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GesturesAndFlashSettingsSheet(
    gestureSettings: GestureSettings,
    flashSettings: FlashAlertSettings,
    onDismiss: () -> Unit,
    onUpdateGestures: (GestureSettings) -> Unit,
    onUpdateFlash: (FlashAlertSettings) -> Unit
) {
    var gSettings by remember { mutableStateOf(gestureSettings) }
    var fSettings by remember { mutableStateOf(flashSettings) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Gesture Controls & Flash Alert",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Gesture Controls Section
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gesture, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gesture Call Controls", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Flip to Mute", fontWeight = FontWeight.SemiBold)
                            Text("Flip phone face-down to mute incoming call ring or mic", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = gSettings.flipToMute,
                            onCheckedChange = {
                                gSettings = gSettings.copy(flipToMute = it)
                                onUpdateGestures(gSettings)
                            }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Raise to Answer", fontWeight = FontWeight.SemiBold)
                            Text("Lift phone to ear to answer incoming calls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = gSettings.raiseToAnswer,
                            onCheckedChange = {
                                gSettings = gSettings.copy(raiseToAnswer = it)
                                onUpdateGestures(gSettings)
                            }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Shake to End Call", fontWeight = FontWeight.SemiBold)
                            Text("Shake phone during active call to hang up", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = gSettings.shakeToEndCall,
                            onCheckedChange = {
                                gSettings = gSettings.copy(shakeToEndCall = it)
                                onUpdateGestures(gSettings)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Flash LED Call Alert Section
            OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Flash / LED Call Alert", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Flash LED for Calls", fontWeight = FontWeight.SemiBold)
                            Text("Blink camera flashlight when receiving calls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = fSettings.enableFlashAlert,
                            onCheckedChange = {
                                fSettings = fSettings.copy(enableFlashAlert = it)
                                onUpdateFlash(fSettings)
                            }
                        )
                    }

                    if (fSettings.enableFlashAlert) {
                        Divider(modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Silent / Vibrate Only", fontWeight = FontWeight.SemiBold)
                                Text("Flash only when phone ringer is silent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = fSettings.flashOnSilentOnly,
                                onCheckedChange = {
                                    fSettings = fSettings.copy(flashOnSilentOnly = it)
                                    onUpdateFlash(fSettings)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Flash Pattern:", style = MaterialTheme.typography.labelLarge)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FlashPattern.values().forEach { pat ->
                                FilterChip(
                                    selected = (fSettings.pattern == pat),
                                    onClick = {
                                        fSettings = fSettings.copy(pattern = pat)
                                        onUpdateFlash(fSettings)
                                    },
                                    label = { Text(pat.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        }
    }
}
