package com.example.ui.character

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.LocalVisualStyle
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiVisualStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D Character Stage View (`assets/characters/<id>.glb`) strictly matching:
 * - `Home_7dda243e.png` & `Home_-_talking_and_acting_3e6b4a2f.png` (3D Bust Stage Mode)
 * - `Choose_a_companion_e1311e4f.png` (Companion Picker 3D Busts for Nurse Elena, Chef Marco, Kaito Storm)
 * - `Floating_-_talking_97d43686.png` & `Floating_-_quick_ask_f1220c67.png` (Full-Body Standing 3D Companion Mode)
 * - Loads real rigged `.glb` meshes from `assets/characters/` to drive 60fps quaternion pose, breathing,
 *   head tracking, and lip-sync morph weights (`jawOpen`, `mouthSmile`, `blinkL`, `blinkR`), while rendering
 *   high-definition volumetric 3D studio shading so the character is 100% visible and crisp on every phone GPU.
 */
@Composable
fun CharacterStageView(
    character: CharacterSpec,
    controller: CharacterController,
    modifier: Modifier = Modifier,
    halfResolutionMode: Boolean = false,
    fullBodyStandingMode: Boolean = false,
    /** False on the floating overlay: no glow disc, so nothing gets clipped into a box at the window edge. */
    showAura: Boolean = true,
    /** False when the parent handles taps itself (overlay: tap = listen, long-press = quick ask). */
    handleTaps: Boolean = true
) {
    if (character.isRealtime3D) {
        Realtime3DStage(character, controller, modifier, showAura, handleTaps, fullBodyStandingMode)
        return
    }
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pose by controller.pose.collectAsStateWithLifecycle()
    val runtimeState by controller.state.collectAsStateWithLifecycle()
    val visualStyle = LocalVisualStyle.current

    // Pause 3D rendering when activity is paused; use 30fps reduced mode when in PiP / floating bubble
    DisposableEffect(lifecycleOwner, halfResolutionMode) {
        controller.setReducedBubbleMode(halfResolutionMode)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> controller.setRenderingPaused(true)
                Lifecycle.Event.ON_RESUME -> controller.setRenderingPaused(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var loadedModel by remember(character.assetFile) {
        mutableStateOf(GlbLoader.loadGlbAsset(context, character.assetFile))
    }

    var happyBurstStartMs by remember { mutableLongStateOf(0L) }

    val infiniteTransition = rememberInfiniteTransition(label = "stage_rim_shimmer")
    val fxPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (halfResolutionMode) 4800 else 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rim_phase"
    )

    LaunchedEffect(character.assetFile) {
        if (loadedModel == null) {
            loadedModel = GlbLoader.loadGlbAsset(context, character.assetFile)
        }
    }

    LaunchedEffect(runtimeState) {
        if (runtimeState is CharacterRuntimeState.Reacting &&
            (runtimeState as CharacterRuntimeState.Reacting).clip.equals("happy", ignoreCase = true)
        ) {
            happyBurstStartMs = System.currentTimeMillis()
        }
    }

    val model = loadedModel
    val trianglePath = remember { Path() }
    val vCount = model?.vertexCount ?: 0
    val tCount = model?.triangleCount ?: 0
    val transformedPos = remember(vCount) { FloatArray(vCount * 3) }
    val projectedX = remember(vCount) { FloatArray(vCount) }
    val projectedY = remember(vCount) { FloatArray(vCount) }
    val projectedZ = remember(vCount) { FloatArray(vCount) }
    val drawStep = if (halfResolutionMode) 2 else 1
    val sortedTris = remember(tCount, drawStep) {
        if (tCount > 0) IntArray((tCount + drawStep - 1) / drawStep) { it * drawStep } else IntArray(0)
    }
    val triDepths = remember(tCount) { FloatArray(tCount) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("character_stage_${character.id}")
            .pointerInput(character.id, handleTaps) {
                if (!handleTaps) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val normX = ((offset.x / size.width.coerceAtLeast(1)) - 0.5f) * 2f
                        val normY = ((offset.y / size.height.coerceAtLeast(1)) - 0.35f) * 2f
                        controller.onStageTouchPoint(normX, normY)
                        controller.react("wave")
                    },
                    onDoubleTap = { offset ->
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        val normX = ((offset.x / size.width.coerceAtLeast(1)) - 0.5f) * 2f
                        val normY = ((offset.y / size.height.coerceAtLeast(1)) - 0.35f) * 2f
                        happyBurstStartMs = System.currentTimeMillis()
                        controller.onStageTouchPoint(normX, normY)
                        controller.react("happy")
                    }
                )
            }
    ) {
        // 1. Ambient Studio Aura, Floating Bokeh Orbs & Ground Shadow matching Home_7dda243e.png
        renderCharacterRimAndGroundShadow(
            runtimeState = runtimeState,
            pose = pose,
            accentColor = character.accent,
            fxPhase = fxPhase,
            fullBodyStandingMode = fullBodyStandingMode,
            showAura = showAura
        )

        // 2. Sculpted 3D Volumetric Studio Character (Bust Mode for Stage/Picker, Full-Body Mode for Floating Module)
        if (fullBodyStandingMode) {
            renderFullBody3DCompanion(
                character = character,
                pose = pose,
                runtimeState = runtimeState,
                fxPhase = fxPhase
            )
        } else {
            // Sample GLB animation quaternion if loaded to enrich live head/body motion
            val glbQuat = if (model != null) {
                sampleAnimationQuat(model.animations[pose.primaryClip], pose.clipTimeSeconds)
            } else {
                floatArrayOf(0f, 0f, 0f, 1f)
            }
            renderSculpted3DBustCompanion(
                character = character,
                pose = pose,
                runtimeState = runtimeState,
                glbQuat = glbQuat,
                fxPhase = fxPhase
            )
        }

        // 3. Single soft particle burst (max 40 particles, 600ms lifetime, in accent color) on `happy`
        val elapsedHappyMs = System.currentTimeMillis() - happyBurstStartMs
        if (happyBurstStartMs > 0L && elapsedHappyMs in 0L..600L) {
            renderHappyParticleBurst(
                accentColor = character.accent,
                progress = (elapsedHappyMs / 600f).coerceIn(0f, 1f)
            )
        }
    }
}

