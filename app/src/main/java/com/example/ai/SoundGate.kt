package com.example.ai

/**
 * Lets the wake-word recogniser rest while the room is quiet. Decoding speech is the heaviest
 * steady job in the app; most of the day there is nothing to decode. Each 100 ms block is checked
 * for loudness first (cheap), and only sound clearly above the room's own noise is passed on,
 * together with a short lead-in so the first syllable of "Hi Elza" is never cut.
 * The gate stays open until [closeAfterQuietBlocks] quiet blocks have passed, long enough for the
 * recogniser to finish the phrase. Pure logic, unit-tested.
 */
class SoundGate(
    private val openRatio: Double = 2.5,
    private val minOpenRms: Double = 120.0,
    private val closeAfterQuietBlocks: Int = 12,
    val preRollBlocks: Int = 3,
    /** Longest the gate stays open in one go (blocks). A wake phrase is under 2 s; 6 s is generous. */
    private val maxOpenBlocks: Int = 60
) {
    enum class Decision {
        /** Quiet: skip decoding (the block is kept as lead-in). */
        SKIP,
        /** Sound just started: decode the lead-in blocks, then this one. */
        OPEN,
        /** Keep decoding. */
        FEED,
        /** Quiet long enough: decode this last block, then finish the phrase and rest. */
        CLOSE
    }

    private var floor = -1.0
    private var open = false
    private var quietRun = 0
    private var openRun = 0
    private var openLevel = 0.0

    var blocksSeen = 0L
        private set
    var blocksDecoded = 0L
        private set

    val isOpen: Boolean get() = open

    fun decide(rms: Double): Decision {
        blocksSeen++
        if (floor < 0) floor = maxOf(rms, MIN_FLOOR)
        val threshold = maxOf(floor * openRatio, minOpenRms)
        val loud = rms > threshold
        if (!open) {
            // While the gate is closed everything heard is the room, so the floor is simply its
            // running average (a floor that dropped at every quiet instant reopened the gate on
            // ordinary room noise: 66% decoding on the S22+).
            floor = floor * 0.95 + rms * 0.05
            floor = maxOf(floor, MIN_FLOOR)
            if (!loud) return Decision.SKIP
            open = true
            quietRun = 0
            openRun = 0
            openLevel = rms
            blocksDecoded += 1 + preRollBlocks
            return Decision.OPEN
        }
        blocksDecoded++
        openRun++
        openLevel = openLevel * 0.9 + rms * 0.1
        quietRun = if (rms > threshold * 0.6) 0 else quietRun + 1
        if (quietRun >= closeAfterQuietBlocks) {
            open = false
            quietRun = 0
            return Decision.CLOSE
        }
        if (openRun >= maxOpenBlocks) {
            // Sound that never stops (a fan, traffic, a TV) is the room, not a wake phrase: learn
            // it as the new floor, so only something clearly louder opens the gate again.
            open = false
            quietRun = 0
            floor = maxOf(floor, openLevel)
            return Decision.CLOSE
        }
        return Decision.FEED
    }

    /** Share of audio actually decoded (0..1), for the performance meter. */
    fun decodedShare(): Double = if (blocksSeen == 0L) 0.0 else (blocksDecoded.toDouble() / blocksSeen).coerceAtMost(1.0)

    private companion object {
        const val MIN_FLOOR = 30.0
    }
}
