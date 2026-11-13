package com.example.ai

import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Collects one spoken turn for the AI to hear directly (no on-phone speech-to-text, so no single
 * language). It does not own a microphone: [MicHub] feeds it frames, so it can start with audio
 * that was already recorded (the request said in the same breath as her name, or words spoken
 * while she was still talking). A voice-activity detector decides when the user started and
 * finished. Results are delivered once, on the main thread.
 */
class SpeechCapture(
    private val onLevel: (Float) -> Unit,
    private val onSpeechStarted: () -> Unit,
    /** A 16 kHz mono WAV of the whole utterance (with a little lead-in). */
    private val onUtterance: (ByteArray) -> Unit,
    /**
     * Nothing was said before the timeout: what the mic picked up meanwhile (WAV), the loudest
     * level in it and the level speech had to reach, so "she didn't hear me" can be checked.
     */
    private val onNoSpeech: (ByteArray, Double, Double, Int, Int) -> Unit,
    /** The room's level from the wake listener's gate (raw RMS), 0 when unknown. */
    ambientFloor: Double = 0.0,
    /** Quiet that ends a turn: shorter for a short request, longer once they talk at length. */
    shortEndSilenceMs: Int = 900,
    longEndSilenceMs: Int = 1_300
) {
    private val main = Handler(Looper.getMainLooper())
    private val vad = VoiceActivity(
        endSilenceMs = longEndSilenceMs,
        shortEndSilenceMs = shortEndSilenceMs,
        initialFloor = if (ambientFloor > 0) ambientFloor else 60.0
    )
    private val preRoll = ArrayDeque<ShortArray>()
    private val speech = ByteArrayOutputStream()
    private val everything = ByteArrayOutputStream() // the whole wait, for the no-speech report
    @Volatile private var done = false
    private var levelTick = 0

    /** True once a result was delivered or the capture was cancelled. */
    val isFinished: Boolean get() = done

    /** Stops without delivering anything. */
    fun cancel() { done = true }

    /** Audio recorded before the capture began (fed in order, before live frames). */
    @Synchronized
    fun seed(frames: List<ShortArray>) {
        for (f in frames) { if (done) return; feed(f, rms(f)) }
    }

    /** One frame from the microphone thread. */
    @Synchronized
    fun feed(chunk: ShortArray, frameRms: Double) {
        if (done) return
        if (everything.size() < RATE * 2 * 12) everything.write(toBytes(chunk))
        val event = vad.feed(frameRms)
        if (++levelTick % 3 == 0) { val db = vad.lastDb; main.post { onLevel(db) } }
        when (event) {
            VoiceActivity.Event.SPEECH_START -> {
                preRoll.forEach { speech.write(toBytes(it)) }
                preRoll.clear()
                speech.write(toBytes(chunk))
                main.post(onSpeechStarted)
            }
            VoiceActivity.Event.IN_SPEECH -> speech.write(toBytes(chunk))
            VoiceActivity.Event.SPEECH_END -> {
                speech.write(toBytes(chunk))
                val wav = wav(speech.toByteArray())
                finish { onUtterance(wav) }
            }
            VoiceActivity.Event.FALSE_START -> speech.reset()
            VoiceActivity.Event.TIMEOUT -> {
                val heard = wav(everything.toByteArray())
                val loudest = vad.peakRms
                val needed = vad.lastThreshold
                val above = vad.framesAbove
                val run = vad.longestRunMs
                finish { onNoSpeech(heard, loudest, needed, above, run) }
            }
            VoiceActivity.Event.SILENCE -> {
                preRoll.addLast(chunk)
                while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
            }
        }
    }

    /** True while the user is speaking (for "did they keep talking after her name?"). */
    val inSpeech: Boolean get() = vad.isInSpeech

    private fun finish(deliver: () -> Unit) {
        done = true
        main.post { deliver() }
    }

    companion object {
        const val RATE = 16_000
        /** 30 ms frames. */
        const val FRAME = RATE * 30 / 1000
        private const val PRE_ROLL_FRAMES = 25 // 750 ms before speech is detected (soft first sounds start below the speech level)

        fun rms(samples: ShortArray): Double {
            if (samples.isEmpty()) return 0.0
            var sum = 0.0
            for (s in samples) sum += s.toDouble() * s
            return sqrt(sum / samples.size)
        }

        fun toBytes(samples: ShortArray): ByteArray {
            val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { buf.putShort(it) }
            return buf.array()
        }

        /** 16-bit mono PCM → WAV file bytes. */
        fun wav(pcm: ByteArray, rate: Int = RATE): ByteArray {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(pcm.size)
            }
            return header.array() + pcm
        }
    }
}

