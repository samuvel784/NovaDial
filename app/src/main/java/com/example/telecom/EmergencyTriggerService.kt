package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class EmergencyTriggerService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "emergency_trigger_channel"
        const val NOTIFICATION_ID = 9110
        private const val SHAKE_THRESHOLD = 16.0f
    }

    private var triggerManager: EmergencyTriggerManager? = null
    private var lastShakeTimestamp = 0L
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

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

        // Initialize Shake Sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        triggerManager?.notifyServiceState(true)
        Log.d("EmergencyTriggerService", "EmergencyTriggerService started and listening for shake gestures.")
    }

    private fun triggerSosDispatch(reason: String) {
        triggerManager?.triggerEmergency(triggerReason = reason)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val settings = triggerManager?.settings?.value ?: return
        if (!settings.isServiceEnabled) {
            stopSelf()
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
            if (now - lastShakeTimestamp > 5000L) {
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background protection for Emergency SOS"
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
            .setContentTitle("Emergency SOS Active")
            .setContentText("Listening for emergency shake gestures.")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        triggerManager?.notifyServiceState(false)
        Log.d("EmergencyTriggerService", "EmergencyTriggerService stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
