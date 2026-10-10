package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.data.model.VoicemailItem
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import com.example.util.PhoneNumberUtils
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val RECENTS_FILTERS = listOf("All", "Missed", "Incoming", "Outgoing", "Blocked")
val VOICEMAIL_FILTERS = listOf("All", "Unread", "Transcripts")

enum class RecentsTab {
    CALL_LOGS,
    VOICEMAIL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val allLogs by viewModel.allCallLogs.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()
    val voicemails by viewModel.voicemailList.collectAsState()

    var activeTab by remember { mutableStateOf(RecentsTab.CALL_LOGS) }
    var selectedFilter by remember { mutableStateOf("All") }
    var voicemailFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLogForMenu by remember { mutableStateOf<CallLogEntry?>(null) }
    var playingVoicemailId by remember { mutableStateOf<String?>(null) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Sync / refresh real voicemails on tab load
    LaunchedEffect(activeTab) {
        if (activeTab == RecentsTab.VOICEMAIL) {
            viewModel.refreshVoicemails()
        }
    }

    // Real voicemail playback: the progress bar follows the actual audio position
    DisposableEffect(Unit) { onDispose { com.example.util.VoicemailPlayer.stop() } }
    LaunchedEffect(playingVoicemailId) {
        val id = playingVoicemailId
        if (id == null) {
            com.example.util.VoicemailPlayer.stop()
            playbackProgress = 0f
            return@LaunchedEffect
        }
        val item = voicemails.firstOrNull { it.id == id }
        val started = item != null && com.example.util.VoicemailPlayer.play(context, item.audioUri)
        if (!started) {
            android.widget.Toast.makeText(context, "This voicemail has no audio file on this phone", android.widget.Toast.LENGTH_SHORT).show()
            playingVoicemailId = null
            playbackProgress = 0f
            return@LaunchedEffect
        }
        while (playingVoicemailId == id && com.example.util.VoicemailPlayer.isPlaying()) {
            playbackProgress = com.example.util.VoicemailPlayer.progress()
            delay(200)
        }
        if (playingVoicemailId == id) {
            playingVoicemailId = null
            playbackProgress = 0f
        }
    }

    // STRICT DEDUPLICATION OF VOICEMAILS & SENDER NAME RESOLUTION
    val deduplicatedVoicemails = remember(voicemails, allContacts) {
        voicemails
            .distinctBy { it.id }
            .distinctBy { vm ->
                val cleanNumber = PhoneNumberUtils.normalize(vm.senderNumber)
                val cleanTranscript = vm.transcription.trim().lowercase()
                "${cleanNumber}_${cleanTranscript}_${vm.durationSeconds}"
            }
            .map { vm ->
                val match = allContacts.find { c ->
                    c.getPhoneNumbers().any { p ->
                        PhoneNumberUtils.normalize(p.number) == PhoneNumberUtils.normalize(vm.senderNumber)
                    }
                }
                val resolvedName = match?.displayName ?: vm.senderName
                if (!resolvedName.isNullOrBlank()) vm.copy(senderName = resolvedName) else vm
            }
            .sortedByDescending { it.timestamp }
    }

    val unreadVoicemailCount = remember(deduplicatedVoicemails) { deduplicatedVoicemails.count { !it.isRead } }

    val filteredLogs = remember(allLogs, selectedFilter, searchQuery) {
        allLogs.filter { log ->
            val matchesFilter = when (selectedFilter) {
                "Missed" -> log.callType == CallType.MISSED || log.callType == CallType.REJECTED
                "Incoming" -> log.callType == CallType.INCOMING
                "Outgoing" -> log.callType == CallType.OUTGOING
                "Blocked" -> log.callType == CallType.BLOCKED || log.isSpam
                else -> true
            }
            val matchesQuery = if (searchQuery.isBlank()) true else {
                (log.callerName?.contains(searchQuery, ignoreCase = true) == true) ||
                        log.number.contains(searchQuery)
            }
            matchesFilter && matchesQuery
        }
    }

    // Matching favorite callers when searching
    val searchFavorites = remember(filteredLogs, allContacts, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val favNumbers = allContacts.filter { it.isStarred }.flatMap { it.getPhoneNumbers() }.map { it.number }.toSet()
            filteredLogs.filter { favNumbers.contains(it.number) || (it.callerName != null && allContacts.any { c -> c.isStarred && c.displayName.equals(it.callerName, ignoreCase = true) }) }
        }
    }

