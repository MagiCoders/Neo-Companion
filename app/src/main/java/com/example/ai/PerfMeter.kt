package com.example.ai

import android.os.Debug
import android.os.Process
import android.os.SystemClock

/**
 * The numbers behind "Mochi uses too much CPU": the app's share of the phone's processor, its
 * memory, how many 3D frames it draws a second, and how much of the time the wake listener is
 * actually decoding. Shown in Settings → Diagnostics so fixes are measured, not guessed.
 */
object PerfMeter {

    data class Reading(
        /** Share of the whole phone's processor, 0–100 (all cores together). */
        val cpuPercent: Double,
        val memoryMb: Int,
        val framesPerSecond: Double,
        /** Share of microphone audio the wake listener decoded, 0–100. */
        val wakeDecodedPercent: Double
    ) {
        fun summary(): String =
            "CPU %.1f%% · memory %d MB · 3D %.0f fps · wake listener decoding %.0f%% of the time"
                .format(cpuPercent, memoryMb, framesPerSecond, wakeDecodedPercent)
    }

    @Volatile var wakeDecodedShare = 0.0
    @Volatile private var frames = 0L

    private var lastWallMs = 0L
    private var lastCpuMs = 0L
    private var lastFrames = 0L

    /** Called once per drawn 3D frame. */
    fun frameDrawn() { frames++ }

    private val history = ArrayDeque<Reading>()

    /**
     * Takes one reading into a one-minute history (called every 5 s by the voice service), so the
     * Diagnostics screen can show what happened on the home screen just before it was opened.
     */
    @Synchronized
    fun sample() {
        val r = read()
        if (r.memoryMb == 0) return
        history.addLast(r)
        while (history.size > 12) history.removeFirst()
    }

    /** Average and peak over the last minute, or null before the first two readings. */
    @Synchronized
    fun lastMinute(): String? {
        val list = history.drop(1) // the very first reading has no time span
        if (list.isEmpty()) return null
        val avg = list.map { it.cpuPercent }.average()
        val peak = list.maxOf { it.cpuPercent }
        val last = list.last()
        return "Last minute: CPU average %.1f%%, peak %.1f%% · memory %d MB · 3D %.0f fps · wake listener decoding %.0f%% of the time"
            .format(avg, peak, last.memoryMb, list.map { it.framesPerSecond }.average(), last.wakeDecodedPercent)
    }

    /** Compares with the previous call. */
    @Synchronized
    fun read(): Reading {
        val wall = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        val f = frames
        val dWall = (wall - lastWallMs).coerceAtLeast(1L)
        val first = lastWallMs == 0L
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cpuPct = if (first) 0.0 else (100.0 * (cpu - lastCpuMs) / dWall / cores).coerceIn(0.0, 100.0)
        val fps = if (first) 0.0 else 1000.0 * (f - lastFrames) / dWall
        lastWallMs = wall; lastCpuMs = cpu; lastFrames = f
        val mem = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss / 1024
        return Reading(cpuPct, mem, fps, wakeDecodedShare * 100)
    }
}
