package com.example.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log

/**
 * The one microphone. A single echo-cancelled recorder stays open while voice is on, and every
 * 30 ms frame goes to whoever needs it (the wake-word decoder, the request recorder, the
 * "is the user talking over her?" detector). Nothing is closed and reopened between her name and
 * your request, so nothing you say falls into a gap.
 *
 * The last [RING_SECONDS] seconds are kept in memory (never stored or sent anywhere), so a request
 * spoken in the same breath as her name can be taken from the moment it started.
 * Frames are delivered on the microphone thread; the sink must be quick.
 */
class MicHub {

    /** Receives every frame on the microphone thread. [startSample] counts samples since start. */
    fun interface FrameSink {
        fun onFrame(frame: ShortArray, rms: Double, startSample: Long)
    }

    @Volatile var sink: FrameSink? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private val effects = mutableListOf<AudioEffect>()

    private val ring = ArrayDeque<Pair<Long, ShortArray>>()
    @Volatile var samplesRecorded = 0L
        private set

    val isRunning: Boolean get() = running

    /** The recorder's audio session (so Android's recording list can be matched to her), -1 if off. */
    @Volatile var sessionId = -1
        private set

    /** Which clean-up effects the phone gave us (for Diagnostics). */
    @Volatile var audioEffects = "not started"
        private set

    /** Opens the microphone. Returns false when it could not be opened. */
    @SuppressLint("MissingPermission") // only started when RECORD_AUDIO is granted
    fun start(onError: (String) -> Unit): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val bufBytes = maxOf(minBuf, RATE / 2 * 2) // half a second of room
        var r: AudioRecord? = null
        for (source in listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val candidate = try {
                if (Build.VERSION.SDK_INT >= 30) {
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
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) { r = candidate; break }
            candidate?.release()
        }
        val rec = r ?: return false
        val applied = mutableListOf<String>()
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.let { it.enabled = true; effects += it; applied += "echo canceller" }
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.let { it.enabled = true; effects += it; applied += "noise suppressor" }
        audioEffects = if (applied.isEmpty()) "none available on this phone" else applied.joinToString(" + ")
        try {
            rec.startRecording()
        } catch (e: Exception) {
            closeRecorder(rec)
            return false
        }
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) { closeRecorder(rec); return false }
        record = rec
        sessionId = rec.audioSessionId
        synchronized(ring) { ring.clear() }
        samplesRecorded = 0L
        running = true
        thread = Thread({ loop(rec, onError) }, "mochi-mic").apply { start() }
        return true
    }

    /** Closes the microphone and waits briefly until it is really released. */
    fun stop() {
        if (!running && thread == null) return
        running = false
        try { record?.stop() } catch (_: Throwable) {} // unblocks a pending read
        val t = thread
        thread = null
        if (t != null && t !== Thread.currentThread()) try { t.join(STOP_WAIT_MS) } catch (_: InterruptedException) {}
    }

    /** Frames recorded from [sample] on (oldest first), as far back as the memory reaches. */
    fun framesSince(sample: Long): List<ShortArray> = synchronized(ring) {
        ring.filter { (start, frame) -> start + frame.size > sample }.map { it.second }
    }

    private fun loop(rec: AudioRecord, onError: (String) -> Unit) {
        val buf = ShortArray(FRAME)
        try {
            while (running) {
                val n = rec.read(buf, 0, FRAME)
                if (n < 0) {
                    if (running) onError("Microphone error $n")
                    break
                }
                if (n == 0) continue
                val frame = buf.copyOf(n)
                val start = samplesRecorded
                samplesRecorded += n
                synchronized(ring) {
                    ring.addLast(start to frame)
                    while (ring.size > RING_FRAMES) ring.removeFirst()
                }
                try {
                    sink?.onFrame(frame, SpeechCapture.rms(frame), start)
                } catch (e: Throwable) {
                    Log.w(TAG, "Frame consumer failed: ${e.javaClass.simpleName}")
                }
            }
        } catch (e: Throwable) {
            if (running) onError("Microphone stopped: ${e.javaClass.simpleName}")
        } finally {
            running = false
            closeRecorder(rec)
            if (record === rec) record = null
            sessionId = -1
        }
    }

    private fun closeRecorder(rec: AudioRecord) {
        effects.forEach { try { it.release() } catch (_: Throwable) {} }
        effects.clear()
        try { rec.stop() } catch (_: Throwable) {}
        try { rec.release() } catch (_: Throwable) {}
    }

    companion object {
        const val RATE = 16_000
        /** 30 ms frames. */
        const val FRAME = RATE * 30 / 1000
        const val RING_SECONDS = 8
        private const val RING_FRAMES = RING_SECONDS * 1000 / 30
        private const val STOP_WAIT_MS = 500L
        private const val TAG = "MicHub"
    }
}
