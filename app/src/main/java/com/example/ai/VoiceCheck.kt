package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.ui.character.CharacterSpec
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-tap Voice check (Settings → Diagnostics). Run it in a quiet room with nobody talking; it
 * takes about a minute and prints pass or fail with numbers, so a new version is judged on facts:
 *  1. Room noise at the microphone.
 *  2. Her voice played through the speaker: how much reaches the microphone, and whether anything
 *     after it would be taken as the user speaking (it must not).
 *  3. The echo guard recognises her own sentence.
 *  4. Three AI answers (turn-by-turn mode), timed.
 *  5. Three natural-voice starts, timed.
 *  6. Three Live voice answers from this phone, timed (the speed of Live conversation).
 */
@OptIn(PublicPreviewAPI::class)
class VoiceCheck(private val context: Context) {

    data class Line(val name: String, val passed: Boolean?, val detail: String)

    private val voice = VoiceCommandService.shared(context)

    suspend fun run(onLine: (Line) -> Unit): List<Line> {
        val lines = ArrayList<Line>()
        fun add(l: Line) { lines += l; onLine(l) }
        voice.holdMicForCheck = true
        try {
            // 1. Room noise.
            val room = measure(2_000L)
            add(Line("Room noise", null, "level %.0f (speech is detected above 250)".format(room.average)))

            // 2. Her voice through the speaker.
            val sentence = "This is a voice check. I am talking, and you should not hear me as yourself."
            VoiceCommandService.notifyAssistantSaid(sentence)
            val audio = withContext(Dispatchers.IO) {
                NaturalVoice.speak(sentence, "en-US", CharacterSpec.MOCHI).toList()
            }
            if (audio.isEmpty()) {
                add(Line("Her voice at the mic", null, "skipped: the natural voice is not available right now (quota or offline)"))
            } else {
                val leak = Meter()
                voice.addProbe(leak.probe)
                VoiceCommandService.notifyAssistantSpeaking(true)
                play(audio)
                VoiceCommandService.notifyAssistantSpeaking(false)
                voice.removeProbe(leak.probe)
                add(Line("Her voice at the mic", leak.average < 1_200, "level %.0f after echo cancelling (talking over her would need over 1200)".format(leak.average)))
                // After her voice: 0.3 s pause (as the app does), then 3 s must contain no "speech".
                delay(300L)
                val vad = VoiceActivity(noSpeechTimeoutMs = 60_000)
                val heardSpeech = AtomicBoolean(false)
                val tail: (ShortArray, Double) -> Unit = { _, rms -> if (vad.feed(rms) == VoiceActivity.Event.SPEECH_START) heardSpeech.set(true) }
                voice.addProbe(tail)
                delay(3_000L)
                voice.removeProbe(tail)
                add(Line("Nothing heard after she stops", !heardSpeech.get(), if (heardSpeech.get()) "FAIL: sound after her voice looked like speech (echo tail or room noise)" else "no speech detected in the 3 s after her voice"))
            }

            // 3. Echo guard.
            val echo = voice.echoGuard.isEcho("I am talking")
            add(Line("Echo guard knows her words", echo, if (echo) "her own sentence is recognised and ignored" else "FAIL: her own sentence was not recognised"))

            // 4. AI answers.
            val brain = CompanionBrain()
            val aiTimes = (1..3).map { i ->
                val t0 = System.currentTimeMillis()
                val ok = runCatching { brain.understand("Quick check $i: say hi in three words.", CharacterSpec.MOCHI, "", emptyList(), "English") }.isSuccess
                if (ok) (System.currentTimeMillis() - t0) / 1000.0 else Double.NaN
            }
            add(timed("AI answer (turn by turn)", aiTimes, 4.0))

            // 5. Natural voice first sound (distinct sentences so nothing comes from the phone's store).
            val voiceTimes = (1..3).map { i ->
                val t0 = System.currentTimeMillis()
                val first = runCatching {
                    withTimeout(15_000L) { NaturalVoice.speak("Check ${System.currentTimeMillis() % 100000} number $i.", "en-US", CharacterSpec.MOCHI).first() }
                }.getOrNull()
                if (first != null) (System.currentTimeMillis() - t0) / 1000.0 else Double.NaN
            }
            add(timed("Natural voice first sound", voiceTimes, 1.5))

            // 6. Live voice.
            add(liveTimes())
        } finally {
            voice.holdMicForCheck = false
        }
        val failed = lines.count { it.passed == false }
        add(Line("Result", failed == 0, if (failed == 0) "all checks passed" else "$failed check(s) failed"))
        return lines
    }

