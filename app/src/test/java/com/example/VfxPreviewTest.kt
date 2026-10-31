package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.character.CharacterRuntimeState
import com.example.ui.character.CompanionVfxLayer
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each companion effect to build/outputs/roborazzi/vfx_*.png for visual review. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h360dp-xxhdpi")
class VfxPreviewTest(private val name: String, private val state: CharacterRuntimeState, private val atMs: Long) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = listOf(
            arrayOf<Any>("idle", CharacterRuntimeState.Idle, 3000L),
            arrayOf<Any>("listening", CharacterRuntimeState.Listening, 1500L),
            arrayOf<Any>("thinking", CharacterRuntimeState.Thinking, 2000L),
            arrayOf<Any>("talking", CharacterRuntimeState.Talking, 1200L),
            arrayOf<Any>("happy", CharacterRuntimeState.Reacting("happy"), 250L),
            arrayOf<Any>("dance", CharacterRuntimeState.Reacting("dance"), 1300L),
            arrayOf<Any>("lightning", CharacterRuntimeState.Reacting("lightning"), 1850L),
            arrayOf<Any>("calm", CharacterRuntimeState.Reacting("calm"), 3000L),
            arrayOf<Any>("fire", CharacterRuntimeState.Reacting("fire"), 1500L),
            arrayOf<Any>("love", CharacterRuntimeState.Reacting("love"), 1500L)
        )
    }

    @get:Rule val compose = createComposeRule()

    @Test fun render() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(360.dp).background(Color(0xFF141519))) {
                CompanionVfxLayer(state = state, accent = Color(0xFF5CC8A1), micLevel = 6f, modifier = Modifier.size(360.dp))
            }
        }
        var t = 0L
        while (t < atMs) { compose.mainClock.advanceTimeBy(16); t += 16 }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/vfx_$name.png")
    }
}
