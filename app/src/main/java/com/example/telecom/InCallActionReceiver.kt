package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class InCallActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "InCallActionReceiver"
        const val ACTION_ANSWER_CALL = "com.example.ACTION_ANSWER_CALL"
        const val ACTION_REJECT_CALL = "com.example.ACTION_REJECT_CALL"
        const val ACTION_END_CALL = "com.example.ACTION_END_CALL"
        const val ACTION_TOGGLE_MUTE = "com.example.ACTION_TOGGLE_MUTE"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Notification action received: $action")

        when (action) {
            ACTION_ANSWER_CALL -> {
                NovaDialInCallService.answerCurrentCall()
                InCallNotificationManager.updateOngoingNotification(context ?: return)
            }
            ACTION_REJECT_CALL -> {
                NovaDialInCallService.rejectCurrentCall()
                InCallNotificationManager.cancelAllNotifications(context ?: return)
            }
            ACTION_END_CALL -> {
                NovaDialInCallService.disconnectCurrentCall()
                InCallNotificationManager.cancelAllNotifications(context ?: return)
            }
            ACTION_TOGGLE_MUTE -> {
                val currentAudio = NovaDialInCallService.currentAudioState.value
                val newMute = !(currentAudio?.isMuted ?: false)
                NovaDialInCallService.setMuted(newMute)
                InCallNotificationManager.updateOngoingNotification(context ?: return)
            }
        }
    }
}
