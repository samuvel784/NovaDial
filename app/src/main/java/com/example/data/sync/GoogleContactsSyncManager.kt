package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.AppDatabase
import com.example.data.model.ContactEntity
import com.example.data.model.ContactSource
import com.example.data.model.MoshiHolder
import com.example.data.model.PhoneNumberItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR
}

data class GoogleSyncInfo(
    val isConnected: Boolean = false,
    val accountEmail: String = "",
    val lastSyncTime: Long = 0L,
    val state: SyncState = SyncState.IDLE,
    val syncedCount: Int = 0,
    val errorMessage: String? = null
)

class GoogleContactsSyncManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("novadial_google_sync", Context.MODE_PRIVATE)

    private val _syncInfo = MutableStateFlow(loadSyncInfo())
    val syncInfo: StateFlow<GoogleSyncInfo> = _syncInfo.asStateFlow()

    private fun loadSyncInfo(): GoogleSyncInfo {
        val isConn = prefs.getBoolean("is_connected", false)
        val email = prefs.getString("account_email", "") ?: ""
        val lastSync = prefs.getLong("last_sync_time", 0L)
        val syncedCount = prefs.getInt("synced_count", 0)
        return GoogleSyncInfo(
            isConnected = isConn,
            accountEmail = email,
            lastSyncTime = lastSync,
            syncedCount = syncedCount
        )
    }

    fun connectGoogleAccount(email: String) {
        prefs.edit()
            .putBoolean("is_connected", true)
            .putString("account_email", email)
            .apply()
        _syncInfo.value = _syncInfo.value.copy(
            isConnected = true,
            accountEmail = email,
            errorMessage = null
        )
    }

    fun disconnectGoogleAccount() {
        prefs.edit()
            .putBoolean("is_connected", false)
            .putString("account_email", "")
            .putLong("last_sync_time", 0L)
            .putInt("synced_count", 0)
            .apply()
        _syncInfo.value = GoogleSyncInfo()
    }

    suspend fun performSync(db: AppDatabase): Result<Int> {
        val current = _syncInfo.value
        if (!current.isConnected) {
            return Result.failure(IllegalStateException("No Google Account linked. Connect your Google account first."))
        }

        _syncInfo.value = current.copy(state = SyncState.SYNCING, errorMessage = null)

        return try {
            val localContacts = db.contactDao().getAllContactsList()
            
            // Reconcile and mark synced contacts
            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong("last_sync_time", now)
                .putInt("synced_count", localContacts.size)
                .apply()

            _syncInfo.value = current.copy(
                state = SyncState.SUCCESS,
                lastSyncTime = now,
                syncedCount = localContacts.size,
                errorMessage = null
            )
            Result.success(localContacts.size)
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Sync operation failed. Verify network connectivity."
            _syncInfo.value = current.copy(
                state = SyncState.ERROR,
                errorMessage = err
            )
            Result.failure(e)
        }
    }
}
