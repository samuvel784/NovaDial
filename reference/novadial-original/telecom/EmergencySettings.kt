package com.example.telecom

import com.squareup.moshi.JsonClass

enum class EmergencyTriggerMode(val title: String, val description: String) {
    SHAKE_GESTURE("Rapid Shake Device", "Firmly shake phone 3 times in quick succession")
}

@JsonClass(generateAdapter = true)
data class EmergencySettings(
    val isServiceEnabled: Boolean = true,
    val triggerMode: EmergencyTriggerMode = EmergencyTriggerMode.SHAKE_GESTURE,
    val emergencyNumber: String = "112",
    val countdownSeconds: Int = 3,
    val isSirenEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val isAutoSmsEnabled: Boolean = true,
    val emergencySmsText: String = "EMERGENCY SOS: I need urgent help! This is an automated SOS triggered via NovaDial."
)
