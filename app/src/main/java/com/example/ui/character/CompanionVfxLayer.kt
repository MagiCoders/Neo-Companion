package com.example.ui.character

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visual effects around the companion, drawn on a transparent layer above the 3D view:
 * stardust (idle), listening rings, thinking orbit, speech halo, and reaction bursts —
 * sparkles, confetti + coins, hearts, music notes, lightning, calming mist.
 * Particles are deterministic functions of time, so there is no per-frame allocation.
 */
@Composable
fun CompanionVfxLayer(
    state: CharacterRuntimeState,
    accent: Color,
    micLevel: Float,
    modifier: Modifier = Modifier
) {
    var time by remember { mutableFloatStateOf(0f) }
    var reactionStart by remember { mutableFloatStateOf(-10f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time += ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
            }
        }
    }
    LaunchedEffect(state) {
        if (state is CharacterRuntimeState.Reacting) reactionStart = time
    }

    Canvas(modifier) {
        val since = time - reactionStart
        when (state) {
            CharacterRuntimeState.Idle -> stardust(time, accent, 10)
            CharacterRuntimeState.Listening -> listeningRings(time, accent, micLevel)
            CharacterRuntimeState.Thinking -> thinkingOrbit(time, accent)
            CharacterRuntimeState.Talking -> speechHalo(time, accent)
            is CharacterRuntimeState.Reacting -> when (state.clip.lowercase()) {
                "happy", "celebrate", "cheer" -> { sparkleBurst(since, accent); confetti(since) }
                "wave" -> { sparkleBurst(since, accent, count = 14); stardust(time, accent, 6) }
                "dance" -> musicNotes(time, accent)
                "love", "pet" -> hearts(since)
                "storm", "lightning" -> lightning(time, accent)
                "calm", "think" -> calmMist(time, accent)
                else -> stardust(time, accent, 8)
            }
        }
    }
}

private fun rnd(i: Int, salt: Int): Float {
    val x = sin((i * 127.1f + salt * 311.7f)) * 43758.547f
    return x - kotlin.math.floor(x)
}

private val DrawScope.bodyCenter get() = Offset(size.width / 2f, size.height * 0.52f)
private val DrawScope.bodyRadius get() = size.minDimension * 0.34f

private fun DrawScope.sparkle(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (alpha <= 0f) return
    val c = color.copy(alpha = alpha.coerceIn(0f, 1f))
    drawLine(c, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = radius * 0.35f, cap = StrokeCap.Round)
    drawLine(c, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = radius * 0.35f, cap = StrokeCap.Round)
    drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center, radius * 1.6f), radius * 1.6f, center)
}

private fun DrawScope.stardust(t: Float, accent: Color, count: Int) {
    val c = bodyCenter
    val r = bodyRadius
    for (i in 0 until count) {
        val life = 4f + rnd(i, 1) * 3f
        val p = ((t + rnd(i, 2) * life) % life) / life
        val x = c.x + (rnd(i, 3) - 0.5f) * r * 3f + sin(t * 0.8f + i) * r * 0.08f
        val y = c.y + r * 1.1f - p * r * 2.6f
        val twinkle = 0.5f + 0.5f * sin(t * 5f + i * 1.7f)
        val alpha = sin(p * PI.toFloat()) * 0.75f * twinkle
        sparkle(Offset(x, y), r * (0.035f + rnd(i, 4) * 0.035f), if (i % 3 == 0) Color(0xFFFFE6A8) else accent, alpha)
    }
}

private fun DrawScope.listeningRings(t: Float, accent: Color, mic: Float) {
    val c = Offset(bodyCenter.x, bodyCenter.y + bodyRadius * 0.95f)
    val level = (mic / 10f).coerceIn(0f, 1f)
    for (k in 0 until 3) {
        val p = ((t * 0.9f + k / 3f) % 1f)
        val rx = bodyRadius * (0.6f + p * (1.2f + level * 0.6f))
        drawOval(
            color = accent.copy(alpha = (1f - p) * (0.45f + level * 0.4f)),
            topLeft = Offset(c.x - rx, c.y - rx * 0.22f),
            size = Size(rx * 2, rx * 0.44f),
            style = Stroke(width = 2.5f + level * 3f)
        )
    }
}

private fun DrawScope.thinkingOrbit(t: Float, accent: Color) {
    val c = Offset(bodyCenter.x, bodyCenter.y - bodyRadius * 0.55f)
    for (i in 0 until 6) {
        val a = t * 2.2f + i * (2f * PI.toFloat() / 6f)
        val x = c.x + cos(a) * bodyRadius * 1.15f
        val y = c.y + sin(a) * bodyRadius * 0.32f
        val front = sin(a) > 0
        sparkle(Offset(x, y), bodyRadius * (if (front) 0.07f else 0.045f), if (i % 2 == 0) accent else Color(0xFFFFE6A8), if (front) 0.95f else 0.45f)
    }
}

