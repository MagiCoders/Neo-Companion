package com.example.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors

/**
 * Decides whether one recognised utterance is the wake phrase. Pure logic, unit-tested.
 *
 * The recogniser runs with a tiny grammar: the wake phrase (and its sound-alike spellings),
 * "stop"/"cancel", decoy words that sound similar, and "[unk]" for everything else. Ordinary talk
 * lands on "[unk]" or a decoy. A phrase must be heard whole, in order, every word confident.
 */
class WakePhraseMatcher(
    /** Each variant is one way of spelling the phrase with words the model knows. */
    val variants: List<List<String>>,
    extraDecoys: List<String> = emptyList()
) {
    constructor(phrase: List<String>) : this(listOf(phrase))

    /** The phrase as the user sees it (first variant). */
    val words: List<String> get() = variants.first()
    private val phraseWords = variants.flatten().toSet()
    private val decoys = (DECOYS + extraDecoys).filter { it !in phraseWords }.distinct()

    fun grammarJson(): String {
        val entries = variants.map { it.joinToString(" ") } + ABORT + decoys + "[unk]"
        return entries.joinToString(",", "[", "]") { "\"$it\"" }
    }

    /** @param resultJson a Vosk result with word confidences (`setWords(true)`). */
    fun isWake(resultJson: String): Boolean {
        val heard = parse(resultJson)
        return variants.any { phrase ->
            heard.size >= phrase.size && (0..heard.size - phrase.size).any { start ->
                val window = heard.subList(start, start + phrase.size)
                window.map { it.first } == phrase && window.all { it.second >= MIN_WORD_CONF }
            }
        }
    }

    /** "stop" / "cancel" said on its own, confidently. */
    fun isAbort(resultJson: String): Boolean {
        val heard = parse(resultJson)
        return heard.size == 1 && heard[0].first in ABORT && heard[0].second >= MIN_ABORT_CONF
    }

    companion object {
        /** Spoken during a countdown or while she talks, to stop her. */
        val ABORT = listOf("stop", "cancel")
        /**
         * Sound-alike words (and lone greetings) that near-misses can land on instead of the wake
         * phrase. Tested with synthetic voices: "hey Mitch", "hello everybody" or "hey mommy" no
         * longer wake her, while the real phrase still does.
         */
        val DECOYS = listOf(
            "mitch", "much", "match", "mocha", "macho", "michael", "money", "maybe", "mommy",
            "buddy", "body", "nobody", "everybody", "bobby", "hey", "hi", "hello", "okay", "honey",
            "there", "sister"
        )
        /** Tested with several synthetic voices; other speech lands on "[unk]". */
        private const val MIN_WORD_CONF = 0.50
        private const val MIN_ABORT_CONF = 0.80

        private fun parse(resultJson: String): List<Pair<String, Double>> =
            try {
                val arr = JSONObject(resultJson).optJSONArray("result")
                if (arr == null) emptyList() else (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    o.optString("word") to o.optDouble("conf", 0.0)
                }
            } catch (_: Exception) {
                emptyList()
            }
    }
}

/**
 * Turns a typed wake phrase into words the offline model can listen for. Words it knows are used
 * as they are; a name it doesn't know ("Elza") is replaced by the closest-sounding words it does
 * know ("elsa", "eliza"), or split into known pieces ("mahsa" → "mah sa"). Pure logic, unit-tested.
 */
object WakeVocabulary {
    data class Resolution(
        val variants: List<List<String>>,
        /** Typed word → the sound-alikes listened for instead. */
        val soundsLike: Map<String, List<String>>,
        val unresolved: List<String>,
        /** Close neighbours of the phrase words (e.g. "ella" for "elsa"), used as decoys. */
        val decoys: List<String>
    ) {
        val ok: Boolean get() = unresolved.isEmpty()
    }

    private const val MAX_VARIANTS = 4

