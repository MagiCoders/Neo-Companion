package com.example.ui.character

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CompanionChatTurn
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.LocalCharacterAccent
import com.example.ui.theme.LocalMochiDarkTheme
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiSpacing
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType
import kotlin.math.roundToInt

enum class FloatingOverlayMode(val label: String) {
    QUICK_ASK("Quick ask"),
    TALKING("Talking"),
    CALL_COUNTDOWN("Call 3s"),
    BUSY_QUIET("Busy mode")
}

/**
 * Interactive 3D Companion Floating Overlay:
 * - Implements CHARACTERS.md floating bubble mode: renders the same character at half resolution,
 *   30 fps, with only Idle and Talking states enabled.
 * - Supports all 4 floating modes from the uploaded design PNGs:
 *   1. Quick ask ("Ask Elena", "Type, or just keep talking...", React row: Wave, Cheer, Calm, Dance, "Open MochiLife ↗")
 *   2. Talking ("Hold Elena for quick ask >", "Elena · speaking |||", "Hi! Opening WhatsApp with your message to Neo.")
 *   3. Call countdown ("Calling Mom · SIM call · mobile" with circular "3" ring, "Cancel", and "Call now")
 *   4. Busy mode over video/game (edge-tucked "E ||| Answering" + "1 saved for later" pills)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveFloatingCompanionOverlay(
    character: CharacterSpec,
    controller: CharacterController,
    latestTurn: CompanionChatTurn?,
    isThinking: Boolean,
    isListening: Boolean,
    onSendMessage: (String) -> Unit,
    onStartVoiceMic: () -> Unit,
    onExpandToFullStage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalCharacterAccent.current
    val isDark = LocalMochiDarkTheme.current
    val textPrimary = MochiTokens.adaptiveTextPrimary(isDark)
    val textSecondary = MochiTokens.adaptiveTextSecondary(isDark)
    val cardBg = MochiTokens.adaptiveBgRaised(isDark)
    val borderCol = MochiTokens.adaptiveHairline(isDark)
    val goldAccent = Color(0xFFE0B462)

    val runtimeState by controller.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var dockedRight by rememberSaveable { mutableStateOf(true) }
    var isEdgePeeked by rememberSaveable { mutableStateOf(false) }
    var showAskBox by rememberSaveable { mutableStateOf(true) }
    var quickAskText by rememberSaveable { mutableStateOf("") }
    var currentMode by rememberSaveable { mutableStateOf(FloatingOverlayMode.QUICK_ASK) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("interactive_floating_companion_overlay")
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(320f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(480f)
        val avatarSizePx = with(density) { 108.dp.toPx() }
        val edgeMarginPx = with(density) { 12.dp.toPx() }

        var rawOffsetX by remember { mutableFloatStateOf(maxW - avatarSizePx - edgeMarginPx) }
        var rawOffsetY by remember { mutableFloatStateOf(maxH * 0.24f) }
        var isDragging by remember { mutableStateOf(false) }

        val targetSnapX = when {
            isDragging -> rawOffsetX
            isEdgePeeked && dockedRight -> maxW - avatarSizePx * 0.42f
            isEdgePeeked && !dockedRight -> -avatarSizePx * 0.58f
            dockedRight -> maxW - avatarSizePx - edgeMarginPx
            else -> edgeMarginPx
        }

        val animatedX by animateFloatAsState(
            targetValue = targetSnapX,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "floating_snap_x"
        )
        val animatedY by animateFloatAsState(
            targetValue = rawOffsetY.coerceIn(
                edgeMarginPx,
                (maxH - avatarSizePx - edgeMarginPx * 4f).coerceAtLeast(edgeMarginPx)
            ),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "floating_snap_y"
        )

        val stateLabel = when {
            isThinking || runtimeState is CharacterRuntimeState.Thinking -> "Thinking"
            isListening || runtimeState is CharacterRuntimeState.Listening -> "Listening"
            runtimeState is CharacterRuntimeState.Talking -> "Speaking"
            else -> "Listening"
        }

        // Busy Mode Edge Pill (matches Floating - busy mode over a video.png & Floating - busy mode - light.png)
        if (currentMode == FloatingOverlayMode.BUSY_QUIET) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                        .background(cardBg)
                        .border(
                            1.dp,
                            borderCol,
                            RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                        )
                        .clickable { currentMode = FloatingOverlayMode.QUICK_ASK }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = character.displayName.take(1),
                            style = MochiType.Body.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF07090F)
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Answering",
                        style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = textPrimary
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                        .background(cardBg)
                        .border(
                            1.dp,
                            borderCol,
                            RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                        .clickable { currentMode = FloatingOverlayMode.TALKING }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(goldAccent)
                    )
                    Text(
                        text = "1 saved for later",
                        style = MochiType.Label,
                        color = textPrimary
                    )
                }
            }
        }

        // Floating Callout Card positioned next to the 3D character
        AnimatedVisibility(
            visible = !isEdgePeeked && showAskBox && currentMode != FloatingOverlayMode.BUSY_QUIET,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val bubbleWidthPx = with(density) { 284.dp.toPx() }
                    val bx = if (dockedRight) {
                        (animatedX - bubbleWidthPx - with(density) { 8.dp.toPx() }).coerceAtLeast(edgeMarginPx)
                    } else {
                        (animatedX + avatarSizePx + with(density) { 8.dp.toPx() })
                            .coerceAtMost(maxW - bubbleWidthPx - edgeMarginPx)
                    }
                    IntOffset(bx.roundToInt(), animatedY.roundToInt())
                }
        ) {
            GlassSurface(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .widthIn(min = 248.dp, max = 288.dp)
                    .testTag("floating_thinking_speech_bubble")
            ) {
                Column(
                    modifier = Modifier.padding(MochiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header with Ask <Name>, Expand button, and Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Ask ${character.displayName.substringBefore(" ")}",
                                style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                                color = textPrimary,
                                maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Text(
                                    text = stateLabel,
                                    style = MochiType.Label,
                                    color = accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(cardBg)
                                    .border(1.dp, borderCol, CircleShape)
                                    .clickable { onExpandToFullStage() }
                                    .testTag("floating_expand_stage_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.OpenInFull,
                                    contentDescription = "Expand to full stage",
                                    tint = textPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(cardBg)
                                    .border(1.dp, borderCol, CircleShape)
                                    .clickable { showAskBox = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close bubble",
                                    tint = textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Mode switcher pills (Quick ask | Talking | Call 3s | Busy mode)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FloatingOverlayMode.entries.forEach { mode ->
                            val selected = currentMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(MochiShapes.pill)
                                    .background(
                                        if (selected) accent.copy(alpha = 0.18f)
                                        else cardBg.copy(alpha = 0.6f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) accent else borderCol,
                                        MochiShapes.pill
                                    )
                                    .clickable { currentMode = mode }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MochiType.Label,
                                    color = if (selected) accent else textSecondary
                                )
                            }
                        }
                    }

                    when (currentMode) {
                        FloatingOverlayMode.TALKING -> {
                            // Matches Floating - talking.png ("Hold Elena for quick ask >" + "Elena · speaking |||" + "Hi! Opening WhatsApp with your message to Neo.")
                            Row(
                                modifier = Modifier
                                    .clip(MochiShapes.pill)
                                    .background(cardBg)
                                    .border(1.dp, borderCol, MochiShapes.pill)
                                    .clickable { currentMode = FloatingOverlayMode.QUICK_ASK }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Hold ${character.displayName.substringBefore(" ")} for quick ask",
                                    style = MochiType.Label,
                                    color = textPrimary
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${character.displayName.substringBefore(" ")} · speaking",
                                    style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                                    color = accent
                                )
                                Icon(
                                    imageVector = Icons.Outlined.GraphicEq,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = latestTurn?.message
                                    ?: "Hi! Opening WhatsApp with your message to Neo.",
                                style = MochiType.Body,
                                color = textPrimary
                            )
                        }

                        FloatingOverlayMode.CALL_COUNTDOWN -> {
                            // Matches Floating - call countdown.png ("Calling Mom · SIM call · mobile", circular "3" ring, "Cancel", "Call now")
                            Text(
                                text = "Calling Mom in 3. Say \"stop\" or tap Cancel.",
                                style = MochiType.Label,
                                color = textSecondary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4B3D42)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "M",
                                            style = MochiType.Body.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Calling Mom",
                                            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                                            color = textPrimary
                                        )
                                        Text(
                                            text = "SIM call · mobile",
                                            style = MochiType.Label,
                                            color = textSecondary
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier.size(44.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            color = borderCol,
                                            style = Stroke(width = 3.dp.toPx())
                                        )
                                        drawArc(
                                            color = accent,
                                            startAngle = -90f,
                                            sweepAngle = 250f,
                                            useCenter = false,
                                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                    Text(
                                        text = "3",
                                        style = MochiType.Body.copy(fontWeight = FontWeight.Bold),
                                        color = textPrimary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(MochiShapes.pill)
                                    .background(if (isDark) Color(0xFFF3F5F9) else Color(0xFF141821))
                                    .clickable { currentMode = FloatingOverlayMode.QUICK_ASK },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cancel",
                                    style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isDark) Color(0xFF07090F) else Color.White
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(MochiShapes.pill)
                                    .background( Color.Transparent)
                                    .border(1.dp, accent.copy(alpha = 0.5f), MochiShapes.pill)
                                    .clickable { onSendMessage("Call Mom now") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Call now",
                                    style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                                    color = accent
                                )
                            }
                        }

                        else -> {
                            // Matches Floating - quick ask.png (React pills: Wave, Cheer, Calm, Dance + Open MochiLife button)
                            if (isThinking) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.5.dp),
                                    color = accent,
                                    trackColor = borderCol
                                )
                            }

                            Text(
                                text = "React",
                                style = MochiType.Label,
                                color = textSecondary
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    Triple("Wave", Icons.Outlined.WavingHand, "wave"),
                                    Triple("Cheer", Icons.Outlined.AutoAwesome, "happy"),
                                    Triple("Calm", Icons.Outlined.Bedtime, "think"),
                                    Triple("Dance", Icons.Outlined.MusicNote, "happy")
                                ).forEach { (label, icon, clip) ->
                                    Row(
                                        modifier = Modifier
                                            .clip(MochiShapes.pill)
                                            .background(cardBg)
                                            .border(1.dp, borderCol, MochiShapes.pill)
                                            .clickable { controller.react(clip) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = textPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = label,
                                            style = MochiType.Label,
                                            color = textPrimary
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(MochiShapes.pill)
                                    .background(goldAccent.copy(alpha = 0.16f))
                                    .border(1.dp, goldAccent.copy(alpha = 0.35f), MochiShapes.pill)
                                    .clickable { onExpandToFullStage() },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Open MochiLife",
                                    style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                                    color = goldAccent
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Icon(
                                    imageVector = Icons.Outlined.NorthEast,
                                    contentDescription = null,
                                    tint = goldAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Interactive Quick-Ask & Voice Input Row ("Type, or just keep talking...", testTags preserved)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(MochiShapes.pill)
                            .background(cardBg)
                            .border(1.dp, borderCol, MochiShapes.pill)
                            .clickable {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).padding(start = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (quickAskText.isEmpty()) {
                                Text(
                                    text = "Type, or just keep talking...",
                                    style = MochiType.Label,
                                    color = textSecondary,
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = quickAskText,
                                onValueChange = { quickAskText = it },
                                textStyle = MochiType.Label.copy(color = textPrimary),
                                cursorBrush = SolidColor(accent),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        val msg = quickAskText.trim()
                                        if (msg.isNotEmpty()) {
                                            quickAskText = ""
                                            keyboardController?.hide()
                                            onSendMessage(msg)
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .testTag("floating_quick_ask_input")
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .clickable { onStartVoiceMic() }
                                .testTag("floating_mic_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "Ask with voice",
                                tint = Color(0xFF07090F),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        val canSend = quickAskText.trim().isNotEmpty()
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (canSend) accent else borderCol)
                                .clickable(enabled = canSend) {
                                    val msg = quickAskText.trim()
                                    if (msg.isNotEmpty()) {
                                        quickAskText = ""
                                        keyboardController?.hide()
                                        onSendMessage(msg)
                                    }
                                }
                                .testTag("floating_send_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = "Send",
                                tint = if (canSend) Color(0xFF07090F) else textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Draggable Full-Body 3D Character Companion Module (matching Floating_-_talking_97d43686.png & Floating_-_quick_ask_f1220c67.png)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
                .size(width = 114.dp, height = 212.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            isEdgePeeked = false
                        },
                        onDragEnd = {
                            isDragging = false
                            val centerX = rawOffsetX + avatarSizePx * 0.5f
                            dockedRight = centerX >= maxW * 0.5f
                            if (rawOffsetX > maxW - avatarSizePx * 0.62f || rawOffsetX < -avatarSizePx * 0.32f) {
                                isEdgePeeked = true
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            rawOffsetX = (rawOffsetX + dragAmount.x).coerceIn(-avatarSizePx * 0.55f, maxW - avatarSizePx * 0.45f)
                            rawOffsetY = (rawOffsetY + dragAmount.y).coerceIn(edgeMarginPx, maxH - avatarSizePx - edgeMarginPx)
                        }
                    )
                }
                .testTag("floating_3d_avatar_orb"),
            contentAlignment = Alignment.Center
        ) {
            // Top "● Listening" / "● Speaking" status pill above the 3D companion head (matches Floating - quick ask.png)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(MochiShapes.pill)
                    .background(cardBg.copy(alpha = 0.94f))
                    .border(1.dp, borderCol, MochiShapes.pill)
                    .clickable { isEdgePeeked = !isEdgePeeked }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("floating_edge_dock_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = if (isEdgePeeked) "Peek" else stateLabel,
                    style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                    color = textPrimary
                )
            }

            // Live Full-Body 3D Character Companion at half resolution & 30fps
            CharacterStageView(
                character = character,
                controller = controller,
                halfResolutionMode = true,
                fullBodyStandingMode = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, bottom = 4.dp)
            )

            // Edge-Peek / Speech Bubble Toggle Tab
            Box(
                modifier = Modifier
                    .align(if (dockedRight) Alignment.CenterStart else Alignment.CenterEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(cardBg)
                    .border(1.dp, accent, CircleShape)
                    .clickable {
                        if (isEdgePeeked) {
                            isEdgePeeked = false
                            showAskBox = true
                        } else {
                            showAskBox = !showAskBox
                        }
                    }
                    .testTag("floating_toggle_bubble_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (dockedRight) {
                        if (isEdgePeeked || !showAskBox) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight
                    } else {
                        if (isEdgePeeked || !showAskBox) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft
                    },
                    contentDescription = "Toggle companion bubble",
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Global Persistent Floating 3D Companion Overlay Module:
 * - Rendered across activity screens whenever `floatingCompanionEnabled` is ON.
 * - Shows a compact draggable 3D companion module on the screen edge with live voice status
 *   ("Hi Mochi · Listening" / "Speaking"), and expands into the full interactive floating
 *   Quick Ask / Talking / Call 3s overlay on tap.
 */
