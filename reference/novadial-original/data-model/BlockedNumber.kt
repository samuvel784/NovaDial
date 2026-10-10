package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey val number: String,
    val normalizedNumber: String,
    val reason: String = "User Blocked",
    val addedAt: Long = System.currentTimeMillis(),
    val blockCount: Int = 0
)

enum class BackupType {
    LOCAL_ENCRYPTED,
    GOOGLE_CONTACTS,
    CLOUD_SYNC
}

@Entity(tableName = "backups")
data class BackupRecordEntity(
    @PrimaryKey val backupId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: BackupType = BackupType.LOCAL_ENCRYPTED,
    val recordCount: Int = 0,
    val sizeBytes: Long = 0L,
    val checksum: String = "",
    val isEncrypted: Boolean = true,
    val encryptionAlgorithm: String = "AES-256-GCM",
    val description: String = "NovaDial Full Encrypted Backup"
)

data class SimAccount(
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val number: String? = null,
    val isDefault: Boolean = false
)
