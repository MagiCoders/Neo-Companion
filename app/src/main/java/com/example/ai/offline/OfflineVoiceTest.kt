package com.example.ai.offline

import android.content.Context
import com.example.ai.SpeechCapture
import com.example.ai.VoiceCommandService
import com.example.ai.gemma.GemmaLab
import com.example.ai.gemma.GemmaRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TEST ONLY: one full offline turn, timed. Hold to talk on Mochi's own microphone, release, then:
 *  A) Whisper writes down what you said → Gemma answers (streamed) → Piper speaks the first sentence;
 *  B) Gemma hears the same recording directly (no Whisper) and answers.
 * The report says how long each step took and when her first sound came after you stopped.
 */
class OfflineVoiceTest(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val voiceService = VoiceCommandService.shared(appContext)
    val packs = OfflinePackDownloader(appContext)

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _report = MutableStateFlow("")
    val report: StateFlow<String> = _report.asStateFlow()
    private val _googleStatus = MutableStateFlow("")
    val googleStatus: StateFlow<String> = _googleStatus.asStateFlow()

    private val pcm = ByteArrayOutputStream()
    private val probe: (ShortArray, Double) -> Unit = { frame, _ ->
        val b = ByteBuffer.allocate(frame.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.asShortBuffer().put(frame)
        synchronized(pcm) { if (pcm.size() < MAX_BYTES) pcm.write(b.array()) }
    }

    private var listener: OfflineListener? = null
    private var listenerKey = ""
    private var voice: OfflineVoice? = null
    private var voiceKey = ""

    fun checkGoogle() {
        _googleStatus.value = "Checking…"
        scope.launch { _googleStatus.value = runCatching { GoogleOnPhoneSpeech.report() }.getOrElse { "Check failed (${it.javaClass.simpleName})" } }
    }

    /** Finger down: record from Mochi's microphone. */
    fun startRecording() {
        if (_busy.value || _recording.value) return
        synchronized(pcm) { pcm.reset() }
        voiceService.holdMicForCheck = true
        voiceService.addProbe(probe)
        _recording.value = true
    }

    /** Finger up: run the offline turn in [language] ("en", "ar", "fa") with the chosen listener. */
    fun stopAndRun(language: String, listenerPack: OfflinePack?) {
        if (!_recording.value) return
        val stoppedAt = System.currentTimeMillis()
        voiceService.removeProbe(probe)
        voiceService.holdMicForCheck = false
        _recording.value = false
        val audio = synchronized(pcm) { pcm.toByteArray() }
        if (audio.size < 16_000) { _report.value = "Too short: hold the button while you talk."; return }
        _busy.value = true
        scope.launch {
            try { _report.value = runTurn(SpeechCapture.wav(audio), audio.size / 32_000.0, stoppedAt, language, listenerPack) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _report.value = "Test failed: ${e.javaClass.simpleName}: ${e.message?.take(160).orEmpty()}"
            } finally { _busy.value = false }
        }
    }

    private suspend fun runTurn(wav: ByteArray, seconds: Double, stoppedAt: Long, language: String, listenerPack: OfflinePack?): String {
        val out = StringBuilder()
        fun line(s: String) { out.appendLine(s); _report.value = out.toString() }
        fun since() = (System.currentTimeMillis() - stoppedAt) / 1000.0
        line("Offline turn · language $language · you spoke %.1f s".format(seconds))
        val runner = GemmaLab.runner(appContext)
        val gemmaReady = runner.state.value is GemmaRunner.State.Loaded
        if (!gemmaReady) line("Gemma is not loaded: tap Use on a model above to time the answer.")

        // ---- A) Whisper → Gemma (streamed) → Piper ----
        var firstSentence: String? = null
        if (listenerPack != null && packs.isReady(listenerPack)) {
            val key = "${listenerPack.id}|$language"
            if (listener == null || listenerKey != key) {
                val t = System.currentTimeMillis()
                listener?.release(); listener = null
                listener = withContext(Dispatchers.IO) { OfflineListener.load(packs.dir(listenerPack), listenerPack, language) }
                listenerKey = key
                line("Listener loaded in %.1f s (once per language)".format((System.currentTimeMillis() - t) / 1000.0))
            }
            val t0 = System.currentTimeMillis()
            val text = withContext(Dispatchers.Default) { listener!!.transcribe(wav) }
            line("A · ${listenerPack.label} wrote \"$text\" in %.1f s".format((System.currentTimeMillis() - t0) / 1000.0))
            if (gemmaReady && text.isNotBlank()) {
                val t1 = System.currentTimeMillis()
                val sb = StringBuilder()
                try {
                    runner.stream(SYSTEM, text).collect { piece ->
                        sb.append(piece)
                        if (firstSentence == null) OfflineAudio.firstSentence(sb.toString())?.let {
                            firstSentence = it
                            line("A · Gemma's first sentence in %.1f s: \"$it\"".format((System.currentTimeMillis() - t1) / 1000.0))
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    line("A · Gemma failed: ${e.javaClass.simpleName}: ${e.message?.take(120).orEmpty()}")
                }
                if (firstSentence == null && sb.isNotBlank()) firstSentence = sb.toString().trim()
                line("A · Gemma's whole answer in %.1f s".format((System.currentTimeMillis() - t1) / 1000.0))
            }
            val sentence = firstSentence
            if (sentence != null) speakOffline(sentence, language, ::line, ::since)
        } else {
            line("A · skipped: download a Whisper listener below.")
        }

        // ---- B) Gemma hears the recording directly ----
        if (gemmaReady) {
            if (runner.hearsAudio) {
                val t2 = System.currentTimeMillis()
                try {
                    val raw = runner.answerAudio(SYSTEM_HEAR, wav, "Listen and answer with JSON only.")
                    val heard = com.example.ai.gemma.GemmaEngine.parseHeard(raw)
                    val reply = com.example.ai.gemma.GemmaEngine.parse(raw).first
                    line("B · Gemma heard \"$heard\" and answered \"${reply.take(120)}\" in %.1f s".format((System.currentTimeMillis() - t2) / 1000.0))
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    line("B · Gemma could not hear: ${e.javaClass.simpleName}: ${e.message?.take(120).orEmpty()}")
                }
            } else line("B · this model loaded without hearing on this phone.")
        }
        line("Memory now: ${memoryMb()} MB")
        return out.toString().trim()
    }

    private suspend fun speakOffline(sentence: String, language: String, line: (String) -> Unit, since: () -> Double) {
        val pack = OfflineCatalog.voiceFor(language)
        if (!packs.isReady(pack)) { line("A · voice skipped: download ${pack.label} below."); return }
        withContext(Dispatchers.IO) {
            if (voice == null || voiceKey != pack.id) {
                val t = System.currentTimeMillis()
                voice?.release(); voice = null
                voice = OfflineVoice.load(appContext, packs.dir(pack), pack)
                voiceKey = pack.id
                line("Voice loaded in %.1f s (once per language)".format((System.currentTimeMillis() - t) / 1000.0))
            }
            val t3 = System.currentTimeMillis()
            var first = 0.0
            voice!!.speak(sentence) {
                first = since()
                line("A · ${pack.label} first sound %.1f s after making it · FIRST SOUND %.1f s AFTER YOU STOPPED".format((System.currentTimeMillis() - t3) / 1000.0, first))
            }
            if (first == 0.0) line("A · the voice made no sound.")
        }
    }

    fun release() {
        voiceService.removeProbe(probe)
        if (_recording.value) voiceService.holdMicForCheck = false
        listener?.release(); listener = null
        voice?.stop(); voice?.release(); voice = null
    }

    private fun memoryMb(): Int = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }.totalPss / 1024

    private companion object {
        const val MAX_BYTES = 30 * 32_000 // 30 s
        const val SYSTEM = "You are Mochi, a warm companion. Answer in one or two short sentences, in the language the user speaks."
        val SYSTEM_HEAR = SYSTEM + " You receive a voice recording. Reply with JSON only: " +
            "{\"heard\": \"the user's exact words\", \"reply\": \"your answer\"}"
    }
}
