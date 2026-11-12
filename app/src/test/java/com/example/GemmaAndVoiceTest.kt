package com.example

import com.example.ai.VoiceGender
import com.example.ai.gemma.GemmaBench
import com.example.ai.gemma.GemmaCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GemmaAndVoiceTest {

    @Test
    fun `phone voice gender is read from Samsung and Google voice names`() {
        assertEquals(true, VoiceGender.guess("en-US-SMTf00"))
        assertEquals(false, VoiceGender.guess("en-US-SMTm00"))
        assertEquals(true, VoiceGender.guess("en-us-x-sfg#female_1-local"))
        assertEquals(false, VoiceGender.guess("en-us-x-iol#male_2-local"))
        assertEquals(true, VoiceGender.guess("en-us-x-tpf-network"))
        assertEquals(false, VoiceGender.guess("en-us-x-iom-local"))
        assertNull(VoiceGender.guess("some-engine-voice-7"))
        assertTrue(VoiceGender.isFemaleCharacter("mochi"))
        assertFalse(VoiceGender.isFemaleCharacter("kaito"))
    }

    @Test
    fun `model fit follows phone memory`() {
        val s22plus = 7_500_000_000L // an "8 GB" phone reports a little less
        assertEquals(GemmaCatalog.Fit.GOOD, GemmaCatalog.fit(GemmaCatalog.E2B, s22plus))
        assertEquals(GemmaCatalog.Fit.TIGHT, GemmaCatalog.fit(GemmaCatalog.E4B, s22plus))
        assertEquals(GemmaCatalog.Fit.TOO_SMALL, GemmaCatalog.fit(GemmaCatalog.E4B, 5_600_000_000L))
        assertEquals(GemmaCatalog.Fit.GOOD, GemmaCatalog.fit(GemmaCatalog.E4B, 11_500_000_000L))
    }

    @Test
    fun `published hash is read from the etag header`() {
        assertEquals("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            GemmaCatalog.sha256FromEtag("\"181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c\""))
        assertNull(GemmaCatalog.sha256FromEtag("\"abc\""))
        assertNull(GemmaCatalog.sha256FromEtag(null))
    }

    @Test
    fun `quality test scores model answers`() {
        val call = GemmaBench.CASES.first { it.action == "CALL" && it.target == "brother" }
        assertTrue(GemmaBench.score(call, "{\"action\":\"CALL\",\"target\":\"my brother\"}", 1.0).passed)
        assertTrue(GemmaBench.score(call, "Sure! ```json\n{\"action\": \"call\", \"target\": \"Brother\"}\n```", 1.0).passed)
        assertFalse(GemmaBench.score(call, "{\"action\":\"WHATSAPP_CALL\",\"target\":\"brother\"}", 1.0).passed)
        assertFalse(GemmaBench.score(call, "I will call your brother.", 1.0).passed)
        val spotify = GemmaBench.CASES.first { it.target == "spotify" }
        val partial = GemmaBench.score(spotify, "{\"action\":\"OPEN_APP\",\"target\":\"\"}", 1.0)
        assertFalse(partial.passed)
        assertTrue(partial.understood)
        assertEquals(16, GemmaBench.CASES.size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GemmaEngineTest {
    @Test
    fun `gemma answers are turned into a reply and a checked action`() {
        val (reply, type, target) = com.example.ai.gemma.GemmaEngine.parse("{\"reply\":\"Calling Ali now.\",\"action\":\"CALL\",\"target\":\"Ali\"}")
        assertEquals("Calling Ali now.", reply)
        assertEquals(com.example.data.CompanionActionType.SIM_CALL, type)
        assertEquals("Ali", target)
        val chat = com.example.ai.gemma.GemmaEngine.parse("```json\n{\"reply\":\"شلونك؟\",\"action\":\"NONE\",\"target\":\"\"}\n```")
        assertEquals("شلونك؟", chat.first)
        assertEquals(com.example.data.CompanionActionType.NONE, chat.second)
        // Plain text instead of JSON: spoken as it is, no action.
        val plain = com.example.ai.gemma.GemmaEngine.parse("Sure, here you go!")
        assertEquals("Sure, here you go!", plain.first)
        assertEquals(com.example.data.CompanionActionType.NONE, plain.second)
        // Unknown actions never run.
        assertEquals(com.example.data.CompanionActionType.NONE, com.example.ai.gemma.GemmaEngine.parse("{\"reply\":\"ok\",\"action\":\"DELETE_FILES\"}").second)
    }
}
