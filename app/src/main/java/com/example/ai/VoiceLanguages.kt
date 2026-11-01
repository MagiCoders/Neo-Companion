package com.example.ai

import java.util.Locale

/**
 * The language the user speaks to the companion. Speech recognition runs in exactly this language,
 * so background noise can't be "heard" as Japanese or Korean, and the AI is told which language
 * to answer in when what it heard is unclear.
 */
object VoiceLanguages {
    data class Option(val label: String, val tag: String)

    const val PHONE = "Phone language"

    val ALL: List<Option> = listOf(
        Option(PHONE, ""),
        Option("English", "en-US"),
        Option("العربية (Arabic)", "ar"),
        Option("العربية – العراق (Iraqi Arabic)", "ar-IQ"),
        Option("العربية – السعودية (Saudi Arabic)", "ar-SA"),
        Option("العربية – مصر (Egyptian Arabic)", "ar-EG"),
        Option("العربية – الإمارات (Emirati Arabic)", "ar-AE"),
        Option("فارسی (Persian)", "fa-IR"),
        Option("Français", "fr-FR"),
        Option("Español", "es-ES"),
        Option("日本語 (Japanese)", "ja-JP"),
        Option("한국어 (Korean)", "ko-KR"),
        Option("Deutsch", "de-DE"),
        Option("Italiano", "it-IT"),
        Option("Português", "pt-BR"),
        Option("Türkçe", "tr-TR"),
        Option("Русский", "ru-RU"),
        Option("हिन्दी (Hindi)", "hi-IN"),
        Option("اردو (Urdu)", "ur-PK"),
        Option("বাংলা (Bengali)", "bn-BD"),
        Option("Bahasa Indonesia", "id-ID"),
        Option("中文 (Chinese)", "zh-CN"),
        Option("Filipino", "fil-PH"),
        Option("Nederlands", "nl-NL"),
        Option("Polski", "pl-PL")
    )

    /** BCP-47 tag for a stored label; unknown or "Phone language" → the phone's own language. */
    fun tagFor(label: String): String {
        val option = ALL.firstOrNull { it.label == label }
            ?: ALL.firstOrNull { it.tag.isNotEmpty() && label.isNotBlank() && it.label.contains(label, ignoreCase = true) }
        return option?.tag?.takeIf { it.isNotEmpty() } ?: Locale.getDefault().toLanguageTag()
    }

    /** Settings label for a BCP-47 tag from the AI ("ar-IQ" → Iraqi Arabic, "fa" → Persian), or null. */
    fun labelForTag(tag: String): String? {
        val t = tag.trim().replace('_', '-')
        if (t.isEmpty()) return null
        return (ALL.firstOrNull { it.tag.equals(t, ignoreCase = true) }
            ?: ALL.firstOrNull { it.tag.isNotEmpty() && it.tag.substringBefore('-').equals(t.substringBefore('-'), ignoreCase = true) })
            ?.label
    }

    /** Language and country in English for the AI prompt ("Arabic (Iraq)", "Persian (Iran)"). */
    fun promptName(tag: String): String = Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH).ifBlank { "English" }

    /** English name of the language ("Persian", "Arabic", …). */
    fun englishName(tag: String): String = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }
}

/**
 * The phrase that wakes the companion. The offline wake engine only knows English words from its
 * vocabulary, so custom phrases are resolved against it (see [WakeVocabulary]).
 */
object WakePhrases {
    const val DEFAULT = "hey mochi"
    val PRESETS = listOf("hey mochi", "hi mochi", "okay mochi", "hello mochi")
    private val WORD = Regex("^[a-z']{2,20}$")

    /** Lower-case words of a phrase, or null if it isn't 2–4 plain English words. */
    fun normalise(phrase: String): List<String>? {
        val words = phrase.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size !in 2..4 || words.any { !WORD.matches(it) }) return null
        return words
    }

    fun display(phrase: String): String =
        phrase.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }
}
