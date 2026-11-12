package com.example.ai.gemma

import org.json.JSONObject

/**
 * A short quality test for an on-phone model: everyday requests in English, Arabic and Persian,
 * each with the action it should be understood as. The model is asked for a tiny JSON answer and
 * scored on the action (and the name or app where one is expected). Pure logic, unit-tested; the
 * runner does the asking.
 */
object GemmaBench {

    data class Case(val text: String, val language: String, val action: String, val target: String? = null)

    /**
     * [passed] = right action and the expected detail; [understood] = right action (the detail —
     * a name, app or time — may be missing).
     */
    data class Outcome(val case: Case, val answer: String, val action: String?, val target: String?, val passed: Boolean, val seconds: Double, val understood: Boolean = passed)

    val ACTIONS = listOf("CALL", "WHATSAPP_CALL", "OPEN_APP", "YOUTUBE", "SET_ALARM", "SET_TIMER", "READ_MESSAGES", "TIME", "CHAT")

    val SYSTEM_PROMPT = """
        You turn a phone user's request into JSON. Reply with JSON only, no other text:
        {"action": one of ${ACTIONS.joinToString(", ")}, "target": the person, app, video search or time, or ""}
        CALL = phone call. WHATSAPP_CALL = call on WhatsApp. OPEN_APP = open an app. YOUTUBE = play or search a video.
        SET_ALARM = alarm at a time. SET_TIMER = countdown. READ_MESSAGES = read new messages. TIME = asks the time or date.
        CHAT = anything else (questions, small talk). Requests may be in English, Arabic or Persian.
    """.trimIndent()

    val CASES = listOf(
        Case("Call my brother", "English", "CALL", "brother"),
        Case("Call Sara on WhatsApp", "English", "WHATSAPP_CALL", "sara"),
        Case("Open Spotify", "English", "OPEN_APP", "spotify"),
        Case("Play relaxing music on YouTube", "English", "YOUTUBE", "relaxing"),
        Case("Wake me up at 6:30 tomorrow", "English", "SET_ALARM", "6:30"),
        Case("Set a timer for ten minutes", "English", "SET_TIMER"),
        Case("What time is it?", "English", "TIME"),
        Case("Tell me something fun about space", "English", "CHAT"),
        Case("اتصل بأمي", "Arabic", "CALL"),
        Case("افتح الواتساب", "Arabic", "OPEN_APP"),
        Case("شغلي فيروز على اليوتيوب", "Arabic", "YOUTUBE"),
        Case("اقرالي الرسائل", "Arabic", "READ_MESSAGES"),
        Case("شلونك اليوم؟", "Arabic", "CHAT"),
        Case("به علی زنگ بزن", "Persian", "CALL"),
        Case("ساعت ۷ صبح بیدارم کن", "Persian", "SET_ALARM", "7"),
        Case("تایمر پنج دقیقه بذار", "Persian", "SET_TIMER")
    )

    /** Finds the first JSON object in a model answer and reads action and target from it. */
    fun parse(answer: String): Pair<String?, String?> {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null to null
        return try {
            val o = JSONObject(answer.substring(start, end + 1))
            o.optString("action").trim().uppercase().ifBlank { null } to o.optString("target").trim().ifBlank { null }
        } catch (_: Exception) {
            null to null
        }
    }

    /** Right action, and the expected name/app/time appears in the target (any script, any case). */
    fun score(case: Case, answer: String, seconds: Double): Outcome {
        val (action, target) = parse(answer)
        val actionOk = action == case.action
        val targetOk = case.target == null || (target ?: "").lowercase().contains(case.target.lowercase())
        return Outcome(case, answer, action, target, actionOk && targetOk, seconds, understood = actionOk)
    }
}
