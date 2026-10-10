package com.example.ui

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.util.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CallHistoryFilter {
    ALL,
    MISSED,
    INCOMING,
    OUTGOING
}

data class CallStats(
    val totalCalls: Int = 0,
    val missedCalls: Int = 0,
    val totalDurationFormatted: String = "0m",
    val mostFrequentCaller: String? = null
)

/**
 * CallLogViewModel: Utilizes Android ContentResolver to fetch, filter, and manage
 * native device call logs asynchronously via Coroutines on Dispatchers.IO.
 */
class CallLogViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    private val _rawCallLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val rawCallLogs: StateFlow<List<CallLogEntry>> = _rawCallLogs.asStateFlow()

    private val _filter = MutableStateFlow(CallHistoryFilter.ALL)
    val filter: StateFlow<CallHistoryFilter> = _filter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    // Filtered Call Logs combining search query, filter type, and raw call logs
    val displayedCallLogs: StateFlow<List<CallLogEntry>> = combine(
        _rawCallLogs,
        _filter,
        _searchQuery
    ) { logs, activeFilter, query ->
        var list = when (activeFilter) {
            CallHistoryFilter.ALL -> logs
            CallHistoryFilter.MISSED -> logs.filter { it.callType == CallType.MISSED || it.callType == CallType.REJECTED }
            CallHistoryFilter.INCOMING -> logs.filter { it.callType == CallType.INCOMING }
            CallHistoryFilter.OUTGOING -> logs.filter { it.callType == CallType.OUTGOING }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.callerName?.lowercase()?.contains(q) == true ||
                        it.number.contains(q) ||
                        PhoneNumberUtils.matchesT9(q, it.callerName ?: "", listOf(it.number))
            }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Summary call statistics
    val callStats: StateFlow<CallStats> = _rawCallLogs.combine(_rawCallLogs) { logs, _ ->
        if (logs.isEmpty()) {
            CallStats()
        } else {
            val missed = logs.count { it.callType == CallType.MISSED }
            val totalSec = logs.sumOf { it.durationSeconds }
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val durFormatted = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

            val topCaller = logs
                .groupBy { it.callerName?.takeIf { name -> name.isNotBlank() } ?: it.number }
                .maxByOrNull { it.value.size }?.key

            CallStats(
                totalCalls = logs.size,
                missedCalls = missed,
                totalDurationFormatted = durFormatted,
                mostFrequentCaller = topCaller
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CallStats())

    init {
        checkPermissionAndLoad()
    }

    fun checkPermissionAndLoad() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        _permissionGranted.value = granted
        if (granted) {
            loadDeviceCallLogs()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        if (granted) {
            loadDeviceCallLogs()
        }
    }

    fun setFilter(filter: CallHistoryFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Queries Android ContentResolver for CallLog.Calls in a background IO thread
     */
    fun loadDeviceCallLogs() {
        viewModelScope.launch {
            _isLoading.value = true
            val logs = fetchCallLogsFromContentResolver(context.contentResolver)
            _rawCallLogs.value = logs
            _isLoading.value = false
        }
    }

    private suspend fun fetchCallLogsFromContentResolver(contentResolver: ContentResolver): List<CallLogEntry> {
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<CallLogEntry>()
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            try {
                contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC LIMIT 150"
                )?.use { cursor ->
                    val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                    val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)

                    while (cursor.moveToNext()) {
                        val num = cursor.getString(numIdx) ?: continue
                        val name = cursor.getString(nameIdx)
                        val typeVal = cursor.getInt(typeIdx)
                        val date = cursor.getLong(dateIdx)
                        val dur = cursor.getLong(durIdx)

                        val callType = when (typeVal) {
                            CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                            CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                            CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                            CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                            CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                            else -> CallType.INCOMING
                        }

                        list.add(
                            CallLogEntry(
                                number = num,
                                normalizedNumber = PhoneNumberUtils.normalize(num),
                                callerName = name,
                                callType = callType,
                                timestamp = date,
                                durationSeconds = dur
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            list
        }
    }
}
