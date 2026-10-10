package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.model.SimAccount
import com.example.util.PhoneNumberUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CallSessionState {
    IDLE,
    DIALING,
    RINGING,
    CONNECTED,
    HELD,
    DISCONNECTED
}

data class ActiveCallSession(
    val number: String = "",
    val displayName: String = "",
    val state: CallSessionState = CallSessionState.IDLE,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isHeld: Boolean = false,
    val isIncoming: Boolean = false,
    val canHold: Boolean = true,
    val supportedAudioRoutes: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
    val currentAudioRoute: Int = CallAudioState.ROUTE_EARPIECE,
    val simSlot: Int = 1,
    val isSpam: Boolean = false,
    val signalLevel: Int = 4,
    val signalDbm: Int = -78,
    val networkType: String = "Cellular Network"
)

class CallManager private constructor(context: Context) {

    private val context = context.applicationContext

    companion object {
        private const val TAG = "CallManager"

        @Volatile
        private var activeInstance: CallManager? = null

        /**
         * The ONE CallManager of the whole process. The UI, the InCallService, the notifications and the floating
         * widget must all use this instance. Creating a second one would replace the object that the UI observes,
         * and the call screen would stop receiving updates.
         */
        fun getInstance(context: Context): CallManager {
            return activeInstance ?: synchronized(this) {
                activeInstance ?: CallManager(context.applicationContext).also { activeInstance = it }
            }
        }

        fun onTelecomCallAdded(call: Call, service: NovaDialInCallService) {
            activeInstance?.handleCallAdded(call, service)
        }

        fun onTelecomCallRemoved(call: Call) {
            activeInstance?.handleCallRemoved(call)
        }

        fun handleTelecomCallStateChanged(call: Call, state: Int) {
            activeInstance?.handleCallStateChanged(call, state)
        }

        fun handleTelecomCallDetailsChanged(call: Call, details: Call.Details) {
            activeInstance?.handleCallDetailsChanged(call, details)
        }

        fun onAudioStateChanged(audioState: CallAudioState) {
            activeInstance?.handleAudioStateChanged(audioState)
        }
    }

    private var toneGenerator: ToneGenerator? = null

    private val _activeSession = MutableStateFlow(ActiveCallSession())
    val activeSession: StateFlow<ActiveCallSession> = _activeSession.asStateFlow()

    private val _simAccounts = MutableStateFlow<List<SimAccount>>(emptyList())
    val simAccounts: StateFlow<List<SimAccount>> = _simAccounts.asStateFlow()

