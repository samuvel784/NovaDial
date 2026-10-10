package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactEntity
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val favoriteContacts by viewModel.favoriteContacts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredFavorites = remember(favoriteContacts, searchQuery) {
        if (searchQuery.isBlank()) {
            favoriteContacts
        } else {
            favoriteContacts.filter { contact ->
                contact.displayName.contains(searchQuery, ignoreCase = true) ||
                        contact.getPhoneNumbers().any { it.number.contains(searchQuery) }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = AppTheme.colors.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Favorites",
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.Home) }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Contact", tint = AppTheme.colors.primary)
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
                .testTag("favorites_screen")
        ) {
            // Top Search Bar (Constant across all menus)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("favorites_search_bar"),
                placeholder = { Text("Search favorite contacts...", color = AppTheme.colors.textMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = AppTheme.colors.tertiary)
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
                    focusedBorderColor = AppTheme.colors.tertiary,
                    unfocusedBorderColor = AppTheme.colors.border,
                    focusedContainerColor = AppTheme.colors.card,
                    unfocusedContainerColor = AppTheme.colors.card,
                    focusedTextColor = AppTheme.colors.textPrimary,
                    unfocusedTextColor = AppTheme.colors.textPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            )

            if (favoriteContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(AppTheme.colors.tertiary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = AppTheme.colors.tertiary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Favorite Contacts Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Star contacts in your address book for instant speed dial and quick access right here.",
                            fontSize = 13.sp,
                            color = AppTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.navigateTo(Screen.Home) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colors.primary,
                                contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse All Contacts")
                        }
                    }
                }
            } else if (filteredFavorites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No favorite matching '$searchQuery'",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredFavorites, key = { it.id }) { contact ->
                        FavoriteContactCard(
                            contact = contact,
                            onClick = { viewModel.navigateTo(Screen.ContactDetail(contact.id)) },
                            onCallClick = {
                                val phone = contact.getPrimaryPhoneNumber()
                                if (phone.isNotBlank()) {
                                    viewModel.initiateCall(phone, contact.displayName)
                                }
                            },
                            onMessageClick = {
                                val phone = contact.getPrimaryPhoneNumber()
                                if (phone.isNotBlank()) {
                                    val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
                                    context.startActivity(smsIntent)
                                }
                            },
                            onUnstarClick = { viewModel.toggleFavorite(contact) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteContactCard(
    contact: ContactEntity,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onMessageClick: () -> Unit,
    onUnstarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initials = contact.displayName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("favorite_card_${contact.id}"),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onUnstarClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Remove Favorite",
                        tint = AppTheme.colors.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.elevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = contact.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = AppTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            val phone = contact.getPrimaryPhoneNumber()
            Text(
                text = phone.ifBlank { "No Number" },
                fontSize = 12.sp,
                color = AppTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.primary),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMessageClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.elevated),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = AppTheme.colors.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = "Message",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
