package com.example.ai.offline

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Whisper turns a recording into text on the phone through sherpa-onnx (used by phone mode and the Lab test).
 */
class OfflineListener private constructor(private val recognizer: OfflineRecognizer) {

    /** Text for a 16 kHz mono 16-bit WAV (or raw PCM) recording, with a little silence around it. */
    fun transcribe(wav: ByteArray): String = transcribe(OfflineAudio.padded(wav))

    fun transcribe(samples: FloatArray): String {
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

/** Audio helpers for the offline test. Pure, unit-tested. */
object OfflineAudio {
    /** 16-bit little-endian PCM (with or without a 44-byte WAV header) to floats in −1…1. */
    fun wavToFloats(wav: ByteArray): FloatArray {
        val offset = if (wav.size >= 44 && String(wav, 0, 4, Charsets.US_ASCII) == "RIFF") 44 else 0
        val count = (wav.size - offset) / 2
        val buf = ByteBuffer.wrap(wav, offset, count * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(count) { buf.get(it) / 32768f }
    }

    /** The clip with [leadMs] of silence before and [tailMs] after (Whisper is steadier on short clips that way). */
    fun padded(wav: ByteArray, leadMs: Int = 300, tailMs: Int = 500, rate: Int = 16_000): FloatArray {
        val clip = wavToFloats(wav)
        val lead = rate * leadMs / 1000
        val tail = rate * tailMs / 1000
        return FloatArray(lead + clip.size + tail).also { clip.copyInto(it, lead) }
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
