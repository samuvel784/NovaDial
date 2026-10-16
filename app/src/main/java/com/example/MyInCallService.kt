/*
 * Copyright (C) 2026 MovStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.example.data.AppDatabase
import com.example.model.AppSetting
import com.example.model.CallRecording
import com.example.telecom.CallDecisionStore
import com.example.telecom.CallerAnnouncerManager
import com.example.telecom.RingingPolicy
import com.example.security.IntentGuard
import com.example.ui.components.getCurrentLocale
import com.example.util.CallAudioHelper
import com.example.util.CallAudioRecorder
import com.example.util.ContactRingtonePrefs
import com.example.util.DynamicIslandOverlayManager
import com.example.util.FlashLightManager
import com.example.util.RecordingFeedbackHelper
import com.example.util.RichHapticEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import androidx.core.net.toUri

class MyInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var callWaitingToneGenerator: ToneGenerator? = null
    private var announcer: CallerAnnouncerManager? = null

    @Synchronized
    private fun playCallWaitingTone() {
        try {
            if (callWaitingToneGenerator == null) {
                callWaitingToneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 75)
            }
            callWaitingToneGenerator?.startTone(ToneGenerator.TONE_SUP_CALL_WAITING, 400)
            RichHapticEngine.performHaptic(this, RichHapticEngine.HapticStyle.DOUBLE_TICK)
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun stopCallWaitingTone() {
        try {
            callWaitingToneGenerator?.stopTone()
            callWaitingToneGenerator?.release()
            callWaitingToneGenerator = null
        } catch (_: Exception) {}
    }

    /**
     * True when the platform or the screening service asked for this call to stay
     * silent. On Android 10+ the platform sets [Call.EXTRA_SILENT_RINGING_REQUESTED];
     * older versions (and NovaDial's own screening service) use [CallDecisionStore].
     */
    private fun isCallSilenced(call: Call?): Boolean {
        val number = call?.details?.handle?.schemeSpecificPart ?: ""
        val requested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            call?.details?.extras?.getBoolean(Call.EXTRA_SILENT_RINGING_REQUESTED, false) == true
        } else {
            false
        }
        return requested || CallDecisionStore.wasSilenced(number)
    }

    /** True if the ringtone URI can actually be opened (file may have been moved/deleted). */
    private fun isRingtoneUriReadable(uri: Uri): Boolean = try {
        when (uri.scheme?.lowercase()) {
            "content", "android.resource", "file" -> contentResolver.openInputStream(uri)?.use { true } ?: false
            else -> RingtoneManager.getRingtone(this, uri) != null
        }
    } catch (_: Exception) {
        false
    }

    @Synchronized
    private fun startRinging(call: Call?) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val androidRinger = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            val number = call?.details?.handle?.schemeSpecificPart ?: ""

            val decision = RingingPolicy.decide(
                ringerMode = when (androidRinger) {
                    AudioManager.RINGER_MODE_SILENT -> RingingPolicy.RingerMode.SILENT
                    AudioManager.RINGER_MODE_VIBRATE -> RingingPolicy.RingerMode.VIBRATE
                    else -> RingingPolicy.RingerMode.NORMAL
                },
                silentRequestedByScreening = isCallSilenced(call),
                silencedByRules = CallDecisionStore.wasSilenced(number)
            )

            // A silenced call: no sound, no vibration. Do not create the players at all.
            if (!decision.playSound && !decision.vibrate) return

            val assignment = if (number.isNotBlank()) {
                runCatching { ContactRingtonePrefs.find(this, number) }.getOrNull()
            } else null

            // 1. Ringtone player (contact ringtone when assigned, else the system default)
            if (decision.playSound) {
                if (ringtone == null) {
                    val contactUri = assignment?.ringtoneUri
                        ?.takeIf { it.isNotBlank() }
                        ?.let { it.toUri() }
                        ?.takeIf { isRingtoneUriReadable(it) }
                    val uri = contactUri
                        ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                        // minSdk 24 > LOLLIPOP (21); AudioAttributes is always available.
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setLegacyStreamType(AudioManager.STREAM_RING)
                            .build()
                    }
                }
                if (ringtone?.isPlaying == false) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone?.isLooping = true
                    }
                    ringtone?.play()
                }
            }

            // 2. Hardware vibration (contact pattern when assigned, else the default pattern)
            if (decision.vibrate) {
                val rawPattern = assignment?.pattern
                    ?.let { ContactRingtonePrefs.patternTimings(it) }
                    ?: longArrayOf(0, 1000, 1000, 1000)
                val pattern = RingingPolicy.ringLoopTimings(rawPattern)
                if (pattern != null) {
                    if (vibrator == null) {
                        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = VibrationEffect.createWaveform(pattern, 0)
                        val audioAttrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setLegacyStreamType(AudioManager.STREAM_RING)
                            .build()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val vibrationAttrs = VibrationAttributes.Builder()
                                .setUsage(VibrationAttributes.USAGE_RINGTONE)
                                .build()
                            vibrator?.vibrate(effect, vibrationAttrs)
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(effect, audioAttrs)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, 0)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun stopRinging() {
        try {
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
            }
            ringtone = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}

        stopCallWaitingTone()
    }

    @Synchronized
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                @Suppress("DEPRECATION")
                val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE
                wakeLock = pm?.newWakeLock(flags, "NovaDial:InCallScreenWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(30_000L)
        } catch (_: Exception) {
            try {
                if (wakeLock == null) {
                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovaDial:InCallCpuWakeLock")?.apply {
                        setReferenceCounted(false)
                    }
                }
                wakeLock?.acquire(30_000L)
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onCreate() {
        super.onCreate()
        initNotificationChannels()
        announcer = CallerAnnouncerManager(this)
    }

    override fun onDestroy() {
        stopRinging()
        releaseWakeLock()
        announcer?.shutdown()
        announcer = null
        DynamicIslandOverlayManager.stopCallMonitoring()
        FlashLightManager.stopFlashing(this)
        nm.cancel(NOTIFICATION_ID_INCOMING)
        nm.cancel(NOTIFICATION_ID_ACTIVE)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HANG_UP, ACTION_DECLINE -> {
                stopRinging()
                CallManager.disconnect()
                nm.cancel(NOTIFICATION_ID_INCOMING)
                nm.cancel(NOTIFICATION_ID_ACTIVE)
            }
            ACTION_ANSWER -> {
                stopRinging()
                CallManager.answer()
                launchCallScreen(answerOnLaunch = true)
            }
            ACTION_TOGGLE_MUTE -> {
                val isMuted = CallManager.audioState.value?.isMuted ?: false
                CallManager.setMuted(!isMuted)
                val activeCall = CallManager.currentCall.value
                if (activeCall != null && activeCall.safeState in listOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING)) {
                    showActiveCallNotification(activeCall)
                } else {
                    nm.cancel(NOTIFICATION_ID_ACTIVE)
                }
            }
            ACTION_TOGGLE_SPEAKER -> {
                val isSpeaker = (CallManager.audioState.value?.route ?: CallAudioState.ROUTE_EARPIECE) == CallAudioState.ROUTE_SPEAKER
                CallManager.setSpeaker(!isSpeaker)
                val activeCall = CallManager.currentCall.value
                if (activeCall != null && activeCall.safeState in listOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING)) {
                    showActiveCallNotification(activeCall)
                } else {
                    nm.cancel(NOTIFICATION_ID_ACTIVE)
                }
            }
            ACTION_TOGGLE_RECORD -> toggleCallRecordingFromNotification()
        }
        return START_NOT_STICKY
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        acquireWakeLock()
        CallManager.inCallService = this
        CallManager.addCall(call)
        val num = call.details?.handle?.schemeSpecificPart ?: ""
        val resolvedName = resolveCallerDisplayName(call, num)
        if (resolvedName.isNotBlank() && resolvedName != num && resolvedName != "Unknown") {
            CallManager.setCallerName(resolvedName)
        }
        DynamicIslandOverlayManager.startCallMonitoring(this)

        persistCnapIfPresent(call)
        handleCallState(call)

        call.registerCallback(object : Call.Callback() {
            private var wasRinging = (call.safeState == Call.STATE_RINGING)

            override fun onStateChanged(c: Call, state: Int) {
                super.onStateChanged(c, state)
                if (state == Call.STATE_RINGING) wasRinging = true

                if (state == Call.STATE_ACTIVE || state == Call.STATE_DISCONNECTED) {
                    stopRinging()
                    announcer?.stop()
                    nm.cancel(NOTIFICATION_ID_INCOMING)
                    FlashLightManager.stopFlashing(this@MyInCallService)
                }

                if (state in listOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING)) {
                    stopRinging()
                    showActiveCallNotification(c)
                }

                if (state == Call.STATE_DISCONNECTED) {
                    stopRinging()
                    nm.cancel(NOTIFICATION_ID_ACTIVE)
                    if (wasRinging) {
                        val cause = c.details?.disconnectCause?.code
                        if (cause != DisconnectCause.REJECTED && cause != DisconnectCause.LOCAL) {
                            showMissedCallNotification(c)
                        }
                    }
                    if (CallManager.waitingCall.value == c) {
                        CallManager.updateWaitingCall(null)
                    }
                    c.unregisterCallback(this)
                }

                if (state == Call.STATE_ACTIVE) wasRinging = false
            }
        })
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        stopRinging()
        CallManager.removeCall(call)
        FlashLightManager.stopFlashing(this)
        nm.cancel(NOTIFICATION_ID_INCOMING)

        val remainingActiveCalls = CallManager.calls.value.filter { it.safeState != Call.STATE_DISCONNECTED }
        if (remainingActiveCalls.isEmpty()) {
            releaseWakeLock()
            DynamicIslandOverlayManager.stopCallMonitoring()
            nm.cancel(NOTIFICATION_ID_ACTIVE)

            if (CallAudioRecorder.isRecording.value) {
                try {
                    CallAudioRecorder.stopRecording()
                    CallAudioHelper.restoreAudioState(this, this)
                } catch (_: Exception) {}
            }
        } else {
            val nextCall = remainingActiveCalls.firstOrNull()
            if (nextCall != null) {
                showActiveCallNotification(nextCall)
            } else {
                nm.cancel(NOTIFICATION_ID_ACTIVE)
            }
        }
    }

    @Deprecated("Deprecated in the platform; kept so older Android versions still work.")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.updateAudioState(audioState)
        val activeCall = CallManager.currentCall.value
        if (activeCall != null && activeCall.safeState in listOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING)) {
            showActiveCallNotification(activeCall)
        } else {
            nm.cancel(NOTIFICATION_ID_ACTIVE)
        }
    }

    private fun handleCallState(call: Call) {
        when (call.safeState) {
            Call.STATE_RINGING -> {
                val hasExistingActiveCall = CallManager.calls.value.any {
                    it != call && (it.safeState == Call.STATE_ACTIVE || it.safeState == Call.STATE_HOLDING || it.safeState == Call.STATE_DIALING)
                }
                if (hasExistingActiveCall) {
                    // Call Waiting: Suppress full speaker ringtone and camera flashlight to protect user's hearing
                    CallManager.updateWaitingCall(call)
                    playCallWaitingTone()
                    showIncomingCallNotification(call, isCallWaiting = true)
                } else {
                    if (!isCallSilenced(call)) {
                        FlashLightManager.startFlashing(this)
                    }
                    acquireWakeLock()
                    startRinging(call)
                    showIncomingCallNotification(call, isCallWaiting = false)
                    launchCallScreen(answerOnLaunch = false)

                    announcer?.announceIncomingCall(
                        call.details?.handle?.schemeSpecificPart ?: "",
                        resolveCallerDisplayName(call, call.details?.handle?.schemeSpecificPart ?: "")
                    )
                }
            }
            Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING -> {
                stopRinging()
                stopCallWaitingTone()
                FlashLightManager.stopFlashing(this)
                showActiveCallNotification(call)
                launchCallScreen(answerOnLaunch = false)
            }
        }
    }

    private fun showIncomingCallNotification(call: Call, isCallWaiting: Boolean = false) {
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val displayName = resolveCallerDisplayName(call, number)

        val fullScreenPendingIntent = if (!isCallWaiting) {
            PendingIntent.getActivity(
                this, 101,
                Intent(this, MainActivity::class.java).apply {
                    action = ACTION_INCOMING_CALL
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra(EXTRA_SHOW_CALL, true)
                    putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@MyInCallService))
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null

        val declinePendingIntent = PendingIntent.getService(
            this, 102,
            Intent(this, MyInCallService::class.java).apply { action = ACTION_DECLINE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val answerPendingIntent = PendingIntent.getActivity(
            this, 103,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_ANSWER
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(EXTRA_SHOW_CALL, true)
                putExtra(EXTRA_ANSWER, true)
                putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@MyInCallService))
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val caller = Person.Builder().setName(displayName).setKey(number).build()
        val style = NotificationCompat.CallStyle.forIncomingCall(caller, declinePendingIntent, answerPendingIntent)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            // Single Audio Source: startRinging() handles audio and vibration cleanly to prevent dual-playback echo
            .setContentIntent(answerPendingIntent)

        if (fullScreenPendingIntent != null) {
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        nm.notify(NOTIFICATION_ID_INCOMING, notificationBuilder.build())
    }

    private fun showActiveCallNotification(call: Call) {
        if (call.safeState == Call.STATE_DISCONNECTED || CallManager.calls.value.none { it == call && it.safeState != Call.STATE_DISCONNECTED }) {
            nm.cancel(NOTIFICATION_ID_ACTIVE)
            return
        }
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val displayName = resolveCallerDisplayName(call, number)

        val contentPendingIntent = PendingIntent.getActivity(
            this, 200,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_SHOW_CALL, true)
                putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@MyInCallService))
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hangUpPendingIntent = PendingIntent.getService(
            this, 201,
            Intent(this, MyInCallService::class.java).apply { action = ACTION_HANG_UP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val recordPendingIntent = PendingIntent.getService(
            this, 202,
            Intent(this, MyInCallService::class.java).apply { action = ACTION_TOGGLE_RECORD },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val speakerPendingIntent = PendingIntent.getService(
            this, 203,
            Intent(this, MyInCallService::class.java).apply { action = ACTION_TOGGLE_SPEAKER },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mutePendingIntent = PendingIntent.getService(
            this, 204,
            Intent(this, MyInCallService::class.java).apply { action = ACTION_TOGGLE_MUTE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isMuted = CallManager.audioState.value?.isMuted ?: false
        val isSpeaker = (CallManager.audioState.value?.route ?: CallAudioState.ROUTE_EARPIECE) == CallAudioState.ROUTE_SPEAKER
        val isRecording = CallAudioRecorder.isRecording.value
        val isConference = CallManager.isConference(call) || CallManager.isConferenceActive.value

        val recordTitle = if (isRecording) "■ Stop Rec" else "● Record"
        val speakerTitle = if (isSpeaker) "Earpiece" else "Speaker"
        val muteTitle = if (isMuted) "Unmute" else "Mute"

        val titlePrefix = if (isConference) getString(R.string.call_status_conference) else "Ongoing Call"
        val contentTitle = if (isRecording) "$titlePrefix • [REC]" else titlePrefix
        val contentText = if (isConference) "Conference call with $displayName is active" else "Call with $displayName is active"

        val notification = NotificationCompat.Builder(this, CHANNEL_ACTIVE)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, recordTitle, recordPendingIntent)
            .addAction(android.R.drawable.stat_notify_call_mute, muteTitle, mutePendingIntent)
            .addAction(android.R.drawable.stat_sys_speakerphone, speakerTitle, speakerPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hang Up", hangUpPendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID_ACTIVE, notification)
    }

    private fun showMissedCallNotification(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val displayName = resolveCallerDisplayName(call, number)

        val contentPendingIntent = PendingIntent.getActivity(
            this, 301,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("SHOW_CALL_LOG", true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callBackPendingIntent = PendingIntent.getActivity(
            this, 302,
            Intent(Intent.ACTION_DIAL).apply {
                data = "tel:$number".toUri()
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(getString(R.string.call_type_missed))
            .setContentText("${getString(R.string.call_type_missed)}: $displayName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.sym_action_call, getString(R.string.btn_call_back), callBackPendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID_MISSED, notification)
    }

    private fun toggleCallRecordingFromNotification() {
        val isRec = CallAudioRecorder.isRecording.value
        val prefs = getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        val chimeEnabled = prefs.getBoolean("recording_chime_enabled", false)
        val autoTune = prefs.getBoolean("auto_tune_recording_volume", true)

        if (isRec) {
            RecordingFeedbackHelper.triggerRecordingStopFeedback(this, chimeEnabled)
            val result = CallAudioRecorder.stopRecording()
            if (autoTune) {
                CallAudioHelper.restoreAudioState(this, this)
            }
            val file = result.file
            if (file != null && file.exists() && file.length() > 0L) {
                val durationSec = result.durationSeconds.coerceAtLeast(1L)
                val number = CallManager.callerNumber.value.ifEmpty { "Unknown" }
                val name = CallManager.callerName.value.ifEmpty { number }
                val locale = getCurrentLocale(this)
                val timestamp = SimpleDateFormat("MMM d, HH:mm", locale).format(Date())

                val recording = CallRecording(
                    number = number,
                    name = name,
                    timestamp = timestamp,
                    duration = durationSec,
                    filePath = file.absolutePath
                )

                serviceScope.launch {
                    try {
                        AppDatabase.getDatabase(this@MyInCallService).dialerDao().insertCallRecording(recording)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } else {
            RecordingFeedbackHelper.triggerRecordingStartFeedback(this, chimeEnabled)
            if (autoTune) {
                CallAudioHelper.prepareSpeakerForRecording(this, this, CallManager.audioState.value)
            }
            val number = CallManager.callerNumber.value.ifEmpty { "Unknown" }
            CallAudioRecorder.startRecording(this, number)
        }
        CallManager.currentCall.value?.let { showActiveCallNotification(it) }
    }

    private fun resolveCallerDisplayName(call: Call, number: String): String {
        if (number.isEmpty()) return "Unknown"
        val cnapName = call.details?.callerDisplayName
        val contactName = getContactNameFromNumber(this, number)
        val savedCnap = if (contactName == null && cnapName.isNullOrBlank()) {
            getSavedCnapNameSync(this, number)
        } else null

        return when {
            !contactName.isNullOrBlank() -> contactName
            !cnapName.isNullOrBlank() -> cnapName
            !savedCnap.isNullOrBlank() -> savedCnap
            else -> number
        }
    }

    private fun persistCnapIfPresent(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: return
        val cnapName = call.details?.callerDisplayName ?: return
        if (cnapName.isNotBlank() && number.isNotBlank()) {
            ContactCache.putCnapName(number, cnapName)
            serviceScope.launch {
                try {
                    AppDatabase.getDatabase(this@MyInCallService)
                        .dialerDao()
                        .insertSetting(AppSetting("cnap_" + number.filter { it.isDigit() }, cnapName))
                } catch (_: Exception) {}
            }
        }
    }

    private fun launchCallScreen(answerOnLaunch: Boolean) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = if (answerOnLaunch) ACTION_ANSWER else ACTION_INCOMING_CALL
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(EXTRA_SHOW_CALL, true)
                putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@MyInCallService))
                if (answerOnLaunch) putExtra(EXTRA_ANSWER, true)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { nm.deleteNotificationChannel("incoming_call_channel_v2") } catch (_: Exception) {}
            try { nm.deleteNotificationChannel("incoming_call_channel_v3") } catch (_: Exception) {}

            val incomingChannel = NotificationChannel(CHANNEL_INCOMING, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming call alerts and full screen notifications"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            nm.createNotificationChannels(listOf(
                incomingChannel,
                NotificationChannel(CHANNEL_ACTIVE, "Active Calls", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ongoing call controls"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_MISSED, "Missed Calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Missed call notifications"
                }
            ))
        }
    }

    companion object {
        const val CHANNEL_INCOMING = "incoming_call_channel_v4"
        const val CHANNEL_ACTIVE = "active_call_channel"
        const val CHANNEL_MISSED = "missed_call_channel"

        const val NOTIFICATION_ID_INCOMING = 1
        const val NOTIFICATION_ID_MISSED = 2
        const val NOTIFICATION_ID_ACTIVE = 3

        const val ACTION_HANG_UP = "com.example.ACTION_HANG_UP"
        const val ACTION_ANSWER = "com.example.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.example.ACTION_DECLINE"
        const val ACTION_TOGGLE_RECORD = "com.example.ACTION_TOGGLE_RECORD"
        const val ACTION_TOGGLE_SPEAKER = "com.example.ACTION_TOGGLE_SPEAKER"
        const val ACTION_TOGGLE_MUTE = "com.example.ACTION_TOGGLE_MUTE"
        const val ACTION_INCOMING_CALL = "com.example.INCOMING_CALL"

        const val EXTRA_SHOW_CALL = "SHOW_CALL_SCREEN"
        const val EXTRA_ANSWER = "ANSWER_ON_LAUNCH"
    }
}
