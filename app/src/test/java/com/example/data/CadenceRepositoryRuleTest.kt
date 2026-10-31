package com.example.data

import android.os.Looper
import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class CadenceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private val testScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun <T> runRepoBlocking(block: suspend () -> T): T {
        val deferred = testScope.async { block() }
        val deadline = System.currentTimeMillis() + 10_000L
        while (!deferred.isCompleted) {
            if (System.currentTimeMillis() > deadline) {
                throw RuntimeException("Coroutine timed out")
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        return deferred.getCompleted()
    }

    override fun tearDownEmulator() {
        testScope.cancel()
        super.tearDownEmulator()
    }

    @Test
    fun `ensureUserProfileInitialized creates valid profile, quests, and meals and emits via snapshots`() {
        val uid = signInTestUser("mochi_owner@example.com")
        val repo = CadenceRepository(context, firestore)

        val profile = runRepoBlocking {
            repo.ensureUserProfileInitialized(
                userId = uid,
                displayName = "Samira",
                email = "mochi_owner@example.com"
            )
        }
        assertEquals(uid, profile.userId)
        assertEquals("Samira", profile.displayName)
        assertEquals("Mochi", profile.companionName)
        assertEquals(15, profile.aiCreditsRemaining)

        val observedProfile = runRepoBlocking {
            repo.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(observedProfile)
        assertEquals("Samira", observedProfile?.displayName)

        val observedQuests = runRepoBlocking {
            repo.observeQuests(uid).first { it.isNotEmpty() }
        }
        assertTrue(observedQuests.size >= 4)

        assertEquals(0, profile.xp)
        assertEquals(0, profile.goldCoins)
    }

    @Test
    fun `a user cannot make themselves Pro or refill their own AI credits`() {
        val uid = signInTestUser("cheater@example.com")
        val repo = CadenceRepository(context, firestore)
        runRepoBlocking { repo.ensureUserProfileInitialized(uid, "Cheater", "cheater@example.com") }
        val doc = firestore.collection("users").document(uid)

        val makePro = runCatching { awaitTask(doc.update("isProSubscriber", true)) }
        assertTrue("client must not be able to grant Pro", makePro.isFailure)

        val refill = runCatching { awaitTask(doc.update("aiCreditsRemaining", 9999)) }
        assertTrue("client must not be able to raise credits", refill.isFailure)

        val spend = runCatching { awaitTask(doc.update(mapOf("aiCreditsRemaining" to 14))) }
        assertTrue("spending one credit is allowed", spend.isSuccess)
    }

    @Test
    fun `toggleQuestCompletion and consumeAiCreditOrThrow obey security rules`() {
        val uid = signInTestUser("quest_player@example.com")
        val repo = CadenceRepository(context, firestore)

        val profile = runRepoBlocking {
            repo.ensureUserProfileInitialized(uid, "Player One", "quest_player@example.com")
        }
        val quests = runRepoBlocking {
            repo.observeQuests(uid).first { it.isNotEmpty() }
        }
        val firstQuest = quests.first()

        runRepoBlocking {
            repo.toggleQuestCompletion(uid, firstQuest, profile)
        }

        val updatedProfile = runRepoBlocking {
            repo.observeUserProfile(uid).first { it != null && it.xp > profile.xp }
        }
        assertNotNull(updatedProfile)
        assertTrue(updatedProfile!!.xp >= profile.xp + firstQuest.xpReward)

        val consumed = runRepoBlocking {
            repo.consumeAiCreditOrThrow(uid, updatedProfile)
        }
        assertTrue(consumed)
    }

    @Test
    fun `cross-user write and unauthenticated write are rejected with PERMISSION_DENIED`() {
        val aliceUid = signInTestUser("alice@example.com")
        val repo = CadenceRepository(context, firestore)
        runRepoBlocking {
            repo.ensureUserProfileInitialized(aliceUid, "Alice", "alice@example.com")
        }

        // Sign in as Bob and attempt to write into Alice's quests
        signInTestUser("bob@example.com")
        try {
            runRepoBlocking {
                repo.createQuest(
                    userId = aliceUid,
                    title = "Unauthorized quest",
                    category = QuestCategory.DAY
                )
            }
            fail("Expected cross-user write to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val cause = e.cause
            assertTrue(
                "Expected FirebaseFirestoreException PERMISSION_DENIED but got $e",
                cause is FirebaseFirestoreException &&
                    cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            )
        }

        // Sign out completely and attempt to write
        auth.signOut()
        shadowOf(Looper.getMainLooper()).idle()
        try {
            runRepoBlocking {
                repo.createQuest(
                    userId = aliceUid,
                    title = "Unauthenticated quest",
                    category = QuestCategory.DAY
                )
            }
            fail("Expected unauthenticated write to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val cause = e.cause
            assertTrue(
                "Expected FirebaseFirestoreException PERMISSION_DENIED but got $e",
                cause is FirebaseFirestoreException &&
                    cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            )
        }
    }
}
