package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BackupRecordEntity
import com.example.data.model.BlockedNumberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY addedAt DESC")
    fun getAllBlockedNumbersFlow(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT * FROM blocked_numbers")
    suspend fun getAllBlockedNumbersList(): List<BlockedNumberEntity>

    @Query("SELECT COUNT(*) FROM blocked_numbers WHERE normalizedNumber = :normalizedNumber")
    suspend fun isNumberBlocked(normalizedNumber: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedNumber(entity: BlockedNumberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BlockedNumberEntity>)

    @Query("DELETE FROM blocked_numbers WHERE number = :number")
    suspend fun deleteBlockedNumber(number: String)

    @Query("UPDATE blocked_numbers SET blockCount = blockCount + 1 WHERE normalizedNumber = :normalizedNumber")
    suspend fun incrementBlockCount(normalizedNumber: String)

    @Query("SELECT COUNT(*) FROM blocked_numbers")
    fun getBlockedCountFlow(): Flow<Int>
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM backups ORDER BY timestamp DESC")
    fun getAllBackupsFlow(): Flow<List<BackupRecordEntity>>

    @Query("SELECT * FROM backups")
    suspend fun getAllBackupsList(): List<BackupRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: BackupRecordEntity)

    @Query("DELETE FROM backups WHERE backupId = :backupId")
    suspend fun deleteBackupById(backupId: String)
}
