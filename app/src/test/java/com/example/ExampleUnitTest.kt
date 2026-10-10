package com.example

import com.example.data.model.ContactEntity
import com.example.data.model.MoshiHolder
import com.example.data.model.PhoneNumberItem
import com.example.data.sync.BackupManager
import com.example.data.sync.DuplicateDetector
import com.example.util.PhoneNumberUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testPhoneNumberNormalization() {
        assertEquals("+15551234567", PhoneNumberUtils.normalize("+1 (555) 123-4567"))
        assertEquals("5551234567", PhoneNumberUtils.normalize("(555) 123-4567"))
        assertEquals("911", PhoneNumberUtils.normalize("911"))
        assertTrue(PhoneNumberUtils.isEmergencyNumber("911"))
        assertTrue(PhoneNumberUtils.isEmergencyNumber("112"))
        assertFalse(PhoneNumberUtils.isEmergencyNumber("5551234"))
    }

    @Test
    fun testT9Matching() {
        val phones = listOf("+15551234567")
        // "5646" corresponds to "JOHN" on telephone keypad
        assertTrue(PhoneNumberUtils.matchesT9("5646", "John Doe", phones))
        assertTrue(PhoneNumberUtils.matchesT9("564", "John Doe", phones))
        // Phone number matching
        assertTrue(PhoneNumberUtils.matchesT9("1234", "Jane", phones))
        assertFalse(PhoneNumberUtils.matchesT9("9999", "John", listOf("1234567")))
    }

    @Test
    fun testDuplicateDetectionAndMerging() {
        val c1 = ContactEntity(
            id = "c1",
            displayName = "Alex Smith",
            phoneNumbersJson = MoshiHolder.phoneNumbersToJson(listOf(PhoneNumberItem("+15551112222", "Mobile"))),
            notes = "Met at conference"
        )
        val c2 = ContactEntity(
            id = "c2",
            displayName = "Alex Smith",
            phoneNumbersJson = MoshiHolder.phoneNumbersToJson(listOf(PhoneNumberItem("5551112222", "Work"))),
            company = "TechCorp"
        )

        val duplicates = DuplicateDetector.findDuplicates(listOf(c1, c2))
        assertEquals(1, duplicates.size)

        val merged = DuplicateDetector.mergeContacts(listOf(c1, c2))
        assertEquals("Alex Smith", merged.displayName)
        assertEquals("TechCorp", merged.company)
        assertTrue(merged.notes.contains("Met at conference"))
        assertEquals(1, merged.getPhoneNumbers().size) // normalized to single unique number
    }

    @Test
    fun testVCardExportAndParsing() {
        val contact = ContactEntity(
            id = "test_vcard",
            displayName = "Jane Doe",
            company = "Acme Inc",
            jobTitle = "Lead Engineer",
            phoneNumbersJson = MoshiHolder.phoneNumbersToJson(listOf(PhoneNumberItem("5559876543", "Mobile"))),
            notes = "VIP Client"
        )

        val vcf = BackupManager.exportToVCard(listOf(contact))
        assertTrue(vcf.contains("FN:Jane Doe"))
        assertTrue(vcf.contains("ORG:Acme Inc"))
        assertTrue(vcf.contains("TEL;TYPE=MOBILE:5559876543"))

        val parsed = BackupManager.parseVCard(vcf)
        assertEquals(1, parsed.size)
        assertEquals("Jane Doe", parsed[0].displayName)
        assertEquals("Acme Inc", parsed[0].company)
        assertEquals("Lead Engineer", parsed[0].jobTitle)
    }

    @Test
    fun testSha256Checksum() {
        val hash1 = BackupManager.calculateSha256("NovaDial Secure Backup Data")
        val hash2 = BackupManager.calculateSha256("NovaDial Secure Backup Data")
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
    }

    @Test
    fun testThemeModeEnum() {
        assertEquals("DAY", com.example.ui.theme.ThemeMode.DAY.name)
        assertEquals("DEFAULT", com.example.ui.theme.ThemeMode.DEFAULT.name)
        assertEquals("DARK", com.example.ui.theme.ThemeMode.DARK.name)
    }

    @Test
    fun testSpamSeedData() {
        val initialSpam = com.example.data.sync.SpamSeedData.getInitialCuratedSpamEntries()
        assertTrue(initialSpam.isNotEmpty())
        assertTrue(initialSpam.any { it.category == com.example.data.model.SpamCallerCategory.FINANCIAL_FRAUD })
        assertTrue(initialSpam.any { it.recommendedAction == com.example.data.model.BlockAction.AUTO_BLOCK })
    }
}
