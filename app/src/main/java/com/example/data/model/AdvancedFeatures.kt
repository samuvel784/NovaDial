package com.example.data.model

enum class SpamCallerCategory(val label: String, val icon: String) {
    TELEMARKETER("Telemarketer", "📣"),
    ROBOCALL("Robocall Automated", "🤖"),
    FINANCIAL_FRAUD("Financial / Debt Collector", "⚠️"),
    COURIER_DELIVERY("Courier & Delivery", "📦"),
    VERIFIED_BUSINESS("Verified Business", "✅"),
    SPAM_OTHER("Suspicious / Spam", "🚨")
}

data class CallerIdentity(
    val phoneNumber: String,
    val name: String? = null,
    val category: SpamCallerCategory? = null,
    val spamScore: Int = 0, // 0..100 (100 = confirmed scam)
    val reportCount: Int = 0,
    val verificationStatus: String = "Clean", // "Verified Business", "Community Flagged", "Scam Alert", "Clean"
    val topReportReason: String? = null,
    val location: String? = null
)

data class SpamReport(
    val id: String,
    val phoneNumber: String,
    val category: SpamCallerCategory,
    val comment: String,
    val timestamp: Long = System.currentTimeMillis(),
    val upvotes: Int = 1
)

data class SpamProtectionSettings(
    val enableSmartCallerId: Boolean = true,
    val autoBlockHighRiskSpam: Boolean = true,
    val spamThresholdScore: Int = 75,
    val showCallerIdOverlay: Boolean = true
)

data class PostCallNote(
    val id: String,
    val phoneNumber: String,
    val contactName: String? = null,
    val noteText: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PostCallActionSettings(
    val showPostCallSheet: Boolean = true,
    val autoOpenForUnknownOnly: Boolean = false,
    val enableQuickReminder: Boolean = true
)

enum class SimRuleMatchingType(val label: String) {
    PREFIX("Country / Area Prefix"),
    CONTACT_TAG("Contact Group / Tag"),
    CONTAINS_DIGITS("Contains Digits")
}

data class MultiSimRule(
    val id: String,
    val ruleName: String,
    val matchingType: SimRuleMatchingType,
    val pattern: String, // e.g. "+1 800", "Work", "00"
    val targetSimSlot: Int, // 1 or 2
    val isEnabled: Boolean = true
)

data class GestureSettings(
    val flipToMute: Boolean = true,
    val raiseToAnswer: Boolean = true,
    val shakeToEndCall: Boolean = false,
    val flipSensitivity: Float = 0.8f
)

enum class CallPosterTheme(val displayName: String, val primaryColorHex: Long, val secondaryColorHex: Long) {
    NEON_CYBER("Neon Cyber", 0xFF6200EE, 0xFF03DAC6),
    SUNSET_GLOW("Sunset Glow", 0xFFFF5722, 0xFFFFC107),
    EMERALD_WAVE("Emerald Wave", 0xFF009688, 0xFF4CAF50),
    MIDNIGHT_DARK("Midnight Dark", 0xFF121212, 0xFF3700B3),
    MINIMAL_PASTEL("Minimal Pastel", 0xFFE1F5FE, 0xFFF3E5F5),
    PARTICLE_PULSE("Particle Pulse", 0xFF3F51B5, 0xFFE91E63)
}

data class ContactCallPoster(
    val phoneNumber: String,
    val theme: CallPosterTheme = CallPosterTheme.NEON_CYBER,
    val customBadgeText: String? = "VIP Contact",
    val customPhotoUri: String? = null
)

enum class FlashPattern(val label: String) {
    PULSE("Steady Pulse"),
    SOS("SOS Emergency"),
    STROBE("Fast Strobe"),
    RHYTHM("Dual Rhythm")
}

data class FlashAlertSettings(
    val enableFlashAlert: Boolean = true,
    val flashOnSilentOnly: Boolean = false,
    val pattern: FlashPattern = FlashPattern.PULSE,
    val flashSpeedMs: Long = 200L
)