@Composable
fun GlobalFloatingCompanionModule(
    character: CharacterSpec,
    controller: CharacterController,
    latestTurn: CompanionChatTurn?,
    isThinking: Boolean,
    isListening: Boolean,
    isExpandedFullOverlay: Boolean,
    onSetExpandedFullOverlay: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onStartVoiceMic: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isExpandedFullOverlay) {
        InteractiveFloatingCompanionOverlay(
            character = character,
            controller = controller,
            latestTurn = latestTurn,
            isThinking = isThinking,
            isListening = isListening,
            onSendMessage = onSendMessage,
            onStartVoiceMic = onStartVoiceMic,
            onExpandToFullStage = { onSetExpandedFullOverlay(false) },
            modifier = modifier
        )
        return
    }

    val accent = LocalCharacterAccent.current
    val isDark = LocalMochiDarkTheme.current
    val textPrimary = MochiTokens.adaptiveTextPrimary(isDark)
    val cardBg = MochiTokens.adaptiveBgRaised(isDark)
    val borderCol = MochiTokens.adaptiveHairline(isDark)
    val runtimeState by controller.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("global_floating_3d_module")
    ) {
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(320f)
        val maxH = constraints.maxHeight.toFloat().coerceAtLeast(480f)
        val modW = with(density) { 86.dp.toPx() }
        val modH = with(density) { 154.dp.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }

        var offsetX by remember { mutableFloatStateOf(maxW - modW - marginPx) }
        var offsetY by remember { mutableFloatStateOf(maxH * 0.54f) }

        val badgeLabel = when {
            isThinking || runtimeState is CharacterRuntimeState.Thinking -> "Thinking"
            runtimeState is CharacterRuntimeState.Talking -> "Speaking"
            isListening || runtimeState is CharacterRuntimeState.Listening -> "Listening"
            else -> "Hi Mochi"
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        offsetX.coerceIn(marginPx, (maxW - modW - marginPx).coerceAtLeast(marginPx)).roundToInt(),
                        offsetY.coerceIn(marginPx, (maxH - modH - marginPx * 6f).coerceAtLeast(marginPx)).roundToInt()
                    )
                }
                .size(width = 86.dp, height = 154.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(marginPx, maxW - modW - marginPx)
                        offsetY = (offsetY + dragAmount.y).coerceIn(marginPx, maxH - modH - marginPx * 4f)
                    }
                }
                .clickable { onSetExpandedFullOverlay(true) }
                .testTag("global_floating_3d_companion_chip"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .clip(MochiShapes.pill)
                    .background(cardBg.copy(alpha = 0.94f))
                    .border(1.dp, accent.copy(alpha = 0.75f), MochiShapes.pill)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = badgeLabel,
                    style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                    color = textPrimary,
                    maxLines = 1
                )
            }

            CharacterStageView(
                character = character,
                controller = controller,
                halfResolutionMode = true,
                fullBodyStandingMode = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 2.dp)
            )
        }
    }
}

