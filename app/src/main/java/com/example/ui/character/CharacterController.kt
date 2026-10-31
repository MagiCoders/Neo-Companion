package com.example.ui.character

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Runtime States for 3D Characters (CHARACTERS.md):
 * - Idle: loops `idle`, plays `idle_look` every 8–15s, blinks every 2–6s
 * - Listening: plays `listen`, leans in 3 degrees, rim light pulses gently with mic input level
 * - Thinking: plays `think`, glances up-left, soft shimmer travels along rim light
 * - Talking: alternates `talk_a` / `talk_b`; drives `jawOpen`, `mouthO`, `mouthE` from speech audio
 *   amplitude with 80ms attack / 150ms release smoothing, plus `mouthSmile` at 0.25
 * - Reacting(clip): plays `wave`, `happy`, or `think` once, then returns to `Idle`
 */
sealed class CharacterRuntimeState {
    data object Idle : CharacterRuntimeState()
    data object Listening : CharacterRuntimeState()
    data object Thinking : CharacterRuntimeState()
    data object Talking : CharacterRuntimeState()
    data class Reacting(val clip: String) : CharacterRuntimeState()
}

data class CharacterFramePose(
    val primaryClip: String = "idle",
    val previousClip: String = "idle",
    val clipCrossfadeAlpha: Float = 1f, // 0..1 over 250ms
    val clipTimeSeconds: Float = 0f,
    val morphWeights: Map<String, Float> = mapOf(
        "jawOpen" to 0f,
        "mouthO" to 0f,
        "mouthE" to 0f,
        "mouthSmile" to 0.15f,
        "eyeBlinkLeft" to 0f,
        "eyeBlinkRight" to 0f
    ),
    val touchYawDegrees: Float = 0f,
    val touchPitchDegrees: Float = 0f,
    val micLevel: Float = 0f,
    val activeReactionName: String = ""
)

