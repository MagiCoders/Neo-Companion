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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by controller.state.collectAsStateWithLifecycle()
    val renderer = remember(assetPath) { MochiSceneRenderer(context.applicationContext, assetPath) }

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

/** Turns companion states into smooth body motion. Pure math, unit-testable. */
class MochiMotion {
    data class Pose(
        var bob: Float = 0f,
        var yawDeg: Float = 0f,
        var pitchDeg: Float = 0f,
        var rollDeg: Float = 0f,
        var stretchY: Float = 1f,
        var widthXZ: Float = 1f
    )

    private var state: CharacterRuntimeState = CharacterRuntimeState.Idle
    private var stateStartSec = 0f
    private var nowSec = 0f
    private val current = Pose()

    fun setState(newState: CharacterRuntimeState) {
        if (newState != state) {
            state = newState
            stateStartSec = nowSec
        }
    }

    /** Advances time and returns the smoothed pose for this frame. */
    fun step(dtSec: Float, speechLevel: Float): Pose {
        nowSec += dtSec
        val t = nowSec
        val since = nowSec - stateStartSec
        val target = Pose()

        // Always alive: slow breathing and a gentle float.
        val breathe = sin(2f * PI.toFloat() * t / 3.4f)
        target.bob = 0.018f * breathe
        target.stretchY = 1f + 0.018f * breathe
        target.yawDeg = 5f * sin(t * 0.45f)

        when (val s = state) {
            CharacterRuntimeState.Idle -> Unit
            CharacterRuntimeState.Listening -> {
                target.pitchDeg = 9f                      // leans in to hear you
                target.bob += 0.03f
                target.stretchY += 0.03f
                target.rollDeg = 4f * sin(t * 2.2f)
            }
            CharacterRuntimeState.Thinking -> {
                target.rollDeg = 10f * sin(t * 0.9f)     // ponders side to side
                target.yawDeg = 18f * sin(t * 0.6f)
                target.bob += 0.02f * sin(t * 1.8f)
            }
            CharacterRuntimeState.Talking -> {
                val lvl = speechLevel.coerceIn(0f, 1f)
                target.stretchY += 0.07f * lvl          // squashes with each syllable
                target.widthXZ -= 0.035f * lvl
                target.pitchDeg = 4f * lvl
                target.bob += 0.015f * lvl
            }
            is CharacterRuntimeState.Reacting -> applyReaction(s.clip, since, target)
        }

        // Critically-damped smoothing so state changes never snap.
        val k = 1f - exp(-dtSec * 10f)
        current.bob += (target.bob - current.bob) * k
        current.yawDeg += (target.yawDeg - current.yawDeg) * k
        current.pitchDeg += (target.pitchDeg - current.pitchDeg) * k
        current.rollDeg += (target.rollDeg - current.rollDeg) * k
        current.stretchY += (target.stretchY - current.stretchY) * k * 1.6f
        current.widthXZ += (target.widthXZ - current.widthXZ) * k * 1.6f
        return current
    }

    private fun applyReaction(clip: String, since: Float, p: Pose) {
        when (clip.lowercase()) {
            "happy", "celebrate", "cheer" -> {
                // Two joyful hops with squash on landing.
                val cycle = 0.55f
                val phase = (since % cycle) / cycle
                if (since < cycle * 2) {
                    val h = 4f * phase * (1f - phase)
                    p.bob += 0.16f * h
                    p.stretchY += 0.10f * h - 0.12f * squashPulse(phase)
                    p.widthXZ += 0.08f * squashPulse(phase)
                }
            }
            "wave" -> {
                p.rollDeg = 14f * sin(since * 9f) * max(0f, 1f - since / 1.6f)
                p.bob += 0.03f
            }
            "dance" -> {
                p.rollDeg = 12f * sin(since * 6.3f)
                p.yawDeg = 20f * sin(since * 3.1f)
                p.bob += 0.05f * abs(sin(since * 6.3f))
                p.stretchY += 0.05f * abs(sin(since * 6.3f))
            }
            "clone", "spin" -> p.yawDeg = (since / 1.0f).coerceAtMost(1f) * 360f
            "calm", "think" -> {
                p.stretchY += 0.04f * sin(since * 1.2f)
                p.bob -= 0.01f
            }
            else -> Unit
        }
    }

    private fun squashPulse(phase: Float): Float = if (phase > 0.85f || phase < 0.1f) 1f else 0f
}

/** Owns the Filament engine, scene and the loaded .glb. One instance per visible view. */
class MochiSceneRenderer(private val context: Context, private val assetPath: String) {

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
        Matrix.translateM(baseTransform, 0, -c[0], -(c[1] - h[1]), -c[2])
        modelHeight = 2f * h[1] * s
        modelWidth = 2f * maxOf(h[0], h[2]) * s
        updateCamera()
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
        Matrix.translateM(work, 0, 0f, pose.bob, 0f)
        Matrix.rotateM(work, 0, pose.yawDeg, 0f, 1f, 0f)
        Matrix.rotateM(work, 0, pose.pitchDeg, 1f, 0f, 0f)
        Matrix.rotateM(work, 0, pose.rollDeg, 0f, 0f, 1f)
        Matrix.scaleM(work, 0, pose.widthXZ, pose.stretchY, pose.widthXZ)
        Matrix.multiplyMM(tmp, 0, work, 0, baseTransform, 0)
        val tm = e.transformManager
        tm.setTransform(tm.getInstance(a.root), tmp)
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
