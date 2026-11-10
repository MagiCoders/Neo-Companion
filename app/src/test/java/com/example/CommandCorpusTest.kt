package com.example

import com.example.ai.LocalCommands
import com.example.data.CompanionActionType
import com.example.data.CompanionChatTurn
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reliability gate: a corpus of everyday requests in English, Arabic and Persian, each with
 * the action it must produce (or null = must go to the AI). The build fails below [GATE].
 * Sentences are what a speech recogniser writes down, so spelling variants and dialect words are
 * included on purpose. When a phrase fails in real life, add it here first, then fix the parser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommandCorpusTest {

    private data class Case(
        val text: String,
        val lang: String,
        val type: CompanionActionType?,
        /** recipient / app / query / "hh:mm" / "seconds" depending on the type; null = not checked. */
        val detail: String? = null
    )

    private val E = "en-US"
    private val A = "ar-IQ"
    private val F = "fa-IR"

    private val corpus = listOf(
        // ---- Calls ----
        Case("call Ali", E, CompanionActionType.SIM_CALL, "Ali"),
        Case("call my mom", E, CompanionActionType.SIM_CALL, "mom"),
        Case("please call Sara", E, CompanionActionType.SIM_CALL, "Sara"),
        Case("phone dad", E, CompanionActionType.SIM_CALL, "dad"),
        Case("اتصل بعلي", A, CompanionActionType.SIM_CALL, "علي"),
        Case("دق لأمي", A, CompanionActionType.SIM_CALL),
        Case("خابر سارة", A, CompanionActionType.SIM_CALL, "سارة"),
        Case("به مامان زنگ بزن", F, CompanionActionType.SIM_CALL),
        Case("به علی زنگ بزن", F, CompanionActionType.SIM_CALL),
        // ---- WhatsApp call / video call ----
        Case("call Sara on WhatsApp", E, CompanionActionType.WHATSAPP_CALL, "Sara"),
        Case("call my brother on whatsapp", E, CompanionActionType.WHATSAPP_CALL),
        Case("اتصل بعلي على الواتساب", A, CompanionActionType.WHATSAPP_CALL),
        Case("video call Sara", E, CompanionActionType.WHATSAPP_VIDEO_CALL, "Sara"),
        Case("video call my sister on WhatsApp", E, CompanionActionType.WHATSAPP_VIDEO_CALL),
        Case("سوي فيديو ل علي", A, CompanionActionType.WHATSAPP_VIDEO_CALL),
        // ---- YouTube ----
        Case("play shape of you on youtube", E, CompanionActionType.YOUTUBE, "shape of you"),
        Case("youtube relaxing music", E, CompanionActionType.YOUTUBE, "relaxing music"),
        Case("شغللي فيروز على اليوتيوب", A, CompanionActionType.YOUTUBE, "فيروز"),
        Case("شغل أم كلثوم على اليوتيوب", A, CompanionActionType.YOUTUBE, "أم كلثوم"),
        // ---- Open apps ----
        Case("open WhatsApp", E, CompanionActionType.OPEN_APP, "WhatsApp"),
        Case("launch Spotify", E, CompanionActionType.OPEN_APP, "Spotify"),
        Case("open the camera", E, CompanionActionType.OPEN_APP, "camera"),
        Case("افتح واتساب", A, CompanionActionType.OPEN_APP, "واتساب"),
        Case("افتح تطبيق تيليجرام", A, CompanionActionType.OPEN_APP, "تيليجرام"),
        Case("واتساپ رو باز کن", F, CompanionActionType.OPEN_APP),
        // ---- Messages ----
        Case("read my messages", E, CompanionActionType.READ_MESSAGES),
        Case("check new messages", E, CompanionActionType.READ_MESSAGES),
        Case("اقرالي الرسائل", A, CompanionActionType.READ_MESSAGES),
        // ---- Time and date ----
        Case("what time is it", E, CompanionActionType.NONE),
        Case("what's the date today", E, CompanionActionType.NONE),
        Case("شكد الساعة", A, CompanionActionType.NONE),
        Case("ساعت چنده", F, CompanionActionType.NONE),
        // ---- Alarms ----
        Case("set an alarm for 7 am", E, CompanionActionType.SET_ALARM, "07:00"),
        Case("set an alarm for 6:30", E, CompanionActionType.SET_ALARM, "06:30"),
        Case("wake me up at 6:30 am", E, CompanionActionType.SET_ALARM, "06:30"),
        Case("set an alarm for 9 pm", E, CompanionActionType.SET_ALARM, "21:00"),
        Case("set an alarm at 12 am", E, CompanionActionType.SET_ALARM, "00:00"),
        Case("set an alarm at 12 pm", E, CompanionActionType.SET_ALARM, "12:00"),
        Case("set an alarm for 5:45 pm every day", E, CompanionActionType.SET_ALARM, "17:45"),
        Case("set an alarm for 8 on weekdays", E, CompanionActionType.SET_ALARM, "08:00"),
        Case("ضبط منبه الساعة 7 الصبح", A, CompanionActionType.SET_ALARM, "07:00"),
        Case("ضبط منبه الساعة ٧ مساء", A, CompanionActionType.SET_ALARM, "19:00"),
        Case("صحيني الساعة 6:30 الصبح", A, CompanionActionType.SET_ALARM, "06:30"),
        Case("زنگ ساعت ۷ صبح بذار", F, CompanionActionType.SET_ALARM, "07:00"),
        Case("یک زنگ برای ساعت ۸ شب تنظیم کن", F, CompanionActionType.SET_ALARM, "20:00"),
        Case("show my alarms", E, CompanionActionType.SHOW_ALARMS),
        // ---- Timers ----
        Case("set a timer for 10 minutes", E, CompanionActionType.SET_TIMER, "600"),
        Case("timer 5 minutes", E, CompanionActionType.SET_TIMER, "300"),
        Case("set a timer for 1 hour 30 minutes", E, CompanionActionType.SET_TIMER, "5400"),
        Case("set a timer for 45 seconds", E, CompanionActionType.SET_TIMER, "45"),
        Case("set a timer for half an hour", E, CompanionActionType.SET_TIMER, "1800"),
        Case("ضبط مؤقت 5 دقايق", A, CompanionActionType.SET_TIMER, "300"),
        Case("مؤقت ١٠ دقائق", A, CompanionActionType.SET_TIMER, "600"),
        Case("تایمر ۱۰ دقیقه بذار", F, CompanionActionType.SET_TIMER, "600"),
        // ---- Must NOT be handled on the phone: multi-step, cancelling, open questions ----
        Case("open WhatsApp and call Ali", E, null),
        Case("cancel my alarm", E, null),
        Case("turn off the 7 am alarm", E, null),
        Case("الغي المنبه", A, null),
        Case("what's the weather like tomorrow", E, null),
        Case("tell me a joke", E, null),
        Case("how do I cook rice", E, null),
        Case("set an alarm and call mom", E, null),
        Case("اتصل بعلي وبعدين افتح واتساب", A, null)
    )

    private fun matches(c: Case, turn: CompanionChatTurn?): Boolean {
        if (c.type == null) return turn == null
        if (turn == null || turn.actionType != c.type) return false
        val d = c.detail ?: return true
        return when (c.type) {
            CompanionActionType.SIM_CALL, CompanionActionType.WHATSAPP_CALL, CompanionActionType.WHATSAPP_VIDEO_CALL ->
                turn.actionRecipient.equals(d, ignoreCase = true)
            CompanionActionType.YOUTUBE, CompanionActionType.OPEN_APP -> turn.actionPayload.equals(d, ignoreCase = true)
            CompanionActionType.SET_ALARM -> JSONObject(turn.actionPayload).let { "%02d:%02d".format(it.getInt("hour"), it.getInt("minute")) == d }
            CompanionActionType.SET_TIMER -> JSONObject(turn.actionPayload).getInt("seconds").toString() == d
            else -> true
        }
    }

    @Test
    fun `core commands pass the reliability gate`() {
        val failures = corpus.filterNot { matches(it, LocalCommands.parse(it.text, it.lang)) }
        val rate = 100.0 * (corpus.size - failures.size) / corpus.size
        println("Command corpus: ${corpus.size - failures.size}/${corpus.size} passed (%.1f%%)".format(rate))
        failures.forEach { println("  FAIL [${it.lang}] \"${it.text}\" -> expected ${it.type} ${it.detail ?: ""}, got ${LocalCommands.parse(it.text, it.lang)?.let { t -> "${t.actionType} r=${t.actionRecipient} p=${t.actionPayload}" }}") }
        assertTrue("Pass rate %.1f%% is below the %.0f%% gate:\n%s".format(rate, GATE, failures.joinToString("\n") { "[${it.lang}] ${it.text}" }), rate >= GATE)
    }

    @Test
    fun `alarm and timer weekday and every-day payloads carry the days`() {
        val every = LocalCommands.parse("set an alarm for 7 am every day", E)!!
        assertTrue(JSONObject(every.actionPayload).getJSONArray("days").length() == 7)
        val weekdays = LocalCommands.parse("set an alarm for 8 on weekdays", E)!!
        assertTrue(JSONObject(weekdays.actionPayload).getJSONArray("days").length() == 5)
    }

    private companion object {
        const val GATE = 95.0
    }
}
