package com.example.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * AGSL RuntimeShader strictly matching DESIGN.md:
 * - Two soft blobs in a desaturated version of `accent`
 * - One full drift cycle every 20 seconds
 * - 3% film-grain noise on top
 * - Static radial gradient fallback below Android 13
 */
private const val MOCHI_AURORA_AGSL_SHADER = """
    uniform float2 uResolution;
    uniform float uTime;
    uniform float3 uAccent;
    uniform float3 uBg;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / max(uResolution, float2(1.0, 1.0));
        float aspect = uResolution.x / max(uResolution.y, 1.0);

        // Desaturate accent slightly for soft ambient aurora blobs
        float luma = dot(uAccent, float3(0.299, 0.587, 0.114));
        float3 desatAccent = mix(uAccent, float3(luma, luma, luma), 0.32);

        // Two soft blobs completing one full drift cycle every 20s (uTime = 0..2*PI)
        float2 blob1 = float2(0.52 + 0.14 * cos(uTime), 0.28 + 0.08 * sin(uTime));
        float2 blob2 = float2(0.44 - 0.12 * sin(uTime), 0.44 + 0.09 * cos(uTime));

        float2 d1 = (uv - blob1) * float2(aspect, 1.0);
        float2 d2 = (uv - blob2) * float2(aspect, 1.0);

        float g1 = exp(-dot(d1, d1) * 3.6) * 0.22;
        float g2 = exp(-dot(d2, d2) * 4.2) * 0.16;

        float3 col = uBg + desatAccent * (g1 + g2);

        // 3% film-grain noise on top
        float grain = (hash(fragCoord + float2(uTime * 11.3, uTime * 5.7)) - 0.5) * 0.03;
        col = clamp(col + float3(grain, grain, grain), 0.0, 1.0);

        return half4(col, 1.0);
    }
"""

@Composable
fun AuroraBackground(
    accentColor: Color,
    modifier: Modifier = Modifier,
    visualStyle: MochiVisualStyle = LocalVisualStyle.current
) {
    // 600ms crossfade when switching characters (per DESIGN.md / CHARACTERS.md)
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 600),
        label = "aurora_accent_crossfade"
    )
    val animatedBg by animateColorAsState(
        targetValue = visualStyle.bg,
        animationSpec = tween(durationMillis = 600),
        label = "aurora_bg_crossfade"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "aurora_20s_cycle")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_phase"
    )

    val agslShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RuntimeShader(MOCHI_AURORA_AGSL_SHADER)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)

        // Desaturated accent for fallback & subtle stage glow
        val luma = animatedAccent.red * 0.299f + animatedAccent.green * 0.587f + animatedAccent.blue * 0.114f
        val desatAccent = Color(
            red = animatedAccent.red * 0.68f + luma * 0.32f,
            green = animatedAccent.green * 0.68f + luma * 0.32f,
            blue = animatedAccent.blue * 0.68f + luma * 0.32f,
            alpha = 1f
        )

        var drewHardwareAgsl = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslShader != null) {
            try {
                agslShader.setFloatUniform("uResolution", w, h)
                agslShader.setFloatUniform("uTime", phase)
                agslShader.setFloatUniform("uAccent", animatedAccent.red, animatedAccent.green, animatedAccent.blue)
                agslShader.setFloatUniform("uBg", animatedBg.red, animatedBg.green, animatedBg.blue)
                drawRect(brush = ShaderBrush(agslShader))
                drewHardwareAgsl = true
            } catch (_: Throwable) {
                drewHardwareAgsl = false
            }
        }

        if (!drewHardwareAgsl) {
            // Static / smooth radial gradient fallback below Android 13
            drawRect(color = animatedBg)

            val blob1 = Offset(
                x = w * (0.52f + 0.12f * cos(phase)),
                y = h * (0.28f + 0.06f * sin(phase))
            )
            val blob2 = Offset(
                x = w * (0.44f - 0.10f * sin(phase)),
                y = h * (0.44f + 0.07f * cos(phase))
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(desatAccent.copy(alpha = 0.20f), Color.Transparent),
                    center = blob1,
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = blob1
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(desatAccent.copy(alpha = 0.14f), Color.Transparent),
                    center = blob2,
                    radius = w * 0.58f
                ),
                radius = w * 0.58f,
                center = blob2
            )
        }

        // Soft ground shadow ellipse under the character's feet so the character feels grounded
        val shadowY = h * 0.56f
        val shadowRx = w * 0.28f
        val shadowRy = h * 0.028f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = if (visualStyle.isLight) 0.10f else 0.42f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, shadowY),
                radius = shadowRx
            ),
            topLeft = Offset(w * 0.5f - shadowRx, shadowY - shadowRy),
            size = Size(shadowRx * 2f, shadowRy * 2f)
        )
    }
}
