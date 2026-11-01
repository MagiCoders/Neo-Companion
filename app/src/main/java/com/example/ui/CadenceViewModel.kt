package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.actions.ActionExecutor
import com.example.actions.ActionOutcome
import com.example.actions.Preparation
import com.example.ai.AiUnavailableException
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.UUID

class CadenceViewModel(
    private val appContext: Context,
    private val repository: CadenceRepository,
    private val aiService: CadenceAiService
) : ViewModel() {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("mochi_persistent_settings", Context.MODE_PRIVATE)

    /** Name, nickname and "about me" are personal: they live encrypted, never in plain prefs. */
    private val secure = com.example.security.SecureStore(appContext)

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

    private fun migratePersonalFieldsToSecureStore() {
        val keys = listOf("userName", "userNickname", "userAboutBio")
        if (keys.none { prefs.contains(it) }) return
        keys.forEach { k -> prefs.getString(k, null)?.let { secure.putString(k, it) } }
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }

    private fun loadSettingsFromDisk(): MochiPersistentSettings {
        migratePersonalFieldsToSecureStore()
        // v2: the always-on wake word used to default to ON and caused the mic to open and close
        // in a loop. Reset it to OFF once; the user can turn it back on in Settings.
        if (prefs.getInt("settingsSchema", 1) < 2) {
            prefs.edit().putBoolean("wakeWordEnabled", false).putInt("settingsSchema", 2).apply()
        }
        // v3: Mochi (real 3D model) becomes the default companion once.
        if (prefs.getInt("settingsSchema", 1) < 3) {
            prefs.edit().putString("selectedCharacterId", "mochi").putInt("settingsSchema", 3).apply()
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
            userName = secure.getString("userName"),
            userNickname = secure.getString("userNickname"),
            userAboutBio = secure.getString("userAboutBio"),
            preferredLanguage = prefs.getString("preferredLanguage", com.example.ai.VoiceLanguages.PHONE) ?: com.example.ai.VoiceLanguages.PHONE,
            floatingCompanionEnabled = prefs.getBoolean("floatingCompanionEnabled", true),
            wakeWordEnabled = prefs.getBoolean("wakeWordEnabled", false),
            wakePhrase = prefs.getString("wakePhrase", com.example.ai.WakePhrases.DEFAULT) ?: com.example.ai.WakePhrases.DEFAULT,
            liveVoiceChat = prefs.getBoolean("liveVoiceChat", false),
            busyWatchingVideos = prefs.getBoolean("busyWatchingVideos", true),
            busyPlayingGames = prefs.getBoolean("busyPlayingGames", true),
            busyFullScreenApps = prefs.getBoolean("busyFullScreenApps", true),
            busyBehaviorIndex = prefs.getInt("busyBehaviorIndex", 0),
            drivingAutoStart = prefs.getBoolean("drivingAutoStart", true),
            saveChatHistory = prefs.getBoolean("saveChatHistory", true),
            useVoiceToneForCheckIns = prefs.getBoolean("useVoiceToneForCheckIns", true),
            shareAnonymousUsage = prefs.getBoolean("shareAnonymousUsage", false),
            isDarkTheme = prefs.getBoolean("isDarkTheme", true),
            themeMode = prefs.getInt("themeMode", if (prefs.getBoolean("isDarkTheme", true)) 0 else 1),
            allowedPermissions = prefs.getStringSet("allowedPermissions", defaultPerms)?.toSet() ?: defaultPerms
        )
    }

    /** Voice language for recognition + speech, and the wake phrase. */
    private fun applyVoiceSettings(s: MochiPersistentSettings) {
        val tag = com.example.ai.VoiceLanguages.tagFor(s.preferredLanguage)
        voiceCommandService.setRecognitionLanguage(tag)
        voiceCommandService.setWakePhrase(s.wakePhrase)
        characterController.defaultLanguageTag = tag
    }

    fun updateSettings(transform: (MochiPersistentSettings) -> MochiPersistentSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        secure.putString("userName", updated.userName.take(100))
        secure.putString("userNickname", updated.userNickname.take(60))
        secure.putString("userAboutBio", updated.userAboutBio.take(1_000))
        voiceCommandService.setWakeWordEnabled(updated.wakeWordEnabled)
        applyVoiceSettings(updated)
        prefs.edit()
            .putString("wakePhrase", updated.wakePhrase)
            .putBoolean("liveVoiceChat", updated.liveVoiceChat)
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
            .putInt("themeMode", updated.themeMode)
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
    private val _quests = MutableStateFlow<List<QuestItem>>(emptyList())
    val quests: StateFlow<List<QuestItem>> = _quests.asStateFlow()

    fun toggleQuestCompleted(questId: String) {
        val uid = _currentUserId.value ?: return
        val quest = _quests.value.firstOrNull { it.id == questId } ?: return
        viewModelScope.launch {
            runCatching { repository.toggleQuestCompletion(uid, quest, _userProfile.value) }
                .onSuccess { if (!quest.isCompleted) characterController.react("happy") }
                .onFailure { android.util.Log.w("CadenceViewModel", "Quest update failed: ${it.javaClass.simpleName}") }
        }
    }

    fun addQuest(title: String, category: QuestCategory = QuestCategory.DAY) {
        val uid = _currentUserId.value ?: return
        val clean = title.trim().take(200)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.createQuest(userId = uid, title = clean, category = category) }
                .onFailure { android.util.Log.w("CadenceViewModel", "Quest create failed: ${it.javaClass.simpleName}") }
        }
    }

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _meals = MutableStateFlow<List<MealCreation>>(emptyList())
    val meals: StateFlow<List<MealCreation>> = _meals.asStateFlow()

    // 3 Launch Characters from CHARACTERS.md
    private val _selectedCharacter = MutableStateFlow(
        CharacterSpec.fromId(
            prefs.getString("selectedCharacterId", CharacterSpec.MOCHI.id) ?: CharacterSpec.MOCHI.id
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
    private val _currentStressPercent = MutableStateFlow(0)
    val currentStressPercent: StateFlow<Int> = _currentStressPercent.asStateFlow()

    private val _currentMoodTone = MutableStateFlow("")
    val currentMoodTone: StateFlow<String> = _currentMoodTone.asStateFlow()

    private val _currentBehavioralInsight = MutableStateFlow("")
    val currentBehavioralInsight: StateFlow<String> = _currentBehavioralInsight.asStateFlow()

    // Tools & Converter State
    private val _selectedConversionTool = MutableStateFlow(ConversionToolMode.IMAGE_TO_PDF)
    val selectedConversionTool: StateFlow<ConversionToolMode> = _selectedConversionTool.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _conversionResult = MutableStateFlow<ConversionOutputResult?>(null)
    val conversionResult: StateFlow<ConversionOutputResult?> = _conversionResult.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<CompanionChatTurn>>(emptyList())
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

    /** "Read my messages" button (driving mode): runs the real READ_MESSAGES action directly, no AI round-trip. */
    fun readMessagesAloud(context: Context, tts: TextToSpeech?) {
        val turn = CompanionChatTurn(
            id = java.util.UUID.randomUUID().toString(),
            senderIsUser = false,
            message = "",
            actionType = CompanionActionType.READ_MESSAGES
        )
        executeTurnActionNow(context, tts, turn)
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
            CompanionActionType.WHATS_PLAYING,
            CompanionActionType.READ_MESSAGES,
            CompanionActionType.REPLY_MESSAGE,
            CompanionActionType.SET_VOICE_LANGUAGE
        )
        val outcome = OmnichannelMediaBridge.autoExecuteCompanionAction(
            context = context,
            tts = tts,
            turn = turn,
            character = _selectedCharacter.value,
            onNavigateHashRoute = { route -> navigateHash(route) }
        )
        if (leavesApp && outcome is ActionOutcome.Done) {
            // A call or another app takes over: stop listening so the mic is free for it.
            voiceCommandService.endConversation("opened another app")
            voiceCommandService.stopListening()
            if (_settings.value.floatingCompanionEnabled) FloatingCompanionService.startIfEnabled(context)
        }
        reportOutcome(context, tts, outcome)
    }

    /** Adds the real result of an action to the chat and says it, unless there is nothing to say. */
    private fun reportOutcome(context: Context, tts: TextToSpeech?, outcome: ActionOutcome) {
        if (outcome is ActionOutcome.NeedsPermission) {
            _permissionRequests.tryEmit(outcome.permission)
        }
        if (outcome.spoken.isBlank()) return
        _chatHistory.value = _chatHistory.value + CompanionChatTurn(
            id = UUID.randomUUID().toString(),
            senderIsUser = false,
            message = outcome.spoken,
            spokenConfirmation = outcome.spoken,
            isActionReport = true
        )
        if (outcome !is ActionOutcome.Done || outcome.speak) {
            characterController.speakWithLipSync(context, tts, outcome.spoken, _selectedCharacter.value)
        }
    }

    // --- Live voice (Gemini Live): talk naturally, interrupt, any language ---
    val liveConversation = com.example.ai.LiveConversation(appContext, viewModelScope, object : com.example.ai.LiveConversation.Callbacks {
        override fun onUserTranscript(text: String) {
            _chatHistory.value = _chatHistory.value + CompanionChatTurn(id = UUID.randomUUID().toString(), senderIsUser = true, message = text)
        }

        override fun onCompanionTranscript(text: String) {
            _chatHistory.value = _chatHistory.value + CompanionChatTurn(id = UUID.randomUUID().toString(), senderIsUser = false, message = text)
        }

        override fun onSpeakingChanged(speaking: Boolean, level: Float) {
            characterController.setLiveSpeaking(speaking, level)
        }

        override fun onActionReady(turn: CompanionChatTurn, title: String) {
            when (turn.actionType) {
                CompanionActionType.NAVIGATE_ROUTE, CompanionActionType.WHATS_PLAYING, CompanionActionType.SPEAK_LOUD ->
                    executeTurnActionNow(appContext, null, turn)
                else -> voiceCommandService.scheduleActionWithUndoWindow(turn, durationSeconds = 3, titleOverride = title) {
                    executeTurnActionNow(appContext, null, it.turn)
                }
            }
        }

        override fun onError(message: String) {
            reportOutcome(appContext, null, ActionOutcome.Failed(message))
        }
    })
    val liveStatus: StateFlow<com.example.ai.LiveConversation.Status> = liveConversation.status

    /** Mic button: start or end a live voice conversation. */
    fun toggleLiveConversation() {
        if (liveConversation.isActive()) {
            liveConversation.stop()
            voiceCommandService.setLiveCallActive(false)
            characterController.transitionTo(CharacterRuntimeState.Idle)
            return
        }
        val uid = _currentUserId.value
        viewModelScope.launch {
            val hasCredit = uid == null || _userProfile.value.userId != uid ||
                runCatching { repository.consumeAiCreditOrThrow(uid, _userProfile.value) }.getOrDefault(true)
            if (!hasCredit) {
                reportOutcome(appContext, null, ActionOutcome.Failed("You've used today's AI energy. It refills tomorrow, or you can go Pro in Wallet."))
                return@launch
            }
            voiceCommandService.setLiveCallActive(true)
            characterController.transitionTo(CharacterRuntimeState.Listening)
            liveConversation.start(_selectedCharacter.value, _settings.value.userName)
        }
    }

    init {
        applyVoiceSettings(_settings.value)
        // Wake phrase heard: take one spoken request (or open a live call if the user chose that).
        voiceCommandService.onWakeWordOnly = {
            if (_settings.value.liveVoiceChat) {
                if (!liveConversation.isActive()) toggleLiveConversation()
            } else {
                // Answer straight away ("Yes? I'm listening."), then keep the conversation going:
                // the mic reopens after every reply until the user says bye or goes quiet.
                voiceCommandService.startConversation()
                val tts = speechEngine
                if (tts != null) characterController.speakWithLipSync(appContext, tts, wakeGreeting(), _selectedCharacter.value)
                else voiceCommandService.triggerManualMicSession(hasRecordPermission = true)
            }
        }
    }

    /** Mic button: one spoken turn by default; a live call when "Live voice chat" is on (or one is running). */
    fun onMicButton(hasRecordPermission: Boolean) {
        if (_settings.value.liveVoiceChat || liveConversation.isActive()) toggleLiveConversation()
        else voiceCommandService.triggerManualMicSession(hasRecordPermission)
    }

    override fun onCleared() {
        voiceCommandService.onWakeWordOnly = null
        liveConversation.stop()
        voiceCommandService.setLiveCallActive(false)
        super.onCleared()
    }

    /** Runtime permissions the UI should ask for (e.g. contacts when the user says "call Mom"). */
    private val _permissionRequests = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val permissionRequests: kotlinx.coroutines.flow.SharedFlow<String> = _permissionRequests

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

    private val _selectedIngredients = MutableStateFlow<Set<String>>(emptySet())
    val selectedIngredients: StateFlow<Set<String>> = _selectedIngredients.asStateFlow()

    private val _fridgePhotoBitmap = MutableStateFlow<Bitmap?>(null)
    val fridgePhotoBitmap: StateFlow<Bitmap?> = _fridgePhotoBitmap.asStateFlow()

    private val _customCraving = MutableStateFlow("")
    val customCraving: StateFlow<String> = _customCraving.asStateFlow()

    private val _chefNote = MutableStateFlow("")
    val chefNote: StateFlow<String> = _chefNote.asStateFlow()

    private val _isChefCooking = MutableStateFlow(false)
    val isChefCooking: StateFlow<Boolean> = _isChefCooking.asStateFlow()

    // Group Receipt & Expense Splitter State
    private val _receiptOrdersInput = MutableStateFlow("")
    val receiptOrdersInput: StateFlow<String> = _receiptOrdersInput.asStateFlow()

    private val _receiptTaxTipPercent = MutableStateFlow(18.0)
    val receiptTaxTipPercent: StateFlow<Double> = _receiptTaxTipPercent.asStateFlow()

    private val _receiptPhotoBitmap = MutableStateFlow<Bitmap?>(null)
    val receiptPhotoBitmap: StateFlow<Bitmap?> = _receiptPhotoBitmap.asStateFlow()

    private val _receiptSplitResult = MutableStateFlow<ReceiptSplitResult?>(null)
    val receiptSplitResult: StateFlow<ReceiptSplitResult?> = _receiptSplitResult.asStateFlow()

    private val _receiptError = MutableStateFlow<String?>(null)
    val receiptError: StateFlow<String?> = _receiptError.asStateFlow()

    private val _isSplittingReceipt = MutableStateFlow(false)
    val isSplittingReceipt: StateFlow<Boolean> = _isSplittingReceipt.asStateFlow()

    // --- Library: everything the user really made (recipes, quests, converted files) ---
    private val _libraryFiles = MutableStateFlow<List<java.io.File>>(emptyList())

    fun refreshLibraryFiles() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _libraryFiles.value = com.example.data.LibraryStore.files(appContext)
        }
    }

    val libraryEntries: StateFlow<List<com.example.data.LibraryEntry>> =
        kotlinx.coroutines.flow.combine(_meals, _quests, _libraryFiles) { meals, quests, files ->
            (meals.map { com.example.data.LibraryEntry("meal_${it.id}", com.example.data.LibraryEntry.Kind.RECIPE, it.title, "Recipe · ${it.prepTimeMinutes} min", it.createdAtMillis) } +
                quests.map { com.example.data.LibraryEntry("quest_${it.id}", com.example.data.LibraryEntry.Kind.QUEST, it.title, if (it.isCompleted) "Quest · done" else "Quest", it.createdAtMillis) } +
                files.map { com.example.data.LibraryEntry("file_${it.name}", com.example.data.LibraryEntry.Kind.FILE, it.name, "File · ${(it.length() / 1024).coerceAtLeast(1)} KB", it.lastModified(), it) })
                .sortedByDescending { it.timestampMillis }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    init {
        refreshLibraryFiles()
        viewModelScope.launch { _conversionResult.collect { if (it != null) refreshLibraryFiles() } }
    }

    private var profileJob: Job? = null
    private var questsJob: Job? = null
    private var mealsJob: Job? = null

    /** Applies Dark / Light / Auto. For Auto, [systemDark] is the phone's current mode. */
    fun applyThemeMode(mode: Int, systemDark: Boolean) {
        val dark = when (mode) { 1 -> false; 2 -> systemDark; else -> true }
        val style = if (dark) MochiVisualStyle.STUDIO_4K_OPTICAL else MochiVisualStyle.WARM_STUDIO_LUXE
        if (_selectedVisualStyle.value != style) _selectedVisualStyle.value = style
        val cur = _settings.value
        if (cur.themeMode != mode || cur.isDarkTheme != dark) {
            updateSettings { it.copy(themeMode = mode, isDarkTheme = dark) }
        }
    }

    fun setPreviewAccentCharacter(spec: CharacterSpec?) {
        _previewAccentCharacter.value = spec
    }

    fun selectVisualStyle(style: MochiVisualStyle) {
        _selectedVisualStyle.value = style
        updateSettings { it.copy(isDarkTheme = !style.isLight, themeMode = if (style.isLight) 1 else 0) }
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

    /** Sign-out on this device: wipe personal data stored locally. */
    fun clearLocalPersonalData() {
        secure.clear()
        com.example.bridge.MessageInbox.clear()
        _chatHistory.value = emptyList()
        _settings.value = _settings.value.copy(userName = "", userNickname = "", userAboutBio = "")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.example.data.LibraryStore.files(appContext).forEach { com.example.data.LibraryStore.delete(appContext, it) }
            java.io.File(appContext.cacheDir, "shared").deleteRecursively()
            refreshLibraryFiles()
        }
    }

    fun clearChatHistory() {
        _chatHistory.value = emptyList()
    }

    fun triggerCalmDeEscalation(context: Context, tts: TextToSpeech?) {
        val activeChar = _selectedCharacter.value
        val calmMsg = "Let's slow down together. Breathe in for four… hold… and out for six. Let your shoulders drop."
        val calmTurn = CompanionChatTurn(
            id = UUID.randomUUID().toString(),
            senderIsUser = false,
            message = calmMsg,
            spokenConfirmation = calmMsg
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
        viewModelScope.launch {
            val outcome = OmnichannelMediaBridge.sendAttachmentToPlatform(
                context = context,
                tts = tts,
                targetPlatform = targetPlatform,
                recipient = turn.actionRecipient,
                messageText = turn.actionPayload.ifBlank { turn.message },
                mediaType = mediaType
            )
            reportOutcome(context, tts, outcome)
        }
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
        questsJob?.cancel()

        if (userId == null) {
            _userProfile.value = UserProfile()
            _quests.value = emptyList()
            _meals.value = emptyList()
            return
        }

        if (!displayName.isNullOrBlank() && _settings.value.userName.isBlank()) {
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
                var refillChecked = false
                repository.observeUserProfile(userId).collectLatest { profile ->
                    if (profile != null) {
                        _userProfile.value = profile
                        if (!refillChecked) {
                            refillChecked = true
                            repository.refillDailyCreditsIfDue(userId, profile)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        questsJob = viewModelScope.launch {
            try {
                repository.observeQuests(userId).collectLatest { _quests.value = it }
            } catch (_: Exception) {
            }
        }

        mealsJob = viewModelScope.launch {
            try {
                repository.observeMeals(userId).collectLatest { list ->
                    _meals.value = list
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * One user message (typed or spoken) → the companion's reply, and an action if one was asked for.
     * Actions are checked first (real contact, permission) and only then get the 3-second countdown.
     */
    fun sendMessageToMochi(
        context: Context,
        tts: TextToSpeech?,
        userText: String,
        /** True when the text came from the microphone: she may listen again after a question. */
        fromVoice: Boolean = false
    ) {
        val clean = userText.trim().take(4_000)
        if (clean.isEmpty() || _isCompanionThinking.value) {
            voiceCommandService.clearAwaitingReply()
            return
        }

        if (STOP_WORDS.matches(clean)) {
            cancelPendingVoiceAction()
            _chatHistory.value.lastOrNull { !it.senderIsUser }?.let { markTurnCancelled(it.id) }
            tts?.stop()
            characterController.transitionTo(CharacterRuntimeState.Idle)
            voiceCommandService.clearAwaitingReply()
            return
        }

        val uid = _currentUserId.value
        val activeChar = _selectedCharacter.value
        val history = _chatHistory.value
        _chatHistory.value = history + CompanionChatTurn(
            id = UUID.randomUUID().toString(),
            senderIsUser = true,
            message = clean
        )

        viewModelScope.launch {
            _isCompanionThinking.value = true
            characterController.transitionTo(CharacterRuntimeState.Thinking)
            // Spoken turns: if the answer takes a moment, say so instead of leaving silence.
            val fillerJob = if (fromVoice) launch {
                kotlinx.coroutines.delay(FILLER_AFTER_MS)
                characterController.speakWithLipSync(context, tts, thinkingFiller(), activeChar)
            } else null
            try {
                // The energy check and the AI call run at the same time; the answer is only used
                // if the check passes. Offline/Firestore hiccups must not block chatting.
                val creditCheck = async {
                    uid == null || _userProfile.value.userId != uid ||
                        runCatching { repository.consumeAiCreditOrThrow(uid, _userProfile.value) }.getOrDefault(true)
                }
                val aiResult = runCatching {
                    voiceCommandService.processNaturalLanguageCommand(
                        commandText = clean,
                        companion = activeChar,
                        userName = _settings.value.userName,
                        history = history.filterNot { it.isActionReport },
                        preferredLanguage = com.example.ai.VoiceLanguages.englishName(
                            com.example.ai.VoiceLanguages.tagFor(_settings.value.preferredLanguage)
                        )
                    )
                }
                fillerJob?.cancel()
                if (!creditCheck.await()) {
                    reportOutcome(context, tts, ActionOutcome.Failed("You've used today's AI energy. It refills tomorrow, or you can go Pro in Wallet."))
                    characterController.transitionTo(CharacterRuntimeState.Idle)
                    return@launch
                }
                val aiTurn = aiResult.getOrElse { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val message = (e as? AiUnavailableException)?.message ?: "Something went wrong."
                    reportOutcome(context, tts, ActionOutcome.Failed(message))
                    characterController.transitionTo(CharacterRuntimeState.Idle)
                    return@launch
                }

                _currentStressPercent.value = aiTurn.angerOrStressPercent
                _currentMoodTone.value = aiTurn.detectedTone

                // Check the action before announcing it, so she never says "Calling Mom" for a contact that doesn't exist.
                val preparation = when (aiTurn.actionType) {
                    CompanionActionType.NONE, CompanionActionType.SPEAK_LOUD,
                    CompanionActionType.NAVIGATE_ROUTE, CompanionActionType.WHATS_PLAYING,
                    CompanionActionType.SET_VOICE_LANGUAGE -> null
                    else -> ActionExecutor(context).prepare(aiTurn)
                }
                if (preparation is Preparation.Blocked) {
                    val blockedTurn = aiTurn.copy(
                        message = preparation.outcome.spoken,
                        spokenConfirmation = preparation.outcome.spoken,
                        actionType = CompanionActionType.NONE,
                        isActionReport = true
                    )
                    _chatHistory.value = _chatHistory.value + blockedTurn
                    if (preparation.outcome is ActionOutcome.NeedsPermission) {
                        _permissionRequests.tryEmit((preparation.outcome as ActionOutcome.NeedsPermission).permission)
                    }
                    characterController.speakWithLipSync(context, tts, blockedTurn.message, activeChar, aiTurn.detectedLanguage)
                    return@launch
                }

                _chatHistory.value = _chatHistory.value + aiTurn
                // "Switch to Arabic": change the voice language first, so her reply is spoken in it
                // and the next thing the user says is recognised in it.
                var replyLanguage = aiTurn.detectedLanguage
                if (aiTurn.actionType == CompanionActionType.SET_VOICE_LANGUAGE) {
                    com.example.ai.VoiceLanguages.labelForTag(aiTurn.actionPayload)?.let { label ->
                        updateSettings { it.copy(preferredLanguage = label) }
                        replyLanguage = aiTurn.actionPayload
                        com.example.ai.VoiceLog.add(com.example.ai.VoiceLog.Kind.AI, "Voice language → $label")
                    }
                }
                characterController.speakWithLipSync(context, tts, aiTurn.message, activeChar, replyLanguage)

                when (aiTurn.actionType) {
                    CompanionActionType.NONE, CompanionActionType.SPEAK_LOUD,
                    CompanionActionType.SET_VOICE_LANGUAGE -> Unit
                    CompanionActionType.NAVIGATE_ROUTE, CompanionActionType.WHATS_PLAYING,
                    CompanionActionType.READ_MESSAGES ->
                        executeTurnActionNow(context, tts, aiTurn)
                    else -> {
                        val ready = (preparation as Preparation.Ready).action
                        voiceCommandService.scheduleActionWithUndoWindow(
                            turn = aiTurn,
                            durationSeconds = 3,
                            titleOverride = ready.title
                        ) { confirmed ->
                            executeTurnActionNow(context, tts, confirmed.turn)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("CadenceViewModel", "Message handling failed: ${e.javaClass.simpleName}")
                reportOutcome(context, tts, ActionOutcome.Failed("Something went wrong on my side. Please try again."))
            } finally {
                fillerJob?.cancel()
                _isCompanionThinking.value = false
                voiceCommandService.clearAwaitingReply()
            }
        }
    }

    /** The app's text-to-speech engine, handed over by the activity once it is ready. */
    @Volatile var speechEngine: TextToSpeech? = null

    /** A short spoken answer to the wake phrase, in the user's voice language. */
    private fun wakeGreeting(): String {
        val tag = com.example.ai.VoiceLanguages.tagFor(_settings.value.preferredLanguage).substringBefore('-')
        val options = WAKE_GREETINGS[tag] ?: WAKE_GREETINGS.getValue("en")
        return options.random()
    }

    /** A short "let me check" in the user's voice language. */
    private fun thinkingFiller(): String {
        val tag = com.example.ai.VoiceLanguages.tagFor(_settings.value.preferredLanguage).substringBefore('-')
        return THINKING_FILLERS[tag] ?: THINKING_FILLERS.getValue("en")
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
        val ingredients = _selectedIngredients.value.toList()

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
            } catch (e: AiUnavailableException) {
                _chefNote.value = e.message.orEmpty()
                characterController.transitionTo(CharacterRuntimeState.Idle)
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
                _receiptError.value = null
                characterController.transitionTo(CharacterRuntimeState.Reacting("happy"))
            } catch (e: AiUnavailableException) {
                _receiptSplitResult.value = null
                _receiptError.value = e.message
                characterController.transitionTo(CharacterRuntimeState.Idle)
            } finally {
                _isSplittingReceipt.value = false
            }
        }
    }

    companion object {
        private val WAKE_GREETINGS = mapOf(
            "en" to listOf("Yes? I'm listening.", "Hi! I'm here.", "Hey! What's up?"),
            "ar" to listOf("نعم؟ أنا أسمعك.", "هلا! تفضل.", "أهلين، شلون أكدر أساعدك؟"),
            "fa" to listOf("بله؟ گوش می‌دم.", "سلام! در خدمتم."),
            "fr" to listOf("Oui ? Je t'écoute."),
            "es" to listOf("¿Sí? Te escucho."),
            "de" to listOf("Ja? Ich höre."),
            "it" to listOf("Sì? Ti ascolto."),
            "pt" to listOf("Sim? Estou ouvindo."),
            "tr" to listOf("Efendim? Dinliyorum."),
            "ru" to listOf("Да? Слушаю."),
            "ja" to listOf("はい、聞いてますよ。"),
            "ko" to listOf("네? 듣고 있어요."),
            "zh" to listOf("在呢，你说。"),
            "hi" to listOf("हाँ? सुन रही हूँ।"),
            "ur" to listOf("جی؟ سن رہی ہوں۔"),
            "id" to listOf("Ya? Aku dengar.")
        )
        /** Say a filler if the AI hasn't answered a spoken turn after this long. */
        private const val FILLER_AFTER_MS = 1_800L
        private val THINKING_FILLERS = mapOf(
            "en" to "Let me check.",
            "ar" to "لحظة، دعني أتحقق.",
            "fa" to "یک لحظه، بذار ببینم.",
            "fr" to "Je regarde.",
            "es" to "Déjame ver.",
            "de" to "Moment, ich schaue nach.",
            "it" to "Fammi controllare.",
            "pt" to "Deixa eu ver.",
            "tr" to "Bir bakayım.",
            "ru" to "Сейчас посмотрю.",
            "ja" to "ちょっと確認しますね。",
            "ko" to "잠깐 확인해 볼게요.",
            "zh" to "我看一下。",
            "hi" to "एक सेकंड, देखती हूँ।",
            "ur" to "ایک لمحہ، دیکھتی ہوں۔",
            "id" to "Sebentar, aku cek dulu."
        )
        private val STOP_WORDS = Regex("(?i)^(stop|cancel|never ?mind|undo|توقف|الغاء|إلغاء|بس|لغو|annule|para|stopp)[.!]?$")

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
