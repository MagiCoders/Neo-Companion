package com.example.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class HazeStageState {
    var activeGlassCount by mutableStateOf(0)
}

@Composable
fun rememberHazeStageState(): HazeStageState = remember { HazeStageState() }

/**
 * GlassSurface strictly following DESIGN.md:
 * - Real backdrop blur (24.dp) on Android 12+
 * - Tint: #FFFFFF at 6% (MochiTokens.glassTint)
 * - Border: 1dp of hairline (#FFFFFF at 10%), slightly brighter (#FFFFFF at 16%) on the top edge
 * - Below Android 12: fall back to bgRaised at 92% opacity (MochiTokens.bgRaisedFallback)
 * - At most 2 glass surfaces on screen at once.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MochiShapes.card,
    blurRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val blurPx = with(density) { blurRadius.toPx() }
    val supportsRealtimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val visualStyle = LocalVisualStyle.current

    val baseFill = when {
        visualStyle.isLight -> MochiTokens.lightBgRaised.copy(alpha = 0.92f)
        supportsRealtimeBlur -> MochiTokens.glassTint
        else -> MochiTokens.bgRaisedFallback
    }
    val borderColor = if (visualStyle.isLight) MochiTokens.lightHairline else MochiTokens.hairline
    val topEdgeColor = if (visualStyle.isLight) MochiTokens.lightHairline else MochiTokens.glassTopHighlight

    Box(
        modifier = modifier
            .clip(shape)
            .background(baseFill)
            .drawWithContent {
                val w = size.width.coerceAtLeast(1f)
                drawContent()

                val strokePx = 1.dp.toPx()
                drawRect(
                    color = borderColor,
                    style = Stroke(width = strokePx)
                )

                // Top edge highlight (#FFFFFF at 16%)
                drawLine(
                    color = topEdgeColor,
                    start = Offset(w * 0.06f, strokePx * 0.5f),
                    end = Offset(w * 0.94f, strokePx * 0.5f),
                    strokeWidth = strokePx
                )
            }
    ) {
        if (supportsRealtimeBlur && !visualStyle.isLight) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = RenderEffect.createBlurEffect(
                            blurPx,
                            blurPx,
                            Shader.TileMode.DECAL
                        ).asComposeRenderEffect()
                    }
                    .background(MochiTokens.bgRaised.copy(alpha = 0.52f))
            )
        }
        content()
    }
}
