package com.example.ai

/**
 * Guesses a phone voice's gender from its name, so her fallback voice matches her natural voice.
 * Android does not report gender, but the two common engines encode it in voice names:
 *  - Samsung: "...SMTf00" (female) / "...SMTm00" (male)
 *  - Google: "en-us-x-sfg#female_1-local", or fixed codes such as "en-us-x-tpf" (female).
 * Unknown names return null (no preference). Pure logic, unit-tested.
 */
object VoiceGender {

    /** Characters with a female natural voice (Leda, Aoede); the others use male voices. */
    fun isFemaleCharacter(characterId: String): Boolean = characterId !in MALE_CHARACTERS

    private val MALE_CHARACTERS = setOf("kaito", "marco")

    /** Google voice codes (the part after "-x-") by gender, from Google's published voice list. */
    private val GOOGLE_FEMALE = setOf("sfg", "iob", "iog", "tpc", "tpf", "gba", "gbc", "gbg", "afh", "arc", "are", "frc", "fra", "dea", "deb", "esc", "ese", "ita", "itb", "cfs", "cfg", "hia", "hic", "jab", "jac", "kob", "koc")
    private val GOOGLE_MALE = setOf("iol", "iom", "tpd", "rjs", "gbb", "gbd", "ard", "arz", "frd", "fre", "dec", "ded", "esd", "esf", "itc", "itd", "cfd", "hid", "hie", "jad", "jae", "kod")

    fun guess(voiceName: String): Boolean? {
        val n = voiceName.lowercase()
        if ("female" in n) return true
        if (Regex("(^|[^a-z])male").containsMatchIn(n)) return false
        Regex("smt([fm])\\d").find(n)?.let { return it.groupValues[1] == "f" }
        Regex("-x-([a-z]{3})").find(n)?.groupValues?.get(1)?.let { code ->
            if (code in GOOGLE_FEMALE) return true
            if (code in GOOGLE_MALE) return false
        }
        return null
    }
}
