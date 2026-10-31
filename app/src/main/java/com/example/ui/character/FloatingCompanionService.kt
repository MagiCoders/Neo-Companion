package com.example.ui.character

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
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
import com.example.ai.CadenceAiService
import com.example.ai.OmnichannelMediaBridge
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiTheme
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * True System-Wide Frameless 3D Companion Overlay Service (`TYPE_APPLICATION_OVERLAY`):
 * Renders the 3D `.glb` character + live thinking/speaking bubble + voice/text ask bar
 * directly over the Android OS home screen and other apps (never a square PiP video box).
 */
class FloatingCompanionService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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
    private var speechRecognizer: SpeechRecognizer? = null

    private val controller = CharacterController(serviceScope)
    private val aiService = CadenceAiService()

    private var activeCharacter by mutableStateOf(CharacterSpec.ELENA)
    private var latestBubbleMessage by mutableStateOf("I'm floating right on your screen! Tap me or ask anything.")
    private var isThinkingState by mutableStateOf(false)
    private var isListeningState by mutableStateOf(false)
    private var showBubbleCard by mutableStateOf(true)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        startForegroundNotification()

        ttsEngine = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.getDefault()
            }
        }
        setupSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val charId = intent?.getStringExtra(EXTRA_CHARACTER_ID) ?: CharacterSpec.ELENA.id
        activeCharacter = CharacterSpec.fromId(charId)
        intent?.getStringExtra(EXTRA_LAST_MESSAGE)?.takeIf { it.isNotBlank() }?.let {
            latestBubbleMessage = it
        }

        if (Settings.canDrawOverlays(this)) {
            attachSystemOverlayWindowIfNeeded()
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        } else {
            stopSelf()
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "mochi_floating_companion_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MochiLife 3D Floating Companion",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("${activeCharacter.displayName} • Active on Screen")
            .setContentText("Tap to return to full 4K Studio Stage")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }

    private fun setupSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListeningState = true
                            controller.transitionTo(CharacterRuntimeState.Listening)
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            isListeningState = false
                        }
                        override fun onError(error: Int) {
                            isListeningState = false
                            controller.transitionTo(CharacterRuntimeState.Idle)
                        }
                        override fun onResults(results: Bundle?) {
                            isListeningState = false
                            val spoken = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (spoken.isNotEmpty()) {
                                handleOverlayPrompt(spoken)
                            } else {
                                controller.transitionTo(CharacterRuntimeState.Idle)
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun startOverlayVoiceRecognition() {
        val sr = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            sr.startListening(intent)
        } catch (_: Throwable) {
            isListeningState = false
        }
    }

    private fun handleOverlayPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || isThinkingState) return
        serviceScope.launch {
            isThinkingState = true
            latestBubbleMessage = "Thinking about \"$clean\"..."
            controller.transitionTo(CharacterRuntimeState.Thinking)
            try {
                val aiTurn = aiService.chatWithMochi(
                    userMessage = clean,
                    companionName = activeCharacter.displayName,
                    userLevel = 1
                )
                latestBubbleMessage = aiTurn.message
                val spokenText = if (aiTurn.spokenConfirmation.isNotBlank() &&
                    aiTurn.actionType != com.example.data.CompanionActionType.NONE
                ) {
                    "${aiTurn.spokenConfirmation} ${aiTurn.message}"
                } else {
                    aiTurn.message
                }
                controller.speakWithLipSync(
                    context = applicationContext,
                    tts = ttsEngine,
                    text = spokenText,
                    character = activeCharacter
                )
                OmnichannelMediaBridge.autoExecuteCompanionAction(
                    context = applicationContext,
                    tts = ttsEngine,
                    turn = aiTurn
                )
            } finally {
                isThinkingState = false
            }
        }
    }

    private fun setOverlayWindowFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val lp = layoutParams ?: return
        val newFlags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        if (lp.flags != newFlags) {
            lp.flags = newFlags
            try {
                wm.updateViewLayout(view, lp)
            } catch (_: Throwable) {
            }
        }
    }

    private fun attachSystemOverlayWindowIfNeeded() {
        if (composeView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.coerceAtLeast(720)
        val screenH = metrics.heightPixels.coerceAtLeast(1280)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenW * 0.04f).roundToInt()
            y = (screenH * 0.20f).roundToInt()
        }
        layoutParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingCompanionService)
            setViewTreeViewModelStoreOwner(this@FloatingCompanionService)
            setViewTreeSavedStateRegistryOwner(this@FloatingCompanionService)
            setContent {
                MochiTheme(characterAccent = activeCharacter.accent) {
                    SystemOverlayContent(
                        character = activeCharacter,
                        controller = controller,
                        message = latestBubbleMessage,
                        isThinking = isThinkingState,
                        isListening = isListeningState,
                        showBubble = showBubbleCard,
                        onToggleBubble = { showBubbleCard = !showBubbleCard },
                        onDragWindow = { dx, dy ->
                            params.x = (params.x + dx.roundToInt()).coerceIn(0, screenW - 220)
                            params.y = (params.y + dy.roundToInt()).coerceIn(48, screenH - 260)
                            try {
                                wm.updateViewLayout(this, params)
                            } catch (_: Throwable) {
                            }
                        },
                        onSnapToEdge = {
                            val mid = screenW / 2
                            params.x = if (params.x + 180 >= mid) (screenW - 420).coerceAtLeast(12) else 12
                            try {
                                wm.updateViewLayout(this, params)
                            } catch (_: Throwable) {
                            }
                        },
                        onInputFocusChanged = { focused ->
                            setOverlayWindowFocusable(focused)
                        },
                        onSendPrompt = { text ->
                            setOverlayWindowFocusable(false)
                            handleOverlayPrompt(text)
                        },
                        onStartVoice = { startOverlayVoiceRecognition() },
                        onOpenFullStage = {
                            val openIntent = Intent(this@FloatingCompanionService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(openIntent)
                            stopSelf()
                        },
                        onCloseOverlay = {
                            stopSelf()
                        }
                    )
                }
            }
        }
        composeView = view
        try {
            wm.addView(view, params)
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        try {
            composeView?.let { windowManager?.removeView(it) }
        } catch (_: Throwable) {
        }
        composeView = null
        try {
            speechRecognizer?.destroy()
        } catch (_: Throwable) {
        }
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
        private const val NOTIFICATION_ID = 4409
        const val EXTRA_CHARACTER_ID = "extra_character_id"
        const val EXTRA_LAST_MESSAGE = "extra_last_message"

        fun startOrRequestSystemOverlay(
            context: Context,
            character: CharacterSpec,
            lastMessage: String?,
            moveAppToBackground: Boolean = false
        ): Boolean {
            if (!Settings.canDrawOverlays(context)) {
                return false
            }
            val serviceIntent = Intent(context, FloatingCompanionService::class.java).apply {
                putExtra(EXTRA_CHARACTER_ID, character.id)
                putExtra(EXTRA_LAST_MESSAGE, lastMessage ?: "")
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                if (moveAppToBackground && context is android.app.Activity) {
                    context.moveTaskToBack(true)
                }
                return true
            } catch (_: Throwable) {
                return false
            }
        }

        fun stopSystemOverlay(context: Context) {
            try {
                context.stopService(Intent(context, FloatingCompanionService::class.java))
            } catch (_: Throwable) {
            }
        }
    }
}