    fun resolve(words: List<String>, vocab: Set<String>): Resolution {
        val keyIndex by lazy { vocab.filter { WORD.matches(it) }.groupBy(::phoneticKey) }
        val options = mutableListOf<List<List<String>>>()   // per typed word: alternatives (each 1–2 words)
        val soundsLike = linkedMapOf<String, List<String>>()
        val unresolved = mutableListOf<String>()
        for (w in words) {
            if (w in vocab) { options += listOf(listOf(w)); continue }
            val similar = keyIndex[phoneticKey(w)].orEmpty()
                .filter { it.length >= 3 && levenshtein(it, w) <= maxOf(2, w.length / 2) }
                .sortedWith(compareBy({ levenshtein(it, w) }, { kotlin.math.abs(it.length - w.length) }, { it }))
                .take(2)
            if (similar.isNotEmpty()) {
                options += similar.map { listOf(it) }
                soundsLike[w] = similar
                continue
            }
            val split = (2..w.length - 2).map { listOf(w.substring(0, it), w.substring(it)) }
                .firstOrNull { parts -> parts.all { it in vocab } }
            if (split != null) {
                options += listOf(split)
                soundsLike[w] = listOf(split.joinToString(" "))
            } else {
                unresolved += w
            }
        }
        if (unresolved.isNotEmpty()) return Resolution(emptyList(), soundsLike, unresolved, emptyList())
        var variants = listOf(emptyList<String>())
        for (alts in options) variants = variants.flatMap { v -> alts.map { v + it } }.take(MAX_VARIANTS)
        val used = variants.flatten().toSet()
        val decoys = used.filter { it.length >= 3 && it !in GREETING_WORDS }.flatMap { word ->
            val sameSound = keyIndex[phoneticKey(word)].orEmpty()
            val oneEditAway = vocab.filter {
                it.length in (word.length - 1)..(word.length + 1) && it.firstOrNull() == word.first() && levenshtein(it, word) == 1
            }
            (sameSound + oneEditAway)
                .filter { it != word && it !in used && it.length >= 3 && WORD.matches(it) }
                .distinct()
                .sortedWith(compareBy({ levenshtein(it, word) }, { it }))
                .take(4)
        }.distinct()
        return Resolution(variants, soundsLike, emptyList(), decoys)
    }

    /** A rough "sounds like" key: same key ≈ similar consonant skeleton (elza/elsa → "Als"). */
    fun phoneticKey(word: String): String {
        var w = word.lowercase()
        for ((a, b) in listOf("ph" to "f", "ck" to "k", "sch" to "sk", "gh" to "g", "kn" to "n", "wr" to "r", "qu" to "kw", "x" to "ks", "z" to "s", "q" to "k")) {
            w = w.replace(a, b)
        }
        w = w.replace(Regex("c(?=[eiy])"), "s").replace("c", "k").replace("tch", "ch")
        w = w.replace(Regex("(.)\\1+"), "$1")
        if (w.isEmpty()) return ""
        val first = if (w[0] in "aeiouy") "A" else w[0].toString()
        return first + w.substring(1).replace(Regex("[aeiouy']"), "")
    }

    fun levenshtein(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    private val WORD = Regex("^[a-z']+$")
    private val GREETING_WORDS = setOf("hey", "hi", "hello", "okay", "ok")
}

/**
 * Always-on, fully offline wake-word listener (Vosk, Apache-2.0, bundled English model in
 * assets/vosk-wake). The model's general dictation graph (graph/Gr.fst) is replaced by an empty
 * graph that keeps only its word list: grammar decoding builds its own graph, so wake results are
 * identical while the app is ~17 MB smaller (see tools/slim_vosk_graph.py).
 *
 * The microphone is opened in voice-communication mode with Android's echo canceller and noise
 * suppressor attached when the phone has them, so her own voice and room noise are reduced before
 * recognition. There are no session restarts and no beeps. Audio never leaves the phone.
 *
 * Public methods are called on the main thread; callbacks arrive on the main thread.
 */
class OfflineWakeWord(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var model: Model? = null
    private var loading = false
    @Volatile private var session: Session? = null

    val isReady: Boolean get() = model != null
    val isListening: Boolean get() = session != null

    /** Whether the last session got the phone's echo canceller / noise suppressor (for Diagnostics). */
    @Volatile var lastAudioEffects: String = "not started"
        private set

    /** Unpacks (first run only) and loads the model in the background. */
    fun prepare(onReady: () -> Unit, onFailed: (String) -> Unit) {
        if (model != null) { onReady(); return }
        if (loading) return
        loading = true
        io.execute {
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                val loaded = Model(unpackModel().absolutePath)
                main.post { loading = false; model = loaded; onReady() }
            } catch (e: Throwable) {
                Log.w(TAG, "Wake model failed to load: ${e.javaClass.simpleName}")
                main.post { loading = false; onFailed("The offline wake-word model could not be loaded.") }
            }
        }
    }

    /** Starts listening. Returns false if the microphone could not be opened. */
    fun start(matcher: WakePhraseMatcher, onWake: () -> Unit, onAbort: () -> Unit, onError: (String) -> Unit): Boolean {
        val m = model ?: return false
        if (session != null) return true
        val s = Session(m, matcher, onWake, onAbort, onError)
        if (!s.open()) return false
        session = s
        s.thread.start()
        return true
    }

    /** Releases the microphone (the recording thread closes it within ~0.1 s). */
    fun stop() {
        session?.running = false
        session = null
    }

    fun release() {
        stop()
        val m = model
        model = null
        io.execute { try { m?.close() } catch (_: Throwable) {} }
    }

