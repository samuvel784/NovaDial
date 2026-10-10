package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.example.data.model.BackupRecordEntity
import com.example.data.sync.BackupManager
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backupHistory by viewModel.backupHistory.collectAsState()
    val restorePreview by viewModel.restorePreview.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var importInputText by remember { mutableStateOf("") }

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
                        text = "AES Vault & Backup",
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
                .testTag("backup_screen"),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Local Encrypted Backup Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AppTheme.colors.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.EnhancedEncryption, contentDescription = null, tint = AppTheme.colors.primary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "NovaDial Encrypted Vault",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = AppTheme.colors.textPrimary
                                )
                                Text(
                                    text = "AES-256-GCM Hardware Keystore Protection",
                                    fontSize = 12.sp,
                                    color = AppTheme.colors.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Back up contacts, favorites, notes, blocked numbers, and call logs into an authenticated ciphertext archive.",
                            fontSize = 13.sp,
                            color = AppTheme.colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.createBackup() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("create_backup_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primary,
                                contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Encrypted Backup Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // vCard Export / Import Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "vCard (.vcf) Standard Interchange",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Export or share all ${allContacts.size} contacts as a standard industry-compatible .vcf file.",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val vcf = BackupManager.exportToVCard(allContacts)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/x-vcard"
                                        putExtra(Intent.EXTRA_TEXT, vcf)
                                        putExtra(Intent.EXTRA_SUBJECT, "contacts_export.vcf")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Contacts"))
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTheme.colors.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export .vcf", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Backup History Section
            item {
                Text(
                    text = "Encrypted Backup Snapshots (${backupHistory.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (backupHistory.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No previous local backups found. Tap 'Create Encrypted Backup Now' above.",
                                color = AppTheme.colors.textMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(backupHistory, key = { it.backupId }) { record ->
                    BackupHistoryCard(
                        record = record,
                        onRestoreClick = { viewModel.previewBackup(record.backupId) }
                    )
                }
            }
        }
    }

    // Restore Preview Bottom Sheet / Dialog
    if (restorePreview != null) {
        val preview = restorePreview!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestorePreview() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = AppTheme.colors.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore Encrypted Backup", color = AppTheme.colors.textPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Backup contains ${preview.recordCount} contacts, ${preview.callLogCount} call logs, and ${preview.blockedCount} blocked numbers.",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "How would you like to restore?",
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.confirmRestore(replaceExisting = false) }) {
                        Text("Merge", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { viewModel.confirmRestore(replaceExisting = true) }) {
                        Text("Replace All", color = AppTheme.colors.error, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRestorePreview() }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.card
        )
    }
}

@Composable
fun BackupHistoryCard(
    record: BackupRecordEntity,
    onRestoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatted = remember(record.timestamp) {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        sdf.format(Date(record.timestamp))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${record.recordCount} Contacts",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AppTheme.colors.textPrimary
                )
                Text(
                    text = dateFormatted,
                    fontSize = 12.sp,
                    color = AppTheme.colors.textSecondary
                )
                Text(
                    text = "AES-256-GCM Hardware Keystore • Authenticated",
                    fontSize = 10.sp,
                    color = AppTheme.colors.primary
                )
            }

            Button(
                onClick = onRestoreClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.primary,
                    contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