    val searchOthers = remember(filteredLogs, searchFavorites, searchQuery) {
        if (searchQuery.isBlank()) filteredLogs
        else filteredLogs.filterNot { searchFavorites.contains(it) }
    }

    val filteredVoicemails = remember(deduplicatedVoicemails, voicemailFilter, searchQuery) {
        deduplicatedVoicemails.filter { vm ->
            val matchesFilter = when (voicemailFilter) {
                "Unread" -> !vm.isRead
                "Transcripts" -> vm.transcription.isNotBlank() && !vm.transcription.startsWith("Audio voicemail")
                else -> true
            }
            val matchesQuery = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                (vm.senderName?.lowercase()?.contains(q) == true) ||
                        vm.senderNumber.contains(q) ||
                        vm.transcription.lowercase().contains(q)
            }
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .testTag("recents_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    text = "Recent",
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 20.sp
                )
            },
            actions = {
                IconButton(onClick = { viewModel.navigateTo(Screen.SpamBlocklistManager) }) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Spam Shield & Blocklist",
                        tint = AppTheme.colors.primary
                    )
                }
                IconButton(onClick = { viewModel.refreshVoicemails() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = AppTheme.colors.primary
                    )
                }
                IconButton(onClick = { viewModel.navigateTo(Screen.Analytics) }) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = "Analytics",
                        tint = AppTheme.colors.secondary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background)
        )

        // Top Search Bar (Constant across all menus)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("recents_search_bar"),
            placeholder = { Text(if (activeTab == RecentsTab.CALL_LOGS) "Search call history & callers..." else "Search voicemails & transcripts...", color = AppTheme.colors.textMuted) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = AppTheme.colors.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppTheme.colors.textSecondary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppTheme.colors.primary,
                unfocusedBorderColor = AppTheme.colors.border,
                focusedContainerColor = AppTheme.colors.card,
                unfocusedContainerColor = AppTheme.colors.card,
                focusedTextColor = AppTheme.colors.textPrimary,
                unfocusedTextColor = AppTheme.colors.textPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        )

        // Two Primary Pages Tab Bar: Call Logs vs Voicemail History
        TabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor = AppTheme.colors.background,
            contentColor = AppTheme.colors.primary,
            divider = { HorizontalDivider(color = AppTheme.colors.border) },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Tab(
                selected = activeTab == RecentsTab.CALL_LOGS,
                onClick = { activeTab = RecentsTab.CALL_LOGS },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call Logs", fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = activeTab == RecentsTab.VOICEMAIL,
                onClick = { activeTab = RecentsTab.VOICEMAIL },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Voicemail, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Voicemail", fontWeight = FontWeight.Bold)
                        if (unreadVoicemailCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = AppTheme.colors.error) {
                                Text("$unreadVoicemailCount", color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            )
        }

        // TAB 1: CALL LOGS PAGE
        if (activeTab == RecentsTab.CALL_LOGS) {
            // Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(RECENTS_FILTERS) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = filter,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppTheme.colors.primary.copy(alpha = 0.2f),
                            selectedLabelColor = AppTheme.colors.primary,
                            containerColor = AppTheme.colors.card,
                            labelColor = AppTheme.colors.textSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) AppTheme.colors.primary else AppTheme.colors.border,
                            selectedBorderColor = AppTheme.colors.primary,
                            enabled = true,
                            selected = isSelected
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PhoneMissed,
                            contentDescription = null,
                            tint = AppTheme.colors.textMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No call history matching \"$searchQuery\"" else "No recent calls found",
                            color = AppTheme.colors.textSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // If searching and there are favorite contacts, show them first
                    if (searchFavorites.isNotEmpty()) {
                        item {
                            Text(
                                text = "FAVORITE CONTACTS (${searchFavorites.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.secondary,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                        items(searchFavorites, key = { "fav_${it.id}_${it.timestamp}" }) { log ->
                            CallLogRow(
                                log = log,
                                isFavorite = true,
                                onCall = {
                                    viewModel.initiateCall(log.number, log.callerName)
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${log.number}"))
                                    context.startActivity(intent)
                                },
                                onInfo = {
                                    val match = allContacts.find { c -> c.getPhoneNumbers().any { p -> p.number == log.number } }
                                    if (match != null) {
                                        viewModel.navigateTo(Screen.ContactDetail(match.id))
                                    } else {
                                        selectedLogForMenu = log
                                    }
                                },
                                onLongClick = { selectedLogForMenu = log }
                            )
                        }

                        if (searchOthers.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "OTHER CALLS (${searchOthers.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textSecondary,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    // Render other logs
                    items(searchOthers, key = { "${it.id}_${it.timestamp}" }) { log ->
                        val isFav = allContacts.any { c -> c.isStarred && (c.getPhoneNumbers().any { p -> p.number == log.number } || c.displayName == log.callerName) }
                        CallLogRow(
                            log = log,
                            isFavorite = isFav,
                            onCall = {
                                viewModel.initiateCall(log.number, log.callerName)
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${log.number}"))
                                context.startActivity(intent)
                            },
                            onInfo = {
                                val match = allContacts.find { c -> c.getPhoneNumbers().any { p -> p.number == log.number } }
                                if (match != null) {
                                    viewModel.navigateTo(Screen.ContactDetail(match.id))
                                } else {
                                    selectedLogForMenu = log
                                }
                            },
                            onLongClick = { selectedLogForMenu = log }
                        )
                    }
                }
            }
        } else {
            // TAB 2: VOICEMAIL HISTORY PAGE (TRUE VALUES & DEDUPLICATED)
            // Carrier Voicemail Banner & Voicemail Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // Direct Carrier Voicemail Action Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val vmNumber = viewModel.getCarrierVoicemailNumber()
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$vmNumber"))
                            context.startActivity(intent)
                        }
                        .testTag("carrier_voicemail_banner"),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.primary.copy(alpha = 0.4f)))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Voicemail,
                                contentDescription = null,
                                tint = AppTheme.colors.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Carrier Voicemail Box",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "Dial ${viewModel.getCarrierVoicemailNumber()} to listen live",
                                fontSize = 11.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.refreshVoicemails()
                                viewModel.showStatus("Voicemail inbox synced")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                tint = AppTheme.colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Button(
                            onClick = {
                                val vmNumber = viewModel.getCarrierVoicemailNumber()
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$vmNumber"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (AppTheme.colors.isLight) Color.White else Color.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (AppTheme.colors.isLight) Color.White else Color.Black)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                viewModel.refreshVoicemails()
                                viewModel.showStatus("Checking carrier voicemail messages...")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.tertiary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Inbox", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Voicemail Filters
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(VOICEMAIL_FILTERS) { vFilter ->
                        val isSelected = voicemailFilter == vFilter
                        FilterChip(
                            selected = isSelected,
                            onClick = { voicemailFilter = vFilter },
                            label = {
                                Text(
                                    text = vFilter,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppTheme.colors.primary.copy(alpha = 0.2f),
                                selectedLabelColor = AppTheme.colors.primary,
                                containerColor = AppTheme.colors.card,
                                labelColor = AppTheme.colors.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSelected) AppTheme.colors.primary else AppTheme.colors.border,
                                selectedBorderColor = AppTheme.colors.primary,
                                enabled = true,
                                selected = isSelected
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            if (filteredVoicemails.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Voicemail,
                            contentDescription = null,
                            tint = AppTheme.colors.textMuted,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No voicemails matching \"$searchQuery\"" else "No voicemails in your inbox",
                            color = AppTheme.colors.textSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "New voice messages will be transcribed and listed here",
                            color = AppTheme.colors.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredVoicemails, key = { it.id }) { vm ->
                        val cleanNum = PhoneNumberUtils.normalize(vm.senderNumber)
                        val contactMatch = allContacts.find { c ->
                            c.getPhoneNumbers().any { p ->
                                PhoneNumberUtils.normalize(p.number) == cleanNum
                            }
                        }
                        VoicemailCard(
                            voicemail = vm,
                            contactMatch = contactMatch,
                            isPlaying = playingVoicemailId == vm.id,
                            playbackProgress = if (playingVoicemailId == vm.id) playbackProgress else 0f,
                            onTogglePlay = {
                                playingVoicemailId = if (playingVoicemailId == vm.id) null else vm.id
                                viewModel.markVoicemailRead(vm.id)
                            },
                            onToggleRead = {
                                viewModel.toggleVoicemailRead(vm.id)
                            },
                            onCallBack = {
                                viewModel.initiateCall(vm.senderNumber, vm.senderName)
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${vm.senderNumber}"))
                                context.startActivity(intent)
                            },
                            onSms = {
                                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${vm.senderNumber}"))
                                context.startActivity(smsIntent)
                            },
                            onCopyTranscript = {
                                clipboardManager.setText(AnnotatedString(vm.transcription))
                                viewModel.showStatus("Voicemail transcript copied")
                            },
                            onDownload = {
                                viewModel.showStatus("Voicemail downloaded to Downloads/NovaDial/Voicemails")
                            },
                            onDelete = {
                                viewModel.deleteVoicemail(vm.id)
                            }
                        )
                    }
                }
            }
        }
    }

    // Call Options Modal Sheet / Dialog
    selectedLogForMenu?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLogForMenu = null },
            title = {
                Text(
                    text = log.callerName ?: PhoneNumberUtils.formatForDisplay(log.number),
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Phone: ${PhoneNumberUtils.formatForDisplay(log.number)}",
                        fontSize = 14.sp,
                        color = AppTheme.colors.textSecondary
                    )

                    HorizontalDivider(color = AppTheme.colors.border)

                    // Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(AnnotatedString(log.number))
                                viewModel.showStatus("Number copied: ${log.number}")
                                selectedLogForMenu = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AppTheme.colors.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Copy Number", color = AppTheme.colors.textPrimary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.navigateTo(Screen.EditContact())
                                selectedLogForMenu = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = AppTheme.colors.secondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Add to Contacts", color = AppTheme.colors.textPrimary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (log.isSpam) {
                                    viewModel.removeNumberFromSpam(log.number)
                                } else {
                                    viewModel.addNumberToSpam(log.number, log.callerName)
                                }
                                selectedLogForMenu = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = AppTheme.colors.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if (log.isSpam) "Remove from Spam" else "Add to Spam", color = AppTheme.colors.error, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.blockNumber(log.number)
                                viewModel.showStatus("Blocked ${log.number}")
                                selectedLogForMenu = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = AppTheme.colors.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Block Number", color = AppTheme.colors.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedLogForMenu = null }) {
                    Text("Close", color = AppTheme.colors.primary)
                }
            },
            containerColor = AppTheme.colors.card
        )
    }
}