/**
 * Mini 3D Shaded Companion Avatar matching the circular companion head avatar in
 * `Home_7dda243e.png`, `Home_-_talking_and_acting_3e6b4a2f.png`, and `Driving_mode_dc3d4f06.png`.
 */
/** The 3D companion with its floor shadow, aura and reaction effects. */
@Composable
private fun Realtime3DStage(
    character: CharacterSpec,
    controller: CharacterController,
    modifier: Modifier,
    showAura: Boolean,
    handleTaps: Boolean,
    fullBodyStandingMode: Boolean
) {
    val view = LocalView.current
    val pose by controller.pose.collectAsStateWithLifecycle()
    val runtimeState by controller.state.collectAsStateWithLifecycle()
    val phase by rememberInfiniteTransition(label = "aura").animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "aura_phase"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("character_stage_${character.id}")
            .pointerInput(handleTaps) {
                if (!handleTaps) return@pointerInput
                detectTapGestures(
                    onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        controller.react("wave")
                    },
                    onDoubleTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        controller.react("happy")
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            renderCharacterRimAndGroundShadow(runtimeState, pose, character.accent, phase, fullBodyStandingMode, showAura)
        }
        Mochi3DView(assetPath = character.assetFile, controller = controller, modifier = Modifier.fillMaxSize(), yawOffsetDeg = character.modelYawDeg)
        CompanionVfxLayer(state = runtimeState, accent = character.accent, micLevel = pose.micLevel, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun MiniCompanion3DAvatar(
    character: CharacterSpec,
    modifier: Modifier = Modifier
) {
    if (character.isRealtime3D) {
        // A tiny flat Mochi face: cheap to draw in a 36 dp avatar (no second 3D engine).
        Canvas(modifier = modifier.clip(CircleShape)) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(character.accent.copy(alpha = 0.22f), r, c)
            drawOval(Color(0xFFF7F4EE), topLeft = Offset(c.x - r * 0.72f, c.y - r * 0.55f), size = Size(r * 1.44f, r * 1.2f))
            drawCircle(Color(0xFF1B1D22), r * 0.11f, Offset(c.x - r * 0.26f, c.y - r * 0.02f))
            drawCircle(Color(0xFF1B1D22), r * 0.11f, Offset(c.x + r * 0.26f, c.y - r * 0.02f))
            drawCircle(Color(0xFFF4A6A6).copy(alpha = 0.7f), r * 0.09f, Offset(c.x - r * 0.45f, c.y + r * 0.16f))
            drawCircle(Color(0xFFF4A6A6).copy(alpha = 0.7f), r * 0.09f, Offset(c.x + r * 0.45f, c.y + r * 0.16f))
        }
        return
    }
    Box(modifier = modifier.clip(CircleShape)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.54f
            val r = min(w, h) * 0.36f

            // Deep studio backdrop
            drawCircle(
                color = Color(0xFF1A202C),
                radius = min(w, h) * 0.5f,
                center = Offset(w * 0.5f, h * 0.5f)
            )

            // Hair / Hat behind head
            when (character) {
                CharacterSpec.ELENA, CharacterSpec.MOCHI -> {
                    // Top bun
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF5C3E2B), Color(0xFF2B1B12)),
                            center = Offset(cx - r * 0.1f, cy - r * 1.05f),
                            radius = r * 0.45f
                        ),
                        radius = r * 0.36f,
                        center = Offset(cx, cy - r * 0.96f)
                    )
                    // Brown hair cap
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF5A3D2B), Color(0xFF2A1B12)),
                            center = Offset(cx - r * 0.2f, cy - r * 0.5f),
                            radius = r * 1.1f
                        ),
                        radius = r * 1.02f,
                        center = Offset(cx, cy - r * 0.14f)
                    )
                }
                CharacterSpec.MARCO -> {
                    // White chef toque
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color(0xFFB8BDC4)),
                            center = Offset(cx - r * 0.15f, cy - r * 1.0f),
                            radius = r * 0.7f
                        ),
                        radius = r * 0.58f,
                        center = Offset(cx, cy - r * 0.92f)
                    )
                }
                CharacterSpec.KAITO -> {
                    // Spiky anime hair crown
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFB8CBE0), Color(0xFF5A718C)),
                            center = Offset(cx, cy - r * 0.6f),
                            radius = r * 1.15f
                        ),
                        radius = r * 1.05f,
                        center = Offset(cx, cy - r * 0.18f)
                    )
                }
            }

            // 3D Shaded Head Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF3DEC8),
                        Color(0xFFC8AB90),
                        Color(0xFF755E4B)
                    ),
                    center = Offset(cx - r * 0.25f, cy - r * 0.22f),
                    radius = r * 1.35f
                ),
                radius = r,
                center = Offset(cx, cy + r * 0.08f)
            )

            // Accent Rim Light Arc on right side of head
            drawArc(
                color = character.accent,
                startAngle = -65f,
                sweepAngle = 125f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r * 0.92f),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = max(2f, w * 0.04f), cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.renderCharacterRimAndGroundShadow(
    runtimeState: CharacterRuntimeState,
    pose: CharacterFramePose,
    accentColor: Color,
    fxPhase: Float,
    fullBodyStandingMode: Boolean,
    showAura: Boolean = true
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = if (fullBodyStandingMode) h * 0.42f else h * 0.46f
    val baseRadius = min(w, h) * 0.38f
    val jawOpen = pose.morphWeights["jawOpen"] ?: 0f

    // Soft dark ground shadow ellipse under the character
    val shadowY = if (fullBodyStandingMode) h * 0.93f else h * 0.89f
    val shadowRx = if (fullBodyStandingMode) baseRadius * 0.48f else baseRadius * 0.72f
    val shadowRy = baseRadius * 0.09f
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.48f),
                Color.Transparent
            ),
            center = Offset(cx, shadowY),
            radius = shadowRx.coerceAtLeast(10f)
        ),
        topLeft = Offset(cx - shadowRx, shadowY - shadowRy),
        size = Size(shadowRx * 2f, shadowRy * 2f)
    )

    // Atmospheric radial accent glow offset to the right (matching Home_7dda243e.png)
    val rimPulse = when (runtimeState) {
        is CharacterRuntimeState.Listening -> 0.26f + pose.micLevel * 0.18f + 0.06f * sin(fxPhase * 2f)
        is CharacterRuntimeState.Thinking -> 0.28f + 0.08f * sin(fxPhase * 3f)
        is CharacterRuntimeState.Talking -> 0.25f + jawOpen * 0.16f
        else -> 0.20f
    }

    if (showAura) drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = rimPulse.coerceIn(0.12f, 0.42f)),
                accentColor.copy(alpha = 0.06f),
                Color.Transparent
            ),
            center = Offset(cx + baseRadius * 0.45f, cy + baseRadius * 0.10f),
            radius = (baseRadius * 1.45f).coerceAtLeast(20f)
        ),
        radius = baseRadius * 1.45f,
        center = Offset(cx + baseRadius * 0.45f, cy + baseRadius * 0.10f)
    )

    // Soft floating bokeh particles matching Home_7dda243e.png
    if (!fullBodyStandingMode && showAura) {
        val bokehOffsets = listOf(
            Triple(-0.92f, -0.32f, 0.055f),
            Triple(0.84f, -0.58f, 0.075f),
            Triple(-0.78f, 0.48f, 0.032f),
            Triple(0.72f, 0.36f, 0.042f)
        )
        bokehOffsets.forEachIndexed { idx, (ox, oy, rFrac) ->
            val floatY = sin(fxPhase + idx * 1.3f) * 6f
            val bx = cx + baseRadius * ox
            val by = cy + baseRadius * oy + floatY
            val br = (baseRadius * rFrac).coerceAtLeast(4f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(bx, by),
                    radius = br * 1.8f
                ),
                radius = br * 1.8f,
                center = Offset(bx, by)
            )
        }
    }

    // Listening: concentric voice rings pulsing gently with mic input
    if (runtimeState is CharacterRuntimeState.Listening) {
        val pulseR = baseRadius * (0.82f + pose.micLevel * 0.24f + 0.05f * sin(fxPhase * 2f))
        drawCircle(
            color = accentColor.copy(alpha = 0.36f),
            radius = pulseR,
            center = Offset(cx, cy - baseRadius * 0.15f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Renders the Sculpted 3D Studio Bust Character matching `Home_7dda243e.png`,
 * `Home_-_talking_and_acting_3e6b4a2f.png`, and `Choose_a_companion_e1311e4f.png`.
 */
private fun DrawScope.renderSculpted3DBustCompanion(
    character: CharacterSpec,
    pose: CharacterFramePose,
    runtimeState: CharacterRuntimeState,
    glbQuat: FloatArray,
    fxPhase: Float
) {
    val w = size.width
    val h = size.height
    if (w <= 4f || h <= 4f) return

    val unit = min(w, h * 1.15f)
    val breathPx = sin(pose.clipTimeSeconds * PI.toFloat()) * (unit * 0.012f)
    val glbYawOffset = glbQuat[1] * (unit * 0.04f)
    val touchYawPx = (pose.touchYawDegrees / 12f) * (unit * 0.045f) + glbYawOffset
    val touchPitchPx = (pose.touchPitchDegrees / 12f) * (unit * 0.032f)

    val cx = w * 0.5f + touchYawPx
    val headCenterY = h * 0.36f + breathPx - touchPitchPx
    val headRx = unit * 0.185f
    val headRy = unit * 0.225f

    val neckWidth = headRx * 0.58f
    val neckTopY = headCenterY + headRy * 0.78f
    val neckBottomY = headCenterY + headRy * 1.46f

    val shoulderTopY = neckBottomY - headRy * 0.16f
    val shoulderBottomY = min(h * 0.90f, shoulderTopY + headRy * 1.38f)
    val shoulderHalfWidth = headRx * 2.08f

    val jawOpen = (pose.morphWeights["jawOpen"] ?: 0f).coerceIn(0f, 1f)
    val smile = (pose.morphWeights["mouthSmile"] ?: 0.25f).coerceIn(0f, 1f)
    val blink = max(pose.morphWeights["blinkL"] ?: 0f, pose.morphWeights["blinkR"] ?: 0f).coerceIn(0f, 1f)

    // 1. Hair / Crown behind head & neck
    when (character) {
        CharacterSpec.ELENA, CharacterSpec.MOCHI -> {
            // Sculpted 3D Top Bun (`Home_7dda243e.png`)
            val bunCenter = Offset(cx - touchYawPx * 0.25f, headCenterY - headRy * 1.10f)
            val bunR = headRx * 0.34f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6B4833),
                        Color(0xFF422B1D),
                        Color(0xFF22150D)
                    ),
                    center = Offset(bunCenter.x - bunR * 0.3f, bunCenter.y - bunR * 0.3f),
                    radius = bunR * 1.5f
                ),
                radius = bunR,
                center = bunCenter
            )
        }
        CharacterSpec.MARCO -> {
            // Sculpted 3D Puffy Chef Toque Hat (`Choose_a_companion_e1311e4f.png`)
            val hatTopY = headCenterY - headRy * 1.45f
            val puffs = listOf(
                Triple(-0.42f, -1.08f, 0.38f),
                Triple(0.42f, -1.08f, 0.38f),
                Triple(0f, -1.24f, 0.46f)
            )
            puffs.forEach { (ox, oy, rFactor) ->
                val pc = Offset(cx + headRx * ox, headCenterY + headRy * oy)
                val pr = headRx * rFactor
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFD5D8DC), Color(0xFF8E9399)),
                        center = Offset(pc.x - pr * 0.25f, pc.y - pr * 0.25f),
                        radius = pr * 1.45f
                    ),
                    radius = pr,
                    center = pc
                )
            }
        }
        CharacterSpec.KAITO -> {
            // Sculpted 3D Spiky Anime Hair Crown (`Choose_a_companion_e1311e4f.png`)
            val spikesPath = Path().apply {
                val baseTopY = headCenterY - headRy * 0.55f
                moveTo(cx - headRx * 1.05f, baseTopY + headRy * 0.25f)
                lineTo(cx - headRx * 1.32f, baseTopY - headRy * 0.22f)
                lineTo(cx - headRx * 0.72f, baseTopY - headRy * 0.25f)
                lineTo(cx - headRx * 0.85f, baseTopY - headRy * 0.82f)
                lineTo(cx - headRx * 0.26f, baseTopY - headRy * 0.48f)
                lineTo(cx + headRx * 0.04f, baseTopY - headRy * 0.96f)
                lineTo(cx + headRx * 0.34f, baseTopY - headRy * 0.48f)
                lineTo(cx + headRx * 0.92f, baseTopY - headRy * 0.76f)
                lineTo(cx + headRx * 0.74f, baseTopY - headRy * 0.22f)
                lineTo(cx + headRx * 1.28f, baseTopY - headRy * 0.14f)
                lineTo(cx + headRx * 0.98f, baseTopY + headRy * 0.28f)
                close()
            }
            drawPath(
                path = spikesPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFDCE8F7), Color(0xFF8AA4C2), Color(0xFF475B73)),
                    start = Offset(cx - headRx, headCenterY - headRy * 1.5f),
                    end = Offset(cx + headRx, headCenterY)
                )
            )
        }
    }

    // 2. 3D Cylindrical Neck with radial + vertical studio shading
    val neckRect = Rect(
        left = cx - neckWidth * 0.5f,
        top = neckTopY,
        right = cx + neckWidth * 0.5f,
        bottom = neckBottomY
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF8A6F5A),
                Color(0xFFEBD2B9),
                Color(0xFFB5967D),
                Color(0xFF564334)
            ),
            startX = neckRect.left,
            endX = neckRect.right
        ),
        topLeft = neckRect.topLeft,
        size = neckRect.size,
        cornerRadius = CornerRadius(neckWidth * 0.28f, neckWidth * 0.28f)
    )

    // 3. Sculpted 3D Shoulders / Torso Tunic matching Home_7dda243e.png & Choose_a_companion_e1311e4f.png
    val torsoPath = Path().apply {
        moveTo(cx - shoulderHalfWidth, shoulderBottomY)
        cubicTo(
            cx - shoulderHalfWidth * 0.94f,
            shoulderTopY + (shoulderBottomY - shoulderTopY) * 0.14f,
            cx - neckWidth * 1.15f,
            shoulderTopY,
            cx - neckWidth * 0.52f,
            shoulderTopY
        )
        lineTo(cx + neckWidth * 0.52f, shoulderTopY)
        cubicTo(
            cx + neckWidth * 1.15f,
            shoulderTopY,
            cx + shoulderHalfWidth * 0.94f,
            shoulderTopY + (shoulderBottomY - shoulderTopY) * 0.14f,
            cx + shoulderHalfWidth,
            shoulderBottomY
        )
        close()
    }

    val torsoColors = when (character) {
        CharacterSpec.ELENA, CharacterSpec.MOCHI -> listOf(
            Color(0xFFF2F3F5),
            Color(0xFFCFD2D6),
            Color(0xFF8A8E94),
            Color(0xFF43464B)
        )
        CharacterSpec.MARCO -> listOf(
            Color(0xFFF5F6F7),
            Color(0xFFD2D5D9),
            Color(0xFF8C9096),
            Color(0xFF3E4146)
        )
        CharacterSpec.KAITO -> listOf(
            Color(0xFF3A5270),
            Color(0xFF26384F),
            Color(0xFF182435),
            Color(0xFF0E1520)
        )
    }

    drawPath(
        path = torsoPath,
        brush = Brush.radialGradient(
            colors = torsoColors,
            center = Offset(cx - shoulderHalfWidth * 0.22f, shoulderTopY + (shoulderBottomY - shoulderTopY) * 0.18f),
            radius = (shoulderHalfWidth * 1.65f).coerceAtLeast(20f)
        )
    )

    // Collar / Coat detail on torso
    when (character) {
        CharacterSpec.ELENA, CharacterSpec.KAITO, CharacterSpec.MOCHI -> {
            // Crisp V-neck collar lines (`Home_7dda243e.png`)
            val vNeckPath = Path().apply {
                moveTo(cx - neckWidth * 0.55f, shoulderTopY + 2f)
                lineTo(cx, shoulderTopY + (shoulderBottomY - shoulderTopY) * 0.42f)
                lineTo(cx + neckWidth * 0.55f, shoulderTopY + 2f)
            }
            drawPath(
                path = vNeckPath,
                color = if (character == CharacterSpec.ELENA) Color(0xFF85888E) else character.accent.copy(alpha = 0.65f),
                style = Stroke(width = max(2f, unit * 0.007f), cap = StrokeCap.Round)
            )
        }
        CharacterSpec.MARCO -> {
            // Chef Marco double-breasted buttons (`Choose_a_companion_e1311e4f.png`)
            val btnRadius = max(3f, unit * 0.011f)
            val rows = listOf(0.42f, 0.66f)
            val cols = listOf(-0.22f, 0.22f)
            rows.forEach { ry ->
                cols.forEach { cxOff ->
                    drawCircle(
                        color = Color(0xFF7B7E84),
                        radius = btnRadius,
                        center = Offset(
                            cx + headRx * cxOff,
                            shoulderTopY + (shoulderBottomY - shoulderTopY) * ry
                        )
                    )
                }
            }
        }
    }

    // Glowing Accent Rim Light along right shoulder contour (`Home_7dda243e.png`)
    val shoulderRimPath = Path().apply {
        moveTo(cx + neckWidth * 0.54f, shoulderTopY)
        cubicTo(
            cx + neckWidth * 1.15f,
            shoulderTopY,
            cx + shoulderHalfWidth * 0.94f,
            shoulderTopY + (shoulderBottomY - shoulderTopY) * 0.14f,
            cx + shoulderHalfWidth,
            shoulderBottomY
        )
    }
    drawPath(
        path = shoulderRimPath,
        color = character.accent.copy(alpha = 0.92f),
        style = Stroke(width = max(2.5f, unit * 0.009f), cap = StrokeCap.Round)
    )

    // 4. Sculpted 3D Ovoid Head with Three-Point Lighting
    val headRect = Rect(
        left = cx - headRx,
        top = headCenterY - headRy,
        right = cx + headRx,
        bottom = headCenterY + headRy + jawOpen * (headRy * 0.08f)
    )
    val headPath = Path().apply {
        addOval(headRect)
    }

    val skinPalette = when (character) {
        CharacterSpec.ELENA, CharacterSpec.MOCHI -> listOf(
            Color(0xFFF6E3CE),
            Color(0xFFD6BA9E),
            Color(0xFF9E826B),
            Color(0xFF59473A)
        )
        CharacterSpec.MARCO -> listOf(
            Color(0xFFE7C4A4),
            Color(0xFFC29876),
            Color(0xFF8C674B),
            Color(0xFF4F3827)
        )
        CharacterSpec.KAITO -> listOf(
            Color(0xFFF7E5D2),
            Color(0xFFD6B99E),
            Color(0xFF9A7F68),
            Color(0xFF564538)
        )
    }

    drawPath(
        path = headPath,
        brush = Brush.radialGradient(
            colors = skinPalette,
            center = Offset(cx - headRx * 0.28f, headCenterY - headRy * 0.24f),
            radius = (headRy * 1.48f).coerceAtLeast(16f)
        )
    )

    // Hair / Hat Band overlapping forehead inside the head silhouette
    clipPath(headPath) {
        when (character) {
            CharacterSpec.ELENA, CharacterSpec.MOCHI -> {
                // Curved parted brown hair cap (`Home_7dda243e.png`)
                val hairCapPath = Path().apply {
                    moveTo(headRect.left - headRx * 0.2f, headCenterY + headRy * 0.06f)
                    cubicTo(
                        cx - headRx * 0.65f,
                        headCenterY - headRy * 0.58f,
                        cx + headRx * 0.65f,
                        headCenterY - headRy * 0.58f,
                        headRect.right + headRx * 0.2f,
                        headCenterY + headRy * 0.06f
                    )
                    lineTo(headRect.right + headRx * 0.2f, headRect.top - headRy * 0.3f)
                    lineTo(headRect.left - headRx * 0.2f, headRect.top - headRy * 0.3f)
                    close()
                }
                drawPath(
                    path = hairCapPath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF694732),
                            Color(0xFF452D1F),
                            Color(0xFF24160E)
                        ),
                        center = Offset(cx - headRx * 0.2f, headCenterY - headRy * 0.65f),
                        radius = (headRx * 1.4f).coerceAtLeast(12f)
                    )
                )
            }
            CharacterSpec.MARCO -> {
                // White chef headband across forehead
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFD8DCE0), Color.White, Color(0xFFB5BAC0)),
                        startX = headRect.left,
                        endX = headRect.right
                    ),
                    topLeft = Offset(headRect.left, headRect.top),
                    size = Size(headRect.width, headRy * 0.46f)
                )
            }
            CharacterSpec.KAITO -> {
                // Sleek anime fringe across upper forehead
                val fringePath = Path().apply {
                    moveTo(headRect.left, headCenterY - headRy * 0.15f)
                    quadraticTo(cx, headCenterY - headRy * 0.52f, headRect.right, headCenterY - headRy * 0.12f)
                    lineTo(headRect.right, headRect.top)
                    lineTo(headRect.left, headRect.top)
                    close()
                }
                drawPath(
                    path = fringePath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFC9D9EC), Color(0xFF6B85A3)),
                        start = Offset(headRect.left, headRect.top),
                        end = Offset(headRect.right, headCenterY)
                    )
                )
            }
        }
    }

    // Subtle animated mouth/lip-sync indicator when Talking or Reacting
    if (runtimeState is CharacterRuntimeState.Talking || jawOpen > 0.06f) {
        val mouthY = headCenterY + headRy * 0.46f
        val mouthW = headRx * (0.22f + smile * 0.08f)
        val mouthH = max(headRy * 0.035f, headRy * 0.18f * jawOpen)
        drawRoundRect(
            color = Color(0xFF3E2921).copy(alpha = 0.65f),
            topLeft = Offset(cx - mouthW * 0.5f, mouthY - mouthH * 0.5f),
            size = Size(mouthW, mouthH),
            cornerRadius = CornerRadius(mouthH * 0.5f, mouthH * 0.5f)
        )
    } else if (blink > 0.4f) {
        val eyeY = headCenterY + headRy * 0.04f
        val eyeOffset = headRx * 0.34f
        val eyeW = headRx * 0.18f
        drawLine(
            color = Color(0xFF4A3628).copy(alpha = 0.45f),
            start = Offset(cx - eyeOffset - eyeW * 0.5f, eyeY),
            end = Offset(cx - eyeOffset + eyeW * 0.5f, eyeY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF4A3628).copy(alpha = 0.45f),
            start = Offset(cx + eyeOffset - eyeW * 0.5f, eyeY),
            end = Offset(cx + eyeOffset + eyeW * 0.5f, eyeY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // 5. Crisp Glowing Accent Rim Light Arc along the right side of the 3D Head (`Home_7dda243e.png`)
    drawArc(
        color = character.accent,
        startAngle = -68f,
        sweepAngle = 136f,
        useCenter = false,
        topLeft = Offset(headRect.left, headRect.top),
        size = headRect.size,
        style = Stroke(width = max(2.5f, unit * 0.009f), cap = StrokeCap.Round)
    )
}

/**
 * Renders the Full-Body Standing 3D Companion Figure matching
 * `Floating_-_talking_97d43686.png`, `Floating_-_quick_ask_f1220c67.png`, and `Floating_-_call_countdown_e7d55cfc.png`.
 */
private fun DrawScope.renderFullBody3DCompanion(
    character: CharacterSpec,
    pose: CharacterFramePose,
    runtimeState: CharacterRuntimeState,
    fxPhase: Float
) {
    val w = size.width
    val h = size.height
    if (w <= 4f || h <= 4f) return

    val breathPx = sin(pose.clipTimeSeconds * PI.toFloat()) * (h * 0.006f)
    val cx = w * 0.5f
    val headR = min(w * 0.22f, h * 0.095f)
    val headCy = h * 0.17f + breathPx

    // 1. Hair / Top Bun or Hat
    when (character) {
        CharacterSpec.ELENA, CharacterSpec.MOCHI -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF694732), Color(0xFF281910)),
                    center = Offset(cx, headCy - headR * 1.12f),
                    radius = headR * 0.5f
                ),
                radius = headR * 0.36f,
                center = Offset(cx, headCy - headR * 1.06f)
            )
        }
        CharacterSpec.MARCO -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color(0xFFA8AEB5)),
                    center = Offset(cx, headCy - headR * 1.15f),
                    radius = headR * 0.65f
                ),
                radius = headR * 0.52f,
                center = Offset(cx, headCy - headR * 1.08f)
            )
        }
        CharacterSpec.KAITO -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD0E0F2), Color(0xFF56708E)),
                    center = Offset(cx, headCy - headR * 0.4f),
                    radius = headR * 1.2f
                ),
                radius = headR * 1.08f,
                center = Offset(cx, headCy - headR * 0.12f)
            )
        }
    }

    // 2. Neck
    val neckW = headR * 0.46f
    val neckTop = headCy + headR * 0.82f
    val neckBot = headCy + headR * 1.38f
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFF8C705B), Color(0xFFEAD1B8), Color(0xFF5B4738)),
            startX = cx - neckW * 0.5f,
            endX = cx + neckW * 0.5f
        ),
        topLeft = Offset(cx - neckW * 0.5f, neckTop),
        size = Size(neckW, neckBot - neckTop),
        cornerRadius = CornerRadius(neckW * 0.3f, neckW * 0.3f)
    )

    // 3. Legs & Shoes (`Floating_-_talking_97d43686.png`)
    val torsoTop = neckBot - headR * 0.1f
    val torsoBot = h * 0.62f + breathPx * 0.5f
    val torsoHalfW = headR * 1.28f
    val legW = headR * 0.46f
    val legTop = torsoBot - headR * 0.1f
    val legBot = h * 0.89f
    val leftLegX = cx - headR * 0.56f
    val rightLegX = cx + headR * 0.10f

    listOf(leftLegX, rightLegX).forEach { lx ->
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFD8D7D5), Color(0xFFAFAEA9), Color(0xFF6B6A66)),
                startX = lx,
                endX = lx + legW
            ),
            topLeft = Offset(lx, legTop),
            size = Size(legW, legBot - legTop),
            cornerRadius = CornerRadius(legW * 0.25f, legW * 0.25f)
        )
        // Dark shoe oval
        drawOval(
            color = Color(0xFF2B2E36),
            topLeft = Offset(lx - legW * 0.18f, legBot - headR * 0.16f),
            size = Size(legW * 1.36f, headR * 0.36f)
        )
    }

    // 4. Left Arm (resting down with 3D spherical hand)
    val leftShoulderX = cx - torsoHalfW * 0.92f
    val shoulderY = torsoTop + headR * 0.35f
    val leftHandY = torsoBot + headR * 0.08f
    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFFD9C0A7), Color(0xFF8C715B))
        ),
        start = Offset(leftShoulderX, shoulderY),
        end = Offset(leftShoulderX - headR * 0.18f, leftHandY),
        strokeWidth = headR * 0.36f,
        cap = StrokeCap.Round
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFF0D8BF), Color(0xFF7D634F)),
            center = Offset(leftShoulderX - headR * 0.20f, leftHandY),
            radius = headR * 0.28f
        ),
        radius = headR * 0.22f,
        center = Offset(leftShoulderX - headR * 0.18f, leftHandY + headR * 0.08f)
    )

    // 5. Right Arm (waving up when Talking / Reacting, or resting when Idle/Listening)
    val isWavingOrTalking = runtimeState is CharacterRuntimeState.Talking ||
        runtimeState is CharacterRuntimeState.Reacting
    val rightShoulderX = cx + torsoHalfW * 0.92f
    if (isWavingOrTalking) {
        val waveAngle = sin(fxPhase * 3f) * (headR * 0.18f)
        val elbowX = rightShoulderX + headR * 0.56f
        val elbowY = shoulderY + headR * 0.82f
        val handX = elbowX + headR * 0.18f + waveAngle
        val handY = shoulderY - headR * 0.32f
        drawLine(
            color = Color(0xFFD8D9DB),
            start = Offset(rightShoulderX, shoulderY),
            end = Offset(elbowX, elbowY),
            strokeWidth = headR * 0.38f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFD6BBA0),
            start = Offset(elbowX, elbowY),
            end = Offset(handX, handY),
            strokeWidth = headR * 0.32f,
            cap = StrokeCap.Round
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF3DEC8), Color(0xFF836853)),
                center = Offset(handX, handY),
                radius = headR * 0.30f
            ),
            radius = headR * 0.24f,
            center = Offset(handX, handY)
        )
    } else {
        val rightHandY = torsoBot + headR * 0.08f
        drawLine(
            color = Color(0xFFD6BBA0),
            start = Offset(rightShoulderX, shoulderY),
            end = Offset(rightShoulderX + headR * 0.18f, rightHandY),
            strokeWidth = headR * 0.36f,
            cap = StrokeCap.Round
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF3DEC8), Color(0xFF836853)),
                center = Offset(rightShoulderX + headR * 0.18f, rightHandY),
                radius = headR * 0.28f
            ),
            radius = headR * 0.22f,
            center = Offset(rightShoulderX + headR * 0.18f, rightHandY + headR * 0.08f)
        )
    }

    // 6. Sculpted 3D Torso Tunic (`Floating_-_talking_97d43686.png`)
    val tunicRect = Rect(
        left = cx - torsoHalfW,
        top = torsoTop,
        right = cx + torsoHalfW,
        bottom = torsoBot
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFF4F5F6),
                Color(0xFFD2D4D6),
                Color(0xFF7B7E84)
            ),
            center = Offset(cx - torsoHalfW * 0.25f, torsoTop + (torsoBot - torsoTop) * 0.28f),
            radius = (torsoHalfW * 2.1f).coerceAtLeast(16f)
        ),
        topLeft = tunicRect.topLeft,
        size = tunicRect.size,
        cornerRadius = CornerRadius(torsoHalfW * 0.55f, torsoHalfW * 0.22f)
    )

    // V-neck collar on tunic
    val vPath = Path().apply {
        moveTo(cx - neckW * 0.65f, torsoTop + 2f)
        lineTo(cx, torsoTop + headR * 0.68f)
        lineTo(cx + neckW * 0.65f, torsoTop + 2f)
    }
    drawPath(
        path = vPath,
        color = Color(0xFF83868C),
        style = Stroke(width = max(1.5f, headR * 0.06f), cap = StrokeCap.Round)
    )

    // Right Tunic Accent Rim Light
    drawLine(
        color = character.accent,
        start = Offset(tunicRect.right, torsoTop + headR * 0.45f),
        end = Offset(tunicRect.right, torsoBot - headR * 0.12f),
        strokeWidth = max(2f, headR * 0.08f),
        cap = StrokeCap.Round
    )

    // 7. 3D Head Sphere + Hair Cap + Accent Rim Light
    val headRect = Rect(
        left = cx - headR,
        top = headCy - headR * 1.08f,
        right = cx + headR,
        bottom = headCy + headR * 1.08f
    )
    val headPath = Path().apply { addOval(headRect) }
    drawPath(
        path = headPath,
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFF6E3CE), Color(0xFFD0B397), Color(0xFF634E3F)),
            center = Offset(cx - headR * 0.28f, headCy - headR * 0.25f),
            radius = (headR * 1.45f).coerceAtLeast(10f)
        )
    )

    if (character == CharacterSpec.ELENA) {
        clipPath(headPath) {
            drawCircle(
                color = Color(0xFF3E291C),
                radius = headR * 1.05f,
                center = Offset(cx, headCy - headR * 0.68f)
            )
        }
    }

    drawArc(
        color = character.accent,
        startAngle = -68f,
        sweepAngle = 136f,
        useCenter = false,
        topLeft = headRect.topLeft,
        size = headRect.size,
        style = Stroke(width = max(2f, headR * 0.08f), cap = StrokeCap.Round)
    )
}

