package com.example.ai

import java.util.Locale

/**
 * Recognises "switch to English" / "حول عربي" / "فارسی صحبت کن" on the phone itself, without the
 * AI. This matters because after switching to Arabic the recogniser writes an English request in
 * Arabic letters ("سويتش تو انجلش"); a fixed word list handles that reliably. Pure logic, tested.
 */
object LanguageSwitch {

    /** Language names (in English, Arabic, Persian, as transliterated by recognisers) → tag. */
    private val NAMES: List<Pair<String, List<String>>> = listOf(
        "ar-IQ" to listOf("iraqi", "عراقي", "عراقية", "عراقی", "ايراقي"),
        "ar-EG" to listOf("egyptian", "مصري", "مصرية", "مصری"),
        "ar-SA" to listOf("saudi", "سعودي", "خليجي", "khaleeji", "gulf"),
        "en-US" to listOf("english", "انجليزي", "انكليزي", "إنجليزي", "انجليزية", "انكليزية", "انجلش", "انقلش", "إنجلش", "انگلیسی", "انگلیسي", "inglés", "anglais"),
        "ar" to listOf("arabic", "عربي", "عربية", "العربية", "عربی", "ارابيك", "عربيك"),
        "fa-IR" to listOf("persian", "farsi", "فارسي", "فارسی", "بيرشن", "پرشین", "فارسیه"),
        "ja-JP" to listOf("japanese", "ياباني", "يابانية", "ژاپنی", "جابنيز", "جابانيز", "日本語"),
        "ko-KR" to listOf("korean", "كوري", "كورية", "کره‌ای", "کره ای", "كوريان", "한국어"),
        "fr-FR" to listOf("french", "فرنسي", "فرنسية", "فرانسوی", "فرنش", "français"),
        "es-ES" to listOf("spanish", "اسباني", "إسباني", "اسبانية", "اسپانیایی", "سبانش", "español"),
        "de-DE" to listOf("german", "الماني", "ألماني", "المانية", "آلمانی", "جيرمن", "deutsch"),
        "tr-TR" to listOf("turkish", "تركي", "تركية", "ترکی", "تركش", "türkçe"),
        "ru-RU" to listOf("russian", "روسي", "روسية", "روسی", "رشن"),
        "hi-IN" to listOf("hindi", "هندي", "هندی"),
        "ur-PK" to listOf("urdu", "اردو", "أوردو"),
        "zh-CN" to listOf("chinese", "mandarin", "صيني", "صينية", "چینی", "中文")
    )

    /** Words that mean "switch / speak / talk / use" in the supported languages. */
    private val TRIGGERS = listOf(
        "switch", "change", "speak", "talk", "respond", "reply", "answer", "use", "language", "سويتش", "شينج",
        "حول", "حولي", "غير", "غيري", "بدل", "بدلي", "تكلم", "تكلمي", "اتكلم", "احجي", "احكي", "كلمني", "خلي", "رد", "ردي", "باللغة", "لغة",
        "عوض", "تغییر", "صحبت", "حرف", "بزن", "زبان", "بگو"
    )

    /** Asking for a translation is not a request to switch. */
    private val NOT_A_SWITCH = Regex(
        "(?i)translat|ترجم|ترجمه|meaning|معنى|معنی|how do you say|شلون تكول|چطوری میگن|" +
            // "talk to my mom in English" is a call/message request, not a switch.
            "\\b(call|phone|message|text|whatsapp|telegram|mom|dad|mother|father)\\b|اتصل|رسالة|واتساب|زنگ|پیام|امي|ابوي|مامان|بابا"
    )

    /** The tag the user wants to switch to, or null if the text isn't a language-switch request. */
    fun detect(text: String): String? {
        val t = text.lowercase(Locale.ROOT).trim()
        val words = t.split(Regex("[\\s,.!?،؟]+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 9 || NOT_A_SWITCH.containsMatchIn(t)) return null
        val hasTrigger = words.any { w -> TRIGGERS.any { w == it || w.removePrefix("و").removePrefix("ب") == it } }
        if (!hasTrigger) return null
        // Most specific first (Iraqi before Arabic).
        return NAMES.firstOrNull { (_, names) -> names.any { n -> words.any { w -> matches(w, n) } || (n.contains(' ') && t.contains(n)) } }?.first
    }

    private fun matches(word: String, name: String): Boolean {
        val w = word.removePrefix("بال").removePrefix("ال").removePrefix("ب").removePrefix("لل")
        return word == name || w == name || w == name.removePrefix("ال")
    }

    /** "Okay, I'll speak English now." said in the new language. */
    fun confirmation(tag: String): String = when {
        tag == "ar-IQ" -> "تمام، هسه راح أحجي وياك عراقي."
        tag.startsWith("ar") -> "تمام، من هسه راح أتكلم عربي."
        tag.startsWith("fa") -> "باشه، از الان فارسی صحبت می‌کنم."
        tag.startsWith("ja") -> "わかりました。これから日本語で話しますね。"
        tag.startsWith("ko") -> "알겠어요. 이제 한국어로 말할게요."
        tag.startsWith("fr") -> "D'accord, je parle français maintenant."
        tag.startsWith("es") -> "Vale, ahora hablo en español."
        tag.startsWith("de") -> "Okay, ich spreche jetzt Deutsch."
        tag.startsWith("tr") -> "Tamam, artık Türkçe konuşuyorum."
        tag.startsWith("ru") -> "Хорошо, теперь говорю по-русски."
        tag.startsWith("hi") -> "ठीक है, अब मैं हिंदी में बात करूँगी।"
        tag.startsWith("ur") -> "ٹھیک ہے، اب میں اردو میں بات کروں گی۔"
        tag.startsWith("zh") -> "好的，现在我说中文。"
        else -> "Okay, I'll speak ${VoiceLanguages.englishName(tag)} now."
    }
}
