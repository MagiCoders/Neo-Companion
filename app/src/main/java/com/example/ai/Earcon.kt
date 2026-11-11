package com.example.ai

import android.media.AudioManager
import android.media.ToneGenerator

/** Tiny sounds that say "I'm listening" without words (a spoken greeting delays the mic by seconds). */
object Earcon {
    private var tone: ToneGenerator? = null

    /** One soft, short beep (about 60 ms). Safe to call from any thread; failures are ignored. */
    @Synchronized
    fun listening() {
        try {
            val t = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 45).also { tone = it }
            t.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
        } catch (_: Throwable) {
            tone = null
        }
    }
}
