package com.example.ai

import com.example.data.CompanionActionType
import com.example.data.CompanionChatTurn
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Everyday commands understood on the phone itself, in English, Arabic and Persian: no AI call,
 * so they are instant and cost no AI quota. Anything not clearly one of these goes to the AI.
 * Only plain, single-step requests are matched ("open X", "call X", "play X on YouTube"); a
 * request with "and / then" in it is left to the AI. Pure logic, unit-tested.
 */
object LocalCommands {

    private const val MAX_NAME_WORDS = 4

    /** "and", "then" in the supported languages: multi-step requests go to the AI. */
    private val MULTI_STEP = Regex(
        "(?i)\\b(and|then|after that)\\b|\\sو\\s|ثم|بعدين|بعدها|بعد ذلك|بعدش|سپس|بعد از اون|" +
            // Arabic "and" is glued to the next verb ("واتصل"); words like "واتساب" or "وليد" are fine.
            "\\s+و(?:اتصل|ارسل|أرسل|ابعث|دز|شغل|افتح|كلم|سوي|دق|اقرا|اقرأ|اكتب|خابر|حط)|" +
            "\\s+و(?:زنگ|بفرست|باز|پخش|بخون)"
    )

    private val YOUTUBE_WORD = "(?:youtube|you tube|يوتيوب|اليوتيوب|یوتیوب|یوتوب)"

    private data class Rule(val regex: Regex, val build: (MatchResult, String) -> CompanionChatTurn?)

    private val rules: List<Rule> = listOf(
        // ---- YouTube: "play X on YouTube", "شغللي X على اليوتيوب", "X رو تو یوتیوب پخش کن" ----
        Rule(Regex("(?i)^(?:please\\s+)?(?:open youtube and\\s+)?(?:play|put on|find)\\s+(.+?)\\s+(?:on|in|from)\\s+$YOUTUBE_WORD$")) { m, l -> youtube(m.groupValues[1], l) },
        Rule(Regex("(?i)^(?:youtube|you tube)\\s+(.+)$")) { m, l -> youtube(m.groupValues[1], l) },
        Rule(Regex("^(?:شغل|شغلي|شغللي|شغّل|شغلّي|حط|حطلي)\\s+(.+?)\\s+(?:على|ب|في|من)\\s*$YOUTUBE_WORD$")) { m, l -> youtube(m.groupValues[1], l) },
        Rule(Regex("^(.+?)\\s+(?:رو|را)?\\s*(?:تو|توی|در|از)\\s+$YOUTUBE_WORD\\s+(?:پخش کن|بذار|بزار|پلی کن)$")) { m, l -> youtube(m.groupValues[1], l) },
        // ---- Video call ----
        Rule(Regex("(?i)^(?:please\\s+)?(?:video\\s?call|facetime)\\s+(.+?)(?:\\s+on\\s+whats\\s?app)?$")) { m, l -> person(CompanionActionType.WHATSAPP_VIDEO_CALL, m.groupValues[1], l) },
        Rule(Regex("^(?:سوي|سويلي|اعمل|دق)?\\s*(?:مكالمة\\s+)?(?:فيديو|ڤيديو)\\s+(?:ل|على|مع|ب)\\s*(.+?)(?:\\s+(?:على|ب)\\s*(?:ال)?واتساب)?$")) { m, l -> person(CompanionActionType.WHATSAPP_VIDEO_CALL, m.groupValues[1], l) },
        Rule(Regex("^(?:به\\s+)?(.+?)\\s+(?:رو|را)?\\s*تماس تصویری\\s+(?:بگیر|بزن)$")) { m, l -> person(CompanionActionType.WHATSAPP_VIDEO_CALL, m.groupValues[1], l) },
        // ---- Calls: "call X (on WhatsApp)", "اتصل بـ X (على الواتساب)", "به X زنگ بزن" ----
        Rule(Regex("(?i)^(?:please\\s+)?(?:call|phone|ring|dial)\\s+(.+?)\\s+on\\s+whats\\s?app$")) { m, l -> person(CompanionActionType.WHATSAPP_CALL, m.groupValues[1], l) },
        Rule(Regex("(?i)^(?:please\\s+)?(?:call|phone|ring|dial)\\s+(.+)$")) { m, l -> person(CompanionActionType.SIM_CALL, m.groupValues[1], l) },
        Rule(Regex("^(?:اتصل|اتصلي|دق|دقلي|خابر|كلم|اتصال)\\s*(?:ب|على|ل|الى|إلى)?\\s*(.+?)\\s+(?:على|ب|من)\\s*(?:ال)?(?:واتساب|واتس اب|واتس)$")) { m, l -> person(CompanionActionType.WHATSAPP_CALL, m.groupValues[1], l) },
        Rule(Regex("^(?:اتصل|اتصلي|دق|دقلي|خابر|اتصال)\\s*(?:ب|على|ل|الى|إلى)?\\s*(.+)$")) { m, l -> person(CompanionActionType.SIM_CALL, m.groupValues[1], l) },
        Rule(Regex("^(?:به\\s+)?(.+?)\\s+(?:رو|را)?\\s*(?:تو|با|از)\\s+واتس[‌ ]?اپ\\s+زنگ بزن$")) { m, l -> person(CompanionActionType.WHATSAPP_CALL, m.groupValues[1], l) },
        Rule(Regex("^(?:به\\s+)(.+?)\\s+زنگ بزن$")) { m, l -> person(CompanionActionType.SIM_CALL, m.groupValues[1], l) },
        // ---- Read messages ----
        Rule(Regex("(?i)^(?:please\\s+)?(?:read|check)\\s+(?:me\\s+)?(?:my\\s+)?(?:new\\s+|unread\\s+)?messages$")) { _, l -> simple(CompanionActionType.READ_MESSAGES, l) },
        Rule(Regex("^(?:اقرا|اقرأ|اقرالي|اقرألي|اقريلي)\\s+(?:ال)?(?:رسائل|رسايل|مسجات|رسائلي|رسايلي)(?:\\s+(?:الجديدة|الجديده))?$")) { _, l -> simple(CompanionActionType.READ_MESSAGES, l) },
        Rule(Regex("^پیام(?:‌| )?(?:ها|های)(?:م|ی جدید)?\\s+(?:رو|را)\\s+(?:بخون|بخوان)$")) { _, l -> simple(CompanionActionType.READ_MESSAGES, l) },
        // ---- Open an app: "open X", "افتح X", "X رو باز کن" ----
        Rule(Regex("(?i)^(?:please\\s+)?(?:open|launch|start)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$")) { m, l -> app(m.groupValues[1], l) },
        Rule(Regex("^(?:افتح|افتحي|افتحلي)\\s+(?:تطبيق\\s+)?(.+)$")) { m, l -> app(m.groupValues[1], l) },
        Rule(Regex("^(?:برنامه\\s+)?(.+?)\\s+(?:رو|را)\\s+باز کن$")) { m, l -> app(m.groupValues[1], l) },
        // ---- Time / date ----
        Rule(Regex("(?i)^(?:what(?:'s| is) the time|what time is it|time please)\\??$")) { _, l -> time(l, date = false) },
        Rule(Regex("(?i)^(?:what(?:'s| is) (?:the )?date(?: today)?|what day is (?:it|today))\\??$")) { _, l -> time(l, date = true) },
        Rule(Regex("^(?:شكد|كم|قديش|شقد)\\s+(?:ال)?ساعة(?:\\s+هسه|\\s+الحين|\\s+الآن)?[؟?]?$")) { _, l -> time(l, date = false) },
        Rule(Regex("^ساعت\\s+(?:چنده|چند است|چنده الان)[؟?]?$")) { _, l -> time(l, date = false) }
    )

