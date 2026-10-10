package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactEntity
import com.example.data.sync.BackupManager
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import com.example.util.PhoneNumberUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var contact by remember { mutableStateOf<ContactEntity?>(null) }
    var decryptedNotes by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(contactId) {
        val found = viewModel.repository.getContactById(contactId)
        contact = found
        if (found != null) {
            decryptedNotes = viewModel.repository.decryptContactNotes(found.notes)
        }
    }

    BackHandler {
        viewModel.navigateBack()
    }

    if (contact == null) {
        Box(
            modifier = modifier.fillMaxSize().background(AppTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AppTheme.colors.primary)
        }
        return
    }

    val currentContact = contact!!

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.toggleFavorite(currentContact)
                        contact = currentContact.copy(isStarred = !currentContact.isStarred)
                    }) {
                        Icon(
                            imageVector = if (currentContact.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (currentContact.isStarred) AppTheme.colors.tertiary else AppTheme.colors.textSecondary
                        )
                    }
                    IconButton(onClick = { viewModel.navigateTo(Screen.EditContact(currentContact.id)) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = AppTheme.colors.primary)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AppTheme.colors.error)
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
                .padding(horizontal = 20.dp)
                .testTag("contact_detail_screen"),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Hero Profile
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.elevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentContact.getInitials(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentContact.isStarred) AppTheme.colors.tertiary else AppTheme.colors.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = currentContact.displayName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    if (currentContact.jobTitle.isNotBlank() || currentContact.company.isNotBlank()) {
                        val subtitle = listOf(currentContact.jobTitle, currentContact.company)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                        Text(
                            text = subtitle,
                            fontSize = 14.sp,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Quick Action Buttons (Call, Message, Share vCard)
            item {
                val primaryPhone = currentContact.getPrimaryPhoneNumber()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Call Button
                    FilledTonalIconButton(
                        onClick = {
                            if (primaryPhone.isNotBlank()) {
                                viewModel.initiateCall(primaryPhone, currentContact.displayName)
                            }
                        },
                        modifier = Modifier.size(54.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = AppTheme.colors.secondary.copy(alpha = 0.15f),
                            contentColor = AppTheme.colors.secondary
                        )
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call", modifier = Modifier.size(24.dp))
                    }

                    // SMS Button
                    FilledTonalIconButton(
                        onClick = {
                            if (primaryPhone.isNotBlank()) {
                                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:${Uri.encode(primaryPhone)}")
                                }
                                context.startActivity(smsIntent)
                            }
                        },
                        modifier = Modifier.size(54.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = AppTheme.colors.primary.copy(alpha = 0.15f),
                            contentColor = AppTheme.colors.primary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Message", modifier = Modifier.size(24.dp))
                    }

                    // Share vCard Button
                    FilledTonalIconButton(
                        onClick = {
                            val vcf = BackupManager.exportToVCard(listOf(currentContact))
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/x-vcard"
                                putExtra(Intent.EXTRA_TEXT, vcf)
                                putExtra(Intent.EXTRA_SUBJECT, "${currentContact.displayName}.vcf")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Contact"))
                        },
                        modifier = Modifier.size(54.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = AppTheme.colors.tertiary.copy(alpha = 0.15f),
                            contentColor = AppTheme.colors.tertiary
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share vCard", modifier = Modifier.size(24.dp))
                    }
                }

                // Instant Messaging Launcher Row (WhatsApp, Telegram, Signal)
                if (primaryPhone.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Surface(
                            onClick = { com.example.util.InstantMessagingUtils.openWhatsApp(context, primaryPhone) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF25D366).copy(alpha = 0.15f)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp", color = Color(0xFF25D366), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Surface(
                            onClick = { com.example.util.InstantMessagingUtils.openTelegram(context, primaryPhone) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0088CC).copy(alpha = 0.15f)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Send, contentDescription = "Telegram", tint = Color(0xFF0088CC), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Telegram", color = Color(0xFF0088CC), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Surface(
                            onClick = { com.example.util.InstantMessagingUtils.openSignal(context, primaryPhone) },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF3A76F0).copy(alpha = 0.15f)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = "Signal", tint = Color(0xFF3A76F0), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Signal", color = Color(0xFF3A76F0), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Spam Shield & Control Card
            item {
                val primaryPhone = currentContact.getPrimaryPhoneNumber()
                if (primaryPhone.isNotBlank()) {
                    val allSpamEntries by viewModel.allSpamEntries.collectAsState(initial = emptyList())
                    val isSpam = remember(allSpamEntries, primaryPhone) {
                        val norm = PhoneNumberUtils.normalize(primaryPhone)
                        allSpamEntries.any { it.normalizedNumber == norm || it.phoneNumber == primaryPhone }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSpam) AppTheme.colors.error.copy(alpha = 0.12f) else AppTheme.colors.card),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSpam) AppTheme.colors.error else AppTheme.colors.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isSpam) "⚠️ SPAM CONTACT" else "Spam Protection",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSpam) AppTheme.colors.error else AppTheme.colors.textPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isSpam) "This contact is marked in red as spam" else "Add or remove this contact from spam list",
                                    color = AppTheme.colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = {
                                    if (isSpam) {
                                        viewModel.removeNumberFromSpam(primaryPhone)
                                    } else {
                                        viewModel.addNumberToSpam(primaryPhone, currentContact.displayName)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSpam) AppTheme.colors.secondary else AppTheme.colors.error
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (isSpam) "Remove Spam" else "Add to Spam",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Phone Numbers Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Phone Numbers",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val phones = currentContact.getPhoneNumbers()
                        if (phones.isEmpty()) {
                            Text("No phone numbers added", color = AppTheme.colors.textMuted, fontSize = 13.sp)
                        } else {
                            phones.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = PhoneNumberUtils.formatForDisplay(item.number),
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppTheme.colors.textPrimary,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = item.type,
                                            color = AppTheme.colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(onClick = {
                                        clipboardManager.setText(AnnotatedString(item.number))
                                        viewModel.showStatus("Copied ${item.number}")
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = AppTheme.colors.textMuted, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = {
                                        viewModel.initiateCall(item.number, currentContact.displayName)
                                    }) {
                                        Icon(Icons.Default.Call, contentDescription = "Call", tint = AppTheme.colors.secondary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Emails Card
            item {
                val emails = currentContact.getEmails()
                if (emails.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                        shape = RoundedCornerShape(16.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Email Addresses",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            emails.forEach { email ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = email.email, color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                                        Text(text = email.type, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                                    }
                                    IconButton(onClick = {
                                        val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:${email.email}")
                                        }
                                        context.startActivity(mailIntent)
                                    }) {
                                        Icon(Icons.Default.Email, contentDescription = "Email", tint = AppTheme.colors.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Encrypted Notes Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Encrypted Notes (AES-256)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = decryptedNotes.ifBlank { "No notes stored for this contact." },
                            color = if (decryptedNotes.isNotBlank()) AppTheme.colors.textPrimary else AppTheme.colors.textMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Contact?", color = AppTheme.colors.textPrimary) },
            text = { Text("Are you sure you want to delete ${currentContact.displayName}? This action cannot be undone.", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteContact(currentContact.id)
                        viewModel.navigateBack()
                    }
                ) {
                    Text("Delete", color = AppTheme.colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.card
        )
    }
}
