package com.example.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Why Android last ended the app's process (crash, engine crash, out of memory, frozen…). */
object ExitReasons {

    fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "app crash (Kotlin/Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "engine crash (native code)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "killed: phone out of memory"
        ApplicationExitInfo.REASON_ANR -> "frozen (not responding)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed: used too much"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by the system (signal)"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "closed by you"
        ApplicationExitInfo.REASON_USER_STOPPED -> "force-stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "a system part it needed stopped"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "a permission changed"
        ApplicationExitInfo.REASON_EXIT_SELF -> "closed itself"
        ApplicationExitInfo.REASON_FREEZER -> "killed while frozen"
        else -> "other ($reason)"
    }

    /** Readable lines from a native crash record (protobuf bytes) without a protobuf library. */
    fun readableFrames(bytes: ByteArray, max: Int = 40): List<String> {
        val keys = listOf(".so", "SIG", "signal", "abort", "Abort", "fault", "sherpa", "onnx", "litert", "vosk", "filament")
        val out = LinkedHashSet<String>()
        val run = StringBuilder()
        fun flush() {
            if (run.length >= 6 && keys.any { run.contains(it) }) out += run.toString()
            run.setLength(0)
        }
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) run.append(c.toChar()) else flush()
        }
        flush()
        return out.take(max)
    }

    private fun importanceName(i: Int) = Importance.name(i)

    fun report(context: Context, count: Int = 3): String {
        if (Build.VERSION.SDK_INT < 30) return "Exit reasons need Android 11 or newer."
        val am = context.getSystemService(ActivityManager::class.java)
        val infos = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, count) }.getOrDefault(emptyList())
        if (infos.isEmpty()) return "Android has no record yet."
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return infos.joinToString("\n\n") { i ->
            buildString {
                appendLine("${fmt.format(Date(i.timestamp))}: ${reasonName(i.reason)}")
                if (!i.description.isNullOrBlank()) appendLine("  ${i.description}")
                appendLine("  app was ${importanceName(i.importance)} · memory ${i.pss / 1024} MB (pss), ${i.rss / 1024} MB (rss)")
                i.processStateSummary?.let { appendLine("  Doing: " + String(it)) }
                if (Build.VERSION.SDK_INT >= 31 &&
                    (i.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || i.reason == ApplicationExitInfo.REASON_ANR)) {
                    runCatching {
                        i.traceInputStream?.use { s ->
                            val data = s.readNBytes(512 * 1024)
                            val lines = if (i.reason == ApplicationExitInfo.REASON_ANR) String(data).lines().take(30)
                            else readableFrames(data)
                            lines.forEach { appendLine("  $it") }
                        }
                    }
                }
            }.trimEnd()
        }
    }
}