private fun DrawScope.speechHalo(t: Float, accent: Color) {
    val c = bodyCenter
    for (side in listOf(-1f, 1f)) {
        for (k in 0 until 3) {
            val p = (t * 1.6f + k / 3f) % 1f
            val rr = bodyRadius * (1.05f + p * 0.5f)
            drawArc(
                color = accent.copy(alpha = (1f - p) * 0.6f),
                startAngle = if (side < 0) 150f else -30f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(c.x - rr, c.y - rr),
                size = Size(rr * 2, rr * 2),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.sparkleBurst(since: Float, accent: Color, count: Int = 26) {
    if (since !in 0f..1.2f) return
    val c = bodyCenter
    val p = since / 1.2f
    for (i in 0 until count) {
        val a = i * (2f * PI.toFloat() / count) + rnd(i, 5) * 0.4f
        val dist = bodyRadius * (0.6f + p * (1.3f + rnd(i, 6) * 0.6f))
        val pos = Offset(c.x + cos(a) * dist, c.y + sin(a) * dist + p * p * bodyRadius * 0.4f)
        sparkle(pos, bodyRadius * 0.06f * (1f - p * 0.5f), if (i % 3 == 0) Color(0xFFFFD36B) else accent, 1f - p)
    }
    drawCircle(accent.copy(alpha = (1f - p) * 0.5f), bodyRadius * (0.9f + p * 1.4f), c, style = Stroke(width = 4f * (1f - p)))
}

private fun DrawScope.confetti(since: Float) {
    if (since !in 0f..2.4f) return
    val colors = listOf(Color(0xFFFFD36B), Color(0xFF5CC8A1), Color(0xFFFF8FA3), Color(0xFF7FB8FF), Color(0xFFE5B869))
    for (i in 0 until 30) {
        val start = rnd(i, 7) * 0.4f
        val p = ((since - start) / 2f).coerceIn(0f, 1f)
        if (p <= 0f) continue
        val x = size.width * (0.1f + rnd(i, 8) * 0.8f) + sin(since * 4f + i) * 10f
        val y = -10f + p * size.height * 1.05f
        rotate(degrees = since * 360f * (0.5f + rnd(i, 9)), pivot = Offset(x, y)) {
            if (i % 5 == 0) {
                drawCircle(Color(0xFFFFC94D).copy(alpha = 1f - p), 7f, Offset(x, y))   // coin
                drawCircle(Color(0xFFB8860B).copy(alpha = 1f - p), 7f, Offset(x, y), style = Stroke(1.5f))
            } else {
                drawRect(colors[i % colors.size].copy(alpha = 1f - p), Offset(x - 5f, y - 2.5f), Size(10f, 5f))
            }
        }
    }
}

private fun DrawScope.hearts(since: Float) {
    for (i in 0 until 6) {
        val p = ((since - i * 0.15f) / 1.6f).coerceIn(0f, 1f)
        if (p <= 0f || p >= 1f) continue
        val x = bodyCenter.x + (rnd(i, 10) - 0.5f) * bodyRadius * 1.6f
        val y = bodyCenter.y - bodyRadius * 0.6f - p * bodyRadius * 1.4f
        heart(Offset(x, y), bodyRadius * 0.14f, Color(0xFFFF7A93).copy(alpha = 1f - p))
    }
}

private fun DrawScope.heart(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y + s * 0.9f)
        cubicTo(c.x - s * 1.4f, c.y, c.x - s * 0.7f, c.y - s * 1.1f, c.x, c.y - s * 0.4f)
        cubicTo(c.x + s * 0.7f, c.y - s * 1.1f, c.x + s * 1.4f, c.y, c.x, c.y + s * 0.9f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.musicNotes(t: Float, accent: Color) {
    for (i in 0 until 5) {
        val p = ((t * 0.6f + i / 5f) % 1f)
        val x = bodyCenter.x + (if (i % 2 == 0) -1 else 1) * bodyRadius * (0.9f + p * 0.5f)
        val y = bodyCenter.y - p * bodyRadius * 1.6f
        val a = sin(p * PI.toFloat())
        val col = (if (i % 2 == 0) accent else Color(0xFFFFD36B)).copy(alpha = a)
        drawCircle(col, bodyRadius * 0.07f, Offset(x, y))
        drawLine(col, Offset(x + bodyRadius * 0.06f, y), Offset(x + bodyRadius * 0.06f, y - bodyRadius * 0.28f), strokeWidth = 3f)
    }
    val spot = abs(sin(t * 3f))
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f + 0.1f * spot), Color.Transparent), bodyCenter, bodyRadius * 1.8f), bodyRadius * 1.8f, bodyCenter)
}

private fun DrawScope.lightning(t: Float, accent: Color) {
    val flash = (t * 3f) % 1f < 0.18f
    if (!flash) return
    val seed = (t * 3f).toInt()
    for (b in 0 until 2) {
        val path = Path()
        var x = bodyCenter.x + (rnd(seed + b, 11) - 0.5f) * bodyRadius * 2f
        var y = bodyCenter.y - bodyRadius * 1.6f
        path.moveTo(x, y)
        repeat(5) { k ->
            x += (rnd(seed * 7 + k + b, 12) - 0.5f) * bodyRadius * 0.6f
            y += bodyRadius * 0.28f
            path.lineTo(x, y)
        }
        drawPath(path, Color.White.copy(alpha = 0.9f), style = Stroke(width = 3f))
        drawPath(path, accent.copy(alpha = 0.5f), style = Stroke(width = 9f))
    }
}

private fun DrawScope.calmMist(t: Float, accent: Color) {
    val breath = 0.5f + 0.5f * sin(t * (2f * PI.toFloat() / 8f))   // 8-second breathing guide
    val r = bodyRadius * (1.1f + 0.5f * breath)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF9FD8FF).copy(alpha = 0.22f), Color.Transparent), bodyCenter, r * 1.4f), r * 1.4f, bodyCenter)
    drawCircle(accent.copy(alpha = 0.35f), r, bodyCenter, style = Stroke(width = 2.5f))
}
