package com.example.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Pure ring of trail lines (unit-tested). */
class TrailBuffer(private val capacity: Int = 200) {
    private val lines = ArrayDeque<String>()
    @Synchronized fun add(line: String) {
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
    }
    @Synchronized fun tail(n: Int): List<String> = lines.toList().takeLast(n)
}

/**
 * A short rolling record of what the app was doing: screens, model loads, downloads, memory.
 * Kept in memory and appended to a small file so it survives a crash or a kill.
 * Events only: never what the user said, typed, or any contact or message.
 */
object AppTrail {
    const val START_MARK = "---- app start"
    private const val MAX_FILE_BYTES = 64 * 1024
    private val SUMMARY_TAGS = setOf("SCREEN", "LAB", "GEMMA", "OFFLINE", "LIVE")

    private val buffer = TrailBuffer(300)
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "trail") }
    @Volatile private var dir: File? = null
    @Volatile private var appContext: Context? = null
    private val clock = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    /** Pure, unit-tested: the runs of lines separated by start marks (oldest first). */
    fun splitRuns(lines: List<String>): List<List<String>> {
        val runs = ArrayList<MutableList<String>>()
        for (l in lines) {
            if (l.contains(START_MARK)) runs += mutableListOf(l) else runs.lastOrNull()?.add(l)
        }
        return runs
    }

    @Synchronized fun init(context: Context) {
        if (dir != null) return
        appContext = context.applicationContext
        val d = File(context.filesDir, "diag").apply { mkdirs() }
        dir = d
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val code = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() } ?: 0L
        raw("$START_MARK v${info?.versionName} ($code) ----")
    }

    fun add(tag: String, text: String) {
        raw("${clock.get()!!.format(Date())} $tag ${text.take(160)}")
        if (tag in SUMMARY_TAGS && Build.VERSION.SDK_INT >= 30) {
            val am = appContext?.getSystemService(ActivityManager::class.java) ?: return
            runCatching {
                val b = "$tag $text".toByteArray()
                am.setProcessStateSummary(b.copyOf(minOf(b.size, 128)))
            }
        }
    }

    private fun raw(line: String) {
        buffer.add(line)
        val d = dir ?: return
        writer.execute {
            runCatching {
                val f = File(d, "trail.log")
                if (f.length() > MAX_FILE_BYTES) {
                    val prev = File(d, "trail.prev.log")
                    prev.delete()
                    f.renameTo(prev)
                }
                File(d, "trail.log").appendText(line + "\n")
            }
        }
    }

    fun tail(n: Int = 60): List<String> = buffer.tail(n)

    /** Lines of the run before the current one (reads the files, so call off the main thread). */
    fun previousRunTail(n: Int = 40): List<String> {
        val d = dir ?: return emptyList()
        val lines = runCatching {
            File(d, "trail.prev.log").takeIf { it.exists() }?.readLines().orEmpty() +
                File(d, "trail.log").takeIf { it.exists() }?.readLines().orEmpty()
        }.getOrDefault(emptyList())
        val runs = splitRuns(lines)
        return if (runs.size >= 2) runs[runs.size - 2].takeLast(n) else emptyList()
    }
}