    /** A ready turn (with its action) for a plain everyday command, or null to ask the AI. */
    fun parse(text: String, languageTag: String): CompanionChatTurn? {
        val t = text.trim().trimEnd('.', '!', '؟', '?').replace(Regex("\\s+"), " ")
        if (t.isEmpty() || t.length > 120) return null
        for (rule in rules) {
            val m = rule.regex.matchEntire(t) ?: continue
            // Multi-step requests ("open WhatsApp and call Ali") need the AI to plan them.
            if (MULTI_STEP.containsMatchIn(t) && !t.lowercase(Locale.ROOT).startsWith("open youtube and")) return null
            return rule.build(m, languageTag)
        }
        return null
    }

    // ---- Builders --------------------------------------------------------------------------------

    private fun turn(type: CompanionActionType, message: String, lang: String, recipient: String = "", payload: String = "") =
        CompanionChatTurn(
            id = UUID.randomUUID().toString(),
            senderIsUser = false,
            message = message,
            spokenConfirmation = message,
            detectedLanguage = lang,
            actionType = type,
            actionRecipient = recipient,
            actionPayload = payload
        )

    private fun cleanName(raw: String): String? {
        val name = raw.trim().trim('"', '\'', '«', '»').removePrefix("ال ").trim()
            .replace(Regex("(?i)^(my|to|the)\\s+"), "").trim()
        val words = name.split(" ").filter { it.isNotEmpty() }
        return if (words.isEmpty() || words.size > MAX_NAME_WORDS) null else name
    }

