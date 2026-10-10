package com.example.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.telecom.DefaultDialerManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BackupRecordEntity
import com.example.data.model.BackupType
import com.example.data.model.BlockedNumberEntity
import com.example.data.model.SimAccount
import com.example.data.sync.BackupManager
import com.example.telecom.EmergencySettings
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blockedCount by viewModel.blockedCount.collectAsState()
    val simAccounts by viewModel.simAccounts.collectAsState()
    val selectedSim by viewModel.selectedSimSlot.collectAsState()
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()
    val emergencySettings by viewModel.emergencySettings.collectAsState()

    val keyguardManager = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }
    val isPhoneSecure = remember { keyguardManager?.isDeviceSecure == true }

    var pendingLockEnableState by remember { mutableStateOf<Boolean?>(null) }
    val settingsAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pendingState = pendingLockEnableState
        pendingLockEnableState = null
        if (result.resultCode == Activity.RESULT_OK && pendingState != null) {
            viewModel.setAppLock(pendingState, lockType = "PHONE_LOCK")
        } else {
            viewModel.showStatus(
                if (pendingState == true) "App Lock activation cancelled or failed"
                else "Authentication required to disable App Lock"
            )
        }
    }

    var showBlockedListDialog by remember { mutableStateOf(false) }
    var showAddBlockNumberDialog by remember { mutableStateOf(false) }
    var blockNumberInput by remember { mutableStateOf("") }

    val blockedList by viewModel.repository.blockedNumbers.collectAsState(initial = emptyList())
    val isLockEnabled by viewModel.isAppLockEnabled.collectAsState()

    val spamSettings by viewModel.spamProtectionSettings.collectAsState()
    val simRules by viewModel.simRules.collectAsState()
    val flashSettings by viewModel.flashAlertSettings.collectAsState()
    val gestureSettings by viewModel.gestureSettings.collectAsState()
    val backupHistory by viewModel.backupHistory.collectAsState()

    val latestBackup = remember(backupHistory) { backupHistory.maxByOrNull { it.timestamp } }
    val lastBackupDateStr = remember(latestBackup) {
        latestBackup?.let {
            SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(it.timestamp))
        } ?: "No backups created yet"
    }
    val backupStorageLocStr = remember(latestBackup) {
        latestBackup?.let {
            if (it.type == BackupType.CLOUD_SYNC) "Saved on this phone" else "Local AES Encrypted Storage"
        } ?: "Local / Cloud Sync Ready"
    }

    var showMultiSimSheet by remember { mutableStateOf(false) }
    var showCallPosterThemeSelector by remember { mutableStateOf(false) }
    var showGesturesAndFlashSheet by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
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
                .padding(horizontal = 16.dp)
                .testTag("settings_screen"),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: Default Phone App & Branding Logo
            item {
                val isDefault by viewModel.isDefaultDialer.collectAsState()
                val defaultDialerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.checkDefaultDialer(context)
                }

                LaunchedEffect(Unit) {
                    viewModel.checkDefaultDialer(context)
                }

                SettingsSectionHeader("Default Phone App & Branding")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        coil.compose.AsyncImage(
                            model = com.example.R.drawable.novadial_logo_1791362870274,
                            contentDescription = "NovaDial Logo",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Text(
                            text = "NovaDial Phone & Contacts",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = if (isDefault) "NovaDial is set as your default phone app." else "NovaDial is not currently your default phone app for handling calls.",
                            fontSize = 13.sp,
                            color = if (isDefault) AppTheme.colors.secondary else AppTheme.colors.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (!isDefault) {
                            Button(
                                onClick = {
                                    val intent = com.example.telecom.DefaultDialerManager.createRequestDefaultDialerIntent(context)
                                    try {
                                        defaultDialerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        viewModel.requestDefaultDialer(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Set as Default Phone App", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section: Voicemail Guide & Instructional Overlay
            item {
                var showGuideOverlay by remember { mutableStateOf(false) }
                SettingsSectionHeader("Voicemail User Guide")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.tertiary))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Voicemail, contentDescription = null, tint = AppTheme.colors.tertiary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How voicemail works",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = AppTheme.colors.textPrimary
                            )
                        }

                        Text(
                            text = "NovaDial shows the voicemails your carrier delivers to this phone and plays them. It cannot record or send voicemails.",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary
                        )

                        Button(
                            onClick = { showGuideOverlay = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.tertiary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Voicemail Guide", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (showGuideOverlay) {
                    VoicemailGuideOverlayDialog(onDismiss = { showGuideOverlay = false })
                }
            }

            // Section: Appearance & Themes (Day Mode, Default Mode, Dark Mode)
            item {
                SettingsSectionHeader("Theme & Display")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Choose Theme Mode",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Triple(ThemeMode.DAY, "Day Mode", Icons.Default.LightMode),
                                Triple(ThemeMode.DEFAULT, "Default Mode", Icons.Default.BrightnessAuto),
                                Triple(ThemeMode.DARK, "Dark Mode", Icons.Default.DarkMode)
                            ).forEach { (mode, label, icon) ->
                                val isSelected = currentThemeMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppTheme.colors.primary.copy(alpha = 0.2f),
                                        selectedLabelColor = AppTheme.colors.primary,
                                        selectedLeadingIconColor = AppTheme.colors.primary,
                                        containerColor = AppTheme.colors.elevated,
                                        labelColor = AppTheme.colors.textSecondary,
                                        iconColor = AppTheme.colors.textSecondary
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Section: Phone Lock & Security
            item {
                SettingsSectionHeader("Phone Lock & Security")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.PhonelinkLock,
                            title = "Phone Screen Lock",
                            subtitle = if (isPhoneSecure) {
                                "Detected: Uses your phone's PIN, Pattern, or Biometrics"
                            } else {
                                "No phone lock detected in system settings"
                            },
                            action = {
                                Switch(
                                    checked = isLockEnabled,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!isPhoneSecure) {
                                                viewModel.showStatus("Cannot enable App Lock: Set up a screen lock in Android Settings first.")
                                            } else {
                                                pendingLockEnableState = true
                                                val intent = keyguardManager?.createConfirmDeviceCredentialIntent(
                                                    "Enable App Lock",
                                                    "Confirm device screen lock to activate App Lock"
                                                )
                                                if (intent != null) {
                                                    settingsAuthLauncher.launch(intent)
                                                } else {
                                                    viewModel.showStatus("Device credential intent unavailable")
                                                }
                                            }
                                        } else {
                                            pendingLockEnableState = false
                                            val intent = keyguardManager?.createConfirmDeviceCredentialIntent(
                                                "Disable App Lock",
                                                "Confirm device screen lock to deactivate App Lock"
                                            )
                                            if (intent != null) {
                                                settingsAuthLauncher.launch(intent)
                                            } else {
                                                viewModel.showStatus("Device credential intent unavailable")
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AppTheme.colors.primary,
                                        checkedTrackColor = AppTheme.colors.elevated
                                    )
                                )
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        SettingsRow(
                            icon = Icons.Default.Fingerprint,
                            title = "Lock Mechanism",
                            subtitle = if (isLockEnabled) "App locked with phone's native credentials" else "Turn on switch above to activate",
                            action = {
                                if (isLockEnabled) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppTheme.colors.secondary)
                                }
                            }
                        )
                    }
                }
            }

            // Section: Spam Shield & Blacklist
            item {
                SettingsSectionHeader("Spam Shield & Blocklist Manager")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.primary.copy(alpha = 0.5f)))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Shield,
                            title = "Real-Time Spam Detection Service",
                            subtitle = "CallScreeningService & Local Known Spam DB",
                            action = {
                                Button(
                                    onClick = { viewModel.navigateTo(Screen.SpamBlocklistManager) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Open Hub", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        SettingsRow(
                            icon = Icons.Default.Block,
                            title = "Local Blocklist ($blockedCount blocked)",
                            subtitle = "Manage personal blacklist & auto-drop rules",
                            action = {
                                TextButton(onClick = { viewModel.navigateTo(Screen.SpamBlocklistManager) }) {
                                    Text("Manage", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }
            }

            // Section: Focus mode, protection, VIP and caller announcer
            item {
                CallProtectionAndAnnouncerSection()
            }

            // Section: Pro Call & Communication Features
            item {
                SettingsSectionHeader("Pro Call & Communication Features")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.primary.copy(alpha = 0.4f)))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Shield,
                            title = "Smart Caller ID & Community Protection",
                            subtitle = "Community spam database score & auto-decline",
                            action = {
                                Switch(
                                    checked = spamSettings.autoBlockHighRiskSpam,
                                    onCheckedChange = {
                                        viewModel.repository.communitySpamManager.updateSettings(
                                            spamSettings.copy(autoBlockHighRiskSpam = it)
                                        )
                                    }
                                )
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        SettingsRow(
                            icon = Icons.Default.SimCard,
                            title = "Smart Multi-SIM Carrier Rules (${simRules.size} active)",
                            subtitle = "Auto-route calls by country prefix or contact group",
                            action = {
                                TextButton(onClick = { showMultiSimSheet = true }) {
                                    Text("Manage Rules", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        SettingsRow(
                            icon = Icons.Default.Palette,
                            title = "Custom Call Posters & Animated Themes",
                            subtitle = "Neon, Sunset, Emerald, Particle Wave poster themes",
                            action = {
                                TextButton(onClick = { showCallPosterThemeSelector = true }) {
                                    Text("Themes", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        SettingsRow(
                            icon = Icons.Default.Gesture,
                            title = "Gesture Controls & LED Flash Alert",
                            subtitle = "Flip to mute, Raise to answer, Flash LED blinking",
                            action = {
                                TextButton(onClick = { showGesturesAndFlashSheet = true }) {
                                    Text("Configure", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        SettingsRow(
                            icon = Icons.Default.Chat,
                            title = "Instant WhatsApp / Telegram / Signal",
                            subtitle = "Direct chat launcher without saving contact",
                            action = {
                                Icon(Icons.Default.Check, contentDescription = null, tint = AppTheme.colors.secondary)
                            }
                        )
                    }
                }
            }

            // Section: Visual 'Backup & Security' Dashboard
            item {
                SettingsSectionHeader("🛡️ Backup & Security Dashboard")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.primary.copy(alpha = 0.5f)))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Dashboard Banner Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(AppTheme.colors.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AES-256 Vault & Encrypted Backup",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Hardware Keystore Encrypted Ciphertext",
                                    fontSize = 12.sp,
                                    color = AppTheme.colors.primary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppTheme.colors.secondary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "AES-GCM",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.secondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = AppTheme.colors.border)

                        // Backup Status Metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Last Backup Date Box
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.elevated),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("LAST BACKUP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textMuted)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = lastBackupDateStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textPrimary,
                                        maxLines = 2
                                    )
                                }
                            }

                            // Storage Location Box
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.elevated),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (latestBackup?.type == BackupType.CLOUD_SYNC) Icons.Default.CloudQueue else Icons.Default.SdStorage,
                                            contentDescription = null,
                                            tint = AppTheme.colors.tertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("STORAGE LOCATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textMuted)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = backupStorageLocStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textPrimary,
                                        maxLines = 2
                                    )
                                }
                            }
                        }

                        // One-Tap Immediate Manual Backup Button
                        Button(
                            onClick = { viewModel.createBackup() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("one_tap_manual_backup_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primary,
                                contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("⚡ Run Immediate Manual Backup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Auxiliary Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.navigateTo(Screen.Backup) },
                                shape = RoundedCornerShape(10.dp),
                                border = ButtonDefaults.outlinedButtonBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppTheme.colors.textPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Inspect History", fontSize = 12.sp, color = AppTheme.colors.textPrimary)
                            }

                            TextButton(
                                onClick = {
                                    val vcf = BackupManager.exportToVCard(allContacts)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/x-vcard"
                                        putExtra(Intent.EXTRA_TEXT, vcf)
                                        putExtra(Intent.EXTRA_SUBJECT, "contacts_export.vcf")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Export Contacts"))
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = AppTheme.colors.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export vCard", fontSize = 12.sp, color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Section: Emergency SOS & Hardware Trigger Service
            item {
                SettingsSectionHeader("🚨 Emergency SOS & Hardware Trigger")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.error.copy(alpha = 0.5f)))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.PowerSettingsNew,
                            title = "Hardware Power-Button Trigger",
                            subtitle = if (emergencySettings.isServiceEnabled) {
                                "Active: Triple-press power button dials ${emergencySettings.emergencyNumber} (${emergencySettings.countdownSeconds}s countdown)"
                            } else {
                                "Disabled: Turn on to listen for 3x power clicks 24/7"
                            },
                            action = {
                                Switch(
                                    checked = emergencySettings.isServiceEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.updateEmergencySettings(
                                            emergencySettings.copy(isServiceEnabled = checked)
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AppTheme.colors.error,
                                        checkedTrackColor = AppTheme.colors.error.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        SettingsRow(
                            icon = Icons.Default.Emergency,
                            title = "Emergency SOS Hub & ICE",
                            subtitle = "Configure ICE contacts, countdown timer, & test trigger",
                            action = {
                                Button(
                                    onClick = { viewModel.navigateTo(Screen.Emergency) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppTheme.colors.error,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Open SOS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }
            }

            // Section: Speed Dial & Smart Dialer
            item {
                SettingsSectionHeader("⚡ Speed Dial & Shortcuts")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Speed,
                            title = "Speed Dial Keys (2-9)",
                            subtitle = "Long-press dialpad keys 2–9 for instant calling",
                            action = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppTheme.colors.secondary)
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        SettingsRow(
                            icon = Icons.Default.Quickreply,
                            title = "Quick SMS Auto-Reply",
                            subtitle = "Pre-set response messages when busy or rejecting",
                            action = {
                                TextButton(onClick = { viewModel.showStatus("Quick SMS templates active.") }) {
                                    Text("Templates", color = AppTheme.colors.primary)
                                }
                            }
                        )
                    }
                }
            }

            // Section: Analytics & Call Vault
            item {
                SettingsSectionHeader("📊 Analytics")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Insights,
                            title = "Call Duration & Usage Stats",
                            subtitle = "View talk time, call breakdowns, and top callers",
                            action = {
                                IconButton(onClick = { viewModel.navigateTo(Screen.Analytics) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            }

            // Section: SIM & Calling Preferences
            item {
                SettingsSectionHeader("SIM & Calling Preferences")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.SimCard,
                            title = "Default SIM Slot",
                            subtitle = if (simAccounts.isNotEmpty()) {
                                val current = simAccounts.find { it.slotIndex == selectedSim }
                                "${current?.displayName ?: "SIM $selectedSim"} (${current?.carrierName ?: "Carrier"})"
                            } else "SIM $selectedSim",
                            action = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = selectedSim == 1,
                                        onClick = { viewModel.selectedSimSlot.value = 1 },
                                        label = { Text("SIM 1") }
                                    )
                                    FilterChip(
                                        selected = selectedSim == 2,
                                        onClick = { viewModel.selectedSimSlot.value = 2 },
                                        label = { Text("SIM 2") }
                                    )
                                }
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        SettingsRow(
                            icon = Icons.Default.MergeType,
                            title = "Duplicate Cleaner",
                            subtitle = "Scan and merge duplicate sets",
                            action = {
                                TextButton(onClick = { viewModel.navigateTo(Screen.DuplicateReview) }) {
                                    Text("Scan", color = AppTheme.colors.tertiary)
                                }
                            }
                        )
                    }
                }
            }

            // Section: Maintenance
            item {
                SettingsSectionHeader("Maintenance")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column {
                        SettingsRow(
                            icon = Icons.Default.Sync,
                            title = "Sync Device Contacts",
                            subtitle = "Re-read ContactsContract & call logs",
                            action = {
                                TextButton(onClick = {
                                    scope.launch {
                                        viewModel.repository.syncWithDeviceContacts()
                                        viewModel.refreshDuplicates()
                                        viewModel.showStatus("Device sync completed.")
                                    }
                                }) {
                                    Text("Sync Now", color = AppTheme.colors.primary)
                                }
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        SettingsRow(
                            icon = Icons.Default.DeleteSweep,
                            title = "Clear Call History",
                            subtitle = "Delete all call log records",
                            action = {
                                TextButton(onClick = { viewModel.clearCallHistory() }) {
                                    Text("Clear", color = AppTheme.colors.error)
                                }
                            }
                        )
                    }
                }
            }

            // Section: Privacy & Data Sharing
            item {
                SettingsSectionHeader("Privacy & Data Sharing")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column {
                        var contactsConsent by remember { mutableStateOf(viewModel.getContactsSyncConsent()) }
                        SettingsRow(
                            icon = Icons.Default.Security,
                            title = "Contacts Sync Consent",
                            subtitle = "Allow reading device contacts on explicit choice",
                            action = {
                                Switch(
                                    checked = contactsConsent,
                                    onCheckedChange = { checked ->
                                        contactsConsent = checked
                                        viewModel.setContactsSyncConsent(checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AppTheme.colors.primary,
                                        checkedTrackColor = AppTheme.colors.elevated
                                    )
                                )
                            }
                        )
                        HorizontalDivider(color = AppTheme.colors.border)
                        var callLogsConsent by remember { mutableStateOf(viewModel.getCallLogsSyncConsent()) }
                        SettingsRow(
                            icon = Icons.Default.History,
                            title = "Call Logs Sync Consent",
                            subtitle = "Allow reading device call logs on explicit choice",
                            action = {
                                Switch(
                                    checked = callLogsConsent,
                                    onCheckedChange = { checked ->
                                        callLogsConsent = checked
                                        viewModel.setCallLogsSyncConsent(checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AppTheme.colors.primary,
                                        checkedTrackColor = AppTheme.colors.elevated
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showMultiSimSheet) {
        com.example.ui.components.MultiSimRulesSheet(
            rules = simRules,
            onDismiss = { showMultiSimSheet = false },
            onAddRule = { viewModel.addSimRule(it) },
            onToggleRule = { id, en -> viewModel.toggleSimRule(id, en) },
            onDeleteRule = { id -> viewModel.removeSimRule(id) }
        )
    }

    if (showCallPosterThemeSelector) {
        com.example.ui.components.CallPosterThemeSelector(
            phoneNumber = "+1 (555) 0199",
            currentPoster = viewModel.repository.getCallPosterForNumber("+1 (555) 0199"),
            onDismiss = { showCallPosterThemeSelector = false },
            onSavePoster = { poster -> viewModel.setCallPoster(poster.phoneNumber, poster) }
        )
    }

    if (showGesturesAndFlashSheet) {
        com.example.ui.components.GesturesAndFlashSettingsSheet(
            gestureSettings = gestureSettings,
            flashSettings = flashSettings,
            onDismiss = { showGesturesAndFlashSheet = false },
            onUpdateGestures = { viewModel.updateGestureSettings(it) },
            onUpdateFlash = { viewModel.updateFlashSettings(it) }
        )
    }

    // Blocked Numbers Manager Dialog
    if (showBlockedListDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedListDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = AppTheme.colors.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Blocked Numbers Blacklist", color = AppTheme.colors.textPrimary)
                }
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 350.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${blockedList.size} numbers in blacklist", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                        TextButton(onClick = { showAddBlockNumberDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppTheme.colors.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Number", fontSize = 12.sp, color = AppTheme.colors.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (blockedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No blocked numbers in blacklist.", color = AppTheme.colors.textMuted, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(blockedList, key = { it.number }) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.elevated),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(item.number, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                                            if (item.reason.isNotBlank()) {
                                                Text(item.reason, fontSize = 11.sp, color = AppTheme.colors.textSecondary)
                                            }
                                        }
                                        IconButton(
                                            onClick = { viewModel.unblockNumber(item.number) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Unblock", tint = AppTheme.colors.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedListDialog = false }) {
                    Text("Close", color = AppTheme.colors.primary)
                }
            },
            containerColor = AppTheme.colors.card
        )
    }

    // Add Block Number Input Dialog
    if (showAddBlockNumberDialog) {
        AlertDialog(
            onDismissRequest = { showAddBlockNumberDialog = false },
            title = { Text("Block Phone Number", color = AppTheme.colors.textPrimary) },
            text = {
                Column {
                    Text("Enter phone number to reject and block:", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = blockNumberInput,
                        onValueChange = { blockNumberInput = it },
                        placeholder = { Text("+1 (555) 000-0000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.error,
                            unfocusedBorderColor = AppTheme.colors.border,
                            focusedTextColor = AppTheme.colors.textPrimary,
                            unfocusedTextColor = AppTheme.colors.textPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (blockNumberInput.isNotBlank()) {
                            viewModel.blockNumber(blockNumberInput.trim())
                            blockNumberInput = ""
                            showAddBlockNumberDialog = false
                        }
                    },
                    enabled = blockNumberInput.isNotBlank()
                ) {
                    Text("Block", color = AppTheme.colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    blockNumberInput = ""
                    showAddBlockNumberDialog = false
                }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.card
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = AppTheme.colors.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.elevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = AppTheme.colors.textSecondary
            )
        }

        action()
    }
}

fun isDefaultDialer(context: Context): Boolean = DefaultDialerManager.isDefaultDialer(context)

fun requestDefaultDialer(context: Context) {
    DefaultDialerManager.requestDefaultDialer(context)
}

@Composable
private fun VoicemailGuideOverlayDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = AppTheme.colors.tertiary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Voicemail Guide & Tutorial", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppTheme.colors.textPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val steps = listOf(
                    Triple("1. Open the Voicemail tab", "Open Recents, then the Voicemail tab. Voicemails that your carrier stored on this phone appear there.", Icons.Default.Voicemail),
                    Triple("2. Play a message", "Tap a voicemail to play it. Audio comes from the voicemail file on your phone.", Icons.Default.GraphicEq),
                    Triple("3. No visual voicemail?", "Not every carrier supports visual voicemail. In that case the list stays empty: call your carrier's voicemail number to listen.", Icons.Default.PersonSearch)
                )

                steps.forEach { (title, desc, icon) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.elevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AppTheme.colors.tertiary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = AppTheme.colors.tertiary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppTheme.colors.textPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(desc, fontSize = 11.sp, color = AppTheme.colors.textSecondary, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary)
            ) {
                Text("Got It!", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = AppTheme.colors.card
    )
}
