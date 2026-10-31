package com.example.ui.character

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Particle effects around the companion. Textured sprites (glow, star, smoke, flame, streak, dust,
 * ring — see tools/make_vfx_textures.py) are tinted and blended additively, the same technique
 * game engines use for sparks, fire, stardust and magic.
 *
 *  idle        drifting stardust with twinkle
 *  listening   motes drawn into the companion + voice-reactive pulse rings
 *  thinking    golden sand vortex orbiting overhead
 *  talking     soft sparkles carried out with the voice
 *  happy       firework burst with gravity, shockwave and confetti
 *  wave        sparkle trail from the waving side
 *  dance       spotlight, colour bursts on every beat, music notes
 *  love / pet  glowing hearts
 *  lightning   branching bolts with glow, sparks and flash
 *  calm        slow blue mist and a breathing ring
 *  fire        rising flames and embers
 */
@Composable
fun CompanionVfxLayer(
    state: CharacterRuntimeState,
    accent: Color,
    micLevel: Float,
    modifier: Modifier = Modifier
) {
    val sprites = VfxSprites(
        glow = ImageBitmap.imageResource(R.drawable.vfx_glow),
        star = ImageBitmap.imageResource(R.drawable.vfx_star),
        smoke = ImageBitmap.imageResource(R.drawable.vfx_smoke),
        flame = ImageBitmap.imageResource(R.drawable.vfx_flame),
        streak = ImageBitmap.imageResource(R.drawable.vfx_streak),
        dust = ImageBitmap.imageResource(R.drawable.vfx_dust),
        ring = ImageBitmap.imageResource(R.drawable.vfx_ring)
    )
    val engine = remember { VfxEngine() }
    val currentState by rememberUpdatedState(state)
    val currentMic by rememberUpdatedState(micLevel)
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(state) { engine.onStateChanged(state) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                engine.step(dt, currentState, currentMic)
                frame++
            }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Canvas(modifier.onSizeChanged { engine.layout(it.width.toFloat(), it.height.toFloat(), density) }) {
        frame // read so the canvas redraws every frame
        engine.draw(this, sprites, accent)
    }
}

class VfxSprites(
    val glow: ImageBitmap,
    val star: ImageBitmap,
    val smoke: ImageBitmap,
    val flame: ImageBitmap,
    val streak: ImageBitmap,
    val dust: ImageBitmap,
    val ring: ImageBitmap
)

/** A fixed-capacity particle system (structure of arrays, no per-frame allocation). */
class VfxEngine(private val capacity: Int = 420) {
    private enum class Sprite { GLOW, STAR, SMOKE, FLAME, STREAK, DUST, RING }
    private enum class Tint { ACCENT, GOLD, WHITE, PINK, BLUE, VIOLET, FIRE, MIST }

    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val size0 = FloatArray(capacity)
    private val size1 = FloatArray(capacity)
    private val gravity = FloatArray(capacity)
    private val drag = FloatArray(capacity)
    private val swirl = FloatArray(capacity)
    private val phase = FloatArray(capacity)
    private val alpha = FloatArray(capacity)
    private val sprite = IntArray(capacity)
    private val tint = IntArray(capacity)
    private val alive = BooleanArray(capacity)

    private var w = 0f
    private var h = 0f
    private var dp = 1f
    private var time = 0f
    private var since = 0f
    private var state: CharacterRuntimeState = CharacterRuntimeState.Idle
    private var spawnAcc = 0f
    private var ringAcc = 0f
    private var beatIndex = -1
    private var pendingBurst = false
    private var mic = 0f
    private val rnd = Random(7)
    private val filters = HashMap<Int, ColorFilter>()

    // Lightning bolt cache (re-rolled on each strike).
    private val boltPaths = Array(3) { Path() }
    private var boltStrikeAt = -1f

    private val cx get() = w / 2f
    private val cy get() = h * 0.52f
    private val radius get() = min(w, h) * 0.34f

    fun layout(width: Float, height: Float, density: Float) {
        w = width; h = height; dp = density
    }

