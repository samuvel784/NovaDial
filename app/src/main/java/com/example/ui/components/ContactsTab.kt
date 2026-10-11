/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.Contact
import com.example.model.getInitials
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalAmoledMode
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.RichHapticEngine
import kotlinx.coroutines.launch
import java.util.Locale

/** Distinct box designs per page so Home and Favorites don't look identical. */
enum class ContactCardStyle { HOME, FAVORITES }

@Composable
fun ContactsTabContent(
    viewModel: DialerViewModel,
    favoriteContacts: List<Contact>,
    onCallClick: (Contact) -> Unit,
    onAddContactClick: () -> Unit,
    onToggleFavorite: (Contact) -> Unit,
    hasPermission: Boolean = true,
    isLoading: Boolean = false,
    onRequestPermission: () -> Unit = {},
    onEditContact: (Contact) -> Unit = {},
    onDeleteContact: (Contact) -> Unit = {},
    startWithFavoritesOnly: Boolean = false,
    cardStyle: ContactCardStyle = ContactCardStyle.HOME
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val allContacts by viewModel.allContactsFlow.collectAsStateWithLifecycle()
    val selectedAccountFilter by viewModel.selectedAccountFilter
    val searchQuery by viewModel.searchQuery

    // Base list depends on the mode (favorites-only vs all), respecting the account filter.
    val baseContacts = remember(startWithFavoritesOnly, favoriteContacts, allContacts, selectedAccountFilter) {
        when {
            startWithFavoritesOnly -> favoriteContacts.sortedBy { it.name.uppercase(Locale.ROOT) }
            selectedAccountFilter.isBlank() -> allContacts
            selectedAccountFilter.equals("Phone", ignoreCase = true) -> allContacts.filter {
                it.accountName.isBlank() || it.accountName.equals("Phone", ignoreCase = true) || it.accountType.contains("local", ignoreCase = true)
            }
            else -> allContacts.filter { it.accountName == selectedAccountFilter }
        }
    }

    // Apply the shared header search to the visible list.
    val contacts = remember(baseContacts, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            baseContacts
        } else {
            baseContacts.filter {
                it.name.contains(query, ignoreCase = true) || it.number.contains(query, ignoreCase = true)
            }
        }
    }

    val alphabet = remember(contacts) {
        val uniqueLetters = contacts.asSequence()
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .map { it.first().uppercaseChar() }
            .distinct()
            .toList()

        val letterChars = uniqueLetters.filter { it.isLetter() }.sorted()
        val otherChars = uniqueLetters.filter { !it.isLetter() }

        val result = mutableListOf<Char>()
        result.addAll(letterChars)
        if (otherChars.isNotEmpty() || result.isEmpty()) {
            result.add('#')
        }

        if (result.size <= 1) ('A'..'Z').toList() + '#' else result
    }

    val letterToIndex = remember(contacts) {
        val map = HashMap<Char, Int>()
        for (i in contacts.indices) {
            val firstChar = contacts[i].name.trim().firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (firstChar.isLetter()) firstChar else '#'
            if (!map.containsKey(key)) {
                map[key] = i
            }
        }
        map
    }

    val activeLetter = remember(gridState, alphabet, contacts) {
        derivedStateOf {
            val firstVisibleIndex = gridState.firstVisibleItemIndex
            val contact = contacts.getOrNull(firstVisibleIndex)
            val firstChar = contact?.name?.trim()?.firstOrNull()?.uppercaseChar() ?: '#'
            if (alphabet.contains(firstChar)) firstChar
            else if (!firstChar.isLetter() && alphabet.contains('#')) '#'
            else alphabet.firstOrNull() ?: '#'
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            if (!hasPermission && !isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.permissions_required),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.contacts_perm_desc),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequestPermission,
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(stringResource(R.string.enable_contacts_perm))
                        }
                    }
                }
            }

            if (hasPermission && !isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAddContactClick,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_contact_fab")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.action_add_contact),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_add_contact),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateIllustration(
                        title = if (startWithFavoritesOnly) stringResource(R.string.no_favorites_title) else stringResource(R.string.no_contacts_title),
                        subtitle = if (startWithFavoritesOnly) stringResource(R.string.no_favorites_subtitle) else stringResource(R.string.no_contacts_subtitle)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (cardStyle == ContactCardStyle.FAVORITES) 2 else 1),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 128.dp)
                ) {
                    items(
                        items = contacts,
                        key = { "${it.id}_${it.number}" }
                    ) { contact ->
                        ContactBoxCard(
                            contact = contact,
                            style = cardStyle,
                            viewModel = viewModel,
                            onCall = onCallClick,
                            onToggleFavorite = onToggleFavorite,
                            onEditContact = onEditContact,
                            onDeleteContact = onDeleteContact
                        )
                    }
                }
            }
        }

        // A-Z Scroller Rail
        if (hasPermission && !isLoading && contacts.isNotEmpty()) {
            val haptic = LocalHapticFeedback.current
            val maxScrollTarget = contacts.size - 1

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(40.dp)
                    .padding(vertical = 40.dp)
                    .pointerInput(alphabet, letterToIndex, maxScrollTarget) {
                        detectTapGestures { offset ->
                            val index = (offset.y / size.height * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                            val char = alphabet[index]
                            letterToIndex[char]?.let { targetIndex ->
                                val safeIndex = targetIndex.coerceIn(0, maxScrollTarget.coerceAtLeast(0))
                                coroutineScope.launch {
                                    gridState.animateScrollToItem(safeIndex)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        }
                    }
                    .pointerInput(alphabet, letterToIndex, maxScrollTarget) {
                        detectDragGestures { change, _ ->
                            val index = (change.position.y / size.height * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                            val char = alphabet[index]
                            letterToIndex[char]?.let { targetIndex ->
                                val safeIndex = targetIndex.coerceIn(0, maxScrollTarget.coerceAtLeast(0))
                                coroutineScope.launch {
                                    gridState.scrollToItem(safeIndex)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                alphabet.forEach { char ->
                    val isActive = char == activeLetter.value
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Text(
                            text = char.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = if (isActive) 11.sp else 9.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single contact "box". Tapping opens the full [ContactProfileSheet] (numbers, message,
 * favourite, edit, share, copy, block, delete); the trailing call button on the Home layout
 * dials the primary number directly.
 *
 * [ContactCardStyle.HOME] lays the avatar left (wide box); [ContactCardStyle.FAVORITES]
 * centres the avatar on top with a gold star so the page looks distinct.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactBoxCard(
    contact: Contact,
    style: ContactCardStyle,
    viewModel: DialerViewModel,
    onCall: (Contact) -> Unit,
    onToggleFavorite: (Contact) -> Unit,
    onEditContact: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit
) {
    val context = LocalContext.current
    var showProfile by remember { mutableStateOf(false) }
    val isAmoled = LocalAmoledMode.current

    val numbers = remember(contact) { contact.getAllNumbers() }
    val primaryNumber = numbers.firstOrNull()?.number ?: contact.number
    val primaryLabel = numbers.firstOrNull()?.label ?: contact.label

    val isFavorites = style == ContactCardStyle.FAVORITES
    val containerColor = when {
        isAmoled -> Color(0xFF141414)
        isFavorites -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val shape = if (isFavorites) RoundedCornerShape(26.dp) else RoundedCornerShape(18.dp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isAmoled) BorderStroke(1.dp, Color(0xFF1C1C1C)) else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                        showProfile = true
                    },
                    onLongClick = {
                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.HEAVY_CLICK)
                        showProfile = true
                    }
                )
                .padding(start = 12.dp, end = if (isFavorites) 12.dp else 4.dp, top = 12.dp, bottom = 12.dp)
        ) {
            if (isFavorites) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ContactBoxAvatar(contact = contact, size = 56.dp, showStar = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = contact.name.ifBlank { contact.number },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = contact.number,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ContactBoxAvatar(contact = contact, size = 46.dp, showStar = contact.favorite)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.name.ifBlank { contact.number },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = contact.number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                            onCall(contact.copy(number = primaryNumber, label = primaryLabel))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = stringResource(R.string.action_call),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    if (showProfile) {
        ContactProfileSheet(
            contact = contact,
            viewModel = viewModel,
            onCall = onCall,
            onToggleFavorite = onToggleFavorite,
            onEditContact = onEditContact,
            onDeleteContact = onDeleteContact,
            onDismiss = { showProfile = false }
        )
    }
}

@Composable
internal fun ContactBoxAvatar(
    contact: Contact,
    size: androidx.compose.ui.unit.Dp,
    showStar: Boolean
) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(AppShapes.Avatar)
                .background(contact.avatarBg),
            contentAlignment = Alignment.Center
        ) {
            if (contact.photoUri.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(contact.photoUri)
                        .size(128, 128)
                        .crossfade(false)
                        .build(),
                    contentDescription = contact.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val isSaved = contact.name.isNotBlank() && contact.name != contact.number && contact.name != "Unknown"
                if (isSaved) {
                    Text(
                        text = contact.avatarText.ifEmpty { getInitials(contact.name) },
                        style = MaterialTheme.typography.titleMedium,
                        color = contact.avatarTextColor,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = contact.avatarTextColor,
                        modifier = Modifier.size(size / 2)
                    )
                }
            }
        }
        if (showStar) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFEAB308),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
