package com.example.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the voice pipeline heard and did, newest last (Settings → Diagnostics → Voice log).
 * In memory only, cleared when the app closes; it exists so problems can be fixed from facts
 * ("echo ignored", "AI took 3.1 s on HIGH") instead of guesses.
 */
object VoiceLog {
    enum class Kind { WAKE, HEARD, IGNORED, AI, SPOKE, ERROR }

    data class Entry(val atMillis: Long, val kind: Kind, val text: String)

    private const val MAX = 40
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(kind: Kind, text: String) {
        // Events only: wake and error lines never quote speech, so they are mirrored to the app trail.
        if (kind == Kind.WAKE || kind == Kind.ERROR) com.example.diag.AppTrail.add("VOICE", text)
        val entry = Entry(System.currentTimeMillis(), kind, text.take(200))
        _entries.value = (_entries.value + entry).takeLast(MAX)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

/**
 * How hard the model should think for one message. Quick commands and chit-chat get LOW (fast,
 * and calls/messages are still double-checked by the app and a countdown); real tasks get HIGH.
 * Pure logic, unit-tested.
 */
object ThinkingRouter {
    enum class Level { LOW, MEDIUM, HIGH }

    private val TASK_WORDS = Regex(
        "(?i)\\b(plan|planning|schedule|organi[sz]e|budget|recipe|cook|split|bill|explain|compare|why|" +
            "how (do|can|should|would)|help me|write|draft|summari[sz]e|translate|calculate|advice|decide|" +
            "should i|steps|list of|itinerary|workout|diet)\\b|" +
            // Arabic / Persian: plan, recipe, explain, why, how, help, write, translate
            "خطة|برنامه|وصفة|دستور|اشرح|توضیح|لماذا|چرا|كيف|چطور|ساعد|کمک|اكتب|بنویس|ترجم|ترجمه"
    )

    fun levelFor(text: String): Level {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        return when {
            TASK_WORDS.containsMatchIn(text) || words > 40 -> Level.HIGH
            words > 14 -> Level.MEDIUM
            else -> Level.LOW
        }
    }
}
