package com.example.ui.character

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Real-time 3D companion rendered with Google Filament on a transparent TextureView, so it can sit
 * borderless on the Home stage or float over other apps.
 *
 * Motion is procedural (no baked clips needed): breathing, squash & stretch, hops, tilts and
 * wobbles are driven by the companion's state and the live speech level. If the .glb contains
 * animation clips named like the app's states ("idle", "talk", "wave"…), those are blended in too.
 */
@Composable
fun Mochi3DView(
    assetPath: String,
    controller: CharacterController,
    modifier: Modifier = Modifier,
    yawOffsetDeg: Float = 0f
) {
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    val renderer = remember(assetPath) { MochiSceneRenderer(context.applicationContext, assetPath, yawOffsetDeg) }

    LaunchedEffect(state) { renderer.motion.setState(state) }
    DisposableEffect(renderer) {
        renderer.speechLevel = { controller.pose.value.morphWeights["jawOpen"] ?: 0f }
        onDispose { renderer.destroy() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx -> TextureView(ctx).also { renderer.attach(it) } }
    )
}

/** Turns companion states into smooth body and limb motion. Pure math, unit-testable. */
class MochiMotion(seed: Long = 1L) {
    data class Pose(
        var bob: Float = 0f,
        var sideX: Float = 0f,
        var yawDeg: Float = 0f,
        var pitchDeg: Float = 0f,
        var rollDeg: Float = 0f,
        var stretchY: Float = 1f,
        var widthXZ: Float = 1f,
        /** Little arms: 0 = resting against the body, 60+ = raised out to the side. */
        var leftArmDeg: Float = 0f,
        var rightArmDeg: Float = 0f,
        /** Feet: forward/back step angle. */
        var leftLegDeg: Float = 0f,
        var rightLegDeg: Float = 0f
    )

    private val rnd = kotlin.random.Random(seed)
    private var state: CharacterRuntimeState = CharacterRuntimeState.Idle
    private var stateStartSec = 0f
    private var nowSec = 0f
    private val current = Pose()

    // Idle variety: every 6–12 s Mochi does a small thing on its own.
    private var nextIdleActionAt = 6f
    private var idleAction = 0
    private var idleActionStart = -10f

    fun setState(newState: CharacterRuntimeState) {
        if (newState != state) {
            state = newState
            stateStartSec = nowSec
        }
    }

    fun step(dtSec: Float, speechLevel: Float): Pose {
        nowSec += dtSec
        val t = nowSec
        val since = nowSec - stateStartSec
        val p = Pose()
        val tau = 2f * PI.toFloat()

        // Always alive: breathing, floating, tiny sway.
        val breathe = sin(tau * t / 3.4f)
        p.bob = 0.016f * breathe
        p.stretchY = 1f + 0.02f * breathe
        p.widthXZ = 1f - 0.008f * breathe
        p.yawDeg = 4f * sin(t * 0.45f)
        p.leftArmDeg = 4f + 3f * sin(t * 1.1f)
        p.rightArmDeg = 4f + 3f * sin(t * 1.1f + 1.3f)

        when (val s = state) {
            CharacterRuntimeState.Idle -> idleLife(t, p)
            CharacterRuntimeState.Listening -> {
                p.pitchDeg = 10f                                  // leans in
                p.bob += 0.025f + 0.01f * sin(t * 6f)
                p.stretchY += 0.03f
                p.rollDeg = 6f * sin(t * 1.6f)                    // curious head tilt
                p.leftArmDeg = 18f; p.rightArmDeg = 18f           // little arms perk up
            }
            CharacterRuntimeState.Thinking -> {
                p.rollDeg = 11f * sin(t * 0.9f)
                p.yawDeg = 22f * sin(t * 0.55f)
                p.bob += 0.02f * sin(t * 1.8f)
                p.rightArmDeg = 42f + 6f * sin(t * 3f)            // "hmm" arm to the chin
                p.leftArmDeg = 6f
            }
            CharacterRuntimeState.Talking -> {
                val lvl = speechLevel.coerceIn(0f, 1f)
                p.stretchY += 0.08f * lvl
                p.widthXZ -= 0.04f * lvl
                p.pitchDeg = 5f * lvl + 2f * sin(t * 4f)          // nods while speaking
                p.bob += 0.015f * lvl
                p.leftArmDeg = 10f + 28f * lvl * (0.5f + 0.5f * sin(t * 5.2f))
                p.rightArmDeg = 10f + 28f * lvl * (0.5f + 0.5f * sin(t * 5.2f + 2.1f))
            }
            is CharacterRuntimeState.Reacting -> reaction(s.clip.lowercase(), since, t, p)
        }

        // Critically-damped smoothing so nothing snaps; limbs move a little faster than the body.
        val k = 1f - exp(-dtSec * 10f)
        val kl = 1f - exp(-dtSec * 16f)
        current.bob += (p.bob - current.bob) * k * 1.4f
        current.sideX += (p.sideX - current.sideX) * k
        current.yawDeg += (p.yawDeg - current.yawDeg) * k
        current.pitchDeg += (p.pitchDeg - current.pitchDeg) * k
        current.rollDeg += (p.rollDeg - current.rollDeg) * k
        current.stretchY += (p.stretchY - current.stretchY) * min(1f, k * 1.8f)
        current.widthXZ += (p.widthXZ - current.widthXZ) * min(1f, k * 1.8f)
        current.leftArmDeg += (p.leftArmDeg - current.leftArmDeg) * kl
        current.rightArmDeg += (p.rightArmDeg - current.rightArmDeg) * kl
        current.leftLegDeg += (p.leftLegDeg - current.leftLegDeg) * kl
        current.rightLegDeg += (p.rightLegDeg - current.rightLegDeg) * kl
        return current
    }

