/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.util

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A single raw contact participating in a duplicate group.
 */
data class DuplicateContact(
    val rawContactId: Long,
    val contactId: Long,
    val name: String,
    val number: String
)

/**
 * A set of contacts that share the same (normalised) phone number.
 */
data class DuplicateGroup(
    val sharedNumber: String,
    val contacts: List<DuplicateContact>
)

/**
 * NovaDial Duplicate Finder.
 *
 * Detects contacts that share a phone number and lets the user either merge them
 * (via [ContactsContract.AggregationExceptions], which permanently aggregates the
 * raw contacts into one) or delete the redundant entries permanently.
 *
 * All operations write directly to the system contacts provider, so they are
 * permanent. Callers must hold READ_CONTACTS + WRITE_CONTACTS.
 */
object DuplicateFinder {

    private fun normalize(number: String): String =
        number.filter { it.isDigit() }.takeLast(10)

    /**
     * Scans the system contacts provider and returns every group of contacts that
     * share a normalised phone number. Runs on [Dispatchers.IO].
     */
    suspend fun findDuplicates(context: Context): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val map = LinkedHashMap<String, MutableList<DuplicateContact>>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val rawIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
                val contactIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val raw = if (rawIdx >= 0) cursor.getLong(rawIdx) else -1L
                    val cid = if (contactIdx >= 0) cursor.getLong(contactIdx) else -1L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val num = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                    if (raw <= 0L || num.isBlank()) continue
                    val key = normalize(num)
                    if (key.length < 6) continue
                    val list = map.getOrPut(key) { mutableListOf() }
                    if (list.none { it.rawContactId == raw }) {
                        list.add(DuplicateContact(raw, cid, name, num))
                    }
                }
            }
        } catch (_: Exception) {
        }
        map.entries
            .filter { it.value.size > 1 }
            .map { DuplicateGroup(it.key, it.value) }
    }

    /**
     * Merges [mergeRawIds] into [keepRawId] using aggregation exceptions so the
     * system treats them as a single contact. Returns the number merged.
     */
    suspend fun mergeInto(
        context: Context,
        keepRawId: Long,
        mergeRawIds: List<Long>
    ): Int = withContext(Dispatchers.IO) {
        var merged = 0
        for (other in mergeRawIds) {
            if (other == keepRawId) continue
            try {
                val op = ContentProviderOperation
                    .newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                    .withValue(
                        ContactsContract.AggregationExceptions.TYPE,
                        ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER
                    )
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, keepRawId)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, other)
                    .build()
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, arrayListOf(op))
                merged++
            } catch (_: Exception) {
            }
        }
        merged
    }

    /**
     * Permanently deletes a single raw contact.
     */
    suspend fun deleteRawContact(context: Context, rawContactId: Long): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val count = context.contentResolver.delete(
                    ContactsContract.RawContacts.CONTENT_URI,
                    "${ContactsContract.RawContacts._ID} = ?",
                    arrayOf(rawContactId.toString())
                )
                count > 0
            } catch (_: Exception) {
                false
            }
        }
}
