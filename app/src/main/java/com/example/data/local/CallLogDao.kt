package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogsFlow(): Flow<List<CallLogEntry>>

    @Query("SELECT * FROM call_logs WHERE callType = :type ORDER BY timestamp DESC")
    fun getCallLogsByTypeFlow(type: CallType): Flow<List<CallLogEntry>>

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCallsFlow(limit: Int): Flow<List<CallLogEntry>>

    @Query("SELECT * FROM call_logs")
    suspend fun getAllCallLogsList(): List<CallLogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLogs(callLogs: List<CallLogEntry>)

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteCallLogById(id: Long)

    @Query("DELETE FROM call_logs")
    suspend fun clearAllCallLogs()

    @Query("SELECT COUNT(*) FROM call_logs WHERE callType = 'BLOCKED' OR isSpam = 1")
    fun getBlockedOrSpamCountFlow(): Flow<Int>
}