    private var activeTelecomCall: Call? = null
    private var activeInCallService: NovaDialInCallService? = null
    private var callTimerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        detectSimCards()
    }

    fun detectSimCards() {
        val list = mutableListOf<SimAccount>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            try {
                val subList: List<SubscriptionInfo>? = subscriptionManager?.activeSubscriptionInfoList
                if (!subList.isNullOrEmpty()) {
                    val defaultVoiceSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
                    for (info in subList) {
                        list.add(
                            SimAccount(
                                slotIndex = info.simSlotIndex + 1,
                                displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                                carrierName = info.carrierName?.toString() ?: "Carrier",
                                number = info.number,
                                isDefault = info.subscriptionId == defaultVoiceSubId
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (list.isEmpty()) {
            // Phone permission missing or no SIM information available: one neutral entry, the phone picks the SIM
            list.add(SimAccount(1, "Default SIM", "", null, isDefault = true))
        }
        _simAccounts.value = list
    }

    /** What the phone really reports. Nothing here is estimated: dBm is 0 when Android does not provide it. */
    private data class SignalSnapshot(val level: Int, val dbm: Int, val networkType: String)

    private fun readSignal(): SignalSnapshot {
        var level = 0
        var dbm = 0
        var networkType = "Cellular network"

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val signalStrength = telephonyManager?.signalStrength
                    if (signalStrength != null) {
                        level = signalStrength.level.coerceIn(0, 4) // 0..4 bars, as the system reports them
                        dbm = signalStrength.cellSignalStrengths.firstOrNull()?.dbm ?: 0
                    }
                }
                // The data network type is shown honestly: it does not prove that the call itself uses VoLTE or HD voice.
                val netType = telephonyManager?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
                networkType = when (netType) {
                    TelephonyManager.NETWORK_TYPE_NR -> "5G"
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                    TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSPAP,
                    TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                    else -> "Cellular network"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Signal information not available", e)
            }
        }
        return SignalSnapshot(level, dbm, networkType)
    }

    /**
     * Official TelecomManager Call Placement.
     * Routes directly through Android Telecom framework to NovaDial's own InCallService.
     */
    fun startCall(
        number: String,
        name: String? = null,
        simSlot: Int = 1,
        isSpam: Boolean = false,
        onCallEnded: ((number: String, name: String?, duration: Long) -> Unit)? = null
    ) {
        val displayName = name?.ifBlank { null } ?: PhoneNumberUtils.formatForDisplay(number)
        val initialSignal = readSignal()
        val isDefault = DefaultDialerManager.isDefaultDialer(context)

        if (!isDefault) {
            Log.w(TAG, "NovaDial is not set as the default dialer. System phone app may take over call UI.")
            DefaultDialerManager.requestDefaultDialer(context)
        }

        _activeSession.value = ActiveCallSession(
            number = number,
            displayName = displayName,
            state = CallSessionState.DIALING,
            simSlot = simSlot,
            isSpam = isSpam,
            isIncoming = false,
            signalLevel = initialSignal.level,
            signalDbm = initialSignal.dbm,
            networkType = initialSignal.networkType
        )

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val uri = Uri.fromParts("tel", number, null)
        val extras = Bundle().apply {
            val phoneAccountHandle = getPhoneAccountHandleForSlot(simSlot)
            if (phoneAccountHandle != null) {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
            }
        }

        try {
            val hasCallPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            if (hasCallPermission && telecomManager != null) {
                Log.d(TAG, "Placing official cellular call via TelecomManager: $number")
                telecomManager.placeCall(uri, extras)
            } else {
                val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error placing call via TelecomManager", e)
            try {
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Could not place the call", ex)
            }
        }

        startDurationTimer()

        // If Android never reports a call (no permission, no SIM, call refused), do not leave a dead call screen open.
        scope.launch {
            delay(12_000)
            val s = _activeSession.value
            if (activeTelecomCall == null && s.state == CallSessionState.DIALING && s.number == number) {
                Log.w(TAG, "No call appeared after placeCall; closing the call screen")
                _activeSession.value = ActiveCallSession(state = CallSessionState.IDLE)
            }
        }
    }

    private fun getPhoneAccountHandleForSlot(slotIndex: Int): PhoneAccountHandle? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return null
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        return try {
            val handles = telecomManager.callCapablePhoneAccounts
            if (subscriptionManager != null) {
                val subInfoList = subscriptionManager.activeSubscriptionInfoList
                if (!subInfoList.isNullOrEmpty()) {
                    val matchingSub = subInfoList.find { it.simSlotIndex + 1 == slotIndex }
                    if (matchingSub != null) {
                        val subIdStr = matchingSub.subscriptionId.toString()
                        val matchedHandle = handles.find { it.id == subIdStr } ?: handles.find { it.id.contains(subIdStr) }
                        if (matchedHandle != null) return matchedHandle
                    }
                }
            }
            handles.getOrNull(slotIndex - 1) ?: handles.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /** Seconds since the call really connected, from Android's own connect time (survives app restarts). */
    private fun realDurationSeconds(call: Call?): Long? {
        val connectTime = call?.details?.connectTimeMillis ?: 0L
        if (connectTime <= 0L) return null
        return ((System.currentTimeMillis() - connectTime) / 1000).coerceAtLeast(0L)
    }

    private fun startDurationTimer() {
        callTimerJob?.cancel()
        callTimerJob = scope.launch {
            while (_activeSession.value.state != CallSessionState.IDLE && _activeSession.value.state != CallSessionState.DISCONNECTED) {
                delay(1000)
                val state = _activeSession.value.state
                if (state == CallSessionState.CONNECTED || state == CallSessionState.HELD) {
                    val real = realDurationSeconds(activeTelecomCall)
                    val signal = readSignal()
                    _activeSession.value = _activeSession.value.copy(
                        durationSeconds = real ?: _activeSession.value.durationSeconds,
                        signalLevel = signal.level,
                        signalDbm = signal.dbm
                    )
                }
            }
            callTimerJob = null
        }
    }

    // Callbacks from NovaDialInCallService
    fun handleCallAdded(call: Call, service: NovaDialInCallService) {
        Log.d(TAG, "handleCallAdded: state=${call.state}")
        activeTelecomCall = call
        activeInCallService = service
        updateSessionFromTelecomCall(call)
    }

    fun handleCallRemoved(call: Call) {
        Log.d(TAG, "handleCallRemoved")
        if (activeTelecomCall == call) {
            activeTelecomCall = null
            activeInCallService = null
            val session = _activeSession.value
            _activeSession.value = session.copy(state = CallSessionState.DISCONNECTED)

            scope.launch {
                delay(600)
                _activeSession.value = ActiveCallSession(state = CallSessionState.IDLE)
            }
        }
    }

    fun handleCallStateChanged(call: Call, state: Int) {
        Log.d(TAG, "handleCallStateChanged: state=$state")
        activeTelecomCall = call
        updateSessionFromTelecomCall(call)
    }

    fun handleCallDetailsChanged(call: Call, details: Call.Details) {
        activeTelecomCall = call
        updateSessionFromTelecomCall(call)
    }

    fun handleAudioStateChanged(audioState: CallAudioState) {
        _activeSession.value = _activeSession.value.copy(
            isMuted = audioState.isMuted,
            isSpeakerOn = audioState.route == CallAudioState.ROUTE_SPEAKER,
            supportedAudioRoutes = audioState.supportedRouteMask,
            currentAudioRoute = audioState.route
        )
    }

    private fun updateSessionFromTelecomCall(call: Call) {
        val handle = call.details?.handle
        val number = handle?.schemeSpecificPart ?: _activeSession.value.number
        val callerInfo = com.example.data.repository.DeviceContactsHelper.lookupCallerInfo(context, number)
        val callerName = call.details?.callerDisplayName?.ifBlank { null }
            ?: callerInfo.first
            ?: _activeSession.value.displayName.ifBlank { null }
            ?: PhoneNumberUtils.formatForDisplay(number)

        val mappedState = when (call.state) {
            Call.STATE_RINGING -> CallSessionState.RINGING
            Call.STATE_DIALING, Call.STATE_CONNECTING -> CallSessionState.DIALING
            Call.STATE_ACTIVE -> CallSessionState.CONNECTED
            Call.STATE_HOLDING -> CallSessionState.HELD
            Call.STATE_DISCONNECTED -> CallSessionState.DISCONNECTED
            else -> CallSessionState.DIALING
        }

        val direction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            call.details?.callDirection
        } else {
            -1
        }
        val previous = _activeSession.value
        val previousStillThisCall = previous.number == number &&
            previous.state != CallSessionState.IDLE && previous.state != CallSessionState.DISCONNECTED
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            direction == android.telecom.Call.Details.DIRECTION_INCOMING
        } else {
            // before Android 10 there is no direction: a call that rang stays "incoming" after it is answered
            call.state == Call.STATE_RINGING || (previousStillThisCall && previous.isIncoming)
        }
        val isHeld = call.state == Call.STATE_HOLDING

        val duration = if (call.state == Call.STATE_ACTIVE || call.state == Call.STATE_HOLDING) {
            realDurationSeconds(call) ?: _activeSession.value.durationSeconds
        } else {
            _activeSession.value.durationSeconds
        }

        _activeSession.value = _activeSession.value.copy(
            number = number,
            displayName = callerName,
            state = mappedState,
            isIncoming = isIncoming,
            isHeld = isHeld,
            durationSeconds = duration
        )

        if (mappedState == CallSessionState.CONNECTED && callTimerJob == null) {
            startDurationTimer()
        }
    }

    fun rejectWithSms(number: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            smsManager?.sendTextMessage(number, null, message, null, null)
            rejectCall()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS on reject", e)
            rejectCall()
        }
    }

    // Call Actions
    fun answerCall() {
        Log.d(TAG, "answerCall() triggered")
        // The UI changes to "connected" only when Telecom confirms it (handleCallStateChanged).
        activeTelecomCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            ?: NovaDialInCallService.answerCurrentCall()
    }

    fun rejectCall() {
        Log.d(TAG, "rejectCall() triggered")
        // The call screen closes when Telecom removes the call (handleCallRemoved).
        activeTelecomCall?.reject(false, null)
            ?: NovaDialInCallService.rejectCurrentCall()
    }

    fun toggleMute() {
        // Only Telecom changes the microphone; handleAudioStateChanged updates the button afterwards.
        NovaDialInCallService.setMuted(!_activeSession.value.isMuted)
    }

    fun toggleSpeaker() {
        val targetRoute = if (_activeSession.value.isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        NovaDialInCallService.setAudioRoute(targetRoute)
    }

    fun setAudioRoute(route: Int) {
        NovaDialInCallService.setAudioRoute(route)
    }

    /** Switches to the next audio output that the phone really offers right now (earpiece, headset, Bluetooth, speaker). */
    fun cycleAudioRoute() {
        val session = _activeSession.value
        val order = listOf(
            CallAudioState.ROUTE_EARPIECE,
            CallAudioState.ROUTE_WIRED_HEADSET,
            CallAudioState.ROUTE_BLUETOOTH,
            CallAudioState.ROUTE_SPEAKER
        ).filter { session.supportedAudioRoutes and it != 0 }
        if (order.size < 2) return
        val next = order[(order.indexOf(session.currentAudioRoute) + 1).mod(order.size)]
        NovaDialInCallService.setAudioRoute(next)
    }

    fun toggleHold() {
        // The HELD / CONNECTED state arrives from Telecom through handleCallStateChanged.
        NovaDialInCallService.toggleHoldCurrentCall(_activeSession.value.isHeld)
    }

    fun playDtmf(key: Char) {
        NovaDialInCallService.playDtmfTone(key)
        val tone = when (key) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> null
        }
        tone?.let { toneGenerator?.startTone(it, 150) }
    }

    fun stopDtmf() {
        NovaDialInCallService.stopDtmfTone()
        toneGenerator?.stopTone()
    }

    fun endCall(): Pair<ActiveCallSession, Long> {
        Log.d(TAG, "endCall() triggered")
        callTimerJob?.cancel()
        callTimerJob = null
        try {
            toneGenerator?.stopTone()
        } catch (e: Exception) {}

        activeTelecomCall?.disconnect()
            ?: NovaDialInCallService.disconnectCurrentCall()

        val session = _activeSession.value
        val duration = session.durationSeconds
        _activeSession.value = session.copy(state = CallSessionState.DISCONNECTED)

        if (session.number.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val db = com.example.data.local.AppDatabase.getDatabase(context)
                    val norm = PhoneNumberUtils.normalize(session.number)
                    val isBlocked = db.blockedNumberDao().isNumberBlocked(norm) > 0
                    val callType = if (session.isIncoming) com.example.data.model.CallType.INCOMING else com.example.data.model.CallType.OUTGOING
                    val log = com.example.data.model.CallLogEntry(
                        number = session.number,
                        normalizedNumber = norm,
                        callerName = session.displayName,
                        callType = if (isBlocked) com.example.data.model.CallType.BLOCKED else callType,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = duration,
                        simSlot = session.simSlot,
                        isSpam = session.isSpam
                    )
                    db.callLogDao().insertCallLog(log)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        scope.launch {
            delay(500)
            _activeSession.value = ActiveCallSession(state = CallSessionState.IDLE)
        }

        return Pair(session, duration)
    }
}
