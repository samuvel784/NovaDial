package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactEntity
import com.example.data.sync.BackupManager
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import kotlinx.coroutines.launch

val HOME_GROUPS = listOf("All", "Favorites", "Work", "Personal", "Family")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val allContacts by viewModel.allContacts.collectAsState()
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val blockedCount by viewModel.blockedCount.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("All") }

    val selectedContactIds = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedContactIds.isNotEmpty()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var customLabelInput by remember { mutableStateOf("") }

    // Filter contacts based on query and selected group
    val filteredContacts = remember(allContacts, searchQuery, selectedGroup) {
        allContacts.filter { contact ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                contact.displayName.contains(searchQuery, ignoreCase = true) ||
                        contact.getPhoneNumbers().any { it.number.contains(searchQuery) } ||
                        contact.getEmails().any { it.email.contains(searchQuery, ignoreCase = true) }
            }
            val matchesGroup = when (selectedGroup) {
                "All" -> true
                "Favorites" -> contact.isStarred
                else -> contact.getGroups().contains(selectedGroup)
            }
            matchesSearch && matchesGroup
        }
    }

    // When searching: separate into Favorites (First) and Others (Below)
    val searchFavorites = remember(filteredContacts, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else filteredContacts.filter { it.isStarred }
    }

    val searchOthers = remember(filteredContacts, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else filteredContacts.filter { !it.isStarred }
    }

    // Default alphabetical grouping when not searching
    val groupedContacts = remember(filteredContacts) {
        filteredContacts.groupBy {
            val first = it.displayName.trim().firstOrNull()?.uppercaseChar() ?: '#'
            if (first in 'A'..'Z') first else '#'
        }.toSortedMap()
    }

    BackHandler(enabled = isSelectionMode || drawerState.isOpen) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (isSelectionMode) {
            selectedContactIds.clear()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AppTheme.colors.card,
                drawerContentColor = AppTheme.colors.textPrimary,
                modifier = Modifier.width(300.dp)
            ) {
                // Drawer Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppTheme.colors.surface)
                        .padding(20.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = if (AppTheme.colors.isLight) Color.White else Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "NovaDial Suite",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Smart Native Dialer & Contacts",
                            fontSize = 12.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = AppTheme.colors.border)

                Spacer(modifier = Modifier.height(8.dp))

                // Drawer Options
                NavigationDrawerItem(
                    label = { Text("Settings", fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AppTheme.colors.primary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(Screen.Settings)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )

                NavigationDrawerItem(
                    label = { Text("Imported Contacts", fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary) },
                    icon = { Icon(Icons.Default.ContactPhone, contentDescription = "Imported Contacts", tint = AppTheme.colors.primary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(Screen.ImportedContacts)
                    },
                    badge = {
                        Text(
                            text = "${allContacts.size}",
                            fontSize = 11.sp,
                            color = AppTheme.colors.textSecondary
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )

                NavigationDrawerItem(
                    label = { Text("Duplicate Cleaner", fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary) },
                    icon = { Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = "Duplicate Cleaner", tint = AppTheme.colors.tertiary) },
                    selected = false,
                    badge = if (duplicateGroups.isNotEmpty()) {
                        {
                            Badge(containerColor = AppTheme.colors.tertiary) {
                                Text("${duplicateGroups.size}", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else null,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(Screen.DuplicateReview)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )

                NavigationDrawerItem(
                    label = { Text("Spam Shield", fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary) },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Spam Shield", tint = AppTheme.colors.secondary) },
                    selected = false,
                    badge = {
                        Text(
                            text = if (blockedCount > 0) "$blockedCount blocked" else "Active",
                            fontSize = 11.sp,
                            color = AppTheme.colors.secondary
                        )
                    },
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(Screen.Settings)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )

                NavigationDrawerItem(
                    label = { Text("🚨 Emergency SOS Hub", fontWeight = FontWeight.Bold, color = AppTheme.colors.error) },
                    icon = { Icon(Icons.Default.Emergency, contentDescription = "SOS", tint = AppTheme.colors.error) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(Screen.Emergency)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )

                NavigationDrawerItem(
                    label = { Text("Call Analytics & Stats", fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary) },
                    icon = { Icon(Icons.Default.Insights, contentDescription = "Analytics", tint = AppTheme.colors.primary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(Screen.Analytics)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )

                NavigationDrawerItem(
                    label = { Text("AES Backup & Vault", fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary) },
                    icon = { Icon(Icons.Default.EnhancedEncryption, contentDescription = "AES Backup", tint = AppTheme.colors.primary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(Screen.Backup)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )

                NavigationDrawerItem(
                    label = { Text("Phone Lock & Security", fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary) },
                    icon = { Icon(Icons.Default.PhonelinkLock, contentDescription = "Phone Lock", tint = AppTheme.colors.primary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(Screen.Settings)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )

                HorizontalDivider(color = AppTheme.colors.border, modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    label = { Text("Favorites Page", fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary) },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Favorites", tint = AppTheme.colors.tertiary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(Screen.Favorites)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Bottom App Info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "NovaDial Suite • Privacy Secured",
                        fontSize = 11.sp,
                        color = AppTheme.colors.textMuted
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = AppTheme.colors.background,
            topBar = {
                if (isSelectionMode) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${selectedContactIds.size} Selected",
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedContactIds.clear() }) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Selection", tint = AppTheme.colors.textPrimary)
                            }
                        },
                        actions = {
                            // Select All / Deselect All
                            IconButton(onClick = {
                                if (selectedContactIds.size == filteredContacts.size) {
                                    selectedContactIds.clear()
                                } else {
                                    selectedContactIds.clear()
                                    selectedContactIds.addAll(filteredContacts.map { it.id })
                                }
                            }) {
                                Icon(
                                    imageVector = if (selectedContactIds.size == filteredContacts.size) Icons.Default.SelectAll else Icons.Default.DoneAll,
                                    contentDescription = "Select All",
                                    tint = AppTheme.colors.primary
                                )
                            }

                            // Add Label / Group
                            IconButton(onClick = { showAddLabelDialog = true }) {
                                Icon(Icons.Default.Label, contentDescription = "Add Label", tint = AppTheme.colors.primary)
                            }

                            // Share vCard
                            IconButton(onClick = {
                                val selectedContacts = allContacts.filter { selectedContactIds.contains(it.id) }
                                val vcf = BackupManager.exportToVCard(selectedContacts)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/x-vcard"
                                    putExtra(Intent.EXTRA_TEXT, vcf)
                                    putExtra(Intent.EXTRA_SUBJECT, "contacts_export.vcf")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Selected Contacts"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = AppTheme.colors.primary)
                            }

                            // Delete
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AppTheme.colors.error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.card)
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Contacts",
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.textPrimary,
                                fontSize = 20.sp
                            )
                        },
                        navigationIcon = {
                            // Top Left Corner 3 Short Lines (Hamburger Menu)
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.open() }
                                },
                                modifier = Modifier.testTag("home_hamburger_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = AppTheme.colors.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.navigateTo(Screen.Favorites) }) {
                                Icon(Icons.Default.Star, contentDescription = "Favorites", tint = AppTheme.colors.tertiary)
                            }
                            IconButton(onClick = { viewModel.navigateTo(Screen.EditContact()) }) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "New Contact", tint = AppTheme.colors.primary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background)
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("home_screen")
            ) {
                if (!isSelectionMode) {
                    // Top Search Bar (Constant across all menus - compact size)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .heightIn(min = 40.dp)
                            .testTag("home_search_bar"),
                        placeholder = { Text("Search contacts...", color = AppTheme.colors.textMuted, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = AppTheme.colors.primary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.primary,
                            unfocusedBorderColor = AppTheme.colors.border,
                            focusedContainerColor = AppTheme.colors.card,
                            unfocusedContainerColor = AppTheme.colors.card,
                            focusedTextColor = AppTheme.colors.textPrimary,
                            unfocusedTextColor = AppTheme.colors.textPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Group Filter Chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(HOME_GROUPS) { group ->
                            FilterChip(
                                selected = selectedGroup == group,
                                onClick = { selectedGroup = group },
                                label = { Text(group, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppTheme.colors.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = AppTheme.colors.primary,
                                    containerColor = AppTheme.colors.elevated,
                                    labelColor = AppTheme.colors.textSecondary
                                )
                            )
                        }
                    }

                    // Duplicate Contacts Alert Banner (if any)
                    if (duplicateGroups.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { viewModel.navigateTo(Screen.DuplicateReview) }
                                .testTag("duplicate_banner"),
                            colors = CardDefaults.cardColors(containerColor = AppTheme.colors.tertiary.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MergeType,
                                    contentDescription = null,
                                    tint = AppTheme.colors.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${duplicateGroups.size} Duplicate sets detected",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Review & Merge >",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.tertiary
                                )
                            }
                        }
                    }
                }

                // Contact List Rendering
                if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PersonOff,
                                contentDescription = null,
                                tint = AppTheme.colors.textMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No contacts matching '$searchQuery'" else "No contacts saved",
                                color = AppTheme.colors.textSecondary,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.navigateTo(Screen.EditContact()) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppTheme.colors.primary,
                                    contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                                )
                            ) {
                                Text("Create Contact")
                            }
                        }
                    }
                } else if (searchQuery.isNotBlank()) {
                    // Searching Mode: Show Favorites First at the Top, Others Below
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        if (searchFavorites.isNotEmpty()) {
                            item {
                                Text(
                                    text = "⭐ FAVORITES (${searchFavorites.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.tertiary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AppTheme.colors.surface)
                                        .padding(horizontal = 18.dp, vertical = 6.dp)
                                )
                            }

                            items(searchFavorites, key = { "fav_${it.id}" }) { contact ->
                                val isSelected = selectedContactIds.contains(contact.id)
                                SelectableContactListItem(
                                    contact = contact,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedContactIds.remove(contact.id) else selectedContactIds.add(contact.id)
                                        } else {
                                            viewModel.navigateTo(Screen.ContactDetail(contact.id))
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            selectedContactIds.add(contact.id)
                                        }
                                    },
                                    onCallClick = {
                                        val phone = contact.getPrimaryPhoneNumber()
                                        if (phone.isNotBlank()) {
                                            viewModel.initiateCall(phone, contact.displayName)
                                        }
                                    },
                                    onFavoriteToggle = { viewModel.toggleFavorite(contact) }
                                )
                            }
                        }

                        if (searchOthers.isNotEmpty()) {
                            item {
                                Text(
                                    text = "👥 OTHER CONTACTS (${searchOthers.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.primary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AppTheme.colors.surface)
                                        .padding(horizontal = 18.dp, vertical = 6.dp)
                                )
                            }

                            items(searchOthers, key = { it.id }) { contact ->
                                val isSelected = selectedContactIds.contains(contact.id)
                                SelectableContactListItem(
                                    contact = contact,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedContactIds.remove(contact.id) else selectedContactIds.add(contact.id)
                                        } else {
                                            viewModel.navigateTo(Screen.ContactDetail(contact.id))
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            selectedContactIds.add(contact.id)
                                        }
                                    },
                                    onCallClick = {
                                        val phone = contact.getPrimaryPhoneNumber()
                                        if (phone.isNotBlank()) {
                                            viewModel.initiateCall(phone, contact.displayName)
                                        }
                                    },
                                    onFavoriteToggle = { viewModel.toggleFavorite(contact) }
                                )
                            }
                        }
                    }
                } else {
                    // Default Alphabetical Section Browsing
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        groupedContacts.forEach { (initial, contactsInGroup) ->
                            item {
                                Text(
                                    text = initial.toString(),
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.primary,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AppTheme.colors.surface)
                                        .padding(horizontal = 18.dp, vertical = 4.dp)
                                )
                            }

                            items(contactsInGroup, key = { it.id }) { contact ->
                                val isSelected = selectedContactIds.contains(contact.id)
                                SelectableContactListItem(
                                    contact = contact,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedContactIds.remove(contact.id) else selectedContactIds.add(contact.id)
                                        } else {
                                            viewModel.navigateTo(Screen.ContactDetail(contact.id))
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            selectedContactIds.add(contact.id)
                                        }
                                    },
                                    onCallClick = {
                                        val phone = contact.getPrimaryPhoneNumber()
                                        if (phone.isNotBlank()) {
                                            viewModel.initiateCall(phone, contact.displayName)
                                        }
                                    },
                                    onFavoriteToggle = { viewModel.toggleFavorite(contact) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete ${selectedContactIds.size} Contacts?", color = AppTheme.colors.textPrimary) },
            text = { Text("This will remove the selected contacts from NovaDial and your synced device address book.", color = AppTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToDelete = selectedContactIds.toList()
                        selectedContactIds.clear()
                        showDeleteConfirmDialog = false
                        viewModel.deleteContacts(idsToDelete)
                    }
                ) {
                    Text("Delete", color = AppTheme.colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.card
        )
    }

    // Add Label / Group Dialog
    if (showAddLabelDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddLabelDialog = false
                customLabelInput = ""
            },
            title = { Text("Assign Group Label", color = AppTheme.colors.textPrimary) },
            text = {
                Column {
                    Text("Select or create a label for ${selectedContactIds.size} contacts:", color = AppTheme.colors.textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Work", "Personal", "Family", "VIP").forEach { label ->
                            SuggestionChip(
                                onClick = {
                                    val ids = selectedContactIds.toList()
                                    selectedContactIds.clear()
                                    showAddLabelDialog = false
                                    viewModel.assignGroupToContacts(ids, label)
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customLabelInput,
                        onValueChange = { customLabelInput = it },
                        placeholder = { Text("Or enter custom group name...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppTheme.colors.primary,
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
                        if (customLabelInput.isNotBlank()) {
                            val ids = selectedContactIds.toList()
                            selectedContactIds.clear()
                            val label = customLabelInput.trim()
                            customLabelInput = ""
                            showAddLabelDialog = false
                            viewModel.assignGroupToContacts(ids, label)
                        }
                    },
                    enabled = customLabelInput.isNotBlank()
                ) {
                    Text("Apply Label", color = AppTheme.colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddLabelDialog = false
                    customLabelInput = ""
                }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
            containerColor = AppTheme.colors.card
        )
    }
}
