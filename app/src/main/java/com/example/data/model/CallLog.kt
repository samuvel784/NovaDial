package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED
}

@Entity(tableName = "call_logs")
data class CallLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val number: String,
    val normalizedNumber: String,
    val callerName: String? = null,
    val callType: CallType,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val simSlot: Int = 1,
    val isSpam: Boolean = false,
    val spamReason: String? = null
) {
    fun formattedDuration(): String {
        if (durationSeconds <= 0) return "0s"
        val mins = durationSeconds / 60
        val secs = durationSeconds % 60
        return if (mins > 0) {
            "${mins}m ${secs}s"
        } else {
            "${secs}s"
        }
    }
}