    fun onStateChanged(s: CharacterRuntimeState) {
        state = s
        since = 0f
        beatIndex = -1
        if (s is CharacterRuntimeState.Reacting) {
            when (s.clip.lowercase()) {
                "happy", "celebrate", "cheer" -> pendingBurst = true
                "storm", "lightning" -> boltStrikeAt = -1f
            }
        }
    }

    fun step(dt: Float, s: CharacterRuntimeState, micLevel: Float) {
        if (w <= 0f) return
        time += dt
        since += dt
        mic = (micLevel / 10f).coerceIn(0f, 1f)
        if (pendingBurst) { pendingBurst = false; burst() }
        emit(dt, s)
        for (i in 0 until capacity) {
            if (!alive[i]) continue
            life[i] += dt
            if (life[i] >= maxLife[i]) { alive[i] = false; continue }
            if (swirl[i] != 0f) {
                // Orbit around the head: tangential push + gentle pull to the orbit radius.
                val dx = x[i] - cx
                val dy = (y[i] - (cy - radius * 0.55f)) * 3f
                val ang = atan2(dy, dx)
                val target = radius * 1.15f
                val dist = hypot(dx, dy)
                vx[i] += (-sin(ang) * swirl[i] - cos(ang) * (dist - target) * 2f) * dt
                vy[i] += (cos(ang) * swirl[i] / 3f - sin(ang) * (dist - target) * 0.6f) * dt
            }
            vy[i] += gravity[i] * dt
            val d = exp(-drag[i] * dt)
            vx[i] *= d; vy[i] *= d
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
    }

    // ---- Emitters --------------------------------------------------------------------------

    private fun emit(dt: Float, s: CharacterRuntimeState) {
        when (s) {
            CharacterRuntimeState.Idle -> rate(dt, 9f) { stardust() }
            CharacterRuntimeState.Listening -> {
                rate(dt, 26f + 40f * mic) { inwardMote() }
                ringAcc += dt
                if (ringAcc > 0.55f - 0.25f * mic) { ringAcc = 0f; ring(Tint.ACCENT, 1.2f, 0.9f + mic * 0.6f) }
            }
            CharacterRuntimeState.Thinking -> rate(dt, 55f) { sandGrain() }
            CharacterRuntimeState.Talking -> rate(dt, 18f) { voiceSparkle() }
            is CharacterRuntimeState.Reacting -> when (s.clip.lowercase()) {
                "happy", "celebrate", "cheer" -> if (since < 1.6f) rate(dt, 40f) { confetti() }
                "wave" -> rate(dt, 30f) { waveSparkle() }
                "dance" -> danceBeat()
                "love", "pet" -> rate(dt, 4f) { heartGlow() }
                "storm", "lightning" -> rate(dt, 10f) { stardust(Tint.BLUE) }
                "calm", "think" -> rate(dt, 6f) { mistPuff() }
                "fire", "hype" -> rate(dt, 70f) { flame() }
                else -> rate(dt, 9f) { stardust() }
            }
        }
    }

    private inline fun rate(dt: Float, perSecond: Float, spawn: () -> Unit) {
        spawnAcc += dt * perSecond
        while (spawnAcc >= 1f) { spawnAcc -= 1f; spawn() }
    }

    private fun slot(): Int {
        for (i in 0 until capacity) if (!alive[i]) return i
        // Recycle the oldest.
        var best = 0
        var bestRatio = -1f
        for (i in 0 until capacity) { val r = life[i] / maxLife[i]; if (r > bestRatio) { bestRatio = r; best = i } }
        return best
    }

    private fun spawn(
        px: Float, py: Float, pvx: Float, pvy: Float, lifeSec: Float, sz0: Float, sz1: Float,
        spr: Sprite, t: Tint, g: Float = 0f, dr: Float = 0.6f, sw: Float = 0f, a: Float = 1f
    ) {
        val i = slot()
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = 0f; maxLife[i] = lifeSec; size0[i] = sz0; size1[i] = sz1
        gravity[i] = g; drag[i] = dr; swirl[i] = sw; alpha[i] = a
        sprite[i] = spr.ordinal; tint[i] = t.ordinal; phase[i] = rnd.nextFloat() * 6.28f
        alive[i] = true
    }

    private fun f(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    private fun stardust(t: Tint? = null) {
        val ang = f(0f, 6.28f)
        val r = radius * f(0.7f, 1.7f)
        spawn(
            cx + cos(ang) * r, cy + sin(ang) * r * 0.8f + radius * 0.3f,
            f(-6f, 6f) * dp, f(-28f, -10f) * dp, f(2.6f, 4.2f),
            f(12f, 26f) * dp, f(5f, 10f) * dp,
            if (rnd.nextFloat() < 0.4f) Sprite.STAR else Sprite.DUST,
            t ?: listOf(Tint.GOLD, Tint.WHITE, Tint.ACCENT)[rnd.nextInt(3)], dr = 0.2f, a = 1f
        )
    }

    private fun inwardMote() {
        val ang = f(0f, 6.28f)
        val r = radius * f(1.6f, 2.2f)
        val px = cx + cos(ang) * r
        val py = cy + sin(ang) * r
        val speed = f(60f, 110f) * dp * (1f + mic)
        spawn(px, py, -cos(ang) * speed, -sin(ang) * speed, r / speed * 0.9f, f(10f, 18f) * dp, 3f * dp, Sprite.DUST, if (rnd.nextFloat() < 0.3f) Tint.WHITE else Tint.ACCENT, dr = 0f, a = 1f)
    }

    private fun ring(t: Tint, lifeSec: Float, scale: Float) {
        spawn(cx, cy + radius * 0.9f, 0f, 0f, lifeSec, radius * 0.6f, radius * 2.2f * scale, Sprite.RING, t, dr = 0f, a = 0.55f)
    }

    private fun sandGrain() {
        val ang = f(0f, 6.28f)
        spawn(
            cx + cos(ang) * radius * 1.15f, cy - radius * 0.55f + sin(ang) * radius * 0.38f,
            0f, 0f, f(1.6f, 2.6f), f(8f, 14f) * dp, f(4f, 7f) * dp,
            Sprite.DUST, if (rnd.nextFloat() < 0.8f) Tint.GOLD else Tint.WHITE, dr = 1.2f, sw = f(240f, 360f) * dp, a = 0.95f
        )
    }

    private fun voiceSparkle() {
        val side = if (rnd.nextBoolean()) 1f else -1f
        spawn(
            cx + side * radius * 0.4f, cy + radius * 0.1f,
            side * f(60f, 120f) * dp, f(-40f, 10f) * dp, f(0.8f, 1.3f),
            f(14f, 24f) * dp, 4f * dp, Sprite.STAR, if (rnd.nextBoolean()) Tint.ACCENT else Tint.WHITE, dr = 1.6f, a = 1f
        )
    }

    private fun burst() {
        repeat(70) {
            val ang = f(0f, 6.28f)
            val speed = f(220f, 520f) * dp
            spawn(
                cx, cy, cos(ang) * speed, sin(ang) * speed, f(0.9f, 1.5f),
                f(20f, 36f) * dp, 4f * dp,
                if (it % 3 == 0) Sprite.STREAK else Sprite.STAR,
                listOf(Tint.ACCENT, Tint.GOLD, Tint.PINK, Tint.WHITE)[it % 4], g = 380f * dp, dr = 2.2f
            )
        }
        spawn(cx, cy, 0f, 0f, 0.7f, radius * 0.5f, radius * 3f, Sprite.RING, Tint.GOLD, dr = 0f, a = 0.9f)
        spawn(cx, cy, 0f, 0f, 0.35f, radius * 1.5f, radius * 3.5f, Sprite.GLOW, Tint.WHITE, dr = 0f, a = 0.6f)
    }

    private fun confetti() {
        spawn(
            f(0.05f, 0.95f) * w, -10f * dp, f(-30f, 30f) * dp, f(60f, 140f) * dp, f(1.8f, 2.6f),
            f(9f, 14f) * dp, f(9f, 14f) * dp, Sprite.DUST,
            listOf(Tint.GOLD, Tint.PINK, Tint.ACCENT, Tint.BLUE, Tint.VIOLET)[rnd.nextInt(5)], g = 40f * dp, dr = 0.4f
        )
    }

    private fun waveSparkle() {
        spawn(
            cx + radius * 0.95f, cy, f(10f, 60f) * dp, f(-90f, -20f) * dp, f(0.7f, 1.1f),
            f(14f, 26f) * dp, 4f * dp, Sprite.STAR, if (rnd.nextBoolean()) Tint.GOLD else Tint.ACCENT, g = 60f * dp, dr = 1.2f
        )
    }

    private fun danceBeat() {
        val beat = (since * 2f).toInt()
        if (beat == beatIndex) return
        beatIndex = beat
        val side = if (beat % 2 == 0) -1f else 1f
        val t = listOf(Tint.PINK, Tint.BLUE, Tint.VIOLET, Tint.GOLD)[beat % 4]
        repeat(18) {
            val ang = f(-2.6f, -0.5f)
            val speed = f(120f, 260f) * dp
            spawn(cx + side * radius * 0.45f, cy + radius * 0.85f, cos(ang) * speed, sin(ang) * speed, f(0.6f, 1.0f), f(16f, 26f) * dp, 4f * dp, Sprite.STAR, t, g = 300f * dp, dr = 1.5f)
        }
        spawn(cx + side * radius * 0.45f, cy + radius * 0.9f, 0f, 0f, 0.5f, radius * 0.3f, radius * 1.4f, Sprite.RING, t, dr = 0f, a = 0.7f)
    }

    private fun heartGlow() {
        spawn(cx + f(-0.8f, 0.8f) * radius, cy - radius * 0.4f, f(-10f, 10f) * dp, f(-60f, -35f) * dp, f(1.6f, 2.2f), radius * 0.45f, radius * 0.6f, Sprite.GLOW, Tint.PINK, dr = 0.2f, a = 0.9f)
    }

    private fun mistPuff() {
        val ang = f(0f, 6.28f)
        spawn(cx + cos(ang) * radius * 0.9f, cy + sin(ang) * radius * 0.5f, cos(ang) * 12f * dp, f(-12f, -4f) * dp, f(3f, 4.5f), radius * 0.5f, radius * 1.4f, Sprite.SMOKE, Tint.MIST, dr = 0.3f, a = 0.35f)
    }

    private fun flame() {
        spawn(cx + f(-0.75f, 0.75f) * radius, cy + radius * 0.95f, f(-10f, 10f) * dp, f(-170f, -90f) * dp, f(0.5f, 0.9f), f(48f, 78f) * dp, f(14f, 24f) * dp, Sprite.FLAME, Tint.FIRE, dr = 0.5f, a = 0.95f)
        if (rnd.nextFloat() < 0.25f) {
            spawn(cx + f(-0.6f, 0.6f) * radius, cy + radius * 0.6f, f(-30f, 30f) * dp, f(-220f, -120f) * dp, f(0.8f, 1.4f), 10f * dp, 3f * dp, Sprite.DUST, Tint.GOLD, dr = 0.8f)
        }
    }

    // ---- Drawing ---------------------------------------------------------------------------

    fun draw(scope: DrawScope, sprites: VfxSprites, accent: Color) = with(scope) {
        if (w <= 0f) return@with
        val s = state
        if (s is CharacterRuntimeState.Reacting) {
            when (s.clip.lowercase()) {
                "dance" -> spotlight(accent)
                "storm", "lightning" -> lightning(accent)
                "calm", "think" -> breathingRing(accent)
                "love", "pet" -> Unit
                else -> Unit
            }
        }
        for (i in 0 until capacity) {
            if (!alive[i]) continue
            val p = life[i] / maxLife[i]
            val sz = size0[i] + (size1[i] - size0[i]) * p
            val fade = when (Sprite.entries[sprite[i]]) {
                Sprite.RING -> (1f - p)
                Sprite.SMOKE -> sin(p * PI.toFloat())
                else -> if (p < 0.15f) p / 0.15f else (1f - p) / 0.85f
            }
            val twinkle = if (sprite[i] == Sprite.STAR.ordinal || sprite[i] == Sprite.DUST.ordinal) 0.65f + 0.35f * sin(time * 9f + phase[i]) else 1f
            val a = (alpha[i] * fade * twinkle).coerceIn(0f, 1f)
            if (a <= 0.01f || sz < 0.5f) continue
            val color = colorFor(Tint.entries[tint[i]], accent, p)
            val img = when (Sprite.entries[sprite[i]]) {
                Sprite.GLOW -> sprites.glow
                Sprite.STAR -> sprites.star
                Sprite.SMOKE -> sprites.smoke
                Sprite.FLAME -> sprites.flame
                Sprite.STREAK -> sprites.streak
                Sprite.DUST -> sprites.dust
                Sprite.RING -> sprites.ring
            }
            val isz = sz.toInt().coerceAtLeast(1)
            val dst = IntOffset((x[i] - sz / 2f).toInt(), (y[i] - sz / 2f).toInt())
            if (sprite[i] == Sprite.STREAK.ordinal) {
                val deg = Math.toDegrees(atan2(vy[i].toDouble(), vx[i].toDouble())).toFloat() + 90f
                rotate(deg, Offset(x[i], y[i])) {
                    drawImage(img, dstOffset = IntOffset((x[i] - sz / 4f).toInt(), (y[i] - sz).toInt()), dstSize = IntSize((sz / 2f).toInt().coerceAtLeast(1), (sz * 2f).toInt()), alpha = a, colorFilter = filter(color), blendMode = BlendMode.Plus)
                }
            } else if (tint[i] == Tint.PINK.ordinal && sprite[i] == Sprite.GLOW.ordinal) {
                drawImage(img, dstOffset = dst, dstSize = IntSize(isz, isz), alpha = a * 0.6f, colorFilter = filter(color), blendMode = BlendMode.Plus)
                heart(Offset(x[i], y[i]), sz * 0.26f, color.copy(alpha = a))
            } else {
                drawImage(img, dstOffset = dst, dstSize = IntSize(isz, isz), alpha = a, colorFilter = filter(color), blendMode = BlendMode.Plus)
            }
        }
        if (s is CharacterRuntimeState.Reacting && s.clip.equals("dance", true)) musicNotes(accent)
    }

    private fun filter(c: Color): ColorFilter = filters.getOrPut(c.copy(alpha = 1f).toArgb()) { ColorFilter.tint(c.copy(alpha = 1f)) }

    private fun colorFor(t: Tint, accent: Color, p: Float): Color = when (t) {
        Tint.ACCENT -> accent
        Tint.GOLD -> Color(0xFFFFD27A)
        Tint.WHITE -> Color(0xFFFFFBF0)
        Tint.PINK -> Color(0xFFFF7FA2)
        Tint.BLUE -> Color(0xFF8CC8FF)
        Tint.VIOLET -> Color(0xFFB79CFF)
        Tint.MIST -> Color(0xFFA8DAFF)
        Tint.FIRE -> when {           // yellow core → orange → deep red as it rises and cools
            p < 0.3f -> lerpColor(Color(0xFFFFF1B0), Color(0xFFFFA43A), p / 0.3f)
            else -> lerpColor(Color(0xFFFFA43A), Color(0xFFB8261A), ((p - 0.3f) / 0.7f).coerceIn(0f, 1f))
        }
    }

    private fun lerpColor(a: Color, b: Color, t: Float) =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

    private fun DrawScope.spotlight(accent: Color) {
        val sway = sin(since * 2.2f) * radius * 0.6f
        val path = Path().apply {
            moveTo(cx + sway * 0.3f - radius * 0.15f, 0f)
            lineTo(cx + sway * 0.3f + radius * 0.15f, 0f)
            lineTo(cx + sway + radius * 1.2f, cy + radius)
            lineTo(cx + sway - radius * 1.2f, cy + radius)
            close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.0f), accent.copy(alpha = 0.16f), accent.copy(alpha = 0.05f)), 0f, cy + radius), blendMode = BlendMode.Plus)
    }

    private fun DrawScope.musicNotes(accent: Color) {
        for (i in 0 until 4) {
            val p = (since * 0.6f + i / 4f) % 1f
            val nx = cx + (if (i % 2 == 0) -1 else 1) * radius * (1.0f + p * 0.4f)
            val ny = cy - p * radius * 1.5f
            val a = sin(p * PI.toFloat())
            val col = (if (i % 2 == 0) accent else Color(0xFFFFD27A)).copy(alpha = a)
            drawCircle(col, radius * 0.07f, Offset(nx, ny))
            drawLine(col, Offset(nx + radius * 0.06f, ny), Offset(nx + radius * 0.06f, ny - radius * 0.28f), strokeWidth = 2.5f * dp)
            drawLine(col, Offset(nx + radius * 0.06f, ny - radius * 0.28f), Offset(nx + radius * 0.2f, ny - radius * 0.2f), strokeWidth = 2.5f * dp)
        }
    }

    private fun DrawScope.lightning(accent: Color) {
        val period = 0.9f
        val cycle = since % period
        val strikeIndex = (since / period).toInt()
        if (boltStrikeAt.toInt() != strikeIndex) {
            boltStrikeAt = strikeIndex.toFloat()
            boltPaths.forEachIndexed { b, path ->
                path.reset()
                var bx = cx + f(-1.2f, 1.2f) * radius
                var by = 0f
                path.moveTo(bx, by)
                val endY = cy - radius * 0.2f
                val steps = 9
                for (k in 1..steps) {
                    bx += f(-0.28f, 0.28f) * radius
                    by = endY * k / steps
                    path.lineTo(bx, by)
                    if (b == 0 && k == steps / 2) {
                        // a side branch
                        path.moveTo(bx, by)
                        path.lineTo(bx + f(-0.6f, 0.6f) * radius, by + radius * 0.4f)
                        path.moveTo(bx, by)
                    }
                }
                if (b == 0) repeat(12) {
                    val ang = f(0f, 6.28f); val sp = f(120f, 300f) * dp
                    spawn(bx, by, cos(ang) * sp, sin(ang) * sp, f(0.3f, 0.6f), 22f * dp, 4f * dp, Sprite.STREAK, Tint.BLUE, g = 400f * dp, dr = 2f)
                }
            }
        }
        if (cycle > 0.22f) return
        val a = 1f - cycle / 0.22f
        drawRect(Color(0xFFBFE3FF).copy(alpha = 0.18f * a), blendMode = BlendMode.Plus)
        boltPaths.forEachIndexed { b, path ->
            if (b > 1) return@forEachIndexed
            drawPath(path, accent.copy(alpha = 0.35f * a), style = Stroke(width = 12f * dp, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            drawPath(path, Color(0xFF9CCBFF).copy(alpha = 0.6f * a), style = Stroke(width = 5f * dp, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
            drawPath(path, Color.White.copy(alpha = a), style = Stroke(width = 1.8f * dp, cap = StrokeCap.Round))
        }
    }

    private fun DrawScope.breathingRing(accent: Color) {
        val breath = 0.5f + 0.5f * sin(since * (2f * PI.toFloat() / 8f) - PI.toFloat() / 2f) // 8 s in/out
        val r = radius * (1.05f + 0.45f * breath)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF9FD8FF).copy(alpha = 0.18f), Color.Transparent), Offset(cx, cy), r * 1.5f), r * 1.5f, Offset(cx, cy), blendMode = BlendMode.Plus)
        drawCircle(accent.copy(alpha = 0.45f), r, Offset(cx, cy), style = Stroke(width = 2.5f * dp))
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

    @Suppress("unused")
    private fun clamp01(v: Float) = max(0f, min(1f, v))
}
