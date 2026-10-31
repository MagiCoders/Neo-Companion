package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.OmnichannelMediaBridge
import com.example.data.CompanionActionType
import com.example.data.MochiHashRoutes
import com.example.ui.CadenceViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MochiLife", appName)
    }

    @Test
    fun `voice command parser handles WhatsApp calls contacts and voice notes`() {
        val callMatch = OmnichannelMediaBridge.parseInstantVoiceCommand("Call Neo on WhatsApp", "Nurse Elena")
        assertNotNull(callMatch)
        assertEquals(CompanionActionType.WHATSAPP_CALL, callMatch?.actionType)
        assertEquals("Neo", callMatch?.actionRecipient)

        val contactsMatch = OmnichannelMediaBridge.parseInstantVoiceCommand("Open WhatsApp contact list", "Nurse Elena")
        assertNotNull(contactsMatch)
        assertEquals(CompanionActionType.WHATSAPP_CONTACTS, contactsMatch?.actionType)

        val readChatMatch = OmnichannelMediaBridge.parseInstantVoiceCommand(
            "Open chat on WhatsApp for Sarah and read the voice message",
            "Nurse Elena"
        )
        assertNotNull(readChatMatch)
        assertEquals(CompanionActionType.WHATSAPP_READ_CHAT, readChatMatch?.actionType)
        assertEquals("Sarah", readChatMatch?.actionRecipient)
    }

    @Test
    fun `voice command parser handles YouTube social media browser and media`() {
        val whatsPlaying = OmnichannelMediaBridge.parseInstantVoiceCommand("Open YouTube and tell me what is playing", "Nurse Elena")
        assertNotNull(whatsPlaying)
        assertEquals(CompanionActionType.WHATS_PLAYING, whatsPlaying?.actionType)

        val ytSearch = OmnichannelMediaBridge.parseInstantVoiceCommand("Search YouTube for lofi chill beats", "Nurse Elena")
        assertNotNull(ytSearch)
        assertEquals(CompanionActionType.YOUTUBE, ytSearch?.actionType)

        val socialMatch = OmnichannelMediaBridge.parseInstantVoiceCommand("Search social media for latest AI news", "Nurse Elena")
        assertNotNull(socialMatch)
        assertEquals(CompanionActionType.SOCIAL_SEARCH, socialMatch?.actionType)

        val browserMatch = OmnichannelMediaBridge.parseInstantVoiceCommand("Search in browser for Mediterranean pasta recipes", "Nurse Elena")
        assertNotNull(browserMatch)
        assertEquals(CompanionActionType.BROWSER_SEARCH, browserMatch?.actionType)

        val mediaMatch = OmnichannelMediaBridge.parseInstantVoiceCommand("Play relaxing music and sounds", "Nurse Elena")
        assertNotNull(mediaMatch)
        assertEquals(CompanionActionType.OPEN_MEDIA, mediaMatch?.actionType)
    }

    @Test
    fun `hash router and settings persist across screen navigation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = CadenceViewModel.provideFactory(context).create(CadenceViewModel::class.java)

        assertEquals(MochiHashRoutes.STAGE, vm.currentHashRoute.value)

        // Toggle wakeWordEnabled off in Profile, navigate to Pantry and Quests, then back to Profile
        vm.navigateHash(MochiHashRoutes.PROFILE)
        assertEquals(MochiHashRoutes.PROFILE, vm.currentHashRoute.value)

        vm.updateSettings { it.copy(wakeWordEnabled = false, userName = "Alex") }
        assertFalse(vm.settings.value.wakeWordEnabled)
        assertEquals("Alex", vm.settings.value.userName)

        vm.navigateHash(MochiHashRoutes.PANTRY)
        assertEquals(MochiHashRoutes.PANTRY, vm.currentHashRoute.value)
        assertFalse(vm.settings.value.wakeWordEnabled)

        vm.navigateHash(MochiHashRoutes.QUESTS)
        assertEquals(MochiHashRoutes.QUESTS, vm.currentHashRoute.value)
        assertFalse(vm.settings.value.wakeWordEnabled)

        // Re-instantiate ViewModel to verify SharedPreferences durability
        val reloadedVm = CadenceViewModel.provideFactory(context).create(CadenceViewModel::class.java)
        assertFalse(reloadedVm.settings.value.wakeWordEnabled)
        assertEquals("Alex", reloadedVm.settings.value.userName)

        // Restore wakeWordEnabled to true and verify
        reloadedVm.updateSettings { it.copy(wakeWordEnabled = true) }
        assertTrue(reloadedVm.settings.value.wakeWordEnabled)
    }

    @Test
    fun `voice command service schedules 3-second undo cancel pill and supports aborting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vm = CadenceViewModel.provideFactory(context).create(CadenceViewModel::class.java)

        val cleanedWakeWord = vm.voiceCommandService.extractWakeWordOrCommand(
            rawSpoken = "Hi Mochi call Neo on WhatsApp",
            companion = vm.selectedCharacter.value
        )
        assertEquals("call Neo on WhatsApp", cleanedWakeWord)

        val turn = OmnichannelMediaBridge.parseInstantVoiceCommand(
            userMessage = cleanedWakeWord,
            companionName = vm.selectedCharacter.value.displayName
        )
        assertNotNull(turn)

        var executed = false
        vm.voiceCommandService.scheduleActionWithUndoWindow(
            turn = turn!!,
            durationSeconds = 3
        ) {
            executed = true
        }

        // Verify the 3-second Undo/Cancel pending action is immediately active
        val pending = vm.pendingVoiceAction.value
        assertNotNull(pending)
        assertEquals(3, pending?.remainingSeconds)
        assertEquals("WhatsApp Call · Neo", pending?.title)

        // Cancel via the Undo/Cancel pill and verify action is aborted
        vm.cancelPendingVoiceAction()
        assertEquals(null, vm.pendingVoiceAction.value)
        assertFalse(executed)
    }
}

