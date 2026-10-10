package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.data.model.ContactEntity
import com.example.data.model.ContactSource
import com.example.data.model.EmailItem
import com.example.data.model.MoshiHolder
import com.example.data.model.PhoneNumberItem
import com.example.data.model.VoicemailItem
import com.example.util.PhoneNumberUtils
import java.util.UUID

object DeviceContactsHelper {

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun readDeviceContacts(context: Context): List<ContactEntity> {
        if (!hasContactsPermission(context)) return emptyList()

        val contactsList = mutableListOf<ContactEntity>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.STARRED
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoIdx = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val starredIdx = it.getColumnIndex(ContactsContract.Contacts.STARRED)

                while (it.moveToNext()) {
                    val rawId = it.getString(idIdx) ?: continue
                    val name = it.getString(nameIdx) ?: "Unknown"
                    val photoUri = if (photoIdx != -1) it.getString(photoIdx) else null
                    val isStarred = if (starredIdx != -1) it.getInt(starredIdx) == 1 else false

                    val phones = readPhonesForContact(contentResolver, rawId)
                    val emails = readEmailsForContact(contentResolver, rawId)

                    if (phones.isNotEmpty() || name.isNotBlank()) {
                        contactsList.add(
                            ContactEntity(
                                id = "dev_$rawId",
                                displayName = name,
                                photoUri = photoUri,
                                isStarred = isStarred,
                                source = ContactSource.DEVICE,
                                phoneNumbersJson = MoshiHolder.phoneNumbersToJson(phones),
                                emailsJson = MoshiHolder.emailsToJson(emails)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return contactsList
    }

    private fun readPhonesForContact(contentResolver: ContentResolver, contactId: String): List<PhoneNumberItem> {
        val phones = mutableListOf<PhoneNumberItem>()
        var phoneCursor: Cursor? = null
        try {
            phoneCursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )
            phoneCursor?.let {
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                var first = true
                while (it.moveToNext()) {
                    val num = it.getString(numIdx) ?: continue
                    val typeInt = it.getInt(typeIdx)
                    val label = when (typeInt) {
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                        else -> "Main"
                    }
                    phones.add(PhoneNumberItem(number = num, type = label, isPrimary = first))
                    first = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            phoneCursor?.close()
        }
        return phones
    }

    private fun readEmailsForContact(contentResolver: ContentResolver, contactId: String): List<EmailItem> {
        val emails = mutableListOf<EmailItem>()
        var emailCursor: Cursor? = null
        try {
            emailCursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.TYPE
                ),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )
            emailCursor?.let {
                val addrIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                val typeIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)
                while (it.moveToNext()) {
                    val addr = it.getString(addrIdx) ?: continue
                    val typeInt = it.getInt(typeIdx)
                    val label = if (typeInt == ContactsContract.CommonDataKinds.Email.TYPE_WORK) "Work" else "Home"
                    emails.add(EmailItem(email = addr, type = label))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            emailCursor?.close()
        }
        return emails
    }

    fun readDeviceCallLogs(context: Context): List<CallLogEntry> {
        if (!hasCallLogPermission(context)) return emptyList()

        val logs = mutableListOf<CallLogEntry>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 100"
            )

            cursor?.let {
                val sysIdIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val sysId = if (sysIdIdx != -1) it.getLong(sysIdIdx) else 0L
                    val num = it.getString(numIdx) ?: continue
                    val name = it.getString(nameIdx)
                    val rawType = it.getInt(typeIdx)
                    val date = it.getLong(dateIdx)
                    val dur = it.getLong(durIdx)

                    val callType = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                        else -> CallType.INCOMING
                    }

                    logs.add(
                        CallLogEntry(
                            id = sysId,
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
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return logs
    }

    fun readDeviceVoicemails(context: Context): List<VoicemailItem> {
        val voicemails = mutableListOf<VoicemailItem>()
        val contentResolver: ContentResolver = context.contentResolver

        // 1. Query Android Voicemail Provider if accessible
        try {
            val voicemailUri = android.provider.VoicemailContract.Voicemails.CONTENT_URI
            val projection = arrayOf(
                android.provider.VoicemailContract.Voicemails._ID,
                android.provider.VoicemailContract.Voicemails.NUMBER,
                android.provider.VoicemailContract.Voicemails.DATE,
                android.provider.VoicemailContract.Voicemails.DURATION,
                android.provider.VoicemailContract.Voicemails.IS_READ,
                android.provider.VoicemailContract.Voicemails.TRANSCRIPTION
            )
            val cursor = contentResolver.query(
                voicemailUri,
                projection,
                null,
                null,
                "${android.provider.VoicemailContract.Voicemails.DATE} DESC"
            )
            cursor?.use { c ->
                val idIdx = c.getColumnIndex(android.provider.VoicemailContract.Voicemails._ID)
                val numIdx = c.getColumnIndex(android.provider.VoicemailContract.Voicemails.NUMBER)
                val dateIdx = c.getColumnIndex(android.provider.VoicemailContract.Voicemails.DATE)
                val durIdx = c.getColumnIndex(android.provider.VoicemailContract.Voicemails.DURATION)
                val readIdx = c.getColumnIndex(android.provider.VoicemailContract.Voicemails.IS_READ)
                val transIdx = c.getColumnIndex(android.provider.VoicemailContract.Voicemails.TRANSCRIPTION)

                while (c.moveToNext()) {
                    val rawId = if (idIdx != -1) c.getString(idIdx) else UUID.randomUUID().toString()
                    val num = if (numIdx != -1) c.getString(numIdx) ?: "" else ""
                    val date = if (dateIdx != -1) c.getLong(dateIdx) else System.currentTimeMillis()
                    val dur = if (durIdx != -1) c.getInt(durIdx) else 0
                    val isRead = if (readIdx != -1) c.getInt(readIdx) == 1 else false
                    val trans = if (transIdx != -1) c.getString(transIdx) ?: "Audio voicemail message" else "Audio voicemail message"

                    voicemails.add(
                        VoicemailItem(
                            id = "dev_vm_$rawId",
                            senderName = null,
                            senderNumber = num,
                            timestamp = date,
                            durationSeconds = dur,
                            transcription = trans,
                            isRead = isRead,
                            audioUri = rawId.toLongOrNull()?.let {
                                android.content.ContentUris.withAppendedId(voicemailUri, it).toString()
                            } ?: ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Silently ignore if provider is not available
        }

        // 2. Query CallLog for VOICEMAIL_TYPE entries if call log permission is granted
        if (hasCallLogPermission(context)) {
            try {
                val projection = arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.IS_READ
                )
                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    "${CallLog.Calls.TYPE} = ?",
                    arrayOf(CallLog.Calls.VOICEMAIL_TYPE.toString()),
                    "${CallLog.Calls.DATE} DESC"
                )
                cursor?.use { c ->
                    val idIdx = c.getColumnIndex(CallLog.Calls._ID)
                    val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                    val durIdx = c.getColumnIndex(CallLog.Calls.DURATION)
                    val readIdx = c.getColumnIndex(CallLog.Calls.IS_READ)

                    while (c.moveToNext()) {
                        val rawId = if (idIdx != -1) c.getString(idIdx) else UUID.randomUUID().toString()
                        val num = if (numIdx != -1) c.getString(numIdx) ?: "" else ""
                        val name = if (nameIdx != -1) c.getString(nameIdx) else null
                        val date = if (dateIdx != -1) c.getLong(dateIdx) else System.currentTimeMillis()
                        val dur = if (durIdx != -1) c.getInt(durIdx) else 0
                        val isRead = if (readIdx != -1) c.getInt(readIdx) == 1 else false

                        voicemails.add(
                            VoicemailItem(
                                id = "log_vm_$rawId",
                                senderName = name,
                                senderNumber = num,
                                timestamp = date,
                                durationSeconds = dur,
                                transcription = "Voice message from ${name ?: num.ifEmpty { "Carrier" }}",
                                isRead = isRead
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore if call log cannot be queried
            }
        }

        // Distinct deduplication
        return voicemails.distinctBy {
            val normNum = PhoneNumberUtils.normalize(it.senderNumber)
            "${normNum}_${it.timestamp / 5000}_${it.durationSeconds}"
        }
    }

    fun deleteDeviceContact(context: Context, rawId: String): Boolean {
        if (!hasWriteContactsPermission(context)) return false
        return try {
            val cleanId = rawId.removePrefix("dev_")
            val count = context.contentResolver.delete(
                ContactsContract.Contacts.CONTENT_URI,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(cleanId)
            )
            count > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun hasWriteContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun insertDeviceContact(context: Context, contact: ContactEntity): String? {
        if (!hasWriteContactsPermission(context)) return null
        return try {
            val ops = ArrayList<android.content.ContentProviderOperation>()
            val rawContactInsertIndex = ops.size

            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            // Name
            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                    .build()
            )

            // Phone numbers
            for (phone in contact.getPhoneNumbers()) {
                val type = when (phone.type.lowercase()) {
                    "work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                    "home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                    else -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                }
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, type)
                        .build()
                )
            }

            // Emails
            for (email in contact.getEmails()) {
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.DATA, email.email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                        .build()
                )
            }

            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            results.firstOrNull()?.uri?.lastPathSegment
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fast reverse lookup from phone number to contact name and photo URI using Android PhoneLookup.
     */
    fun lookupCallerInfo(context: Context, number: String): Pair<String?, String?> {
        if (number.isBlank() || !hasContactsPermission(context)) return Pair(null, null)
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                    val photo = if (photoIdx != -1) cursor.getString(photoIdx) else null
                    Pair(name, photo)
                } else {
                    Pair(null, null)
                }
            } ?: Pair(null, null)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    fun persistMergeInDeviceContacts(context: Context, primary: ContactEntity, cluster: List<ContactEntity>): Boolean {
        if (!hasWriteContactsPermission(context)) return false
        return try {
            val primaryRawId = primary.id.removePrefix("dev_")
            // 1. Delete secondary device contacts from ContactsProvider
            for (c in cluster) {
                if (c.id != primary.id && c.id.startsWith("dev_")) {
                    val secRawId = c.id.removePrefix("dev_")
                    context.contentResolver.delete(
                        ContactsContract.Contacts.CONTENT_URI,
                        "${ContactsContract.Contacts._ID} = ?",
                        arrayOf(secRawId)
                    )
                }
            }

            // 2. If primary is a device contact, update its phone numbers and emails in Contacts Provider
            if (primary.id.startsWith("dev_")) {
                val rawContactId = getRawContactIdForContactId(context, primaryRawId)
                if (rawContactId != null) {
                    val ops = ArrayList<android.content.ContentProviderOperation>()

                    // Delete old data rows for raw contact
                    ops.add(
                        android.content.ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                            .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ?", arrayOf(rawContactId))
                            .build()
                    )

                    // Re-insert structured name
                    ops.add(
                        android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, primary.displayName)
                            .build()
                    )

                    // Re-insert merged phone numbers
                    for (phone in primary.getPhoneNumbers()) {
                        val type = when (phone.type.lowercase()) {
                            "work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                            "home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                            else -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        }
                        ops.add(
                            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, type)
                                .build()
                        )
                    }

                    // Re-insert merged emails
                    for (email in primary.getEmails()) {
                        ops.add(
                            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Email.DATA, email.email)
                                .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                                .build()
                        )
                    }

                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getRawContactIdForContactId(context: Context, contactId: String): String? {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
                if (idx != -1) return cursor.getString(idx)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return null
    }
}
