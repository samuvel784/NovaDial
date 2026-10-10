package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MultiSimRule
import com.example.data.model.SimRuleMatchingType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSimRulesSheet(
    rules: List<MultiSimRule>,
    onDismiss: () -> Unit,
    onAddRule: (MultiSimRule) -> Unit,
    onToggleRule: (id: String, enabled: Boolean) -> Unit,
    onDeleteRule: (id: String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Smart Multi-SIM Rules",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Automatically route outbound calls to preferred SIM slots",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Add Rule", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active SIM rules. Tap + to add prefix or group routing rules.")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        OutlinedCard(
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = rule.ruleName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "SIM ${rule.targetSimSlot}",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${rule.matchingType.label}: \"${rule.pattern}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = rule.isEnabled,
                                        onCheckedChange = { onToggleRule(rule.id, it) }
                                    )
                                    IconButton(onClick = { onDeleteRule(rule.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }
        }
    }

    if (showAddDialog) {
        AddSimRuleDialog(
            onDismiss = { showAddDialog = false },
            onSave = { rule ->
                onAddRule(rule)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddSimRuleDialog(
    onDismiss: () -> Unit,
    onSave: (MultiSimRule) -> Unit
) {
    var ruleName by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SimRuleMatchingType.PREFIX) }
    var selectedSimSlot by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Multi-SIM Carrier Rule") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Rule Name") },
                    placeholder = { Text("e.g. Free Toll 800 Numbers") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text("Rule Type:", style = MaterialTheme.typography.labelLarge)
                Row {
                    FilterChip(
                        selected = (selectedType == SimRuleMatchingType.PREFIX),
                        onClick = { selectedType = SimRuleMatchingType.PREFIX },
                        label = { Text("Prefix") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = (selectedType == SimRuleMatchingType.CONTACT_TAG),
                        onClick = { selectedType = SimRuleMatchingType.CONTACT_TAG },
                        label = { Text("Group Tag") }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern / Prefix") },
                    placeholder = { Text("e.g. +1 800 or Work") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Target SIM Slot:", style = MaterialTheme.typography.labelLarge)
                Row {
                    FilterChip(
                        selected = (selectedSimSlot == 1),
                        onClick = { selectedSimSlot = 1 },
                        label = { Text("SIM 1 (Primary)") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = (selectedSimSlot == 2),
                        onClick = { selectedSimSlot = 2 },
                        label = { Text("SIM 2 (Secondary)") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ruleName.isNotBlank() && pattern.isNotBlank()) {
                        onSave(
                            MultiSimRule(
                                id = UUID.randomUUID().toString(),
                                ruleName = ruleName,
                                matchingType = selectedType,
                                pattern = pattern,
                                targetSimSlot = selectedSimSlot
                            )
                        )
                    }
                },
                enabled = ruleName.isNotBlank() && pattern.isNotBlank()
            ) {
                Text("Add Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
