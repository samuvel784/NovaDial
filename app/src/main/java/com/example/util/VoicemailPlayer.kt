package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/** Plays one voicemail at a time from the phone's voicemail provider. Use from the main thread only. */
object VoicemailPlayer {
    private const val TAG = "VoicemailPlayer"
    private var player: MediaPlayer? = null
    private var finished = false

    /** Starts playing [uriString]. Returns false when the file cannot be opened (no audio is claimed to play). */
    fun play(context: Context, uriString: String): Boolean {
        stop()
        if (uriString.isBlank()) return false
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(context.applicationContext, Uri.parse(uriString))
            mp.setOnCompletionListener { finished = true }
            mp.prepare()
            mp.start()
            finished = false
            player = mp
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not play voicemail", e)
            stop()
            false
        }
    }

    fun isPlaying(): Boolean = !finished && (player?.isPlaying == true)

    /** 0f..1f, the real playback position. */
    fun progress(): Float {
        val mp = player ?: return 0f
        return try {
            val total = mp.duration
            if (total <= 0) 0f else (mp.currentPosition.toFloat() / total).coerceIn(0f, 1f)
        } catch (e: Exception) {
            0f
        }
    }

    fun stop() {
        try {
            player?.stop()
        } catch (ignored: Exception) {
        }
        try {
            player?.release()
        } catch (ignored: Exception) {
        }
        player = null
        finished = false
    }
}
