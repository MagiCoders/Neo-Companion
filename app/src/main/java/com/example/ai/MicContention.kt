package com.example.ai

/**
 * Decides whether Mochi should step back from the microphone. It no longer counts "other
 * recorders" (that counted her own recorders and always-on helpers like "Hey Google" or Bixby,
 * which made her pause for nothing). It listens to what Android itself reports:
 *  - a phone or VoIP call is in progress, or
 *  - Android has silenced one of her own recorders because another app took the mic.
 * A state must hold for [holdMs] before it is accepted, so a moment of overlap never flickers.
 * Pure logic, unit-tested; the service feeds it on every recording change and once a second.
 */
class MicContention(private val holdMs: Long = 1_000L) {

    enum class State { FREE, SILENCED, CALL }

    data class Sample(val ownRecorderSilenced: Boolean, val inCall: Boolean)

    var state = State.FREE
        private set
    private var candidate = State.FREE
    private var candidateSince = 0L

    /** Returns the new state when it changes, else null. */
    fun update(sample: Sample, nowMs: Long): State? {
        val raw = when {
            sample.inCall -> State.CALL
            sample.ownRecorderSilenced -> State.SILENCED
            else -> State.FREE
        }
        if (raw == state) { candidate = state; return null }
        if (raw != candidate) { candidate = raw; candidateSince = nowMs; return null }
        if (nowMs - candidateSince < holdMs) return null
        state = raw
        return raw
    }

    fun reset() { state = State.FREE; candidate = State.FREE; candidateSince = 0L }
}
