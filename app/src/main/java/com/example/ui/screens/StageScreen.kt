package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CompanionActionType
import com.example.data.CompanionChatTurn
import com.example.ui.character.CharacterController
import com.example.ui.character.CharacterRuntimeState
import com.example.ui.character.CharacterSpec
import com.example.ui.character.CharacterStageView
import com.example.ui.character.MiniCompanion3DAvatar
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LocalCharacterAccent
import com.example.ui.theme.LocalVisualStyle
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiSpacing
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType
import com.example.ui.theme.MochiVisualStyle
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Screen 1 — Stage (Home) & Screen 3 — Conversation Bottom Sheet:
 * - Strictly aligned with DESIGN.md, CHARACTERS.md, and the uploaded Home & Talking/Acting reference designs.
 * - Incorporates dedicated layout containers for:
 *   1. Status Bar (`stage_status_bar`) with Companion Picker, Mood Indicator (`stage_mood_indicator`), and Navigation Controls
 *   2. 3D Mochi Character Stage (`stage_character_container`) filling the top 62% of the screen
 *   3. Conversation Glass Bottom Sheet (`conversation_glass_sheet`) with auto-executing 3-second countdown
 *   4. Quick Actions & Voice/Text Input Bar (`stage_input_bar`) with continuous "Hi Mochi" wake-word support
 */
