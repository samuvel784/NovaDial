package com.example.data.model

data class EmergencyContact(
    val id: String,
    val name: String,
    val number: String,
    val relationship: String, // e.g. "Police", "Ambulance", "Doctor", "Family", "ICE"
    val isPrimary: Boolean = false
)

data class SpeedDialItem(
    val keyDigit: Char, // '2' to '9'
    val contactId: String,
    val contactName: String,
    val phoneNumber: String
)

data class VoicemailItem(
    val id: String,
    val senderName: String?,
    val senderNumber: String,
    val timestamp: Long,
    val durationSeconds: Int,
    val transcription: String,
    val isRead: Boolean = false,
    val audioWaveform: List<Float> = listOf(0.2f, 0.5f, 0.9f, 0.6f, 0.3f, 0.8f, 0.4f, 0.7f, 0.2f, 0.9f, 0.5f, 0.3f),
    /** content:// address of the real voicemail audio. Empty when the phone has no audio file for this entry. */
    val audioUri: String = ""
)

data class QuickSmsTemplate(
    val id: String,
    val title: String,
    val messageText: String
)
