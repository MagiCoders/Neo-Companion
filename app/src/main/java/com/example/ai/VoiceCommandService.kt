package com.example.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.CompanionActionType
import com.example.data.CompanionChatTurn
import com.example.data.MochiHashRoutes
import com.example.data.QuestCategory
import com.example.ui.character.CharacterSpec
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Represents a voice-triggered or text-triggered action that enters a 3-second
 * Undo/Cancel countdown window before executing navigation, WhatsApp, YouTube,
 * Browser, or media playback.
 */
data class PendingVoiceAction(
    val id: String = UUID.randomUUID().toString(),
    val turn: CompanionChatTurn,
    val title: String,
    val subtitle: String,
    val actionType: CompanionActionType,
    val targetRecipient: String,
    val targetPayload: String,
    val totalSeconds: Int = 3,
    val remainingSeconds: Int = 3,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * VoiceCommandService:
 * 1. Uses Firebase AI Logic (`Firebase.ai.generativeModel("gemini-3.8-flash")`) for natural language
 *    processing with structured JSON schema output.
 * 2. Maintains a global, persistent `'Hi Mochi'` wake-word listening loop across all app screens
 *    whenever `wakeWordEnabled` is active.
 * 3. Provides an instant intent-based router (`routeInstantIntent`) that handles WhatsApp (calls,
 *    contacts, chat & voice messages), YouTube (search, play, what's playing), Browser search,
 *    Social Media, Media playback, and HashRouter screen navigation with zero delay.
 * 4. Manages a 3-second `'Undo/Cancel'` countdown state (`pendingAction`) so users can abort any
 *    voice-triggered navigation or media/app launch by tapping Undo/Cancel or saying "stop" / "cancel".
 */
class VoiceCommandService(
    private val appContext: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "VoiceCommandService"
        private val WAKE_WORD_REGEX = Regex(
            "(?i)\\b(hi|hey|hello|okay|ok)\\s+(mochi|elena|kaito|marco)\\b[,\\s]*"
        )
        private val ABORT_COMMAND_REGEX = Regex(
            "(?i)^(stop|cancel|undo|abort|wait|don't|do not|never mind|nevermind)$"
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var countdownJob: Job? = null
    private var restartListeningJob: Job? = null

    private val _isWakeWordEnabled = MutableStateFlow(true)
    val isWakeWordEnabled: StateFlow<Boolean> = _isWakeWordEnabled.asStateFlow()

    private val _isActivelyListening = MutableStateFlow(false)
    val isActivelyListening: StateFlow<Boolean> = _isActivelyListening.asStateFlow()

    private val _wakeWordArmed = MutableStateFlow(false)
    val wakeWordArmed: StateFlow<Boolean> = _wakeWordArmed.asStateFlow()

    private val _livePartialTranscript = MutableStateFlow("")
    val livePartialTranscript: StateFlow<String> = _livePartialTranscript.asStateFlow()

    private val _micRmsDb = MutableStateFlow(0f)
    val micRmsDb: StateFlow<Float> = _micRmsDb.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingVoiceAction?>(null)
    val pendingAction: StateFlow<PendingVoiceAction?> = _pendingAction.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var onCommandRecognizedCallback: ((String) -> Unit)? = null
    private var onExecutePendingActionCallback: ((PendingVoiceAction) -> Unit)? = null

    private val nlpSchema = Schema.obj(
        properties = mapOf(
            "reply" to Schema.string(description = "Warm, concise spoken response (1-2 sentences)."),
            "spokenConfirmation" to Schema.string(description = "Short confirmation read aloud by TTS."),
            "actionType" to Schema.enumeration(
                values = listOf(
                    "NONE",
                    "WHATSAPP",
                    "WHATSAPP_CALL",
                    "WHATSAPP_CONTACTS",
                    "WHATSAPP_READ_CHAT",
                    "TELEGRAM",
                    "SIM_CALL",
                    "SOCIAL_CALL",
                    "SOCIAL_SEARCH",
                    "YOUTUBE",
                    "WHATS_PLAYING",
                    "BROWSER_SEARCH",
                    "OPEN_MEDIA",
                    "NAVIGATE_ROUTE",
                    "SPEAK_LOUD"
                ),
                description = "Resolved intent type for the user's command."
            ),
            "actionRecipient" to Schema.string(description = "Contact name, platform, or route name."),
            "actionPayload" to Schema.string(description = "Search query, message body, or hash route.")
        )
    )

    private val nlpModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.25f
                responseMimeType = "application/json"
                responseSchema = nlpSchema
            }
        )
    }

    fun bindCallbacks(
        onCommandRecognized: (String) -> Unit,
        onExecutePendingAction: (PendingVoiceAction) -> Unit
    ) {
        onCommandRecognizedCallback = onCommandRecognized
        onExecutePendingActionCallback = onExecutePendingAction
    }

    /**
     * Strips wake-word prefixes ("Hi Mochi", "Hey Mochi", "Hi Elena", "Hey Marco", "Hi Kaito")
     * from spoken input and returns the clean natural language command.
     */
    fun extractWakeWordOrCommand(rawSpoken: String, companion: CharacterSpec): String {
        val cleaned = rawSpoken.replace(WAKE_WORD_REGEX, "").trim()
        return cleaned.ifEmpty {
            "Hi ${companion.displayName.substringAfter(" ")}, I'm listening!"
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        _isWakeWordEnabled.value = enabled
        if (!enabled) {
            stopListening()
        }
    }

    /**
     * Enables or disables global persistent `'Hi Mochi'` wake-word listening.
     */
    fun setWakeWordListeningEnabled(enabled: Boolean, hasRecordPermission: Boolean) {
        _isWakeWordEnabled.value = enabled
        if (enabled && hasRecordPermission) {
            startPersistentListeningLoop()
        } else if (!enabled) {
            stopListening()
        }
    }

    /**
     * Triggers an immediate manual or wake-word voice listening session on the main thread.
     */
    fun triggerManualMicSession(hasRecordPermission: Boolean) {
        if (!hasRecordPermission) return
        _wakeWordArmed.value = true
        startPersistentListeningLoop(forceImmediateCommandCapture = true)
    }

    fun startPersistentListeningLoop(forceImmediateCommandCapture: Boolean = false) {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                    Log.w(TAG, "SpeechRecognizer not available on this device.")
                    return@post
                }
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                        setRecognitionListener(createRecognitionListener())
                    }
                }
                if (forceImmediateCommandCapture) {
                    _wakeWordArmed.value = true
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer?.startListening(intent)
                _isActivelyListening.value = true
            } catch (e: Exception) {
                Log.w(TAG, "Could not start SpeechRecognizer: ${e.message}")
                _isActivelyListening.value = false
                scheduleListeningRestart(1500L)
            }
        }
    }

    fun stopListening() {
        restartListeningJob?.cancel()
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Throwable) {
            }
            _isActivelyListening.value = false
            _wakeWordArmed.value = false
            _livePartialTranscript.value = ""
        }
    }

    private fun scheduleListeningRestart(delayMs: Long = 900L) {
        restartListeningJob?.cancel()
        if (!_isWakeWordEnabled.value) return
        restartListeningJob = scope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (_isWakeWordEnabled.value && !_isActivelyListening.value) {
                startPersistentListeningLoop(forceImmediateCommandCapture = false)
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isActivelyListening.value = true
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            _micRmsDb.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _isActivelyListening.value = false
        }

        override fun onError(error: Int) {
            _isActivelyListening.value = false
            // Automatically keep the global 'Hi Mochi' wake-word loop alive
            if (_isWakeWordEnabled.value) {
                scheduleListeningRestart(1100L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isNotEmpty()) {
                _livePartialTranscript.value = partial
                // Check if user said "stop", "cancel", or "undo" during an active 3s countdown
                if (_pendingAction.value != null && ABORT_COMMAND_REGEX.matches(partial)) {
                    cancelPendingAction()
                    return
                }
                if (WAKE_WORD_REGEX.containsMatchIn(partial)) {
                    _wakeWordArmed.value = true
                }
            }
        }

        override fun onResults(results: Bundle?) {
            _isActivelyListening.value = false
            val rawSpoken = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            _livePartialTranscript.value = ""

            if (rawSpoken.isNotEmpty()) {
                if (_pendingAction.value != null && ABORT_COMMAND_REGEX.matches(rawSpoken)) {
                    cancelPendingAction()
                } else {
                    val hasWakePhrase = WAKE_WORD_REGEX.containsMatchIn(rawSpoken)
                    val cleanedCommand = rawSpoken.replace(WAKE_WORD_REGEX, "").trim()
                    if (hasWakePhrase || _wakeWordArmed.value || _isWakeWordEnabled.value) {
                        _wakeWordArmed.value = false
                        val finalCommand = cleanedCommand.ifEmpty {
                            "Hi Mochi, I'm listening"
                        }
                        onCommandRecognizedCallback?.invoke(finalCommand)
                    }
                }
            }

            if (_isWakeWordEnabled.value) {
                scheduleListeningRestart(700L)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Instant Intent-Based Router + Firebase AI NLP Processor:
     * 1. Immediately checks deterministic local rules (<10ms) for WhatsApp, YouTube, Browser,
     *    Social Media, Media playback, and HashRouter navigation.
     * 2. If matched locally, returns immediately so the 3-second Undo/Cancel pill appears with
     *    zero delay.
     * 3. Otherwise invokes Firebase AI (`gemini-3.8-flash`) with a 60s timeout to classify
     *    complex natural language requests into structured `CompanionChatTurn` intents.
     */
    suspend fun processNaturalLanguageCommand(
        commandText: String,
        companion: CharacterSpec
    ): CompanionChatTurn = withContext(Dispatchers.Default) {
        val trimmed = commandText.trim()
        // 1. Check abort / undo intent first
        if (ABORT_COMMAND_REGEX.matches(trimmed)) {
            cancelPendingAction()
            return@withContext CompanionChatTurn(
                id = UUID.randomUUID().toString(),
                senderIsUser = false,
                message = "Cancelled. I stopped the action.",
                spokenConfirmation = "Cancelled.",
                actionType = CompanionActionType.NONE
            )
        }

        // 2. Zero-delay local intent router for WhatsApp, YouTube, Browser, Social, Media, and Routes
        OmnichannelMediaBridge.parseInstantVoiceCommand(
            userMessage = trimmed,
            companionName = companion.displayName
        )?.let { instantTurn ->
            return@withContext instantTurn
        }

        // 3. Firebase AI Logic (`gemini-3.8-flash`) structured NLP fallback
        try {
            val prompt = """
                You are ${companion.displayName}, a voice-first Android companion in MochiLife.
                User command: "$trimmed"
                Classify the user's request into the appropriate actionType:
                - WHATSAPP_CALL: calling a contact on WhatsApp
                - WHATSAPP_CONTACTS: opening WhatsApp contact list
                - WHATSAPP_READ_CHAT: opening WhatsApp chat for a contact and reading their voice/text message
                - WHATSAPP: sending a WhatsApp message to a contact
                - YOUTUBE: opening YouTube or searching for a YouTube video
                - WHATS_PLAYING: telling the user what song/video is playing right now
                - BROWSER_SEARCH: searching in the web browser or opening a website
                - SOCIAL_SEARCH: checking posts or searching news on Instagram, X, TikTok, Reddit, or social media
                - OPEN_MEDIA: playing music, sounds, or videos
                - NAVIGATE_ROUTE: switching to an internal app route (${MochiHashRoutes.STAGE}, ${MochiHashRoutes.PANTRY}, ${MochiHashRoutes.QUESTS}, ${MochiHashRoutes.TOOLS}, ${MochiHashRoutes.LIBRARY}, ${MochiHashRoutes.PROFILE}, ${MochiHashRoutes.DRIVING})
                - NONE: general conversation
            """.trimIndent()

            val response = withTimeout(60_000L) {
                nlpModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Empty Firebase AI response")
            val json = JSONObject(rawJson)
            val actionName = json.optString("actionType", "NONE").uppercase()
            val resolvedType = CompanionActionType.entries.firstOrNull { it.name == actionName }
                ?: CompanionActionType.NONE
            val reply = json.optString("reply", "On it right away.")
            val spoken = json.optString("spokenConfirmation", reply)
            val recipient = json.optString("actionRecipient", "")
            val payload = json.optString("actionPayload", trimmed)

            CompanionChatTurn(
                id = UUID.randomUUID().toString(),
                senderIsUser = false,
                message = reply,
                spokenConfirmation = spoken,
                actionType = resolvedType,
                actionRecipient = recipient,
                actionPayload = payload,
                suggestedQuestCategory = QuestCategory.DAY
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI NLP timed out", e)
            _lastError.value = "Voice AI request timed out."
            OmnichannelMediaBridge.parseInstantVoiceCommand(trimmed, companion.displayName)
                ?: CompanionChatTurn(
                    id = UUID.randomUUID().toString(),
                    senderIsUser = false,
                    message = "I heard \"$trimmed\". Let me help with that right away.",
                    spokenConfirmation = "Let me help with that right away.",
                    actionType = CompanionActionType.NONE
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI NLP error: ${e.message}", e)
            _lastError.value = e.localizedMessage
            OmnichannelMediaBridge.parseInstantVoiceCommand(trimmed, companion.displayName)
                ?: CompanionChatTurn(
                    id = UUID.randomUUID().toString(),
                    senderIsUser = false,
                    message = "Got it! Tell me if you'd like me to open WhatsApp, YouTube, or search the web for \"$trimmed\".",
                    spokenConfirmation = "Got it! I'm ready.",
                    actionType = CompanionActionType.NONE
                )
        }
    }

    /**
     * Starts the 3-second Undo/Cancel overlay countdown for any actionable voice command
     * (navigation, WhatsApp call/chat/contacts, YouTube, Browser, Social Media, or Media playback).
     */
    fun scheduleActionWithUndoWindow(
        turn: CompanionChatTurn,
        durationSeconds: Int = 3,
        onExecuteConfirmed: ((PendingVoiceAction) -> Unit)? = null
    ) {
        if (turn.actionType == CompanionActionType.NONE || turn.actionType == CompanionActionType.SPEAK_LOUD) {
            return
        }
        countdownJob?.cancel()
        if (onExecuteConfirmed != null) {
            onExecutePendingActionCallback = onExecuteConfirmed
        }
        val title = when (turn.actionType) {
            CompanionActionType.WHATSAPP_CALL -> "WhatsApp Call · ${turn.actionRecipient.ifBlank { "Contact" }}"
            CompanionActionType.WHATSAPP_CONTACTS -> "Opening WhatsApp Contacts"
            CompanionActionType.WHATSAPP_READ_CHAT -> "Reading ${turn.actionRecipient.ifBlank { "WhatsApp" }} voice chat"
            CompanionActionType.WHATSAPP -> "Messaging ${turn.actionRecipient.ifBlank { "Contact" }} on WhatsApp"
            CompanionActionType.YOUTUBE -> "Opening YouTube · ${turn.actionPayload.ifBlank { "Search" }}"
            CompanionActionType.WHATS_PLAYING -> "Checking what is playing"
            CompanionActionType.BROWSER_SEARCH -> "Searching Browser · ${turn.actionPayload.ifBlank { "Web" }}"
            CompanionActionType.SOCIAL_SEARCH -> "Opening ${turn.actionRecipient.ifBlank { "Social Media" }}"
            CompanionActionType.OPEN_MEDIA -> "Playing ${turn.actionRecipient.ifBlank { "Media" }}"
            CompanionActionType.NAVIGATE_ROUTE -> "Navigating to ${turn.actionRecipient.ifBlank { "Screen" }}"
            CompanionActionType.SIM_CALL, CompanionActionType.SOCIAL_CALL -> "Calling ${turn.actionRecipient.ifBlank { "Contact" }}"
            CompanionActionType.TELEGRAM -> "Opening Telegram · ${turn.actionRecipient}"
            else -> turn.actionType.label
        }
        val safeTotal = durationSeconds.coerceAtLeast(1)
        val subtitle = "Executing in ${safeTotal}s · Tap Undo or say \"stop\" to cancel"

        val pending = PendingVoiceAction(
            id = turn.id,
            turn = turn,
            title = title,
            subtitle = subtitle,
            actionType = turn.actionType,
            targetRecipient = turn.actionRecipient,
            targetPayload = turn.actionPayload,
            totalSeconds = safeTotal,
            remainingSeconds = safeTotal
        )
        _pendingAction.value = pending

        countdownJob = scope.launch(Dispatchers.Main) {
            var rem = safeTotal
            while (rem > 0 && isActive) {
                delay(1000L)
                rem -= 1
                val current = _pendingAction.value
                if (current == null || current.id != pending.id) return@launch
                _pendingAction.value = current.copy(
                    remainingSeconds = rem,
                    subtitle = if (rem > 0) {
                        "Executing in ${rem}s · Tap Undo or say \"stop\" to cancel"
                    } else {
                        "Launching now..."
                    }
                )
            }
            val finalPending = _pendingAction.value
            if (finalPending != null && finalPending.id == pending.id) {
                _pendingAction.value = null
                onExecutePendingActionCallback?.invoke(finalPending)
            }
        }
    }

    /**
     * Immediately aborts the pending voice action before the 3-second timer completes.
     */
    fun cancelPendingAction(): PendingVoiceAction? {
        countdownJob?.cancel()
        val cancelled = _pendingAction.value
        _pendingAction.value = null
        return cancelled
    }

    /**
     * Immediately executes the pending voice action without waiting for the remaining countdown.
     */
    fun executePendingActionImmediately(): PendingVoiceAction? {
        countdownJob?.cancel()
        val current = _pendingAction.value ?: return null
        _pendingAction.value = null
        onExecutePendingActionCallback?.invoke(current)
        return current
    }

    /**
     * Executes the resolved intent via `OmnichannelMediaBridge` or internal HashRouter callback.
     */
    fun executeActionIntent(
        context: Context,
        tts: TextToSpeech?,
        turn: CompanionChatTurn,
        companion: CharacterSpec,
        onNavigateRoute: (String) -> Unit
    ) {
        OmnichannelMediaBridge.autoExecuteCompanionAction(
            context = context,
            tts = tts,
            turn = turn,
            character = companion,
            onNavigateHashRoute = onNavigateRoute
        )
    }

    fun release() {
        countdownJob?.cancel()
        restartListeningJob?.cancel()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Throwable) {
            }
        }
    }
}
