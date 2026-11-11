package com.example.ai

import android.content.Context
import android.content.Intent
import android.os.Build
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
        /** The last recording sent to the AI (overwritten each turn, never uploaded anywhere else). */
        fun lastHeardFile(context: Context): java.io.File = java.io.File(context.cacheDir, "last_heard.wav")

        private const val TAG = "VoiceCommandService"
        private val WAKE_WORD_REGEX = Regex(
            "(?i)\\b(hi|hey|hello|okay|ok)\\s+(mochi|mochie|mochy|elena|kaito|marco)\\b[,.!?\\s]*"
        )
        /** Ends a conversation (she replies once more, then stops listening). */
        val GOODBYE_REGEX = Regex(
            "(?i)\\b(bye|goodbye|good night|that'?s all|that is all|stop listening|go to sleep|see you|thanks,? that'?s it|nothing else|nothing more)\\b|" +
                // A plain "no" / "no thanks" (the answer to "anything else?").
                "(?i)^\\s*(no|nope|nah|no thanks|no thank you|not now|i'?m good|that'?s it)[.!,]?\\s*(thanks|thank you)?[.!]?\\s*$|" +
                "مع السلامة|باي|تصبح على خير|خلاص|بس كذا|خداحافظ|شب بخیر|بسه|ممنون،? همین|^\\s*لا\\s*(شكرا|شكراً|ماكو|ماريد)?\\s*$|ماكو شي|^\\s*نه\\s*(ممنون|مرسی|مرسي)?\\s*$|چیزی نمی‌خوام"
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
        private const val FOLLOW_UP_DELAY_MS = 350L
        /** Pause that ends the user's turn (Android's default is shorter and cuts people off). */
        private const val END_OF_SPEECH_SILENCE_MS = 1_800L
        /** Quiet turns in a row before she says goodbye and stops listening. */
        private const val MAX_QUIET_TURNS = 2
        /** How long the speech recogniser's own recording may linger after it stops. */

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
        /** The first sound of her answer is coming out of the speaker now. */
        fun notifyVoiceStarted() {
            INSTANCE?.let { svc -> svc.runOnMain {
                if (svc.speechEndAt > 0) {
                    VoiceLog.add(VoiceLog.Kind.SPOKE, "Her voice started %.1f s after you finished speaking".format((System.currentTimeMillis() - svc.speechEndAt) / 1000.0))
                    svc.speechEndAt = 0L
                }
            } }
        }

        fun notifyAssistantSaid(text: String) {
            VoiceLog.add(VoiceLog.Kind.SPOKE, "\"$text\"")
            INSTANCE?.let { svc -> svc.runOnMain { svc.echoGuard.remember(text) } }
        }
    }

    private enum class SessionKind { WAKE, COMMAND }

    private class CallbackSet(
        val onCommand: (String) -> Unit,
        val onExecutePending: (PendingVoiceAction) -> Unit,
        /** Spoken turn as audio for the AI to hear directly (null: this owner only takes text). */
        val onAudio: ((ByteArray) -> Unit)? = null
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    /** Used only for a spoken request after the wake phrase or a tap (one beep, by Android). */
    private var speechRecognizer: SpeechRecognizer? = null
    /** Quiet offline "Hey Mochi" listener (no SpeechRecognizer restarts, so no beeps). */
    private val wakeEngine = OfflineWakeWord(appContext)
    /** Hides the recogniser's start/stop beeps (Settings → "Silent listening"). */
    private val beeps = BeepSilencer(appContext)
    /** In a conversation, how many times in a row she got silence / couldn't understand. */
    private var quietTurns = 0
    /** Real requests heard in this conversation (0 = maybe a false wake-up from noise). */
    private var userTurns = 0

    enum class Idle { NOT_UNDERSTOOD, ASK_ANYTHING_ELSE, GOODBYE }

    /** Language switches asked for from the floating companion, for the app to save. */
    private val _languageRequests = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 2)
    val languageRequests: kotlinx.coroutines.flow.SharedFlow<String> = _languageRequests

    fun requestLanguageSwitch(tag: String) {
        setRecognitionLanguage(tag)
        _languageRequests.tryEmit(tag)
    }

    // ---- Another app using the microphone (a call, a voice note, a recorder) ----
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var otherAppUsingMic = false
    private val recordingCallback = object : android.media.AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<android.media.AudioRecordingConfiguration>) {
            runOnMain { checkOtherRecorders() }
        }
    }

    init {
        scope.launch(Dispatchers.Main) {
            var ticks = 0
            while (true) {
                delay(1_000L)
                checkOtherRecorders()
                tickPhase()
                if (++ticks % 5 == 0) launch(Dispatchers.Default) { PerfMeter.sample() }
            }
        }
        try { audioManager.registerAudioRecordingCallback(recordingCallback, mainHandler) } catch (_: Throwable) {}
        if (Build.VERSION.SDK_INT >= 31) {
            try { audioManager.addOnModeChangedListener({ mainHandler.post(it) }) { runOnMain { checkOtherRecorders() } } } catch (_: Throwable) {}
        }
    }

    /**
     * Steps back from the microphone only when Android says so: a call is in progress, or Android
     * silenced one of her own recorders because another app took the mic. Background helpers such
     * as "Hey Google" or Bixby, and her own recorders, never count. During a call all listening
     * stops; when silenced, the wake listener stays open (it costs nothing on silence) so she
     * notices the moment the mic is hers again.
     */
    private val micContention = MicContention()
    private var pausedForCall = false

    private fun ownSessionIds(): Set<Int> =
        setOf(wakeEngine.activeSessionId, capture?.sessionId ?: -1) - (-1)

    private fun checkOtherRecorders() {
        val mode = audioManager.mode
        val inCall = mode == android.media.AudioManager.MODE_IN_CALL || mode == android.media.AudioManager.MODE_IN_COMMUNICATION
        val mine = ownSessionIds()
        val silenced = Build.VERSION.SDK_INT >= 29 && mine.isNotEmpty() && try {
            audioManager.activeRecordingConfigurations.any { it.clientAudioSessionId in mine && it.isClientSilenced }
        } catch (_: Throwable) { false }
        val change = micContention.update(MicContention.Sample(silenced, inCall), System.currentTimeMillis()) ?: return
        when (change) {
            MicContention.State.CALL -> {
                otherAppUsingMic = true
                pausedForCall = true
                VoiceLog.add(VoiceLog.Kind.IGNORED, "Paused: a call is in progress (audio mode $mode)")
                endConversation("a call started")
                if (sessionKind != null) cancelSession(destroy = false)
            }
            MicContention.State.SILENCED -> {
                otherAppUsingMic = true
                pausedForCall = false
                VoiceLog.add(VoiceLog.Kind.IGNORED, "Paused: Android gave the microphone to another app (her recorder ${mine.joinToString()} silenced)")
                endConversation("another app needs the mic")
                if (sessionKind == SessionKind.COMMAND) cancelSession(destroy = false)
            }
            MicContention.State.FREE -> {
                otherAppUsingMic = false
                pausedForCall = false
                VoiceLog.add(VoiceLog.Kind.WAKE, "Microphone is hers again, listening resumed")
                reevaluate(delayMs = WAKE_RESTART_MS)
            }
        }
    }

    /** She says something when the user goes quiet (the view model speaks it in her voice). */
    var onConversationIdle: ((Idle) -> Unit)? = null
    private var wakeMatcher = WakePhraseMatcher(WakePhrases.normalise(WakePhrases.DEFAULT)!!)
    private var wakeStripRegex = phraseRegex(wakeMatcher.words)
    /** BCP-47 language the user speaks; recognition runs only in this language. */
    private var recognitionLanguage: String = Locale.getDefault().toLanguageTag()
    val recognitionLanguageTag: String get() = recognitionLanguage
    internal val echoGuard = EchoGuard()
    /**
     * Conversation mode: after the wake phrase or a mic tap she keeps listening after every reply,
     * until the user says goodbye, stays silent, or an action takes them to another app.
     */
    private var conversationActive = false
    private val _inConversation = MutableStateFlow(false)
    val inConversation: StateFlow<Boolean> = _inConversation.asStateFlow()

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

    // ---- What she is doing right now, and the watchdog that undoes anything stuck ----
    private val _phase = MutableStateFlow(VoicePhase.OFF)
    val phase: StateFlow<VoicePhase> = _phase.asStateFlow()
    private var phaseSince = System.currentTimeMillis()
    private val watchdog = VoiceWatchdog()

    private fun tickPhase() {
        val now = System.currentTimeMillis()
        val p = VoicePhases.derive(
            VoiceFlags(
                hasHost = hosts.isNotEmpty(),
                paused = liveCallActive || otherAppUsingMic,
                speaking = assistantSpeaking,
                awaitingReply = awaitingReply,
                commandSession = sessionKind == SessionKind.COMMAND,
                wakeSession = sessionKind == SessionKind.WAKE
            )
        )
        if (p != _phase.value) { _phase.value = p; phaseSince = now }
        val fix = watchdog.check(p, wantsWakeSession() && !wakeEngine.isLoading && !wakeLoopSuspended, now - phaseSince)
        if (fix == Recovery.NONE) return
        VoiceLog.add(VoiceLog.Kind.ERROR, "Watchdog: stuck in \"${p.label}\" for ${(now - phaseSince) / 1000}s, resetting ($fix)")
        phaseSince = now
        CrashReports.nonFatal("Voice watchdog: stuck in ${p.name}, recovery $fix")
        when (fix) {
            Recovery.STOP_WAITING -> { awaitReplyJob?.cancel(); awaitingReply = false; reevaluate() }
            Recovery.STOP_SPEAKING -> { bargeIn("Watchdog: speech ran far too long"); reevaluate() }
            Recovery.CLOSE_MIC -> { cancelSession(destroy = false); reevaluate() }
            Recovery.RESTART_WAKE -> reevaluate()
            Recovery.CLOSE_WAKE -> { if (sessionKind == SessionKind.WAKE) cancelSession(destroy = false); reevaluate() }
            Recovery.NONE -> {}
        }
    }

    private val _pendingAction = MutableStateFlow<PendingVoiceAction?>(null)
    val pendingAction: StateFlow<PendingVoiceAction?> = _pendingAction.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Last bound owner wins (the overlay sits on top of the app while it is showing). */
    private val callbackStack = ArrayList<Pair<String, CallbackSet>>()
    private var pendingExecuteOverride: ((PendingVoiceAction) -> Unit)? = null

    private val onCommandRecognizedCallback: ((String) -> Unit)?
        get() = callbackStack.lastOrNull()?.second?.onCommand

    private val onAudioCallback: ((ByteArray) -> Unit)?
        get() = callbackStack.lastOrNull()?.second?.onAudio

    // ---- Listening engine: the AI hears the audio (default), or the phone's recogniser ----
    private var capture: SpeechCapture? = null
    @Volatile private var aiListening = true
    /** After a quota error the phone's recogniser is used until this time. */
    @Volatile private var phoneRecognizerUntil = 0L

    /** Settings → "AI listening". */
    fun setAiListening(enabled: Boolean) { aiListening = enabled }

    /** The AI can't hear right now (e.g. quota): use the phone's recogniser for a while. */
    fun usePhoneRecognizerFor(minutes: Int) {
        phoneRecognizerUntil = System.currentTimeMillis() + minutes * 60_000L
        VoiceLog.add(VoiceLog.Kind.ERROR, "AI listening paused for $minutes min; using the phone's speech recogniser")
    }

    private fun useAiListening(): Boolean =
        aiListening && onAudioCallback != null && System.currentTimeMillis() >= phoneRecognizerUntil

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
        onExecutePendingAction: (PendingVoiceAction) -> Unit,
        onAudioCaptured: ((ByteArray) -> Unit)? = null
    ) {
        runOnMain {
            callbackStack.removeAll { it.first == owner }
            callbackStack.add(owner to CallbackSet(onCommandRecognized, onExecutePendingAction, onAudioCaptured))
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

    /** Contact and app names the recogniser should favour ("Ali Odeh", not "Ali audits"). */
    @Volatile private var biasingStrings: ArrayList<String> = arrayListOf()

    fun setBiasingStrings(names: List<String>) {
        biasingStrings = ArrayList(names.map { it.trim() }.filter { it.isNotEmpty() && it.length <= 60 }.distinct().take(500))
    }

    fun setRecognitionLanguage(tag: String) {
        runOnMain { recognitionLanguage = tag.ifBlank { Locale.getDefault().toLanguageTag() } }
    }

    /** Starts a back-and-forth conversation (the mic reopens after each of her replies). */
    fun startConversation() {
        runOnMain {
            if (!conversationActive) VoiceLog.add(VoiceLog.Kind.WAKE, "Conversation started")
            conversationActive = true
            quietTurns = 0
            userTurns = 0
            _inConversation.value = true
        }
    }

    /** Ends conversation mode; quiet wake listening (if on) takes over again. */
    fun endConversation(reason: String) {
        runOnMain {
            if (!conversationActive) return@runOnMain
            conversationActive = false
            _inConversation.value = false
            VoiceLog.add(VoiceLog.Kind.WAKE, "Conversation ended ($reason)")
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
                // Tapping again while she listens ends the conversation.
                cancelSession(destroy = false)
                endConversation("mic tapped")
                reevaluate(delayMs = WAKE_RESTART_MS)
                return@runOnMain
            }
            wakeLoopSuspended = false
            hardErrorCount = 0
            awaitingReply = false
            startConversation()
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
            } else if (conversationActive && hosts.isNotEmpty() && hasRecordPermission && !liveCallActive) {
                // In a conversation: listen for the next thing the user says, once the speaker is quiet.
                restartListeningJob?.cancel()
                restartListeningJob = scope.launch(Dispatchers.Main) {
                    delay(FOLLOW_UP_DELAY_MS)
                    if (!assistantSpeaking && !awaitingReply && sessionKind != SessionKind.COMMAND) startSession(SessionKind.COMMAND)
                }
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

    internal fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /**
     * Quiet offline listening is wanted when the wake word is on and she isn't busy answering, and
     * also while she is talking, so "Hey Mochi" or "stop" can interrupt her.
     */
    private fun wantsWakeSession(): Boolean =
        hosts.isNotEmpty() && hasRecordPermission && !liveCallActive && !wakeLoopSuspended && !pausedForCall &&
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
                    wakeAt = System.currentTimeMillis()
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

    /** One spoken turn recorded for the AI (see [SpeechCapture]): any language, no beeps. */
    private fun startCapture() {
        restartListeningJob?.cancel()
        if (otherAppUsingMic) {
            VoiceLog.add(VoiceLog.Kind.IGNORED, "Not listening: another app has the microphone right now")
            _lastError.value = "Another app is using the microphone right now. Try again when it's done."
            return
        }
        if (sessionKind != null) cancelSession(destroy = false)
        val c = SpeechCapture(
            onLevel = { db -> if (sessionKind == SessionKind.COMMAND) _micRmsDb.value = ((db - 30f) / 4f).coerceIn(0f, 10f) },
            onSpeechStarted = { _livePartialTranscript.value = "…" },
            onUtterance = { wav ->
                capture = null
                onSessionEnded()
                hardErrorCount = 0
                _livePartialTranscript.value = ""
                deliverAudio(wav)
            },
            onNoSpeech = { wav, loudest, needed ->
                capture = null
                VoiceLog.add(VoiceLog.Kind.IGNORED, "No speech heard in %.0f s (loudest sound %.0f, speech needs %.0f). Play it back in Diagnostics.".format((wav.size - 44) / 32_000.0, loudest, needed))
                scope.launch(Dispatchers.IO) { runCatching { lastHeardFile(appContext).writeBytes(wav) } }
                onSessionEnded()
                _livePartialTranscript.value = ""
                handleQuietTurn(notUnderstood = false)
            },
            onError = { reason ->
                capture = null
                VoiceLog.add(VoiceLog.Kind.ERROR, reason)
                onHardError(reason, destroy = false)
            }
        )
        capture = c
        sessionKind = SessionKind.COMMAND
        _wakeWordArmed.value = true
        _isActivelyListening.value = true
        _isWakeLoopRunning.value = false
        _livePartialTranscript.value = ""
        Earcon.listening() // "your turn": the same soft beep every time the mic opens
        c.start()
        val sinceWake = System.currentTimeMillis() - wakeAt
        VoiceLog.add(VoiceLog.Kind.HEARD, "Mic open, the AI is listening" + if (wakeAt > 0 && sinceWake < 15_000) " (%.1f s after her name)".format(sinceWake / 1000.0) else "")
        wakeAt = 0L
    }

    private fun deliverAudio(wav: ByteArray) {
        val callback = onAudioCallback ?: return
        lastUtteranceSec = (wav.size - 44) / 32_000.0
        speechEndAt = System.currentTimeMillis()
        VoiceLog.add(VoiceLog.Kind.HEARD, "Sent %.1f s of sound to the AI".format(lastUtteranceSec))
        // Kept on the phone only, replaced every turn: Diagnostics can play back what she heard.
        scope.launch(Dispatchers.IO) { runCatching { lastHeardFile(appContext).writeBytes(wav) } }
        awaitingReply = true
        awaitReplyJob?.cancel()
        awaitReplyJob = scope.launch(Dispatchers.Main) {
            delay(AWAIT_REPLY_TIMEOUT_MS)
            awaitingReply = false
            reevaluate()
        }
        callback(wav)
    }

    /** The AI heard the user say [transcript] (an audio turn). */
    fun onUserSaid(transcript: String) {
        runOnMain {
            VoiceLog.add(VoiceLog.Kind.HEARD, "\"$transcript\"")
            quietTurns = 0
            userTurns++
            if (GOODBYE_REGEX.containsMatchIn(transcript)) endConversation("goodbye")
        }
    }

    private var lastUtteranceSec = 0.0
    private var speechEndAt = 0L
    private var wakeAt = 0L

    /** The AI could make out no words in the recording: listen again quietly. */
    fun onNothingForMe() {
        runOnMain {
            val note = brain.lastHeardNote
            VoiceLog.add(VoiceLog.Kind.IGNORED, "The AI found no words in %.1f s of sound".format(lastUtteranceSec) +
                (if (note.isNotBlank()) " (it heard: $note)" else "") + ". Play it back in Diagnostics → \"Play what she heard\".")
            speechEndAt = 0L
            awaitReplyJob?.cancel()
            awaitingReply = false
            handleQuietTurn(notUnderstood = true, silent = true)
        }
    }

    private fun startSession(kind: SessionKind) {
        if (kind == SessionKind.WAKE) { startWakeListening(); return }
        if (kind == SessionKind.COMMAND && useAiListening() && hasRecordPermission) {
            if (assistantSpeaking) bargeIn("Interrupted her with the mic button")
            if (hosts.isEmpty()) return
            startCapture()
            return
        }
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
                // Give the user time to think mid-sentence before deciding they've finished.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, END_OF_SPEECH_SILENCE_MS)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, END_OF_SPEECH_SILENCE_MS)
                if (Build.VERSION.SDK_INT >= 33 && biasingStrings.isNotEmpty()) {
                    putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, biasingStrings)
                }
            }
            sessionKind = SessionKind.COMMAND
            _wakeWordArmed.value = true
            _isActivelyListening.value = true
            _isWakeLoopRunning.value = false
            _livePartialTranscript.value = ""
            beeps.mute()
            recognizer.startListening(intent)
            VoiceLog.add(VoiceLog.Kind.HEARD, "Mic open, listening for you ($recognitionLanguage)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not start SpeechRecognizer: ${e.message}")
            onHardError("start failed: ${e.message}", destroy = true)
        }
    }

    /** Settings → "Silent listening": hide the recogniser's beeps. */
    fun setSilentListening(enabled: Boolean) {
        runOnMain { beeps.enabled = enabled; if (!enabled) beeps.unmute() }
    }

    private fun cancelSession(destroy: Boolean) {
        beeps.unmute()
        capture?.stop()
        capture = null
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
        quietTurns = 0
        userTurns++
        // "Bye" / "that's all": she still answers, then stops listening.
        if (GOODBYE_REGEX.containsMatchIn(command)) endConversation("goodbye")
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

    /**
     * The user said nothing (or nothing understandable) in a conversation. First time: "Sorry?"
     * or "Anything else?" and listen again. Second time: a polite goodbye, then quiet wake
     * listening. Outside a conversation: just go back to wake listening.
     */
    private fun handleQuietTurn(notUnderstood: Boolean, silent: Boolean = false) {
        val idle = onConversationIdle
        if (!conversationActive || idle == null) {
            endConversation("no reply")
            reevaluate(delayMs = WAKE_RESTART_MS)
            return
        }
        if (userTurns == 0) {
            // Woken but never spoken to (often noise in a busy room): go back to sleep quietly
            // instead of asking "anything else?".
            endConversation(if (notUnderstood) "only noise heard" else "nothing said after waking")
            reevaluate(delayMs = WAKE_RESTART_MS)
            return
        }
        quietTurns++
        when {
            quietTurns >= MAX_QUIET_TURNS -> {
                endConversation("no reply")
                idle(Idle.GOODBYE)
                reevaluate(delayMs = WAKE_RESTART_MS)
            }
            // Background noise only: just listen again, without "sorry?".
            silent -> startSession(SessionKind.COMMAND)
            notUnderstood -> idle(Idle.NOT_UNDERSTOOD)   // conversation stays on: mic reopens after she speaks
            else -> idle(Idle.ASK_ANYTHING_ELSE)
        }
    }

    private fun stripWake(text: String): String {
        var t = text.replace(wakeStripRegex, "")
        // Also the sound-alike spellings the recogniser may write ("hi Elsa" for "hi Elza").
        wakeMatcher.variants.forEach { t = t.replace(phraseRegex(it), "") }
        return t.replace(WAKE_WORD_REGEX, " ").trim()
    }

    private fun createRecognitionListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            beeps.unmute(afterMs = 350L) // the start beep has played by now
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            _micRmsDb.value = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            beeps.mute() // the stop beep follows; unmuted when the result arrives
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    beeps.unmute(afterMs = 300L)
                    val notUnderstood = error == SpeechRecognizer.ERROR_NO_MATCH
                    VoiceLog.add(VoiceLog.Kind.IGNORED, if (notUnderstood) "Speech heard but not understood" else "No speech heard")
                    onSessionEnded()
                    hardErrorCount = 0
                    _livePartialTranscript.value = ""
                    handleQuietTurn(notUnderstood)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    onSessionEnded()
                    hasRecordPermission = false
                    _lastError.value = "Microphone permission is off."
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> onHardError("busy/client ($error)", destroy = true)
                else -> {
                    VoiceLog.add(VoiceLog.Kind.ERROR, "Speech recogniser error $error")
                    onHardError("error $error", destroy = false)
                }
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
            beeps.unmute(afterMs = 300L)
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
    /** A spoken turn as audio → (what the user said, the companion's answer or null for noise). */
    suspend fun understandAudio(
        wav: ByteArray,
        companion: CharacterSpec,
        userName: String,
        history: List<CompanionChatTurn>,
        preferredLanguage: String
    ): Pair<String, CompanionChatTurn?> = brain.understandAudio(wav, companion, userName, history, preferredLanguage)

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
    private val recent = ArrayDeque<Pair<List<String>, Long>>()

    fun remember(spoken: String) {
        val words = tokens(spoken)
        if (words.isEmpty()) return
        recent.addLast(words to clock())
        while (recent.size > 6) recent.removeFirst()
    }

    /** True when she said all of [words] in the last [WINDOW_MS] (her own voice, not the user's). */
    fun saidRecently(words: List<String>): Boolean {
        val now = clock()
        return recent.any { (said, at) -> now - at <= WINDOW_MS && words.all { it.lowercase() in said } }
    }

    /**
     * True when [heard] repeats a stretch of what she just said, word for word and in order
     * (one slip allowed in 4+ words). Sharing a few words is not enough: "can you hear me"
     * is the user's question even right after she said "yes, I can hear you".
     */
    fun isEcho(heard: String): Boolean {
        val words = tokens(heard)
        if (words.size < 3) return false
        val allowedSlips = if (words.size >= 4) 1 else 0
        val now = clock()
        return recent.any { (said, at) ->
            now - at <= WINDOW_MS && said.size >= words.size && (0..said.size - words.size).any { start ->
                words.indices.count { words[it] != said[start + it] } <= allowedSlips
            }
        }
    }

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).filter { it.length > 1 }

    private companion object {
        const val WINDOW_MS = 25_000L
    }
}