    private fun person(type: CompanionActionType, raw: String, lang: String): CompanionChatTurn? {
        val name = cleanName(raw) ?: return null
        val msg = when (type) {
            CompanionActionType.WHATSAPP_VIDEO_CALL -> say(lang, "Video calling $name on WhatsApp.", "راح أسوي مكالمة فيديو ويا $name.", "دارم با $name تماس تصویری می‌گیرم.")
            CompanionActionType.WHATSAPP_CALL -> say(lang, "Calling $name on WhatsApp.", "راح أتصل بـ $name على الواتساب.", "دارم با واتس‌اپ به $name زنگ می‌زنم.")
            else -> say(lang, "Calling $name.", "راح أتصل بـ $name.", "دارم به $name زنگ می‌زنم.")
        }
        return turn(type, msg, lang, recipient = name)
    }

    private fun youtube(raw: String, lang: String): CompanionChatTurn? {
        val query = raw.trim().takeIf { it.isNotEmpty() && it.length <= 100 } ?: return null
        return turn(CompanionActionType.YOUTUBE, say(lang, "Looking for \"$query\" on YouTube.", "دا أدور على \"$query\" باليوتيوب.", "دارم \"$query\" رو تو یوتیوب پیدا می‌کنم."), lang, payload = query)
    }

    private fun app(raw: String, lang: String): CompanionChatTurn? {
        val name = cleanName(raw) ?: return null
        if (name.split(" ").size > 3) return null
        return turn(CompanionActionType.OPEN_APP, say(lang, "Opening $name.", "دا أفتح $name.", "دارم $name رو باز می‌کنم."), lang, payload = name)
    }

    private fun simple(type: CompanionActionType, lang: String) =
        turn(type, say(lang, "Let me check your messages.", "خليني أشوف رسائلك.", "بذار پیام‌هات رو ببینم."), lang)

    private fun time(lang: String, date: Boolean): CompanionChatTurn {
        val locale = Locale.forLanguageTag(lang)
        val now = Date()
        val text = if (date) DateFormat.getDateInstance(DateFormat.FULL, locale).format(now)
        else DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(now)
        val msg = if (date) say(lang, "Today is $text.", "اليوم $text.", "امروز $text است.")
        else say(lang, "It's $text.", "الساعة هسه $text.", "ساعت $text است.")
        return turn(CompanionActionType.NONE, msg, lang)
    }

    private fun say(lang: String, en: String, ar: String, fa: String) = when (lang.substringBefore('-')) {
        "ar" -> ar
        "fa" -> fa
        else -> en
    }

    // ---- Picking from a list ("the second one", "الثاني", "دومی") ---------------------------------

    private val ORDINALS: List<List<String>> = listOf(
        listOf("1", "one", "first", "the first", "first one", "number one", "الاول", "الأول", "اول", "أول", "واحد", "اولی", "اولين", "یک", "یکی", "يك"),
        listOf("2", "two", "second", "the second", "second one", "number two", "الثاني", "ثاني", "الثانية", "اثنين", "ثنين", "دو", "دومی", "دوم", "دومين"),
        listOf("3", "three", "third", "the third", "third one", "number three", "الثالث", "ثالث", "ثلاثة", "ثلاث", "تلاتة", "سه", "سومی", "سوم"),
        listOf("4", "four", "fourth", "the fourth", "fourth one", "number four", "الرابع", "رابع", "اربعة", "أربعة", "چهار", "چهارمی", "چهارم"),
        listOf("5", "five", "fifth", "the fifth", "fifth one", "number five", "الخامس", "خامس", "خمسة", "پنج", "پنجمی", "پنجم")
    )
    private val NONE_OF_THEM = Regex("(?i)\\b(none|cancel|never ?mind|no)\\b|ولا واحد|لا شي|خلاص|هیچکدوم|هیچ کدوم|ولش")

    /** 0-based index chosen from a list of [count] options, -1 for "none of them", null if not a choice. */
    fun parseChoice(text: String, count: Int): Int? {
        val t = text.lowercase(Locale.ROOT).trim().trimEnd('.', '!', '؟', '?')
            .removePrefix("play ").removePrefix("the ").removePrefix("شغل ").removePrefix("number ").trim()
        if (NONE_OF_THEM.containsMatchIn(t) && t.split(" ").size <= 3) return -1
        val words = t.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (words.size > 4) return null
        val meaningful = words.filterNot { it in setOf("one", "please", "the", "number", "اللي", "يا", "رو", "را", "اون") }
        val stripped = meaningful.joinToString(" ")
        // Whole answer first ("the second one" → "second"), then any single word ("play the third").
        ORDINALS.forEachIndexed { i, names -> if (i < count && (stripped in names || t in names)) return i }
        ORDINALS.forEachIndexed { i, names -> if (i < count && meaningful.any { it in names }) return i }
        if (words.any { it in setOf("last", "الاخير", "الأخير", "آخری", "آخر") }) return count - 1
        return null
    }
}
