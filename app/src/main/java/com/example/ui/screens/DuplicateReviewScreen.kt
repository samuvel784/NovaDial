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
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.example.data.sync.DuplicateGroup
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import com.example.util.PhoneNumberUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateReviewScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Duplicate Cleaner",
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textPrimary)
                    }
                },
                actions = {
                    if (duplicateGroups.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.mergeAllDuplicates() },
                            colors = ButtonDefaults.textButtonColors(contentColor = AppTheme.colors.secondary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Merge All", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background)
            )
        }
    ) { innerPadding ->
        if (duplicateGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = AppTheme.colors.secondary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Duplicate Contacts Found",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your contact database is clean and organized.",
                        fontSize = 14.sp,
                        color = AppTheme.colors.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .testTag("duplicate_review_list"),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.secondary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, tint = AppTheme.colors.secondary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${duplicateGroups.size} Duplicate Set${if (duplicateGroups.size > 1) "s" else ""} Detected",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = AppTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "Merge all duplicate sets into single unified contacts with one button without data loss.",
                                        fontSize = 12.sp,
                                        color = AppTheme.colors.textSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.mergeAllDuplicates() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppTheme.colors.secondary,
                                    contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Merge All Duplicate Sets", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                items(duplicateGroups, key = { it.id }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        onMerge = { viewModel.mergeDuplicate(group) }
                    )
                }
            }
        }
    }
}

@Composable
fun DuplicateGroupCard(
    group: DuplicateGroup,
    onMerge: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.tertiary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = group.mergedPreview.getInitials(),
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.tertiary,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.mergedPreview.displayName,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${group.contacts.size} matching contacts found",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = onMerge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.secondary,
                        contentColor = if (AppTheme.colors.isLight) Color.White else Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Merge", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = AppTheme.colors.border)

            Text(
                text = "Contacts To Be Merged:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textMuted
            )

            group.contacts.forEach { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppTheme.colors.elevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.displayName,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 13.sp
                        )
                        val phones = contact.getPhoneNumbers().joinToString(", ") { it.number }
                        if (phones.isNotBlank()) {
                            Text(
                                text = phones,
                                color = AppTheme.colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
