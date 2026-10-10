package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.ui.CallHistoryFilter
import com.example.ui.CallLogViewModel
import com.example.ui.theme.AppTheme
import com.example.util.PhoneNumberUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * CallHistoryScreen: ContentResolver-powered screen that displays native device
 * call history, stats summary, smart search, and respects the currently selected theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    callLogViewModel: CallLogViewModel = viewModel(),
    onCallClick: (String) -> Unit = {},
    onContactClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val callLogs by callLogViewModel.displayedCallLogs.collectAsState()
    val activeFilter by callLogViewModel.filter.collectAsState()
    val searchQuery by callLogViewModel.searchQuery.collectAsState()
    val isLoading by callLogViewModel.isLoading.collectAsState()
    val permissionGranted by callLogViewModel.permissionGranted.collectAsState()
    val stats by callLogViewModel.callStats.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        callLogViewModel.onPermissionResult(granted)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("call_history_screen"),
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Call History",
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                actions = {
                    IconButton(
                        onClick = { callLogViewModel.loadDeviceCallLogs() },
                        modifier = Modifier.testTag("refresh_call_logs_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = AppTheme.colors.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { callLogViewModel.setSearchQuery(it) },
                placeholder = { Text("Search by name, number, or T9...", color = AppTheme.colors.textMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = AppTheme.colors.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { callLogViewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AppTheme.colors.textSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppTheme.colors.card,
                    unfocusedContainerColor = AppTheme.colors.card,
                    focusedBorderColor = AppTheme.colors.primary,
                    unfocusedBorderColor = AppTheme.colors.border,
                    focusedTextColor = AppTheme.colors.textPrimary,
                    unfocusedTextColor = AppTheme.colors.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("call_history_search_input")
            )

            // Quick Call Analytics Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Total Calls", fontSize = 11.sp, color = AppTheme.colors.textSecondary)
                        Text(text = "${stats.totalCalls}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textPrimary)
                    }
                    VerticalDivider(modifier = Modifier.height(24.dp), color = AppTheme.colors.border)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Missed", fontSize = 11.sp, color = AppTheme.colors.textSecondary)
                        Text(text = "${stats.missedCalls}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.error)
                    }
                    VerticalDivider(modifier = Modifier.height(24.dp), color = AppTheme.colors.border)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Total Talk Time", fontSize = 11.sp, color = AppTheme.colors.textSecondary)
                        Text(text = stats.totalDurationFormatted, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.secondary)
                    }
                }
            }

            // Filter Tabs (All, Missed, Incoming, Outgoing)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CallHistoryFilter.values()) { f ->
                    val isSelected = activeFilter == f
                    FilterChip(
                        selected = isSelected,
                        onClick = { callLogViewModel.setFilter(f) },
                        label = {
                            Text(
                                text = f.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppTheme.colors.primary.copy(alpha = 0.2f),
                            selectedLabelColor = AppTheme.colors.primary,
                            containerColor = AppTheme.colors.elevated,
                            labelColor = AppTheme.colors.textSecondary
                        )
                    )
                }
            }

            // Permission Request Banner (if needed)
            if (!permissionGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneCallback,
                            contentDescription = null,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Permission Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Grant Call Log permission to read device call history via Android ContentResolver.",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { permissionLauncher.launch(android.Manifest.permission.READ_CALL_LOG) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primary,
                                contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Grant Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Call Logs List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppTheme.colors.primary)
                }
            } else if (callLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = null,
                            tint = AppTheme.colors.textMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No call records found",
                            color = AppTheme.colors.textSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(callLogs, key = { "${it.number}_${it.timestamp}" }) { log ->
                        CallLogItemRow(
                            log = log,
                            onCall = {
                                onCallClick(log.number)
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${log.number}"))
                                context.startActivity(intent)
                            },
                            onSms = {
                                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${log.number}"))
                                context.startActivity(smsIntent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogItemRow(
    log: CallLogEntry,
    onCall: () -> Unit,
    onSms: () -> Unit
) {
    val (icon: ImageVector, iconColor: Color) = when (log.callType) {
        CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to AppTheme.colors.secondary
        CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to AppTheme.colors.primary
        CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to AppTheme.colors.error
        CallType.REJECTED -> Icons.Default.CallEnd to AppTheme.colors.error
        CallType.BLOCKED -> Icons.Default.Block to AppTheme.colors.error
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val timeFormatted = remember(log.timestamp) { dateFormatter.format(Date(log.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_log_item_${log.number}"),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = log.callType.name,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Caller Info & Timestamp
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.callerName ?: PhoneNumberUtils.formatForDisplay(log.number),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (log.callType == CallType.MISSED) AppTheme.colors.error else AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!log.callerName.isNullOrBlank()) {
                    Text(
                        text = PhoneNumberUtils.formatForDisplay(log.number),
                        fontSize = 12.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = timeFormatted,
                        fontSize = 11.sp,
                        color = AppTheme.colors.textMuted
                    )
                    if (log.durationSeconds > 0) {
                        Text(
                            text = " • ${log.durationSeconds}s",
                            fontSize = 11.sp,
                            color = AppTheme.colors.textMuted
                        )
                    }
                }
            }

            // Quick Call & SMS Actions
            IconButton(onClick = onSms) {
                Icon(
                    imageVector = Icons.Default.Message,
                    contentDescription = "SMS",
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onCall) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = AppTheme.colors.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