    /** Opens a Live session, asks three short questions by text and times the first sound of each. */
    private suspend fun liveTimes(): Line = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val session = try {
            withTimeout(8_000L) {
                Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                    modelName = AiModels.LIVE,
                    generationConfig = liveGenerationConfig {
                        responseModality = ResponseModality.AUDIO
                        speechConfig = SpeechConfig(voice = Voice("Leda"))
                    }
                ).connect()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            return@withContext Line("Live voice", false, "could not connect (${AiDiagnostics.describe(e)}): conversation will run turn by turn")
        }
        val connect = (System.currentTimeMillis() - t0) / 1000.0
        val times = ArrayList<Double>()
        try {
            kotlinx.coroutines.coroutineScope {
                // One collector for the whole session (a Live session can only be received once).
                val inbox = kotlinx.coroutines.channels.Channel<com.google.firebase.ai.type.LiveServerMessage>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                val reader = launch { session.receive().collect { inbox.send(it) } }
                suspend fun waitFor(ms: Long, test: (com.google.firebase.ai.type.LiveServerMessage) -> Boolean): Boolean =
                    runCatching { withTimeout(ms) { while (!test(inbox.receive())) Unit } }.isSuccess
                for (i in 1..3) {
                    val s0 = System.currentTimeMillis()
                    session.send("Say only: hello number $i.")
                    val got = waitFor(15_000L) { m -> m is LiveServerContent && m.content?.parts.orEmpty().any { it is InlineDataPart } }
                    times += if (got) (System.currentTimeMillis() - s0) / 1000.0 else Double.NaN
                    // Let the rest of the answer arrive before the next question.
                    waitFor(10_000L) { m -> m is LiveServerContent && m.turnComplete }
                }
                reader.cancel()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        } finally {
            runCatching { session.stopReceiving() }
            runCatching { session.close() }
        }
        val line = timed("Live voice first sound", times, 1.5)
        line.copy(detail = line.detail + " · connected in %.1f s".format(connect) +
            if (line.passed == false) " · Live will hand over to turn by turn when this slow" else "")
    }

    private fun timed(name: String, seconds: List<Double>, passAt: Double): Line {
        val ok = seconds.filter { !it.isNaN() }
        if (ok.isEmpty()) return Line(name, false, "no answer (check internet and quota)")
        val median = ok.sorted()[ok.size / 2]
        val worst = ok.max()
        val failures = seconds.size - ok.size
        return Line(
            name, median <= passAt && failures == 0,
            "median %.1f s, worst %.1f s%s (target %.1f s)".format(median, worst, if (failures > 0) ", $failures failed" else "", passAt)
        )
    }

    private class Meter {
        @Volatile var sum = 0.0
        @Volatile var n = 0
        val probe: (ShortArray, Double) -> Unit = { _, rms -> sum += rms; n++ }
        val average: Double get() = if (n == 0) 0.0 else sum / n
    }

    private suspend fun measure(ms: Long): Meter {
        val m = Meter()
        voice.addProbe(m.probe)
        delay(ms)
        voice.removeProbe(m.probe)
        return m
    }

    private suspend fun play(pieces: List<NaturalVoice.Audio>) = withContext(Dispatchers.IO) {
        val rate = pieces.first().sampleRate
        val all = ShortArray(pieces.sumOf { it.pcm.size }).also { out -> var p = 0; pieces.forEach { it.pcm.copyInto(out, p); p += it.pcm.size } }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(all.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(all, 0, all.size)
            track.play()
            while (track.playbackHeadPosition < all.size && track.playState == AudioTrack.PLAYSTATE_PLAYING) delay(30L)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    companion object {
        fun report(lines: List<Line>): String = lines.joinToString("\n") { l ->
            (when (l.passed) { true -> "PASS "; false -> "FAIL "; null -> "INFO " }) + l.name + ": " + l.detail
        }
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.toList(): List<T> {
    val out = ArrayList<T>()
    collect { out += it }
    return out
}
