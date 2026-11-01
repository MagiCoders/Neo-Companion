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
 * 1. Understands commands in any language through [CompanionBrain] (Gemini via Firebase AI Logic).
 * 2. Maintains a global, persistent `'Hi Mochi'` wake-word listening loop across all app screens
 *    whenever `wakeWordEnabled` is active.
 * 3. Manages a 3-second `'Undo/Cancel'` countdown state (`pendingAction`) so users can abort any
 *    voice-triggered navigation or media/app launch by tapping Undo/Cancel or saying "stop" / "cancel".
 */
class VoiceCommandService(
    private val appContext: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "VoiceCommandService"
        private val WAKE_WORD_REGEX = Regex(
            "(?i)\\b(hi|hey|hello|okay|ok)\\s+(mochi|mochie|mochy|elena|kaito|marco)\\b[,.!?\\s]*"
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
        /** Pause after she stops talking before a follow-up listen, so the room echo has died down. */
        private const val FOLLOW_UP_DELAY_MS = 600L

        private fun phraseRegex(words: List<String>): Regex =
            Regex("(?i)^\\W*" + words.joinToString("\\W+") { Regex.escape(it) } + "\\b[,.!?\\s]*")

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

        /** Stops whoever is speaking right now (registered by CharacterController while it talks). */
        private val activeSpeakers = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

        fun registerSpeaker(stop: () -> Unit) { activeSpeakers += stop }
        fun unregisterSpeaker(stop: () -> Unit) { activeSpeakers -= stop }

        /** What the companion just said aloud, so an echo of it is never taken as a command. */
        fun notifyAssistantSaid(text: String) {
            INSTANCE?.let { svc -> svc.runOnMain { svc.echoGuard.remember(text) } }
        }
    }

    private enum class SessionKind { WAKE, COMMAND }

    private class CallbackSet(
        val onCommand: (String) -> Unit,
        val onExecutePending: (PendingVoiceAction) -> Unit
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    /** Used only for a spoken request after the wake phrase or a tap (one beep, by Android). */
    private var speechRecognizer: SpeechRecognizer? = null
    /** Quiet offline "Hey Mochi" listener (no SpeechRecognizer restarts, so no beeps). */
    private val wakeEngine = OfflineWakeWord(appContext)
    private var wakeMatcher = WakePhraseMatcher(WakePhrases.normalise(WakePhrases.DEFAULT)!!)
    private var wakeStripRegex = phraseRegex(wakeMatcher.words)
    /** BCP-47 language the user speaks; recognition runs only in this language. */
    private var recognitionLanguage: String = Locale.getDefault().toLanguageTag()
    val recognitionLanguageTag: String get() = recognitionLanguage
    internal val echoGuard = EchoGuard()
    /** Open the mic once more when the current reply finishes (she asked a question). */
    private var followUpPending = false

    /** Called when only the wake phrase was heard ("Hey Mochi") — e.g. to open a live voice call. */
    var onWakeWordOnly: (() -> Unit)? = null
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
    /** A Gemini Live call owns the microphone; the recognizer must stay off. */
    private var liveCallActive = false

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

    private val brain = CompanionBrain()

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

    private var wakePhraseTyped: List<String> = WakePhrases.normalise(WakePhrases.DEFAULT)!!

    /**
     * The phrase that wakes her, as the user typed it ("hi elza"). Words the offline model doesn't
     * know are listened for through their closest sound-alikes (see [WakeVocabulary]).
     */
    fun setWakePhrase(phrase: String) {
        runOnMain {
            val words = WakePhrases.normalise(phrase) ?: WakePhrases.normalise(WakePhrases.DEFAULT)!!
            if (words == wakePhraseTyped) return@runOnMain
            wakePhraseTyped = words
            wakeStripRegex = phraseRegex(words)
            wakeEngine.resolvePhrase(words) { resolution ->
                if (words != wakePhraseTyped) return@resolvePhrase
                wakeMatcher = if (resolution.ok) WakePhraseMatcher(resolution.variants, resolution.decoys)
                else WakePhraseMatcher(words)
                if (sessionKind == SessionKind.WAKE) {
                    cancelSession(destroy = false)
                    reevaluate()
                }
            }
        }
    }

    /** Checks a proposed wake phrase: which sound-alikes would be used, or which words can't work. */
    fun resolveWakePhrase(words: List<String>, onResult: (WakeVocabulary.Resolution) -> Unit) =
        wakeEngine.resolvePhrase(words, onResult)

    /** For Diagnostics: the wake variants listened for and the phone's audio clean-up. */
    val wakeStatus: String
        get() = "Listening for: " + wakeMatcher.variants.joinToString(" / ") { it.joinToString(" ") } +
            "\nMicrophone clean-up: " + wakeEngine.lastAudioEffects

    fun setRecognitionLanguage(tag: String) {
        runOnMain { recognitionLanguage = tag.ifBlank { Locale.getDefault().toLanguageTag() } }
    }

    /** Call when the reply about to be spoken expects an answer: the mic reopens once after it. */
    fun listenAfterNextReply() {
        runOnMain { followUpPending = true }
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
                // A spoken request in progress ends; quiet wake listening carries on so the user
                // can interrupt her by saying the wake phrase or "stop".
                if (sessionKind == SessionKind.COMMAND) cancelSession(destroy = false)
                reevaluate()
            } else if (followUpPending && hosts.isNotEmpty() && hasRecordPermission && !liveCallActive) {
                // She asked something: listen for the answer once, after the speaker has gone quiet.
                followUpPending = false
                restartListeningJob?.cancel()
                restartListeningJob = scope.launch(Dispatchers.Main) {
                    delay(FOLLOW_UP_DELAY_MS)
                    if (!assistantSpeaking && sessionKind != SessionKind.COMMAND) startSession(SessionKind.COMMAND)
                }
            } else {
                followUpPending = false
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

    internal fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /**
     * Quiet offline listening is wanted when the wake word is on and she isn't busy answering, and
     * also while she is talking, so "Hey Mochi" or "stop" can interrupt her.
     */
    private fun wantsWakeSession(): Boolean =
        hosts.isNotEmpty() && hasRecordPermission && !liveCallActive && !wakeLoopSuspended &&
            (assistantSpeaking || (_isWakeWordEnabled.value && !awaitingReply))

    fun setLiveCallActive(active: Boolean) {
        runOnMain {
            liveCallActive = active
            if (active) cancelSession(destroy = true) else reevaluate(delayMs = WAKE_RESTART_MS)
        }
    }

    /** Decides whether the passive wake loop should be running, and (re)starts it if so. */
    private fun reevaluate(delayMs: Long = 0L) {
        restartListeningJob?.cancel()
        if (sessionKind == SessionKind.COMMAND) {
            if (hosts.isEmpty() || !hasRecordPermission || liveCallActive) cancelSession(destroy = hosts.isEmpty())
            return
        }
        if (!wantsWakeSession()) {
            if (sessionKind != null) cancelSession(destroy = hosts.isEmpty())
            return
        }
        if (sessionKind == SessionKind.WAKE) return
        if (delayMs <= 0L) {
            startWakeListening()
        } else {
            restartListeningJob = scope.launch(Dispatchers.Main) {
                delay(delayMs)
                if (sessionKind == null && wantsWakeSession()) startWakeListening()
            }
        }
    }

    /** She is cut off mid-sentence. */
    private fun bargeIn(reason: String) {
        VoiceLog.add(VoiceLog.Kind.WAKE, reason)
        followUpPending = false
        assistantSpeaking = false
        activeSpeakers.forEach { stop -> try { stop() } catch (_: Throwable) {} }
    }

    /** Passive listening for the wake phrase: the offline engine, never the SpeechRecognizer loop. */
    private fun startWakeListening() {
        if (!wantsWakeSession()) return
        if (!wakeEngine.isReady) {
            wakeEngine.prepare(
                onReady = { reevaluate() },
                onFailed = { msg -> wakeLoopSuspended = true; _lastError.value = msg }
            )
            return
        }
        restartListeningJob?.cancel()
        if (sessionKind != null) cancelSession(destroy = false)
        val started = wakeEngine.start(
            matcher = wakeMatcher,
            onWake = {
                onSessionEnded()
                hardErrorCount = 0
                if (assistantSpeaking) {
                    if (echoGuard.saidRecently(wakeMatcher.words)) {
                        // She said her own wake phrase out loud: that's not the user.
                        VoiceLog.add(VoiceLog.Kind.IGNORED, "Heard the wake phrase in her own voice")
                        reevaluate()
                        return@start
                    }
                    bargeIn("Interrupted her with the wake phrase")
                } else {
                    VoiceLog.add(VoiceLog.Kind.WAKE, "Wake phrase heard")
                }
                // Open a live call if the user chose that, else take one spoken request.
                onWakeWordOnly?.invoke() ?: startSession(SessionKind.COMMAND)
            },
            onAbort = {
                if (assistantSpeaking && !echoGuard.saidRecently(listOf("stop")) && !echoGuard.saidRecently(listOf("cancel"))) {
                    bargeIn("Stopped her (\"stop\")")
                }
                if (_pendingAction.value != null) {
                    VoiceLog.add(VoiceLog.Kind.WAKE, "Countdown cancelled by voice")
                    cancelPendingAction()
                }
            },
            onError = { reason -> onHardError(reason, destroy = false) }
        )
        if (!started) {
            onHardError("microphone busy", destroy = false)
            return
        }
        sessionKind = SessionKind.WAKE
        _wakeWordArmed.value = false
        _isActivelyListening.value = false
        _isWakeLoopRunning.value = true
    }

    private fun startSession(kind: SessionKind) {
        if (kind == SessionKind.WAKE) { startWakeListening(); return }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _lastError.value = "Speech recognition isn't available on this device."
            return
        }
        if (!hasRecordPermission) return
        if (kind == SessionKind.COMMAND && (hosts.isEmpty() || assistantSpeaking)) {
            if (assistantSpeaking) {
                // Tapping the mic while she talks means "let me speak": stop her and listen.
                bargeIn("Interrupted her with the mic button")
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
                // Only the user's chosen language: noise can't come back as Japanese or Korean.
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLanguage)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            }
            sessionKind = SessionKind.COMMAND
            _wakeWordArmed.value = true
            _isActivelyListening.value = true
            _isWakeLoopRunning.value = false
            _livePartialTranscript.value = ""
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
        wakeEngine.stop()
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
        if (echoGuard.isEcho(command)) {
            // The mic caught her own voice from the speaker: drop it, never answer herself.
            VoiceLog.add(VoiceLog.Kind.IGNORED, "Echo of her own voice: \"$command\"")
            reevaluate(delayMs = WAKE_RESTART_MS)
            return
        }
        val callback = onCommandRecognizedCallback ?: return
        VoiceLog.add(VoiceLog.Kind.HEARD, "\"$command\" (${recognitionLanguage})")
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

    private fun stripWake(text: String): String =
        text.replace(wakeStripRegex, "").replace(WAKE_WORD_REGEX, " ").trim()

    private fun createRecognitionListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            _micRmsDb.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Nothing said after the tap / wake phrase: go back to quiet wake listening.
                    onSessionEnded()
                    hardErrorCount = 0
                    _livePartialTranscript.value = ""
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
            if (sessionKind == SessionKind.COMMAND) _livePartialTranscript.value = stripWake(partial)
        }

        override fun onResults(results: Bundle?) {
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

            val cleaned = stripWake(rawSpoken)
            if (cleaned.isNotEmpty()) deliverCommand(cleaned) else reevaluate(WAKE_RESTART_MS)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** Understands one utterance. Throws [AiUnavailableException] when the AI can't answer. */
    suspend fun processNaturalLanguageCommand(
        commandText: String,
        companion: CharacterSpec,
        userName: String = "",
        history: List<CompanionChatTurn> = emptyList(),
        preferredLanguage: String = ""
    ): CompanionChatTurn {
        val trimmed = commandText.trim()
        if (ABORT_COMMAND_REGEX.matches(trimmed)) {
            cancelPendingAction()
            return CompanionChatTurn(
                id = UUID.randomUUID().toString(),
                senderIsUser = false,
                message = "Okay, cancelled.",
                spokenConfirmation = "Okay, cancelled.",
                isActionReport = true
            )
        }
        // Throws AiUnavailableException on failure; callers show an honest error.
        return brain.understand(trimmed, companion, userName, history, preferredLanguage)
    }

    /**
     * Starts the 3-second Undo/Cancel overlay countdown for any actionable voice command
     * (navigation, WhatsApp call/chat/contacts, YouTube, Browser, Social Media, or Media playback).
     */
    fun scheduleActionWithUndoWindow(
        turn: CompanionChatTurn,
        durationSeconds: Int = 3,
        titleOverride: String? = null,
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
        val finalTitle = titleOverride ?: title
        val safeTotal = durationSeconds.coerceAtLeast(1)
        val subtitle = "Executing in ${safeTotal}s · Tap Undo or say \"stop\" to cancel"

        val pending = PendingVoiceAction(
            id = turn.id,
            turn = turn,
            title = finalTitle,
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
    ): com.example.actions.ActionOutcome {
        return OmnichannelMediaBridge.autoExecuteCompanionAction(
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
        runOnMain {
            cancelSession(destroy = true)
            wakeEngine.release()
        }
    }
}

/**
 * Recognises the companion's own words coming back through the microphone (speaker echo).
 * Main thread only. Pure logic, unit-tested.
 */
class EchoGuard(private val clock: () -> Long = System::currentTimeMillis) {
    private val recent = ArrayDeque<Pair<Set<String>, Long>>()

    fun remember(spoken: String) {
        val words = tokens(spoken)
        if (words.isEmpty()) return
        recent.addLast(words.toSet() to clock())
        while (recent.size > 6) recent.removeFirst()
    }

    /** True when she said all of [words] in the last [WINDOW_MS] (her own voice, not the user's). */
    fun saidRecently(words: List<String>): Boolean {
        val now = clock()
        return recent.any { (said, at) -> now - at <= WINDOW_MS && words.all { it.lowercase() in said } }
    }

    /** True when most words of [heard] are words she said in the last [WINDOW_MS]. */
    fun isEcho(heard: String): Boolean {
        val words = tokens(heard)
        if (words.size < 2) return false
        val now = clock()
        return recent.any { (said, at) ->
            now - at <= WINDOW_MS && words.count { it in said }.toFloat() / words.size >= OVERLAP
        }
    }

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).filter { it.length > 1 }

    private companion object {
        const val WINDOW_MS = 25_000L
        const val OVERLAP = 0.7f
    }
}
