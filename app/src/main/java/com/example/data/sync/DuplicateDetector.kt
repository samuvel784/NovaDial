package com.example.data.sync

import com.example.data.model.ContactEntity
import com.example.data.model.EmailItem
import com.example.data.model.MoshiHolder
import com.example.data.model.PhoneNumberItem
import com.example.util.PhoneNumberUtils
import java.util.UUID

data class DuplicateGroup(
    val id: String = UUID.randomUUID().toString(),
    val matchReason: String,
    val contacts: List<ContactEntity>,
    val mergedPreview: ContactEntity
)

object DuplicateDetector {

    fun findDuplicates(contacts: List<ContactEntity>): List<DuplicateGroup> {
        val visited = mutableSetOf<String>()
        val groups = mutableListOf<DuplicateGroup>()

        for (i in contacts.indices) {
            val c1 = contacts[i]
            if (visited.contains(c1.id)) continue

            val cluster = mutableListOf(c1)
            var reason = ""

            for (j in i + 1 until contacts.size) {
                val c2 = contacts[j]
                if (visited.contains(c2.id)) continue

                val match = checkMatch(c1, c2)
                if (match != null) {
                    cluster.add(c2)
                    visited.add(c2.id)
                    if (reason.isEmpty()) reason = match
                }
            }

            if (cluster.size > 1) {
                visited.add(c1.id)
                val merged = mergeContacts(cluster)
                groups.add(
                    DuplicateGroup(
                        matchReason = reason.ifEmpty { "Similar contact details" },
                        contacts = cluster,
                        mergedPreview = merged
                    )
                )
            }
        }

        return groups
    }

    private fun checkMatch(c1: ContactEntity, c2: ContactEntity): String? {
        // 1. Phone numbers match
        val phones1 = c1.getPhoneNumbers().map { PhoneNumberUtils.normalize(it.number) }.filter { it.isNotBlank() }
        val phones2 = c2.getPhoneNumbers().map { PhoneNumberUtils.normalize(it.number) }.filter { it.isNotBlank() }
        for (p1 in phones1) {
            for (p2 in phones2) {
                if (PhoneNumberUtils.numbersMatch(p1, p2)) {
                    return "Matching Phone: ${PhoneNumberUtils.formatForDisplay(p1)}"
                }
            }
        }

        // 2. Email match
        val emails1 = c1.getEmails().map { it.email.trim().lowercase() }.filter { it.isNotBlank() }
        val emails2 = c2.getEmails().map { it.email.trim().lowercase() }.filter { it.isNotBlank() }
        for (e1 in emails1) {
            if (emails2.contains(e1)) {
                return "Matching Email: $e1"
            }
        }

        // 3. Name exact match
        val n1 = c1.displayName.trim().lowercase()
        val n2 = c2.displayName.trim().lowercase()
        if (n1.isNotBlank() && n1 == n2) {
            return "Identical Name: ${c1.displayName}"
        }

        return null
    }

    fun mergeContacts(cluster: List<ContactEntity>): ContactEntity {
        require(cluster.isNotEmpty()) { "Cannot merge empty cluster" }
        if (cluster.size == 1) return cluster[0]

        // Best name (longest or first non-empty)
        val bestName = cluster.maxByOrNull { it.displayName.length }?.displayName ?: cluster[0].displayName
        val bestPhoto = cluster.firstOrNull { !it.photoUri.isNullOrBlank() }?.photoUri
        val isStarred = cluster.any { it.isStarred }
        val bestCompany = cluster.firstOrNull { it.company.isNotBlank() }?.company ?: ""
        val bestJob = cluster.firstOrNull { it.jobTitle.isNotBlank() }?.jobTitle ?: ""

        // Merge notes without data loss
        val notesCombined = cluster
            .map { it.notes.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n---\n")

        // Merge phones uniquely
        val mergedPhones = mutableListOf<PhoneNumberItem>()
        val seenNormPhones = mutableListOf<String>()
        for (c in cluster) {
            for (p in c.getPhoneNumbers()) {
                val norm = PhoneNumberUtils.normalize(p.number)
                if (norm.isNotBlank() && seenNormPhones.none { PhoneNumberUtils.numbersMatch(it, norm) }) {
                    seenNormPhones.add(norm)
                    mergedPhones.add(p)
                }
            }
        }

        // Merge emails uniquely
        val mergedEmails = mutableListOf<EmailItem>()
        val seenEmails = mutableSetOf<String>()
        for (c in cluster) {
            for (e in c.getEmails()) {
                val lower = e.email.trim().lowercase()
                if (lower.isNotBlank() && !seenEmails.contains(lower)) {
                    seenEmails.add(lower)
                    mergedEmails.add(e)
                }
            }
        }

        // Merge groups uniquely
        val mergedGroups = cluster.flatMap { it.getGroups() }.distinct()

        val primaryId = cluster[0].id // Retain first contact's ID for continuity

        return ContactEntity(
            id = primaryId,
            displayName = bestName,
            photoUri = bestPhoto,
            isStarred = isStarred,
            source = cluster[0].source,
            company = bestCompany,
            jobTitle = bestJob,
            notes = notesCombined,
            phoneNumbersJson = MoshiHolder.phoneNumbersToJson(mergedPhones),
            emailsJson = MoshiHolder.emailsToJson(mergedEmails),
            groupsJson = MoshiHolder.groupsToJson(mergedGroups),
            lastContacted = cluster.maxOfOrNull { it.lastContacted } ?: 0L,
            timesContacted = cluster.sumOf { it.timesContacted },
            updatedAt = System.currentTimeMillis()
        )
    }
}
