/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Opt-in background watch that starts the Emergency SOS countdown when the phone is shaken
 * firmly. Runs as a background service without posting any persistent notifications, as requested.
 */
class EmergencySosService : Service(), SensorEventListener {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var countdownJob: Job? = null
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var lastShakeAt = 0L
    private var shakeHits = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.size < 3) return

        val gForce = sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
        ) / SensorManager.GRAVITY_EARTH

        val now = System.currentTimeMillis()
        if (gForce > SHAKE_G_THRESHOLD) {
            if (now - lastShakeAt > SHAKE_COOLDOWN_MS) {
                lastShakeAt = now
                shakeHits = 1
            } else {
                shakeHits++
            }
            if (shakeHits >= SHAKE_HITS_REQUIRED) {
                shakeHits = 0
                lastShakeAt = now
                startCountdown()
            }
        } else if (now - lastShakeAt > SHAKE_RESET_MS) {
            shakeHits = 0
        }
    }

    private fun startCountdown() {
        if (countdownJob?.isActive == true) return

        val total = EmergencySosPrefs.getCountdownSeconds(this)
        if (total <= 0) {
            runSos()
            return
        }
        countdownJob = scope.launch {
            var remaining = total
            while (remaining > 0) {
                delay(1_000)
                remaining--
            }
            runSos()
        }
    }

    private fun runSos() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    EmergencySosManager.trigger(this@EmergencySosService)
                } catch (_: Exception) {
                }
            }
            stopSelf()
        }
    }

    companion object {
        private const val SHAKE_G_THRESHOLD = 2.6f
        private const val SHAKE_HITS_REQUIRED = 2
        private const val SHAKE_COOLDOWN_MS = 1_200L
        private const val SHAKE_RESET_MS = 1_500L

        /** Starts the opt-in shake watch without posting any notifications. */
        fun start(context: Context) {
            try {
                context.startService(
                    Intent(context, EmergencySosService::class.java)
                )
            } catch (_: Exception) {
            }
        }

        /** Stops the shake watch. */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, EmergencySosService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
