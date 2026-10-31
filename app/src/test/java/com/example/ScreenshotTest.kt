package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.MochiPersistentSettings
import com.example.ui.character.CharacterController
import com.example.ui.character.CharacterSpec
import com.example.ui.screens.CharacterPickerScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.HelpAndFeedbackScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MochiBottomNavBar
import com.example.ui.screens.MochiMainTab
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.PrivacyAndDataScreen
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.StageScreen
import com.example.ui.screens.ToolsGridScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.screens.WhenYouAreBusyScreen
import com.example.ui.theme.MochiTheme
import com.example.ui.theme.MochiTokens
import com.example.ui.theme.MochiVisualStyle
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every main screen in dark and light to app/build/outputs/roborazzi/.
 * Run:  ./gradlew :app:testDebugUnitTest --tests "com.example.ScreenshotTest"
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenshotTest(private val light: Boolean) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "light={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    @get:Rule
    val compose = createComposeRule()

    private val settings = MochiPersistentSettings(userName = "Elza")
    private val controller = CharacterController(CoroutineScope(Dispatchers.Main))

    private fun shot(name: String, withNav: MochiMainTab? = null, content: @Composable () -> Unit) {
        compose.setContent {
            MochiTheme(
                characterAccent = CharacterSpec.ELENA.accent,
                visualStyle = if (light) MochiVisualStyle.WARM_STUDIO_LUXE else MochiVisualStyle.STUDIO_4K_OPTICAL
            ) {
                Column(Modifier.fillMaxSize().background(MochiTokens.bg)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                    if (withNav != null) MochiBottomNavBar(currentTab = withNav, onSelectTab = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_${if (light) "light" else "dark"}.png")
    }

    @Test fun home() = shot("01_home", MochiMainTab.HOME) {
        StageScreen(
            character = CharacterSpec.ELENA, controller = controller, chatHistory = emptyList(),
            isThinking = false, showConversationSheet = false, onToggleConversationSheet = {},
            onSendMessage = {}, onOpenCharacterPicker = {}, onOpenTools = {}
        )
    }

    @Test fun characters() = shot("02_characters") {
        CharacterPickerScreen(CharacterSpec.ELENA, controller, {}, {}, {})
    }

    @Test fun tools() = shot("03_tools", MochiMainTab.TOOLS) { ToolsGridScreen(onSelectTool = {}, onBack = {}) }

    @Test fun library() = shot("04_library", MochiMainTab.LIBRARY) { LibraryScreen({}, {}, {}, {}) }

    @Test fun search() = shot("05_search") { SearchScreen({}, {}, {}) }

    @Test fun settings() = shot("06_settings") {
        ProfileAndSettingsScreen(
            activeCharacter = CharacterSpec.ELENA, isDarkTheme = !light, onToggleDarkTheme = {},
            onOpenEditProfile = {}, onOpenWallet = {}, onOpenCharacterPicker = {}, onOpenWhenBusy = {},
            onOpenDrivingMode = {}, onOpenPermissions = {}, onOpenPrivacyData = {}, onOpenHelpFeedback = {},
            onOpenComponentGallery = {}, onBack = {}, settings = settings
        )
    }

    @Test fun editProfile() = shot("07_edit_profile") { EditProfileScreen(onBack = {}, settings = settings) }

    @Test fun wallet() = shot("08_wallet") { WalletScreen(onBack = {}, settings = settings) }

    @Test fun busy() = shot("09_when_busy") { WhenYouAreBusyScreen(onBack = {}, settings = settings) }

    @Test fun permissions() = shot("10_permissions") { PermissionsScreen(onBack = {}, settings = settings) }

    @Test fun privacy() = shot("11_privacy") { PrivacyAndDataScreen(onBack = {}, settings = settings) }

    @Test fun help() = shot("12_help") { HelpAndFeedbackScreen(onAskCompanion = {}, onBack = {}) }
}
