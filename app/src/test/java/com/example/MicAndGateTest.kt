package com.example

import com.example.ai.MicContention
import com.example.ai.MicContention.Sample
import com.example.ai.MicContention.State
import com.example.ai.SoundGate
import com.example.ai.SoundGate.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicAndGateTest {

    @Test
    fun `a moment of overlap never pauses her`() {
        val m = MicContention(holdMs = 1_000)
        assertNull(m.update(Sample(ownRecorderSilenced = true, inCall = false), 0))
        assertNull(m.update(Sample(false, false), 500)) // gone again within the hold
        assertNull(m.update(Sample(true, false), 900))
        assertNull(m.update(Sample(true, false), 1_500))
        assertEquals(State.SILENCED, m.update(Sample(true, false), 1_900))
        assertEquals(State.SILENCED, m.state)
    }

    @Test
    fun `free again only after it holds, and a call wins over silenced`() {
        val m = MicContention(holdMs = 1_000)
        m.update(Sample(true, false), 0); m.update(Sample(true, false), 1_000)
        assertEquals(State.SILENCED, m.state)
        assertNull(m.update(Sample(false, false), 2_000))
        assertNull(m.update(Sample(false, false), 2_500))
        assertEquals(State.FREE, m.update(Sample(false, false), 3_000))
        m.update(Sample(true, true), 4_000)
        assertEquals(State.CALL, m.update(Sample(true, true), 5_000))
    }

    @Test
    fun `nothing reported means never paused`() {
        val m = MicContention()
        for (t in 0L..60_000L step 1_000L) assertNull(m.update(Sample(false, false), t))
        assertEquals(State.FREE, m.state)
    }

    @Test
    fun `quiet room is skipped and speech opens the gate with lead-in`() {
        val g = SoundGate(closeAfterQuietBlocks = 3)
        repeat(50) { assertEquals(Decision.SKIP, g.decide(60.0)) }
        assertEquals(Decision.OPEN, g.decide(900.0))
        assertEquals(Decision.FEED, g.decide(800.0))
        assertEquals(Decision.FEED, g.decide(50.0))
        assertEquals(Decision.FEED, g.decide(50.0))
        assertEquals(Decision.CLOSE, g.decide(50.0))
        assertEquals(Decision.SKIP, g.decide(50.0))
        assertTrue("decoded share ${g.decodedShare()}", g.decodedShare() < 0.2)
    }

    @Test
    fun `a pause between words keeps the gate open`() {
        val g = SoundGate(closeAfterQuietBlocks = 12)
        repeat(20) { g.decide(60.0) }
        assertEquals(Decision.OPEN, g.decide(700.0))
        repeat(8) { assertEquals(Decision.FEED, g.decide(70.0)) } // 0.8 s gap
        assertEquals(Decision.FEED, g.decide(700.0))
    }

    @Test
    fun `steady loud noise raises the floor so the gate does not stay open forever`() {
        val g = SoundGate(closeAfterQuietBlocks = 3)
        // A fan slowly getting louder: the floor follows while the gate is closed.
        var level = 60.0
        repeat(300) { level = minOf(level * 1.01, 400.0); g.decide(level) }
        assertEquals(Decision.SKIP, g.decide(420.0))
    }

    @Test
    fun `noise that starts after a quiet moment is learned instead of decoded forever`() {
        // What happened on the S22+: the room measured quiet at first, then steady noise kept the
        // gate open 95% of the time. Now the gate closes after its limit and learns the level.
        val g = SoundGate()
        repeat(10) { g.decide(40.0) }
        repeat(600) { g.decide(180.0 + (it % 3) * 10) } // a minute of steady noise
        assertTrue("decoded share ${g.decodedShare()}", g.decodedShare() < 0.2)
        // A voice clearly above that noise still opens it.
        var opened = false
        repeat(5) { if (g.decide(900.0) == Decision.OPEN) opened = true }
        assertTrue(opened)
    }

    @Test
    fun `soft but clear speech still opens the gate`() {
        val g = SoundGate()
        repeat(30) { g.decide(35.0) }
        assertEquals(Decision.OPEN, g.decide(150.0))
    }
}
