package com.example

import com.example.ai.gemma.GemmaEngine
import com.example.ai.offline.OfflineAudio
import com.example.ai.offline.OfflineCatalog
import com.example.ai.offline.PhoneListener
import com.example.ai.offline.WhisperText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs under Robolectric because GemmaEngine reads JSON with Android's org.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineVoiceUnitTest {

    @Test fun wavHeaderIsSkippedAndSamplesScaled() {
        val header = ByteArray(44).also { "RIFF".toByteArray().copyInto(it) }
        val pcm = byteArrayOf(0x00, 0x40, 0x00, 0xC0.toByte()) // 16384, -16384
        val f = OfflineAudio.wavToFloats(header + pcm)
        assertEquals(2, f.size)
        assertEquals(0.5f, f[0], 1e-4f)
        assertEquals(-0.5f, f[1], 1e-4f)
        assertEquals(2, OfflineAudio.wavToFloats(pcm).size) // raw PCM works too
    }

    @Test fun firstSentenceWaitsForTheEnd() {
        assertNull(OfflineAudio.firstSentence("Sure, I can hel"))
        assertEquals("Sure, I can help with that.", OfflineAudio.firstSentence("Sure, I can help with that. Which one"))
        assertNull(OfflineAudio.firstSentence("Hi. ")) // too short to start speaking on
        assertEquals("شلونك اليوم؟", OfflineAudio.firstSentence("شلونك اليوم؟ آني"))
    }

    @Test fun gemmaHeardIsRead() {
        assertEquals("call ali", GemmaEngine.parseHeard("""```json
            {"heard": "call ali", "reply": "Calling Ali.", "action": "CALL", "target": "Ali"}```"""))
        assertEquals("", GemmaEngine.parseHeard("""{"heard": ""}"""))
        assertNull(GemmaEngine.parseHeard("no json here"))
        assertNull(GemmaEngine.parseHeard("""{"reply":"hi"}"""))
    }

    @Test fun catalogPointsAtTheRightFiles() {
        OfflineCatalog.ALL.forEach { pack ->
            pack.files.forEach { (name, size) ->
                assertTrue(pack.url(name).startsWith("https://huggingface.co/csukuangfj/"))
                assertTrue(size > 0)
            }
        }
        OfflineCatalog.LISTENERS.forEach { l ->
            val prefix = l.files.first().first.substringBefore("-encoder")
            assertTrue(l.files.any { it.first == "$prefix-decoder.int8.onnx" })
            assertTrue(l.files.any { it.first == "$prefix-tokens.txt" })
        }
    }

    @Test fun listenerChoiceFollowsLanguage() {
        val both = setOf(OfflineCatalog.LISTENER_BASE.id, OfflineCatalog.LISTENER_SMALL.id)
        assertEquals(OfflineCatalog.LISTENER_BASE, PhoneListener.choosePack("en", both))
        assertEquals(OfflineCatalog.LISTENER_SMALL, PhoneListener.choosePack("ar", both))
        assertEquals(OfflineCatalog.LISTENER_BASE, PhoneListener.choosePack("ar", setOf(OfflineCatalog.LISTENER_BASE.id)))
        assertNull(PhoneListener.choosePack("fa", emptySet()))
        assertEquals("ar", PhoneListener.whisperLanguage("ar-IQ"))
        assertEquals("en", PhoneListener.whisperLanguage("xx-YY"))
    }

    @Test fun whisperSilencePhrasesAreDropped() {
        assertEquals("", WhisperText.clean(" [BLANK_AUDIO] "))
        assertEquals("", WhisperText.clean("(music)"))
        assertEquals("", WhisperText.clean("Thank you."))
        assertEquals("", WhisperText.clean("..."))
        assertEquals("Thank you, call Ali", WhisperText.clean("Thank you, call Ali"))
        assertEquals("شلونك اليوم؟", WhisperText.clean("شلونك اليوم؟"))
    }
}
