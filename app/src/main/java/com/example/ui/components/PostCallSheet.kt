package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SpamCallerCategory
import com.example.telecom.ActiveCallSession
import com.example.util.InstantMessagingUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCallSheet(
    session: ActiveCallSession,
    onDismiss: () -> Unit,
    onSaveNote: (phoneNumber: String, contactName: String?, noteText: String) -> Unit,
    onSaveContact: (phoneNumber: String) -> Unit,
    onReportSpam: (phoneNumber: String, category: SpamCallerCategory, comment: String) -> Unit
) {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf("") }
    var showReportSpamDialog by remember { mutableStateOf(false) }
    var reminderSavedMessage by remember { mutableStateOf<String?>(null) }

    val formattedDuration = remember(session.durationSeconds) {
        val m = session.durationSeconds / 60
        val s = session.durationSeconds % 60
        String.format("%02d:%02d", m, s)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Call Finished",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = session.displayName.ifBlank { session.number },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "⏱ $formattedDuration",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Post Call Note Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Quick Call Note",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Add follow-up notes (e.g. Call back at 3 PM)...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (noteText.isNotBlank()) {
                                onSaveNote(session.number, session.displayName, noteText)
                                noteText = ""
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = noteText.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Note")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Instant Launcher Action Grid
            Text(
                text = "Instant Action Launcher",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PostCallActionButton(
                    icon = Icons.Default.Chat,
                    label = "WhatsApp",
                    color = Color(0xFF25D366),
                    onClick = {
                        InstantMessagingUtils.openWhatsApp(context, session.number)
                    }
                )
                PostCallActionButton(
                    icon = Icons.Default.Send,
                    label = "Telegram",
                    color = Color(0xFF0088CC),
                    onClick = {
                        InstantMessagingUtils.openTelegram(context, session.number)
                    }
                )
                PostCallActionButton(
                    icon = Icons.Default.Lock,
                    label = "Signal",
                    color = Color(0xFF3A76F0),
                    onClick = {
                        InstantMessagingUtils.openSignal(context, session.number)
                    }
                )
                PostCallActionButton(
                    icon = Icons.Default.Alarm,
                    label = "Reminder",
                    color = Color(0xFFFF9800),
                    onClick = {
                        reminderSavedMessage = "⏰ Follow-up reminder set for 1 hour from now"
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PostCallActionButton(
                    icon = Icons.Default.PersonAdd,
                    label = "Add Contact",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onSaveContact(session.number) }
                )
                PostCallActionButton(
                    icon = Icons.Default.ReportProblem,
                    label = "Report Spam",
                    color = MaterialTheme.colorScheme.error,
                    onClick = { showReportSpamDialog = true }
                )
            }

            reminderSavedMessage?.let { msg ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Done")
            }
        }
    }

    if (showReportSpamDialog) {
        SpamReportDialog(
            phoneNumber = session.number,
            onDismiss = { showReportSpamDialog = false },
            onSubmitReport = { category, comment ->
                onReportSpam(session.number, category, comment)
                showReportSpamDialog = false
            }
        )
    }
}

@Composable
private fun PostCallActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}
