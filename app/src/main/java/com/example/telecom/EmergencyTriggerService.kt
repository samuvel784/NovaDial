package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlin.math.sqrt

class EmergencyTriggerService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "emergency_trigger_channel"
        const val NOTIFICATION_ID = 9110
        private const val TRIPLE_PRESS_WINDOW_MS = 3500L
        private const val SHAKE_THRESHOLD = 16.0f
    }

    private var triggerManager: EmergencyTriggerManager? = null
    private val pressTimestamps = mutableListOf<Long>()
    private var lastShakeTimestamp = 0L
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_SCREEN_ON) {
                handlePowerButtonPress()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        triggerManager = EmergencyTriggerManager(this)
        createNotificationChannel()

        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Allow notification to be swiped away / removed by user at any time while service stays active
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }

        // Register hardware power button listener via screen state broadcasts
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        // Initialize Shake Sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        triggerManager?.notifyServiceState(true)
        Log.d("EmergencyTriggerService", "Service started and listening for hardware triggers.")
    }

    private fun handlePowerButtonPress() {
        val now = System.currentTimeMillis()
        synchronized(pressTimestamps) {
            // Remove timestamps older than window
            pressTimestamps.removeAll { now - it > TRIPLE_PRESS_WINDOW_MS }
            pressTimestamps.add(now)

            Log.d("EmergencyTriggerService", "Power press detected. Count in window: ${pressTimestamps.size}")

            if (pressTimestamps.size >= 3) {
                Log.w("EmergencyTriggerService", "TRIPLE POWER PRESS DETECTED! Triggering Emergency SOS...")
                pressTimestamps.clear()
                triggerSosDispatch("Triple-Press Power Button Trigger")
            }
        }
    }

    private fun triggerSosDispatch(reason: String) {
        triggerManager?.triggerEmergency(triggerReason = reason)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val settings = triggerManager?.settings?.value ?: return
        if (settings.triggerMode != EmergencyTriggerMode.SHAKE_GESTURE && settings.triggerMode != EmergencyTriggerMode.BOTH) {
            return
        }

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > (SHAKE_THRESHOLD / SensorManager.GRAVITY_EARTH)) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTimestamp > 3000) {
                lastShakeTimestamp = now
                Log.w("EmergencyTriggerService", "SHAKE GESTURE DETECTED! Triggering Emergency SOS...")
                triggerSosDispatch("Rapid Shake Motion Trigger")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency Protection",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Running in background"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Emergency SOS Protection Active")
            .setContentText("Triple-press power button or shake for Emergency SOS.")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(false)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sensorManager?.unregisterListener(this)
        triggerManager?.notifyServiceState(false)
        Log.d("EmergencyTriggerService", "EmergencyTriggerService stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
