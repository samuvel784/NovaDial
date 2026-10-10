package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SpamCallerCategory

@Composable
fun SpamReportDialog(
    phoneNumber: String,
    onDismiss: () -> Unit,
    onSubmitReport: (category: SpamCallerCategory, comment: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(SpamCallerCategory.TELEMARKETER) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Community Spam Report", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Help protect the community by reporting $phoneNumber:")
                Spacer(modifier = Modifier.height(12.dp))

                Text("Select Category:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))

                SpamCallerCategory.values().forEach { cat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        RadioButton(
                            selected = (selectedCategory == cat),
                            onClick = { selectedCategory = cat }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${cat.icon} ${cat.label}", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Details (optional)") },
                    placeholder = { Text("e.g. Automated loan offer bot") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitReport(selectedCategory, comment) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
