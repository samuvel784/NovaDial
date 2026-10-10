package com.example.ui.screens

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactEntity
import com.example.data.model.ContactSource
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportedContactsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val allContacts by viewModel.allContacts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val importedContacts = remember(allContacts) {
        allContacts.filter { it.source == ContactSource.DEVICE || it.id.startsWith("dev_") }
    }

    val displayList = remember(allContacts, importedContacts) {
        if (importedContacts.isNotEmpty()) importedContacts else allContacts
    }

    val searchFavorites = remember(displayList, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else displayList.filter {
            it.isStarred && (it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.getPhoneNumbers().any { p -> p.number.contains(searchQuery) })
        }
    }

    val searchOthers = remember(displayList, searchQuery) {
        if (searchQuery.isBlank()) displayList
        else displayList.filter {
            !it.isStarred && (it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.getPhoneNumbers().any { p -> p.number.contains(searchQuery) })
        }
    }

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
                            text = "Imported Contacts",
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${displayList.size} Native Contacts on Device",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                viewModel.repository.syncWithDeviceContacts()
                                viewModel.refreshDuplicates()
                                viewModel.showStatus("Device contacts refreshed.")
                                isSyncing = false
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AppTheme.colors.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Re-sync", tint = AppTheme.colors.primary)
                        }
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
                .testTag("imported_contacts_screen")
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("imported_search_bar"),
                placeholder = { Text("Search imported contacts...", color = AppTheme.colors.textMuted) },
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

            // Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContactPhone, contentDescription = null, tint = AppTheme.colors.primary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Phone ContentResolver Synced",
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Modifications sync bidirectionally with Android ContactsContract",
                            color = AppTheme.colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PermContactCalendar, contentDescription = null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No contacts imported from device.", color = AppTheme.colors.textSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.repository.syncWithDeviceContacts()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primary,
                                contentColor = if (AppTheme.colors.isLight) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                            )
                        ) {
                            Text("Import Now")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    // If searching and there are matching favorites, show favorites first
                    if (searchFavorites.isNotEmpty()) {
                        item {
                            Text(
                                text = "⭐ FAVORITES (${searchFavorites.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.tertiary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppTheme.colors.surface)
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        items(searchFavorites, key = { "fav_${it.id}" }) { contact ->
                            ImportedContactItem(
                                contact = contact,
                                onClick = { viewModel.navigateTo(Screen.ContactDetail(contact.id)) },
                                onCallClick = {
                                    val phone = contact.getPrimaryPhoneNumber()
                                    if (phone.isNotBlank()) viewModel.initiateCall(phone, contact.displayName)
                                }
                            )
                        }

                        item {
                            Text(
                                text = "👥 OTHER CONTACTS (${searchOthers.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppTheme.colors.surface)
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    items(searchOthers, key = { it.id }) { contact ->
                        ImportedContactItem(
                            contact = contact,
                            onClick = { viewModel.navigateTo(Screen.ContactDetail(contact.id)) },
                            onCallClick = {
                                val phone = contact.getPrimaryPhoneNumber()
                                if (phone.isNotBlank()) viewModel.initiateCall(phone, contact.displayName)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImportedContactItem(
    contact: ContactEntity,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initials = contact.displayName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("imported_contact_${contact.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (contact.isStarred) AppTheme.colors.tertiary.copy(alpha = 0.2f) else AppTheme.colors.elevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (contact.isStarred) AppTheme.colors.tertiary else AppTheme.colors.primary
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.isStarred) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Starred",
                        tint = AppTheme.colors.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            val phone = contact.getPrimaryPhoneNumber()
            Text(
                text = phone.ifBlank { "No phone number" },
                fontSize = 13.sp,
                color = AppTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onCallClick,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.secondary.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call",
                tint = AppTheme.colors.secondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
