package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE ASC")
    fun getAllContactsFlow(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isStarred = 1 ORDER BY displayName COLLATE NOCASE ASC")
    fun getFavoriteContactsFlow(): Flow<List<ContactEntity>>

    @Query("""
        SELECT * FROM contacts 
        WHERE displayName LIKE '%' || :query || '%' 
           OR phoneNumbersJson LIKE '%' || :query || '%' 
           OR emailsJson LIKE '%' || :query || '%' 
        ORDER BY displayName COLLATE NOCASE ASC
    """)
    fun searchContactsFlow(query: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE phoneNumbersJson LIKE '%' || :numberSnippet || '%' LIMIT 1")
    suspend fun findContactByNumberSnippet(numberSnippet: String): ContactEntity?

    @Query("SELECT * FROM contacts")
    suspend fun getAllContactsList(): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContactsIgnore(contacts: List<ContactEntity>)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContactById(id: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getContactCount(): Int

    @Query("SELECT primaryId FROM merged_contact_mappings WHERE suppressedId = :suppressedId")
    suspend fun getPrimaryIdForSuppressed(suppressedId: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMergedMapping(mapping: com.example.data.model.MergedContactMappingEntity)

    @Query("SELECT * FROM merged_contact_mappings")
    suspend fun getAllMergedMappings(): List<com.example.data.model.MergedContactMappingEntity>
}
