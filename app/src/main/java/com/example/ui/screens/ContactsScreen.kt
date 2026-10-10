package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import com.example.util.PhoneNumberUtils

val CONTACT_GROUPS = listOf("All", "Favorites", "Work", "Family", "Friends", "Personal")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allContacts by viewModel.allContacts.collectAsState()
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("All") }

    val dynamicGroups = remember(allContacts) {
        val custom = allContacts.flatMap { it.getGroups() }.toSet()
        (CONTACT_GROUPS.drop(1) + custom).distinct()
    }

    val allAvailableGroups = remember(dynamicGroups) {
        listOf("All") + dynamicGroups
    }

    val selectedContactIds = remember { mutableStateListOf<String>() }
    val isSelectionMode = selectedContactIds.isNotEmpty()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var customLabelInput by remember { mutableStateOf("") }
    var newGroupNameInput by remember { mutableStateOf("") }

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

    val groupedContacts = remember(filteredContacts) {
        filteredContacts.groupBy {
            val first = it.displayName.trim().firstOrNull()?.uppercaseChar() ?: '#'
            if (first in 'A'..'Z') first else '#'
        }.toSortedMap()
    }

    BackHandler(enabled = isSelectionMode) {
        selectedContactIds.clear()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = NovaNavyDeep,
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedContactIds.size} Selected",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedContactIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Selection", tint = TextPrimary)
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
                                tint = NovaCyan
                            )
                        }

                        // Add Label / Group
                        IconButton(onClick = { showAddLabelDialog = true }) {
                            Icon(Icons.Default.Label, contentDescription = "Add Label", tint = NovaCyan)
                        }

                        // Share
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
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = NovaCyan)
                        }

                        // Delete
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NovaCoral)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NovaNavyCard)
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { viewModel.navigateTo(Screen.EditContact()) },
                    containerColor = NovaCyan,
                    contentColor = NovaNavyDeep,
                    modifier = Modifier
                        .padding(bottom = 80.dp)
                        .testTag("add_contact_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("contacts_screen")
        ) {
            if (!isSelectionMode) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("contact_search_bar"),
                    placeholder = { Text("Search by name, phone, or email...", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = NovaCyan)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NovaCyan,
                        unfocusedBorderColor = NovaBorder,
                        focusedContainerColor = NovaNavyCard,
                        unfocusedContainerColor = NovaNavyCard,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                // Group Filter Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allAvailableGroups) { group ->
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = { selectedGroup = group },
                            label = { Text(group, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NovaCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NovaCyan
                            )
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { showCreateGroupDialog = true },
                            label = { Text("+ New Folder", fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = NovaNavyCard, labelColor = NovaCyan)
                        )
                    }
                }

                // Duplicate Contacts Alert Banner
                if (duplicateGroups.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { viewModel.navigateTo(Screen.DuplicateReview) },
                        colors = CardDefaults.cardColors(containerColor = NovaAmber.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MergeType,
                                contentDescription = null,
                                tint = NovaAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${duplicateGroups.size} Duplicate sets detected",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Review & Merge >",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NovaAmber
                            )
                        }
                    }
                }
            }

            // Contact List with Alphabetical Section Dividers
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
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No contacts matching '$searchQuery'" else "No contacts saved",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.navigateTo(Screen.EditContact()) },
                            colors = ButtonDefaults.buttonColors(containerColor = NovaCyan, contentColor = NovaNavyDeep)
                        ) {
                            Text("Create Contact")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    groupedContacts.forEach { (initial, contactsInGroup) ->
                        item {
                            Text(
                                text = initial.toString(),
                                fontWeight = FontWeight.Bold,
                                color = NovaCyan,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NovaNavyDark.copy(alpha = 0.7f))
                                    .padding(horizontal = 18.dp, vertical = 4.dp)
                            )
                        }

                        items(contactsInGroup) { contact ->
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

    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete ${selectedContactIds.size} Contacts?") },
            text = { Text("This will remove the selected contacts from NovaDial and your synced device address book.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToDelete = selectedContactIds.toList()
                        selectedContactIds.clear()
                        showDeleteConfirmDialog = false
                        viewModel.deleteContacts(idsToDelete)
                    }
                ) {
                    Text("Delete", color = NovaCoral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = NovaNavyCard
        )
    }

    // Add Label / Group Dialog
    if (showAddLabelDialog) {
        AlertDialog(
            onDismissRequest = { showAddLabelDialog = false },
            title = { Text("Add Label to ${selectedContactIds.size} Contacts") },
            text = {
                Column {
                    Text("Choose or enter a group label:", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    listOf("Favorites", "Work", "Family", "Friends", "Personal").forEach { label ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.assignGroupToContacts(selectedContactIds.toList(), label)
                                    selectedContactIds.clear()
                                    showAddLabelDialog = false
                                },
                            color = NovaNavyElevated,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(label, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customLabelInput,
                        onValueChange = { customLabelInput = it },
                        placeholder = { Text("Or custom folder...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NovaCyan,
                            unfocusedBorderColor = NovaBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customLabelInput.isNotBlank()) {
                            viewModel.assignGroupToContacts(selectedContactIds.toList(), customLabelInput.trim())
                            selectedContactIds.clear()
                            showAddLabelDialog = false
                            customLabelInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaCyan, contentColor = NovaNavyDeep)
                ) {
                    Text("Apply Label")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLabelDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = NovaNavyCard
        )
    }

    // Create New Group Folder Dialog
    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create New Group Folder") },
            text = {
                Column {
                    Text("Enter a new folder name (e.g. 'Gym', 'School', 'Colleagues'):", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newGroupNameInput,
                        onValueChange = { newGroupNameInput = it },
                        placeholder = { Text("Folder name...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NovaCyan,
                            unfocusedBorderColor = NovaBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupNameInput.isNotBlank()) {
                            selectedGroup = newGroupNameInput.trim()
                            showCreateGroupDialog = false
                            newGroupNameInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NovaCyan, contentColor = NovaNavyDeep)
                ) {
                    Text("Create & Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = NovaNavyCard
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableContactListItem(
    contact: ContactEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCallClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (isSelected) AppTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = AppTheme.colors.primary,
                    checkmarkColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.elevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.getInitials(),
                fontWeight = FontWeight.Bold,
                color = if (contact.isStarred) AppTheme.colors.tertiary else AppTheme.colors.primary,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val primaryPhone = contact.getPrimaryPhoneNumber()
            if (primaryPhone.isNotBlank()) {
                Text(
                    text = PhoneNumberUtils.formatForDisplay(primaryPhone),
                    color = AppTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
            } else if (contact.company.isNotBlank()) {
                Text(
                    text = contact.company,
                    color = AppTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        if (!isSelectionMode) {
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (contact.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (contact.isStarred) AppTheme.colors.tertiary else AppTheme.colors.textMuted,
                    modifier = Modifier.size(20.dp)
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
}
