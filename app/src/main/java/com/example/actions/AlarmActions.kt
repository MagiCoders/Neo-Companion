package com.example.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.example.ai.VoiceLog
import android.provider.AlarmClock
import android.provider.CalendarContract
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar

/**
 * Alarms and timers through the phone's own Clock app (silently, no extra screen), from the
 * small JSON the AI writes in actionPayload:
 *  alarm: {"hour":21,"minute":0,"days":["mon","fri"],"date":"2026-11-03","label":"Gym"}
 *  timer: {"seconds":600,"label":"Tea"}
 */
class AlarmActions(private val context: Context) {

    data class AlarmSpec(val hour: Int, val minute: Int, val days: List<DayOfWeek>, val date: LocalDate?, val label: String)

    fun setAlarm(payload: String, now: LocalDateTime = LocalDateTime.now()): ActionOutcome {
        val spec = parseAlarm(payload) ?: return ActionOutcome.Failed("What time should the alarm be?")
        val time = "%02d:%02d".format(spec.hour, spec.minute)
        // A specific day further away than the alarm's next ring can't be a Clock alarm (the Clock
        // app only knows times and weekdays): it becomes a calendar event with a reminder.
        if (spec.date != null && spec.days.isEmpty() && spec.date != nextRingDate(spec.hour, spec.minute, now)) {
            return calendarReminder(spec)
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, spec.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, spec.minute)
            if (spec.label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, spec.label)
            if (spec.days.isNotEmpty()) putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(spec.days.map(::calendarDay)))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        val repeat = describeDays(spec.days)
        return start(intent, "I couldn't reach your Clock app.") {
            "Alarm set for $time" + (if (repeat.isNotEmpty()) ", $repeat" else "") + (if (spec.label.isNotBlank()) " — ${spec.label}" else "") + "."
        }
    }

    fun setTimer(payload: String): ActionOutcome {
        val json = runCatching { JSONObject(payload) }.getOrNull()
        val seconds = json?.optInt("seconds", 0) ?: 0
        if (seconds !in 1..86_400) return ActionOutcome.Failed("How long should the timer be?")
        val label = json?.optString("label").orEmpty().take(60)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        return start(intent, "I couldn't reach your Clock app.") { "Timer set for ${describeDuration(seconds)}." }
    }

    fun showAlarms(): ActionOutcome =
        start(Intent(AlarmClock.ACTION_SHOW_ALARMS), "I couldn't open your alarms.") { "Here are your alarms." }

    private fun calendarReminder(spec: AlarmSpec): ActionOutcome {
        val begin = spec.date!!.atTime(spec.hour, spec.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 15 * 60_000L)
            putExtra(CalendarContract.Events.TITLE, spec.label.ifBlank { "Reminder" })
            putExtra(CalendarContract.Events.HAS_ALARM, 1)
        }
        return start(intent, "I couldn't open your calendar.") {
            "Your Clock app can only set alarms by time and weekday, so I've prepared a calendar reminder for ${spec.date} at %02d:%02d — tap Save.".format(spec.hour, spec.minute)
        }
    }

    /**
     * Starts the Clock app's intent only when some app on the phone actually handles it, and logs
     * which one (so "alarm doesn't work" can be told apart from "no Clock app answers").
     */
    private inline fun start(intent: Intent, failure: String, success: () -> String): ActionOutcome {
        val handler = try { intent.resolveActivity(context.packageManager) } catch (_: Throwable) { null }
        if (handler == null) {
            VoiceLog.add(VoiceLog.Kind.ERROR, "No app on this phone handles ${intent.action}")
            return ActionOutcome.Failed("$failure No Clock app on this phone answers that request.")
        }
        VoiceLog.add(VoiceLog.Kind.SPOKE, "${intent.action?.substringAfterLast('.')} handled by ${handler.packageName}")
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionOutcome.Done(success(), speak = true)
        } catch (e: ActivityNotFoundException) {
            VoiceLog.add(VoiceLog.Kind.ERROR, "Clock app refused: ActivityNotFound")
            ActionOutcome.Failed(failure)
        } catch (e: SecurityException) {
            VoiceLog.add(VoiceLog.Kind.ERROR, "Clock app refused: ${e.message?.take(80)}")
            ActionOutcome.Failed("$failure The Clock app needs permission to set alarms from this app.")
        }
    }

    companion object {
        private val DAY_NAMES = mapOf(
            "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY
        )

        fun parseAlarm(payload: String): AlarmSpec? {
            val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
            val hour = json.optInt("hour", -1)
            val minute = json.optInt("minute", 0)
            if (hour !in 0..23 || minute !in 0..59) return null
            val days = json.optJSONArray("days")?.let { arr ->
                (0 until arr.length()).mapNotNull { DAY_NAMES[arr.optString(it).trim().lowercase().take(3)] }
            }.orEmpty().distinct().sorted()
            val date = json.optString("date").takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            return AlarmSpec(hour, minute, days, date, json.optString("label").trim().take(60))
        }

        /** The day an alarm at hour:minute would ring next (today if still ahead, else tomorrow). */
        fun nextRingDate(hour: Int, minute: Int, now: LocalDateTime): LocalDate =
            if (now.toLocalTime() < LocalTime.of(hour, minute)) now.toLocalDate() else now.toLocalDate().plusDays(1)

        private fun calendarDay(d: DayOfWeek): Int = when (d) {
            DayOfWeek.SUNDAY -> Calendar.SUNDAY
            DayOfWeek.MONDAY -> Calendar.MONDAY
            DayOfWeek.TUESDAY -> Calendar.TUESDAY
            DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
            DayOfWeek.THURSDAY -> Calendar.THURSDAY
            DayOfWeek.FRIDAY -> Calendar.FRIDAY
            DayOfWeek.SATURDAY -> Calendar.SATURDAY
        }

        fun describeDays(days: List<DayOfWeek>): String = when {
            days.isEmpty() -> ""
            days.size == 7 -> "every day"
            days == listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> "on weekdays"
            days == listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "on weekends"
            else -> "every " + days.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::titlecase) }
        }

        fun describeDuration(seconds: Int): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return listOfNotNull(
                if (h > 0) "$h hour${if (h > 1) "s" else ""}" else null,
                if (m > 0) "$m minute${if (m > 1) "s" else ""}" else null,
                if (s > 0) "$s second${if (s > 1) "s" else ""}" else null
            ).joinToString(" ")
        }
    }
}
