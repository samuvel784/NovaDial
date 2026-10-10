package com.example.telecom

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.EmergencyContact
import com.example.data.model.MoshiHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmergencyTriggerManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("novadial_emergency_prefs", Context.MODE_PRIVATE)
    private val moshiAdapter = MoshiHolder.moshi.adapter(EmergencySettings::class.java)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<EmergencySettings> = _settings.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Automatically ensure service state matches settings
        if (_settings.value.isServiceEnabled) {
            startEmergencyService()
        }
    }

    private var lastSosTriggerTime = 0L

    fun updateSettings(newSettings: EmergencySettings) {
        val oldEnabled = _settings.value.isServiceEnabled
        _settings.value = newSettings
        saveSettings(newSettings)
        if (newSettings.isServiceEnabled != oldEnabled) {
            if (newSettings.isServiceEnabled) {
                startEmergencyService()
            } else {
                stopEmergencyService()
            }
        }
    }

    private fun loadSettings(): EmergencySettings {
        val json = prefs.getString("emergency_settings_json", null)
        return if (json != null) {
            try {
                moshiAdapter.fromJson(json) ?: EmergencySettings()
            } catch (e: Exception) {
                EmergencySettings()
            }
        } else {
            EmergencySettings()
        }
    }

    private fun saveSettings(settings: EmergencySettings) {
        try {
            val json = moshiAdapter.toJson(settings)
            prefs.edit().putString("emergency_settings_json", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startEmergencyService() {
        try {
            val intent = Intent(context, EmergencyTriggerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            _isServiceRunning.value = true
        } catch (e: Exception) {
            Log.e("EmergencyTriggerManager", "Failed to start EmergencyTriggerService", e)
        }
    }

    fun stopEmergencyService() {
        try {
            val intent = Intent(context, EmergencyTriggerService::class.java)
            context.stopService(intent)
            _isServiceRunning.value = false
        } catch (e: Exception) {
            Log.e("EmergencyTriggerManager", "Failed to stop EmergencyTriggerService", e)
        }
    }

    fun notifyServiceState(running: Boolean) {
        _isServiceRunning.value = running
    }

    /**
     * Triggers the Emergency SOS flow.
     * Can be invoked from hardware events, shake gestures, or UI test triggers.
     */
    fun triggerEmergency(triggerReason: String = "Hardware Button Trigger", contacts: List<EmergencyContact> = emptyList()) {
        val now = System.currentTimeMillis()
        if (now - lastSosTriggerTime < 15000L) {
            Log.d("EmergencyTriggerManager", "SOS trigger ignored due to 15s cooldown.")
            return
        }
        lastSosTriggerTime = now

        val currentSettings = _settings.value

        // 1. Play immediate SOS Haptic Pattern
        if (currentSettings.isVibrationEnabled) {
            vibrateSosPattern()
        }

        // 2. Launch full-screen EmergencyAlertActivity which shows OVER the lockscreen
        val intent = Intent(context, EmergencyAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("EXTRA_TRIGGER_REASON", triggerReason)
            putExtra("EXTRA_EMERGENCY_NUMBER", currentSettings.emergencyNumber)
            putExtra("EXTRA_COUNTDOWN_SECONDS", currentSettings.countdownSeconds)
            putExtra("EXTRA_SOUND_SIREN", currentSettings.isSirenEnabled)
            putExtra("EXTRA_AUTO_SMS", currentSettings.isAutoSmsEnabled)
            putExtra("EXTRA_SMS_TEXT", currentSettings.emergencySmsText)
        }
        context.startActivity(intent)

        // 3. Dispatch SOS SMS if enabled
        if (currentSettings.isAutoSmsEnabled && contacts.isNotEmpty()) {
            scope.launch {
                sendEmergencySmsToContacts(contacts, currentSettings.emergencySmsText)
            }
        }
    }

    fun vibrateSosPattern() {
        try {
            val sosTimings = longArrayOf(0, 200, 200, 200, 200, 200, 500, 600, 200, 600, 200, 600, 500, 200, 200, 200, 200, 200)
            val sosAmplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createWaveform(sosTimings, sosAmplitudes, -1)
                vibratorManager?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(sosTimings, sosAmplitudes, -1)
                    vibrator?.vibrate(effect)
                } else {
                    vibrator?.vibrate(sosTimings, -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendEmergencySmsToContacts(contacts: List<EmergencyContact>, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            for (contact in contacts) {
                if (contact.number.isNotBlank() && contact.number != "911" && contact.number != "112") {
                    try {
                        val cleanNum = contact.number.replace("[^0-9+]".toRegex(), "")
                        if (cleanNum.length >= 3) {
                            smsManager?.sendTextMessage(cleanNum, null, message, null, null)
                        }
                    } catch (e: Exception) {
                        Log.e("EmergencyTriggerManager", "Failed to send SMS to ${contact.name}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("EmergencyTriggerManager", "SMS dispatch error", e)
        }
    }
}
