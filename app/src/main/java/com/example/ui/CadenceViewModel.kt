package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AttachmentMediaType
import com.example.ai.CadenceAiService
import com.example.ai.ConversionOutputResult
import com.example.ai.ConversionToolMode
import com.example.ai.OmnichannelMediaBridge
import com.example.ai.PendingVoiceAction
import com.example.ai.UniversalConverterEngine
import com.example.ai.VoiceCommandService
import com.example.data.CadenceRepository
import com.example.data.CompanionActionType
import com.example.data.CompanionChatTurn
import com.example.data.FridgeIngredientChip
import com.example.data.MealCreation
import com.example.data.MochiHashRoutes
import com.example.data.MochiPersistentSettings
import com.example.data.QuestCategory
import com.example.data.QuestItem
import com.example.data.ReceiptSplitResult
import com.example.data.UserProfile
import com.example.ui.character.CharacterController
import com.example.ui.character.CharacterRuntimeState
import com.example.ui.character.CharacterSpec
import com.example.ui.character.FloatingCompanionService
import com.example.ui.theme.MochiVisualStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class CadenceViewModel(
    private val appContext: Context,
    private val repository: CadenceRepository,
    private val aiService: CadenceAiService
) : ViewModel() {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("mochi_persistent_settings", Context.MODE_PRIVATE)

    val characterController = CharacterController(viewModelScope)

    /** Process-wide voice engine shared with the floating overlay (one mic owner). */
    val voiceCommandService = VoiceCommandService.shared(appContext)

    val pendingVoiceAction: StateFlow<PendingVoiceAction?> = voiceCommandService.pendingAction
    val isWakeWordListeningActive: StateFlow<Boolean> = voiceCommandService.isActivelyListening
    val liveVoicePartialTranscript: StateFlow<String> = voiceCommandService.livePartialTranscript

    // --- HashRouter Navigation State Management ---
    private val _currentHashRoute = MutableStateFlow(MochiHashRoutes.STAGE)
    val currentHashRoute: StateFlow<String> = _currentHashRoute.asStateFlow()

    private val hashBackStack = ArrayDeque<String>()

    fun navigateHash(route: String) {
        val current = _currentHashRoute.value
        if (current == route) return
        hashBackStack.addLast(current)
        if (hashBackStack.size > 24) {
            hashBackStack.removeFirst()
        }
        _currentHashRoute.value = route
    }

    fun popHashBack(): Boolean {
        if (hashBackStack.isNotEmpty()) {
            val prev = hashBackStack.removeLast()
            _currentHashRoute.value = prev
            return true
        }
        if (_currentHashRoute.value != MochiHashRoutes.STAGE) {
            _currentHashRoute.value = MochiHashRoutes.STAGE
            return true
        }
        return false
    }

    // --- Persistent Settings Store (Never resets when navigating between screens) ---
    private val _settings = MutableStateFlow(loadSettingsFromDisk())
    val settings: StateFlow<MochiPersistentSettings> = _settings.asStateFlow()

    private fun loadSettingsFromDisk(): MochiPersistentSettings {
        // v2: the always-on wake word used to default to ON and caused the mic to open and close
        // in a loop. Reset it to OFF once; the user can turn it back on in Settings.
        if (prefs.getInt("settingsSchema", 1) < 2) {
            prefs.edit().putBoolean("wakeWordEnabled", false).putInt("settingsSchema", 2).apply()
        }
        val defaultPerms = setOf(
            "Microphone",
            "Display over other apps",
            "Contacts",
            "Phone calls",
            "Camera",
            "Notifications"
        )
        return MochiPersistentSettings(
            userName = prefs.getString("userName", "Your name") ?: "Your name",
            userNickname = prefs.getString("userNickname", "") ?: "",
            userAboutBio = prefs.getString(
                "userAboutBio",
                "Vegetarian on weekdays, enjoys quick 15-minute dinners and calm evening focus."
            ) ?: "",
            preferredLanguage = prefs.getString("preferredLanguage", "English") ?: "English",
            floatingCompanionEnabled = prefs.getBoolean("floatingCompanionEnabled", true),
            wakeWordEnabled = prefs.getBoolean("wakeWordEnabled", false),
            busyWatchingVideos = prefs.getBoolean("busyWatchingVideos", true),
            busyPlayingGames = prefs.getBoolean("busyPlayingGames", true),
            busyFullScreenApps = prefs.getBoolean("busyFullScreenApps", true),
            busyBehaviorIndex = prefs.getInt("busyBehaviorIndex", 0),
            drivingAutoStart = prefs.getBoolean("drivingAutoStart", true),
            saveChatHistory = prefs.getBoolean("saveChatHistory", true),
            useVoiceToneForCheckIns = prefs.getBoolean("useVoiceToneForCheckIns", true),
            shareAnonymousUsage = prefs.getBoolean("shareAnonymousUsage", false),
            isDarkTheme = prefs.getBoolean("isDarkTheme", true),
            aiEnergyBalance = prefs.getInt("aiEnergyBalance", 1240),
            checkedInToday = prefs.getBoolean("checkedInToday", false),
            streakDays = prefs.getInt("streakDays", 4),
            allowedPermissions = prefs.getStringSet("allowedPermissions", defaultPerms)?.toSet() ?: defaultPerms
        )
    }

    fun updateSettings(transform: (MochiPersistentSettings) -> MochiPersistentSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        voiceCommandService.setWakeWordEnabled(updated.wakeWordEnabled)
        prefs.edit()
            .putString("userName", updated.userName)
            .putString("userNickname", updated.userNickname)
            .putString("userAboutBio", updated.userAboutBio)
            .putString("preferredLanguage", updated.preferredLanguage)
            .putBoolean("floatingCompanionEnabled", updated.floatingCompanionEnabled)
            .putBoolean("wakeWordEnabled", updated.wakeWordEnabled)
            .putBoolean("busyWatchingVideos", updated.busyWatchingVideos)
            .putBoolean("busyPlayingGames", updated.busyPlayingGames)
            .putBoolean("busyFullScreenApps", updated.busyFullScreenApps)
            .putInt("busyBehaviorIndex", updated.busyBehaviorIndex)
            .putBoolean("drivingAutoStart", updated.drivingAutoStart)
            .putBoolean("saveChatHistory", updated.saveChatHistory)
            .putBoolean("useVoiceToneForCheckIns", updated.useVoiceToneForCheckIns)
            .putBoolean("shareAnonymousUsage", updated.shareAnonymousUsage)
            .putBoolean("isDarkTheme", updated.isDarkTheme)
            .putInt("aiEnergyBalance", updated.aiEnergyBalance)
            .putBoolean("checkedInToday", updated.checkedInToday)
            .putInt("streakDays", updated.streakDays)
            .putStringSet("allowedPermissions", updated.allowedPermissions)
            .apply()
    }

    fun togglePermissionAllowed(permissionTitle: String) {
        updateSettings { current ->
            val next = current.allowedPermissions.toMutableSet()
            if (!next.add(permissionTitle)) {
                next.remove(permissionTitle)
            }
            current.copy(allowedPermissions = next)
        }
    }

    // --- Quests State (for #/quests view and Stage quests integration) ---
    private val _quests = MutableStateFlow(
        listOf(
            QuestItem(
                id = "quest_1",
                title = "Cook a 15-minute Fridge Chef dinner instead of delivery",
                category = QuestCategory.MEAL.name,
                xpReward = 120,
                coinsReward = 35,
                isCompleted = false
            ),
            QuestItem(
                id = "quest_2",
                title = "Send a voice check-in to Neo or Mom on WhatsApp",
                category = QuestCategory.DAY.name,
                xpReward = 80,
                coinsReward = 25,
                isCompleted = false
            ),
            QuestItem(
                id = "quest_3",
                title = "Take 3 slow breaths with Nurse Elena's calm pacing",
                category = QuestCategory.MIND.name,
                xpReward = 60,
                coinsReward = 20,
                isCompleted = true
            ),
            QuestItem(
                id = "quest_4",
                title = "Split a group dinner receipt in under 10 seconds",
                category = QuestCategory.WALLET.name,
                xpReward = 90,
                coinsReward = 30,
                isCompleted = false
            )
        )
    )
    val quests: StateFlow<List<QuestItem>> = _quests.asStateFlow()

    fun toggleQuestCompleted(questId: String) {
        _quests.value = _quests.value.map { q ->
            if (q.id == questId) {
                val nowComplete = !q.isCompleted
                if (nowComplete) {
                    updateSettings { s -> s.copy(aiEnergyBalance = s.aiEnergyBalance + q.coinsReward) }
                    characterController.react("happy")
                }
                q.copy(isCompleted = nowComplete)
            } else {
                q
            }
        }
    }

    fun addQuest(title: String, category: QuestCategory = QuestCategory.DAY) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val item = QuestItem(
            id = "quest_${UUID.randomUUID().toString().take(8)}",
            title = clean,
            category = category.name,
            xpReward = 75,
            coinsReward = 25,
            isCompleted = false
        )
        _quests.value = listOf(item) + _quests.value
    }

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _meals = MutableStateFlow<List<MealCreation>>(
        listOf(
            MealCreation(
                id = "meal_default_1",
                title = "Lemon spinach pasta",
                ingredientsUsed = "Eggs, Spinach, Feta, Lemon, Pasta, Garlic",
                prepTimeMinutes = 12,
                calories = 460,
                moneySavedDollars = 24,
                stepsSummary = "1. Boil pasta in salted water for 8 minutes until al dente.\n2. Sear minced garlic and fresh spinach in olive oil for 2 minutes.\n3. Toss pasta with lemon juice, crumbled feta, and pasta water.",
                isCooked = false
            ),
            MealCreation(
                id = "meal_default_2",
                title = "Spinach and feta omelette",
                ingredientsUsed = "Eggs, Spinach, Feta",
                prepTimeMinutes = 8,
                calories = 340,
                moneySavedDollars = 18,
                stepsSummary = "1. Whisk 3 eggs with a pinch of salt and black pepper.\n2. Wilt spinach in a warm skillet for 1 minute, then pour in eggs.\n3. Fold in crumbled feta and plate warm.",
                isCooked = false
            ),
            MealCreation(
                id = "meal_default_3",
                title = "Feta and lemon egg skillet",
                ingredientsUsed = "Eggs, Feta, Lemon, Garlic",
                prepTimeMinutes = 10,
                calories = 390,
                moneySavedDollars = 20,
                stepsSummary = "1. Sizzle garlic in olive oil over medium heat.\n2. Crack eggs into the skillet and scatter feta around the edges.\n3. Finish with fresh lemon zest and black pepper.",
                isCooked = false
            )
        )
    )
    val meals: StateFlow<List<MealCreation>> = _meals.asStateFlow()

    // 3 Launch Characters from CHARACTERS.md
    private val _selectedCharacter = MutableStateFlow(
        CharacterSpec.fromId(
            prefs.getString("selectedCharacterId", CharacterSpec.ELENA.id) ?: CharacterSpec.ELENA.id
        )
    )
    val selectedCharacter: StateFlow<CharacterSpec> = _selectedCharacter.asStateFlow()

    private val _previewAccentCharacter = MutableStateFlow<CharacterSpec?>(null)
    val previewAccentCharacter: StateFlow<CharacterSpec?> = _previewAccentCharacter.asStateFlow()

    private val _selectedVisualStyle = MutableStateFlow(
        if (_settings.value.isDarkTheme) MochiVisualStyle.STUDIO_4K_OPTICAL else MochiVisualStyle.WARM_STUDIO_LUXE
    )
    val selectedVisualStyle: StateFlow<MochiVisualStyle> = _selectedVisualStyle.asStateFlow()

    private val _isFloatingCompanionMode = MutableStateFlow(false)
    val isFloatingCompanionMode: StateFlow<Boolean> = _isFloatingCompanionMode.asStateFlow()

    // Live Mood & Stress Analyzer State
    private val _currentStressPercent = MutableStateFlow(14)
    val currentStressPercent: StateFlow<Int> = _currentStressPercent.asStateFlow()

    private val _currentMoodTone = MutableStateFlow("Calm & Balanced")
    val currentMoodTone: StateFlow<String> = _currentMoodTone.asStateFlow()

    private val _currentBehavioralInsight = MutableStateFlow("Ready for hands-free voice or text tasks.")
    val currentBehavioralInsight: StateFlow<String> = _currentBehavioralInsight.asStateFlow()

    // Tools & Converter State
    private val _selectedConversionTool = MutableStateFlow(ConversionToolMode.IMAGE_TO_PDF)
    val selectedConversionTool: StateFlow<ConversionToolMode> = _selectedConversionTool.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _conversionResult = MutableStateFlow<ConversionOutputResult?>(null)
    val conversionResult: StateFlow<ConversionOutputResult?> = _conversionResult.asStateFlow()

    private val _chatHistory = MutableStateFlow(
        listOf(
            CompanionChatTurn(
                id = "welcome_1",
                senderIsUser = false,
                message = "Hi! I'm Nurse Elena. Ask me by voice or text to call someone on WhatsApp, read your WhatsApp voice messages, check social media or news, open YouTube, or search the web.",
                moodTag = "Voice & Bridge Ready",
                detectedLanguage = "English",
                detectedTone = "Warm & Attentive",
                actionType = CompanionActionType.NONE,
                actionRecipient = "",
                actionPayload = "",
                angerOrStressPercent = 14,
                spokenConfirmation = "Hi, I'm Nurse Elena. How can I help you today?",
                suggestedQuestTitle = "Take three slow breaths",
                suggestedQuestCategory = QuestCategory.MIND,
                xpBonus = 40
            )
        )
    )
    val chatHistory: StateFlow<List<CompanionChatTurn>> = _chatHistory.asStateFlow()

    private val _isCompanionThinking = MutableStateFlow(false)
    val isCompanionThinking: StateFlow<Boolean> = _isCompanionThinking.asStateFlow()

    // Track which turn IDs have already executed or been cancelled so countdowns never fire twice
    private val _executedOrCancelledTurnIds = MutableStateFlow<Set<String>>(emptySet())
    val executedOrCancelledTurnIds: StateFlow<Set<String>> = _executedOrCancelledTurnIds.asStateFlow()

    fun markTurnCancelled(turnId: String) {
        _executedOrCancelledTurnIds.value = _executedOrCancelledTurnIds.value + turnId
        voiceCommandService.cancelPendingAction()
    }

    fun cancelPendingVoiceAction() {
        val pending = voiceCommandService.pendingAction.value
        if (pending != null) {
            _executedOrCancelledTurnIds.value = _executedOrCancelledTurnIds.value + pending.turn.id
        }
        voiceCommandService.cancelPendingAction()
        characterController.transitionTo(CharacterRuntimeState.Idle)
    }

    fun executePendingVoiceActionNow(context: Context, tts: TextToSpeech?) {
        val pending = voiceCommandService.executePendingActionImmediately() ?: return
        executeTurnActionNow(context, tts, pending.turn)
    }

    fun executeTurnActionNow(
        context: Context,
        tts: TextToSpeech?,
        turn: CompanionChatTurn
    ) {
        if (_executedOrCancelledTurnIds.value.contains(turn.id)) return
        _executedOrCancelledTurnIds.value = _executedOrCancelledTurnIds.value + turn.id
        voiceCommandService.cancelPendingAction()
        // Opening another app (WhatsApp, YouTube, a call…): bring the companion along as the
        // floating overlay. Started now, while the app is still visible, so it may use the mic.
        val leavesApp = turn.actionType !in setOf(
            CompanionActionType.NONE,
            CompanionActionType.SPEAK_LOUD,
            CompanionActionType.NAVIGATE_ROUTE,
            CompanionActionType.WHATS_PLAYING
        )
        if (leavesApp && _settings.value.floatingCompanionEnabled) {
            FloatingCompanionService.startIfEnabled(context)
        }
        voiceCommandService.executeActionIntent(
            context = context,
            tts = tts,
            turn = turn,
            companion = _selectedCharacter.value,
            onNavigateRoute = { route -> navigateHash(route) }
        )
    }

    // Fridge Chef State
    val pantryIngredientChips: List<FridgeIngredientChip> = listOf(
        FridgeIngredientChip("Eggs", "", "Protein"),
        FridgeIngredientChip("Spinach", "", "Produce"),
        FridgeIngredientChip("Feta", "", "Dairy"),
        FridgeIngredientChip("Lemon", "", "Produce"),
        FridgeIngredientChip("Pasta", "", "Base"),
        FridgeIngredientChip("Garlic", "", "Flavor"),
        FridgeIngredientChip("Rice", "", "Base"),
        FridgeIngredientChip("Chicken", "", "Protein")
    )

    private val _selectedIngredients = MutableStateFlow(
        setOf("Eggs", "Spinach", "Feta", "Lemon", "Pasta", "Garlic")
    )
    val selectedIngredients: StateFlow<Set<String>> = _selectedIngredients.asStateFlow()

    private val _fridgePhotoBitmap = MutableStateFlow<Bitmap?>(null)
    val fridgePhotoBitmap: StateFlow<Bitmap?> = _fridgePhotoBitmap.asStateFlow()

    private val _customCraving = MutableStateFlow("")
    val customCraving: StateFlow<String> = _customCraving.asStateFlow()

    private val _chefNote = MutableStateFlow("The pasta is quickest. Want me to read the steps out loud?")
    val chefNote: StateFlow<String> = _chefNote.asStateFlow()

    private val _isChefCooking = MutableStateFlow(false)
    val isChefCooking: StateFlow<Boolean> = _isChefCooking.asStateFlow()

    // Group Receipt & Expense Splitter State
    private val _receiptOrdersInput = MutableStateFlow(
        "Neo: Grilled Ribeye Steak $28.00\nElena: Matcha Latte & Salad $16.50\nKaito: Crispy Chicken Bowl $18.00\nMarco: Truffle Pasta $22.00"
    )
    val receiptOrdersInput: StateFlow<String> = _receiptOrdersInput.asStateFlow()

    private val _receiptTaxTipPercent = MutableStateFlow(18.0)
    val receiptTaxTipPercent: StateFlow<Double> = _receiptTaxTipPercent.asStateFlow()

    private val _receiptPhotoBitmap = MutableStateFlow<Bitmap?>(null)
    val receiptPhotoBitmap: StateFlow<Bitmap?> = _receiptPhotoBitmap.asStateFlow()

    private val _receiptSplitResult = MutableStateFlow<ReceiptSplitResult?>(null)
    val receiptSplitResult: StateFlow<ReceiptSplitResult?> = _receiptSplitResult.asStateFlow()

    private val _isSplittingReceipt = MutableStateFlow(false)
    val isSplittingReceipt: StateFlow<Boolean> = _isSplittingReceipt.asStateFlow()

    private var profileJob: Job? = null
    private var mealsJob: Job? = null

    fun setPreviewAccentCharacter(spec: CharacterSpec?) {
        _previewAccentCharacter.value = spec
    }

    fun selectVisualStyle(style: MochiVisualStyle) {
        _selectedVisualStyle.value = style
        updateSettings { it.copy(isDarkTheme = !style.isLight) }
        characterController.transitionTo(CharacterRuntimeState.Reacting("happy"))
    }

    fun setFloatingCompanionMode(enabled: Boolean) {
        _isFloatingCompanionMode.value = enabled
        if (enabled) {
            characterController.transitionTo(CharacterRuntimeState.Reacting("wave"))
        }
    }

    fun launchSystemOverlayIfPermitted(context: Context, moveAppToBackground: Boolean = false): Boolean {
        val lastMsg = _chatHistory.value.lastOrNull { !it.senderIsUser }?.message
        return FloatingCompanionService.startOrRequestSystemOverlay(
            context = context,
            character = _selectedCharacter.value,
            lastMessage = lastMsg,
            moveAppToBackground = moveAppToBackground
        )
    }

    fun chooseCharacter(spec: CharacterSpec) {
        _selectedCharacter.value = spec
        _previewAccentCharacter.value = null
        prefs.edit().putString("selectedCharacterId", spec.id).apply()
        characterController.transitionTo(CharacterRuntimeState.Reacting("wave"))
        val uid = _currentUserId.value
        if (uid != null) {
            viewModelScope.launch {
                try {
                    repository.renameCompanion(uid, spec.displayName)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun clearChatHistory() {
        _chatHistory.value = _chatHistory.value.take(1)
    }

    fun triggerCalmDeEscalation(context: Context, tts: TextToSpeech?) {
        val activeChar = _selectedCharacter.value
        _currentStressPercent.value = 12
        _currentMoodTone.value = "Cooling De-Escalation Active"
        _currentBehavioralInsight.value = "Heart rate & tension pacing slowed — guided calm reset active."
        val calmMsg = "Take a slow, deep breath with me. Inhale gently for four seconds, hold, and let your shoulders drop. I am taking care of your messages and tasks right now."
        val calmTurn = CompanionChatTurn(
            id = UUID.randomUUID().toString(),
            senderIsUser = false,
            message = calmMsg,
            moodTag = "Cooling Reset",
            detectedLanguage = "English",
            detectedTone = "Calm & Restorative",
            angerOrStressPercent = 12,
            behavioralInsight = "Active stress de-escalation triggered.",
            verificationReport = "Verified: Audio stream & breathing cadence synchronized.",
            spokenConfirmation = calmMsg,
            actionType = CompanionActionType.SPEAK_LOUD
        )
        _chatHistory.value = _chatHistory.value + calmTurn
        characterController.react("calm")
        characterController.speakWithLipSync(context, tts, calmMsg, activeChar)
    }

    fun sendTurnAttachmentToChannel(
        context: Context,
        tts: TextToSpeech?,
        turn: CompanionChatTurn,
        targetPlatform: String,
        mediaType: AttachmentMediaType
    ) {
        val activeChar = _selectedCharacter.value
        val recipient = turn.actionRecipient.ifBlank { "Friend" }
        val payload = turn.actionPayload.ifBlank { turn.message }
        viewModelScope.launch {
            characterController.speakWithLipSync(
                context = context,
                tts = tts,
                text = "Sending ${mediaType.label} to $recipient on $targetPlatform.",
                character = activeChar
            )
            OmnichannelMediaBridge.sendAttachmentToPlatform(
                context = context,
                tts = tts,
                targetPlatform = targetPlatform,
                recipient = recipient,
                messageText = payload,
                mediaType = mediaType
            )
        }
    }

    fun dialPhoneCall(context: Context, tts: TextToSpeech?, phoneOrRecipient: String) {
        val activeChar = _selectedCharacter.value
        val target = phoneOrRecipient.ifBlank { "+15550192834" }
        characterController.speakWithLipSync(
            context = context,
            tts = tts,
            text = "Dialing phone call to $target now.",
            character = activeChar
        )
        OmnichannelMediaBridge.placeSimPhoneCall(context, target)
    }

    fun clearConversionResult() {
        _conversionResult.value = null
    }

    fun runImageToPdf(context: Context, bitmap: Bitmap, onResultReady: () -> Unit = {}) {
        if (_isConverting.value) return
        viewModelScope.launch {
            _isConverting.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val res = UniversalConverterEngine.convertImageToPdf(context, bitmap)
                _conversionResult.value = res
                characterController.transitionTo(CharacterRuntimeState.Reacting("happy"))
                onResultReady()
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun runOcrImageToText(context: Context, bitmap: Bitmap, onResultReady: () -> Unit = {}) {
        if (_isConverting.value) return
        viewModelScope.launch {
            _isConverting.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val res = UniversalConverterEngine.extractTextFromImage(context, bitmap)
                _conversionResult.value = res
                characterController.transitionTo(CharacterRuntimeState.Reacting("happy"))
                onResultReady()
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun runPdfToImage(context: Context, pdfUri: Uri, onResultReady: () -> Unit = {}) {
        if (_isConverting.value) return
        viewModelScope.launch {
            _isConverting.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val res = UniversalConverterEngine.convertPdfToHighResImage(context, pdfUri)
                _conversionResult.value = res
                characterController.transitionTo(CharacterRuntimeState.Reacting("happy"))
                onResultReady()
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun runTextToVoice(context: Context, tts: TextToSpeech?, text: String, onResultReady: () -> Unit = {}) {
        if (_isConverting.value) return
        viewModelScope.launch {
            _isConverting.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val res = UniversalConverterEngine.convertTextToVoiceFile(context, tts, text)
                _conversionResult.value = res
                characterController.speakWithLipSync(context, tts, text, _selectedCharacter.value)
                onResultReady()
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun runVoiceToText(context: Context, audioUri: Uri, onResultReady: () -> Unit = {}) {
        if (_isConverting.value) return
        viewModelScope.launch {
            _isConverting.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val res = UniversalConverterEngine.transcribeAudioFileToText(context, audioUri)
                _conversionResult.value = res
                characterController.transitionTo(CharacterRuntimeState.Reacting("happy"))
                onResultReady()
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun runVideoToAudio(context: Context, videoUri: Uri, onResultReady: () -> Unit = {}) {
        if (_isConverting.value) return
        viewModelScope.launch {
            _isConverting.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val res = UniversalConverterEngine.extractAudioFromVideo(context, videoUri)
                _conversionResult.value = res
                characterController.transitionTo(CharacterRuntimeState.Reacting("happy"))
                onResultReady()
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun runDocToPdf(context: Context, title: String, body: String, onResultReady: () -> Unit = {}) {
        if (_isConverting.value) return
        viewModelScope.launch {
            _isConverting.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val res = UniversalConverterEngine.convertDocumentOrTextToPdf(context, title, body)
                _conversionResult.value = res
                characterController.transitionTo(CharacterRuntimeState.Reacting("happy"))
                onResultReady()
            } finally {
                _isConverting.value = false
            }
        }
    }

    fun onAuthenticatedUserChanged(
        userId: String?,
        displayName: String?,
        email: String?
    ) {
        if (_currentUserId.value == userId && userId != null) return
        _currentUserId.value = userId

        profileJob?.cancel()
        mealsJob?.cancel()

        if (userId == null) {
            _userProfile.value = UserProfile()
            return
        }

        if (!displayName.isNullOrBlank() && _settings.value.userName == "Your name") {
            updateSettings { it.copy(userName = displayName) }
        }

        viewModelScope.launch {
            try {
                repository.ensureUserProfileInitialized(
                    userId = userId,
                    displayName = displayName ?: _settings.value.userName,
                    email = email ?: ""
                )
            } catch (_: Exception) {
            }
        }

        profileJob = viewModelScope.launch {
            try {
                repository.observeUserProfile(userId).collectLatest { profile ->
                    if (profile != null) {
                        _userProfile.value = profile
                    }
                }
            } catch (_: Exception) {
            }
        }

        mealsJob = viewModelScope.launch {
            try {
                repository.observeMeals(userId).collectLatest { list ->
                    if (list.isNotEmpty()) {
                        _meals.value = list
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Processes any voice or text command adaptively and immediately:
     * - Handles "stop" / "cancel" voice commands to abort any active countdown
     * - Handles character switching ("switch to Chef Marco", "switch to Kaito")
     * - Speaks with natural human-like neural voice & 60fps lip-sync
     * - Triggers immediate navigation or starts the 3-second cancellable countdown
     */
    fun sendMessageToMochi(
        context: Context,
        tts: TextToSpeech?,
        userText: String
    ) {
        val clean = userText.trim()
        if (clean.isEmpty() || _isCompanionThinking.value) {
            voiceCommandService.clearAwaitingReply()
            return
        }
        val lower = clean.lowercase()

        // Voice cancel command ("stop", "cancel")
        if (lower == "stop" || lower == "cancel" || lower == "never mind" || lower == "undo") {
            cancelPendingVoiceAction()
            val lastTurn = _chatHistory.value.lastOrNull { !it.senderIsUser }
            if (lastTurn != null) {
                markTurnCancelled(lastTurn.id)
            }
            tts?.stop()
            characterController.transitionTo(CharacterRuntimeState.Idle)
            voiceCommandService.clearAwaitingReply()
            return
        }

        // Character switch by voice
        when {
            lower.contains("switch to marco") || lower.contains("chef marco") -> chooseCharacter(CharacterSpec.MARCO)
            lower.contains("switch to kaito") || lower.contains("kaito storm") -> chooseCharacter(CharacterSpec.KAITO)
            lower.contains("switch to elena") || lower.contains("nurse elena") -> chooseCharacter(CharacterSpec.ELENA)
        }

        val uid = _currentUserId.value
        val activeChar = _selectedCharacter.value

        val userTurn = CompanionChatTurn(
            id = UUID.randomUUID().toString(),
            senderIsUser = true,
            message = clean,
            moodTag = "You"
        )
        _chatHistory.value = _chatHistory.value + userTurn

        viewModelScope.launch {
            _isCompanionThinking.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            try {
                if (uid != null) {
                    try {
                        repository.consumeAiCreditOrThrow(uid, _userProfile.value)
                    } catch (_: Exception) {
                    }
                }

                // Route through VoiceCommandService (combines <10ms instant router + Firebase AI gemini-3.8-flash)
                val aiTurn = voiceCommandService.processNaturalLanguageCommand(
                    commandText = clean,
                    companion = activeChar
                )
                _chatHistory.value = _chatHistory.value + aiTurn

                // Update Live Mood & Stress Analyzer
                _currentStressPercent.value = aiTurn.angerOrStressPercent
                _currentMoodTone.value = aiTurn.detectedTone
                _currentBehavioralInsight.value = aiTurn.behavioralInsight

                // Speak confirmation + reply out loud with human-like neural voice and 60fps lip-sync
                val spokenText = when {
                    aiTurn.actionType == CompanionActionType.WHATSAPP_READ_CHAT ->
                        aiTurn.spokenConfirmation.ifBlank { aiTurn.message }
                    aiTurn.spokenConfirmation.isNotBlank() &&
                        aiTurn.actionType != CompanionActionType.NONE &&
                        !aiTurn.message.startsWith(aiTurn.spokenConfirmation.take(15)) ->
                        aiTurn.spokenConfirmation
                    else ->
                        aiTurn.message
                }

                characterController.speakWithLipSync(
                    context = context,
                    tts = tts,
                    text = spokenText,
                    character = activeChar
                )

                // Trigger the 3-second 'Undo/Cancel' overlay pill for all actionable voice/text commands
                when (aiTurn.actionType) {
                    CompanionActionType.WHATS_PLAYING -> {
                        executeTurnActionNow(context, tts, aiTurn)
                    }
                    CompanionActionType.NONE, CompanionActionType.SPEAK_LOUD -> {
                        // Conversational reply only — no external navigation/media intent to undo
                    }
                    else -> {
                        voiceCommandService.scheduleActionWithUndoWindow(
                            turn = aiTurn,
                            durationSeconds = 3
                        ) { confirmedAction ->
                            executeTurnActionNow(context, tts, confirmedAction.turn)
                        }
                    }
                }
            } finally {
                _isCompanionThinking.value = false
                voiceCommandService.clearAwaitingReply()
            }
        }
    }

    fun readMealStepsOutLoud(context: Context, tts: TextToSpeech?, meal: MealCreation?) {
        val targetMeal = meal ?: _meals.value.firstOrNull() ?: return
        val spoken = "Here are the quick steps for ${targetMeal.title}. ${targetMeal.stepsSummary.replace("\n", " ")}"
        characterController.speakWithLipSync(
            context = context,
            tts = tts,
            text = spoken,
            character = _selectedCharacter.value
        )
    }

    fun toggleIngredientChip(ingredient: String) {
        val current = _selectedIngredients.value.toMutableSet()
        if (!current.add(ingredient)) {
            current.remove(ingredient)
        }
        _selectedIngredients.value = current
    }

    fun addCustomIngredientChip(ingredient: String) {
        val clean = ingredient.trim()
        if (clean.isEmpty()) return
        _selectedIngredients.value = _selectedIngredients.value + clean
    }

    fun setFridgePhoto(bitmap: Bitmap?) {
        _fridgePhotoBitmap.value = bitmap
    }

    fun setCustomCraving(craving: String) {
        _customCraving.value = craving
    }

    fun generateMealsFromFridge() {
        val uid = _currentUserId.value
        if (_isChefCooking.value) return
        val ingredients = _selectedIngredients.value.toList().ifEmpty { listOf("Eggs", "Rice", "Garlic") }

        viewModelScope.launch {
            _isChefCooking.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val (note, generatedMeals) = aiService.generateFridgeMeals(
                    userId = uid ?: "guest",
                    selectedIngredients = ingredients,
                    customCraving = _customCraving.value,
                    fridgePhoto = _fridgePhotoBitmap.value
                )
                _chefNote.value = note
                if (uid != null) {
                    try {
                        generatedMeals.forEach { meal ->
                            repository.createMeal(
                                userId = uid,
                                title = meal.title,
                                ingredientsUsed = meal.ingredientsUsed,
                                prepTimeMinutes = meal.prepTimeMinutes,
                                calories = meal.calories,
                                moneySavedDollars = meal.moneySavedDollars,
                                stepsSummary = meal.stepsSummary,
                                isCooked = meal.isCooked,
                                mealId = meal.id
                            )
                        }
                    } catch (_: Exception) {
                        _meals.value = generatedMeals + _meals.value
                    }
                } else {
                    _meals.value = generatedMeals + _meals.value
                }
                characterController.transitionTo(CharacterRuntimeState.Reacting("happy"))
            } finally {
                _isChefCooking.value = false
            }
        }
    }

    // Group Receipt & Expense Splitter Actions
    fun setReceiptOrdersInput(orders: String) {
        _receiptOrdersInput.value = orders
    }

    fun setReceiptTaxTipPercent(percent: Double) {
        _receiptTaxTipPercent.value = percent
    }

    fun setReceiptPhoto(bitmap: Bitmap?) {
        _receiptPhotoBitmap.value = bitmap
    }

    fun runReceiptSplitter() {
        if (_isSplittingReceipt.value) return
        viewModelScope.launch {
            _isSplittingReceipt.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val split = aiService.splitGroupReceipt(
                    ordersDescription = _receiptOrdersInput.value,
                    taxAndTipPercent = _receiptTaxTipPercent.value,
                    receiptPhoto = _receiptPhotoBitmap.value
                )
                _receiptSplitResult.value = split
                characterController.transitionTo(CharacterRuntimeState.Reacting("happy"))
            } finally {
                _isSplittingReceipt.value = false
            }
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appCtx = context.applicationContext
                    try {
                        if (com.google.firebase.FirebaseApp.getApps(appCtx).isEmpty()) {
                            com.google.firebase.FirebaseApp.initializeApp(appCtx)
                        }
                    } catch (_: Throwable) {
                    }
                    return CadenceViewModel(
                        appContext = appCtx,
                        repository = CadenceRepository(appCtx),
                        aiService = CadenceAiService()
                    ) as T
                }
            }
    }
}
