package com.example.ai

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Hides the start/stop beep Android's speech recogniser plays, by muting the sound streams for a
 * moment around it. Only streams that weren't already muted are touched, every mute has a safety
 * timer, and anything still muted from an earlier run (e.g. the app was killed mid-beep) is
 * restored the next time the voice engine starts. Main thread only.
 */
class BeepSilencer(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs = context.getSharedPreferences("mochi_beep_silencer", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val muted = mutableSetOf<Int>()
    private val safety = Runnable { unmute() }

    var enabled = true

    init {
        // Restore streams left muted by a previous process.
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.forEach { setMuted(it, false) }
        prefs.edit().remove(KEY).apply()
    }

    /** Mutes for at most [maxMs]; call [unmute] as soon as the beep has played. */
    fun mute(maxMs: Long = 1_500L) {
        if (!enabled) return
        for (stream in STREAMS) {
            if (stream in muted) continue
            val alreadyMuted = try { audio.isStreamMute(stream) } catch (_: Exception) { true }
            if (!alreadyMuted && setMuted(stream, true)) muted += stream
        }
        prefs.edit().putStringSet(KEY, muted.map { it.toString() }.toSet()).apply()
        main.removeCallbacks(safety)
        main.postDelayed(safety, maxMs)
    }

    fun unmute(afterMs: Long = 0L) {
        main.removeCallbacks(safety)
        if (afterMs > 0) { main.postDelayed(safety, afterMs); return }
        muted.forEach { setMuted(it, false) }
        muted.clear()
        prefs.edit().remove(KEY).apply()
    }

    private fun setMuted(stream: Int, mute: Boolean): Boolean = try {
        audio.adjustStreamVolume(stream, if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
        true
    } catch (_: SecurityException) {
        false // e.g. Do Not Disturb forbids changing this stream: leave it alone
    }

    private companion object {
        const val KEY = "muted_streams"
        /** Recognisers play their beep on one of these, depending on the phone. */
        val STREAMS = listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)
    }
}
