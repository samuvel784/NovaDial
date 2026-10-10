package com.example.telecom

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import com.example.data.model.FlashAlertSettings
import com.example.data.model.FlashPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FlashAlertManager(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var cameraId: String? = null
    private var flashJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _settings = MutableStateFlow(FlashAlertSettings())
    val settings: StateFlow<FlashAlertSettings> = _settings.asStateFlow()

    private val _isFlashing = MutableStateFlow(false)
    val isFlashing: StateFlow<Boolean> = _isFlashing.asStateFlow()

    init {
        try {
            cameraId = cameraManager?.cameraIdList?.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateSettings(newSettings: FlashAlertSettings) {
        _settings.value = newSettings
    }

    fun triggerIncomingCallFlash() {
        val cfg = _settings.value
        if (!cfg.enableFlashAlert) return

        if (cfg.flashOnSilentOnly) {
            val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                return // Skip because phone is not in silent or vibrate mode
            }
        }

        stopFlash()
        _isFlashing.value = true

        flashJob = scope.launch {
            try {
                when (cfg.pattern) {
                    FlashPattern.PULSE -> {
                        while (_isFlashing.value) {
                            toggleTorch(true)
                            delay(cfg.flashSpeedMs)
                            toggleTorch(false)
                            delay(cfg.flashSpeedMs)
                        }
                    }
                    FlashPattern.STROBE -> {
                        while (_isFlashing.value) {
                            toggleTorch(true)
                            delay(80)
                            toggleTorch(false)
                            delay(80)
                        }
                    }
                    FlashPattern.SOS -> {
                        while (_isFlashing.value) {
                            // ...
                            repeat(3) { toggleTorch(true); delay(150); toggleTorch(false); delay(150) }
                            repeat(3) { toggleTorch(true); delay(400); toggleTorch(false); delay(200) }
                            repeat(3) { toggleTorch(true); delay(150); toggleTorch(false); delay(150) }
                            delay(1000)
                        }
                    }
                    FlashPattern.RHYTHM -> {
                        while (_isFlashing.value) {
                            toggleTorch(true); delay(120); toggleTorch(false); delay(80)
                            toggleTorch(true); delay(250); toggleTorch(false); delay(300)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                toggleTorch(false)
                _isFlashing.value = false
            }
        }
    }

    fun stopFlash() {
        flashJob?.cancel()
        flashJob = null
        toggleTorch(false)
        _isFlashing.value = false
    }

    private fun toggleTorch(enable: Boolean) {
        try {
            val id = cameraId ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager?.setTorchMode(id, enable)
            }
        } catch (e: Exception) {
            // Permission or hardware unavailable in emulator
        }
    }
}
