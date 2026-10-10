package com.example.telecom

import com.squareup.moshi.JsonClass

enum class EmergencyTriggerMode(val title: String, val description: String) {
    POWER_TRIPLE_PRESS("Triple-Press Power Button", "Rapidly press power button 3 times (screen on/off)"),
    SHAKE_GESTURE("Rapid Shake Device", "Firmly shake phone 3 times in quick succession"),
    BOTH("Both Hardware & Shake", "Activate with either 3x power clicks or shaking device")
}

@JsonClass(generateAdapter = true)
data class EmergencySettings(
    val isServiceEnabled: Boolean = true,
    val triggerMode: EmergencyTriggerMode = EmergencyTriggerMode.POWER_TRIPLE_PRESS,
    val emergencyNumber: String = "112",
    val countdownSeconds: Int = 3,
    val isSirenEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val isAutoSmsEnabled: Boolean = true,
    val emergencySmsText: String = "EMERGENCY SOS: I need urgent help! This is an automated SOS triggered via NovaDial."
)
