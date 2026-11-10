package com.example

import com.example.ai.Recovery
import com.example.ai.VoiceFlags
import com.example.ai.VoicePhase
import com.example.ai.VoicePhases
import com.example.ai.VoiceWatchdog
import org.junit.Assert.assertEquals
import org.junit.Test

class VoicePhaseTest {
    private fun flags(
        host: Boolean = true, paused: Boolean = false, speaking: Boolean = false,
        awaiting: Boolean = false, command: Boolean = false, wake: Boolean = false
    ) = VoiceFlags(host, paused, speaking, awaiting, command, wake)

    @Test
    fun `phase follows the flags in priority order`() {
        assertEquals(VoicePhase.OFF, VoicePhases.derive(flags(host = false, speaking = true)))
        assertEquals(VoicePhase.PAUSED, VoicePhases.derive(flags(paused = true, speaking = true)))
        assertEquals(VoicePhase.SPEAKING, VoicePhases.derive(flags(speaking = true, awaiting = true)))
        assertEquals(VoicePhase.THINKING, VoicePhases.derive(flags(awaiting = true, command = true)))
        assertEquals(VoicePhase.LISTENING, VoicePhases.derive(flags(command = true, wake = true)))
        assertEquals(VoicePhase.WAKE, VoicePhases.derive(flags(wake = true)))
        assertEquals(VoicePhase.IDLE, VoicePhases.derive(flags()))
    }

    @Test
    fun `watchdog resets only what has been stuck too long`() {
        val w = VoiceWatchdog()
        assertEquals(Recovery.NONE, w.check(VoicePhase.THINKING, false, 10_000))
        assertEquals(Recovery.STOP_WAITING, w.check(VoicePhase.THINKING, false, 31_000))
        assertEquals(Recovery.NONE, w.check(VoicePhase.SPEAKING, false, 60_000))
        assertEquals(Recovery.STOP_SPEAKING, w.check(VoicePhase.SPEAKING, false, 121_000))
        assertEquals(Recovery.CLOSE_MIC, w.check(VoicePhase.LISTENING, false, 46_000))
        assertEquals(Recovery.NONE, w.check(VoicePhase.IDLE, false, 999_000))
        assertEquals(Recovery.RESTART_WAKE, w.check(VoicePhase.IDLE, true, 9_000))
        assertEquals(Recovery.NONE, w.check(VoicePhase.WAKE, true, 999_000))
        assertEquals(Recovery.CLOSE_WAKE, w.check(VoicePhase.WAKE, false, 5_000))
        assertEquals(Recovery.NONE, w.check(VoicePhase.PAUSED, true, 999_000))
        assertEquals(Recovery.NONE, w.check(VoicePhase.OFF, true, 999_000))
    }
}
