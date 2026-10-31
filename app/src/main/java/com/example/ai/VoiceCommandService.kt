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
import kotlinx.coroutines.SupervisorJob
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
            "(?i)\\b(hi|hey|hello|okay|ok)\\s+(mochi|elena|kaito|marco)\\b[,.!?\\s]*"
        )
        private val ABORT_COMMAND_REGEX = Regex(
            "(?i)^(stop|cancel|undo|abort|wait|don't|do not|never mind|nevermind)[.!]?$"
        )

        /** Pause between two passive wake-word sessions (silence → restart). */
        private const val WAKE_RESTART_MS = 1_500L
        /** After a hard error the wait doubles each time, up to this cap. */
        private const val MAX_BACKOFF_MS = 30_000L
        /** After this many hard errors in a row the wake loop stops until something changes. */
        private const val MAX_HARD_ERRORS = 6
        /** Safety net: stop waiting for a reply after this long. */
        private const val AWAIT_REPLY_TIMEOUT_MS = 20_000L

        @Volatile
        private var INSTANCE: VoiceCommandService? = null

        /**
         * One voice engine per process. The app UI and the floating overlay share it so only one
         * SpeechRecognizer ever owns the microphone (several recognizers fighting over the mic was
         * the cause of the "opening and closing" loop).
         */
        fun shared(context: Context): VoiceCommandService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoiceCommandService(
                    context.applicationContext,
                    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                ).also { INSTANCE = it }
            }

        /** Called by anything that makes the companion talk, so the mic never hears it. */
        fun notifyAssistantSpeaking(speaking: Boolean) {
            INSTANCE?.setAssistantSpeaking(speaking)
        }
    }

    private enum class SessionKind { WAKE, COMMAND }

    private class CallbackSet(
        val onCommand: (String) -> Unit,
        val onExecutePending: (PendingVoiceAction) -> Unit
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var countdownJob: Job? = null
    private var restartListeningJob: Job? = null
    private var awaitReplyJob: Job? = null

    // ---- Mic arbitration state (main thread only) ----
    private var hasRecordPermission = false
    private val hosts = linkedSetOf<String>()
    private var assistantSpeaking = false
    private var awaitingReply = false
    private var sessionKind: SessionKind? = null
    private var hardErrorCount = 0
    private var wakeLoopSuspended = false

    private val _isWakeWordEnabled = MutableStateFlow(false)
    val isWakeWordEnabled: StateFlow<Boolean> = _isWakeWordEnabled.asStateFlow()

    /** True only while a tap-to-talk (command) session is listening — drives the "Listening…" UI. */
    private val _isActivelyListening = MutableStateFlow(false)
    val isActivelyListening: StateFlow<Boolean> = _isActivelyListening.asStateFlow()

    /** True while the passive "Hey Elena" loop is running quietly in the background. */
    private val _isWakeLoopRunning = MutableStateFlow(false)
    val isWakeLoopRunning: StateFlow<Boolean> = _isWakeLoopRunning.asStateFlow()

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

    /** Last bound owner wins (the overlay sits on top of the app while it is showing). */
    private val callbackStack = ArrayList<Pair<String, CallbackSet>>()
    private var pendingExecuteOverride: ((PendingVoiceAction) -> Unit)? = null

    private val onCommandRecognizedCallback: ((String) -> Unit)?
        get() = callbackStack.lastOrNull()?.second?.onCommand

    private val onExecutePendingActionCallback: ((PendingVoiceAction) -> Unit)?
        get() = pendingExecuteOverride ?: callbackStack.lastOrNull()?.second?.onExecutePending

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

    // ------------------------------------------------------------------------------------------
    // Callback binding
    // ------------------------------------------------------------------------------------------

    fun bindCallbacks(
        onCommandRecognized: (String) -> Unit,
        onExecutePendingAction: (PendingVoiceAction) -> Unit
    ) = bindCallbacks("app", onCommandRecognized, onExecutePendingAction)

    fun bindCallbacks(
        owner: String,
        onCommandRecognized: (String) -> Unit,
        onExecutePendingAction: (PendingVoiceAction) -> Unit
    ) {
        runOnMain {
            callbackStack.removeAll { it.first == owner }
            callbackStack.add(owner to CallbackSet(onCommandRecognized, onExecutePendingAction))
        }
    }

    fun unbindCallbacks(owner: String) {
        runOnMain { callbackStack.removeAll { it.first == owner } }
    }

    /**
     * Strips wake-word prefixes ("Hi Mochi", "Hey Elena", …) and returns the clean command.
     */
    fun extractWakeWordOrCommand(rawSpoken: String, companion: CharacterSpec): String {
        val cleaned = rawSpoken.replace(WAKE_WORD_REGEX, "").trim()
        return cleaned.ifEmpty {
            "Hi ${companion.displayName.substringAfter(" ")}, I'm listening!"
        }
    }

    // ------------------------------------------------------------------------------------------
    // Public controls
    // ------------------------------------------------------------------------------------------

    fun setWakeWordEnabled(enabled: Boolean) {
        runOnMain {
            if (_isWakeWordEnabled.value != enabled) {
                _isWakeWordEnabled.value = enabled
                wakeLoopSuspended = false
                hardErrorCount = 0
            }
            reevaluate()
        }
    }

    /** Enables or disables the passive "Hey Elena" wake-word loop. */
    fun setWakeWordListeningEnabled(enabled: Boolean, hasRecordPermission: Boolean) {
        runOnMain {
            this.hasRecordPermission = hasRecordPermission
            if (_isWakeWordEnabled.value != enabled) {
                _isWakeWordEnabled.value = enabled
                wakeLoopSuspended = false
                hardErrorCount = 0
            }
            reevaluate()
        }
    }

    fun updateRecordPermission(granted: Boolean) {
        runOnMain {
            if (hasRecordPermission != granted) {
                hasRecordPermission = granted
                wakeLoopSuspended = false
                hardErrorCount = 0
            }
            reevaluate()
        }
    }

    /**
     * A "host" is something that is allowed to use the mic right now: the visible app
     * (foreground) or the floating overlay (foreground service with microphone type).
     * With no host, the mic is released — Android blocks background mic access anyway and
     * retrying it was part of the restart storm.
     */
    fun acquireHost(tag: String) {
        runOnMain {
            if (hosts.add(tag)) {
                wakeLoopSuspended = false
                hardErrorCount = 0
            }
            reevaluate()
        }
    }

    fun releaseHost(tag: String) {
        runOnMain {
            hosts.remove(tag)
            if (hosts.isEmpty()) {
                cancelSession(destroy = true)
            }
            reevaluate()
        }
    }

    /** Tap-to-talk. Tapping again while listening stops it. */
    fun triggerManualMicSession(hasRecordPermission: Boolean) {
        runOnMain {
            this.hasRecordPermission = hasRecordPermission
            if (!hasRecordPermission) return@runOnMain
            if (sessionKind == SessionKind.COMMAND) {
                cancelSession(destroy = false)
                reevaluate(delayMs = WAKE_RESTART_MS)
                return@runOnMain
            }
            wakeLoopSuspended = false
            hardErrorCount = 0
            awaitingReply = false
            startSession(SessionKind.COMMAND)
        }
    }

    /** Kept for older call sites: starts a command session when forced, otherwise re-checks the wake loop. */
    fun startPersistentListeningLoop(forceImmediateCommandCapture: Boolean = false) {
        runOnMain {
            if (forceImmediateCommandCapture) startSession(SessionKind.COMMAND) else reevaluate()
        }
    }

    fun stopListening() {
        runOnMain {
            restartListeningJob?.cancel()
            cancelSession(destroy = false)
        }
    }

    /** The app finished handling the last command (reply shown, speech queued or skipped). */
    fun clearAwaitingReply() {
        runOnMain {
            awaitReplyJob?.cancel()
            if (awaitingReply) {
                awaitingReply = false
                reevaluate(delayMs = 700L)
            }
        }
    }

    fun setAssistantSpeaking(speaking: Boolean) {
        runOnMain {
            if (assistantSpeaking == speaking) return@runOnMain
            assistantSpeaking = speaking
            if (speaking) {
                awaitingReply = false
                awaitReplyJob?.cancel()
                restartListeningJob?.cancel()
                cancelSession(destroy = false)
            } else {
                // Give the speaker a moment to go quiet before the mic opens again.
                reevaluate(delayMs = 700L)
            }
        }
    }

    /**
     * Watches a TextToSpeech engine and reports when it falls silent. Use after any tts.speak()
     * that is not already wrapped by CharacterController.speakWithLipSync.
     */
    fun watchTtsUntilSilent(tts: TextToSpeech?) {
        if (tts == null) return
        setAssistantSpeaking(true)
        val startedAt = System.currentTimeMillis()
        val poll = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startedAt
                val speaking = try { tts.isSpeaking } catch (_: Throwable) { false }
                if ((speaking || elapsed < 400L) && elapsed < 60_000L) {
                    mainHandler.postDelayed(this, 200L)
                } else {
                    setAssistantSpeaking(false)
                }
            }
        }
        mainHandler.postDelayed(poll, 200L)
    }

    // ------------------------------------------------------------------------------------------
    // Session engine (main thread)
    // ------------------------------------------------------------------------------------------

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun canUseMic(): Boolean =
        hosts.isNotEmpty() && hasRecordPermission && !assistantSpeaking

    /** Decides whether the passive wake loop should be running, and (re)starts it if so. */
    private fun reevaluate(delayMs: Long = 0L) {
        restartListeningJob?.cancel()
        if (!canUseMic()) {
            if (sessionKind != null) cancelSession(destroy = hosts.isEmpty())
            return
        }
        if (sessionKind != null) return
        if (!_isWakeWordEnabled.value || wakeLoopSuspended || awaitingReply) return
        if (delayMs <= 0L) {
            startSession(SessionKind.WAKE)
        } else {
            restartListeningJob = scope.launch(Dispatchers.Main) {
                delay(delayMs)
                if (canUseMic() && sessionKind == null && _isWakeWordEnabled.value &&
                    !wakeLoopSuspended && !awaitingReply
                ) {
                    startSession(SessionKind.WAKE)
                }
            }
        }
    }

    private fun startSession(kind: SessionKind) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _lastError.value = "Speech recognition isn't available on this device."
            return
        }
        if (!hasRecordPermission) return
        if (kind == SessionKind.WAKE && !canUseMic()) return
        if (kind == SessionKind.COMMAND && (hosts.isEmpty() || assistantSpeaking)) {
            if (assistantSpeaking) {
                // Tapping the mic while she talks means "let me speak": stop her and listen.
                assistantSpeaking = false
            } else {
                return
            }
        }
        restartListeningJob?.cancel()
        if (sessionKind != null) cancelSession(destroy = false)
        try {
            val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
                it.setRecognitionListener(createRecognitionListener())
                speechRecognizer = it
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                if (kind == SessionKind.WAKE) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            sessionKind = kind
            _wakeWordArmed.value = kind == SessionKind.COMMAND
            _isActivelyListening.value = kind == SessionKind.COMMAND
            _isWakeLoopRunning.value = kind == SessionKind.WAKE
            if (kind == SessionKind.COMMAND) _livePartialTranscript.value = ""
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start SpeechRecognizer: ${e.message}")
            onHardError("start failed: ${e.message}", destroy = true)
        }
    }

    private fun cancelSession(destroy: Boolean) {
        val hadSession = sessionKind != null
        sessionKind = null
        _isActivelyListening.value = false
        _isWakeLoopRunning.value = false
        _wakeWordArmed.value = false
        _livePartialTranscript.value = ""
        _micRmsDb.value = 0f
        try {
            if (hadSession) speechRecognizer?.cancel()
            if (destroy) {
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
        } catch (_: Throwable) {
            speechRecognizer = null
        }
    }

    private fun onSessionEnded() {
        sessionKind = null
        _isActivelyListening.value = false
        _isWakeLoopRunning.value = false
        _wakeWordArmed.value = false
        _micRmsDb.value = 0f
    }

    private fun onHardError(reason: String, destroy: Boolean) {
        onSessionEnded()
        if (destroy) {
            try { speechRecognizer?.destroy() } catch (_: Throwable) {}
            speechRecognizer = null
        }
        hardErrorCount++
        Log.w(TAG, "Speech error #$hardErrorCount: $reason")
        if (hardErrorCount >= MAX_HARD_ERRORS) {
            wakeLoopSuspended = true
            _lastError.value = "Voice paused — the speech service kept failing. Tap the mic to try again."
            return
        }
        val backoff = (1_000L shl (hardErrorCount - 1).coerceAtMost(5)).coerceAtMost(MAX_BACKOFF_MS)
        reevaluate(delayMs = backoff)
    }

    private fun deliverCommand(command: String) {
        val callback = onCommandRecognizedCallback ?: return
        // Hold the wake loop until she has answered (or a timeout passes).
        awaitingReply = true
        awaitReplyJob?.cancel()
        awaitReplyJob = scope.launch(Dispatchers.Main) {
            delay(AWAIT_REPLY_TIMEOUT_MS)
            awaitingReply = false
            reevaluate()
        }
        callback(command)
    }

    private fun createRecognitionListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            if (sessionKind == SessionKind.COMMAND) _micRmsDb.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val kind = sessionKind
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Plain silence — completely normal, not a failure.
                    onSessionEnded()
                    hardErrorCount = 0
                    if (kind == SessionKind.COMMAND) {
                        _livePartialTranscript.value = ""
                    }
                    reevaluate(delayMs = WAKE_RESTART_MS)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    onSessionEnded()
                    hasRecordPermission = false
                    _lastError.value = "Microphone permission is off."
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> onHardError("busy/client ($error)", destroy = true)
                else -> onHardError("error $error", destroy = false)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isEmpty()) return
            if (_pendingAction.value != null && ABORT_COMMAND_REGEX.matches(partial)) {
                cancelPendingAction()
                return
            }
            when (sessionKind) {
                SessionKind.COMMAND -> _livePartialTranscript.value = partial.replace(WAKE_WORD_REGEX, "")
                SessionKind.WAKE -> if (WAKE_WORD_REGEX.containsMatchIn(partial)) {
                    // She heard her name: switch the UI to "Listening…" straight away.
                    _wakeWordArmed.value = true
                    _isActivelyListening.value = true
                    _livePartialTranscript.value = partial.replace(WAKE_WORD_REGEX, "").trim()
                }
                null -> Unit
            }
        }

        override fun onResults(results: Bundle?) {
            val kind = sessionKind
            val wasArmed = _wakeWordArmed.value
            onSessionEnded()
            hardErrorCount = 0
            val rawSpoken = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            _livePartialTranscript.value = ""

            if (rawSpoken.isEmpty()) {
                reevaluate(delayMs = WAKE_RESTART_MS)
                return
            }
            if (_pendingAction.value != null && ABORT_COMMAND_REGEX.matches(rawSpoken)) {
                cancelPendingAction()
                reevaluate(delayMs = WAKE_RESTART_MS)
                return
            }

            val hasWakePhrase = WAKE_WORD_REGEX.containsMatchIn(rawSpoken)
            val cleaned = rawSpoken.replace(WAKE_WORD_REGEX, " ").trim()
            when {
                kind == SessionKind.COMMAND -> {
                    if (cleaned.isNotEmpty()) deliverCommand(cleaned) else reevaluate(WAKE_RESTART_MS)
                }
                hasWakePhrase || wasArmed -> {
                    if (cleaned.length >= 2) {
                        deliverCommand(cleaned)
                    } else {
                        // Just "Hey Elena" — open a command session for the actual request.
                        startSession(SessionKind.COMMAND)
                    }
                }
                else -> {
                    // Background chatter without her name: ignore it (this used to be sent as a command).
                    reevaluate(delayMs = WAKE_RESTART_MS)
                }
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
        pendingExecuteOverride = onExecuteConfirmed
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
                val exec = onExecutePendingActionCallback
                pendingExecuteOverride = null
                exec?.invoke(finalPending)
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
        pendingExecuteOverride = null
        return cancelled
    }

    /**
     * Immediately executes the pending voice action without waiting for the remaining countdown.
     */
    fun executePendingActionImmediately(): PendingVoiceAction? {
        countdownJob?.cancel()
        val current = _pendingAction.value ?: return null
        _pendingAction.value = null
        val exec = onExecutePendingActionCallback
        pendingExecuteOverride = null
        exec?.invoke(current)
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
        awaitReplyJob?.cancel()
        runOnMain { cancelSession(destroy = true) }
    }
}