    private fun idleLife(t: Float, p: Pose) {
        if (t >= nextIdleActionAt) {
            idleAction = rnd.nextInt(5)
            idleActionStart = t
            nextIdleActionAt = t + 6f + rnd.nextFloat() * 6f
        }
        val a = t - idleActionStart
        when (idleAction) {
            0 -> if (a < 2.2f) p.yawDeg = 28f * sin(a / 2.2f * 2f * PI.toFloat())          // looks around
            1 -> if (a < 0.6f) { val h = 4f * (a / 0.6f) * (1f - a / 0.6f); p.bob += 0.07f * h; p.stretchY += 0.06f * h } // little hop
            2 -> if (a < 1.6f) { p.leftArmDeg = 55f * sin(a / 1.6f * PI.toFloat()); p.rightArmDeg = p.leftArmDeg } // stretch
            3 -> if (a < 1.2f) p.rollDeg = 9f * sin(a * 10f) * (1f - a / 1.2f)            // wiggle
            4 -> if (a < 1.4f) { p.pitchDeg = -8f * sin(a / 1.4f * PI.toFloat()) }       // looks up
        }
    }

    private fun hop(phase: Float) = 4f * phase * (1f - phase)

    private fun reaction(clip: String, since: Float, t: Float, p: Pose) {
        val tau = 2f * PI.toFloat()
        when (clip) {
            "happy", "cheer" -> {
                val cycle = 0.5f
                if (since < cycle * 2) {
                    val ph = (since % cycle) / cycle
                    p.bob += 0.15f * hop(ph)
                    p.stretchY += 0.12f * hop(ph) - (if (ph < 0.12f || ph > 0.88f) 0.12f else 0f)
                    p.widthXZ += if (ph < 0.12f || ph > 0.88f) 0.08f else 0f
                }
                p.leftArmDeg = 70f; p.rightArmDeg = 70f
            }
            "celebrate" -> {
                val ph = (since / 0.9f).coerceAtMost(1f)
                p.bob += 0.28f * hop(ph)
                p.yawDeg = 360f * ph
                p.stretchY += 0.15f * hop(ph)
                p.leftArmDeg = 80f; p.rightArmDeg = 80f
            }
            "wave" -> {
                val fade = (1f - since / 2.2f).coerceAtLeast(0f)
                p.rightArmDeg = 45f + 25f * sin(since * 11f) * fade + (1f - fade) * -40f
                p.rollDeg = -8f + 4f * sin(since * 11f) * fade
                p.bob += 0.02f
            }
            "dance" -> {
                // 4-beat loop at 120 bpm: bounce, alternate arms & feet, twist; spin every 4th bar.
                val beat = since * 2f
                val inBeat = beat % 1f
                val bar = (beat / 4f).toInt()
                val b = (beat.toInt() % 2)
                p.bob += 0.06f * hop(inBeat)
                p.stretchY += 0.07f * hop(inBeat) - 0.05f * (if (inBeat < 0.1f) 1f else 0f)
                p.sideX = 0.05f * sin(beat * PI.toFloat())
                p.rollDeg = 12f * sin(beat * PI.toFloat())
                p.yawDeg = if (bar % 4 == 3) 360f * ((beat % 4f) / 4f) else 18f * sin(beat * PI.toFloat() / 2f)
                p.leftArmDeg = if (b == 0) 75f else 20f
                p.rightArmDeg = if (b == 0) 20f else 75f
                p.leftLegDeg = if (b == 0) 22f else -8f
                p.rightLegDeg = if (b == 0) -8f else 22f
            }
            "love", "pet" -> {
                val pulse = sin(since * 9f)
                p.stretchY += 0.04f * pulse
                p.widthXZ += 0.03f * pulse
                p.rollDeg = 7f * sin(since * 4f)
                p.leftArmDeg = 30f; p.rightArmDeg = 30f
            }
            "sad" -> {
                p.stretchY -= 0.06f; p.widthXZ += 0.03f
                p.pitchDeg = 12f; p.bob -= 0.01f
                p.leftArmDeg = 0f; p.rightArmDeg = 0f
            }
            "surprise" -> {
                val ph = (since / 0.5f).coerceAtMost(1f)
                p.stretchY += 0.14f * (1f - ph * 0.5f)
                p.bob += 0.06f * (1f - ph * 0.6f)
                p.leftArmDeg = 85f; p.rightArmDeg = 85f
                p.pitchDeg = -10f
            }
            "sleep" -> {
                p.stretchY = 1f - 0.05f + 0.03f * sin(tau * t / 5f)
                p.widthXZ = 1.04f
                p.bob = -0.01f
                p.rollDeg = 10f
                p.leftArmDeg = 0f; p.rightArmDeg = 0f
            }
            "calm", "think" -> {
                val breath = sin(since * tau / 8f - PI.toFloat() / 2f)   // 8-second breathing guide
                p.stretchY = 1f + 0.05f * breath
                p.widthXZ = 1f + 0.025f * breath
                p.leftArmDeg = 12f + 8f * breath; p.rightArmDeg = p.leftArmDeg
            }
            "storm", "lightning", "fire", "hype" -> {
                p.bob += 0.02f * kotlin.math.abs(sin(since * 14f))
                p.rollDeg = 4f * sin(since * 23f)
                p.leftArmDeg = 65f; p.rightArmDeg = 65f
            }
            "clone", "spin" -> {
                val ph = (since / 1.0f).coerceAtMost(1f)
                p.yawDeg = 360f * ph
                p.leftArmDeg = 60f; p.rightArmDeg = 60f
            }
            else -> idleLife(t, p)
        }
    }
}

