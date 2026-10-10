package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactEntity
import com.example.data.model.PhoneNumberItem
import com.example.data.model.SimAccount
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import com.example.util.PhoneNumberUtils

data class DialPadKey(
    val digit: Char,
    val letters: String
)

val DIAL_PAD_KEYS = listOf(
    DialPadKey('1', ""),
    DialPadKey('2', "ABC"),
    DialPadKey('3', "DEF"),
    DialPadKey('4', "GHI"),
    DialPadKey('5', "JKL"),
    DialPadKey('6', "MNO"),
    DialPadKey('7', "PQRS"),
    DialPadKey('8', "TUV"),
    DialPadKey('9', "WXYZ"),
    DialPadKey('*', ""),
    DialPadKey('0', "+"),
    DialPadKey('#', "")
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val query by viewModel.dialerQuery.collectAsState()
    val matchedContacts by viewModel.dialerMatchedContacts.collectAsState()
    val simAccounts by viewModel.simAccounts.collectAsState()
    val selectedSim by viewModel.selectedSimSlot.collectAsState()
    val speedDialItems by viewModel.speedDialItems.collectAsState()
    val isDefaultDialer by viewModel.isDefaultDialer.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val defaultDialerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkDefaultDialer(context)
    }

    LaunchedEffect(Unit) {
        viewModel.checkDefaultDialer(context)
    }

    var showNumberContextMenu by remember { mutableStateOf(false) }
    var showEmptyAreaPasteMenu by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.navigateBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
            .padding(bottom = 80.dp)
            .testTag("dialer_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header Bar with Back button and Quick Actions
        TopAppBar(
            title = {
                Text(
                    text = "Keypad & T9 Search",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppTheme.colors.textPrimary
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = { viewModel.navigateBack() },
                    modifier = Modifier.testTag("dialer_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppTheme.colors.primary
                    )
                }
            },
            actions = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearDialer() },
                        modifier = Modifier.testTag("dialer_clear_all_top")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear All",
                            tint = AppTheme.colors.textSecondary
                        )
                    }
                }
                IconButton(onClick = { viewModel.navigateTo(Screen.Emergency) }) {
                    Icon(
                        imageVector = Icons.Default.Emergency,
                        contentDescription = "Emergency SOS",
                        tint = AppTheme.colors.error
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background)
        )

        // Default Phone App Status Banner
        if (!isDefaultDialer) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("default_dialer_prompt_banner"),
                color = AppTheme.colors.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = null,
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Set NovaDial as Default Dialer",
                                fontSize = 12.sp,
                                color = AppTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Required to handle cellular calls directly in NovaDial",
                                fontSize = 11.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intent = com.example.telecom.DefaultDialerManager.createRequestDefaultDialerIntent(context)
                            try {
                                defaultDialerLauncher.launch(intent)
                            } catch (e: Exception) {
                                viewModel.requestDefaultDialer(context)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.primary),
                        modifier = Modifier.testTag("set_default_dialer_button")
                    ) {
                        Text("Set Default", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Predictive T9 Results or Recent Matches
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 600.dp)
        ) {
            val favMatches = matchedContacts.filter { it.isStarred }
            val otherMatches = matchedContacts.filter { !it.isStarred }

            if (matchedContacts.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (favMatches.isNotEmpty()) {
                        item {
                            Text(
                                text = "⭐ FAVORITES (${favMatches.size})",
                                fontSize = 11.sp,
                                color = AppTheme.colors.tertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp, top = 2.dp)
                            )
                        }
                        items(favMatches, key = { "fav_${it.id}" }) { contact ->
                            MatchedDialerContactCard(contact = contact, viewModel = viewModel)
                        }
                    }

                    if (otherMatches.isNotEmpty()) {
                        item {
                            Text(
                                text = "👥 OTHER CONTACTS (${otherMatches.size})",
                                fontSize = 11.sp,
                                color = AppTheme.colors.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp, top = 6.dp)
                            )
                        }
                        items(otherMatches, key = { it.id }) { contact ->
                            MatchedDialerContactCard(contact = contact, viewModel = viewModel)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Dial a number or search by name (T9)",
                            color = AppTheme.colors.textMuted,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "No saved contacts match '$query'",
                            color = AppTheme.colors.textMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Selected Number Display with Long-Press Options (Copy / Paste / Cut / Empty Area Paste)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.elevated.copy(alpha = 0.4f))
                .combinedClickable(
                    onClick = {
                        if (query.isEmpty()) {
                            showEmptyAreaPasteMenu = true
                        } else {
                            showNumberContextMenu = true
                        }
                    },
                    onLongClick = {
                        if (query.isNotEmpty()) {
                            showNumberContextMenu = true
                        } else {
                            showEmptyAreaPasteMenu = true
                        }
                    }
                )
                .testTag("keypad_number_display_area"),
            contentAlignment = Alignment.Center
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Tap or hold to paste number",
                    fontSize = 13.sp,
                    color = AppTheme.colors.textMuted.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = PhoneNumberUtils.formatForDisplay(query),
                    fontSize = if (query.length > 14) 20.sp else if (query.length > 10) 24.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .testTag("dialer_number_text")
                )
            }

            // Dropdown Menu when number is entered (Long Press on Number)
            DropdownMenu(
                expanded = showNumberContextMenu,
                onDismissRequest = { showNumberContextMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("📋 Copy Number") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(query))
                        viewModel.showStatus("Copied '$query' to clipboard")
                        showNumberContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
                )
                DropdownMenuItem(
                    text = { Text("📋 Paste Number") },
                    onClick = {
                        val clipText = clipboardManager.getText()?.text
                        if (!clipText.isNullOrBlank()) {
                            val digits = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                            if (digits.isNotEmpty()) {
                                viewModel.setDialerNumber(query + digits)
                                viewModel.showStatus("Pasted '$digits'")
                            } else {
                                viewModel.showStatus("Clipboard has no valid phone digits")
                            }
                        } else {
                            viewModel.showStatus("Clipboard is empty")
                        }
                        showNumberContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = "Paste") }
                )
                DropdownMenuItem(
                    text = { Text("✂️ Cut Number") },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(query))
                        viewModel.clearDialer()
                        viewModel.showStatus("Cut number to clipboard")
                        showNumberContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = "Cut") }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("👤 Save to Contacts") },
                    onClick = {
                        viewModel.navigateTo(Screen.EditContact())
                        showNumberContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = "Save Contact") }
                )
                DropdownMenuItem(
                    text = { Text("💬 Send SMS Message") },
                    onClick = {
                        val smsIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("sms:$query"))
                        try {
                            context.startActivity(smsIntent)
                        } catch (e: Exception) {
                            viewModel.showStatus("Cannot open SMS app")
                        }
                        showNumberContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Message, contentDescription = "SMS") }
                )
                DropdownMenuItem(
                    text = { Text("🧹 Clear Keypad") },
                    onClick = {
                        viewModel.clearDialer()
                        showNumberContextMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                )
            }

            // Dropdown Menu when keypad display area or empty background is long-pressed
            DropdownMenu(
                expanded = showEmptyAreaPasteMenu,
                onDismissRequest = { showEmptyAreaPasteMenu = false }
            ) {
                val clipText = clipboardManager.getText()?.text
                val digits = clipText?.filter { it.isDigit() || it == '+' || it == '*' || it == '#' } ?: ""

                DropdownMenuItem(
                    text = { Text(if (digits.isNotEmpty()) "📋 Paste '$digits'" else "📋 Paste from Clipboard") },
                    onClick = {
                        if (digits.isNotEmpty()) {
                            viewModel.setDialerNumber(digits)
                            viewModel.showStatus("Pasted '$digits'")
                        } else {
                            viewModel.showStatus("Clipboard contains no valid phone digits")
                        }
                        showEmptyAreaPasteMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = "Paste") }
                )
                if (digits.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("📞 Paste & Call Immediately") },
                        onClick = {
                            viewModel.setDialerNumber(digits)
                            viewModel.initiateCall(digits)
                            showEmptyAreaPasteMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Call, contentDescription = "Paste and Call") }
                    )
                }
            }

            // Top Quick Backspace
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.deleteDialerDigit() },
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.CenterEnd)
                        .testTag("dialer_top_backspace")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = AppTheme.colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // SIM Slot Selector Chip
        if (simAccounts.size > 1) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                simAccounts.forEach { sim ->
                    val isSelected = sim.slotIndex == selectedSim
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectedSimSlot.value = sim.slotIndex },
                        label = { Text("SIM ${sim.slotIndex}: ${sim.carrierName}", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppTheme.colors.primary.copy(alpha = 0.2f),
                            selectedLabelColor = AppTheme.colors.primary,
                            containerColor = AppTheme.colors.elevated,
                            labelColor = AppTheme.colors.textSecondary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3x4 Dialpad Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val rows = DIAL_PAD_KEYS.chunked(3)
            rows.forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowKeys.forEach { key ->
                        DialerPadButton(
                            key = key,
                            onClick = { viewModel.appendDialerDigit(key.digit) },
                            onLongClick = {
                                if (key.digit == '0') {
                                    viewModel.appendDialerDigit('+')
                                } else if (key.digit == '1') {
                                    val vmNumber = viewModel.getCarrierVoicemailNumber()
                                    viewModel.showStatus("⚡ Calling Voicemail ($vmNumber)")
                                    val vmIntent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$vmNumber"))
                                    try {
                                        context.startActivity(vmIntent)
                                    } catch (e: Exception) {
                                        viewModel.initiateCall(vmNumber, "Voicemail")
                                    }
                                } else if (key.digit in '2'..'9') {
                                    val speedItem = speedDialItems.find { it.keyDigit == key.digit }
                                    if (speedItem != null) {
                                        viewModel.showStatus("⚡ Calling Speed Dial ${key.digit}: ${speedItem.contactName}")
                                        viewModel.initiateCall(speedItem.phoneNumber, speedItem.contactName)
                                    } else {
                                        viewModel.showStatus("Speed Dial ${key.digit} unassigned. Configure in Settings > Speed Dial.")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom Action Bar with CONSTANT Erase/Backspace button area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .padding(horizontal = 36.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Constant Slot: Add Contact or Paste
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            viewModel.navigateTo(Screen.EditContact())
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.elevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Save Contact",
                            tint = AppTheme.colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                val digits = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                                viewModel.setDialerNumber(digits)
                            }
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.elevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = AppTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Central Constant Slot: Call Button
            Button(
                onClick = {
                    if (query.isNotBlank()) {
                        viewModel.initiateCall(query)
                    }
                },
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .testTag("dialer_call_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.secondary,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Right Constant Slot: Dedicated Constant Erase Button (Tap = Delete digit, Long press = Clear all)
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.elevated)
                            .combinedClickable(
                                onClick = { viewModel.deleteDialerDigit() },
                                onLongClick = {
                                    viewModel.clearDialer()
                                    viewModel.showStatus("Cleared")
                                }
                            )
                            .testTag("dialer_erase_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Erase",
                            tint = AppTheme.colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(AppTheme.colors.elevated.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Erase Disabled",
                            tint = AppTheme.colors.textMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerPadButton(
    key: DialPadKey,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("dialpad_key_${key.digit}"),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.elevated)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = key.digit.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
                lineHeight = 28.sp
            )
            if (key.letters.isNotEmpty()) {
                Text(
                    text = key.letters,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.primary,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatchedDialerContactCard(
    contact: ContactEntity,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .combinedClickable(
                onClick = {
                    val phone = contact.getPrimaryPhoneNumber()
                    if (phone.isNotBlank()) {
                        viewModel.initiateCall(phone, contact.displayName)
                    }
                },
                onLongClick = {
                    viewModel.navigateTo(Screen.ContactDetail(contact.id))
                }
            ),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (contact.isStarred) AppTheme.colors.tertiary.copy(alpha = 0.2f) else AppTheme.colors.elevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.getInitials(),
                    fontWeight = FontWeight.Bold,
                    color = if (contact.isStarred) AppTheme.colors.tertiary else AppTheme.colors.primary,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.displayName,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (contact.isStarred) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favorite",
                            tint = AppTheme.colors.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = PhoneNumberUtils.formatForDisplay(contact.getPrimaryPhoneNumber()),
                    color = AppTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            IconButton(
                onClick = {
                    viewModel.initiateCall(contact.getPrimaryPhoneNumber(), contact.displayName)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = AppTheme.colors.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
