package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CadenceRepository(
    context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.getString(R.string.firestore_database_id)
    )
) {

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return firestore.collection("users")
            .document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) snapshot.toUserProfile() else null
            }
            .catch { e -> handleFirestoreError(e, OperationType.GET, path) }
    }

    fun observeQuests(userId: String): Flow<List<QuestItem>> {
        val path = "users/$userId/quests"
        return firestore.collection("users")
            .document(userId)
            .collection("quests")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toQuestItem() } }
            .catch { e -> handleFirestoreError(e, OperationType.LIST, path) }
    }

    fun observeMeals(userId: String): Flow<List<MealCreation>> {
        val path = "users/$userId/meals"
        return firestore.collection("users")
            .document(userId)
            .collection("meals")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toMealCreation() } }
            .catch { e -> handleFirestoreError(e, OperationType.LIST, path) }
    }

    suspend fun ensureUserProfileInitialized(
        userId: String,
        displayName: String,
        email: String
    ): UserProfile {
        val path = "users/$userId"
        val docRef = firestore.collection("users").document(userId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                existing.toUserProfile() ?: UserProfile(userId = userId, displayName = displayName, email = email)
            } else {
                val safeName = displayName.trim().ifEmpty { "Adventurer" }.take(100)
                val safeEmail = email.trim().take(150)
                val payload = mapOf(
                    "userId" to userId,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "companionName" to "Mochi",
                    "level" to 1,
                    "xp" to 0,
                    "goldCoins" to 0,
                    "streakDays" to 0,
                    "moneySavedDollars" to 0,
                    "aiCreditsRemaining" to 15,
                    "aiCreditsMax" to 15,
                    "isProSubscriber" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                seedStarterQuests(userId)
                docRef.get().await().toUserProfile()
                    ?: UserProfile(userId = userId, displayName = safeName, email = safeEmail)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    private suspend fun seedStarterQuests(userId: String) {
        createQuest(
            userId = userId,
            title = "Cook 1 quick 15-min meal from your fridge tonight",
            category = QuestCategory.MEAL,
            xpReward = 150,
            coinsReward = 50
        )
        createQuest(
            userId = userId,
            title = "Take 3 deep breaths with Mochi to clear stress",
            category = QuestCategory.MIND,
            xpReward = 75,
            coinsReward = 30
        )
        createQuest(
            userId = userId,
            title = "Skip 1 delivery order and save $20+ in your wallet",
            category = QuestCategory.WALLET,
            xpReward = 120,
            coinsReward = 45
        )
        createQuest(
            userId = userId,
            title = "Knock out your #1 priority task in a 10-min sprint",
            category = QuestCategory.DAY,
            xpReward = 100,
            coinsReward = 35
        )
    }

    suspend fun createQuest(
        userId: String,
        title: String,
        category: QuestCategory,
        xpReward: Int = 80,
        coinsReward: Int = 30,
        questId: String = "quest_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    ): String {
        val path = "users/$userId/quests/$questId"
        val safeTitle = title.trim().ifEmpty { "Daily Quest" }.take(200)
        val payload = mapOf(
            "userId" to userId,
            "title" to safeTitle,
            "category" to category.name,
            "xpReward" to xpReward.coerceIn(5, 5000),
            "coinsReward" to coinsReward.coerceIn(1, 5000),
            "isCompleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            firestore.collection("users")
                .document(userId)
                .collection("quests")
                .document(questId)
                .set(payload)
                .await()
            return questId
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun toggleQuestCompletion(
        userId: String,
        quest: QuestItem,
        currentProfile: UserProfile?
    ) {
        val path = "users/$userId/quests/${quest.id}"
        val newCompleted = !quest.isCompleted
        try {
            firestore.collection("users")
                .document(userId)
                .collection("quests")
                .document(quest.id)
                .update(
                    mapOf(
                        "isCompleted" to newCompleted,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            if (newCompleted && currentProfile != null) {
                awardXpAndCoins(
                    userId = userId,
                    currentProfile = currentProfile,
                    xpDelta = quest.xpReward,
                    coinsDelta = quest.coinsReward,
                    moneySavedDelta = if (quest.categoryEnum == QuestCategory.WALLET) 20 else 0
                )
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun deleteQuest(userId: String, questId: String) {
        val path = "users/$userId/quests/$questId"
        try {
            firestore.collection("users")
                .document(userId)
                .collection("quests")
                .document(questId)
                .delete()
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    suspend fun createMeal(
        userId: String,
        title: String,
        ingredientsUsed: String,
        prepTimeMinutes: Int,
        calories: Int,
        moneySavedDollars: Int,
        stepsSummary: String,
        isCooked: Boolean = false,
        mealId: String = "meal_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    ): String {
        val path = "users/$userId/meals/$mealId"
        val payload = mapOf(
            "userId" to userId,
            "title" to title.trim().ifEmpty { "15-Min Fridge Skillet" }.take(150),
            "ingredientsUsed" to ingredientsUsed.trim().ifEmpty { "Pantry staples" }.take(500),
            "prepTimeMinutes" to prepTimeMinutes.coerceIn(1, 240),
            "calories" to calories.coerceIn(50, 5000),
            "moneySavedDollars" to moneySavedDollars.coerceIn(1, 500),
            "stepsSummary" to stepsSummary.trim().ifEmpty { "1. Prep ingredients\n2. Cook and enjoy" }.take(2000),
            "isCooked" to isCooked,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            firestore.collection("users")
                .document(userId)
                .collection("meals")
                .document(mealId)
                .set(payload)
                .await()
            return mealId
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun markMealCooked(
        userId: String,
        meal: MealCreation,
        currentProfile: UserProfile?
    ) {
        val path = "users/$userId/meals/${meal.id}"
        try {
            firestore.collection("users")
                .document(userId)
                .collection("meals")
                .document(meal.id)
                .update(
                    mapOf(
                        "isCooked" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            if (!meal.isCooked && currentProfile != null) {
                awardXpAndCoins(
                    userId = userId,
                    currentProfile = currentProfile,
                    xpDelta = 150,
                    coinsDelta = 60,
                    moneySavedDelta = meal.moneySavedDollars
                )
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun deleteMeal(userId: String, mealId: String) {
        val path = "users/$userId/meals/$mealId"
        try {
            firestore.collection("users")
                .document(userId)
                .collection("meals")
                .document(mealId)
                .delete()
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    suspend fun awardXpAndCoins(
        userId: String,
        currentProfile: UserProfile,
        xpDelta: Int,
        coinsDelta: Int,
        moneySavedDelta: Int = 0
    ) {
        val path = "users/$userId"
        val newXp = (currentProfile.xp + xpDelta).coerceIn(0, 1000000)
        val newLevel = ((newXp / 200) + 1).coerceIn(1, 999)
        val newCoins = (currentProfile.goldCoins + coinsDelta).coerceIn(0, 1000000)
        val newMoneySaved = (currentProfile.moneySavedDollars + moneySavedDelta).coerceIn(0, 1000000)

        try {
            firestore.collection("users")
                .document(userId)
                .update(
                    mapOf(
                        "xp" to newXp,
                        "level" to newLevel,
                        "goldCoins" to newCoins,
                        "moneySavedDollars" to newMoneySaved,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun consumeAiCreditOrThrow(
        userId: String,
        currentProfile: UserProfile
    ): Boolean {
        if (currentProfile.isProSubscriber) return true
        if (currentProfile.aiCreditsRemaining <= 0) return false
        val path = "users/$userId"
        val updatedRemaining = (currentProfile.aiCreditsRemaining - 1).coerceAtLeast(0)
        try {
            firestore.collection("users")
                .document(userId)
                .update(
                    mapOf(
                        "aiCreditsRemaining" to updatedRemaining,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            return true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun renameCompanion(
        userId: String,
        newName: String
    ) {
        val safeName = newName.trim().ifEmpty { "Mochi" }.take(50)
        val path = "users/$userId"
        try {
            firestore.collection("users")
                .document(userId)
                .update(
                    mapOf(
                        "companionName" to safeName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        val uid = getString("userId") ?: id
        val displayName = getString("displayName") ?: return null
        return UserProfile(
            userId = uid,
            displayName = displayName,
            email = getString("email") ?: "",
            companionName = getString("companionName") ?: "Mochi",
            level = (getLong("level") ?: 1L).toInt(),
            xp = (getLong("xp") ?: 0L).toInt(),
            goldCoins = (getLong("goldCoins") ?: 0L).toInt(),
            streakDays = (getLong("streakDays") ?: 1L).toInt(),
            moneySavedDollars = (getLong("moneySavedDollars") ?: 0L).toInt(),
            aiCreditsRemaining = (getLong("aiCreditsRemaining") ?: 15L).toInt(),
            aiCreditsMax = (getLong("aiCreditsMax") ?: 15L).toInt(),
            isProSubscriber = getBoolean("isProSubscriber") ?: false,
            createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
            updatedAtMillis = getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
        )
    }

    private fun DocumentSnapshot.toQuestItem(): QuestItem? {
        val title = getString("title") ?: return null
        return QuestItem(
            id = id,
            userId = getString("userId") ?: "",
            title = title,
            category = getString("category") ?: QuestCategory.DAY.name,
            xpReward = (getLong("xpReward") ?: 50L).toInt(),
            coinsReward = (getLong("coinsReward") ?: 20L).toInt(),
            isCompleted = getBoolean("isCompleted") ?: false,
            createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
            updatedAtMillis = getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
        )
    }

    private fun DocumentSnapshot.toMealCreation(): MealCreation? {
        val title = getString("title") ?: return null
        return MealCreation(
            id = id,
            userId = getString("userId") ?: "",
            title = title,
            ingredientsUsed = getString("ingredientsUsed") ?: "",
            prepTimeMinutes = (getLong("prepTimeMinutes") ?: 15L).toInt(),
            calories = (getLong("calories") ?: 450L).toInt(),
            moneySavedDollars = (getLong("moneySavedDollars") ?: 20L).toInt(),
            stepsSummary = getString("stepsSummary") ?: "",
            isCooked = getBoolean("isCooked") ?: false,
            createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
            updatedAtMillis = getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
        )
    }
}