/** Owns the Filament engine, scene and the loaded .glb. One instance per visible view. */
class MochiSceneRenderer(
    private val context: Context,
    private val assetPath: String,
    /** Turns the model so its face looks at the camera (Tripo exports often face sideways). */
    private val yawOffsetDeg: Float = 0f
) {

    val motion = MochiMotion()
    var speechLevel: () -> Float = { 0f }

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var cameraEntity = 0
    private var sunEntity = 0
    private var fillEntity = 0
    private var indirect: IndirectLight? = null
    private var swapChain: SwapChain? = null
    private var uiHelper: UiHelper? = null
    private var materials: UbershaderProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var asset: FilamentAsset? = null
    private val baseTransform = FloatArray(16)
    private val work = FloatArray(16)
    private val tmp = FloatArray(16)
    private var lastFrameNanos = 0L
    private var running = false
    private var aspect = 1f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            Choreographer.getInstance().postFrameCallback(this)
            renderFrame(frameTimeNanos)
        }
    }

    fun attach(textureView: TextureView) {
        try {
            ensureNativeLibs()
            val e = Engine.create()
            engine = e
            renderer = e.createRenderer().apply {
                clearOptions = clearOptions.apply {
                    clear = true
                    clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
                }
            }
            scene = e.createScene()
            cameraEntity = EntityManager.get().create()
            camera = e.createCamera(cameraEntity).apply { setExposure(16f, 1f / 125f, 100f) }
            view = e.createView().apply {
                scene = this@MochiSceneRenderer.scene
                camera = this@MochiSceneRenderer.camera
                blendMode = View.BlendMode.TRANSLUCENT
            }
            setupLights(e)
            loadAsset(e)

            textureView.isOpaque = false
            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
                isOpaque = false
                renderCallback = object : UiHelper.RendererCallback {
                    override fun onNativeWindowChanged(surface: Surface) {
                        swapChain?.let { engine?.destroySwapChain(it) }
                        swapChain = engine?.createSwapChain(surface, swapChainFlags)
                        start()
                    }

                    override fun onDetachedFromSurface() {
                        stop()
                        swapChain?.let { sc ->
                            engine?.destroySwapChain(sc)
                            engine?.flushAndWait()
                        }
                        swapChain = null
                    }

                    override fun onResized(width: Int, height: Int) {
                        view?.viewport = Viewport(0, 0, width, height)
                        aspect = width.toFloat() / height.coerceAtLeast(1)
                        updateCamera()
                    }
                }
                attachTo(textureView)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "3D view failed to start: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun setupLights(e: Engine) {
        val sc = scene ?: return
        sunEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.97f, 0.92f)
            .intensity(95_000f)
            .direction(-0.35f, -0.8f, -0.55f)
            .castShadows(false)
            .build(e, sunEntity)
        sc.addEntity(sunEntity)

        fillEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.85f, 0.95f, 1.0f)
            .intensity(35_000f)
            .direction(0.6f, -0.2f, -0.4f)
            .castShadows(false)
            .build(e, fillEntity)
        sc.addEntity(fillEntity)

        // Soft studio ambient (first spherical-harmonics band only).
        indirect = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.95f, 0.95f, 1.0f))
            .intensity(28_000f)
            .build(e)
        sc.indirectLight = indirect
    }

    private fun loadAsset(e: Engine) {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.flip() }
        val provider = UbershaderProvider(e).also { materials = it }
        val loader = AssetLoader(e, provider, EntityManager.get()).also { assetLoader = it }
        val loaded = loader.createAsset(buffer) ?: throw IllegalStateException("Could not parse $assetPath")
        resourceLoader = ResourceLoader(e).also { it.loadResources(loaded) }
        loaded.releaseSourceData()
        scene?.addEntities(loaded.entities)
        asset = loaded

        // Normalise: largest dimension = 1 unit, feet on the floor, centred.
        val box = loaded.boundingBox
        val c = box.center
        val h = box.halfExtent
        val s = 1f / (2f * maxOf(h[0], h[1], h[2]).coerceAtLeast(1e-4f))
        Matrix.setIdentityM(baseTransform, 0)
        Matrix.scaleM(baseTransform, 0, s, s, s)
        Matrix.rotateM(baseTransform, 0, yawOffsetDeg, 0f, 1f, 0f)
        Matrix.translateM(baseTransform, 0, -c[0], -(c[1] - h[1]), -c[2])
        findLimbs(loaded, e)
        modelHeight = 2f * h[1] * s
        modelWidth = 2f * maxOf(h[0], h[2]) * s
        updateCamera()
    }

    /** A limb joint we animate: its entity, rest pose and the local axis it swings around. */
    private class Limb(val entity: Int, val rest: FloatArray, val axisX: Float, val axisY: Float, val axisZ: Float, val sign: Float)
    private var leftArm: Limb? = null
    private var rightArm: Limb? = null
    private var leftLeg: Limb? = null
    private var rightLeg: Limb? = null
    private val limbWork = FloatArray(16)
    private val limbRot = FloatArray(16)

    private fun findLimbs(a: FilamentAsset, e: Engine) {
        val tm = e.transformManager
        fun limb(name: String, ax: Float, ay: Float, az: Float, sign: Float): Limb? {
            val ent = a.getFirstEntityByName(name)
            if (ent == 0) return null
            val inst = tm.getInstance(ent)
            if (inst == 0) return null
            return Limb(ent, tm.getTransform(inst, FloatArray(16)), ax, ay, az, sign)
        }
        // Axes verified offline against this rig: forearm Z lifts the nub out to the side.
        leftArm = limb("mixamorig:LeftForeArm", 0f, 0f, 1f, 1f)
        rightArm = limb("mixamorig:RightForeArm", 0f, 0f, 1f, -1f)
        leftLeg = limb("mixamorig:LeftUpLeg", 1f, 0f, 0f, 1f)
        rightLeg = limb("mixamorig:RightUpLeg", 1f, 0f, 0f, 1f)
    }

    private fun applyLimb(limb: Limb?, deg: Float, e: Engine) {
        limb ?: return
        val tm = e.transformManager
        val inst = tm.getInstance(limb.entity)
        if (inst == 0) return
        Matrix.setRotateM(limbRot, 0, deg * limb.sign, limb.axisX, limb.axisY, limb.axisZ)
        Matrix.multiplyMM(limbWork, 0, limb.rest, 0, limbRot, 0)
        tm.setTransform(inst, limbWork)
    }

    private var modelHeight = 1f
    private var modelWidth = 1f

    private fun updateCamera() {
        val cam = camera ?: return
        val fov = 30.0
        // Room for hops above and a little margin around; fit both width and height.
        val needH = max(modelHeight * 1.45f, (modelWidth * 1.15f) / aspect.coerceAtLeast(0.1f))
        val dist = needH / 2f / tan(Math.toRadians(fov / 2)).toFloat()
        cam.setProjection(fov, aspect.toDouble(), 0.05, 50.0, Camera.Fov.VERTICAL)
        val lookY = modelHeight * 0.55f
        cam.lookAt(0.0, lookY.toDouble() + 0.05, dist.toDouble(), 0.0, lookY.toDouble(), 0.0, 0.0, 1.0, 0.0)
    }

    private fun renderFrame(frameTimeNanos: Long) {
        val e = engine ?: return
        val r = renderer ?: return
        val sc = swapChain ?: return
        val a = asset ?: return
        val dt = if (lastFrameNanos == 0L) 0.016f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastFrameNanos = frameTimeNanos

        val pose = motion.step(dt, speechLevel())
        Matrix.setIdentityM(work, 0)
        Matrix.translateM(work, 0, pose.sideX, pose.bob, 0f)
        Matrix.rotateM(work, 0, pose.yawDeg, 0f, 1f, 0f)
        Matrix.rotateM(work, 0, pose.pitchDeg, 1f, 0f, 0f)
        Matrix.rotateM(work, 0, pose.rollDeg, 0f, 0f, 1f)
        Matrix.scaleM(work, 0, pose.widthXZ, pose.stretchY, pose.widthXZ)
        Matrix.multiplyMM(tmp, 0, work, 0, baseTransform, 0)
        val tm = e.transformManager
        tm.setTransform(tm.getInstance(a.root), tmp)
        applyLimb(leftArm, pose.leftArmDeg.coerceIn(-20f, 90f), e)
        applyLimb(rightArm, pose.rightArmDeg.coerceIn(-20f, 90f), e)
        applyLimb(leftLeg, pose.leftLegDeg.coerceIn(-30f, 30f), e)
        applyLimb(rightLeg, pose.rightLegDeg.coerceIn(-30f, 30f), e)
        a.instance.animator.updateBoneMatrices()

        if (r.beginFrame(sc, frameTimeNanos)) {
            r.render(view!!)
            r.endFrame()
        }
    }

    private fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun destroy() {
        stop()
        val e = engine ?: return
        try {
            uiHelper?.detach()
            asset?.let { a ->
                scene?.removeEntities(a.entities)
                assetLoader?.destroyAsset(a)
            }
            resourceLoader?.destroy()
            assetLoader?.destroy()
            materials?.destroyMaterials()
            materials?.destroy()
            indirect?.let { e.destroyIndirectLight(it) }
            listOf(sunEntity, fillEntity).forEach { if (it != 0) { e.destroyEntity(it); EntityManager.get().destroy(it) } }
            swapChain?.let { e.destroySwapChain(it) }
            view?.let { e.destroyView(it) }
            scene?.let { e.destroyScene(it) }
            renderer?.let { e.destroyRenderer(it) }
            if (cameraEntity != 0) {
                e.destroyCameraComponent(cameraEntity)
                EntityManager.get().destroy(cameraEntity)
            }
            e.destroy()
        } catch (t: Throwable) {
            Log.w(TAG, "3D teardown: ${t.javaClass.simpleName}")
        }
        engine = null
        asset = null
        swapChain = null
    }

    companion object {
        private const val TAG = "Mochi3D"
        @Volatile private var nativeReady = false

        private fun ensureNativeLibs() {
            if (nativeReady) return
            synchronized(this) {
                if (!nativeReady) {
                    Filament.init()
                    Gltfio.init()
                    nativeReady = true
                }
            }
        }
    }
}
