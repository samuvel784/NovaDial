package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EmergencyContact
import com.example.telecom.EmergencySettings
import com.example.telecom.EmergencyTriggerMode
import com.example.ui.MainViewModel
import com.example.ui.theme.AppTheme
import com.example.util.PhoneNumberUtils
import java.util.UUID

/**
 * EmergencyScreen: Dedicated Emergency SOS Hub providing:
 * 1. Background Hardware Trigger Service (Triple-press power button / shake to call without unlocking or opening app)
 * 2. Instant One-Tap SOS Panic Calling
 * 3. Broadcast SMS Alerts to ICE contacts
 * 4. ICE (In Case of Emergency) Contact Management
 * 5. Interactive Test Trigger button for testing lock-screen bypass overlay
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val emergencyContacts by viewModel.emergencyContacts.collectAsState()
    val emergencySettings by viewModel.emergencySettings.collectAsState()
    val isServiceRunning by viewModel.isEmergencyServiceRunning.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showSosConfirm by remember { mutableStateOf(false) }

    if (showSosConfirm) {
        AlertDialog(
            onDismissRequest = { showSosConfirm = false },
            title = { Text("Start emergency SOS?") },
            text = {
                Text("This starts the SOS countdown. When it ends, NovaDial sends your emergency message and can call the emergency number. You can cancel during the countdown.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSosConfirm = false
                    viewModel.triggerEmergencySos("Manual SOS started in the app")
                }) { Text("Start SOS") }
            },
            dismissButton = { TextButton(onClick = { showSosConfirm = false }) { Text("Cancel") } }
        )
    }
    var nameInput by remember { mutableStateOf("") }
    var numberInput by remember { mutableStateOf("") }
    var relationshipInput by remember { mutableStateOf("Family ICE") }

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("emergency_screen"),
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🚨 Emergency SOS Hub",
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.error
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = "Add ICE Contact",
                            tint = AppTheme.colors.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Section 1: Background Hardware Trigger Service
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hardware_emergency_service_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (emergencySettings.isServiceEnabled) {
                            AppTheme.colors.error.copy(alpha = 0.08f)
                        } else {
                            AppTheme.colors.card
                        }
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (emergencySettings.isServiceEnabled) AppTheme.colors.error.copy(alpha = 0.6f) else AppTheme.colors.border
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(AppTheme.colors.error.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = AppTheme.colors.error,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Hardware Button Emergency Trigger",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (emergencySettings.isServiceEnabled) Color(0xFF00E676) else Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (emergencySettings.isServiceEnabled) "Service Active (Listens 24/7)" else "Service Disabled",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (emergencySettings.isServiceEnabled) Color(0xFF00E676) else AppTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }

                            Switch(
                                checked = emergencySettings.isServiceEnabled,
                                onCheckedChange = { isChecked ->
                                    viewModel.updateEmergencySettings(
                                        emergencySettings.copy(isServiceEnabled = isChecked)
                                    )
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppTheme.colors.error,
                                    checkedTrackColor = AppTheme.colors.error.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("emergency_service_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "When activated, triple-pressing the power button (screen on/off) instantly wakes device, bypasses lockscreen, counts down ${emergencySettings.countdownSeconds}s, and dials ${emergencySettings.emergencyNumber} without needing to open the app or unlock phone.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = AppTheme.colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Trigger Mode Chip Selector
                        Text(
                            text = "Trigger Activation Mode:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EmergencyTriggerMode.values().forEach { mode ->
                                val isSelected = emergencySettings.triggerMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateEmergencySettings(
                                            emergencySettings.copy(triggerMode = mode)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = when (mode) {
                                                EmergencyTriggerMode.POWER_TRIPLE_PRESS -> "Power (3x)"
                                                EmergencyTriggerMode.SHAKE_GESTURE -> "Shake"
                                                EmergencyTriggerMode.BOTH -> "Both"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppTheme.colors.error.copy(alpha = 0.2f),
                                        selectedLabelColor = AppTheme.colors.error,
                                        containerColor = AppTheme.colors.elevated,
                                        labelColor = AppTheme.colors.textSecondary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Settings Rows (Countdown, Target Number, manual SOS)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showConfigDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Configure (${emergencySettings.emergencyNumber}, ${emergencySettings.countdownSeconds}s)", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    showSosConfirm = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppTheme.colors.error,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_emergency_trigger_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start SOS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section 2: Instant SOS Panic Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.error.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(18.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.error))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.error)
                                .clickable {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${emergencySettings.emergencyNumber}"))
                                    context.startActivity(intent)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Emergency,
                                contentDescription = "SOS",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "QUICK SOS CALL (${emergencySettings.emergencyNumber})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AppTheme.colors.error
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Tap button above for direct emergency line connection (Police, Ambulance, Fire).",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Section 3: Quick Assistance SMS Alert
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.tertiary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmsFailed,
                                contentDescription = null,
                                tint = AppTheme.colors.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Broadcast Emergency SMS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "Send instant SOS alert text to all your ICE emergency contacts.",
                                fontSize = 11.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        Button(
                            onClick = {
                                val numbers = emergencyContacts.map { it.number }.joinToString(";")
                                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$numbers")).apply {
                                    putExtra("sms_body", emergencySettings.emergencySmsText)
                                }
                                context.startActivity(smsIntent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.tertiary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Send SOS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section 4: ICE Contacts Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ICE & Emergency Contacts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("+ Add Contact", color = AppTheme.colors.primary, fontSize = 12.sp)
                    }
                }
            }

            items(emergencyContacts, key = { it.id }) { contact ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (contact.isPrimary) AppTheme.colors.error.copy(alpha = 0.15f) else AppTheme.colors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (contact.isPrimary) Icons.Default.MedicalServices else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (contact.isPrimary) AppTheme.colors.error else AppTheme.colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "${contact.relationship} • ${PhoneNumberUtils.formatForDisplay(contact.number)}",
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = AppTheme.colors.secondary)
                        }

                        if (!contact.isPrimary) {
                            IconButton(onClick = { viewModel.repository.removeEmergencyContact(contact.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = AppTheme.colors.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    // Configure Emergency Trigger Parameters Dialog
    if (showConfigDialog) {
        var tempNumber by remember { mutableStateOf(emergencySettings.emergencyNumber) }
        var tempCountdown by remember { mutableIntStateOf(emergencySettings.countdownSeconds) }
        var tempSiren by remember { mutableStateOf(emergencySettings.isSirenEnabled) }
        var tempVibrate by remember { mutableStateOf(emergencySettings.isVibrationEnabled) }
        var tempAutoSms by remember { mutableStateOf(emergencySettings.isAutoSmsEnabled) }
        var tempSmsText by remember { mutableStateOf(emergencySettings.emergencySmsText) }

        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = {
                Text(
                    text = "Configure Emergency Trigger",
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = tempNumber,
                        onValueChange = { tempNumber = it },
                        label = { Text("Emergency Number (112 works on every mobile network, or an ICE number)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Countdown Before Auto-Dialing:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0, 3, 5, 10).forEach { seconds ->
                            FilterChip(
                                selected = tempCountdown == seconds,
                                onClick = { tempCountdown = seconds },
                                label = { Text(if (seconds == 0) "Instant" else "${seconds}s") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sound Siren / Alarm Tone", fontSize = 13.sp, color = AppTheme.colors.textPrimary)
                        Switch(checked = tempSiren, onCheckedChange = { tempSiren = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vibrate SOS Pattern", fontSize = 13.sp, color = AppTheme.colors.textPrimary)
                        Switch(checked = tempVibrate, onCheckedChange = { tempVibrate = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Auto-Send SOS SMS to ICE", fontSize = 13.sp, color = AppTheme.colors.textPrimary)
                        Switch(checked = tempAutoSms, onCheckedChange = { tempAutoSms = it })
                    }

                    if (tempAutoSms) {
                        OutlinedTextField(
                            value = tempSmsText,
                            onValueChange = { tempSmsText = it },
                            label = { Text("SOS Message Content") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateEmergencySettings(
                            emergencySettings.copy(
                                emergencyNumber = tempNumber.trim().ifBlank { "112" },
                                countdownSeconds = tempCountdown,
                                isSirenEnabled = tempSiren,
                                isVibrationEnabled = tempVibrate,
                                isAutoSmsEnabled = tempAutoSms,
                                emergencySmsText = tempSmsText.trim()
                            )
                        )
                        showConfigDialog = false
                    }
                ) {
                    Text("Save Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = AppTheme.colors.card
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Emergency Contact", fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Contact Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = numberInput,
                        onValueChange = { numberInput = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = relationshipInput,
                        onValueChange = { relationshipInput = it },
                        label = { Text("Relationship / Type (e.g. Doctor, Sister)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank() && numberInput.isNotBlank()) {
                            viewModel.repository.addEmergencyContact(
                                EmergencyContact(
                                    id = UUID.randomUUID().toString(),
                                    name = nameInput.trim(),
                                    number = numberInput.trim(),
                                    relationship = relationshipInput.trim()
                                )
                            )
                            nameInput = ""
                            numberInput = ""
                            showAddDialog = false
                            viewModel.showStatus("Emergency contact added.")
                        }
                    }
                ) {
                    Text("Save Contact")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = AppTheme.colors.card
        )
    }
}
