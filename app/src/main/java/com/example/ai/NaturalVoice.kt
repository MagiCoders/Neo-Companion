package com.example.ai

import android.util.Log
import com.example.ui.character.CharacterSpec
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * Natural, human-sounding speech from Gemini's speech model, with the accent of the user's
 * language variety (Iraqi Arabic, Persian, …), instead of the phone's built-in voice. Any failure
 * returns null and the phone's voice is used; after a quota error it rests for 30 minutes.
 */
@OptIn(com.google.firebase.ai.type.PublicPreviewAPI::class)
object NaturalVoice {

    data class Audio(val pcm: ShortArray, val sampleRate: Int)

    private const val TIMEOUT_MS = 12_000L
    private const val CACHE_SIZE = 24
    private const val MAX_CHARS = 600
    @Volatile private var restingUntil = 0L

    private val models = ConcurrentHashMap<String, GenerativeModel>()
    /** Short lines that repeat (greetings, "let me check") play instantly the second time. */
    private val cache = object : LinkedHashMap<String, Audio>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Audio>?) = size > CACHE_SIZE
    }

    @Volatile var enabled = true

    private fun model(voice: String) = models.getOrPut(voice) {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = AiModels.TTS,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(voice = Voice(voice))
            }
        )
    }

    /** Network call: run off the main thread. */
    suspend fun synthesize(text: String, languageTag: String, character: CharacterSpec): Audio? {
        if (!enabled || text.isBlank() || text.length > MAX_CHARS || System.currentTimeMillis() < restingUntil) return null
        val voice = voiceFor(character)
        val key = "$voice|$languageTag|$text"
        synchronized(cache) { cache[key] }?.let { return it }
        val started = System.currentTimeMillis()
        return try {
            val response = withTimeout(TIMEOUT_MS) { model(voice).generateContent(direction(text, languageTag)) }
            val part = response.candidates.firstOrNull()?.content?.parts?.filterIsInstance<InlineDataPart>()?.firstOrNull()
                ?: return null
            val rate = Regex("rate=(\\d+)").find(part.mimeType)?.groupValues?.get(1)?.toIntOrNull() ?: 24_000
            val bytes = part.inlineData
            val pcm = ShortArray(bytes.size / 2).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it) }
            VoiceLog.add(VoiceLog.Kind.SPOKE, "Natural voice ready in %.1f s".format((System.currentTimeMillis() - started) / 1000.0))
            Audio(pcm, rate).also { audio -> if (text.length <= 80) synchronized(cache) { cache[key] = audio } }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            val reason = AiDiagnostics.classify(e)
            if (reason == AiDiagnostics.Reason.QUOTA) restingUntil = System.currentTimeMillis() + 30 * 60_000L
            VoiceLog.add(VoiceLog.Kind.ERROR, "Natural voice unavailable (${e.javaClass.simpleName}); using the phone's voice")
            Log.w("NaturalVoice", "TTS failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /** What to say and how: the accent instruction plus the exact words. */
    fun direction(text: String, languageTag: String): String {
        val accent = accentFor(languageTag)
        return "Say this warmly and naturally, like a friendly young woman talking to a close friend" +
            (if (accent.isNotEmpty()) ", with a native $accent accent" else "") +
            ". Read exactly these words and nothing else:\n$text"
    }

    fun accentFor(tag: String): String = when {
        tag.startsWith("ar-IQ", true) -> "Iraqi Arabic (Baghdadi)"
        tag.startsWith("ar-EG", true) -> "Egyptian Arabic (Cairene)"
        tag.startsWith("ar-SA", true) -> "Saudi Arabic"
        tag.startsWith("ar-AE", true) -> "Emirati Arabic"
        tag.startsWith("ar", true) -> "Arabic"
        tag.startsWith("fa", true) -> "Persian (Tehrani)"
        tag.startsWith("en", true) -> ""
        else -> VoiceLanguages.englishName(tag)
    }

    private fun voiceFor(character: CharacterSpec): String = when (character.id) {
        "elena" -> "Aoede"
        "kaito" -> "Puck"
        "marco" -> "Charon"
        else -> "Leda"
    }
}