/**
 * Energy-based voice-activity detector with an adaptive noise floor. Speech starts after ~150 ms
 * clearly above the room's noise; it ends after [endSilenceMs] of quiet (long enough for a
 * thinking pause). Too-short bursts (a cough, a door) are discarded. Pure logic, unit-tested.
 */
class VoiceActivity(
    private val endSilenceMs: Int = 1_300,
    /** Used while the request is still short (under [longAfterVoicedMs] of voice). */
    shortEndSilenceMs: Int = 900,
    private val longAfterVoicedMs: Int = 2_500,
    private val noSpeechTimeoutMs: Int = 7_000,
    private val maxUtteranceMs: Int = 25_000,
    private val frameMs: Int = 30,
    /** Where the room's noise level starts (never the first frame heard: that can be the user's own voice). */
    initialFloor: Double = MIN_FLOOR
) {
    enum class Event { SILENCE, SPEECH_START, IN_SPEECH, SPEECH_END, FALSE_START, TIMEOUT }

    private val shortEndMs = minOf(shortEndSilenceMs, endSilenceMs)
    val isInSpeech: Boolean get() = inSpeech
    private var noiseFloor = initialFloor.coerceIn(MIN_FLOOR, MAX_FLOOR)
    private var aboveRun = 0
    private var belowRun = 0
    private var inSpeech = false
    private var speechFrames = 0
    private var waitedFrames = 0
    var lastDb = 0f
        private set
    /** Loudest frame so far and the level a frame must pass to count as speech (raw 16-bit RMS). */
    var peakRms = 0.0
        private set
    var lastThreshold = 0.0
        private set
    /** Diagnostics only: frames above the speech level, and the longest unbroken run of them (ms). */
    var framesAbove = 0
        private set
    var longestRunMs = 0
        private set
    private var currentRun = 0

    fun feed(rms: Double): Event {
        lastDb = (20 * log10(max(rms, 1.0))).toFloat()
        val threshold = max(noiseFloor * START_RATIO, MIN_SPEECH_RMS)
        lastThreshold = threshold
        if (rms > peakRms) peakRms = rms
        val loud = rms > threshold
        if (loud) { framesAbove++; currentRun++; if (currentRun * frameMs > longestRunMs) longestRunMs = currentRun * frameMs } else currentRun = 0
        if (!inSpeech) {
            waitedFrames++
            // The floor follows the room: quickly down, slowly up (so speech doesn't raise it).
            noiseFloor = if (rms < noiseFloor) noiseFloor * 0.8 + rms * 0.2 else noiseFloor * 0.995 + rms * 0.005
            noiseFloor = noiseFloor.coerceIn(MIN_FLOOR, MAX_FLOOR)
            aboveRun = if (loud) aboveRun + 1 else 0
            if (aboveRun >= START_FRAMES) {
                inSpeech = true
                speechFrames = aboveRun
                belowRun = 0
                return Event.SPEECH_START
            }
            return if (waitedFrames * frameMs >= noSpeechTimeoutMs) Event.TIMEOUT else Event.SILENCE
        }
        speechFrames++
        belowRun = if (rms > threshold * END_RATIO) 0 else belowRun + 1
        val voicedMs = (speechFrames - belowRun) * frameMs
        val endAfter = if (voicedMs < longAfterVoicedMs) shortEndMs else endSilenceMs
        if (belowRun * frameMs >= endAfter) {
            if (voicedMs < MIN_UTTERANCE_MS) {
                // Just a bump: forget it and keep listening.
                inSpeech = false; aboveRun = 0; speechFrames = 0
                return Event.FALSE_START
            }
            return Event.SPEECH_END
        }
        return if (speechFrames * frameMs >= maxUtteranceMs) Event.SPEECH_END else Event.IN_SPEECH
    }

    private companion object {
        const val START_RATIO = 3.0
        const val END_RATIO = 0.6
        const val START_FRAMES = 5          // 150 ms
        const val MIN_UTTERANCE_MS = 350
        const val MIN_FLOOR = 60.0
        /** The speech level (floor × 3) never passes 1,500: real voices at the mic are 3,000 and up. */
        const val MAX_FLOOR = 500.0
        const val MIN_SPEECH_RMS = 250.0
    }
}
