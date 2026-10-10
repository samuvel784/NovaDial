package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BlockAction
import com.example.data.model.BlockedNumberEntity
import com.example.data.model.SpamCallerCategory
import com.example.data.model.SpamEntryEntity
import com.example.data.model.ThreatLevel
import com.example.ui.MainViewModel
import com.example.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamBlocklistManagerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val spamEntries by viewModel.allSpamEntries.collectAsState()
    val spamCount by viewModel.spamDatabaseCount.collectAsState()
    val highRiskCount by viewModel.highRiskSpamCount.collectAsState()
    val interceptedCalls by viewModel.interceptedSpamCalls.collectAsState()
    val blockedList by viewModel.repository.blockedNumbers.collectAsState(initial = emptyList())
    val spamSettings by viewModel.spamProtectionSettings.collectAsState()

    val testVerdict by viewModel.testVerdict.collectAsState()
    val isTesting by viewModel.isTestingNumber.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<SpamCallerCategory?>(null) }

    var showAddSpamDialog by remember { mutableStateOf(false) }
    var showAddBlockNumberDialog by remember { mutableStateOf(false) }
    var testInputNumber by remember { mutableStateOf("") }

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Spam Shield & Blocklist",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Real-Time Call Screening & Local Database",
                            fontSize = 11.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("spam_manager_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (selectedTabIndex == 1) {
                        showAddBlockNumberDialog = true
                    } else {
                        showAddSpamDialog = true
                    }
                },
                containerColor = AppTheme.colors.primary,
                contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        if (selectedTabIndex == 1) "Block Number" else "Report / Add Spam",
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .testTag("add_spam_or_block_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("spam_blocklist_screen"),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Real-Time Status Hero Card
            item {
                RealtimeServiceHeroCard(
                    isScreeningActive = true,
                    autoBlock = spamSettings.autoBlockHighRiskSpam,
                    onToggleAutoBlock = {
                        viewModel.repository.communitySpamManager.updateSettings(
                            spamSettings.copy(autoBlockHighRiskSpam = it)
                        )
                    }
                )
            }

            // 2. Metrics Quick Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricChip(
                        label = "KNOWN SPAM DB",
                        value = "${spamEntries.size} entries",
                        icon = Icons.Default.Shield,
                        color = AppTheme.colors.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        label = "USER BLOCKED",
                        value = "${blockedList.size} numbers",
                        icon = Icons.Default.Block,
                        color = AppTheme.colors.error,
                        modifier = Modifier.weight(1f)
                    )
                    MetricChip(
                        label = "INTERCEPTED",
                        value = "${interceptedCalls.size} screened",
                        icon = Icons.Default.CallMissed,
                        color = AppTheme.colors.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Segmented Navigation Tabs
            item {
                val tabs = listOf(
                    "Database (${spamEntries.size})",
                    "My Blocklist (${blockedList.size})",
                    "Check a number",
                    "Log (${interceptedCalls.size})"
                )
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = AppTheme.colors.background,
                    contentColor = AppTheme.colors.primary,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // 4. Tab Contents
            when (selectedTabIndex) {
                0 -> {
                    // TAB 0: Database of Known Spam Numbers
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by number, scam type, or entity...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textMuted) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spam_search_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppTheme.colors.primary,
                                unfocusedBorderColor = AppTheme.colors.border
                            )
                        )
                    }

                    // Category filters
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedCategoryFilter == null,
                                onClick = { selectedCategoryFilter = null },
                                label = { Text("All", fontSize = 11.sp) }
                            )
                            SpamCallerCategory.entries.take(4).forEach { cat ->
                                FilterChip(
                                    selected = selectedCategoryFilter == cat,
                                    onClick = {
                                        selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                                    },
                                    label = { Text("${cat.icon} ${cat.label}", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    val filteredSpam = spamEntries.filter { item ->
                        val matchesSearch = searchQuery.isBlank() ||
                                item.phoneNumber.contains(searchQuery, ignoreCase = true) ||
                                item.callerName.contains(searchQuery, ignoreCase = true) ||
                                item.threatDetails.contains(searchQuery, ignoreCase = true)
                        val matchesCat = selectedCategoryFilter == null || item.category == selectedCategoryFilter
                        matchesSearch && matchesCat
                    }

                    if (filteredSpam.isEmpty()) {
                        item {
                            EmptyStatePlaceholder(
                                message = if (searchQuery.isNotBlank()) "No spam numbers match '$searchQuery'" else "Spam database is empty."
                            )
                        }
                    } else {
                        items(filteredSpam, key = { it.id }) { spamItem ->
                            SpamEntryCard(
                                item = spamItem,
                                onDelete = { viewModel.removeSpamEntry(spamItem.id) }
                            )
                        }
                    }
                }

                1 -> {
                    // TAB 1: User's Local Blocklist
                    if (blockedList.isEmpty()) {
                        item {
                            EmptyStatePlaceholder(
                                message = "No phone numbers in your personal blocklist.\nTap '+ Block Number' below to add one."
                            )
                        }
                    } else {
                        items(blockedList, key = { it.number }) { blockedItem ->
                            UserBlockedItemCard(
                                item = blockedItem,
                                onUnblock = { viewModel.unblockNumber(blockedItem.number) }
                            )
                        }
                    }
                }

                2 -> {
                    // TAB 2: Check how your own rules treat a number
                    item {
                        LiveScreeningTesterSection(
                            inputNumber = testInputNumber,
                            onNumberChange = { testInputNumber = it },
                            isTesting = isTesting,
                            verdict = testVerdict,
                            onTest = { viewModel.testScreenNumber(testInputNumber) },
                            onSelectPreset = { preset ->
                                testInputNumber = preset
                                viewModel.testScreenNumber(preset)
                            },
                            onClearVerdict = { viewModel.clearTestVerdict() }
                        )
                    }
                }

                3 -> {
                    // TAB 3: Intercepted Call Log
                    if (interceptedCalls.isEmpty()) {
                        item {
                            EmptyStatePlaceholder(
                                message = "No incoming spam calls intercepted yet.\nCalls blocked by CallScreeningService will be logged here."
                            )
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${interceptedCalls.size} screened events",
                                    fontSize = 12.sp,
                                    color = AppTheme.colors.textSecondary
                                )
                                TextButton(onClick = { viewModel.clearInterceptedCalls() }) {
                                    Text("Clear Log", color = AppTheme.colors.error, fontSize = 12.sp)
                                }
                            }
                        }
                        items(interceptedCalls, key = { it.id }) { call ->
                            InterceptedCallCard(call = call)
                        }
                    }
                }
            }
        }
    }

    // Dialog: Add / Report Spam to Local Database
    if (showAddSpamDialog) {
        AddSpamEntryDialog(
            onDismiss = { showAddSpamDialog = false },
            onAdd = { num, name, cat, score, action, reason ->
                viewModel.addCustomSpamEntry(num, name, cat, score, action, reason)
                showAddSpamDialog = false
            }
        )
    }

    // Dialog: Add Number to Personal Blocklist
    if (showAddBlockNumberDialog) {
        var newBlockNumber by remember { mutableStateOf("") }
        var newBlockReason by remember { mutableStateOf("Blocked by User") }

        AlertDialog(
            onDismissRequest = { showAddBlockNumberDialog = false },
            title = { Text("Add to Personal Blocklist", color = AppTheme.colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the phone number you wish to reject automatically:",
                        fontSize = 12.sp,
                        color = AppTheme.colors.textSecondary
                    )
                    OutlinedTextField(
                        value = newBlockNumber,
                        onValueChange = { newBlockNumber = it },
                        placeholder = { Text("+1 (555) 000-0000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.primary,
                            unfocusedBorderColor = AppTheme.colors.border
                        )
                    )
                    OutlinedTextField(
                        value = newBlockReason,
                        onValueChange = { newBlockReason = it },
                        label = { Text("Reason (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newBlockNumber.isNotBlank()) {
                            viewModel.blockNumber(newBlockNumber.trim(), newBlockReason.trim())
                            showAddBlockNumberDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.error)
                ) {
                    Text("Block Number", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBlockNumberDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.card
        )
    }
}

@Composable
fun RealtimeServiceHeroCard(
    isScreeningActive: Boolean,
    autoBlock: Boolean,
    onToggleAutoBlock: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.primary.copy(alpha = 0.5f))
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(AppTheme.colors.secondary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = AppTheme.colors.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "REAL-TIME PROTECTION",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AppTheme.colors.secondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.secondary)
                        )
                    }
                    Text(
                        text = "CallScreeningService Active • Sub-5ms Screening",
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            HorizontalDivider(color = AppTheme.colors.border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Auto-Block High-Risk Calls",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        "Automatically reject calls with risk score ≥ 75%",
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }

                Switch(
                    checked = autoBlock,
                    onCheckedChange = onToggleAutoBlock,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppTheme.colors.primary,
                        checkedTrackColor = AppTheme.colors.primary.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
fun MetricChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.elevated),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textMuted
                )
            }
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
        }
    }
}

