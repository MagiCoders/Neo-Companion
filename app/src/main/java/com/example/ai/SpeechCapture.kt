package com.example.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records one spoken turn for the AI to hear directly (no on-phone speech-to-text, so no single
 * language, no beeps, no cut-offs). The microphone runs in voice-call mode with the phone's echo
 * canceller and noise suppressor, and a voice-activity detector decides when the user started
 * and when they finished. Callbacks arrive on the main thread.
 */
class SpeechCapture(
    private val onLevel: (Float) -> Unit,
    private val onSpeechStarted: () -> Unit,
    /** A 16 kHz mono WAV of the whole utterance (with a little lead-in). */
    private val onUtterance: (ByteArray) -> Unit,
    /** Nothing was said before the timeout. */
    private val onNoSpeech: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    fun start(): Boolean {
        if (running) return true
        running = true
        thread = Thread(::loop, "speech-capture").apply { start() }
        return true
    }

    /** Stops at once and waits briefly until the microphone is released. Nothing is delivered. */
    fun stop() {
        running = false
        val t = thread
        thread = null
        if (t != null && t !== Thread.currentThread()) try { t.join(STOP_WAIT_MS) } catch (_: InterruptedException) {}
    }

    @SuppressLint("MissingPermission") // only started when RECORD_AUDIO is granted
    private fun loop() {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = openRecorder(maxOf(minBuf, RATE)) ?: run {
            running = false
            main.post { onError("The microphone is busy.") }
            return
        }
        val effects = buildList {
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.let { it.enabled = true; add(it) }
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.let { it.enabled = true; add(it) }
        }
        val vad = VoiceActivity()
        val frame = ShortArray(FRAME)
        val preRoll = ArrayDeque<ShortArray>()
        val speech = ByteArrayOutputStream()
        var outcome: (() -> Unit)? = null
        try {
            record.startRecording()
            while (running) {
                val n = record.read(frame, 0, FRAME)
                if (n <= 0) { if (n < 0) { outcome = { onError("Microphone error $n") }; break }; continue }
                val chunk = frame.copyOf(n)
                val event = vad.feed(rms(chunk))
                main.post { onLevel(vad.lastDb) }
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
                        outcome = { onUtterance(wav) }
                        break
                    }
                    VoiceActivity.Event.FALSE_START -> speech.reset()
                    VoiceActivity.Event.TIMEOUT -> { outcome = onNoSpeech; break }
                    VoiceActivity.Event.SILENCE -> {
                        preRoll.addLast(chunk)
                        while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                    }
                }
            }
        } catch (e: Exception) {
            outcome = { onError("Microphone error: ${e.javaClass.simpleName}") }
        } finally {
            effects.forEach { try { it.release() } catch (_: Throwable) {} }
            try { record.stop() } catch (_: Throwable) {}
            record.release()
            val deliver = outcome
            val wasRunning = running
            running = false
            if (deliver != null && wasRunning) main.post(deliver)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(bufBytes: Int): AudioRecord? {
        for (source in listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val r = try {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    AudioRecord.Builder()
                        .setAudioSource(source)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setBufferSizeInBytes(bufBytes)
                        .setPrivacySensitive(false)
                        .build()
                } else {
                    AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes)
                }
            } catch (_: Exception) { null }
            if (r != null && r.state == AudioRecord.STATE_INITIALIZED) return r
            r?.release()
        }
        return null
    }

    companion object {
        const val RATE = 16_000
        /** 30 ms frames. */
        const val FRAME = RATE * 30 / 1000
        private const val PRE_ROLL_FRAMES = 15 // 450 ms before speech is detected
        private const val STOP_WAIT_MS = 400L

        fun rms(samples: ShortArray): Double {
            if (samples.isEmpty()) return 0.0
            var sum = 0.0
            for (s in samples) sum += s.toDouble() * s
            return sqrt(sum / samples.size)
        }

        private fun toBytes(samples: ShortArray): ByteArray {
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
    private val noSpeechTimeoutMs: Int = 7_000,
    private val maxUtteranceMs: Int = 25_000,
    private val frameMs: Int = 30
) {
    enum class Event { SILENCE, SPEECH_START, IN_SPEECH, SPEECH_END, FALSE_START, TIMEOUT }

    private var noiseFloor = -1.0
    private var aboveRun = 0
    private var belowRun = 0
    private var inSpeech = false
    private var speechFrames = 0
    private var waitedFrames = 0
    var lastDb = 0f
        private set

    fun feed(rms: Double): Event {
        lastDb = (20 * log10(max(rms, 1.0))).toFloat()
        if (noiseFloor < 0) noiseFloor = max(rms, MIN_FLOOR)
        val threshold = max(noiseFloor * START_RATIO, MIN_SPEECH_RMS)
        val loud = rms > threshold
        if (!inSpeech) {
            waitedFrames++
            // The floor follows the room: quickly down, slowly up (so speech doesn't raise it).
            noiseFloor = if (rms < noiseFloor) noiseFloor * 0.8 + rms * 0.2 else noiseFloor * 0.995 + rms * 0.005
            noiseFloor = max(noiseFloor, MIN_FLOOR)
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
        if (belowRun * frameMs >= endSilenceMs) {
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
        const val MIN_SPEECH_RMS = 250.0
    }
}