@Composable
private fun SystemOverlayContent(
    character: CharacterSpec,
    controller: CharacterController,
    message: String,
    isThinking: Boolean,
    isListening: Boolean,
    showBubble: Boolean,
    onToggleBubble: () -> Unit,
    onDragWindow: (Float, Float) -> Unit,
    onSnapToEdge: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
    onSendPrompt: (String) -> Unit,
    onStartVoice: () -> Unit,
    onOpenFullStage: () -> Unit,
    onCloseOverlay: () -> Unit
) {
    val runtimeState by controller.state.collectAsStateWithLifecycle()
    var quickInput by remember { mutableStateOf("") }
    val accent = character.accent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(6.dp)
    ) {
        // Frameless 3D Character Orb with Soft Studio Halo
        Box(
            modifier = Modifier
                .size(138.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { onSnapToEdge() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDragWindow(dragAmount.x, dragAmount.y)
                            controller.onStageTouchPoint(
                                (dragAmount.x / 20f).coerceIn(-1f, 1f),
                                (dragAmount.y / 20f).coerceIn(-1f, 1f)
                            )
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MochiTokens.keyLightIvory.copy(alpha = 0.24f),
                                accent.copy(alpha = 0.22f),
                                MochiTokens.bg.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(1.5.dp, accent.copy(alpha = 0.75f), CircleShape)
            )

            CharacterStageView(
                character = character,
                controller = controller,
                halfResolutionMode = true,
                modifier = Modifier.fillMaxSize()
            )

            // Tap badge at bottom of character orb to show/hide speech bubble
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(MochiShapes.pill)
                    .background(MochiTokens.glassTint)
                    .border(1.dp, accent.copy(alpha = 0.7f), MochiShapes.pill)
                    .clickable { onToggleBubble() }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when {
                        isThinking || runtimeState is CharacterRuntimeState.Thinking -> "Thinking..."
                        isListening || runtimeState is CharacterRuntimeState.Listening -> "Listening..."
                        runtimeState is CharacterRuntimeState.Talking -> "Speaking"
                        else -> character.displayName
                    },
                    style = MochiType.Label,
                    color = MochiTokens.textPrimary
                )
            }
        }

        // Attached Optical Crystal Thinking / Speaking & Quick-Ask Bubble
        if (showBubble) {
            Column(
                modifier = Modifier
                    .width(236.dp)
                    .clip(MochiShapes.card)
                    .background(MochiTokens.glassTint)
                    .border(1.dp, MochiTokens.glassTopHighlight, MochiShapes.card)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = character.displayName,
                        style = MochiType.Label,
                        color = accent
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MochiTokens.bgRaised)
                                .clickable { onOpenFullStage() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInFull,
                                contentDescription = "Open full studio",
                                tint = MochiTokens.textPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MochiTokens.bgRaised)
                                .clickable { onCloseOverlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close overlay",
                                tint = MochiTokens.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (isThinking) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = accent,
                        trackColor = MochiTokens.hairline
                    )
                }

                Text(
                    text = message,
                    style = MochiType.Label,
                    color = MochiTokens.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                // Quick Reaction Pills (Wave, Dance, Clone 3x, Celebrate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OverlayMiniChip("Wave", Icons.Outlined.PanTool, accent) {
                        controller.react("wave")
                    }
                    OverlayMiniChip("Dance", Icons.Outlined.MusicNote, MochiTokens.primaryGold) {
                        controller.react("dance")
                    }
                    OverlayMiniChip("Clone", Icons.Outlined.PeopleOutline, accent) {
                        controller.react("clone")
                    }
                    OverlayMiniChip("Burst", Icons.Outlined.AutoAwesome, MochiTokens.primaryGold) {
                        controller.react("happy")
                    }
                }

                // Voice Mic + Text Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(MochiShapes.pill)
                        .background(MochiTokens.bg)
                        .border(1.dp, accent.copy(alpha = 0.55f), MochiShapes.pill)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isListening) accent.copy(alpha = 0.3f) else MochiTokens.bgRaised)
                            .clickable { onStartVoice() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mic,
                            contentDescription = "Voice input",
                            tint = if (isListening) accent else MochiTokens.textPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (quickInput.isEmpty()) {
                            Text(
                                text = "Ask or call...",
                                style = MochiType.Label,
                                color = MochiTokens.textTertiary,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = quickInput,
                            onValueChange = { quickInput = it },
                            textStyle = MochiType.Label.copy(color = MochiTokens.textPrimary),
                            cursorBrush = SolidColor(accent),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val clean = quickInput.trim()
                                    if (clean.isNotEmpty()) {
                                        quickInput = ""
                                        onSendPrompt(clean)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { onInputFocusChanged(it.isFocused) }
                        )
                    }

                    val canSend = quickInput.trim().isNotEmpty()
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (canSend) accent else MochiTokens.hairline)
                            .clickable(enabled = canSend) {
                                val clean = quickInput.trim()
                                if (clean.isNotEmpty()) {
                                    quickInput = ""
                                    onSendPrompt(clean)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Send",
                            tint = if (canSend) MochiTokens.bg else MochiTokens.textTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayMiniChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(MochiShapes.pill)
            .background(MochiTokens.bgRaised)
            .border(1.dp, tint.copy(alpha = 0.55f), MochiShapes.pill)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                style = MochiType.Label,
                color = MochiTokens.textPrimary
            )
        }
    }
}
