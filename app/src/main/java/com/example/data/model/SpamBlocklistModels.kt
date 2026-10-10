package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class BlockAction(val label: String, val description: String) {
    AUTO_BLOCK("Auto-Block & Reject", "Drops incoming call before phone rings"),
    SILENCE_RINGTONE("Silence Ringtone", "Rings silently without vibrating or audio"),
    WARN_HEADS_UP("Heads-Up Warning", "Allows ring with prominent high-risk spam alert")
}

enum class ThreatLevel(val label: String, val badgeColorHex: Long) {
    CLEAN("Verified / Clean", 0xFF4CAF50),
    LOW_RISK("Low Risk", 0xFF8BC34A),
    SUSPICIOUS("Suspicious", 0xFFFF9800),
    HIGH_RISK_SCAM("High-Risk Scam", 0xFFF44336),
    USER_BLOCKED("User Blocked", 0xFF9C27B0)
}

@Entity(
    tableName = "spam_entries",
    indices = [
        Index(value = ["normalizedNumber"]),
        Index(value = ["category"]),
        Index(value = ["riskScore"])
    ]
)
data class SpamEntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val phoneNumber: String,
    val normalizedNumber: String,
    val callerName: String,
    val category: SpamCallerCategory,
    val riskScore: Int, // 0..100
    val reportCount: Int = 1,
    val source: String = "Global Threat Intelligence",
    val recommendedAction: BlockAction = BlockAction.AUTO_BLOCK,
    val threatDetails: String = "Reported by community users",
    val patternType: String = "EXACT", // "EXACT", "PREFIX", "WILDCARD"
    val addedAt: Long = System.currentTimeMillis(),
    val isUserReported: Boolean = false
)

@Entity(
    tableName = "intercepted_spam_calls",
    indices = [Index(value = ["timestamp"])]
)
data class InterceptedSpamCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val phoneNumber: String,
    val callerName: String,
    val category: SpamCallerCategory,
    val riskScore: Int,
    val actionTaken: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SpamDetectionVerdict(
    val phoneNumber: String,
    val isSpam: Boolean,
    val isBlocked: Boolean,
    val riskScore: Int,
    val threatLevel: ThreatLevel,
    val category: SpamCallerCategory,
    val callerName: String,
    val source: String,
    val action: BlockAction,
    val reason: String,
    val scanLatencyMs: Long = 2L
)
