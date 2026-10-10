package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.crypto.CryptoManager
import com.example.data.local.AppDatabase
import com.example.data.model.BackupRecordEntity
import com.example.data.model.BlockedNumberEntity
import com.example.data.model.CallLogEntry
import com.example.data.model.CallPosterTheme
import com.example.data.model.CallType
import com.example.data.model.ContactCallPoster
import com.example.data.model.ContactEntity
import com.example.data.model.ContactSource
import com.example.data.model.EmergencyContact
import com.example.data.model.MoshiHolder
import com.example.data.model.PhoneNumberItem
import com.example.data.model.PostCallNote
import com.example.data.model.QuickSmsTemplate
import com.example.data.model.SpeedDialItem
import com.example.data.model.SpamEntryEntity
import com.example.data.model.InterceptedSpamCallEntity
import com.example.data.model.SpamDetectionVerdict
import com.example.data.model.VoicemailItem
import com.example.data.sync.DuplicateDetector
import com.example.data.sync.DuplicateGroup
import com.example.data.sync.GoogleContactsSyncManager
import com.example.telecom.CallManager
import com.example.telecom.CommunitySpamManager
import com.example.telecom.EmergencySettings
import com.example.telecom.EmergencyTriggerManager
import com.example.telecom.FlashAlertManager
import com.example.telecom.GestureControlManager
import com.example.telecom.RealtimeSpamDetector
import com.example.ui.theme.ThemeMode
import com.example.util.PhoneNumberUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

class NovaDialRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    val callManager = CallManager.getInstance(context)
    val googleSyncManager = GoogleContactsSyncManager(context)
    val emergencyTriggerManager = EmergencyTriggerManager(context)
    val communitySpamManager = CommunitySpamManager(context)
    val flashAlertManager = FlashAlertManager(context)
    val gestureControlManager = GestureControlManager(context)

    private val prefs: SharedPreferences = context.getSharedPreferences("novadial_security_prefs", Context.MODE_PRIVATE)
    private val privacyPrefs = context.getSharedPreferences("novadial_privacy_prefs", Context.MODE_PRIVATE)

    fun setContactsSyncConsent(consent: Boolean) {
        privacyPrefs.edit().putBoolean("sync_contacts_consent", consent).apply()
        android.util.Log.d("PrivacyAudit", "User consent updated for contacts sync: $consent")
    }

    fun getContactsSyncConsent(): Boolean {
        return privacyPrefs.getBoolean("sync_contacts_consent", false)
    }

    fun setCallLogsSyncConsent(consent: Boolean) {
        privacyPrefs.edit().putBoolean("sync_call_logs_consent", consent).apply()
        android.util.Log.d("PrivacyAudit", "User consent updated for call logs sync: $consent")
        if (!consent) {
            unregisterCallLogObserver()
        }
    }

    fun getCallLogsSyncConsent(): Boolean {
        return privacyPrefs.getBoolean("sync_call_logs_consent", false)
    }

    private fun unregisterCallLogObserver() {
        try {
            context.contentResolver.unregisterContentObserver(callLogObserver)
            android.util.Log.d("PrivacyAudit", "CallLog ContentObserver unregistered due to revoked consent or permission.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val callLogObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            CoroutineScope(Dispatchers.IO).launch {
                syncWithDeviceCallLogs()
            }
        }
    }

    init {
        try {
            if (DeviceContactsHelper.hasCallLogPermission(context) && getCallLogsSyncConsent()) {
                context.contentResolver.registerContentObserver(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    true,
                    callLogObserver
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncWithDeviceCallLogs() = withContext(Dispatchers.IO) {
        val hasPermission = DeviceContactsHelper.hasCallLogPermission(context)
        val hasConsent = getCallLogsSyncConsent()
        if (!hasPermission || !hasConsent) {
            android.util.Log.w("PrivacyAudit", "Data access blocked: Call log permission ($hasPermission) or in-app consent ($hasConsent) denied.")
            unregisterCallLogObserver()
            return@withContext
        }
        val deviceCalls = DeviceContactsHelper.readDeviceCallLogs(context)
        if (deviceCalls.isNotEmpty()) {
            db.callLogDao().insertCallLogs(deviceCalls)
        }
    }

    // Flows from DAOs
    val allContacts: Flow<List<ContactEntity>> = db.contactDao().getAllContactsFlow()
    val favoriteContacts: Flow<List<ContactEntity>> = db.contactDao().getFavoriteContactsFlow()
    val allCallLogs: Flow<List<CallLogEntry>> = db.callLogDao().getAllCallLogsFlow()
    val recentCallLogs: Flow<List<CallLogEntry>> = db.callLogDao().getRecentCallsFlow(15)
    val blockedNumbers: Flow<List<BlockedNumberEntity>> = db.blockedNumberDao().getAllBlockedNumbersFlow()
    val backupHistory: Flow<List<BackupRecordEntity>> = db.backupDao().getAllBackupsFlow()
    val blockedCount: Flow<Int> = db.blockedNumberDao().getBlockedCountFlow()

    // Real-Time Spam Detection Engine & Database Flows
    val realtimeSpamDetector = RealtimeSpamDetector(context)
    val allSpamEntries: Flow<List<SpamEntryEntity>> = db.spamDao().getAllSpamFlow()
    val spamDatabaseCount: Flow<Int> = db.spamDao().getSpamCountFlow()
    val highRiskSpamCount: Flow<Int> = db.spamDao().getHighRiskCountFlow()
    val interceptedSpamCalls: Flow<List<InterceptedSpamCallEntity>> = db.spamDao().getInterceptedCallsFlow()

    // Post Call Quick Notes State
    private val _postCallNotes = MutableStateFlow<List<PostCallNote>>(emptyList())
    val postCallNotes: StateFlow<List<PostCallNote>> = _postCallNotes.asStateFlow()

    // Contact Call Posters State
    private val _callPosters = MutableStateFlow<Map<String, ContactCallPoster>>(emptyMap())
    val callPosters: StateFlow<Map<String, ContactCallPoster>> = _callPosters.asStateFlow()

    // Emergency Trigger & Settings Flow
    val emergencySettings: StateFlow<EmergencySettings> = emergencyTriggerManager.settings
    val isEmergencyServiceRunning: StateFlow<Boolean> = emergencyTriggerManager.isServiceRunning

    // App Lock State
    private val _isAppLocked = MutableStateFlow(checkInitialAppLock())
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _isAppLockEnabled = MutableStateFlow(checkInitialAppLock())
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled.asStateFlow()

    // Theme Mode State
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Emergency SOS Contacts
    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(loadEmergencyContacts())
    val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts.asStateFlow()

    // Speed Dial (2-9)
    private val _speedDialItems = MutableStateFlow<List<SpeedDialItem>>(loadSpeedDialItems())
    val speedDialItems: StateFlow<List<SpeedDialItem>> = _speedDialItems.asStateFlow()

    // Voicemail History
    private val _voicemailList = MutableStateFlow<List<VoicemailItem>>(loadInitialVoicemails())
    val voicemailList: StateFlow<List<VoicemailItem>> = _voicemailList.asStateFlow()

    // Quick SMS Auto-Reply Templates
    private val _quickSmsTemplates = MutableStateFlow<List<QuickSmsTemplate>>(loadQuickSmsTemplates())
    val quickSmsTemplates: StateFlow<List<QuickSmsTemplate>> = _quickSmsTemplates.asStateFlow()

    private fun loadEmergencyContacts(): List<EmergencyContact> {
        // 112 is the international GSM emergency number and is routed to local emergency services in most countries
        return listOf(
            EmergencyContact("em_1", "Emergency Services (Police / Fire / Ambulance)", "112", "National Emergency", isPrimary = true)
        )
    }

    private fun loadSpeedDialItems(): List<SpeedDialItem> {
        return emptyList()
    }

    private fun loadInitialVoicemails(): List<VoicemailItem> {
        return emptyList()
    }

    private fun loadQuickSmsTemplates(): List<QuickSmsTemplate> {
        return listOf(
            QuickSmsTemplate("tpl_1", "Driving", "I'm currently driving. I'll call you as soon as I arrive."),
            QuickSmsTemplate("tpl_2", "In a Meeting", "Can't talk right now. In a meeting, will call you back shortly."),
            QuickSmsTemplate("tpl_3", "At Work", "At work right now. Please text me if urgent."),
            QuickSmsTemplate("tpl_4", "Call Later", "Sorry, I missed your call. I will phone you back in a few minutes.")
        )
    }

    fun addEmergencyContact(contact: EmergencyContact) {
        _emergencyContacts.value = _emergencyContacts.value + contact
    }

    fun removeEmergencyContact(id: String) {
        _emergencyContacts.value = _emergencyContacts.value.filter { it.id != id }
    }

    fun updateEmergencySettings(settings: EmergencySettings) {
        emergencyTriggerManager.updateSettings(settings)
    }

    fun startEmergencyService() {
        emergencyTriggerManager.startEmergencyService()
    }

    fun stopEmergencyService() {
        emergencyTriggerManager.stopEmergencyService()
    }

    fun triggerEmergencySos(reason: String = "Manual User Trigger") {
        emergencyTriggerManager.triggerEmergency(reason, _emergencyContacts.value)
    }

    fun setSpeedDial(keyDigit: Char, contact: ContactEntity, phone: String) {
        val current = _speedDialItems.value.filter { it.keyDigit != keyDigit }
        _speedDialItems.value = current + SpeedDialItem(
            keyDigit = keyDigit,
            contactId = contact.id,
            contactName = contact.displayName,
            phoneNumber = phone
        )
    }

    fun removeSpeedDial(keyDigit: Char) {
        _speedDialItems.value = _speedDialItems.value.filter { it.keyDigit != keyDigit }
    }

    fun markVoicemailRead(id: String) {
        _voicemailList.value = _voicemailList.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }.distinctBy { it.id }
    }

    fun toggleVoicemailRead(id: String) {
        _voicemailList.value = _voicemailList.value.map {
            if (it.id == id) it.copy(isRead = !it.isRead) else it
        }.distinctBy { it.id }
    }

    fun deleteVoicemail(id: String) {
        _voicemailList.value = _voicemailList.value.filter { it.id != id }
    }

    fun clearAllVoicemails() {
        _voicemailList.value = emptyList()
    }

    fun refreshVoicemails() {
        val deviceVms = DeviceContactsHelper.readDeviceVoicemails(context)
        val current = _voicemailList.value
        
        // Merge device voicemails with current voicemails, strictly eliminating any duplicate values
        val merged = (deviceVms + current)
            .distinctBy {
                val cleanNumber = PhoneNumberUtils.normalize(it.senderNumber)
                val approxTime = it.timestamp / 10000 // 10-second cluster window for deduplication
                "${cleanNumber}_${approxTime}_${it.durationSeconds}"
            }
            .sortedByDescending { it.timestamp }

        _voicemailList.value = merged
    }

    fun getCarrierVoicemailNumber(): String {
        return try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_PHONE_STATE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                val vm = tm?.voiceMailNumber
                if (!vm.isNullOrBlank()) vm else "*86"
            } else {
                "*86"
            }
        } catch (e: Exception) {
            "*86"
        }
    }

    fun addQuickSmsTemplate(title: String, messageText: String) {
        _quickSmsTemplates.value = _quickSmsTemplates.value + QuickSmsTemplate(
            id = UUID.randomUUID().toString(),
            title = title,
            messageText = messageText
        )
    }

    fun addPostCallNote(phoneNumber: String, contactName: String?, noteText: String) {
        val note = PostCallNote(
            id = UUID.randomUUID().toString(),
            phoneNumber = phoneNumber,
            contactName = contactName,
            noteText = noteText,
            timestamp = System.currentTimeMillis()
        )
        _postCallNotes.value = listOf(note) + _postCallNotes.value
    }

    fun setCallPoster(phoneNumber: String, poster: ContactCallPoster) {
        _callPosters.value = _callPosters.value + (phoneNumber to poster)
    }

    fun getCallPosterForNumber(phoneNumber: String): ContactCallPoster {
        return _callPosters.value[phoneNumber] ?: ContactCallPoster(phoneNumber = phoneNumber, theme = CallPosterTheme.NEON_CYBER)
    }

    private fun loadThemeMode(): ThemeMode {
        val str = prefs.getString("theme_mode", "DAY") ?: "DAY"
        return try {
            when (str) {
                "DARK" -> ThemeMode.DARK
                "DEFAULT", "NAVY" -> ThemeMode.DEFAULT
                else -> ThemeMode.DAY // Day mode is the default light theme
            }
        } catch (e: Exception) {
            ThemeMode.DAY
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    // Phone Lock / Device Credential Detection
    fun isPhoneDeviceSecure(): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        return km?.isDeviceSecure == true
    }

    // Known Spam prefix or reported numbers
    private val KNOWN_SPAM_PREFIXES = listOf("+1800", "+1888", "+1877", "+1866", "140", "190")

    private fun checkInitialAppLock(): Boolean {
        return prefs.getBoolean("app_lock_enabled", false)
    }

    fun isAppLockConfigured(): Boolean {
        return prefs.getBoolean("app_lock_enabled", false)
    }

    fun getAppLockType(): String {
        return if (isPhoneDeviceSecure()) "PHONE_LOCK" else "DEVICE_PIN"
    }

    fun verifyPin(pin: String): Boolean {
        // Device credential authentication (KeyguardManager / BiometricPrompt) is used.
        // Direct PIN string matching without device system verification is disallowed.
        return false
    }

    fun verifyPattern(pattern: List<Int>): Boolean {
        // Device credential authentication (KeyguardManager / BiometricPrompt) is used.
        // Direct pattern matching without device system verification is disallowed.
        return false
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun setAppLock(
        enabled: Boolean,
        pin: String? = null,
        lockType: String = "PHONE_LOCK",
        pattern: List<Int>? = null,
        timeoutMinutes: Int = 1
    ) {
        val editor = prefs.edit().putBoolean("app_lock_enabled", enabled)
        editor.putString("app_lock_type", lockType)
        editor.putInt("app_lock_timeout", timeoutMinutes)
        editor.apply()
        _isAppLockEnabled.value = enabled
        if (!enabled) {
            _isAppLocked.value = false
        }
    }

    fun lockApp() {
        if (isAppLockConfigured()) {
            _isAppLocked.value = true
        }
    }

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("NovaDial_Salt_${pin}".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun syncWithDeviceContacts() = withContext(Dispatchers.IO) {
        val hasPermission = DeviceContactsHelper.hasContactsPermission(context)
        val hasConsent = getContactsSyncConsent()
        if (!hasPermission || !hasConsent) {
            android.util.Log.w("PrivacyAudit", "Data access blocked: Contacts permission ($hasPermission) or in-app consent ($hasConsent) denied.")
            return@withContext
        }
        val deviceContacts = DeviceContactsHelper.readDeviceContacts(context)
        if (deviceContacts.isNotEmpty()) {
            val suppressedMappings = db.contactDao().getAllMergedMappings()
            val suppressedIds = suppressedMappings.map { it.suppressedId }.toSet()
            val primaryIdsWithMappings = suppressedMappings.map { it.primaryId }.toSet()

            val filteredContacts = deviceContacts.filter { !suppressedIds.contains(it.id) }
            for (contact in filteredContacts) {
                if (primaryIdsWithMappings.contains(contact.id)) {
                    val existingPrimary = db.contactDao().getContactById(contact.id)
                    if (existingPrimary != null) {
                        val merged = DuplicateDetector.mergeContacts(listOf(existingPrimary, contact))
                        db.contactDao().insertContact(merged)
                        continue
                    }
                }
                db.contactDao().insertContact(contact)
            }
        }

        val hasCallPerm = DeviceContactsHelper.hasCallLogPermission(context)
        val hasCallConsent = getCallLogsSyncConsent()
        if (hasCallPerm && hasCallConsent) {
            val deviceCalls = DeviceContactsHelper.readDeviceCallLogs(context)
            if (deviceCalls.isNotEmpty()) {
                db.callLogDao().insertCallLogs(deviceCalls)
            }
        }
    }

    suspend fun saveContact(
        id: String?,
        displayName: String,
        phoneNumbers: List<PhoneNumberItem>,
        emails: List<com.example.data.model.EmailItem> = emptyList(),
        notes: String = "",
        company: String = "",
        jobTitle: String = "",
        address: String = "",
        ringtone: String = "",
        birthday: String = "",
        nickname: String = "",
        phoneticName: String = "",
        website: String = "",
        pronouns: String = "",
        relationship: String = "",
        sipAddress: String = "",
        messageTone: String = "",
        vibrationPattern: String = "",
        department: String = "",
        middleName: String = "",
        suffix: String = "",
        prefix: String = "",
        groups: List<String> = emptyList(),
        isStarred: Boolean = false,
        photoUri: String? = null
    ) = withContext(Dispatchers.IO) {
        val contactId = id ?: UUID.randomUUID().toString()
        val encryptedNotes = if (notes.isNotBlank()) CryptoManager.encrypt(notes) else ""

        val entity = ContactEntity(
            id = contactId,
            displayName = displayName.trim(),
            photoUri = photoUri,
            isStarred = isStarred,
            source = ContactSource.NOVADIAL,
            company = company.trim(),
            jobTitle = jobTitle.trim(),
            address = address.trim(),
            ringtone = ringtone.trim(),
            birthday = birthday.trim(),
            nickname = nickname.trim(),
            phoneticName = phoneticName.trim(),
            website = website.trim(),
            pronouns = pronouns.trim(),
            relationship = relationship.trim(),
            sipAddress = sipAddress.trim(),
            messageTone = messageTone.trim(),
            vibrationPattern = vibrationPattern.trim(),
            department = department.trim(),
            middleName = middleName.trim(),
            suffix = suffix.trim(),
            prefix = prefix.trim(),
            notes = encryptedNotes,
            phoneNumbersJson = MoshiHolder.phoneNumbersToJson(phoneNumbers),
            emailsJson = MoshiHolder.emailsToJson(emails),
            groupsJson = MoshiHolder.groupsToJson(groups),
            updatedAt = System.currentTimeMillis()
        )
        db.contactDao().insertContact(entity)
        try {
            if (DeviceContactsHelper.hasWriteContactsPermission(context)) {
                DeviceContactsHelper.insertDeviceContact(context, entity)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun decryptContactNotes(notesCipher: String): String {
        return try {
            if (notesCipher.isBlank()) "" else CryptoManager.decrypt(notesCipher)
        } catch (e: Exception) {
            notesCipher // fallback to plaintext if previously unencrypted
        }
    }

    suspend fun toggleFavorite(contact: ContactEntity) = withContext(Dispatchers.IO) {
        db.contactDao().updateContact(contact.copy(isStarred = !contact.isStarred))
    }

    suspend fun deleteContact(contactId: String) = withContext(Dispatchers.IO) {
        db.contactDao().deleteContactById(contactId)
        try {
            if (DeviceContactsHelper.hasWriteContactsPermission(context)) {
                DeviceContactsHelper.deleteDeviceContact(context, contactId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteContacts(contactIds: List<String>) = withContext(Dispatchers.IO) {
        for (id in contactIds) {
            db.contactDao().deleteContactById(id)
            try {
                if (DeviceContactsHelper.hasWriteContactsPermission(context)) {
                    DeviceContactsHelper.deleteDeviceContact(context, id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun assignGroupToContacts(contactIds: List<String>, groupName: String) = withContext(Dispatchers.IO) {
        for (id in contactIds) {
            val contact = db.contactDao().getContactById(id)
            if (contact != null) {
                val currentGroups = contact.getGroups().toMutableList()
                if (!currentGroups.contains(groupName)) {
                    currentGroups.add(groupName)
                    val updated = contact.copy(
                        groupsJson = MoshiHolder.groupsToJson(currentGroups),
                        isStarred = if (groupName == "Favorites") true else contact.isStarred
                    )
                    db.contactDao().updateContact(updated)
                }
            }
        }
    }

    suspend fun getContactById(id: String): ContactEntity? = withContext(Dispatchers.IO) {
        db.contactDao().getContactById(id)
    }

    suspend fun recordCallLog(
        number: String,
        name: String?,
        type: CallType,
        durationSeconds: Long,
        simSlot: Int = 1
    ) = withContext(Dispatchers.IO) {
        val normalized = PhoneNumberUtils.normalize(number)
        val isBlocked = db.blockedNumberDao().isNumberBlocked(normalized) > 0
        val isSpam = isBlocked || isSuspectedSpam(normalized)

        val log = CallLogEntry(
            number = number,
            normalizedNumber = normalized,
            callerName = name,
            callType = if (isBlocked) CallType.BLOCKED else type,
            timestamp = System.currentTimeMillis(),
            durationSeconds = durationSeconds,
            simSlot = simSlot,
            isSpam = isSpam,
            spamReason = if (isBlocked) "Blocked Contact" else if (isSpam) "Robocall / Telemarketing Pattern" else null
        )
        db.callLogDao().insertCallLog(log)
        if (isBlocked) {
            db.blockedNumberDao().incrementBlockCount(normalized)
        }
    }

    suspend fun isSuspectedSpam(number: String): Boolean = withContext(Dispatchers.IO) {
        val verdict = realtimeSpamDetector.screenIncomingCallerId(number)
        verdict.isSpam || verdict.isBlocked
    }

    suspend fun screenCallerId(number: String): SpamDetectionVerdict = withContext(Dispatchers.IO) {
        realtimeSpamDetector.screenIncomingCallerId(number)
    }

    suspend fun addSpamEntry(entry: SpamEntryEntity) = withContext(Dispatchers.IO) {
        db.spamDao().insertSpam(entry)
    }

    suspend fun addNumberToSpam(number: String, name: String? = null, category: com.example.data.model.SpamCallerCategory = com.example.data.model.SpamCallerCategory.SPAM_OTHER, reason: String = "User Reported Spam") = withContext(Dispatchers.IO) {
        val norm = PhoneNumberUtils.normalize(number)
        val entry = SpamEntryEntity(
            id = "user_spam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            phoneNumber = number,
            normalizedNumber = norm,
            callerName = name?.ifBlank { null } ?: "Reported Spam Caller",
            category = category,
            riskScore = 90,
            reportCount = 1,
            source = "User Reported",
            recommendedAction = com.example.data.model.BlockAction.WARN_HEADS_UP,
            threatDetails = reason,
            patternType = "EXACT",
            addedAt = System.currentTimeMillis(),
            isUserReported = true
        )
        db.spamDao().insertSpam(entry)
    }

    suspend fun removeNumberFromSpam(number: String) = withContext(Dispatchers.IO) {
        val norm = PhoneNumberUtils.normalize(number)
        db.spamDao().deleteSpamByNumber(norm)
        db.spamDao().deleteSpamByNumber(number)
    }

    suspend fun isSpamNumber(number: String): Boolean = withContext(Dispatchers.IO) {
        val norm = PhoneNumberUtils.normalize(number)
        db.spamDao().getSpamByNormalizedNumber(norm) != null
    }

    suspend fun deleteSpamEntry(id: String) = withContext(Dispatchers.IO) {
        db.spamDao().deleteSpamById(id)
    }

    /** Earlier versions shipped about 20 invented "known spam" numbers. They are removed; only your own rules stay. */
    suspend fun removeBuiltInSampleSpamEntries() = withContext(Dispatchers.IO) {
        db.spamDao().deleteBuiltInSampleEntries()
    }

    suspend fun clearInterceptedCalls() = withContext(Dispatchers.IO) {
        db.spamDao().clearInterceptedCalls()
    }

    suspend fun blockNumber(number: String, reason: String = "Blocked by User") = withContext(Dispatchers.IO) {
        val norm = PhoneNumberUtils.normalize(number)
        db.blockedNumberDao().insertBlockedNumber(
            BlockedNumberEntity(
                number = number,
                normalizedNumber = norm,
                reason = reason,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun unblockNumber(number: String) = withContext(Dispatchers.IO) {
        db.blockedNumberDao().deleteBlockedNumber(number)
    }

    suspend fun deleteCallLog(id: Long) = withContext(Dispatchers.IO) {
        db.callLogDao().deleteCallLogById(id)
    }

    suspend fun clearCallHistory() = withContext(Dispatchers.IO) {
        db.callLogDao().clearAllCallLogs()
    }

    suspend fun findDuplicates(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val all = db.contactDao().getAllContactsList()
        DuplicateDetector.findDuplicates(all)
    }

    suspend fun mergeDuplicateGroup(group: DuplicateGroup) = withContext(Dispatchers.IO) {
        // 1. Persist merge in Android system Contacts Provider
        try {
            if (DeviceContactsHelper.hasWriteContactsPermission(context)) {
                DeviceContactsHelper.persistMergeInDeviceContacts(context, group.mergedPreview, group.contacts)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Persist merge mappings and delete secondary contacts in local Room database
        for (contact in group.contacts) {
            if (contact.id != group.mergedPreview.id) {
                db.contactDao().insertMergedMapping(
                    com.example.data.model.MergedContactMappingEntity(
                        suppressedId = contact.id,
                        primaryId = group.mergedPreview.id
                    )
                )
                db.contactDao().deleteContactById(contact.id)
            }
        }
        db.contactDao().insertContact(group.mergedPreview)
    }

    suspend fun mergeAllDuplicates(): Int = withContext(Dispatchers.IO) {
        val groups = findDuplicates()
        for (group in groups) {
            mergeDuplicateGroup(group)
        }
        groups.size
    }

    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        val count = db.contactDao().getContactCount()
        if (count == 0 && DeviceContactsHelper.hasContactsPermission(context)) {
            syncWithDeviceContacts()
        }
        // no built-in spam list: remove the invented sample entries that older versions saved
        removeBuiltInSampleSpamEntries()
    }
}
