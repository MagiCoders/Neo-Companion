package com.example

import com.example.ai.EchoGuard
import com.example.ai.LoopBreaker
import com.example.ai.VoiceActivity
import com.example.ai.WakePhraseMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OneMicTest {

    private fun result(vararg words: Pair<String, Double>) =
        "{\"result\":[" + words.joinToString(",") { "{\"word\":\"${it.first}\",\"conf\":${it.second}}" } + "],\"text\":\"\"}"

    @Test
    fun `words after the wake phrase mean the request came in the same breath`() {
        val m = WakePhraseMatcher(listOf(listOf("hi", "elsa"), listOf("hi", "eliza")))
        assertEquals(0, m.wordsAfterWake(result("hi" to 0.9, "elsa" to 0.9)))
        assertEquals(2, m.wordsAfterWake(result("hi" to 0.9, "elsa" to 0.9, "[unk]" to 1.0, "[unk]" to 1.0)))
        assertEquals(1, m.wordsAfterWake(result("[unk]" to 1.0, "hi" to 0.9, "eliza" to 0.8, "[unk]" to 1.0)))
        assertEquals(0, m.wordsAfterWake(result("[unk]" to 1.0)))
    }

    @Test
    fun `her own short phrases are recognised as echo, the user's questions are not`() {
        var now = 0L
        val g = EchoGuard { now }
        g.remember("I'm listening. Tell me what's on your mind.")
        now = 2_000
        assertTrue(g.isEcho("I'm listening..."))
        assertTrue(g.isEcho("tell me what's on"))
        g.remember("Yes, I can hear you clearly.")
        assertTrue(!g.isEcho("can you hear me"))
        assertTrue(!g.isEcho("open WhatsApp"))
        now = 60_000 // too long ago
        assertTrue(!g.isEcho("I'm listening"))
    }

    @Test
    fun `three quick short turns with nobody calling her is a loop`() {
        val t0 = 100_000L
        assertTrue(LoopBreaker.isLoop(listOf(t0 to 2, t0 + 6_000 to 2, t0 + 12_000 to 1), lastWakeOrTapAt = t0 - 5_000))
        // A wake phrase or tap during them: a real conversation.
        assertTrue(!LoopBreaker.isLoop(listOf(t0 to 2, t0 + 6_000 to 2, t0 + 12_000 to 1), lastWakeOrTapAt = t0 + 1_000))
        // Longer sentences: a real conversation.
        assertTrue(!LoopBreaker.isLoop(listOf(t0 to 2, t0 + 6_000 to 6, t0 + 12_000 to 1), lastWakeOrTapAt = 0))
        // Slow turns: a real conversation.
        assertTrue(!LoopBreaker.isLoop(listOf(t0 to 2, t0 + 20_000 to 2, t0 + 30_000 to 1), lastWakeOrTapAt = 0))
        assertTrue(!LoopBreaker.isLoop(listOf(t0 to 2, t0 + 6_000 to 2), lastWakeOrTapAt = 0))
    }

    @Test
    fun `short requests end after a shorter pause than long ones`() {
        fun framesToEnd(voicedFrames: Int): Int {
            val vad = VoiceActivity(endSilenceMs = 1_300, shortEndSilenceMs = 900)
            repeat(20) { vad.feed(80.0) }
            repeat(voicedFrames) { vad.feed(2_000.0) }
            var n = 0
            while (vad.feed(80.0) != VoiceActivity.Event.SPEECH_END) { n++; if (n > 200) break }
            return n + 1
        }
        assertEquals(30, framesToEnd(40))  // ~1.2 s request: 0.9 s pause
        assertEquals(44, framesToEnd(120)) // ~3.6 s request: 1.3 s pause (rounded to frames)
    }
}
