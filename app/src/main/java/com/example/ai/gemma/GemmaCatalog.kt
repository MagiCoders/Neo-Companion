package com.example.ai.gemma

/**
 * The on-phone Gemma 4 models the Lab can download and test. Only the two phone-sized models
 * exist for LiteRT-LM; the larger Gemma 4 models do not run on phones.
 * Files come from Google's public LiteRT community page on Hugging Face (Apache 2.0 licence) and
 * are checked against the SHA-256 the server publishes before they are used.
 */
data class GemmaModel(
    val id: String,
    val label: String,
    val repo: String,
    val file: String,
    /** Approximate download size, shown before downloading (the server's exact size is used after). */
    val approxBytes: Long,
    /** Phone memory at which it runs comfortably / at all (GB, rounded as phones are sold). */
    val comfortableRamGb: Int,
    val minimumRamGb: Int
) {
    val url: String get() = "https://huggingface.co/$repo/resolve/main/$file"
}

object GemmaCatalog {

    val E2B = GemmaModel(
        id = "e2b",
        label = "Gemma 4 E2B",
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        file = "gemma-4-E2B-it.litertlm",
        approxBytes = 2_588_147_712L,
        comfortableRamGb = 8,
        minimumRamGb = 6
    )

    val E4B = GemmaModel(
        id = "e4b",
        label = "Gemma 4 E4B",
        repo = "litert-community/gemma-4-E4B-it-litert-lm",
        file = "gemma-4-E4B-it.litertlm",
        approxBytes = 3_659_530_240L,
        comfortableRamGb = 12,
        minimumRamGb = 8
    )

    val MODELS = listOf(E2B, E4B)

    enum class Fit(val label: String) {
        GOOD("Fits this phone"),
        TIGHT("Experimental on this phone: may be slow or fail to load"),
        TOO_SMALL("Not enough memory on this phone")
    }

    /**
     * How well [model] fits a phone with [totalRamBytes] of memory. Phones sold as "8 GB" report a
     * little less (the system reserves some), so the comparison allows 10%.
     */
    fun fit(model: GemmaModel, totalRamBytes: Long): Fit {
        val gb = totalRamBytes / 1_000_000_000.0 * 1.1
        return when {
            gb >= model.comfortableRamGb -> Fit.GOOD
            gb >= model.minimumRamGb -> Fit.TIGHT
            else -> Fit.TOO_SMALL
        }
    }

    /** A SHA-256 as Hugging Face publishes it in the X-Linked-Etag header (quoted hex), or null. */
    fun sha256FromEtag(etag: String?): String? =
        etag?.trim()?.removePrefix("W/")?.trim('"')?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
}