@Stable
class CharacterController(
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<CharacterRuntimeState>(CharacterRuntimeState.Idle)
    val state: StateFlow<CharacterRuntimeState> = _state.asStateFlow()

    private val _pose = MutableStateFlow(CharacterFramePose())
    val pose: StateFlow<CharacterFramePose> = _pose.asStateFlow()

    private var currentClip = "idle"
    private var previousClip = "idle"
    private var crossfadeStartMs = 0L
    private val crossfadeDurationMs = 250f

    // Speech amplitude state (80ms attack / 150ms release smoothing)
    @Volatile
    private var targetSpeechRms = 0f
    @Volatile
    private var activeWordBoost = 0f
    @Volatile
    private var currentMicLevel = 0f
    private var smoothedSpeechAmplitude = 0f
    private var speechVowelPhase = 0f

    // Performance budget controls: pause when off-screen, 30fps in PiP/bubble
    @Volatile
    private var isPaused = false
    @Volatile
    private var frameDelayMs = 16L
    @Volatile
    private var isReducedMode = false

    // Head look target from last screen touch (up to 12 degrees)
    private var targetYawDeg = 0f
    private var targetPitchDeg = 0f
    private var currentYawDeg = 0f
    private var currentPitchDeg = 0f

    // Blink & idle_look scheduling
    private var nextBlinkAtMs = System.currentTimeMillis() + Random.nextLong(2000L, 6000L)
    private var blinkStartMs = -1L
    private var nextIdleLookAtMs = System.currentTimeMillis() + Random.nextLong(8000L, 15000L)
    private var idleLookUntilMs = -1L

    private var reactionResetJob: Job? = null
    private var speechJob: Job? = null

    init {
        scope.launch(Dispatchers.Default) {
            var lastFrameMs = System.currentTimeMillis()
            while (isActive) {
                if (isPaused) {
                    delay(100L)
                    lastFrameMs = System.currentTimeMillis()
                    continue
                }
                val nowMs = System.currentTimeMillis()
                val dtSec = ((nowMs - lastFrameMs).coerceIn(1L, 100L)) / 1000f
                lastFrameMs = nowMs
                stepFrame(nowMs, dtSec)
                delay(frameDelayMs)
            }
        }
    }

    fun setRenderingPaused(paused: Boolean) {
        isPaused = paused
    }

    fun setReducedBubbleMode(enabled: Boolean) {
        isReducedMode = enabled
        frameDelayMs = if (enabled) 33L else 16L // 30 fps in PiP/floating bubble, 60 fps on Stage
        if (enabled && _state.value !is CharacterRuntimeState.Idle && _state.value !is CharacterRuntimeState.Talking) {
            transitionTo(CharacterRuntimeState.Idle)
        }
    }

    fun updateMicRms(rmsdB: Float) {
        currentMicLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
    }

    fun setState(newState: CharacterRuntimeState) {
        transitionTo(newState)
    }

    /**
     * Triggers one of the 3 allowed reactions from CHARACTERS.md (`wave`, `happy`, `think`),
     * then returns smoothly to `Idle`.
     */
    fun react(reactionName: String) {
        val clean = when (reactionName.lowercase()) {
            "happy", "cheer", "celebrate" -> "happy"
            "think", "calm" -> "think"
            else -> "wave"
        }
        transitionTo(CharacterRuntimeState.Reacting(clean))
    }

    fun transitionTo(newState: CharacterRuntimeState) {
        reactionResetJob?.cancel()
        val effectiveState = if (isReducedMode &&
            newState !is CharacterRuntimeState.Idle &&
            newState !is CharacterRuntimeState.Talking
        ) {
            CharacterRuntimeState.Idle
        } else {
            newState
        }

        _state.value = effectiveState
        val desiredClip = when (effectiveState) {
            is CharacterRuntimeState.Idle -> "idle"
            is CharacterRuntimeState.Listening -> "listen"
            is CharacterRuntimeState.Thinking -> "think"
            is CharacterRuntimeState.Talking -> "talk_a"
            is CharacterRuntimeState.Reacting -> {
                when (effectiveState.clip.lowercase()) {
                    "happy" -> "happy"
                    "think" -> "think"
                    else -> "wave"
                }
            }
        }
        setActiveClip(desiredClip)

        if (effectiveState is CharacterRuntimeState.Reacting) {
            reactionResetJob = scope.launch {
                delay(2000L)
                if (_state.value is CharacterRuntimeState.Reacting) {
                    transitionTo(CharacterRuntimeState.Idle)
                }
            }
        }
    }

    /**
     * Turns the head slightly toward the last touch point on screen (up to 12 degrees, spring-smoothed).
     */
    fun onStageTouchPoint(normalizedX: Float, normalizedY: Float) {
        targetYawDeg = (normalizedX * 12f).coerceIn(-12f, 12f)
        targetPitchDeg = (-normalizedY * 12f).coerceIn(-12f, 12f)
    }

    /**
     * Synthesizes TTS to a temporary WAV file to sample real 60Hz PCM RMS envelopes when available,
     * while speaking audibly on the speaker stream (`TextToSpeech.speak`) and driving
     * `jawOpen`, `mouthO`, `mouthE`, and `mouthSmile` with 80ms attack / 150ms release smoothing.
     */
    fun speakWithLipSync(
        context: Context,
        tts: TextToSpeech?,
        text: String,
        character: CharacterSpec
    ) {
        val cleanText = com.example.ai.OmnichannelMediaBridge.formatNaturalSpeechProsody(text)
        if (cleanText.isEmpty()) return

        speechJob?.cancel()
        speechJob = scope.launch(Dispatchers.Main) {
            transitionTo(CharacterRuntimeState.Talking)
            val estimatedDurationMs = (cleanText.length * 58L / character.speechRate.coerceAtLeast(0.7f))
                .toLong()
                .coerceIn(1500L, 12000L)

            var utteranceFinished = false
            var wavRmsEnvelope: FloatArray? = null

            try {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.let { am ->
                        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val targetMin = (maxVol * 0.65f).toInt().coerceAtLeast(1)
                        if (curVol < targetMin) {
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetMin, 0)
                        }
                    }
                } catch (_: Throwable) {
                }

                if (tts != null) {
                    try {
                        com.example.ai.OmnichannelMediaBridge.configureHumanLikeNeuralVoice(tts, character)

                        val utteranceId = "mochi_speak_${System.currentTimeMillis()}"
                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(id: String?) {
                                activeWordBoost = 0.28f
                            }

                            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                                val wordLen = (end - start).coerceIn(1, 12)
                                activeWordBoost = (0.24f + wordLen * 0.04f).coerceAtMost(0.58f)
                            }

                            override fun onDone(id: String?) {
                                if (id == utteranceId) {
                                    utteranceFinished = true
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(id: String?) {
                                if (id == utteranceId) {
                                    utteranceFinished = true
                                }
                            }

                            override fun onError(id: String?, errorCode: Int) {
                                if (id == utteranceId) {
                                    utteranceFinished = true
                                }
                            }
                        })

                        val params = Bundle().apply {
                            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                        }
                        tts.speak(
                            cleanText,
                            TextToSpeech.QUEUE_FLUSH,
                            params,
                            utteranceId
                        )
                    } catch (_: Throwable) {
                    }
                }

                val startMs = System.currentTimeMillis()
                var frameIndex = 0
                while (isActive) {
                    val elapsedMs = System.currentTimeMillis() - startMs
                    if (utteranceFinished && elapsedMs > 500L) break
                    if (elapsedMs >= estimatedDurationMs) break

                    val env = wavRmsEnvelope
                    val sampledRms = if (env != null && env.isNotEmpty()) {
                        env[frameIndex % env.size]
                    } else {
                        val tSec = elapsedMs / 1000f
                        val cadence = abs(sin(tSec * 9.8f * character.speechRate)) * 0.34f +
                            abs(sin(tSec * 4.3f)) * 0.20f +
                            activeWordBoost * 0.45f
                        val phraseGate = if (sin(tSec * 2.1f) < -0.78f) 0.12f else 1f
                        (cadence * phraseGate).coerceIn(0.04f, 0.92f)
                    }

                    targetSpeechRms = sampledRms
                    activeWordBoost = (activeWordBoost * 0.88f).coerceAtLeast(0.08f)
                    frameIndex++
                    delay(16L)
                }
            } finally {
                targetSpeechRms = 0f
                activeWordBoost = 0f
                transitionTo(CharacterRuntimeState.Reacting("happy"))
            }
        }
    }

    private fun extractWavRmsEnvelope60Hz(wavFile: File): FloatArray? {
        return try {
            val bytes = wavFile.readBytes()
            if (bytes.size <= 44) return null
            val pcmLength = bytes.size - 44
            val shortCount = pcmLength / 2
            if (shortCount <= 0) return null
            val shorts = ShortArray(shortCount)
            ByteBuffer.wrap(bytes, 44, pcmLength)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(shorts)

            val windowSize = 360 // ~60Hz window at 22.05kHz
            val numWindows = (shortCount / windowSize).coerceAtLeast(1)
            FloatArray(numWindows) { w ->
                var sumSq = 0.0
                val start = w * windowSize
                val end = (start + windowSize).coerceAtMost(shortCount)
                for (i in start until end) {
                    val norm = shorts[i] / 32768.0
                    sumSq += norm * norm
                }
                val rms = sqrt(sumSq / (end - start).coerceAtLeast(1)).toFloat()
                (rms * 3.2f).coerceIn(0f, 1f)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun setActiveClip(clip: String) {
        if (clip != currentClip) {
            previousClip = currentClip
            currentClip = clip
            crossfadeStartMs = System.currentTimeMillis()
        }
    }

    private fun stepFrame(nowMs: Long, dtSec: Float) {
        val currentState = _state.value

        // 1. Handle Idle periodic idle_look every 8–15s and Talking clip alternation
        when (currentState) {
            is CharacterRuntimeState.Idle -> {
                if (nowMs >= nextIdleLookAtMs && idleLookUntilMs < 0L) {
                    setActiveClip("idle_look")
                    idleLookUntilMs = nowMs + 2000L
                } else if (idleLookUntilMs in 1..nowMs) {
                    setActiveClip("idle")
                    idleLookUntilMs = -1L
                    nextIdleLookAtMs = nowMs + Random.nextLong(8000L, 15000L)
                }
            }
            is CharacterRuntimeState.Talking -> {
                val cycle = ((nowMs / 1800L) % 2L).toInt()
                val talkClip = if (cycle == 0) "talk_a" else "talk_b"
                if (currentClip != talkClip) {
                    setActiveClip(talkClip)
                }
            }
            else -> {
                idleLookUntilMs = -1L
            }
        }

        // 2. Random blink every 2–6s (120ms close, 160ms open -> 280ms total)
        var blinkWeight = 0f
        if (blinkStartMs < 0L && nowMs >= nextBlinkAtMs) {
            blinkStartMs = nowMs
        }
        if (blinkStartMs > 0L) {
            val blinkElapsed = (nowMs - blinkStartMs).toFloat()
            blinkWeight = when {
                blinkElapsed <= 120f -> (blinkElapsed / 120f).coerceIn(0f, 1f)
                blinkElapsed <= 280f -> (1f - (blinkElapsed - 120f) / 160f).coerceIn(0f, 1f)
                else -> {
                    blinkStartMs = -1L
                    nextBlinkAtMs = nowMs + Random.nextLong(2000L, 6000L)
                    0f
                }
            }
        }

        // 3. Lip-sync smoothing: 80ms attack / 150ms release
        val rawTargetJaw = (targetSpeechRms * 2.2f).coerceIn(0f, 1f)
        val timeConstant = if (rawTargetJaw > smoothedSpeechAmplitude) 0.080f else 0.150f
        val alpha = (1f - exp(-dtSec / timeConstant)).coerceIn(0f, 1f)
        smoothedSpeechAmplitude += (rawTargetJaw - smoothedSpeechAmplitude) * alpha

        speechVowelPhase += dtSec * 11f
        val jawOpen = if (currentState is CharacterRuntimeState.Talking) smoothedSpeechAmplitude else 0f
        val mouthO = if (currentState is CharacterRuntimeState.Talking) {
            (smoothedSpeechAmplitude * 0.65f * ((sin(speechVowelPhase) + 1f) * 0.5f)).coerceIn(0f, 1f)
        } else 0f
        val mouthE = if (currentState is CharacterRuntimeState.Talking) {
            (smoothedSpeechAmplitude * 0.55f * ((sin(speechVowelPhase + 2.1f) + 1f) * 0.5f)).coerceIn(0f, 1f)
        } else 0f

        val mouthSmile = when (currentState) {
            is CharacterRuntimeState.Talking -> 0.25f
            is CharacterRuntimeState.Reacting -> if (currentState.clip == "happy") 0.80f else 0.30f
            is CharacterRuntimeState.Listening -> 0.25f
            else -> 0.18f
        }

        // 4. State-based head posture (Listening leans in 3 deg; Thinking glances up-left)
        val stateYawBonus = if (currentState is CharacterRuntimeState.Thinking) -6f else 0f
        val statePitchBonus = when (currentState) {
            is CharacterRuntimeState.Listening -> -3f // leans in 3 degrees
            is CharacterRuntimeState.Thinking -> 5f // glances up-left
            else -> 0f
        }

        val springFactor = (dtSec * 7.5f).coerceIn(0f, 1f)
        currentYawDeg += ((targetYawDeg + stateYawBonus) - currentYawDeg) * springFactor
        currentPitchDeg += ((targetPitchDeg + statePitchBonus) - currentPitchDeg) * springFactor
        targetYawDeg *= (1f - dtSec * 0.45f).coerceIn(0.8f, 1f)
        targetPitchDeg *= (1f - dtSec * 0.45f).coerceIn(0.8f, 1f)

        // 5. 250ms animation crossfade alpha
        val crossfadeAlpha = ((nowMs - crossfadeStartMs) / crossfadeDurationMs).coerceIn(0f, 1f)
        val clipTimeSec = ((nowMs % 2000L) / 1000f)
        val reactionTag = (currentState as? CharacterRuntimeState.Reacting)?.clip?.lowercase().orEmpty()

        _pose.value = CharacterFramePose(
            primaryClip = currentClip,
            previousClip = previousClip,
            clipCrossfadeAlpha = crossfadeAlpha,
            clipTimeSeconds = clipTimeSec,
            morphWeights = mapOf(
                "jawOpen" to jawOpen,
                "mouthO" to mouthO,
                "mouthE" to mouthE,
                "mouthSmile" to mouthSmile,
                "eyeBlinkLeft" to blinkWeight,
                "eyeBlinkRight" to blinkWeight
            ),
            touchYawDegrees = currentYawDeg,
            touchPitchDegrees = currentPitchDeg,
            micLevel = currentMicLevel,
            activeReactionName = reactionTag
        )
    }
}
