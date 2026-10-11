/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
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
 * firmly. It runs as a foreground service (with a visible notification) so Android does not kill
 * it, and is only ever started when the user enables "Shake to trigger SOS".
 *
 * Nothing is sent until the countdown finishes, and the notification offers a Cancel action, so an
 * accidental shake can always be reversed. No data leaves the device beyond the user's own alert.
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
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.sos_shake_service_text)))

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelCountdown()
        }
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
                updateNotification(getString(R.string.sos_countdown_body, remaining))
                delay(1_000)
                remaining--
            }
            runSos()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        updateNotification(getString(R.string.sos_shake_service_text))
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

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sos_shake_service_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.sos_shake_service_text)
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            pendingFlags()
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, EmergencySosService::class.java).setAction(ACTION_CANCEL),
            pendingFlags()
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.sos_shake_service_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.btn_cancel), cancelIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    companion object {
        private const val CHANNEL_ID = "novadial_sos_shake"
        private const val NOTIFICATION_ID = 8471
        private const val ACTION_CANCEL = "com.example.telecom.action.SOS_CANCEL"

        // A firm shake produces short bursts well above 2.6 g. Two hits in quick succession are
        // required so a single bump does not fire the alert, and a cooldown stops double-firing.
        private const val SHAKE_G_THRESHOLD = 2.6f
        private const val SHAKE_HITS_REQUIRED = 2
        private const val SHAKE_COOLDOWN_MS = 1_200L
        private const val SHAKE_RESET_MS = 1_500L

        /** Starts the opt-in shake watch (safe to call repeatedly). */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
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
