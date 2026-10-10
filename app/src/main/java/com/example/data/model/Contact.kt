package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

enum class ContactSource {
    DEVICE,
    GOOGLE,
    NOVADIAL
}

data class PhoneNumberItem(
    val number: String,
    val type: String = "Mobile", // Mobile, Work, Home, Main
    val isPrimary: Boolean = false
)

data class EmailItem(
    val email: String,
    val type: String = "Home" // Home, Work, Other
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String, // Unique UUID or device contact ID
    val displayName: String,
    val givenName: String = "",
    val familyName: String = "",
    val photoUri: String? = null,
    val isStarred: Boolean = false,
    val source: ContactSource = ContactSource.NOVADIAL,
    val company: String = "",
    val jobTitle: String = "",
    val address: String = "",
    val ringtone: String = "",
    val birthday: String = "",
    val nickname: String = "",
    val phoneticName: String = "",
    val website: String = "",
    val pronouns: String = "",
    val relationship: String = "",
    val sipAddress: String = "",
    val messageTone: String = "",
    val vibrationPattern: String = "",
    val department: String = "",
    val middleName: String = "",
    val suffix: String = "",
    val prefix: String = "",
    val notes: String = "", // Can be encrypted locally
    val phoneNumbersJson: String = "[]", // Serialized List<PhoneNumberItem>
    val emailsJson: String = "[]", // Serialized List<EmailItem>
    val groupsJson: String = "[]", // Serialized List<String>
    val lastContacted: Long = 0L,
    val timesContacted: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getPhoneNumbers(): List<PhoneNumberItem> {
        return try {
            val type = Types.newParameterizedType(List::class.java, PhoneNumberItem::class.java)
            val adapter = MoshiHolder.moshi.adapter<List<PhoneNumberItem>>(type)
            adapter.fromJson(phoneNumbersJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getEmails(): List<EmailItem> {
        return try {
            val type = Types.newParameterizedType(List::class.java, EmailItem::class.java)
            val adapter = MoshiHolder.moshi.adapter<List<EmailItem>>(type)
            adapter.fromJson(emailsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getGroups(): List<String> {
        return try {
            val type = Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = MoshiHolder.moshi.adapter<List<String>>(type)
            adapter.fromJson(groupsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getPrimaryPhoneNumber(): String {
        val phones = getPhoneNumbers()
        return phones.firstOrNull { it.isPrimary }?.number
            ?: phones.firstOrNull()?.number
            ?: ""
    }

    fun getInitials(): String {
        val parts = displayName.trim().split(" ").filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "#"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts[0].first()}${parts[1].first()}".uppercase()
        }
    }
}

object MoshiHolder {
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun phoneNumbersToJson(items: List<PhoneNumberItem>): String {
        val type = Types.newParameterizedType(List::class.java, PhoneNumberItem::class.java)
        return moshi.adapter<List<PhoneNumberItem>>(type).toJson(items)
    }

    fun emailsToJson(items: List<EmailItem>): String {
        val type = Types.newParameterizedType(List::class.java, EmailItem::class.java)
        return moshi.adapter<List<EmailItem>>(type).toJson(items)
    }

    fun groupsToJson(items: List<String>): String {
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).toJson(items)
    }
}