@Composable
fun StageScreen(
    character: CharacterSpec,
    controller: CharacterController,
    chatHistory: List<CompanionChatTurn>,
    isThinking: Boolean,
    showConversationSheet: Boolean,
    onToggleConversationSheet: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onOpenCharacterPicker: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenFridgeChef: () -> Unit = onOpenTools,
    onOpenQuests: () -> Unit = onOpenTools,
    onOpenConverter: () -> Unit = onOpenTools,
    onOpenProfile: () -> Unit = {},
    moodToneLabel: String = "Calm & Balanced",
    wakeWordEnabled: Boolean = true,
    executedOrCancelledTurnIds: Set<String> = emptySet(),
    onCancelTurnAction: (String) -> Unit = {},
    onExecuteTurnActionNow: (CompanionChatTurn) -> Unit = {},
    visualStyle: MochiVisualStyle = LocalVisualStyle.current,
    onSelectVisualStyle: (MochiVisualStyle) -> Unit = {},
    isFloatingCompanionMode: Boolean = false,
    onToggleFloatingCompanionMode: (Boolean) -> Unit = {},
    isListening: Boolean = false,
    livePartialTranscript: String = "",
    onMicTap: () -> Unit = {},
    recentEntry: com.example.data.LibraryEntry? = null,
    onOpenRecent: (com.example.data.LibraryEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val accent = LocalCharacterAccent.current
    val runtimeState by controller.state.collectAsStateWithLifecycle()
    var draftText by rememberSaveable { mutableStateOf("") }
    // The mic belongs to the shared VoiceCommandService; this screen only shows its state.
    val isListeningMic = isListening
    LaunchedEffect(livePartialTranscript) {
        if (livePartialTranscript.isNotBlank()) draftText = livePartialTranscript
    }
    LaunchedEffect(isListening) {
        if (isListening) {
            controller.transitionTo(CharacterRuntimeState.Listening)
        } else if (controller.state.value is CharacterRuntimeState.Listening) {
            controller.transitionTo(CharacterRuntimeState.Idle)
            draftText = ""
        }
    }
    val textPrimary = if (visualStyle.isLight) MochiTokens.lightTextPrimary else MochiTokens.textPrimary
    val textSecondary = if (visualStyle.isLight) MochiTokens.lightTextSecondary else MochiTokens.textSecondary
    val hairline = if (visualStyle.isLight) MochiTokens.lightHairline else MochiTokens.hairline
    val surfaceRaised = if (visualStyle.isLight) MochiTokens.lightBgRaised else MochiTokens.bgRaised

    val density = androidx.compose.ui.platform.LocalDensity.current
    var controlsHeightPx by remember { mutableStateOf(0) }

    fun triggerVoiceMic() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onMicTap()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("stage_screen_scaffold")
    ) {
        // 1. Mochi 3D Character Stage Container (sized so bottom controls never overlap the 3D character)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Fills exactly the space between the top bar and the controls, so the status
                // pill can never sit on top of the greeting (it used to overlap on most phones).
                .then(
                    if (showConversationSheet) Modifier.fillMaxHeight(0.40f)
                    else Modifier.fillMaxHeight().padding(bottom = with(density) { controlsHeightPx.toDp() } + 8.dp)
                )
                .padding(top = 60.dp)
                .align(Alignment.TopCenter)
                .testTag("stage_character_container")
        ) {
            CharacterStageView(
                character = character,
                controller = controller,
                modifier = Modifier.fillMaxSize()
            )

            // Live Mood & Voice State Indicator Pill directly below character
            val stateBadgeText = when {
                isThinking || runtimeState is CharacterRuntimeState.Thinking -> "Thinking..."
                isListeningMic || runtimeState is CharacterRuntimeState.Listening -> "Listening..."
                runtimeState is CharacterRuntimeState.Talking -> "Speaking"
                wakeWordEnabled -> "\"Hi Mochi\" · $moodToneLabel"
                else -> moodToneLabel
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .clip(MochiShapes.pill)
                    .background(surfaceRaised.copy(alpha = 0.92f))
                    .border(1.dp, hairline, MochiShapes.pill)
                    .clickable {
                        if (wakeWordEnabled && !isListeningMic) {
                            triggerVoiceMic()
                        } else {
                            onToggleConversationSheet(true)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("stage_live_speech_bubble"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = "Mood and voice status indicator",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stateBadgeText,
                    style = MochiType.Label,
                    color = textPrimary,
                    modifier = Modifier.testTag("stage_mood_indicator")
                )
            }
        }

        // 2. Top Status Bar Container: Top-left companion avatar pill + Top-right Floating / Profile / Tools buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MochiSpacing.screenPadding, vertical = MochiSpacing.xs)
                .align(Alignment.TopCenter)
                .testTag("stage_status_bar"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Top-left: Companion Avatar Button (opens Character Picker)
            Row(
                modifier = Modifier
                    .height(MochiSpacing.minTouchTarget)
                    .clip(MochiShapes.pill)
                    .background(surfaceRaised.copy(alpha = 0.92f))
                    .border(1.dp, hairline, MochiShapes.pill)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onOpenCharacterPicker()
                    }
                    .padding(start = 6.dp, end = 12.dp)
                    .testTag("stage_character_picker_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniCompanion3DAvatar(
                    character = character,
                    modifier = Modifier.size(36.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        text = character.displayName.substringAfter(" "),
                        style = MochiType.Body,
                        color = textPrimary
                    )
                    Text(
                        text = "${character.category.label} companion",
                        style = MochiType.Label,
                        color = textSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Choose companion",
                    tint = textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Top-right: When conversation sheet is open, show Close X; otherwise Floating, Profile & Tools
            if (showConversationSheet) {
                Box(
                    modifier = Modifier
                        .size(MochiSpacing.minTouchTarget)
                        .clip(MochiShapes.pill)
                        .background(surfaceRaised.copy(alpha = 0.92f))
                        .border(1.dp, hairline, MochiShapes.pill)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onToggleConversationSheet(false)
                        }
                        .testTag("close_conversation_sheet"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close conversation",
                        tint = textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Floating Companion Mode button
                    Box(
                        modifier = Modifier
                            .size(MochiSpacing.minTouchTarget)
                            .clip(MochiShapes.pill)
                            .background(surfaceRaised.copy(alpha = 0.92f))
                            .border(1.dp, hairline, MochiShapes.pill)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onToggleFloatingCompanionMode(true)
                            }
                            .testTag("stage_float_companion_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PictureInPictureAlt,
                            contentDescription = "Floating companion",
                            tint = textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Profile & Settings button
                    Box(
                        modifier = Modifier
                            .size(MochiSpacing.minTouchTarget)
                            .clip(MochiShapes.pill)
                            .background(surfaceRaised.copy(alpha = 0.92f))
                            .border(1.dp, hairline, MochiShapes.pill)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onOpenProfile()
                            }
                            .testTag("stage_profile_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonOutline,
                            contentDescription = "Profile and settings",
                            tint = textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                }
            }
        }

        // 3. Screen 3 — Conversation Glass Bottom Sheet (matches Home - talking and acting.png & DESIGN.md)
        AnimatedVisibility(
            visible = showConversationSheet,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                initialOffsetY = { it / 2 }
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                targetOffsetY = { it / 2 }
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 78.dp)
        ) {
            GlassSurface(
                shape = MochiShapes.sheet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MochiSpacing.screenPadding)
                    .heightIn(min = 240.dp, max = 420.dp)
                    .testTag("conversation_glass_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MochiSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm)
                ) {
                    // Drag handle pill
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(MochiShapes.pill)
                            .background(hairline)
                            .align(Alignment.CenterHorizontally)
                            .clickable { onToggleConversationSheet(false) }
                    )

                    if (isThinking) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = accent,
                            trackColor = hairline
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MochiSpacing.sm),
                        contentPadding = PaddingValues(vertical = MochiSpacing.unit)
                    ) {
                        items(chatHistory.takeLast(8), key = { it.id }) { turn ->
                            val alreadyHandled = executedOrCancelledTurnIds.contains(turn.id)
                            ConversationTurnItem(
                                turn = turn,
                                character = character,
                                accentColor = accent,
                                isAlreadyExecutedOrCancelled = alreadyHandled,
                                onCancelAction = {
                                    onCancelTurnAction(turn.id)
                                },
                                onExecuteActionNow = {
                                    onExecuteTurnActionNow(turn)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Bottom Controls (Greeting + 3 Suggestion Chips + Quick Actions + GlassSurface Input Bar)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onSizeChanged { controlsHeightPx = it.height }
                .padding(
                    start = MochiSpacing.screenPadding,
                    end = MochiSpacing.screenPadding,
                    bottom = MochiSpacing.sm
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!showConversationSheet) {
                // Greeting header matching Home_7dda243e.png
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleConversationSheet(true) }
                        .testTag("stage_conversation_toggle"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = greetingForNow(),
                        style = MochiType.Label,
                        color = textSecondary
                    )
                    Text(
                        text = "How can I help tonight?",
                        style = MochiType.Title,
                        color = textPrimary
                    )
                }

                // Up to 3 suggestion chips (strictly per DESIGN.md)
                val suggestions = remember(character) {
                    listOf("What's for dinner?", "Split last night's bill", "Message someone") +
                        character.defaultSuggestions
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
                ) {
                    suggestions.take(3).forEachIndexed { index, suggestion ->
                        Box(
                            modifier = Modifier
                                .clip(MochiShapes.pill)
                                .background(surfaceRaised.copy(alpha = 0.92f))
                                .border(1.dp, hairline, MochiShapes.pill)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onToggleConversationSheet(true)
                                    onSendMessage(suggestion)
                                }
                                .padding(horizontal = MochiSpacing.md, vertical = 10.dp)
                                .testTag("stage_suggestion_chip_$index")
                        ) {
                            Text(
                                text = suggestion,
                                style = MochiType.Label,
                                color = textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Quick actions 4-column row matching Home_7dda243e.png + HashRouter views (Call, Message, Pantry/Fridge Chef, Quests)
                Text(
                    text = "Quick actions",
                    style = MochiType.Label,
                    color = textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomeQuickActionTile(
                        label = "Call",
                        icon = Icons.Outlined.Call,
                        accent = accent,
                        surfaceRaised = surfaceRaised,
                        hairline = hairline,
                        textPrimary = textPrimary,
                        onClick = {
                            onToggleConversationSheet(true)
                            onSendMessage("I want to make a call")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    HomeQuickActionTile(
                        label = "Message",
                        icon = Icons.Outlined.ChatBubbleOutline,
                        accent = accent,
                        surfaceRaised = surfaceRaised,
                        hairline = hairline,
                        textPrimary = textPrimary,
                        onClick = {
                            onToggleConversationSheet(true)
                            onSendMessage("I want to send a message")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    HomeQuickActionTile(
                        label = "Fridge Chef",
                        icon = Icons.Outlined.Restaurant,
                        accent = accent,
                        surfaceRaised = surfaceRaised,
                        hairline = hairline,
                        textPrimary = textPrimary,
                        onClick = onOpenFridgeChef,
                        modifier = Modifier.weight(1f)
                    )
                    HomeQuickActionTile(
                        label = "Convert",
                        icon = Icons.Outlined.SwapHoriz,
                        accent = accent,
                        surfaceRaised = surfaceRaised,
                        hairline = hairline,
                        textPrimary = textPrimary,
                        onClick = onOpenConverter,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Most recent real Library item (hidden when there is none).
                if (recentEntry != null) Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MochiShapes.card)
                        .background(surfaceRaised.copy(alpha = 0.92f))
                        .border(1.dp, hairline, MochiShapes.card)
                        .clickable { onOpenRecent(recentEntry) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MochiShapes.card)
                            .background(MochiTokens.accentMarco.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Restaurant,
                            contentDescription = null,
                            tint = MochiTokens.accentMarco,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recentEntry.title,
                            style = MochiType.Body,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = recentEntry.subtitle,
                            style = MochiType.Label,
                            color = textSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            StageInputBar(
                value = draftText,
                onValueChange = { draftText = it },
                placeholder = if (showConversationSheet) {
                    "Reply to ${character.displayName.substringAfter(" ")}..."
                } else {
                    "Ask ${character.displayName.substringAfter(" ")} anything..."
                },
                isListening = isListeningMic,
                onMicClick = { triggerVoiceMic() },
                onSendClick = {
                    val clean = draftText.trim()
                    if (clean.isNotEmpty()) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        draftText = ""
                        onToggleConversationSheet(true)
                        onSendMessage(clean)
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeQuickActionTile(
    label: String,
    icon: ImageVector,
    accent: Color,
    surfaceRaised: Color,
    hairline: Color,
    textPrimary: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .clip(MochiShapes.card)
            .background(surfaceRaised.copy(alpha = 0.92f))
            .border(1.dp, hairline, MochiShapes.card)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MochiType.Label,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isListening: Boolean,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask Elena anything..."
) {
    val accent = LocalCharacterAccent.current
    val visualStyle = LocalVisualStyle.current
    val textPrimary = if (visualStyle.isLight) MochiTokens.lightTextPrimary else MochiTokens.textPrimary
    val textTertiary = if (visualStyle.isLight) MochiTokens.lightTextTertiary else MochiTokens.textTertiary
    var isPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "input_btn_scale"
    )

    GlassSurface(
        shape = MochiShapes.pill,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("stage_input_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MochiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MochiSpacing.xs)
        ) {
            // Text input field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MochiSpacing.sm),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = if (isListening) "Listening..." else placeholder,
                        style = MochiType.Body,
                        color = textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MochiType.Body.copy(color = textPrimary),
                    cursorBrush = SolidColor(accent),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stage_text_field")
                )
            }

            val canSend = value.trim().isNotEmpty()

            // One round button: mic while empty, send once there is text (as in the design).
            if (!canSend) Box(
                modifier = Modifier
                    .size(MochiSpacing.minTouchTarget)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .clip(MochiShapes.pill)
                    .background(if (!canSend) accent else MochiTokens.hairline)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            },
                            onTap = { onMicClick() }
                        )
                    }
                    .testTag("stage_mic_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = "Voice input",
                    tint = MochiTokens.onAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (canSend) Box(
                modifier = Modifier
                    .size(MochiSpacing.minTouchTarget)
                    .clip(MochiShapes.pill)
                    .background(if (canSend) accent else MochiTokens.hairline)
                    .clickable(enabled = canSend) { onSendClick() }
                    .testTag("stage_send_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "Send message",
                    tint = MochiTokens.onAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ConversationTurnItem(
    turn: CompanionChatTurn,
    character: CharacterSpec,
    accentColor: Color,
    isAlreadyExecutedOrCancelled: Boolean,
    onCancelAction: () -> Unit,
    onExecuteActionNow: () -> Unit
) {
    val visualStyle = LocalVisualStyle.current
    val textPrimary = if (visualStyle.isLight) MochiTokens.lightTextPrimary else MochiTokens.textPrimary
    val textSecondary = if (visualStyle.isLight) MochiTokens.lightTextSecondary else MochiTokens.textSecondary
    val surfaceRaised = if (visualStyle.isLight) MochiTokens.lightBgRaised else MochiTokens.bgRaised
    val hairline = if (visualStyle.isLight) MochiTokens.lightHairline else MochiTokens.hairline

    var countdownSeconds by remember(turn.id) { mutableIntStateOf(3) }
    var countdownCancelled by remember(turn.id) { mutableStateOf(false) }

    val hasExternalAction = !turn.senderIsUser &&
        turn.actionType != CompanionActionType.NONE &&
        turn.actionType != CompanionActionType.NAVIGATE_ROUTE &&
        turn.actionType != CompanionActionType.SPEAK_LOUD

    LaunchedEffect(turn.id, hasExternalAction, countdownCancelled, isAlreadyExecutedOrCancelled) {
        if (hasExternalAction && !countdownCancelled && !isAlreadyExecutedOrCancelled) {
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds--
            }
            if (!countdownCancelled && !isAlreadyExecutedOrCancelled && countdownSeconds == 0) {
                onExecuteActionNow()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (turn.senderIsUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (turn.senderIsUser) {
            Box(
                modifier = Modifier
                    .clip(MochiShapes.card)
                    .background(surfaceRaised)
                    .border(1.dp, hairline, MochiShapes.card)
                    .padding(horizontal = MochiSpacing.md, vertical = MochiSpacing.sm)
            ) {
                Text(
                    text = turn.message,
                    style = MochiType.Body,
                    color = textPrimary
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniCompanion3DAvatar(
                    character = character,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = turn.message,
                    style = MochiType.Body,
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Action Countdown Card matching Home - talking and acting.png
            if (hasExternalAction && !countdownCancelled) {
                val recipientName = turn.actionRecipient
                val payloadPreview = turn.actionPayload.ifBlank { turn.message }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MochiShapes.card)
                        .background(surfaceRaised)
                        .border(1.dp, accentColor.copy(alpha = 0.45f), MochiShapes.card)
                        .padding(MochiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(hairline),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = recipientName.take(1).uppercase(),
                                    style = MochiType.Body,
                                    color = textPrimary
                                )
                            }
                            Column {
                                Text(
                                    text = recipientName,
                                    style = MochiType.Body,
                                    color = textPrimary
                                )
                                Text(
                                    text = turn.actionType.channelSubtitle,
                                    style = MochiType.Label,
                                    color = textSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(2.dp, accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAlreadyExecutedOrCancelled || countdownSeconds <= 0) "✓" else countdownSeconds.toString(),
                                style = MochiType.Body,
                                color = textPrimary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MochiShapes.card)
                            .background(if (visualStyle.isLight) MochiTokens.lightBg else MochiTokens.bg)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = payloadPreview,
                            style = MochiType.Body,
                            color = textPrimary
                        )
                    }

                    Text(
                        text = if (isAlreadyExecutedOrCancelled || countdownSeconds <= 0) {
                            "Executed via ${turn.actionType.label}."
                        } else {
                            "Executing ${turn.actionType.label} in $countdownSeconds seconds. Say \"stop\" or tap Cancel."
                        },
                        style = MochiType.Label,
                        color = textSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(MochiSpacing.minTouchTarget)
                                .clip(MochiShapes.pill)
                                .border(1.dp, hairline, MochiShapes.pill)
                                .clickable {
                                    countdownCancelled = true
                                    onCancelAction()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                style = MochiType.Body,
                                color = textPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(MochiSpacing.minTouchTarget)
                                .clip(MochiShapes.pill)
                                .background(accentColor)
                                .clickable { onExecuteActionNow() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Open now",
                                style = MochiType.Body,
                                color = MochiTokens.bg
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun greetingForNow(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}
