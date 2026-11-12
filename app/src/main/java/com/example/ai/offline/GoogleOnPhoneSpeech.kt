package com.example.ai.offline

import android.os.Build
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.texttospeech.SpeechSynthesis
import com.google.mlkit.genai.texttospeech.speechSynthesizerOptions
import java.util.Locale

/**
 * Asks Google's on-phone speech (ML Kit GenAI) what this phone can do: listening in each language
 * (basic and advanced modes) and on-phone voices. Status only; nothing is downloaded.
 */
object GoogleOnPhoneSpeech {

    fun statusName(s: Int): String = when (s) {
        FeatureStatus.AVAILABLE -> "ready"
        FeatureStatus.DOWNLOADABLE -> "can download"
        FeatureStatus.DOWNLOADING -> "downloading"
        FeatureStatus.UNAVAILABLE -> "not on this phone"
        else -> "unknown ($s)"
    }

    suspend fun report(): String {
        val lines = ArrayList<String>()
        if (Build.VERSION.SDK_INT < 31) return "Google on-phone speech needs Android 12 or newer."
        for (tag in listOf("en-US", "ar-SA")) {
            for ((label, mode) in listOf("basic" to SpeechRecognizerOptions.Mode.MODE_BASIC, "advanced" to SpeechRecognizerOptions.Mode.MODE_ADVANCED)) {
                val line = try {
                    val client = SpeechRecognition.getClient(speechRecognizerOptions {
                        locale = Locale.forLanguageTag(tag)
                        preferredMode = mode
                    })
                    try { statusName(client.checkStatus()) } finally { runCatching { client.close() } }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    "error (${e.javaClass.simpleName})"
                }
                lines += "Listening $tag $label: $line"
            }
        }
        for (tag in listOf("en-US", "ar")) {
            val line = try {
                val locale = Locale.forLanguageTag(tag)
                val client = SpeechSynthesis.getClient(speechSynthesizerOptions(locale) {})
                try {
                    val voices = client.getSupportedVoices(locale)
                    if (voices.isEmpty()) "no voices" else "${voices.size} voice(s), " + statusName(client.checkStatus(voices.first()))
                } finally { runCatching { client.close() } }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                "error (${e.javaClass.simpleName})"
            }
            lines += "Voice $tag: $line"
        }
        return lines.joinToString("\n")
    }
}
