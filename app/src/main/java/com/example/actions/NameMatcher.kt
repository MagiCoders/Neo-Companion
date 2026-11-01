package com.example.actions

import com.example.ai.WakeVocabulary

/**
 * How well a spoken name matches a saved contact name, 0.0–1.0, by spelling AND by sound, across
 * Latin and Arabic/Persian letters ("Ali audit" ≈ "Ali Odeh", "علي اوديت" ≈ "Ali Odeh").
 * Pure logic, unit-tested.
 */
object NameMatcher {

    fun score(spoken: String, saved: String): Double {
        val a = tokens(spoken)
        val b = tokens(saved)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        // Each spoken word against its best saved word; a name with more saved words (middle
        // names) is fine, a spoken word that matches nothing pulls the score down.
        val perWord = matchWords(a, b)
        // Joined-up comparison only when the words were split differently ("aliodeh" vs "Ali Odeh").
        val whole = if (a.size != b.size) tokenScore(a.joinToString(""), b.joinToString("")) * 0.95 else 0.0
        val firstWordBonus = if (tokenScore(a.first(), b.first()) >= 0.9) 0.05 else 0.0
        return (maxOf(perWord, whole) + firstWordBonus).coerceAtMost(0.99)
    }

    /**
     * Average score with each spoken word paired to a different saved word (best pairs first), so
     * "Ali audits" can't use "Ali" twice. Spoken words left without a partner score 0.
     */
    private fun matchWords(spoken: List<String>, saved: List<String>): Double {
        val pairs = spoken.indices.flatMap { i -> saved.indices.map { j -> Triple(i, j, tokenScore(spoken[i], saved[j])) } }
            .sortedByDescending { it.third }
        val usedSpoken = HashSet<Int>()
        val usedSaved = HashSet<Int>()
        var total = 0.0
        for ((i, j, sc) in pairs) {
            if (i in usedSpoken || j in usedSaved) continue
            usedSpoken += i; usedSaved += j; total += sc
        }
        return total / spoken.size
    }

    private fun tokenScore(x: String, y: String): Double {
        if (x == y) return 1.0
        val spelling = similarity(x, y)
        val sx = skeleton(x)
        val sy = skeleton(y)
        val sound = if (sx.isEmpty() || sy.isEmpty()) 0.0 else similarity(sx, sy) * 0.9
        return maxOf(spelling, sound)
    }

    private fun similarity(x: String, y: String): Double {
        val longest = maxOf(x.length, y.length)
        return if (longest == 0) 0.0 else 1.0 - WakeVocabulary.levenshtein(x, y).toDouble() / longest
    }

    fun tokens(s: String): List<String> = ContactResolver.normalize(s).split(" ").filter { it.isNotEmpty() }

    /**
     * A rough sound skeleton shared by both scripts: Arabic/Persian letters are mapped to Latin
     * consonants, vowels and silent letters dropped ("odeh" → "d", "اوديت" → "dt", "audit" → "dt").
     */
    fun skeleton(word: String): String {
        val latin = buildString {
            for (ch in word) append(ARABIC_TO_LATIN[ch] ?: ch.toString())
        }.lowercase()
            .replace("ph", "f").replace("ck", "k").replace("q", "k").replace("c", "k")
            .replace("z", "s").replace("x", "ks").replace("th", "t").replace("dh", "d").replace("kh", "k").replace("gh", "g").replace("sh", "s").replace("ch", "s")
        val consonants = latin.filter { it.isLetter() && it !in "aeiouyw'h" }
        return consonants.replace(Regex("(.)\\1+"), "$1")
    }

    private val ARABIC_TO_LATIN: Map<Char, String> = mapOf(
        'ا' to "a", 'أ' to "a", 'إ' to "a", 'آ' to "a", 'ء' to "", 'ؤ' to "", 'ئ' to "", 'ع' to "", 'ى' to "a",
        'ب' to "b", 'پ' to "p", 'ت' to "t", 'ث' to "t", 'ج' to "j", 'چ' to "ch", 'ح' to "h", 'خ' to "kh",
        'د' to "d", 'ذ' to "d", 'ر' to "r", 'ز' to "z", 'ژ' to "zh", 'س' to "s", 'ش' to "sh", 'ص' to "s",
        'ض' to "d", 'ط' to "t", 'ظ' to "z", 'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ك' to "k", 'ک' to "k",
        'گ' to "g", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ه' to "h", 'ة' to "h", 'و' to "w", 'ي' to "y", 'ی' to "y"
    )
}
