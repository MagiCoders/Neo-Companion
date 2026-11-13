package com.example.diag

import android.os.Process
import android.os.SystemClock
import java.io.File

/** Every 30 s: this process's CPU use; when it is high while the app is not on screen, names the busiest threads in the trail. */
object CpuWatch {
    private var lastCpuMs = 0L
    private var lastWallMs = 0L
    private val lastTicks = HashMap<Int, Long>()

    /** Pure: one /proc/<pid>/task/<tid>/stat line into (thread name, utime + stime ticks). */
    fun parseStat(line: String): Pair<String, Long>? {
        val open = line.indexOf('(')
        val close = line.lastIndexOf(')')
        if (open < 0 || close < open) return null
        val rest = line.substring(close + 1).trim().split(' ')
        // After ")": state is index 0, so utime is field 14 (index 11) and stime field 15 (index 12).
        val utime = rest.getOrNull(11)?.toLongOrNull() ?: return null
        val stime = rest.getOrNull(12)?.toLongOrNull() ?: return null
        return line.substring(open + 1, close) to (utime + stime)
    }

    /** Percent of one core since the last sample (0 on the first call). */
    @Synchronized fun sample(onScreen: Boolean): Int {
        val cpu = Process.getElapsedCpuTime()
        val wall = SystemClock.elapsedRealtime()
        val percent = if (lastWallMs == 0L) 0 else ((cpu - lastCpuMs) * 100 / (wall - lastWallMs).coerceAtLeast(1)).toInt()
        lastCpuMs = cpu; lastWallMs = wall
        if (!onScreen && percent >= 8) {
            val busy = ArrayList<Pair<String, Long>>()
            File("/proc/self/task").listFiles()?.forEach { dir ->
                runCatching {
                    val tid = dir.name.toInt()
                    val (name, ticks) = parseStat(File(dir, "stat").readText()) ?: return@runCatching
                    val delta = ticks - (lastTicks[tid] ?: ticks)
                    lastTicks[tid] = ticks
                    if (delta > 0) busy += name to delta
                }
            }
            AppTrail.add("CPU", "busy in background $percent%: " + busy.sortedByDescending { it.second }.take(4).joinToString { "${it.first} ${it.second}" })
        } else if (onScreen) lastTicks.clear()
        return percent
    }
}
