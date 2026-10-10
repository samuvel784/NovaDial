package com.example.telecom

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Official Android InCallService for NovaDial.
 *
 * Registered in AndroidManifest.xml with android.permission.BIND_INCALL_SERVICE
 * and meta-data IN_CALL_SERVICE_UI = true.
 *
 * Manages the full cellular call lifecycle (incoming ringing, outgoing dialing,
 * active connection, holding, audio routing, and termination) and provides
 * callbacks to trigger the NovaDial in-call UI.
 */
class NovaDialInCallService : InCallService() {

    interface InCallServiceListener {
        fun onCallAdded(call: Call)
        fun onCallRemoved(call: Call)
        fun onCallStateChanged(call: Call, state: Int)
        fun onCallDetailsChanged(call: Call, details: Call.Details)
        fun onAudioStateChanged(audioState: CallAudioState)
        fun onRingerSilenced() {}
    }

    companion object {
        private const val TAG = "NovaDialInCallService"

        var instance: NovaDialInCallService? = null
            private set

        private val _currentCall = MutableStateFlow<Call?>(null)
        val currentCall: StateFlow<Call?> = _currentCall.asStateFlow()

        private val _allCalls = MutableStateFlow<List<Call>>(emptyList())
        val allCalls: StateFlow<List<Call>> = _allCalls.asStateFlow()

        private val _currentAudioState = MutableStateFlow<CallAudioState?>(null)
        val currentAudioState: StateFlow<CallAudioState?> = _currentAudioState.asStateFlow()

        private val listeners = CopyOnWriteArrayList<InCallServiceListener>()

        fun addListener(listener: InCallServiceListener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }

        fun removeListener(listener: InCallServiceListener) {
            listeners.remove(listener)
        }

        // --- Call Control APIs ---

        fun answerCurrentCall() {
            try {
                _currentCall.value?.answer(VideoProfile.STATE_AUDIO_ONLY)
            } catch (e: Exception) {
                Log.e(TAG, "Error answering call", e)
            }
        }

        fun rejectCurrentCall() {
            try {
                _currentCall.value?.reject(false, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting call", e)
            }
        }

        fun disconnectCurrentCall() {
            try {
                _currentCall.value?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting call", e)
            }
        }

        fun toggleHoldCurrentCall(isHeld: Boolean) {
            try {
                val call = _currentCall.value ?: return
                if (isHeld) {
                    call.unhold()
                } else {
                    call.hold()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling call hold state", e)
            }
        }

        fun playDtmfTone(digit: Char) {
            try {
                _currentCall.value?.playDtmfTone(digit)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing DTMF tone for $digit", e)
            }
        }

        fun stopDtmfTone() {
            try {
                _currentCall.value?.stopDtmfTone()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping DTMF tone", e)
            }
        }

        fun setMuted(muted: Boolean) {
            try {
                instance?.setMuted(muted)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting mute state to $muted", e)
            }
        }

        fun setAudioRoute(route: Int) {
            try {
                instance?.setAudioRoute(route)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting audio route to $route", e)
            }
        }

        fun mergeCalls() {
            try {
                val calls = _allCalls.value
                if (calls.size >= 2) {
                    val primary = calls[0]
                    val secondary = calls[1]
                    primary.conference(secondary)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error merging calls", e)
            }
        }

        fun swapCalls() {
            try {
                val calls = _allCalls.value
                if (calls.size >= 2) {
                    val activeCall = calls.find { it.state == Call.STATE_ACTIVE }
                    val heldCall = calls.find { it.state == Call.STATE_HOLDING }
                    activeCall?.hold()
                    heldCall?.unhold()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error swapping calls", e)
            }
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "Call onStateChanged: state=$state for call=$call")
            updateCallsState()

            // Update CallManager
            CallManager.handleTelecomCallStateChanged(call, state)

            // Notify registered listeners
            listeners.forEach { it.onCallStateChanged(call, state) }

            // Manage Notifications & Full Screen Intent
            val session = CallManager.getInstance(this@NovaDialInCallService).activeSession.value
            when (state) {
                Call.STATE_RINGING -> {
                    InCallNotificationManager.showIncomingCallNotification(this@NovaDialInCallService, session)
                    announceOnce(call, session)
                    bringAppToForeground()
                }
                Call.STATE_ACTIVE, Call.STATE_HOLDING, Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                    announcerManager?.stop()
                    InCallNotificationManager.showOngoingNotification(this@NovaDialInCallService, session)
                    FloatingCallWidgetService.start(this@NovaDialInCallService)
                }
                Call.STATE_DISCONNECTED -> {
                    announcerManager?.stop()
                    InCallNotificationManager.cancelAllNotifications(this@NovaDialInCallService)
                    FloatingCallWidgetService.stop(this@NovaDialInCallService)
                }
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            Log.d(TAG, "Call onDetailsChanged for call=$call")
            updateCallsState()

            CallManager.handleTelecomCallDetailsChanged(call, details)
            listeners.forEach { it.onCallDetailsChanged(call, details) }
        }
    }

    private var announcerManager: CallerAnnouncerManager? = null
    private var announcedCall: Call? = null

    /** Speaks the caller's name once per ringing call (never again for state changes of the same call). */
    private fun announceOnce(call: Call, session: ActiveCallSession) {
        if (announcedCall === call) return
        announcedCall = call
        announcerManager?.announceIncomingCall(session.number, session.displayName)
    }

    /** Settings test button. */
    fun testAnnouncement() {
        announcerManager?.testAnnouncement()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        announcerManager = CallerAnnouncerManager(this)
        updateCallsState()
        Log.d(TAG, "NovaDialInCallService initialized and ready")
    }

    override fun onDestroy() {
        super.onDestroy()
        announcerManager?.shutdown()
        announcerManager = null
        instance = null
        _currentCall.value = null
        _allCalls.value = emptyList()
        _currentAudioState.value = null
        Log.d(TAG, "NovaDialInCallService destroyed")
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: state=${call.state}")
        call.registerCallback(callCallback)
        updateCallsState()

        // Inform CallManager singleton
        CallManager.onTelecomCallAdded(call, this)

        // Inform registered listeners
        listeners.forEach { it.onCallAdded(call) }

        val session = CallManager.getInstance(this).activeSession.value
        if (call.state == Call.STATE_RINGING) {
            InCallNotificationManager.showIncomingCallNotification(this, session)
            announceOnce(call, session)
        } else {
            InCallNotificationManager.showOngoingNotification(this, session)
        }

        // Launch NovaDial in-call UI immediately
        bringAppToForeground()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved: state=${call.state}")
        call.unregisterCallback(callCallback)
        announcerManager?.stop()
        if (announcedCall === call) announcedCall = null
        updateCallsState()

        CallManager.onTelecomCallRemoved(call)
        listeners.forEach { it.onCallRemoved(call) }

        if (calls.isNullOrEmpty()) {
            InCallNotificationManager.cancelAllNotifications(this)
            FloatingCallWidgetService.stop(this)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        Log.d(TAG, "onCallAudioStateChanged: route=${audioState.route}, isMuted=${audioState.isMuted}")
        _currentAudioState.value = audioState

        CallManager.onAudioStateChanged(audioState)
        listeners.forEach { it.onAudioStateChanged(audioState) }
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()
        Log.d(TAG, "onSilenceRinger called by system")
        listeners.forEach { it.onRingerSilenced() }
    }

    private fun updateCallsState() {
        val currentCallsList = try {
            calls ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        _allCalls.value = currentCallsList
        _currentCall.value = currentCallsList.firstOrNull { it.state != Call.STATE_DISCONNECTED }
            ?: currentCallsList.firstOrNull()

        currentCallsList.firstOrNull()?.let { call ->
            CallManager.handleTelecomCallStateChanged(call, call.state)
        }
    }

    /**
     * Brings NovaDial into the foreground so user interacts with NovaDial's own in-call UI.
     */
    private fun bringAppToForeground() {
        try {
            com.example.ui.screens.CallActivity.start(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error bringing NovaDial to foreground via CallActivity", e)
        }
    }
}
