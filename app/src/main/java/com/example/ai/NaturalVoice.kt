package com.example.ai

import android.content.Context
import android.util.Log
import com.example.ui.character.CharacterSpec
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Natural, human-sounding speech from Gemini's speech model, with the accent of the user's
 * language variety (Iraqi Arabic, Persian, …), instead of the phone's built-in voice.
 *
 * Speed: the sound is streamed, so she starts talking when the first piece arrives instead of
 * after the whole sentence is ready, and short lines (greeting, "anything else?", goodbyes) are
 * kept on the phone so they play instantly from the second time on. Any failure yields nothing and
 * the phone's voice is used; after a quota error it rests for 30 minutes.
 */
@OptIn(com.google.firebase.ai.type.PublicPreviewAPI::class)
object NaturalVoice {

    class Audio(val pcm: ShortArray, val sampleRate: Int)

    private const val FIRST_SOUND_TIMEOUT_MS = 8_000L
    private const val WHOLE_TIMEOUT_MS = 20_000L
    private const val MEMORY_LINES = 24
    private const val DISK_LINES = 80
    private const val MAX_CHARS = 600
    /** Lines up to this length are kept (they are the ones that repeat). */
    private const val KEEP_UP_TO_CHARS = 80
    @Volatile private var restingUntil = 0L
    @Volatile private var cacheDir: File? = null
    @Volatile private var appContext: Context? = null

