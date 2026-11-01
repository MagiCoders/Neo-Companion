package com.example.data

enum class QuestCategory(
    val label: String,
    val emoji: String = ""
) {
    MEAL("Fridge chef"),
    MIND("Calm and mood"),
    DAY("Quick win"),
    WALLET("Smart saver")
}

enum class CompanionActionType(
    val label: String,
    val emoji: String = "",
    val channelSubtitle: String = "Mochi Bridge"
) {
    NONE("Direct answer", "", "Mochi Studio"),
    WHATSAPP("WhatsApp message", "💬", "WhatsApp · message"),
    WHATSAPP_CALL("WhatsApp call", "📞", "WhatsApp · voice/video call"),
    WHATSAPP_CONTACTS("WhatsApp contact list", "👥", "WhatsApp · contacts"),
    WHATSAPP_READ_CHAT("WhatsApp chat & voice reader", "🎧", "WhatsApp · chat & voice note"),
    TELEGRAM("Telegram message", "✈️", "Telegram · message"),
    SIM_CALL("Phone call", "📱", "SIM Dialer · mobile"),
    SOCIAL_CALL("Social call", "📲", "WhatsApp / Telegram · call"),
    SOCIAL_SEARCH("Social media & news", "🌐", "Social & News · live feed"),
    YOUTUBE("YouTube video & search", "▶️", "YouTube · video"),
    WHATS_PLAYING("Now playing inspector", "🎵", "Audio Session · live check"),
    BROWSER_SEARCH("Browser search", "🔎", "Web Browser · search"),
    OPEN_MEDIA("Videos, sounds & music", "🎶", "Media Player · audio/video"),
    NAVIGATE_ROUTE("App navigation", "🧭", "MochiLife · screen"),
    SPEAK_LOUD("Speaker broadcast", "🔊", "Speaker · neural voice"),
    READ_MESSAGES("Read my messages", "📥", "Notifications · read aloud"),
    REPLY_MESSAGE("Reply to a message", "↩️", "Notifications · reply"),
    WHATSAPP_PLAY_VOICE("Play WhatsApp voice note", "🎧", "WhatsApp · voice note")
}

object MochiHashRoutes {
    const val STAGE = "#/stage"
    const val PANTRY = "#/pantry"
    const val QUESTS = "#/quests"
    const val TOOLS = "#/tools"
    const val LIBRARY = "#/library"
    const val SEARCH = "#/search"
    const val PROFILE = "#/profile"
    const val EDIT_PROFILE = "#/profile/edit"
    const val WALLET = "#/profile/wallet"
    const val PERMISSIONS = "#/profile/permissions"
    const val PRIVACY = "#/profile/privacy"
    const val HELP = "#/profile/help"
    const val BUSY = "#/profile/busy"
    const val DRIVING = "#/driving"
    const val CHARACTER_PICKER = "#/character-picker"
    const val SINGLE_TOOL = "#/tools/single"
    const val TOOL_RESULT = "#/tools/result"

    /** Screens the AI is allowed to open by voice. */
    val ALL: List<String> = listOf(STAGE, PANTRY, QUESTS, TOOLS, LIBRARY, SEARCH, PROFILE, WALLET, PERMISSIONS, PRIVACY, HELP, BUSY, DRIVING, CHARACTER_PICKER)
}

data class MochiPersistentSettings(
    val userName: String = "",
    val userNickname: String = "",
    val userAboutBio: String = "",
    /** Voice language label from VoiceLanguages.ALL ("Phone language" = follow the phone). */
    val preferredLanguage: String = "Phone language",
    val floatingCompanionEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    /** Lower-case phrase that wakes the companion (checked against the offline vocabulary). */
    val wakePhrase: String = "hey mochi",
    /** Open-mic Gemini Live conversation instead of turn-by-turn voice. Best with headphones. */
    val liveVoiceChat: Boolean = false,
    val busyWatchingVideos: Boolean = true,
    val busyPlayingGames: Boolean = true,
    val busyFullScreenApps: Boolean = true,
    val busyBehaviorIndex: Int = 0, // 0: Answers by voice + edge pill, 1: Silent + save, 2: Normal
    val drivingAutoStart: Boolean = true,
    val saveChatHistory: Boolean = true,
    val useVoiceToneForCheckIns: Boolean = true,
    val shareAnonymousUsage: Boolean = false,
    val isDarkTheme: Boolean = true,
    /** 0 = Dark, 1 = Light, 2 = Auto (follow the phone). */
    val themeMode: Int = 0,
    val allowedPermissions: Set<String> = setOf(
        "Microphone",
        "Display over other apps",
        "Contacts",
        "Phone calls",
        "Camera",
        "Notifications"
    )
)

data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val companionName: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    val goldCoins: Int = 0,
    val streakDays: Int = 0,
    val moneySavedDollars: Int = 0,
    val aiCreditsRemaining: Int = 0,
    val aiCreditsMax: Int = 100,
    val creditsResetAtMillis: Long = 0L,
    val isProSubscriber: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val xpForNextLevel: Int
        get() = level * 200

    val levelProgressFraction: Float
        get() {
            val currentTierBase = (level - 1) * 200
            val span = 200f
            return ((xp - currentTierBase).toFloat() / span).coerceIn(0.05f, 1f)
        }
}

data class QuestItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = QuestCategory.DAY.name,
    val xpReward: Int = 50,
    val coinsReward: Int = 20,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val categoryEnum: QuestCategory
        get() = QuestCategory.entries.firstOrNull { it.name == category } ?: QuestCategory.DAY
}

data class MealCreation(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val ingredientsUsed: String = "",
    val prepTimeMinutes: Int = 15,
    val calories: Int = 480,
    val moneySavedDollars: Int = 22,
    val stepsSummary: String = "",
    val isCooked: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val parsedSteps: List<String>
        get() = stepsSummary.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

data class CompanionChatTurn(
    val id: String,
    val senderIsUser: Boolean,
    val message: String,
    val moodTag: String = "",
    val detectedLanguage: String = "",
    val detectedTone: String = "",
    val angerOrStressPercent: Int = 0,
    val behavioralInsight: String = "",
    /** Kept for stored history compatibility; never filled with invented text. */
    val verificationReport: String = "",
    val spokenConfirmation: String = "",
    val actionType: CompanionActionType = CompanionActionType.NONE,
    val actionRecipient: String = "",
    val actionPayload: String = "",
    val suggestedQuestTitle: String? = null,
    val suggestedQuestCategory: QuestCategory = QuestCategory.DAY,
    val xpBonus: Int = 0,
    /** True when this turn reports an action outcome (so the UI never shows a countdown for it). */
    val isActionReport: Boolean = false
)

data class FridgeIngredientChip(
    val name: String,
    val emoji: String = "",
    val category: String
)

data class PersonReceiptShare(
    val personName: String,
    val itemsOrdered: String,
    val subtotal: Double,
    val taxAndTipShare: Double,
    val totalOwed: Double
)

data class ReceiptSplitResult(
    val restaurantOrTitle: String,
    val currencySymbol: String = "$",
    val shares: List<PersonReceiptShare>,
    val grandTotal: Double,
    val shareableGroupText: String
)

data class CelebrationBurst(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val subtitle: String,
    val xpGained: Int,
    val coinsGained: Int,
    val moneySavedBonus: Int = 0
)
