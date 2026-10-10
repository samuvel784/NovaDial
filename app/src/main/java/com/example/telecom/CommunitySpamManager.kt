package com.example.telecom

import android.content.Context
import com.example.data.model.CallerIdentity
import com.example.data.model.MultiSimRule
import com.example.data.model.SimRuleMatchingType
import com.example.data.model.SpamCallerCategory
import com.example.data.model.SpamProtectionSettings
import com.example.data.model.SpamReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class CommunitySpamManager(private val context: Context) {

    private val _settings = MutableStateFlow(SpamProtectionSettings())
    val settings: StateFlow<SpamProtectionSettings> = _settings.asStateFlow()

    private val _simRules = MutableStateFlow<List<MultiSimRule>>(loadInitialSimRules())
    val simRules: StateFlow<List<MultiSimRule>> = _simRules.asStateFlow()

    private val _userReports = MutableStateFlow<List<SpamReport>>(loadInitialSpamReports())
    val userReports: StateFlow<List<SpamReport>> = _userReports.asStateFlow()

    fun updateSettings(newSettings: SpamProtectionSettings) {
        _settings.value = newSettings
    }

    // No sample data: SIM rules and spam reports contain only what the user adds.
    private fun loadInitialSimRules(): List<MultiSimRule> = emptyList()

    private fun loadInitialSpamReports(): List<SpamReport> = emptyList()

    fun resolveCallerIdentity(phoneNumber: String, contactName: String? = null): CallerIdentity {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

        // Known verified or user report check
        val reportsForNum = _userReports.value.filter { it.phoneNumber.replace(Regex("[^0-9+]"), "") == cleanNumber }
        if (reportsForNum.isNotEmpty()) {
            val topCategory = reportsForNum.groupBy { it.category }.maxByOrNull { it.value.size }?.key ?: SpamCallerCategory.SPAM_OTHER
            val totalUpvotes = reportsForNum.sumOf { it.upvotes }
            val score = (60 + (totalUpvotes.coerceAtMost(40))).coerceIn(0, 99)
            return CallerIdentity(
                phoneNumber = phoneNumber,
                name = contactName ?: "Reported by you",
                category = topCategory,
                spamScore = score,
                reportCount = reportsForNum.size + totalUpvotes,
                verificationStatus = if (score > 75) "Scam Alert" else "Community Flagged",
                topReportReason = reportsForNum.lastOrNull()?.comment ?: "Reported by users",
                location = "Community Registry"
            )
        }

        // Real Room Database known threat intelligence lookup
        try {
            val db = com.example.data.local.AppDatabase.getDatabase(context)
            val normalized = com.example.util.PhoneNumberUtils.normalize(phoneNumber)
            val dbSpam = db.spamDao().getSpamByNormalizedNumberSync(normalized)
            if (dbSpam != null) {
                return CallerIdentity(
                    phoneNumber = phoneNumber,
                    name = dbSpam.callerName,
                    category = dbSpam.category,
                    spamScore = dbSpam.riskScore,
                    reportCount = dbSpam.reportCount,
                    verificationStatus = if (dbSpam.riskScore >= 75) "Scam Alert" else "Suspicious",
                    topReportReason = dbSpam.threatDetails,
                    location = dbSpam.source
                )
            }
        } catch (e: Exception) {
            // Fallback gracefully
        }

        // Normal number
        return CallerIdentity(
            phoneNumber = phoneNumber,
            name = contactName,
            category = null,
            spamScore = 2,
            reportCount = 0,
            verificationStatus = "Clean",
            topReportReason = null,
            location = "Local Carrier"
        )
    }

    fun submitSpamReport(phoneNumber: String, category: SpamCallerCategory, comment: String) {
        val newReport = SpamReport(
            id = UUID.randomUUID().toString(),
            phoneNumber = phoneNumber,
            category = category,
            comment = comment,
            timestamp = System.currentTimeMillis(),
            upvotes = 1
        )
        _userReports.value = _userReports.value + newReport
    }

    // Smart Multi-SIM Carrier Rules Evaluation
    fun determineSimSlotForNumber(phoneNumber: String, contactTags: List<String> = emptyList()): Int {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        for (rule in _simRules.value) {
            if (!rule.isEnabled) continue
            when (rule.matchingType) {
                SimRuleMatchingType.PREFIX -> {
                    val cleanPattern = rule.pattern.replace(Regex("[^0-9+]"), "")
                    if (cleanNumber.startsWith(cleanPattern) || phoneNumber.startsWith(rule.pattern)) {
                        return rule.targetSimSlot
                    }
                }
                SimRuleMatchingType.CONTACT_TAG -> {
                    if (contactTags.any { it.contains(rule.pattern, ignoreCase = true) }) {
                        return rule.targetSimSlot
                    }
                }
                SimRuleMatchingType.CONTAINS_DIGITS -> {
                    if (cleanNumber.contains(rule.pattern)) {
                        return rule.targetSimSlot
                    }
                }
            }
        }
        return 1 // Default to SIM 1
    }

    fun addSimRule(rule: MultiSimRule) {
        _simRules.value = _simRules.value + rule
    }

    fun removeSimRule(id: String) {
        _simRules.value = _simRules.value.filter { it.id != id }
    }

    fun toggleSimRule(id: String, enabled: Boolean) {
        _simRules.value = _simRules.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
    }
}
