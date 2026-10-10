package com.example.data.sync

import android.content.Context
import com.example.data.crypto.CryptoManager
import com.example.data.local.AppDatabase
import com.example.data.model.BackupRecordEntity
import com.example.data.model.BackupType
import com.example.data.model.BlockedNumberEntity
import com.example.data.model.CallLogEntry
import com.example.data.model.ContactEntity
import com.example.data.model.EmailItem
import com.example.data.model.MoshiHolder
import com.example.data.model.PhoneNumberItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BackupDataContainer(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val app: String = "NovaDial",
    val contacts: List<ContactEntity>,
    val callLogs: List<CallLogEntry>,
    val blockedNumbers: List<BlockedNumberEntity>
)

data class RestorePreview(
    val recordCount: Int,
    val callLogCount: Int,
    val blockedCount: Int,
    val createdAt: Long,
    val container: BackupDataContainer
)

object BackupManager {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val containerAdapter = moshi.adapter(BackupDataContainer::class.java)

    fun calculateSha256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun createLocalEncryptedBackup(context: Context): BackupRecordEntity {
        val db = AppDatabase.getDatabase(context)
        val contacts = db.contactDao().getAllContactsList()
        val callLogs = db.callLogDao().getAllCallLogsList()
        val blocked = db.blockedNumberDao().getAllBlockedNumbersList()

        val container = BackupDataContainer(
            contacts = contacts,
            callLogs = callLogs,
            blockedNumbers = blocked
        )

        val json = containerAdapter.toJson(container)
        val checksum = calculateSha256(json)
        val encryptedBase64 = CryptoManager.encrypt(json)

        val backupId = "backup_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val backupFile = File(backupDir, "$backupId.ndbak")
        backupFile.writeText(encryptedBase64, Charsets.UTF_8)

        val record = BackupRecordEntity(
            backupId = backupId,
            timestamp = System.currentTimeMillis(),
            type = BackupType.LOCAL_ENCRYPTED,
            recordCount = contacts.size,
            sizeBytes = backupFile.length(),
            checksum = checksum,
            isEncrypted = true,
            encryptionAlgorithm = "AES-256-GCM (Hardware Keystore)",
            description = "Protected backup containing ${contacts.size} contacts, ${callLogs.size} logs"
        )

        db.backupDao().insertBackup(record)
        return record
    }

    fun getBackupFile(context: Context, backupId: String): File {
        return File(File(context.filesDir, "backups"), "$backupId.ndbak")
    }

    fun previewBackupFromText(encryptedText: String): RestorePreview {
        val decryptedJson = CryptoManager.decrypt(encryptedText)
        if (decryptedJson.isBlank()) throw IllegalStateException("Decryption failed. Authentication tag mismatch or corrupt key.")

        val container = containerAdapter.fromJson(decryptedJson)
            ?: throw IllegalStateException("Malformed backup payload")

        return RestorePreview(
            recordCount = container.contacts.size,
            callLogCount = container.callLogs.size,
            blockedCount = container.blockedNumbers.size,
            createdAt = container.createdAt,
            container = container
        )
    }

    suspend fun previewBackup(context: Context, backupId: String): RestorePreview {
        val backupFile = File(File(context.filesDir, "backups"), "$backupId.ndbak")
        if (!backupFile.exists()) throw IllegalArgumentException("Backup file not found")

        val encryptedText = backupFile.readText(Charsets.UTF_8)
        val decryptedJson = CryptoManager.decrypt(encryptedText)
        if (decryptedJson.isBlank()) throw IllegalStateException("Decryption failed. Authentication tag mismatch or corrupt key.")

        val container = containerAdapter.fromJson(decryptedJson)
            ?: throw IllegalStateException("Malformed backup payload")

        return RestorePreview(
            recordCount = container.contacts.size,
            callLogCount = container.callLogs.size,
            blockedCount = container.blockedNumbers.size,
            createdAt = container.createdAt,
            container = container
        )
    }

    suspend fun restoreFromContainer(context: Context, container: BackupDataContainer, replaceAll: Boolean) {
        val db = AppDatabase.getDatabase(context)
        if (replaceAll) {
            db.contactDao().deleteAllContacts()
            db.callLogDao().clearAllCallLogs()
        }
        db.contactDao().insertContacts(container.contacts)
        db.callLogDao().insertCallLogs(container.callLogs)
        db.blockedNumberDao().insertAll(container.blockedNumbers)
    }

    fun exportToVCard(contacts: List<ContactEntity>): String {
        val sb = StringBuilder()
        for (c in contacts) {
            sb.append("BEGIN:VCARD\n")
            sb.append("VERSION:3.0\n")
            sb.append("FN:${c.displayName}\n")
            if (c.company.isNotBlank()) sb.append("ORG:${c.company}\n")
            if (c.jobTitle.isNotBlank()) sb.append("TITLE:${c.jobTitle}\n")
            for (p in c.getPhoneNumbers()) {
                val type = p.type.uppercase()
                sb.append("TEL;TYPE=$type:${p.number}\n")
            }
            for (e in c.getEmails()) {
                val type = e.type.uppercase()
                sb.append("EMAIL;TYPE=$type:${e.email}\n")
            }
            if (c.notes.isNotBlank()) {
                sb.append("NOTE:${c.notes.replace("\n", "\\n")}\n")
            }
            sb.append("END:VCARD\n\n")
        }
        return sb.toString()
    }

    fun parseVCard(vCardContent: String): List<ContactEntity> {
        val contacts = mutableListOf<ContactEntity>()
        val cards = vCardContent.split("BEGIN:VCARD").filter { it.contains("END:VCARD") }

        for (card in cards) {
            var name = ""
            var org = ""
            var title = ""
            var note = ""
            val phones = mutableListOf<PhoneNumberItem>()
            val emails = mutableListOf<EmailItem>()

            for (line in card.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("FN:") -> name = trimmed.substring(3).trim()
                    trimmed.startsWith("ORG:") -> org = trimmed.substring(4).trim()
                    trimmed.startsWith("TITLE:") -> title = trimmed.substring(6).trim()
                    trimmed.startsWith("NOTE:") -> note = trimmed.substring(5).trim().replace("\\n", "\n")
                    trimmed.startsWith("TEL") -> {
                        val colon = trimmed.indexOf(':')
                        if (colon != -1) {
                            val num = trimmed.substring(colon + 1).trim()
                            val type = if (trimmed.contains("CELL", true) || trimmed.contains("MOBILE", true)) "Mobile" else "Other"
                            phones.add(PhoneNumberItem(number = num, type = type))
                        }
                    }
                    trimmed.startsWith("EMAIL") -> {
                        val colon = trimmed.indexOf(':')
                        if (colon != -1) {
                            val addr = trimmed.substring(colon + 1).trim()
                            emails.add(EmailItem(email = addr, type = "Work"))
                        }
                    }
                }
            }

            if (name.isNotBlank() || phones.isNotEmpty()) {
                contacts.add(
                    ContactEntity(
                        id = UUID.randomUUID().toString(),
                        displayName = name.ifBlank { phones.firstOrNull()?.number ?: "Unnamed" },
                        company = org,
                        jobTitle = title,
                        notes = note,
                        phoneNumbersJson = MoshiHolder.phoneNumbersToJson(phones),
                        emailsJson = MoshiHolder.emailsToJson(emails),
                        source = com.example.data.model.ContactSource.NOVADIAL
                    )
                )
            }
        }
        return contacts
    }
}