    private val models = ConcurrentHashMap<String, GenerativeModel>()
    private val memory = object : LinkedHashMap<String, Audio>(MEMORY_LINES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Audio>?) = size > MEMORY_LINES
    }

    @Volatile var enabled = true

    /** Call once with the app context so short lines survive restarts. */
    fun init(context: Context) {
        if (cacheDir == null) cacheDir = File(context.filesDir, "voice_lines").apply { mkdirs() }
        if (appContext == null) appContext = context.applicationContext
    }

    private fun model(voice: String) = models.getOrPut(voice) {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = AiModels.TTS,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(voice = Voice(voice))
            }
        )
    }

    private fun key(text: String, languageTag: String, character: CharacterSpec) = "${voiceFor(character)}|$languageTag|$text"

    /** True when [text] can be spoken instantly (already kept from an earlier time). */
    fun isReady(text: String, languageTag: String, character: CharacterSpec): Boolean {
        val k = key(text, languageTag, character)
        return synchronized(memory) { memory.containsKey(k) } || diskFile(k)?.exists() == true
    }

    /**
     * The speech for [text] as it becomes available: one piece if it was kept from before, else
     * pieces as the model produces them. An empty flow means "use the phone's voice".
     */
    fun speak(text: String, languageTag: String, character: CharacterSpec): Flow<Audio> = flow {
        if (!enabled || text.isBlank() || text.length > MAX_CHARS) return@flow
        val k = key(text, languageTag, character)
        (synchronized(memory) { memory[k] } ?: readDisk(k)?.also { a -> synchronized(memory) { memory[k] = a } })?.let {
            VoiceLog.add(VoiceLog.Kind.SPOKE, "Natural voice: instant (kept from before)")
            emit(it)
            return@flow
        }
        if (System.currentTimeMillis() < restingUntil) return@flow
        // Offline: use the phone's own voice at once instead of waiting for a timeout.
        appContext?.let { if (!NetworkState.isOnline(it)) return@flow }
        val voice = voiceFor(character)
        val started = System.currentTimeMillis()
        val all = ArrayList<ShortArray>()
        var rate = 24_000
        var emitted = false
        try {
            val prompt = direction(text, languageTag)
            try {
                // Streamed: the first sound usually arrives well before the whole line is ready.
                var carry: Byte? = null
                withTimeout(WHOLE_TIMEOUT_MS) {
                    model(voice).generateContentStream(prompt).collect { response ->
                        for (part in audioParts(response)) {
                            rate = rateOf(part.mimeType) ?: rate
                            var bytes = part.inlineData
                            carry?.let { c -> bytes = byteArrayOf(c) + bytes; carry = null }
                            if (bytes.size % 2 == 1) { carry = bytes.last(); bytes = bytes.copyOf(bytes.size - 1) }
                            if (bytes.isEmpty()) continue
                            val pcm = toPcm(bytes)
                            if (!emitted) {
                                VoiceLog.add(VoiceLog.Kind.SPOKE, "Natural voice: first sound in %.1f s".format((System.currentTimeMillis() - started) / 1000.0))
                                emitted = true
                            }
                            all += pcm
                            emit(Audio(pcm, rate))
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                if (emitted || AiDiagnostics.classify(e) == AiDiagnostics.Reason.QUOTA) throw e
                // Streaming not available for this model or connection: ask for the whole line at once.
                Log.w("NaturalVoice", "Stream failed (${e.javaClass.simpleName}); trying one piece")
                val response = withTimeout(FIRST_SOUND_TIMEOUT_MS + 4_000L) { model(voice).generateContent(prompt) }
                val part = audioParts(response).firstOrNull() ?: return@flow
                rate = rateOf(part.mimeType) ?: rate
                val pcm = toPcm(part.inlineData.let { if (it.size % 2 == 1) it.copyOf(it.size - 1) else it })
                VoiceLog.add(VoiceLog.Kind.SPOKE, "Natural voice: ready in %.1f s (not streamed)".format((System.currentTimeMillis() - started) / 1000.0))
                emitted = true
                all += pcm
                emit(Audio(pcm, rate))
            }
            if (emitted && text.length <= KEEP_UP_TO_CHARS) keep(k, join(all), rate)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            if (AiDiagnostics.classify(e) == AiDiagnostics.Reason.QUOTA) restingUntil = System.currentTimeMillis() + 30 * 60_000L
            VoiceLog.add(VoiceLog.Kind.ERROR, "Natural voice unavailable (${e.javaClass.simpleName})" + if (emitted) "" else "; using the phone's voice")
            Log.w("NaturalVoice", "TTS failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Prepares short fixed lines in the background (once per language and character), so the
     * greeting after her name and "anything else?" play instantly. Network call: run off the main
     * thread. Stops at the first failure (no quota, offline) and tries again another time.
     */
    suspend fun prepare(lines: List<String>, languageTag: String, character: CharacterSpec) {
        for (line in lines) {
            if (!enabled || isReady(line, languageTag, character)) continue
            var got = false
            speak(line, languageTag, character).collect { got = true }
            if (!got) return
        }
    }

    private fun audioParts(response: GenerateContentResponse): List<InlineDataPart> =
        response.candidates.firstOrNull()?.content?.parts?.filterIsInstance<InlineDataPart>().orEmpty()

    private fun rateOf(mime: String): Int? = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull()

    private fun toPcm(bytes: ByteArray): ShortArray =
        ShortArray(bytes.size / 2).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(it) }

    private fun join(parts: List<ShortArray>): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var pos = 0
        for (p in parts) { p.copyInto(out, pos); pos += p.size }
        return out
    }

    // ---- Kept lines: memory first, then a small folder on the phone ---------------------------------

    private fun diskFile(key: String): File? {
        val dir = cacheDir ?: return null
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
        return File(dir, "$hash.pcm")
    }

    private fun keep(key: String, pcm: ShortArray, rate: Int) {
        if (pcm.isEmpty()) return
        synchronized(memory) { memory[key] = Audio(pcm, rate) }
        val file = diskFile(key) ?: return
        runCatching {
            val buf = ByteBuffer.allocate(4 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(rate)
            buf.asShortBuffer().put(pcm)
            file.writeBytes(buf.array())
            // Keep the folder small: oldest lines go first.
            file.parentFile?.listFiles()?.sortedByDescending { it.lastModified() }?.drop(DISK_LINES)?.forEach { it.delete() }
        }
    }

    private fun readDisk(key: String): Audio? {
        val file = diskFile(key)?.takeIf { it.exists() && it.length() > 4 } ?: return null
        return runCatching {
            val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val rate = buf.int
            val pcm = ShortArray(buf.remaining() / 2).also { buf.asShortBuffer().get(it) }
            file.setLastModified(System.currentTimeMillis())
            Audio(pcm, rate)
        }.getOrNull()
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