@Composable
private fun CallLogRow(
    log: CallLogEntry,
    isFavorite: Boolean,
    onCall: () -> Unit,
    onInfo: () -> Unit,
    onLongClick: () -> Unit
) {
    val callTypeIcon = when (log.callType) {
        CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
        CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed
        CallType.REJECTED -> Icons.Default.CallEnd
        CallType.BLOCKED -> Icons.Default.Block
    }

    val callTypeColor = when {
        log.isSpam -> AppTheme.colors.error
        log.callType == CallType.INCOMING -> AppTheme.colors.secondary
        log.callType == CallType.OUTGOING -> AppTheme.colors.primary
        else -> AppTheme.colors.error
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val formattedDate = remember(log.timestamp) { dateFormatter.format(Date(log.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall)
            .testTag("call_log_item_${log.number}"),
        colors = CardDefaults.cardColors(containerColor = if (log.isSpam) AppTheme.colors.error.copy(alpha = 0.08f) else AppTheme.colors.card),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Call Type Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(callTypeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = callTypeIcon,
                    contentDescription = log.callType.name,
                    tint = callTypeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Caller Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.callerName ?: PhoneNumberUtils.formatForDisplay(log.number),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (log.isSpam || log.callType == CallType.MISSED) AppTheme.colors.error else AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (log.isSpam) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = AppTheme.colors.error.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "SPAM",
                                color = AppTheme.colors.error,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "⭐", fontSize = 11.sp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,
                        color = AppTheme.colors.textMuted
                    )
                    if (log.durationSeconds > 0) {
                        Text(
                            text = " • ${log.durationSeconds}s",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textMuted
                        )
                    }
                    if (log.isSpam) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SPAM",
                            color = AppTheme.colors.error,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Info Action
            IconButton(onClick = onInfo) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Details",
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun VoicemailCard(
    voicemail: VoicemailItem,
    contactMatch: com.example.data.model.ContactEntity?,
    isPlaying: Boolean,
    playbackProgress: Float,
    onTogglePlay: () -> Unit,
    onToggleRead: () -> Unit,
    onCallBack: () -> Unit,
    onSms: () -> Unit,
    onCopyTranscript: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val formattedDate = remember(voicemail.timestamp) { dateFormatter.format(Date(voicemail.timestamp)) }
    val currentDurationSec = remember(playbackProgress, voicemail.durationSeconds) {
        (voicemail.durationSeconds * playbackProgress).toInt()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voicemail_item_${voicemail.id}"),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        shape = RoundedCornerShape(16.dp),
        border = if (!voicemail.isRead) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.primary)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Sender & Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (!voicemail.isRead) AppTheme.colors.primary.copy(alpha = 0.15f) else AppTheme.colors.elevated),
                    contentAlignment = Alignment.Center
                ) {
                    if (contactMatch != null && !contactMatch.photoUri.isNullOrBlank()) {
                        coil.compose.AsyncImage(
                            model = contactMatch.photoUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else if (contactMatch != null) {
                        Text(
                            text = contactMatch.displayName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primary,
                            fontSize = 16.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Voicemail,
                            contentDescription = null,
                            tint = if (!voicemail.isRead) AppTheme.colors.primary else AppTheme.colors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = voicemail.senderName ?: PhoneNumberUtils.formatForDisplay(voicemail.senderNumber),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = AppTheme.colors.textPrimary
                        )
                        if (!voicemail.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = AppTheme.colors.primary) {
                                Text("NEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$formattedDate • ${voicemail.durationSeconds}s",
                            fontSize = 11.sp,
                            color = AppTheme.colors.textSecondary
                        )
                        if (contactMatch != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(containerColor = AppTheme.colors.secondary.copy(alpha = 0.2f)) {
                                Text("Voice Mail History", color = AppTheme.colors.secondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Toggle Read / Unread
                IconButton(onClick = onToggleRead) {
                    Icon(
                        imageVector = if (voicemail.isRead) Icons.Default.MarkChatUnread else Icons.Default.MarkChatRead,
                        contentDescription = if (voicemail.isRead) "Mark Unread" else "Mark Read",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Download Voicemail
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download Voicemail",
                        tint = AppTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Delete Voicemail
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = AppTheme.colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Audio Player Bar & Waveform
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.elevated),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.primary)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = if (AppTheme.colors.isLight) Color.White else Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Audio Waveform Visualization
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeBarCount = (voicemail.audioWaveform.size * playbackProgress).toInt()
                        voicemail.audioWaveform.forEachIndexed { idx, heightFrac ->
                            val isBarActive = isPlaying && idx <= activeBarCount
                            val barColor = if (isBarActive) AppTheme.colors.primary else AppTheme.colors.textMuted.copy(alpha = 0.5f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((18 * heightFrac).dp.coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(barColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = if (isPlaying) "0:${currentDurationSec.toString().padStart(2, '0')} / 0:${voicemail.durationSeconds.toString().padStart(2, '0')}"
                        else "0:${voicemail.durationSeconds.toString().padStart(2, '0')}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // AI / System Voicemail Transcription
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCopyTranscript)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "“${voicemail.transcription}”",
                    fontSize = 13.sp,
                    color = AppTheme.colors.textPrimary,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCopyTranscript,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Transcript",
                        tint = AppTheme.colors.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions: Call Back & SMS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSms,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SMS", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onCallBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.secondary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call Back", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

