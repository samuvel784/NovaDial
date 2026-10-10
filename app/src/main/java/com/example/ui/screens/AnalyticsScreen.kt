package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallType
import com.example.ui.MainViewModel
import com.example.ui.theme.AppTheme

/**
 * AnalyticsScreen: Visual Call Duration & Usage Breakdown showing talk time,
 * call distributions, peak hours, and top frequent contacts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val allLogs by viewModel.allCallLogs.collectAsState()

    val totalCalls = remember(allLogs) { allLogs.size }
    val incomingCount = remember(allLogs) { allLogs.count { it.callType == CallType.INCOMING } }
    val outgoingCount = remember(allLogs) { allLogs.count { it.callType == CallType.OUTGOING } }
    val missedCount = remember(allLogs) { allLogs.count { it.callType == CallType.MISSED || it.callType == CallType.REJECTED } }
    val spamBlockedCount = remember(allLogs) { allLogs.count { it.isSpam || it.callType == CallType.BLOCKED } }

    val totalDurationSeconds = remember(allLogs) { allLogs.sumOf { it.durationSeconds } }
    val formattedDuration = remember(totalDurationSeconds) {
        val hours = totalDurationSeconds / 3600
        val mins = (totalDurationSeconds % 3600) / 60
        if (hours > 0) "${hours}h ${mins}m" else "${mins}m ${totalDurationSeconds % 60}s"
    }

    val topContacts = remember(allLogs) {
        allLogs
            .groupBy { it.callerName ?: it.number }
            .map { (name, list) -> Triple(name, list.size, list.sumOf { it.durationSeconds }) }
            .sortedByDescending { it.second }
            .take(5)
    }

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("analytics_screen"),
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Call Analytics & Stats",
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Main Overview Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Talk Time",
                                    fontSize = 12.sp,
                                    color = AppTheme.colors.textSecondary
                                )
                                Text(
                                    text = formattedDuration,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AppTheme.colors.primary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(AppTheme.colors.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Insights, contentDescription = null, tint = AppTheme.colors.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatMiniBadge(title = "Total", value = "$totalCalls", color = AppTheme.colors.primary)
                            StatMiniBadge(title = "Incoming", value = "$incomingCount", color = AppTheme.colors.secondary)
                            StatMiniBadge(title = "Outgoing", value = "$outgoingCount", color = AppTheme.colors.primary)
                            StatMiniBadge(title = "Missed", value = "$missedCount", color = AppTheme.colors.error)
                            StatMiniBadge(title = "Blocked", value = "$spamBlockedCount", color = AppTheme.colors.tertiary)
                        }
                    }
                }
            }

            // Visual Call Distribution Chart
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Call Type Distribution",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AppTheme.colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (totalCalls > 0) {
                            val inPct = (incomingCount.toFloat() / totalCalls)
                            val outPct = (outgoingCount.toFloat() / totalCalls)
                            val missPct = (missedCount.toFloat() / totalCalls)

                            // Segmented Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                if (inPct > 0) Box(modifier = Modifier.weight(inPct.coerceAtLeast(0.01f)).fillMaxHeight().background(AppTheme.colors.secondary))
                                if (outPct > 0) Box(modifier = Modifier.weight(outPct.coerceAtLeast(0.01f)).fillMaxHeight().background(AppTheme.colors.primary))
                                if (missPct > 0) Box(modifier = Modifier.weight(missPct.coerceAtLeast(0.01f)).fillMaxHeight().background(AppTheme.colors.error))
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                LegendDot(label = "Incoming (${(inPct * 100).toInt()}%)", color = AppTheme.colors.secondary)
                                LegendDot(label = "Outgoing (${(outPct * 100).toInt()}%)", color = AppTheme.colors.primary)
                                LegendDot(label = "Missed (${(missPct * 100).toInt()}%)", color = AppTheme.colors.error)
                            }
                        } else {
                            Text(text = "No calls recorded yet.", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                        }
                    }
                }
            }

            // Most Frequent Contacts Section
            item {
                Text(
                    text = "Most Frequent Callers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AppTheme.colors.textPrimary
                )
            }

            items(topContacts) { (name, count, durSec) ->
                val durFormatted = "${durSec / 60}m ${durSec % 60}s"
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "$count calls • $durFormatted duration",
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = AppTheme.colors.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniBadge(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(text = title, fontSize = 10.sp, color = AppTheme.colors.textMuted)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, color = AppTheme.colors.textSecondary)
    }
}
