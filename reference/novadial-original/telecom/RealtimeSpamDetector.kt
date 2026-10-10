package com.example.telecom

import android.content.Context
import android.os.SystemClock
import com.example.data.local.AppDatabase
import com.example.data.model.BlockAction
import com.example.data.model.InterceptedSpamCallEntity
import com.example.data.model.SpamCallerCategory
import com.example.data.model.SpamDetectionVerdict
import com.example.data.model.ThreatLevel
import com.example.util.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RealtimeSpamDetector(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun screenIncomingCallerId(rawNumber: String): SpamDetectionVerdict = withContext(Dispatchers.IO) {
        val startTime = SystemClock.elapsedRealtime()
        val normalized = PhoneNumberUtils.normalize(rawNumber)

        val cleanDigits = rawNumber.replace(Regex("[^0-9]"), "")
        val isEmergency = cleanDigits == "911" || cleanDigits == "112" || cleanDigits == "999" || cleanDigits == "108" || cleanDigits == "000" || cleanDigits == "110" || cleanDigits == "119"
        if (isEmergency) {
            val latency = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(1L)
            return@withContext SpamDetectionVerdict(
                phoneNumber = rawNumber,
                isSpam = false,
                isBlocked = false,
                riskScore = 0,
                threatLevel = ThreatLevel.CLEAN,
                category = SpamCallerCategory.VERIFIED_BUSINESS,
                callerName = "Emergency Services",
                source = "System Emergency Override",
                action = BlockAction.WARN_HEADS_UP,
                reason = "Emergency numbers are never blocked or flagged as spam.",
                scanLatencyMs = latency
            )
        }

        // 1. Check local user manual blocklist
        // Compare with the phone's number matching (last 8 digits), so +91 98765 43210 equals 9876543210
        val isUserBlocked = db.blockedNumberDao().getAllBlockedNumbersList().any {
            PhoneNumberUtils.numbersMatch(it.normalizedNumber, rawNumber)
        }
        if (isUserBlocked) {
            val latency = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(1L)
            val verdict = SpamDetectionVerdict(
                phoneNumber = rawNumber,
                isSpam = true,
                isBlocked = true,
                riskScore = 100,
                threatLevel = ThreatLevel.USER_BLOCKED,
                category = SpamCallerCategory.SPAM_OTHER,
                callerName = "User Blocklisted Number",
                source = "Personal Blocklist",
                action = BlockAction.AUTO_BLOCK,
                reason = "Explicitly blocked by user in NovaDial Blacklist",
                scanLatencyMs = latency
            )
            recordInterception(rawNumber, verdict.callerName, verdict.category, verdict.riskScore, "Auto-Blocked (User Blacklist)")
            return@withContext verdict
        }

        // 2. Check known spam database (Exact match)
        val exactSpam = db.spamDao().getSpamByNormalizedNumber(normalized)
            ?: db.spamDao().getAllSpamList().firstOrNull {
                it.patternType == "EXACT" && PhoneNumberUtils.numbersMatch(it.normalizedNumber, rawNumber)
            }
        if (exactSpam != null) {
            val latency = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(1L)
            val threat = when {
                exactSpam.riskScore >= 90 -> ThreatLevel.HIGH_RISK_SCAM
                exactSpam.riskScore >= 75 -> ThreatLevel.SUSPICIOUS
                else -> ThreatLevel.LOW_RISK
            }
            val verdict = SpamDetectionVerdict(
                phoneNumber = rawNumber,
                isSpam = true,
                // honour the action the user chose for this number (auto-block, silence or warn only)
                isBlocked = exactSpam.recommendedAction == BlockAction.AUTO_BLOCK,
                riskScore = exactSpam.riskScore,
                threatLevel = threat,
                category = exactSpam.category,
                callerName = exactSpam.callerName,
                source = exactSpam.source,
                action = exactSpam.recommendedAction,
                reason = exactSpam.threatDetails,
                scanLatencyMs = latency
            )
            if (verdict.action == BlockAction.AUTO_BLOCK) {
                recordInterception(rawNumber, verdict.callerName, verdict.category, verdict.riskScore, "Auto-Blocked (Spam Rule)")
            }
            return@withContext verdict
        }

        // 3. Check prefix / wildcard rules
        val prefixRules = db.spamDao().getPrefixAndWildcardRules()
        for (rule in prefixRules) {
            val cleanPrefix = PhoneNumberUtils.normalize(rule.phoneNumber)
            if (normalized.startsWith(cleanPrefix) || rawNumber.startsWith(rule.phoneNumber)) {
                val latency = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(1L)
                val verdict = SpamDetectionVerdict(
                    phoneNumber = rawNumber,
                    isSpam = true,
                    isBlocked = rule.recommendedAction == BlockAction.AUTO_BLOCK,
                    riskScore = rule.riskScore,
                    threatLevel = if (rule.riskScore >= 80) ThreatLevel.HIGH_RISK_SCAM else ThreatLevel.SUSPICIOUS,
                    category = rule.category,
                    callerName = rule.callerName,
                    source = rule.source,
                    action = rule.recommendedAction,
                    reason = "${rule.threatDetails} (Matches prefix ${rule.phoneNumber})",
                    scanLatencyMs = latency
                )
                if (verdict.action == BlockAction.AUTO_BLOCK) {
                    recordInterception(rawNumber, verdict.callerName, verdict.category, verdict.riskScore, "Auto-Blocked (Prefix Rule)")
                }
                return@withContext verdict
            }
        }

        // 4. Check saved contacts in local phonebook
        val deviceName = com.example.data.repository.DeviceContactsHelper.lookupCallerInfo(context, rawNumber).first
        val savedContact = if (deviceName == null && normalized.length >= 7) {
            db.contactDao().findContactByNumberSnippet(normalized.takeLast(7))
        } else null

        if (deviceName != null || savedContact != null) {
            val latency = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(1L)
            return@withContext SpamDetectionVerdict(
                phoneNumber = rawNumber,
                isSpam = false,
                isBlocked = false,
                riskScore = 0,
                threatLevel = ThreatLevel.CLEAN,
                category = SpamCallerCategory.VERIFIED_BUSINESS,
                callerName = deviceName ?: savedContact?.displayName ?: rawNumber,
                source = "Saved Phonebook Contact",
                action = BlockAction.WARN_HEADS_UP, // Allow call normally
                reason = "Verified contact from your device address book",
                scanLatencyMs = latency
            )
        }

        // 5. Unknown caller / unsaved number.
        // Toll-free numbers (800, 888, ...) are used by real banks, shops and delivery companies, so they are
        // NOT treated as spam. Without a user rule there is no evidence either way.
        val latency = (SystemClock.elapsedRealtime() - startTime).coerceAtLeast(1L)
        return@withContext SpamDetectionVerdict(
            phoneNumber = rawNumber,
            isSpam = false,
            isBlocked = false,
            riskScore = 0,
            threatLevel = ThreatLevel.LOW_RISK,
            category = SpamCallerCategory.SPAM_OTHER,
            callerName = "Unknown / Unsaved Number",
            source = "Your device",
            action = BlockAction.WARN_HEADS_UP,
            reason = "Not in your contacts and not in your own blocklist or spam rules",
            scanLatencyMs = latency
        )
    }

    private suspend fun recordInterception(
        number: String,
        name: String,
        category: SpamCallerCategory,
        riskScore: Int,
        actionTaken: String
    ) {
        try {
            db.spamDao().insertInterceptedCall(
                InterceptedSpamCallEntity(
                    phoneNumber = number,
                    callerName = name,
                    category = category,
                    riskScore = riskScore,
                    actionTaken = actionTaken,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
