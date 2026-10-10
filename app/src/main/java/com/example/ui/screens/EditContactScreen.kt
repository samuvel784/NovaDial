package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EmailItem
import com.example.data.model.PhoneNumberItem
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactScreen(
    contactId: String?,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var displayName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var ringtone by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isStarred by remember { mutableStateOf(false) }

    // Additional Fields (More button)
    var company by remember { mutableStateOf("") }
    var jobTitle by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var phoneticName by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var pronouns by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var sipAddress by remember { mutableStateOf("") }
    var messageTone by remember { mutableStateOf("") }
    var vibrationPattern by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var middleName by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var showMoreFields by remember { mutableStateOf(false) }

    val phoneList = remember { mutableStateListOf(PhoneNumberItem("", "Mobile", true)) }
    val emailList = remember { mutableStateListOf<EmailItem>() }
    val selectedGroups = remember { mutableStateListOf<String>() }

    // Pre-populate if editing existing contact
    LaunchedEffect(contactId) {
        if (contactId != null) {
            val existing = viewModel.repository.getContactById(contactId)
            if (existing != null) {
                displayName = existing.displayName
                address = existing.address
                ringtone = existing.ringtone
                birthday = existing.birthday
                photoUri = existing.photoUri ?: ""
                company = existing.company
                jobTitle = existing.jobTitle
                nickname = existing.nickname
                phoneticName = existing.phoneticName
                website = existing.website
                pronouns = existing.pronouns
                relationship = existing.relationship
                sipAddress = existing.sipAddress
                messageTone = existing.messageTone
                vibrationPattern = existing.vibrationPattern
                department = existing.department
                middleName = existing.middleName
                suffix = existing.suffix
                prefix = existing.prefix
                isStarred = existing.isStarred
                notes = viewModel.repository.decryptContactNotes(existing.notes)

                phoneList.clear()
                val phones = existing.getPhoneNumbers()
                if (phones.isNotEmpty()) {
                    phoneList.addAll(phones)
                } else {
                    phoneList.add(PhoneNumberItem("", "Mobile", true))
                }

                emailList.clear()
                emailList.addAll(existing.getEmails())

                selectedGroups.clear()
                selectedGroups.addAll(existing.getGroups())
            }
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
                    Text(
                        text = if (contactId != null) "Edit Contact" else "New Contact",
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
                    IconButton(
                        onClick = {
                            if (displayName.isBlank() && phoneList.all { it.number.isBlank() }) {
                                viewModel.showStatus("Please enter a name or phone number.")
                                return@IconButton
                            }
                            coroutineScope.launch {
                                val validPhones = phoneList.filter { it.number.isNotBlank() }
                                val validEmails = emailList.filter { it.email.isNotBlank() }
                                viewModel.repository.saveContact(
                                    id = contactId,
                                    displayName = displayName.ifBlank { validPhones.firstOrNull()?.number ?: "Unnamed" },
                                    phoneNumbers = validPhones,
                                    emails = validEmails,
                                    notes = notes,
                                    company = company,
                                    jobTitle = jobTitle,
                                    address = address,
                                    ringtone = ringtone,
                                    birthday = birthday,
                                    nickname = nickname,
                                    phoneticName = phoneticName,
                                    website = website,
                                    pronouns = pronouns,
                                    relationship = relationship,
                                    sipAddress = sipAddress,
                                    messageTone = messageTone,
                                    vibrationPattern = vibrationPattern,
                                    department = department,
                                    middleName = middleName,
                                    suffix = suffix,
                                    prefix = prefix,
                                    groups = selectedGroups.toList(),
                                    isStarred = isStarred,
                                    photoUri = photoUri.ifBlank { null }
                                )
                                viewModel.refreshDuplicates()
                                viewModel.showStatus("Contact saved.")
                                viewModel.navigateBack()
                            }
                        },
                        modifier = Modifier.testTag("save_contact_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save", tint = AppTheme.colors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Core Contact Fields",
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primary,
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Name (Full Name)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_name_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppTheme.colors.primary,
                                unfocusedBorderColor = AppTheme.colors.border,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            )
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppTheme.colors.primary,
                                unfocusedBorderColor = AppTheme.colors.border,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            )
                        )

                        OutlinedTextField(
                            value = ringtone,
                            onValueChange = { ringtone = it },
                            label = { Text("Ringtone") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppTheme.colors.primary,
                                unfocusedBorderColor = AppTheme.colors.border,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            )
                        )

                        OutlinedTextField(
                            value = birthday,
                            onValueChange = { birthday = it },
                            label = { Text("Birthday (e.g. MM/DD/YYYY)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppTheme.colors.primary,
                                unfocusedBorderColor = AppTheme.colors.border,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            )
                        )

                        OutlinedTextField(
                            value = photoUri,
                            onValueChange = { photoUri = it },
                            label = { Text("Photo URL / URI (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppTheme.colors.primary,
                                unfocusedBorderColor = AppTheme.colors.border,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            )
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Add to Favorites",
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = isStarred,
                                onCheckedChange = { isStarred = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppTheme.colors.tertiary,
                                    checkedTrackColor = AppTheme.colors.elevated
                                )
                            )
                        }
                    }
                }
            }

            // Phone Numbers Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Phone Numbers",
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primary,
                                fontSize = 13.sp
                            )
                            TextButton(onClick = {
                                phoneList.add(PhoneNumberItem("", "Mobile", phoneList.isEmpty()))
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppTheme.colors.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", color = AppTheme.colors.primary)
                            }
                        }

                        phoneList.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = item.number,
                                    onValueChange = { newNum ->
                                        phoneList[index] = item.copy(number = newNum)
                                    },
                                    label = { Text("Phone ${index + 1} (${item.type})") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AppTheme.colors.primary,
                                        unfocusedBorderColor = AppTheme.colors.border,
                                        focusedTextColor = AppTheme.colors.textPrimary,
                                        unfocusedTextColor = AppTheme.colors.textPrimary
                                    )
                                )

                                if (phoneList.size > 1) {
                                    IconButton(onClick = { phoneList.removeAt(index) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = AppTheme.colors.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Emails Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Email Addresses",
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primary,
                                fontSize = 13.sp
                            )
                            TextButton(onClick = {
                                emailList.add(EmailItem("", "Work"))
                            }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = AppTheme.colors.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", color = AppTheme.colors.primary)
                            }
                        }

                        if (emailList.isEmpty()) {
                            Text("No emails added.", color = AppTheme.colors.textMuted, fontSize = 13.sp)
                        } else {
                            emailList.forEachIndexed { index, emailItem ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = emailItem.email,
                                        onValueChange = { newEmail ->
                                            emailList[index] = emailItem.copy(email = newEmail)
                                        },
                                        label = { Text("Email ${index + 1}") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AppTheme.colors.primary,
                                            unfocusedBorderColor = AppTheme.colors.border,
                                            focusedTextColor = AppTheme.colors.textPrimary,
                                            unfocusedTextColor = AppTheme.colors.textPrimary
                                        )
                                    )

                                    IconButton(onClick = { emailList.removeAt(index) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = AppTheme.colors.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Notes Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.border))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Notes (Encrypted Vault)",
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.primary,
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            placeholder = { Text("Private contact notes...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppTheme.colors.primary,
                                unfocusedBorderColor = AppTheme.colors.border,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            )
                        )
                    }
                }
            }

            // More Fields Button & Expandable Additional Fields Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Additional Fields (More)",
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.colors.primary,
                                fontSize = 13.sp
                            )
                            TextButton(onClick = { showMoreFields = !showMoreFields }) {
                                Text(if (showMoreFields) "Less" else "More", color = AppTheme.colors.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (showMoreFields) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = AppTheme.colors.primary
                                )
                            }
                        }

                        if (showMoreFields) {
                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = company,
                                onValueChange = { company = it },
                                label = { Text("Company") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = jobTitle,
                                onValueChange = { jobTitle = it },
                                label = { Text("Job Title") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = nickname,
                                onValueChange = { nickname = it },
                                label = { Text("Nickname") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = phoneticName,
                                onValueChange = { phoneticName = it },
                                label = { Text("Phonetic Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = website,
                                onValueChange = { website = it },
                                label = { Text("Website") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = pronouns,
                                onValueChange = { pronouns = it },
                                label = { Text("Pronouns") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = relationship,
                                onValueChange = { relationship = it },
                                label = { Text("Relationship") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = sipAddress,
                                onValueChange = { sipAddress = it },
                                label = { Text("SIP / Internet Call Address") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = messageTone,
                                onValueChange = { messageTone = it },
                                label = { Text("Message Tone") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = vibrationPattern,
                                onValueChange = { vibrationPattern = it },
                                label = { Text("Vibration Pattern") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = department,
                                onValueChange = { department = it },
                                label = { Text("Department") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = middleName,
                                onValueChange = { middleName = it },
                                label = { Text("Middle Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = suffix,
                                onValueChange = { suffix = it },
                                label = { Text("Suffix") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )

                            OutlinedTextField(
                                value = prefix,
                                onValueChange = { prefix = it },
                                label = { Text("Prefix") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppTheme.colors.primary,
                                    unfocusedBorderColor = AppTheme.colors.border,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
