package com.example.telecom

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.data.model.GestureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class GestureControlManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _settings = MutableStateFlow(GestureSettings())
    val settings: StateFlow<GestureSettings> = _settings.asStateFlow()

    private var onFlipToMuteListener: (() -> Unit)? = null
    private var onRaiseToAnswerListener: (() -> Unit)? = null
    private var onShakeToEndCallListener: (() -> Unit)? = null

    private var isListening = false
    private var lastShakeTime = 0L
    private var lastFlipTime = 0L
    private var lastProximityNear = false

    fun updateSettings(newSettings: GestureSettings) {
        _settings.value = newSettings
    }

    fun startListening(
        onFlipToMute: (() -> Unit)? = null,
        onRaiseToAnswer: (() -> Unit)? = null,
        onShakeToEndCall: (() -> Unit)? = null
    ) {
        if (isListening) stopListening()
        this.onFlipToMuteListener = onFlipToMute
        this.onRaiseToAnswerListener = onRaiseToAnswer
        this.onShakeToEndCallListener = onShakeToEndCall

        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        proximity?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        isListening = true
    }

    fun stopListening() {
        if (isListening) {
            sensorManager?.unregisterListener(this)
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val cfg = _settings.value

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val now = System.currentTimeMillis()

                // 1. Flip to Mute check (device lies face down, z < -7.0f)
                if (cfg.flipToMute && z < -7.0f && (now - lastFlipTime > 2000)) {
                    lastFlipTime = now
                    onFlipToMuteListener?.invoke()
                }

                // 2. Shake to End Call check (magnitude > 18.0 m/s^2)
                if (cfg.shakeToEndCall && (now - lastShakeTime > 1500)) {
                    val gVector = sqrt(x * x + y * y + z * z)
                    if (gVector > 18.0f) {
                        lastShakeTime = now
                        onShakeToEndCallListener?.invoke()
                    }
                }

                // 3. Raise to answer combined check
                if (cfg.raiseToAnswer && lastProximityNear && y > 5.0f && z > 1.0f) {
                    onRaiseToAnswerListener?.invoke()
                }
            }

            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                lastProximityNear = distance < maxRange && distance < 4.0f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
