package com.example.ai

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Alarm and timer requests understood on the phone (English, Arabic, Persian), so they work
 * instantly and cost no AI quota. Produces the same small JSON the AI would write:
 *  alarm: {"hour":7,"minute":30,"days":["mon",...],"label":""}   timer: {"seconds":600}
 * Anything it is not sure about returns null and goes to the AI. Pure logic, unit-tested.
 */
object LocalTimeCommands {

    sealed class Parsed {
        data class Alarm(val hour: Int, val minute: Int, val days: List<String>) : Parsed()
        data class Timer(val seconds: Int) : Parsed()
        object ShowAlarms : Parsed()
    }

    private val ALL_DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    private val WEEKDAYS = listOf("mon", "tue", "wed", "thu", "fri")
    private val WEEKEND = listOf("sat", "sun")

    /** Eastern Arabic and Persian digits to ASCII, so "٧:٣٠" and "۷:۳۰" read as 7:30. */
    fun asciiDigits(s: String): String = buildString {
        for (c in s) append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                '٫', '：' -> ':'
                else -> c
            }
        )
    }

    private val ALARM_WORD = Regex("(?i)\\b(alarm|wake me(?: up)?)\\b|منبه|المنبه|منبّه|ساعة منبه|زنگ|ساعت زنگ|بیدارم کن|صحيني|صحّيني|نبهني|نبّهني")
    private val TIMER_WORD = Regex("(?i)\\btimer\\b|\\bcount ?down\\b|مؤقت|تايمر|تایمر|عداد|شمارش معکوس")
    private val SET_WORD = Regex("(?i)\\b(set|make|create|start|put|wake)\\b|ضبط|اضبط|ظبط|سوي|سوّي|حط|شغل|اعمل|بذار|بزار|بگذار|تنظیم|بزن|بذارید|صحيني|صحّيني|نبهني|نبّهني|بیدارم کن")
    private val SHOW_WORD = Regex("(?i)\\b(show|open|list)\\b.*\\balarms?\\b|وري.*منبه|اعرض.*المنبهات|افتح.*المنبهات|نشان بده.*زنگ|زنگ.*(?:ها)?.*نشان")

    private val CANCEL_WORD = Regex("(?i)\\b(cancel|delete|remove|stop|turn off|disable|snooze|change)\\b|الغي|ألغي|احذف|اطفي|أطفئ|وقف|لغو|حذف|خاموش")
    private val AM = Regex("(?i)\\b(a\\.?m\\.?|in the morning|morning)\\b|صباحا|صباحاً|الصبح|ص\\b|صبح|قبل از ظهر")
    private val PM = Regex("(?i)\\b(p\\.?m\\.?|in the evening|in the afternoon|at night|tonight|evening|afternoon)\\b|مساء|مساءً|العصر|بعد الظهر|بالليل|الليل|م\\b|عصر|شب|بعد از ظهر")

    fun parse(raw: String): Parsed? {
        val t = asciiDigits(raw.trim().trimEnd('.', '!', '؟', '?')).lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (t.isEmpty() || t.length > 90) return null
        if (SHOW_WORD.containsMatchIn(t)) return Parsed.ShowAlarms
        if (TIMER_WORD.containsMatchIn(t)) return timer(t)
        // Cancelling or changing an alarm is a job for the AI (or the Clock app), never a new alarm.
        if (CANCEL_WORD.containsMatchIn(t)) return null
        if (ALARM_WORD.containsMatchIn(t) && SET_WORD.containsMatchIn(t)) return alarm(t)
        return null
    }

    // ---- Timer: "set a timer for 10 minutes", "مؤقت 5 دقايق", "تایمر ۱۰ دقیقه" ----------------------

    private val HOUR = "(?:hours?|hrs?|ساعة|ساعات|ساعه|ساعت)"
    private val MINUTE = "(?:minutes?|mins?|دقيقة|دقيقه|دقائق|دقايق|دقيقتين|دقیقه|دقیقه‌ی)"
    private val SECOND = "(?:seconds?|secs?|ثانية|ثانيه|ثواني|ثوان|ثانیه)"

    private fun timer(t: String): Parsed? {
        var seconds = 0
        var found = false
        Regex("(\\d+(?:\\.\\d+)?)\\s*$HOUR").find(t)?.let { seconds += (it.groupValues[1].toDouble() * 3600).toInt(); found = true }
        Regex("(\\d+)\\s*$MINUTE").find(t)?.let { seconds += it.groupValues[1].toInt() * 60; found = true }
        Regex("(\\d+)\\s*$SECOND").find(t)?.let { seconds += it.groupValues[1].toInt(); found = true }
        if (!found) {
            when {
                Regex("(?i)half an hour|نص ساعة|نصف ساعة|نیم ساعت").containsMatchIn(t) -> { seconds = 1800; found = true }
                Regex("(?i)an hour|one hour|ساعة واحدة|یک ساعت").containsMatchIn(t) -> { seconds = 3600; found = true }
                Regex("دقيقتين|دو دقیقه").containsMatchIn(t) -> { seconds = 120; found = true }
            }
        }
        return if (found && seconds in 1..86_400) Parsed.Timer(seconds) else null
    }

    // ---- Alarm: "wake me up at 6:30", "set an alarm for 7 pm every day", "منبه الساعة 7 الصبح" ----------

    private val CLOCK = Regex("(\\d{1,2})(?:[:.](\\d{2}))?")

    private fun alarm(t: String): Parsed? {
        // Pick the clock time: after "at / for / الساعة / ساعت", else the first number.
        val anchor = Regex("(?:\\bat\\b|\\bfor\\b|\\bto\\b|الساعة|الساعه|ساعة|ساعت|ب)\\s*(\\d{1,2}(?:[:.]\\d{2})?)").find(t)
        val m = (if (anchor != null) CLOCK.find(anchor.groupValues[1]) else null) ?: CLOCK.find(t) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (minute !in 0..59 || hour !in 0..24) return null
        val pm = PM.containsMatchIn(t)
        val am = AM.containsMatchIn(t)
        when {
            pm && hour in 1..11 -> hour += 12
            am && hour == 12 -> hour = 0
            // "12 pm" stays 12.
        }
        if (hour == 24) hour = 0
        if (hour !in 0..23) return null
        return Parsed.Alarm(hour, minute, days(t))
    }

    private fun days(t: String): List<String> = when {
        Regex("(?i)every ?day|daily|كل يوم|يوميا|يومياً|هر روز|روزانه").containsMatchIn(t) -> ALL_DAYS
        Regex("(?i)week ?days|على مدار الأسبوع|ايام الدوام|أيام الدوام|روزهای کاری|روزهای هفته").containsMatchIn(t) -> WEEKDAYS
        Regex("(?i)week ?ends?|نهاية الأسبوع|عطلة الأسبوع|آخر هفته|آخر هفته|آخر الأسبوع").containsMatchIn(t) -> WEEKEND
        else -> emptyList()
    }

    /** The action payload the executor reads. */
    fun payload(p: Parsed): String = when (p) {
        is Parsed.Alarm -> JSONObject().put("hour", p.hour).put("minute", p.minute).put("days", JSONArray(p.days)).put("label", "").toString()
        is Parsed.Timer -> JSONObject().put("seconds", p.seconds).toString()
        Parsed.ShowAlarms -> ""
    }
}
