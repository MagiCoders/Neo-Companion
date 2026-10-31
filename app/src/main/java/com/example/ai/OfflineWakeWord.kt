package com.example.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Decides whether one recognised utterance is the wake phrase. Pure logic, unit-tested.
 *
 * The recogniser runs with a tiny grammar (greetings + companion names + "[unk]" for everything
 * else), so any speech is forced onto those words. To avoid waking on random talk we require the
 * name itself to be recognised confidently, and a bare name (no greeting) even more confidently.
 */
object WakePhraseMatcher {
    val GREETINGS = listOf("hey", "hi", "hello", "okay")
    val NAMES = listOf("mochi", "elena", "marco")
    /** Spoken during the 3-second countdown to cancel the action. */
    val ABORT = listOf("stop", "cancel")
    /** Greeting + name. Tested with several synthetic voices; other speech lands on "[unk]". */
    private const val MIN_NAME_CONF = 0.50
    private const val MIN_ABORT_CONF = 0.80
    private const val MIN_BARE_NAME_CONF = 0.93

    /** The grammar handed to the recogniser. */
    fun grammarJson(): String {
        val phrases = NAMES.flatMap { n -> GREETINGS.map { "$it $n" } + n } + ABORT + "[unk]"
        return phrases.joinToString(",", "[", "]") { "\"$it\"" }
    }

    /** "stop" / "cancel" said on its own, confidently. */
    fun isAbort(resultJson: String): Boolean {
        val words = parse(resultJson)
        return words.size == 1 && words[0].first in ABORT && words[0].second >= MIN_ABORT_CONF
    }

    /** @param resultJson a Vosk result with word confidences (`setWords(true)`). */
    fun isWake(resultJson: String): Boolean {
        val words = parse(resultJson)
        words.forEachIndexed { i, (word, conf) ->
            if (word !in NAMES) return@forEachIndexed
            val greeted = i > 0 && words[i - 1].first in GREETINGS
            if (greeted && conf >= MIN_NAME_CONF) return true
            if (!greeted && words.size == 1 && conf >= MIN_BARE_NAME_CONF) return true
        }
        return false
    }

    private fun parse(resultJson: String): List<Pair<String, Double>> =
        try {
            val arr = JSONObject(resultJson).optJSONArray("result") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.optString("word") to o.optDouble("conf", 0.0)
            }
        } catch (_: Exception) {
            emptyList()
        }
}

/**
 * Always-on, fully offline wake-word listener (Vosk, Apache-2.0, bundled English model in
 * assets/vosk-wake). The model's general dictation graph (graph/Gr.fst) is replaced by an empty
 * graph that keeps only its word list: grammar decoding builds its own graph, so wake results are
 * identical while the app is ~17 MB smaller (see tools/slim_vosk_graph.py). It holds the microphone with a plain AudioRecord, so unlike SpeechRecognizer
 * there are no session restarts and no start/stop beeps. Audio never leaves the phone.
 *
 * All public methods are called on the main thread; callbacks arrive on the main thread.
 */
class OfflineWakeWord(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var model: Model? = null
    private var loading = false
    private var service: SpeechService? = null
    private var recognizer: Recognizer? = null

    val isReady: Boolean get() = model != null
    val isListening: Boolean get() = service != null

    /** Unpacks (first run only) and loads the model in the background. */
    fun prepare(onReady: () -> Unit, onFailed: (String) -> Unit) {
        if (model != null) { onReady(); return }
        if (loading) return
        loading = true
        io.execute {
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                val dir = unpackModel()
                val loaded = Model(dir.absolutePath)
                main.post { loading = false; model = loaded; onReady() }
            } catch (e: Throwable) {
                Log.w(TAG, "Wake model failed to load: ${e.javaClass.simpleName}")
                main.post { loading = false; onFailed("The offline wake-word model could not be loaded.") }
            }
        }
    }

    /** Starts listening. Returns false if the mic could not be opened. */
    fun start(onWake: () -> Unit, onAbort: () -> Unit, onError: (String) -> Unit): Boolean {
        val m = model ?: return false
        if (service != null) return true
        return try {
            val rec = Recognizer(m, SAMPLE_RATE, WakePhraseMatcher.grammarJson()).apply { setWords(true) }
            val svc = SpeechService(rec, SAMPLE_RATE)
            recognizer = rec
            service = svc
            svc.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {}
                override fun onResult(hypothesis: String?) {
                    if (hypothesis == null) return
                    if (WakePhraseMatcher.isWake(hypothesis)) {
                        stop()
                        onWake()
                    } else if (WakePhraseMatcher.isAbort(hypothesis)) {
                        onAbort()
                    }
                }
                override fun onFinalResult(hypothesis: String?) {}
                override fun onError(exception: Exception?) {
                    stop()
                    onError("Wake-word listening stopped: ${exception?.javaClass?.simpleName ?: "error"}")
                }
                override fun onTimeout() { stop() }
            })
            true
        } catch (e: IOException) {
            stop()
            false
        }
    }

    /** Releases the microphone immediately. */
    fun stop() {
        val svc = service
        service = null
        try { svc?.stop(); svc?.shutdown() } catch (_: Throwable) {}
        try { recognizer?.close() } catch (_: Throwable) {}
        recognizer = null
    }

    fun release() {
        stop()
        val m = model
        model = null
        io.execute { try { m?.close() } catch (_: Throwable) {} }
    }

    /** Copies assets/vosk-wake into private storage once per model version. */
    private fun unpackModel(): File {
        val target = File(appContext.noBackupFilesDir, "vosk-wake-$MODEL_VERSION")
        val marker = File(target, ".complete")
        if (marker.exists()) return target
        target.deleteRecursively()
        copyAssetDir(ASSET_DIR, target)
        marker.writeText(MODEL_VERSION)
        // Remove older model versions.
        appContext.noBackupFilesDir.listFiles { f -> f.name.startsWith("vosk-wake-") && f != target }
            ?.forEach { it.deleteRecursively() }
        return target
    }

    private fun copyAssetDir(assetPath: String, dest: File) {
        val children = appContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input -> dest.outputStream().use { input.copyTo(it) } }
            return
        }
        dest.mkdirs()
        children.forEach { copyAssetDir("$assetPath/$it", File(dest, it)) }
    }

    companion object {
        private const val TAG = "OfflineWakeWord"
        private const val SAMPLE_RATE = 16_000f
        private const val ASSET_DIR = "vosk-wake"
        /** Bump when the bundled model changes so it is unpacked again. */
        private const val MODEL_VERSION = "small-en-us-0.15-wake1"
    }
}