    /**
     * Resolves a typed phrase against the model's vocabulary (see [WakeVocabulary]). The word list
     * is read from the model file, used, and dropped again. Result on the main thread.
     */
    fun resolvePhrase(words: List<String>, onResult: (WakeVocabulary.Resolution) -> Unit) {
        io.execute {
            val result = try {
                WakeVocabulary.resolve(words, readVocabulary(File(unpackModel(), "graph/Gr.fst")))
            } catch (e: Exception) {
                Log.w(TAG, "Vocabulary check failed: ${e.javaClass.simpleName}")
                WakeVocabulary.Resolution(emptyList(), emptyMap(), words, emptyList())
            }
            main.post { onResult(result) }
        }
    }

    private inner class Session(
        private val model: Model,
        private val matcher: WakePhraseMatcher,
        private val onWake: () -> Unit,
        private val onAbort: () -> Unit,
        private val onError: (String) -> Unit
    ) {
        @Volatile var running = true
        private var record: AudioRecord? = null
        private var recognizer: Recognizer? = null
        private val effects = mutableListOf<android.media.audiofx.AudioEffect>()
        val thread = Thread(::loop, "wake-word")

        @SuppressLint("MissingPermission") // only started when RECORD_AUDIO is granted
        fun open(): Boolean {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return false
            val bufBytes = maxOf(minBuf, SAMPLE_RATE / 5 * 2)
            for (source in listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_RECOGNITION)) {
                val r = try {
                    AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes)
                } catch (_: Exception) { null }
                if (r != null && r.state == AudioRecord.STATE_INITIALIZED) { record = r; break }
                r?.release()
            }
            val r = record ?: return false
            val applied = mutableListOf<String>()
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(r.audioSessionId)?.let { it.enabled = true; effects += it; applied += "echo canceller" }
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(r.audioSessionId)?.let { it.enabled = true; effects += it; applied += "noise suppressor" }
            lastAudioEffects = if (applied.isEmpty()) "none available on this phone" else applied.joinToString(" + ")
            return try {
                recognizer = Recognizer(model, SAMPLE_RATE.toFloat(), matcher.grammarJson()).apply { setWords(true) }
                r.startRecording()
                r.recordingState == AudioRecord.RECORDSTATE_RECORDING
            } catch (e: Exception) {
                closeAll()
                false
            }
        }

        private fun loop() {
            val buf = ShortArray(SAMPLE_RATE / 10)
            try {
                while (running) {
                    val n = record?.read(buf, 0, buf.size) ?: break
                    if (n < 0) {
                        if (running) main.post { if (session === this) { stop(); onError("Microphone error $n") } }
                        break
                    }
                    val rec = recognizer ?: break
                    if (n > 0 && rec.acceptWaveForm(buf, n)) deliver(rec.result)
                }
            } catch (e: Throwable) {
                if (running) main.post { if (session === this) { stop(); onError("Wake-word listening stopped: ${e.javaClass.simpleName}") } }
            } finally {
                closeAll()
            }
        }

        private fun deliver(result: String) {
            val wake = matcher.isWake(result)
            val abort = !wake && matcher.isAbort(result)
            if (!wake && !abort) return
            main.post {
                if (session !== this) return@post
                if (wake) { stop(); onWake() } else onAbort()
            }
        }

        private fun closeAll() {
            effects.forEach { try { it.release() } catch (_: Throwable) {} }
            effects.clear()
            try { record?.stop() } catch (_: Throwable) {}
            try { record?.release() } catch (_: Throwable) {}
            record = null
            try { recognizer?.close() } catch (_: Throwable) {}
            recognizer = null
        }
    }

    /** Reads the word symbol table stored in an OpenFst file header (little-endian). */
    private fun readVocabulary(fst: File): Set<String> {
        val buf = java.nio.ByteBuffer.wrap(fst.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        fun str(): String { val n = buf.int; val b = ByteArray(n); buf.get(b); return String(b, Charsets.UTF_8) }
        buf.int; str(); str(); buf.int                      // magic, fst type, arc type, version
        val flags = buf.int
        buf.long; buf.long; buf.long; buf.long              // properties, start, states, arcs
        check(flags and 1 != 0) { "no input symbols" }
        buf.int; str(); buf.long                            // symbol table magic, name, available key
        val size = buf.long.toInt()
        val words = HashSet<String>(size * 2)
        repeat(size) { words += str(); buf.long }
        return words
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
        private const val SAMPLE_RATE = 16_000
        private const val ASSET_DIR = "vosk-wake"
        /** Bump when the bundled model changes so it is unpacked again. */
        private const val MODEL_VERSION = "small-en-us-0.15-wake1"
    }
}
