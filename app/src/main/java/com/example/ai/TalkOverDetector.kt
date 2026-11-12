package com.example.ai

/**
 * Notices the user starting to talk while she is thinking or speaking, so she can stop and listen
 * instead of making them wait. Pure logic, unit-tested; fed one 30 ms frame level at a time.
 *
 * While she speaks, some of her own voice reaches the microphone even after echo cancelling. The
 * detector learns that level as it goes and only reacts to sound well above it, held for
 * [holdFrames] frames, so her own voice does not count as the user. While she is only thinking
 * there is no voice of hers to subtract, so normal speech is enough.
 */
class TalkOverDetector(
    private val holdFrames: Int = 10,          // 300 ms of sustained voice
    private val overEchoRatio: Double = 4.0,
    private val minWhileSpeaking: Double = 1_200.0,
    // Higher than normal speech detection: a false alarm here drops the answer she is preparing.
    private val minWhileThinking: Double = 700.0,
    private val roomRatio: Double = 4.0
) {
    enum class Mode { THINKING, SPEAKING }

    private var echoLevel = -1.0
    private var roomLevel = -1.0
    private var run = 0
    private var runStart = -1L
    private var mode = Mode.THINKING

    /** Her voice level after echo cancelling, as last measured (for the voice log). */
    val herVoiceLevel: Double get() = echoLevel.coerceAtLeast(0.0)

    /** Starts a new watch (for example when she starts speaking). */
    fun reset(newMode: Mode) {
        if (newMode != mode) { run = 0; runStart = -1L }
        mode = newMode
        if (newMode == Mode.SPEAKING) echoLevel = -1.0
    }

    /**
     * @return the sample at which the user started talking, once they have talked long enough;
     * otherwise -1.
     */
    fun feed(rms: Double, frameStartSample: Long): Long {
        val threshold = when (mode) {
            Mode.SPEAKING -> {
                if (echoLevel < 0) echoLevel = rms
                maxOf(echoLevel * overEchoRatio, minWhileSpeaking)
            }
            Mode.THINKING -> {
                if (roomLevel < 0) roomLevel = rms
                maxOf(roomLevel * roomRatio, minWhileThinking)
            }
        }
        val loud = rms > threshold
        if (loud) {
            if (run == 0) runStart = frameStartSample
            run++
            if (run >= holdFrames) {
                val from = runStart
                run = 0
                runStart = -1L
                return from
            }
        } else {
            run = 0
            runStart = -1L
            // Learn the background only from frames that are not the user.
            when (mode) {
                Mode.SPEAKING -> echoLevel = echoLevel * 0.9 + rms * 0.1
                Mode.THINKING -> roomLevel = roomLevel * 0.95 + rms * 0.05
            }
        }
        return -1L
    }
}
