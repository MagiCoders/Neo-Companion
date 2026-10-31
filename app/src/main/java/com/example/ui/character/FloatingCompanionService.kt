package com.example.ui.character

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.ai.OmnichannelMediaBridge
import com.example.ai.PendingVoiceAction
import com.example.ai.VoiceCommandService
import com.example.data.CompanionActionType
import com.example.ui.theme.MochiTheme
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType
import com.example.ui.theme.MochiVisualStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The companion floating over other apps (`TYPE_APPLICATION_OVERLAY`).
 *
 * - Borderless: a transparent window exactly the size of the character (+ bubble when she talks).
 *   No circle, no card, no square Picture-in-Picture.
 * - Tap her to talk, long-press for the quick-ask card, drag to move; she snaps to the nearest edge.
 * - Uses the app's single voice engine, so the app and the overlay never fight over the mic.
 * - Must be started while the app is still visible (Home button / Float button) — Android only lets
 *   a foreground service use the microphone if it was started from the foreground.
 */
class FloatingCompanionService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var ttsEngine: TextToSpeech? = null
    private var micHostAcquired = false

    private val controller = CharacterController(serviceScope)
    private val voice by lazy { VoiceCommandService.shared(applicationContext) }

    private var activeCharacter by mutableStateOf(CharacterSpec.ELENA)
    private var bubbleText by mutableStateOf<String?>(null)
    private var isThinking by mutableStateOf(false)
    private var quickAskOpen by mutableStateOf(false)
    private var dockedRight by mutableStateOf(true)
    private var lightTheme by mutableStateOf(false)
    private var bubbleHideJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lightTheme = !prefs.getBoolean("isDarkTheme", true)
        activeCharacter = CharacterSpec.fromId(prefs.getString("selectedCharacterId", null) ?: CharacterSpec.MOCHI.id)

        startForegroundCompat()

        ttsEngine = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.let { OmnichannelMediaBridge.configureHumanLikeNeuralVoice(it, activeCharacter) }
            }
        }

        voice.bindCallbacks(
            owner = OWNER,
            onCommandRecognized = { command -> handlePrompt(command) },
            onExecutePendingAction = { pending -> executeAction(pending) }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        intent?.getStringExtra(EXTRA_CHARACTER_ID)?.let { activeCharacter = CharacterSpec.fromId(it) }
        intent?.getStringExtra(EXTRA_LAST_MESSAGE)?.takeIf { it.isNotBlank() }?.let { showBubble(it, 5_000L) }

        if (Settings.canDrawOverlays(this)) {
            attachOverlayWindowIfNeeded()
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        } else {
            stopSelf()
        }
        // Not sticky: a system restart in the background could not use the mic anyway.
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------------------------------
    // Foreground service
    // ------------------------------------------------------------------------------------------

    private fun startForegroundCompat() {
        val channelId = "mochi_floating_companion"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Floating companion", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while your companion is floating over other apps"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.createNotificationChannel(channel)
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hideIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingCompanionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val firstName = activeCharacter.displayName.substringAfter(" ")
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("$firstName is floating")
            .setContentText("Tap her to talk · hold for quick ask")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .addAction(0, "Open app", openIntent)
            .addAction(0, "Hide", hideIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val specialUse = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val microphone = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0

        micHostAcquired = false
        if (micGranted && microphone != 0) {
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, microphone or specialUse)
                micHostAcquired = true
            } catch (e: Exception) {
                // Started from the background, or mic not allowed right now: float without voice.
                Log.w(TAG, "Microphone foreground type refused: ${e.message}")
            }
        }
        if (!micHostAcquired) {
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, specialUse)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start foreground service", e)
                stopSelf()
                return
            }
        }
        if (micHostAcquired || Build.VERSION.SDK_INT < 30) {
            micHostAcquired = true
            voice.acquireHost(OWNER)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Conversation
    // ------------------------------------------------------------------------------------------

    private fun showBubble(text: String, autoHideMs: Long = 7_000L) {
        bubbleText = text
        bubbleHideJob?.cancel()
        bubbleHideJob = serviceScope.launch {
            delay(autoHideMs)
            if (!isThinking) bubbleText = null
        }
    }

    private fun startListening() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted || !micHostAcquired) {
            showBubble("Open the app once and allow the microphone so I can hear you here.")
            return
        }
        ttsEngine?.stop()
        voice.triggerManualMicSession(hasRecordPermission = true)
    }

    private fun handlePrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || isThinking) {
            voice.clearAwaitingReply()
            return
        }
        serviceScope.launch {
            isThinking = true
            bubbleHideJob?.cancel()
            bubbleText = null
            controller.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val userName = com.example.security.SecureStore(applicationContext).getString("userName")
                val turn = voice.processNaturalLanguageCommand(clean, activeCharacter, userName)
                val prep = when (turn.actionType) {
                    CompanionActionType.NONE, CompanionActionType.SPEAK_LOUD,
                    CompanionActionType.NAVIGATE_ROUTE, CompanionActionType.WHATS_PLAYING -> null
                    else -> com.example.actions.ActionExecutor(applicationContext).prepare(turn)
                }
                if (prep is com.example.actions.Preparation.Blocked) {
                    showBubble(prep.outcome.spoken, autoHideMs = 9_000L)
                    controller.speakWithLipSync(applicationContext, ttsEngine, prep.outcome.spoken, activeCharacter)
                    return@launch
                }
                showBubble(turn.message, autoHideMs = 9_000L)
                controller.speakWithLipSync(applicationContext, ttsEngine, turn.message, activeCharacter)
                when (turn.actionType) {
                    CompanionActionType.NONE, CompanionActionType.SPEAK_LOUD -> Unit
                    CompanionActionType.NAVIGATE_ROUTE, CompanionActionType.WHATS_PLAYING -> executeTurn(turn)
                    else -> voice.scheduleActionWithUndoWindow(
                        turn,
                        durationSeconds = 3,
                        titleOverride = (prep as com.example.actions.Preparation.Ready).action.title
                    ) { executeAction(it) }
                }
            } catch (e: com.example.ai.AiUnavailableException) {
                showBubble(e.message ?: "Please try again.")
                controller.transitionTo(CharacterRuntimeState.Idle)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Overlay prompt failed: ${e.javaClass.simpleName}")
                showBubble("Something went wrong on my side. Please try again.")
                controller.transitionTo(CharacterRuntimeState.Idle)
            } finally {
                isThinking = false
                voice.clearAwaitingReply()
            }
        }
    }

    private fun executeAction(pending: PendingVoiceAction) = executeTurn(pending.turn)

    private fun executeTurn(turn: com.example.data.CompanionChatTurn) {
        val outcome = OmnichannelMediaBridge.autoExecuteCompanionAction(
            context = applicationContext,
            tts = ttsEngine,
            turn = turn,
            character = activeCharacter,
            onNavigateHashRoute = { openApp() }
        )
        if (outcome.spoken.isNotBlank()) showBubble(outcome.spoken, 6_000L)
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        stopSelf()
    }

    // ------------------------------------------------------------------------------------------
    // Window
    // ------------------------------------------------------------------------------------------

    private fun setWindowFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val lp = layoutParams ?: return
        val base = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        val newFlags = if (focusable) base else base or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (lp.flags != newFlags) {
            lp.flags = newFlags
            lp.softInputMode = if (focusable) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN else 0
            try { wm.updateViewLayout(view, lp) } catch (_: Throwable) {}
        }
    }

    private fun attachOverlayWindowIfNeeded() {
        if (composeView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val density = resources.displayMetrics.density
        val edgeMargin = (8 * density).roundToInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, // required for the 3D TextureView
            PixelFormat.TRANSLUCENT
        ).apply {
            // Anchored to the bottom edge so the bubble grows upward and she never jumps.
            gravity = Gravity.BOTTOM or Gravity.END
            x = edgeMargin
            y = (resources.displayMetrics.heightPixels * 0.18f).roundToInt()
            title = "Companion overlay"
        }
        layoutParams = params
        dockedRight = true

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingCompanionService)
            setViewTreeViewModelStoreOwner(this@FloatingCompanionService)
            setViewTreeSavedStateRegistryOwner(this@FloatingCompanionService)
            setContent {
                MochiTheme(
                    characterAccent = activeCharacter.accent,
                    visualStyle = if (lightTheme) MochiVisualStyle.WARM_STUDIO_LUXE else MochiVisualStyle.STUDIO_4K_OPTICAL
                ) {
                    val listening by voice.isActivelyListening.collectAsStateWithLifecycle()
                    val partial by voice.livePartialTranscript.collectAsStateWithLifecycle()
                    val pending by voice.pendingAction.collectAsStateWithLifecycle()
                    OverlayContent(
                        character = activeCharacter,
                        controller = controller,
                        bubbleText = bubbleText,
                        isThinking = isThinking,
                        isListening = listening,
                        partialTranscript = partial,
                        pendingAction = pending,
                        quickAskOpen = quickAskOpen,
                        dockedRight = dockedRight,
                        onTapCharacter = {
                            if (quickAskOpen) {
                                quickAskOpen = false
                                setWindowFocusable(false)
                            } else {
                                startListening()
                            }
                        },
                        onLongPressCharacter = {
                            quickAskOpen = true
                            controller.react("wave")
                        },
                        onDragStart = { showDismissTarget() },
                        onDrag = { dx, dy -> dragBy(dx, dy) },
                        onDragEnd = { finishDrag() },
                        onInputFocusChanged = { focused -> setWindowFocusable(focused) },
                        onSendPrompt = { text ->
                            quickAskOpen = false
                            setWindowFocusable(false)
                            handlePrompt(text)
                        },
                        onMicInQuickAsk = {
                            quickAskOpen = false
                            setWindowFocusable(false)
                            startListening()
                        },
                        onReaction = { controller.react(it) },
                        onCancelPending = {
                            voice.cancelPendingAction()
                            showBubble("Okay, cancelled.", 2_500L)
                        },
                        onRunPendingNow = { voice.executePendingActionImmediately() },
                        onOpenApp = { openApp() },
                        onClose = { stopSelf() }
                    )
                }
            }
        }
        composeView = view
        try {
            wm.addView(view, params)
        } catch (e: Throwable) {
            Log.e(TAG, "Could not add overlay window", e)
            composeView = null
            stopSelf()
        }
    }

    /** Converts the window to left-anchored coordinates while dragging. */
    private fun dragBy(dx: Float, dy: Float) {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val lp = layoutParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        if (lp.gravity and Gravity.END == Gravity.END) {
            lp.x = (screenW - lp.x - view.width).coerceAtLeast(0)
            lp.gravity = Gravity.BOTTOM or Gravity.START
        }
        lp.x = (lp.x + dx.roundToInt()).coerceIn(-view.width / 3, screenW - view.width * 2 / 3)
        lp.y = (lp.y - dy.roundToInt()).coerceIn(0, (screenH - view.height).coerceAtLeast(0))
        try { wm.updateViewLayout(view, lp) } catch (_: Throwable) {}
        val near = overDismissTarget()
        if (near != nearDismiss) {
            nearDismiss = near
            if (near) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // ---- Drag to the bottom circle to close (like Android chat heads) ----------------------

    private var dismissView: ComposeView? = null
    private var nearDismiss by mutableStateOf(false)

    private fun showDismissTarget() {
        if (dismissView != null) return
        val wm = windowManager ?: return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (170 * resources.displayMetrics.density).roundToInt(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            title = "Companion dismiss target"
        }
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingCompanionService)
            setViewTreeViewModelStoreOwner(this@FloatingCompanionService)
            setViewTreeSavedStateRegistryOwner(this@FloatingCompanionService)
            setContent { DismissTarget(active = nearDismiss) }
        }
        try {
            wm.addView(v, lp)
            dismissView = v
        } catch (_: Throwable) {
        }
    }

    private fun hideDismissTarget() {
        dismissView?.let { v -> try { windowManager?.removeView(v) } catch (_: Throwable) {} }
        dismissView = null
        nearDismiss = false
    }

    /** True when the companion's centre is over the bottom-centre circle. */
    private fun overDismissTarget(): Boolean {
        val view = composeView ?: return false
        val lp = layoutParams ?: return false
        val dm = resources.displayMetrics
        val leftX = if (lp.gravity and Gravity.END == Gravity.END) dm.widthPixels - lp.x - view.width else lp.x
        val cx = leftX + view.width / 2f
        val cy = dm.heightPixels - lp.y - view.height / 2f
        val tx = dm.widthPixels / 2f
        val ty = dm.heightPixels - 85 * dm.density
        return kotlin.math.hypot(cx - tx, cy - ty) < 95 * dm.density
    }

    private fun finishDrag() {
        if (overDismissTarget()) {
            composeView?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            hideDismissTarget()
            stopSelf()
            return
        }
        hideDismissTarget()
        snapToEdge()
    }

    private fun snapToEdge() {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val lp = layoutParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val margin = (8 * resources.displayMetrics.density).roundToInt()
        val center = lp.x + view.width / 2
        dockedRight = center > screenW / 2
        lp.gravity = Gravity.BOTTOM or (if (dockedRight) Gravity.END else Gravity.START)
        lp.x = margin
        try { wm.updateViewLayout(view, lp) } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        hideDismissTarget()
        voice.unbindCallbacks(OWNER)
        if (micHostAcquired) voice.releaseHost(OWNER)
        bubbleHideJob?.cancel()
        try {
            composeView?.let { windowManager?.removeView(it) }
        } catch (_: Throwable) {
        }
        composeView = null
        try {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
        } catch (_: Throwable) {
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FloatingCompanion"
        private const val OWNER = "overlay"
        private const val PREFS = "mochi_persistent_settings"
        private const val NOTIFICATION_ID = 4409
        const val ACTION_STOP = "com.example.action.STOP_FLOATING_COMPANION"
        const val EXTRA_CHARACTER_ID = "extra_character_id"
        const val EXTRA_LAST_MESSAGE = "extra_last_message"

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        /**
         * Starts the overlay. Call it while the activity is still visible (onUserLeaveHint, a button),
         * otherwise Android will refuse the microphone for it.
         */
        fun startOrRequestSystemOverlay(
            context: Context,
            character: CharacterSpec,
            lastMessage: String?,
            moveAppToBackground: Boolean = false
        ): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            val serviceIntent = Intent(context, FloatingCompanionService::class.java).apply {
                putExtra(EXTRA_CHARACTER_ID, character.id)
                putExtra(EXTRA_LAST_MESSAGE, lastMessage ?: "")
            }
            return try {
                ContextCompat.startForegroundService(context, serviceIntent)
                if (moveAppToBackground && context is android.app.Activity) {
                    context.moveTaskToBack(true)
                }
                true
            } catch (e: Throwable) {
                Log.w(TAG, "Could not start overlay: ${e.message}")
                false
            }
        }

        /** Used by MainActivity when the user leaves the app (Home / Recents). */
        fun startIfEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("floatingCompanionEnabled", true)) return false
            if (!Settings.canDrawOverlays(context)) return false
            val character = CharacterSpec.fromId(prefs.getString("selectedCharacterId", null) ?: CharacterSpec.MOCHI.id)
            return startOrRequestSystemOverlay(context, character, lastMessage = null)
        }

        fun stopSystemOverlay(context: Context) {
            try {
                context.stopService(Intent(context, FloatingCompanionService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Overlay UI
// ----------------------------------------------------------------------------------------------

private val OverlayCharacterWidth = 84.dp
private val OverlayCharacterHeight = 84.dp

@Composable
private fun OverlayContent(
    character: CharacterSpec,
    controller: CharacterController,
    bubbleText: String?,
    isThinking: Boolean,
    isListening: Boolean,
    partialTranscript: String,
    pendingAction: PendingVoiceAction?,
    quickAskOpen: Boolean,
    dockedRight: Boolean,
    onTapCharacter: () -> Unit,
    onLongPressCharacter: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
    onSendPrompt: (String) -> Unit,
    onMicInQuickAsk: () -> Unit,
    onReaction: (String) -> Unit,
    onCancelPending: () -> Unit,
    onRunPendingNow: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit
) {
    val view = LocalView.current
    val align = if (dockedRight) Alignment.End else Alignment.Start
    val accent = character.accent
    val firstName = character.displayName.substringAfter(" ")

    Column(
        horizontalAlignment = align,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        // What she is saying / doing. Only one card at a time, newest state wins.
        when {
            pendingAction != null -> CountdownCard(pendingAction, accent, onCancelPending, onRunPendingNow)
            quickAskOpen -> QuickAskCard(
                firstName = firstName,
                accent = accent,
                onInputFocusChanged = onInputFocusChanged,
                onSend = onSendPrompt,
                onMic = onMicInQuickAsk,
                onReaction = onReaction,
                onOpenApp = onOpenApp,
                onClose = onClose
            )
            isListening -> SpeechBubble(
                text = partialTranscript.ifBlank { "Listening…" },
                accent = accent,
                label = "Listening",
                dockedRight = dockedRight
            )
            isThinking -> SpeechBubble(text = "Thinking…", accent = accent, label = firstName, dockedRight = dockedRight)
            bubbleText != null -> SpeechBubble(text = bubbleText, accent = accent, label = firstName, dockedRight = dockedRight)
        }

        // The character itself — transparent, borderless, full body.
        val pulse = rememberInfiniteTransition(label = "listen_pulse")
        val ring by pulse.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "ring"
        )
        Box(
            modifier = Modifier
                .width(OverlayCharacterWidth)
                .height(OverlayCharacterHeight)
                .drawBehind {
                    if (isListening) {
                        // A soft accent halo on the floor while the mic is open.
                        val r = size.width * 0.42f * ring
                        drawOval(
                            color = accent.copy(alpha = 0.28f),
                            topLeft = Offset(size.width / 2 - r, size.height * 0.92f - r * 0.18f),
                            size = androidx.compose.ui.geometry.Size(r * 2, r * 0.36f)
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onTapCharacter()
                        },
                        onLongPress = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongPressCharacter()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.x, amount.y)
                        }
                    )
                }
        ) {
            CharacterStageView(
                character = character,
                controller = controller,
                halfResolutionMode = true,
                fullBodyStandingMode = true,
                showAura = false,
                handleTaps = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SpeechBubble(text: String, accent: Color, label: String, dockedRight: Boolean) {
    val shape = if (dockedRight) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }
    Column(
        modifier = Modifier
            .widthIn(max = 236.dp)
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(shape)
            .background(MochiTokens.sheet)
            .border(1.dp, MochiTokens.hairline, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Text(label, style = MochiType.Label.copy(fontWeight = FontWeight.W600), color = MochiTokens.accentInk(accent))
        }
        Text(
            text = text,
            style = MochiType.Label.copy(fontSize = MochiType.Label.fontSize * 1.08f),
            color = MochiTokens.textPrimary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CountdownCard(
    pending: PendingVoiceAction,
    accent: Color,
    onCancel: () -> Unit,
    onNow: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .width(248.dp)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(MochiTokens.sheet)
            .border(1.dp, MochiTokens.hairline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${pending.remainingSeconds}",
                    style = MochiType.Body.copy(fontWeight = FontWeight.W700),
                    color = MochiTokens.accentInk(accent)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(pending.title, style = MochiType.Label.copy(fontWeight = FontWeight.W600), color = MochiTokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Starting in ${pending.remainingSeconds}s", style = MochiType.Label, color = MochiTokens.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OverlayPillButton("Cancel", MochiTokens.dangerSolid, Color.White, Modifier.weight(1f), onCancel)
            OverlayPillButton("Now", MochiTokens.card2, MochiTokens.textPrimary, Modifier.weight(1f), onNow)
        }
    }
}

@Composable
private fun QuickAskCard(
    firstName: String,
    accent: Color,
    onInputFocusChanged: (Boolean) -> Unit,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    onReaction: (String) -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .width(264.dp)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(MochiTokens.sheet)
            .border(1.dp, MochiTokens.hairline, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ask $firstName", style = MochiType.Body.copy(fontWeight = FontWeight.W600), color = MochiTokens.textPrimary, modifier = Modifier.weight(1f))
            OverlayIconButton(Icons.Outlined.OpenInFull, "Open app", onOpenApp)
            Spacer(Modifier.width(6.dp))
            OverlayIconButton(Icons.Outlined.Close, "Hide companion", onClose)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(CircleShape)
                .background(MochiTokens.card2)
                .border(1.dp, MochiTokens.hairline, CircleShape)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (input.isEmpty()) {
                    Text("Type a question…", style = MochiType.Label, color = MochiTokens.textTertiary, maxLines = 1)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = MochiType.Label.copy(color = MochiTokens.textPrimary),
                    cursorBrush = SolidColor(accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        input.trim().takeIf { it.isNotEmpty() }?.let { input = ""; onSend(it) }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { onInputFocusChanged(it.isFocused) }
                )
            }
            val canSend = input.isNotBlank()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable {
                        if (canSend) {
                            val text = input.trim(); input = ""; onSend(text)
                        } else {
                            onMic()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (canSend) Icons.AutoMirrored.Outlined.Send else Icons.Outlined.Mic,
                    contentDescription = if (canSend) "Send" else "Talk",
                    tint = MochiTokens.onAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Wave" to "wave", "Dance" to "dance", "Cheer" to "happy").forEach { (label, clip) ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MochiTokens.card2)
                        .clickable { onReaction(clip) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(label, style = MochiType.Label, color = MochiTokens.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun OverlayIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MochiTokens.card2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = MochiTokens.textSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun OverlayPillButton(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MochiType.Label.copy(fontWeight = FontWeight.W600), color = fg)
    }
}

/** The bottom-centre "drop here to close" circle shown while dragging the companion. */
@Composable
private fun DismissTarget(active: Boolean) {
    val scale by androidx.compose.animation.core.animateFloatAsState(if (active) 1.25f else 1f, label = "dismiss_scale")
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
    }, contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier = Modifier
                .padding(bottom = 52.dp)
                .size(64.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(if (active) Color(0xFFD93F45) else Color(0xCC1C1E25))
                .border(2.dp, Color.White.copy(alpha = if (active) 0.9f else 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Drop here to close", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}
