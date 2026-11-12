package com.example

import com.example.ai.TalkOverDetector
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
    fun `her own voice through the speaker does not count as talking over her`() {
        val d = TalkOverDetector()
        d.reset(TalkOverDetector.Mode.SPEAKING)
        var sample = 0L
        // Her voice leaking through at 300-700 for 5 seconds.
        repeat(160) { i -> assertEquals(-1L, d.feed(300.0 + (i % 5) * 100, sample)); sample += 480 }
        assertTrue(d.herVoiceLevel in 250.0..800.0)
    }

    @Test
    fun `the user speaking clearly over her is noticed within a third of a second, from their first word`() {
        val d = TalkOverDetector()
        d.reset(TalkOverDetector.Mode.SPEAKING)
        var sample = 0L
        repeat(60) { d.feed(400.0, sample); sample += 480 }
        val userStart = sample
        var found = -1L
        for (i in 0 until 12) { val r = d.feed(3_000.0, sample); if (r >= 0) { found = r; break }; sample += 480 }
        assertEquals(userStart, found)
    }

    @Test
    fun `a cough or a door does not interrupt her`() {
        val d = TalkOverDetector()
        d.reset(TalkOverDetector.Mode.SPEAKING)
        var sample = 0L
        repeat(30) { d.feed(400.0, sample); sample += 480 }
        repeat(5) { assertEquals(-1L, d.feed(5_000.0, sample)); sample += 480 } // 150 ms bang
        repeat(30) { assertEquals(-1L, d.feed(400.0, sample)); sample += 480 }
    }

    @Test
    fun `while she thinks normal speech is enough`() {
        val d = TalkOverDetector()
        d.reset(TalkOverDetector.Mode.THINKING)
        var sample = 0L
        repeat(30) { d.feed(80.0, sample); sample += 480 }
        var found = -1L
        repeat(12) { if (found < 0) found = d.feed(1_200.0, sample); sample += 480 }
        assertTrue(found >= 0)
    }

    @Test
    fun `background chatter while she thinks does not drop her answer`() {
        val d = TalkOverDetector()
        d.reset(TalkOverDetector.Mode.THINKING)
        var sample = 0L
        repeat(30) { d.feed(80.0, sample); sample += 480 }
        repeat(100) { assertEquals(-1L, d.feed(400.0, sample)); sample += 480 } // TV in the room
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
