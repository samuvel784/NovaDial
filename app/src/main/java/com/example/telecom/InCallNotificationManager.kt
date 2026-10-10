package com.example.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.util.PhoneNumberUtils

object InCallNotificationManager {

    private const val INCOMING_CHANNEL_ID = "nova_incoming_call_channel"
    private const val ONGOING_CHANNEL_ID = "nova_ongoing_call_channel"

    const val INCOMING_NOTIFICATION_ID = 2001
    const val ONGOING_NOTIFICATION_ID = 2002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Incoming Call Channel (High Importance for Heads-up notification & Lockscreen)
            val incomingChannel = NotificationChannel(
                INCOMING_CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notifications and lockscreen overlay for incoming calls"
                setShowBadge(true)
                enableVibration(true)
                setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE), null)
            }

            // 2. Ongoing Active Call Channel
            val ongoingChannel = NotificationChannel(
                ONGOING_CHANNEL_ID,
                "Active Call Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status bar controls during active calls"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            manager.createNotificationChannel(incomingChannel)
            manager.createNotificationChannel(ongoingChannel)
        }
    }

    fun showIncomingCallNotification(context: Context, session: ActiveCallSession) {
        createChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Full Screen Intent to wake lockscreen and show MainActivity
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("EXTRA_IN_CALL_MODE", true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Action PendingIntent
        val answerIntent = Intent(context, InCallActionReceiver::class.java).apply {
            action = InCallActionReceiver.ACTION_ANSWER_CALL
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Reject Action PendingIntent
        val rejectIntent = Intent(context, InCallActionReceiver::class.java).apply {
            action = InCallActionReceiver.ACTION_REJECT_CALL
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerTitle = session.displayName.ifBlank { PhoneNumberUtils.formatForDisplay(session.number) }
        val subText = if (session.isSpam) "⚠️ SPAM CALLER" else PhoneNumberUtils.formatForDisplay(session.number)

        val person = androidx.core.app.Person.Builder().setName(callerTitle).build()
        val callStyle = NotificationCompat.CallStyle.forIncomingCall(person, rejectPendingIntent, answerPendingIntent)

        val notification = NotificationCompat.Builder(context, INCOMING_CHANNEL_ID)
            .setContentTitle(callerTitle)
            .setContentText(subText)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setStyle(callStyle)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_call, "Accept", answerPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", rejectPendingIntent)
            .build()

        manager.notify(INCOMING_NOTIFICATION_ID, notification)
    }

    fun updateOngoingNotification(context: Context) {
        val session = CallManager.getInstance(context).activeSession.value
        if (session.state == CallSessionState.IDLE || session.state == CallSessionState.DISCONNECTED) {
            cancelAllNotifications(context)
            return
        }
        showOngoingNotification(context, session)
    }

    fun showOngoingNotification(context: Context, session: ActiveCallSession) {
        createChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Open App PendingIntent
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("EXTRA_IN_CALL_MODE", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // End Call PendingIntent
        val endIntent = Intent(context, InCallActionReceiver::class.java).apply {
            action = InCallActionReceiver.ACTION_END_CALL
        }
        val endPendingIntent = PendingIntent.getBroadcast(
            context,
            11,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mute / Unmute PendingIntent
        val muteIntent = Intent(context, InCallActionReceiver::class.java).apply {
            action = InCallActionReceiver.ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getBroadcast(
            context,
            12,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerTitle = session.displayName.ifBlank { PhoneNumberUtils.formatForDisplay(session.number) }
        val stateText = when (session.state) {
            CallSessionState.DIALING -> "Dialing..."
            CallSessionState.RINGING -> "Ringing..."
            CallSessionState.CONNECTED -> "Active Call (${formatDuration(session.durationSeconds)})"
            CallSessionState.HELD -> "Call on Hold"
            CallSessionState.DISCONNECTED -> "Call Ended"
            CallSessionState.IDLE -> ""
        }

        val muteLabel = if (session.isMuted) "Unmute" else "Mute"

        val person = androidx.core.app.Person.Builder().setName(callerTitle).build()
        val callStyle = NotificationCompat.CallStyle.forOngoingCall(person, endPendingIntent)

        val notification = NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setContentTitle(callerTitle)
            .setContentText(stateText)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setStyle(callStyle)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, muteLabel, mutePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", endPendingIntent)
            .build()

        // Cancel incoming notification if call answered/connected
        manager.cancel(INCOMING_NOTIFICATION_ID)
        manager.notify(ONGOING_NOTIFICATION_ID, notification)
    }

    fun cancelAllNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.cancel(INCOMING_NOTIFICATION_ID)
        manager.cancel(ONGOING_NOTIFICATION_ID)
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)
    }
}
