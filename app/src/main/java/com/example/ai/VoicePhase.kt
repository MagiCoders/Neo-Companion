package com.example.ai

/**
 * What the voice system is doing right now, in one word. The service derives it from its flags,
 * shows it on screen, and a watchdog uses it to notice (and undo) anything stuck.
 */
enum class VoicePhase(val label: String) {
    OFF("Voice off"),
    IDLE("Ready"),
    WAKE("Waiting for her name"),
    LISTENING("Listening"),
    THINKING("Thinking"),
    SPEAKING("Speaking"),
    PAUSED("Paused (mic in use)")
}

/** The flags the phase is derived from (kept as plain values so the logic is unit-testable). */
data class VoiceFlags(
    val hasHost: Boolean,
    val paused: Boolean,
    val speaking: Boolean,
    val awaitingReply: Boolean,
    val commandSession: Boolean,
    val wakeSession: Boolean
)

object VoicePhases {
    fun derive(f: VoiceFlags): VoicePhase = when {
        !f.hasHost -> VoicePhase.OFF
        f.paused -> VoicePhase.PAUSED
        f.speaking -> VoicePhase.SPEAKING
        f.awaitingReply -> VoicePhase.THINKING
        f.commandSession -> VoicePhase.LISTENING
        f.wakeSession -> VoicePhase.WAKE
        else -> VoicePhase.IDLE
    }
}

/** What the watchdog asks the service to do about something stuck. */
enum class Recovery { NONE, STOP_WAITING, STOP_SPEAKING, CLOSE_MIC, RESTART_WAKE, CLOSE_WAKE }

/**
 * Notices a phase that lasts too long. Every phase has a limit; passing it is a bug or a lost
 * callback, never normal, so the service resets that part and logs what it did.
 */
class VoiceWatchdog(
    private val thinkingMs: Long = 30_000L,
    private val speakingMs: Long = 120_000L,
    private val listeningMs: Long = 45_000L,
    private val idleWantedWakeMs: Long = 8_000L,
    private val wakeUnwantedMs: Long = 4_000L
) {
    /**
     * @param wakeWanted the service wants the quiet wake listener running right now.
     * @param inPhaseMs how long the phase has lasted.
     */
    fun check(phase: VoicePhase, wakeWanted: Boolean, inPhaseMs: Long): Recovery = when (phase) {
        VoicePhase.THINKING -> if (inPhaseMs > thinkingMs) Recovery.STOP_WAITING else Recovery.NONE
        VoicePhase.SPEAKING -> if (inPhaseMs > speakingMs) Recovery.STOP_SPEAKING else Recovery.NONE
        VoicePhase.LISTENING -> if (inPhaseMs > listeningMs) Recovery.CLOSE_MIC else Recovery.NONE
        VoicePhase.IDLE -> if (wakeWanted && inPhaseMs > idleWantedWakeMs) Recovery.RESTART_WAKE else Recovery.NONE
        VoicePhase.WAKE -> if (!wakeWanted && inPhaseMs > wakeUnwantedMs) Recovery.CLOSE_WAKE else Recovery.NONE
        VoicePhase.OFF, VoicePhase.PAUSED -> Recovery.NONE
    }
}
