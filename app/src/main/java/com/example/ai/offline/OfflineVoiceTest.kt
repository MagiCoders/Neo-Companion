package com.example.ai.offline

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.diag.AppTrail
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
        try {
            val b = ByteBuffer.allocate(frame.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            b.asShortBuffer().put(frame)
            synchronized(pcm) { if (pcm.size() < MAX_BYTES) pcm.write(b.array()) }
        } catch (t: Throwable) {
            AppTrail.add("LAB", "probe error ${t.javaClass.simpleName}")
        }
    }


    fun checkGoogle() {
        _googleStatus.value = "Checking…"
        scope.launch { _googleStatus.value = runCatching { GoogleOnPhoneSpeech.report() }.getOrElse { "Check failed (${it.javaClass.simpleName})" } }
    }

    /** Finger down: record from Mochi's microphone. */
    fun startRecording() {
        if (_busy.value || _recording.value) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _report.value = "Microphone permission is off. Allow it in the home screen first."
            return
        }
        AppTrail.add("LAB", "hold to talk: recording")
        synchronized(pcm) { pcm.reset() }
        voiceService.holdMicForCheck = true
        voiceService.addProbe(probe)
        _recording.value = true
    }

    /** The press was cancelled (finger slid away, screen closed): drop the recording and let go of the mic. */
    fun cancelRecording() {
        if (!_recording.value) return
        voiceService.removeProbe(probe)
        voiceService.holdMicForCheck = false
        _recording.value = false
        synchronized(pcm) { pcm.reset() }
        _report.value = "Cancelled."
    }

    /** Finger up: run the offline turn in [language] ("en", "ar", "fa") with the chosen listener. */
    fun stopAndRun(language: String, listenerPack: OfflinePack?) {
        if (!_recording.value) return
        val stoppedAt = System.currentTimeMillis()
        voiceService.removeProbe(probe)
        voiceService.holdMicForCheck = false
        _recording.value = false
        val audio = synchronized(pcm) { pcm.toByteArray() }
        AppTrail.add("LAB", "released after %.1f s of sound".format(audio.size / 32_000.0))
        if (audio.size < 16_000) {
            _report.value = if (audio.size < 16_000 / 2) {
                "No sound reached the test (%.1f s). Open the home screen once so her microphone is running, then try again.".format(audio.size / 32_000.0)
            } else "Too short: hold the button while you talk."
            return
        }
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
        line("Offline turn · language $language · you spoke %.1f s".format(seconds))
        val runner = GemmaLab.runner(appContext)
        val gemmaReady = runner.state.value is GemmaRunner.State.Loaded
        if (!gemmaReady) line("Gemma is not loaded: tap Use on a model above to time the answer.")

        // ---- A) Whisper → Gemma (streamed) ----
        var firstSentence: String? = null
        var listenSeconds = 0.0
        var answerSeconds = 0.0
        if (listenerPack != null && packs.isReady(listenerPack)) {
            val r = PhoneListener.transcribe(appContext, wav, language, forcePack = listenerPack)
            if (r is PhoneListener.Result.Unavailable) { line("A · ${r.reason}"); return out.toString().trim() }
            r as PhoneListener.Result.Text
            if (r.loadSeconds > 0) line("Listener loaded in %.1f s (once per language)".format(r.loadSeconds))
            val text = r.text
            listenSeconds = r.seconds
            line("A · ${listenerPack.label} wrote \"$text\" in %.1f s".format(r.seconds))
            if (gemmaReady && text.isNotBlank()) {
                val t1 = System.currentTimeMillis()
                val sb = StringBuilder()
                try {
                    runner.stream(SYSTEM, text).collect { piece ->
                        sb.append(piece)
                        if (firstSentence == null) OfflineAudio.firstSentence(sb.toString())?.let {
                            firstSentence = it
                            answerSeconds = (System.currentTimeMillis() - t1) / 1000.0
                            line("A · Gemma's first sentence in %.1f s: \"$it\"".format(answerSeconds))
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    line("A · Gemma failed: ${e.javaClass.simpleName}: ${e.message?.take(120).orEmpty()}")
                }
                if (firstSentence == null && sb.isNotBlank()) firstSentence = sb.toString().trim()
                line("A · Gemma's whole answer in %.1f s".format((System.currentTimeMillis() - t1) / 1000.0))
            }
            if (firstSentence != null) line("Phone mode total (listener + first sentence): %.1f s".format(listenSeconds + answerSeconds))
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

    fun release() {
        voiceService.removeProbe(probe)
        if (_recording.value) voiceService.holdMicForCheck = false
    }

    private fun memoryMb(): Int = android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }.totalPss / 1024

    private companion object {
        const val MAX_BYTES = 30 * 32_000 // 30 s
        const val SYSTEM = "You are Mochi, a warm companion. Answer in one or two short sentences, in the language the user speaks."
        val SYSTEM_HEAR = SYSTEM + " You receive a voice recording. Reply with JSON only: " +
            "{\"heard\": \"the user's exact words\", \"reply\": \"your answer\"}"
    }
}
