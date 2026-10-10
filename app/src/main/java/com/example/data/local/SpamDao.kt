package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.InterceptedSpamCallEntity
import com.example.data.model.SpamEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpamDao {

    @Query("SELECT * FROM spam_entries ORDER BY riskScore DESC, addedAt DESC")
    fun getAllSpamFlow(): Flow<List<SpamEntryEntity>>

    @Query("SELECT * FROM spam_entries WHERE phoneNumber LIKE '%' || :query || '%' OR normalizedNumber LIKE '%' || :query || '%' OR callerName LIKE '%' || :query || '%' ORDER BY riskScore DESC")
    fun searchSpam(query: String): Flow<List<SpamEntryEntity>>

    @Query("SELECT * FROM spam_entries WHERE normalizedNumber = :normalizedNumber LIMIT 1")
    suspend fun getSpamByNormalizedNumber(normalizedNumber: String): SpamEntryEntity?

    @Query("SELECT * FROM spam_entries WHERE normalizedNumber = :normalizedNumber LIMIT 1")
    fun getSpamByNormalizedNumberSync(normalizedNumber: String): SpamEntryEntity?

    @Query("SELECT * FROM spam_entries WHERE normalizedNumber = :normalizedNumber")
    suspend fun findSpamByNormalized(normalizedNumber: String): List<SpamEntryEntity>

    @Query("SELECT * FROM spam_entries WHERE patternType = 'PREFIX' OR patternType = 'WILDCARD'")
    suspend fun getPrefixAndWildcardRules(): List<SpamEntryEntity>

    @Query("SELECT * FROM spam_entries")
    suspend fun getAllSpamList(): List<SpamEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpam(entry: SpamEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SpamEntryEntity>)

    @Query("DELETE FROM spam_entries WHERE id LIKE 'seed_%'")
    suspend fun deleteBuiltInSampleEntries()

    @Query("DELETE FROM spam_entries WHERE id = :id")
    suspend fun deleteSpamById(id: String)

    @Query("DELETE FROM spam_entries WHERE phoneNumber = :phoneNumber OR normalizedNumber = :phoneNumber")
    suspend fun deleteSpamByNumber(phoneNumber: String)

    @Query("SELECT COUNT(*) FROM spam_entries")
    fun getSpamCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM spam_entries WHERE riskScore >= 75")
    fun getHighRiskCountFlow(): Flow<Int>

    @Query("DELETE FROM spam_entries")
    suspend fun clearAllSpam()

    // Intercepted calls log
    @Query("SELECT * FROM intercepted_spam_calls ORDER BY timestamp DESC LIMIT 100")
    fun getInterceptedCallsFlow(): Flow<List<InterceptedSpamCallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInterceptedCall(call: InterceptedSpamCallEntity)

    @Query("DELETE FROM intercepted_spam_calls")
    suspend fun clearInterceptedCalls()
}