@Composable
fun SpamEntryCard(
    item: SpamEntryEntity,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (item.riskScore >= 90) AppTheme.colors.error.copy(alpha = 0.5f)
                else AppTheme.colors.border
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category & Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(item.category.icon, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = item.callerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AppTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.phoneNumber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.primary
                        )
                    }
                }

                // Risk Score Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.riskScore >= 90) AppTheme.colors.error.copy(alpha = 0.18f) else AppTheme.colors.primary.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "Score: ${item.riskScore}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.riskScore >= 90) AppTheme.colors.error else AppTheme.colors.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = item.threatDetails,
                fontSize = 12.sp,
                color = AppTheme.colors.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (item.recommendedAction == BlockAction.AUTO_BLOCK) Icons.Default.Block else Icons.Default.VolumeMute,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = AppTheme.colors.textMuted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${item.recommendedAction.label} • ${item.source}",
                        fontSize = 10.sp,
                        color = AppTheme.colors.textMuted
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = AppTheme.colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UserBlockedItemCard(
    item: BlockedNumberEntity,
    onUnblock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.error.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = AppTheme.colors.error, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.number,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "${item.reason} • ${item.blockCount} calls rejected",
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            Button(
                onClick = onUnblock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.elevated,
                    contentColor = AppTheme.colors.primary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Unblock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LiveScreeningTesterSection(
    inputNumber: String,
    onNumberChange: (String) -> Unit,
    isTesting: Boolean,
    verdict: com.example.data.model.SpamDetectionVerdict?,
    onTest: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onClearVerdict: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.primary.copy(alpha = 0.4f))
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Check a number",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "See how your blocklist and spam rules treat a number. No call is made.",
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            OutlinedTextField(
                value = inputNumber,
                onValueChange = onNumberChange,
                placeholder = { Text("Enter a phone number") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tester_number_input"),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (inputNumber.isNotEmpty()) {
                        IconButton(onClick = {
                            onNumberChange("")
                            onClearVerdict()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            Button(
                onClick = onTest,
                enabled = inputNumber.isNotBlank() && !isTesting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("run_test_screening_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.primary,
                    contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check this number", fontWeight = FontWeight.Bold)
                }
            }

            // Results Card
            AnimatedVisibility(
                visible = verdict != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                verdict?.let { res ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_screening_result_card"),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.elevated),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (res.riskScore >= 75) AppTheme.colors.error else AppTheme.colors.secondary
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (res.riskScore >= 75) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (res.riskScore >= 75) AppTheme.colors.error else AppTheme.colors.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = res.threatLevel.label.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (res.riskScore >= 75) AppTheme.colors.error else AppTheme.colors.secondary
                                    )
                                }

                                Text(
                                    text = "Scan: ${res.scanLatencyMs}ms",
                                    fontSize = 11.sp,
                                    color = AppTheme.colors.textMuted
                                )
                            }

                            HorizontalDivider(color = AppTheme.colors.border)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = res.callerName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "${res.category.icon} ${res.category.label} • Source: ${res.source}",
                                        fontSize = 11.sp,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (res.riskScore >= 75) AppTheme.colors.error.copy(alpha = 0.2f) else AppTheme.colors.secondary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${res.riskScore}% RISK",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (res.riskScore >= 75) AppTheme.colors.error else AppTheme.colors.secondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = res.reason,
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary
                            )

                            // Action Taken Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppTheme.colors.background
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = AppTheme.colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Action: ${res.action.label} (${res.action.description})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InterceptedCallCard(call: com.example.data.model.InterceptedSpamCallEntity) {
    val dateStr = remember(call.timestamp) {
        SimpleDateFormat("MMM dd • hh:mm a", Locale.getDefault()).format(Date(call.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.error.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = null,
                        tint = AppTheme.colors.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = call.callerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AppTheme.colors.textPrimary
                    )
                    Text(
                        text = "${call.phoneNumber} • ${call.actionTaken}",
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${call.riskScore}% risk",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.error
                )
                Text(
                    text = dateStr,
                    fontSize = 10.sp,
                    color = AppTheme.colors.textMuted
                )
            }
        }
    }
}

@Composable
fun AddSpamEntryDialog(
    onDismiss: () -> Unit,
    onAdd: (number: String, name: String, category: SpamCallerCategory, riskScore: Int, action: BlockAction, reason: String) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(SpamCallerCategory.ROBOCALL) }
    var selectedAction by remember { mutableStateOf(BlockAction.AUTO_BLOCK) }
    var riskScoreText by remember { mutableStateOf("95") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add / Report Known Spam Number", color = AppTheme.colors.textPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+1 (800) 000-0000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Caller Entity / Scam Title") },
                    placeholder = { Text("e.g. IRS Tax Scam") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Spam Category:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpamCallerCategory.entries.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text("${cat.icon} ${cat.label}", fontSize = 10.sp) }
                        )
                    }
                }

                Text("Action:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BlockAction.entries.forEach { act ->
                        FilterChip(
                            selected = selectedAction == act,
                            onClick = { selectedAction = act },
                            label = { Text(act.label, fontSize = 10.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Threat Details / Scam Tactic") },
                    placeholder = { Text("e.g. Automated robocall claiming IRS debt") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (number.isNotBlank()) {
                        val score = riskScoreText.toIntOrNull() ?: 90
                        onAdd(number.trim(), name.trim(), selectedCategory, score, selectedAction, reason.trim())
                    }
                },
                enabled = number.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary)
            ) {
                Text("Add to Database", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppTheme.colors.textSecondary)
            }
        },
        containerColor = AppTheme.colors.card
    )
}

@Composable
fun EmptyStatePlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = AppTheme.colors.textMuted,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