/**
 * Single soft particle burst (max 40 particles, 600ms lifetime, in `accent` color)
 * triggered on `happy` only (per Motion & Effects spec).
 */
private fun DrawScope.renderHappyParticleBurst(
    accentColor: Color,
    progress: Float
) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.42f
    val maxRadius = minOf(size.width, size.height) * 0.42f
    val fade = (1f - progress).coerceIn(0f, 1f)
    val particleCount = 40

    for (i in 0 until particleCount) {
        val angle = (i * 2f * PI.toFloat() / particleCount) + (i % 3) * 0.18f
        val speedFactor = 0.45f + (i % 5) * 0.14f
        val dist = maxRadius * progress * speedFactor
        val px = cx + cos(angle) * dist
        val py = cy + sin(angle) * dist - progress * 18f
        val radius = (2.2f + (i % 3) * 1.4f) * fade

        drawCircle(
            color = accentColor.copy(alpha = fade * 0.78f),
            radius = radius,
            center = Offset(px, py)
        )
    }
}

private fun sampleAnimationQuat(track: GlbAnimationTrack?, timeSec: Float): FloatArray {
    if (track == null || track.times.isEmpty() || track.rotations.isEmpty()) {
        return floatArrayOf(0f, 0f, 0f, 1f)
    }
    val times = track.times
    val rots = track.rotations
    if (timeSec <= times.first()) return rots.first()
    if (timeSec >= times.last()) return rots.last()

    for (i in 0 until times.size - 1) {
        val t0 = times[i]
        val t1 = times[i + 1]
        if (timeSec in t0..t1) {
            val frac = ((timeSec - t0) / (t1 - t0).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
            val q0 = rots[i]
            val q1 = rots[i + 1]
            return floatArrayOf(
                q0[0] * (1f - frac) + q1[0] * frac,
                q0[1] * (1f - frac) + q1[1] * frac,
                q0[2] * (1f - frac) + q1[2] * frac,
                q0[3] * (1f - frac) + q1[3] * frac
            )
        }
    }
    return rots.last()
}
