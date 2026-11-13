package com.example.ai.offline

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TEST ONLY (Gemma Lab → Offline voice). Whisper turns a recording into text and Piper speaks,
 * both on the phone through sherpa-onnx. The Piper voices need espeak-ng, which is GPL-licensed:
 * this is for measuring on the user's phone and must not ship in a Play release as it is.
 */
class OfflineListener private constructor(private val recognizer: OfflineRecognizer) {

    /** Text for a 16 kHz mono 16-bit WAV (or raw PCM) recording. */
    fun transcribe(wav: ByteArray): String {
        val samples = OfflineAudio.wavToFloats(wav)
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, 16_000)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally { stream.release() }
    }

    fun release() = recognizer.release()

    companion object {
        /** [language] "en", "ar" or "fa" (Whisper is told the language so it doesn't guess). */
        fun load(dir: File, pack: OfflinePack, language: String): OfflineListener {
            val prefix = pack.files.first().first.substringBefore("-encoder")
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(dir, "$prefix-encoder.int8.onnx").absolutePath,
                        decoder = File(dir, "$prefix-decoder.int8.onnx").absolutePath,
                        language = language,
                        task = "transcribe"
                    ),
                    tokens = File(dir, "$prefix-tokens.txt").absolutePath,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                    modelType = "whisper"
                )
            )
            return OfflineListener(OfflineRecognizer(assetManager = null, config = config))
        }
    }
}

class OfflineVoice private constructor(private val tts: OfflineTts) {

    val sampleRate: Int get() = tts.sampleRate()
    @Volatile private var stopped = false

    /**
     * Speaks [text] through the speaker as it is made (first piece plays as soon as it is ready).
     * [onFirstSound] is called once when the first sound starts. Blocking: call off the main thread.
     */
    fun speak(text: String, onFirstSound: () -> Unit = {}) {
        stopped = false
        val rate = sampleRate
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        var started = false
        try {
            track.play()
            tts.generateWithCallback(text, 0, 1.0f) { samples ->
                if (stopped) return@generateWithCallback 0
                if (!started) { started = true; onFirstSound() }
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                1
            }
            // Let the last piece play out.
            val tail = (track.bufferSizeInFrames * 1000L / rate).coerceIn(50L, 600L)
            if (!stopped) Thread.sleep(tail)
        } finally {
            try { track.stop() } catch (_: Throwable) {}
            track.release()
        }
    }

    fun stop() { stopped = true }
    fun release() = tts.release()

    companion object {
        /** Copies the espeak-ng pronunciation data (bundled for this test) to app storage once. */
        fun espeakDir(context: Context): File {
            val target = File(context.filesDir, "offline-speech/espeak-ng-data")
            val marker = File(target, ".complete")
            if (!marker.exists()) {
                target.deleteRecursively()
                copyAssets(context.assets, "espeak-ng-data", target)
                marker.writeText("1")
            }
            return target
        }

        private fun copyAssets(am: AssetManager, path: String, to: File) {
            val children = am.list(path).orEmpty()
            if (children.isEmpty()) {
                to.parentFile?.mkdirs()
                am.open(path).use { input -> to.outputStream().use { input.copyTo(it) } }
                return
            }
            to.mkdirs()
            for (c in children) copyAssets(am, "$path/$c", File(to, c))
        }

        fun load(context: Context, dir: File, pack: OfflinePack): OfflineVoice {
            val model = pack.files.first { it.first.endsWith(".onnx") }.first
            com.example.diag.AppTrail.add("OFFLINE", "voice ${pack.id} loading")
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(dir, model).absolutePath,
                        tokens = File(dir, "tokens.txt").absolutePath,
                        dataDir = espeakDir(context).absolutePath
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                maxNumSentences = 1
            )
            return OfflineVoice(OfflineTts(assetManager = null, config = config))
        }
    }
}

/** Audio helpers for the offline test. Pure, unit-tested. */
object OfflineAudio {
    /** 16-bit little-endian PCM (with or without a 44-byte WAV header) to floats in −1…1. */
    fun wavToFloats(wav: ByteArray): FloatArray {
        val offset = if (wav.size >= 44 && String(wav, 0, 4, Charsets.US_ASCII) == "RIFF") 44 else 0
        val count = (wav.size - offset) / 2
        val buf = ByteBuffer.wrap(wav, offset, count * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(count) { buf.get(it) / 32768f }
    }

    /**
     * The first sentence of a reply as it streams in: text up to the first sentence end (. ! ? and
     * the Arabic/Persian question mark), or null while the sentence isn't finished yet.
     */
    fun firstSentence(sofar: String, minChars: Int = 12): String? {
        val m = Regex("[.!?؟。](\\s|$)").findAll(sofar).firstOrNull { it.range.first + 1 >= minChars } ?: return null
        return sofar.substring(0, m.range.first + 1).trim()
    }
}
