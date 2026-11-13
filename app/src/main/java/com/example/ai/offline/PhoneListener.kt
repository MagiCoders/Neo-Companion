package com.example.ai.offline

import android.content.Context
import com.example.ai.PhoneLoad
import com.example.diag.AppTrail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** The one thread every sherpa-onnx engine call runs on (the engines are not thread-safe). */
object OfflineSpeechThread {
    val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "offline-speech") }.asCoroutineDispatcher()
}

/** Whisper's habits on silence or noise (pure, unit-tested). */
object WhisperText {
    private val SILENCE_PHRASES = setOf(
        "you", "thank you", "thanks for watching", "thank you for watching", "bye", ".",
        "ترجمة نانسي قنقر", "اشتركوا في القناة", "شكرا", "موسیقی"
    )

    /** "" when Whisper produced nothing real: blank, bracketed tags, or its known silence phrases. */
    fun clean(raw: String): String {
        val t = raw.trim().replace(Regex("\\[[^\\]]*]|\\([^)]*\\)"), "").trim()
        if (t.isEmpty() || t.all { !it.isLetterOrDigit() }) return ""
        val bare = t.lowercase().trimEnd('.', '!', '?', '؟', ',', '،', ' ')
        return if (bare in SILENCE_PHRASES) "" else t
    }
}

/**
 * The on-phone listener for "On this phone" mode: Whisper turns Mochi's own recording into text.
 * One model loaded at a time, all engine calls on one thread.
 */
object PhoneListener {
    sealed class Result {
        /** [text] may be ""; [loadSeconds] is 0 when the model was already loaded. */
        data class Text(val text: String, val seconds: Double, val pack: OfflinePack, val loadSeconds: Double = 0.0) : Result()
        data class Unavailable(val reason: String) : Result()
    }

    private val WHISPER_LANGUAGES = setOf("en", "ar", "fa", "tr", "ur", "fr", "de", "es", "hi", "ru", "it", "pt", "id", "ku")
    private const val IDLE_UNLOAD_MS = 5 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var listener: OfflineListener? = null
    private var key = ""
    private var idleJob: Job? = null

    /** English: base if ready, else small. Others: small if ready, else base. null if none. */
    fun choosePack(language: String, readyIds: Set<String>): OfflinePack? {
        val order = if (language == "en") listOf(OfflineCatalog.LISTENER_BASE, OfflineCatalog.LISTENER_SMALL)
        else listOf(OfflineCatalog.LISTENER_SMALL, OfflineCatalog.LISTENER_BASE)
        return order.firstOrNull { it.id in readyIds }
    }

    private fun readyIds(context: Context) =
        OfflineCatalog.LISTENERS.filter { OfflineCatalog.packReady(context.filesDir, it) }.map { it.id }.toSet()

    fun isAvailable(context: Context): Boolean = readyIds(context).isNotEmpty()

    fun whisperLanguage(languageTag: String): String =
        languageTag.substringBefore('-').lowercase().takeIf { it in WHISPER_LANGUAGES } ?: "en"

    suspend fun transcribe(context: Context, wav: ByteArray, languageTag: String, forcePack: OfflinePack? = null): Result {
        val lang = whisperLanguage(languageTag)
        val pack = forcePack?.takeIf { OfflineCatalog.packReady(context.filesDir, it) }
            ?: choosePack(lang, readyIds(context))
            ?: return Result.Unavailable("No listener is downloaded. Download Whisper in Gemma Lab.")
        val appContext = context.applicationContext
        return withContext(OfflineSpeechThread.dispatcher) {
            try {
                var loadSeconds = 0.0
                val wanted = "${pack.id}|$lang"
                if (listener == null || key != wanted) {
                    val t = System.currentTimeMillis()
                    listener?.release(); listener = null
                    PhoneLoad.during { listener = OfflineListener.load(OfflineCatalog.packDir(appContext.filesDir, pack), pack, lang) }
                    key = wanted
                    loadSeconds = (System.currentTimeMillis() - t) / 1000.0
                    AppTrail.add("OFFLINE", "listener ${pack.id} $lang loaded in %.1f s".format(loadSeconds))
                }
                val t0 = System.currentTimeMillis()
                val raw = PhoneLoad.during { listener!!.transcribe(wav) }
                scheduleIdleUnload()
                Result.Text(WhisperText.clean(raw), (System.currentTimeMillis() - t0) / 1000.0, pack, loadSeconds)
            } catch (t: Throwable) {
                AppTrail.add("OFFLINE", "listener failed ${t.javaClass.simpleName}")
                runCatching { listener?.release() }; listener = null; key = ""
                Result.Unavailable("The on-phone listener failed (${t.javaClass.simpleName}).")
            }
        }
    }

    private fun scheduleIdleUnload() {
        idleJob?.cancel()
        idleJob = scope.launch { delay(IDLE_UNLOAD_MS); release() }
    }

    /** Frees the model; safe from any thread. */
    fun release() {
        scope.launch(OfflineSpeechThread.dispatcher) {
            if (listener != null) AppTrail.add("OFFLINE", "listener released")
            runCatching { listener?.release() }
            listener = null; key = ""
        }
    }
}
