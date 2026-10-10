package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BackupRecordEntity
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.data.model.ContactEntity
import com.example.data.model.EmailItem
import com.example.data.model.PhoneNumberItem
import com.example.data.model.SimAccount
import com.example.data.repository.NovaDialRepository
import com.example.data.sync.BackupManager
import com.example.data.sync.DuplicateGroup
import com.example.data.sync.GoogleSyncInfo
import com.example.data.sync.RestorePreview
import com.example.telecom.ActiveCallSession
import com.example.telecom.CallSessionState
import com.example.ui.theme.ThemeMode
import com.example.util.PhoneNumberUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    object Recents : Screen()
    object Dialer : Screen()
    object Favorites : Screen()
    object Contacts : Screen()
    object ImportedContacts : Screen()
    data class ContactDetail(val contactId: String) : Screen()
    data class EditContact(val contactId: String? = null) : Screen()
    object DuplicateReview : Screen()
    object Backup : Screen()
    object Settings : Screen()
    object Analytics : Screen()
    object Emergency : Screen()
    object SpamBlocklistManager : Screen()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = NovaDialRepository(application)

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Navigation back stack
    private val backStack = mutableListOf<Screen>()

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            backStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (backStack.isNotEmpty()) {
            _currentScreen.value = backStack.removeAt(backStack.size - 1)
            return true
        }
        return false
    }

    // Call Manager State
    val activeCallSession: StateFlow<ActiveCallSession> = repository.callManager.activeSession
    val simAccounts: StateFlow<List<SimAccount>> = repository.callManager.simAccounts
    val selectedSimSlot = MutableStateFlow(1)

    // Default Phone/Dialer Role State
    private val _isDefaultDialer = MutableStateFlow(false)
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    fun checkDefaultDialer(context: Context) {
        _isDefaultDialer.value = com.example.telecom.DefaultDialerManager.isDefaultDialer(context)
    }

    fun requestDefaultDialer(context: Context) {
        val launched = com.example.telecom.DefaultDialerManager.requestDefaultDialer(context)
        if (!launched) {
            showStatus("Please set NovaDial as default in Android Settings > Apps > Default Apps")
        }
    }

    // Data streams
    val allContacts: StateFlow<List<ContactEntity>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteContacts: StateFlow<List<ContactEntity>> = repository.favoriteContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCallLogs: StateFlow<List<CallLogEntry>> = repository.allCallLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentCallLogs: StateFlow<List<CallLogEntry>> = repository.recentCallLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedCount: StateFlow<Int> = repository.blockedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val googleSyncInfo: StateFlow<GoogleSyncInfo> = repository.googleSyncManager.syncInfo

    val backupHistory: StateFlow<List<BackupRecordEntity>> = repository.backupHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Emergency SOS & Speed Dial & Voicemail
    val emergencyContacts = repository.emergencyContacts
    val emergencySettings = repository.emergencySettings
    val isEmergencyServiceRunning = repository.isEmergencyServiceRunning
    val speedDialItems = repository.speedDialItems
    val voicemailList = repository.voicemailList
    val quickSmsTemplates = repository.quickSmsTemplates

    // Advanced Features Managers & State
    val spamProtectionSettings = repository.communitySpamManager.settings
    val simRules = repository.communitySpamManager.simRules
    val flashAlertSettings = repository.flashAlertManager.settings
    val gestureSettings = repository.gestureControlManager.settings
    val postCallNotes = repository.postCallNotes
    val callPosters = repository.callPosters

    val postCallSheetSession = MutableStateFlow<ActiveCallSession?>(null)

    fun submitSpamReport(phoneNumber: String, category: com.example.data.model.SpamCallerCategory, comment: String) {
        repository.communitySpamManager.submitSpamReport(phoneNumber, category, comment)
        showStatus("Spam report submitted for $phoneNumber")
    }

    fun addSimRule(rule: com.example.data.model.MultiSimRule) {
        repository.communitySpamManager.addSimRule(rule)
        showStatus("Multi-SIM carrier rule added")
    }

    fun toggleSimRule(id: String, enabled: Boolean) {
        repository.communitySpamManager.toggleSimRule(id, enabled)
    }

    fun removeSimRule(id: String) {
        repository.communitySpamManager.removeSimRule(id)
    }

    fun updateFlashSettings(settings: com.example.data.model.FlashAlertSettings) {
        repository.flashAlertManager.updateSettings(settings)
    }

    fun updateGestureSettings(settings: com.example.data.model.GestureSettings) {
        repository.gestureControlManager.updateSettings(settings)
    }

    fun addPostCallNote(phoneNumber: String, contactName: String?, noteText: String) {
        repository.addPostCallNote(phoneNumber, contactName, noteText)
        showStatus("Note saved for $phoneNumber")
    }

    fun setCallPoster(phoneNumber: String, poster: com.example.data.model.ContactCallPoster) {
        repository.setCallPoster(phoneNumber, poster)
        showStatus("Call poster updated for $phoneNumber")
    }

    fun triggerEndCall() {
        val (endedSession, _) = repository.callManager.endCall()
        if (repository.communitySpamManager.settings.value.showCallerIdOverlay && endedSession.number.isNotBlank()) {
            postCallSheetSession.value = endedSession
        }
    }

    fun dismissPostCallSheet() {
        postCallSheetSession.value = null
    }

    // Real-Time Spam Detection Service & Local Blocklist Manager State
    val allSpamEntries: StateFlow<List<com.example.data.model.SpamEntryEntity>> = repository.allSpamEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val spamDatabaseCount: StateFlow<Int> = repository.spamDatabaseCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val highRiskSpamCount: StateFlow<Int> = repository.highRiskSpamCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val interceptedSpamCalls: StateFlow<List<com.example.data.model.InterceptedSpamCallEntity>> = repository.interceptedSpamCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Screening Tester state
    private val _testVerdict = MutableStateFlow<com.example.data.model.SpamDetectionVerdict?>(null)
    val testVerdict: StateFlow<com.example.data.model.SpamDetectionVerdict?> = _testVerdict.asStateFlow()
    private val _isTestingNumber = MutableStateFlow(false)
    val isTestingNumber: StateFlow<Boolean> = _isTestingNumber.asStateFlow()

    fun testScreenNumber(rawNumber: String) {
        if (rawNumber.isBlank()) return
        viewModelScope.launch {
            _isTestingNumber.value = true
            val verdict = repository.screenCallerId(rawNumber)
            _testVerdict.value = verdict
            _isTestingNumber.value = false
        }
    }

    fun clearTestVerdict() {
        _testVerdict.value = null
    }

    fun addCustomSpamEntry(
        number: String,
        name: String,
        category: com.example.data.model.SpamCallerCategory,
        riskScore: Int,
        action: com.example.data.model.BlockAction,
        reason: String
    ) {
        viewModelScope.launch {
            val entry = com.example.data.model.SpamEntryEntity(
                phoneNumber = number,
                normalizedNumber = PhoneNumberUtils.normalize(number),
                callerName = name.ifBlank { "Flagged Caller" },
                category = category,
                riskScore = riskScore,
                reportCount = 1,
                source = "Custom Blocklist",
                recommendedAction = action,
                threatDetails = reason.ifBlank { "User reported spam" },
                isUserReported = true
            )
            repository.addSpamEntry(entry)
            showStatus("Added $number to spam database")
        }
    }

    fun removeSpamEntry(id: String) {
        viewModelScope.launch {
            repository.deleteSpamEntry(id)
            showStatus("Removed from spam database")
        }
    }

    fun clearInterceptedCalls() {
        viewModelScope.launch {
            repository.clearInterceptedCalls()
            showStatus("Intercepted call logs cleared")
        }
    }

    // Dialer State
    val dialerQuery = MutableStateFlow("")
    val dialerMatchedContacts = combine(dialerQuery, allContacts) { query, contacts ->
        if (query.isBlank()) {
            emptyList()
        } else {
            contacts.filter { contact ->
                val phoneNumbers = contact.getPhoneNumbers().map { it.number }
                PhoneNumberUtils.matchesT9(query, contact.displayName, phoneNumbers)
            }.take(8)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Duplicates State
    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    // Backup & Restore State
    private val _restorePreview = MutableStateFlow<RestorePreview?>(null)
    val restorePreview: StateFlow<RestorePreview?> = _restorePreview.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // App Lock
    val isAppLocked: StateFlow<Boolean> = repository.isAppLocked
    val isAppLockEnabled: StateFlow<Boolean> = repository.isAppLockEnabled

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
            refreshDuplicates()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showStatus(msg: String) {
        _statusMessage.value = msg
    }

    // Dialer Actions
    fun appendDialerDigit(char: Char) {
        dialerQuery.value = dialerQuery.value + char
        repository.callManager.playDtmf(char)
    }

    fun deleteDialerDigit() {
        if (dialerQuery.value.isNotEmpty()) {
            dialerQuery.value = dialerQuery.value.dropLast(1)
        }
    }

    fun clearDialer() {
        dialerQuery.value = ""
    }

    fun setDialerNumber(number: String) {
        dialerQuery.value = number
    }

    // Theme Mode
    val themeMode: StateFlow<ThemeMode> = repository.themeMode

    fun setThemeMode(mode: ThemeMode) {
        repository.setThemeMode(mode)
    }

    // Call Execution
    fun initiateCall(number: String, contactName: String? = null) {
        if (number.isBlank()) return
        viewModelScope.launch {
            val autoSimSlot = repository.communitySpamManager.determineSimSlotForNumber(number)
            val effectiveSimSlot = if (selectedSimSlot.value != 1) selectedSimSlot.value else autoSimSlot

            val verdict = repository.screenCallerId(number)
            val callerId = repository.communitySpamManager.resolveCallerIdentity(number, contactName)
            val isSpam = verdict.isSpam || verdict.isBlocked || callerId.spamScore >= 60
            val resolvedName = contactName ?: if (verdict.isSpam) verdict.callerName else callerId.name

            repository.callManager.startCall(
                number = number,
                name = resolvedName,
                simSlot = effectiveSimSlot,
                isSpam = isSpam
            )
            repository.recordCallLog(
                number = number,
                name = resolvedName,
                type = CallType.OUTGOING,
                durationSeconds = 0L,
                simSlot = effectiveSimSlot
            )
        }
    }

    fun endActiveCall() {
        viewModelScope.launch {
            val (session, duration) = repository.callManager.endCall()
            if (session.number.isNotBlank()) {
                repository.recordCallLog(
                    number = session.number,
                    name = session.displayName,
                    type = CallType.OUTGOING,
                    durationSeconds = duration,
                    simSlot = session.simSlot
                )
                if (repository.communitySpamManager.settings.value.showCallerIdOverlay) {
                    postCallSheetSession.value = session
                }
            }
        }
    }

    // Contacts
    fun toggleFavorite(contact: ContactEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(contact)
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            repository.deleteContact(contactId)
            refreshDuplicates()
            showStatus("Contact removed.")
        }
    }

    fun deleteContacts(contactIds: List<String>) {
        viewModelScope.launch {
            repository.deleteContacts(contactIds)
            refreshDuplicates()
            showStatus("${contactIds.size} contacts deleted.")
        }
    }

    fun assignGroupToContacts(contactIds: List<String>, groupName: String) {
        viewModelScope.launch {
            repository.assignGroupToContacts(contactIds, groupName)
            showStatus("Added ${contactIds.size} contacts to $groupName.")
        }
    }

    fun refreshDuplicates() {
        viewModelScope.launch {
            _duplicateGroups.value = repository.findDuplicates()
        }
    }

    fun mergeDuplicate(group: DuplicateGroup) {
        viewModelScope.launch {
            repository.mergeDuplicateGroup(group)
            refreshDuplicates()
            showStatus("Successfully merged contacts into ${group.mergedPreview.displayName}")
        }
    }

    fun mergeAllDuplicates() {
        viewModelScope.launch {
            val count = repository.mergeAllDuplicates()
            refreshDuplicates()
            showStatus("Merged all $count duplicate sets successfully.")
        }
    }

    // Call Log Actions
    fun deleteCallLog(id: Long) {
        viewModelScope.launch {
            repository.deleteCallLog(id)
        }
    }

    fun blockNumber(number: String, reason: String = "Blocked by User") {
        viewModelScope.launch {
            repository.blockNumber(number, reason)
            showStatus("Blocked $number from future calls.")
        }
    }

    fun unblockNumber(number: String) {
        viewModelScope.launch {
            repository.unblockNumber(number)
            showStatus("Unblocked $number.")
        }
    }

    fun addNumberToSpam(number: String, name: String? = null, category: com.example.data.model.SpamCallerCategory = com.example.data.model.SpamCallerCategory.SPAM_OTHER, reason: String = "Reported as Spam") {
        viewModelScope.launch {
            repository.addNumberToSpam(number, name, category, reason)
            showStatus("Added $number to Spam list")
        }
    }

    fun removeNumberFromSpam(number: String) {
        viewModelScope.launch {
            repository.removeNumberFromSpam(number)
            showStatus("Removed $number from Spam list")
        }
    }

    fun clearCallHistory() {
        viewModelScope.launch {
            repository.clearCallHistory()
            showStatus("Call logs cleared.")
        }
    }

    // Backup actions
    fun createBackup() {
        viewModelScope.launch {
            try {
                val record = BackupManager.createLocalEncryptedBackup(getApplication())
                showStatus("Encrypted backup created (${record.recordCount} contacts).")
            } catch (e: Exception) {
                showStatus("Backup failed: ${e.message}")
            }
        }
    }

    fun previewBackup(backupId: String) {
        viewModelScope.launch {
            try {
                val preview = BackupManager.previewBackup(getApplication(), backupId)
                _restorePreview.value = preview
            } catch (e: Exception) {
                showStatus("Cannot read backup: ${e.message}")
            }
        }
    }

    fun confirmRestore(replaceExisting: Boolean) {
        val preview = _restorePreview.value ?: return
        viewModelScope.launch {
            try {
                BackupManager.restoreFromContainer(getApplication(), preview.container, replaceExisting)
                _restorePreview.value = null
                refreshDuplicates()
                showStatus("Restore completed successfully.")
            } catch (e: Exception) {
                showStatus("Restore error: ${e.message}")
            }
        }
    }

    fun dismissRestorePreview() {
        _restorePreview.value = null
    }

    // Google Sync
    fun connectGoogleAccount(email: String) {
        repository.googleSyncManager.connectGoogleAccount(email)
        showStatus("Connected to Google: $email")
    }

    fun disconnectGoogleAccount() {
        repository.googleSyncManager.disconnectGoogleAccount()
        showStatus("Google Account disconnected.")
    }

    fun syncGoogleContacts() {
        viewModelScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(getApplication())
            val result = repository.googleSyncManager.performSync(db)
            if (result.isSuccess) {
                showStatus("Synchronized ${result.getOrNull()} contacts with Google.")
            } else {
                showStatus("Google Sync issue: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    // App Lock
    fun verifyPin(pin: String): Boolean {
        return repository.verifyPin(pin)
    }

    fun verifyPattern(pattern: List<Int>): Boolean {
        return repository.verifyPattern(pattern)
    }

    fun unlockApp() {
        repository.unlockApp()
    }

    fun getAppLockType(): String {
        return repository.getAppLockType()
    }

    fun setAppLock(
        enabled: Boolean,
        pin: String? = null,
        lockType: String = "PIN",
        pattern: List<Int>? = null
    ) {
        repository.setAppLock(enabled, pin, lockType, pattern)
        showStatus(if (enabled) "App Lock enabled with $lockType security." else "App Lock disabled.")
    }

    fun lockApp() {
        repository.lockApp()
    }

    // Voicemail Actions
    fun refreshVoicemails() {
        viewModelScope.launch {
            repository.refreshVoicemails()
        }
    }

    fun deleteVoicemail(id: String) {
        repository.deleteVoicemail(id)
        showStatus("Voicemail removed")
    }

    fun markVoicemailRead(id: String) {
        repository.markVoicemailRead(id)
    }

    fun toggleVoicemailRead(id: String) {
        repository.toggleVoicemailRead(id)
    }

    fun getCarrierVoicemailNumber(): String {
        return repository.getCarrierVoicemailNumber()
    }

    // Emergency Service Actions
    fun updateEmergencySettings(settings: com.example.telecom.EmergencySettings) {
        repository.updateEmergencySettings(settings)
        showStatus("Emergency settings updated.")
    }

    fun triggerEmergencySos(reason: String = "Manual User Trigger") {
        repository.triggerEmergencySos(reason)
    }
}
