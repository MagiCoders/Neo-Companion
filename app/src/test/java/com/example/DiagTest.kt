package com.example

import com.example.ai.VoiceActivity
import com.example.diag.AppTrail
import com.example.diag.ExitReasons
import com.example.diag.TrailBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagTest {

    @Test fun trailKeepsNewestLinesInOrder() {
        val b = TrailBuffer(3)
        (1..5).forEach { b.add("l$it") }
        assertEquals(listOf("l3", "l4", "l5"), b.tail(10))
        assertEquals(listOf("l4", "l5"), b.tail(2))
    }

    @Test fun runsAreSplitOnStartMarks() {
        val runs = AppTrail.splitRuns(listOf("${AppTrail.START_MARK} v1 ----", "a", "b", "${AppTrail.START_MARK} v2 ----", "c"))
        assertEquals(2, runs.size)
        assertEquals(listOf("${AppTrail.START_MARK} v1 ----", "a", "b"), runs[0])
        assertEquals("c", runs[1].last())
    }

    @Test fun exitReasonsHaveReadableNames() {
        // Values of ApplicationExitInfo.REASON_*: CRASH 4, CRASH_NATIVE 5, LOW_MEMORY 3, ANR 6.
        assertTrue(ExitReasons.reasonName(4).contains("app crash"))
        assertTrue(ExitReasons.reasonName(5).contains("native"))
        assertTrue(ExitReasons.reasonName(3).contains("out of memory"))
        assertTrue(ExitReasons.reasonName(6).contains("frozen"))
        assertEquals("other (999)", ExitReasons.reasonName(999))
    }

    @Test fun nativeCrashStringsAreExtracted() {
        val bytes = "\u0001\u0002libsherpa-onnx-jni.so\u0000xx\u0003SIGSEGV fault addr\u0000a.so\u0000unrelated text here".toByteArray()
        val frames = ExitReasons.readableFrames(bytes)
        assertTrue(frames.contains("libsherpa-onnx-jni.so"))
        assertTrue(frames.contains("SIGSEGV fault addr"))
        assertFalse(frames.contains("unrelated text here"))
        assertFalse(frames.contains("a.so"))
    }

    @Test fun voiceActivityCountsLoudFrames() {
        val vad = VoiceActivity(endSilenceMs = 300, noSpeechTimeoutMs = 60_000)
        repeat(10) { vad.feed(100.0) }
        repeat(5) { vad.feed(4000.0) }
        repeat(3) { vad.feed(100.0) }
        repeat(8) { vad.feed(4000.0) }
        assertTrue(vad.framesAbove >= 8)
        assertTrue(vad.longestRunMs >= 8 * 30)
    }
}
