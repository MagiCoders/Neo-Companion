package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.PendingVoiceAction
import com.example.ui.theme.LocalCharacterAccent
import com.example.ui.theme.LocalMochiDarkTheme
import com.example.ui.theme.MochiShapes
import com.example.ui.theme.MochiSpacing
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiType

/**
 * Global 3-Second 'Undo/Cancel' Pill UI Overlay (`VoiceUndoCancelOverlay`):
 * - Appears immediately after a voice or text command triggers an action (WhatsApp call/chat,
 *   YouTube video/search, Browser search, Media playback, or HashRouter screen navigation).
 * - Displays a live 3-second countdown ring (`3... 2... 1...`), animated progress bar,
 *   and prominent "Undo / Cancel" + "Open now" buttons so the user can abort navigation
 *   or media playback before execution.
 */
@Composable
fun VoiceUndoCancelOverlay(
    pendingAction: PendingVoiceAction?,
    onUndoCancel: () -> Unit,
    onExecuteNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalCharacterAccent.current
    val isDark = LocalMochiDarkTheme.current
    val cardBg = if (isDark) Color(0xF2121722) else Color(0xF7FFFFFF)
    val textPrimary = MochiTokens.adaptiveTextPrimary(isDark)
    val textSecondary = MochiTokens.adaptiveTextSecondary(isDark)
    val borderCol = accent.copy(alpha = 0.55f)

    AnimatedVisibility(
        visible = pendingAction != null,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f),
            initialOffsetY = { -it }
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
            targetOffsetY = { -it }
        ) + fadeOut(animationSpec = tween(150)),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = MochiSpacing.screenPadding, vertical = 10.dp)
            .testTag("voice_undo_cancel_overlay")
    ) {
        val action = pendingAction ?: return@AnimatedVisibility
        val fraction by animateFloatAsState(
            targetValue = (action.remainingSeconds.toFloat() / action.totalSeconds.coerceAtLeast(1).toFloat())
                .coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 320),
            label = "undo_countdown_fraction"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(cardBg)
                .border(1.5.dp, borderCol, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Countdown Ring with live remaining seconds (3 -> 2 -> 1)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("voice_undo_countdown_ring"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = accent.copy(alpha = 0.20f),
                                style = Stroke(width = 3.dp.toPx())
                            )
                            drawArc(
                                color = accent,
                                startAngle = -90f,
                                sweepAngle = 360f * fraction,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "${action.remainingSeconds}",
                            style = MochiType.Body.copy(fontWeight = FontWeight.Bold),
                            color = textPrimary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = action.title,
                            style = MochiType.Body.copy(fontWeight = FontWeight.SemiBold),
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = action.subtitle,
                            style = MochiType.Label,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(MochiShapes.pill),
                color = accent,
                trackColor = accent.copy(alpha = 0.16f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Undo / Cancel Pill Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(MochiShapes.pill)
                        .background(if (isDark) Color(0xFFF3F5F9) else Color(0xFF17181A))
                        .clickable { onUndoCancel() }
                        .testTag("voice_undo_cancel_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Undo / Cancel",
                        style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isDark) Color(0xFF07090F) else Color.White
                    )
                }

                // Execute Immediately Pill Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(MochiShapes.pill)
                        .background(accent)
                        .clickable { onExecuteNow() }
                        .testTag("voice_execute_now_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open now",
                        style = MochiType.Label.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF07090F)
                    )
                }
            }
        }
    }
}
