const { test, describe, before, after, beforeEach } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-mochilife-project',
    firestore: {
      rules: fs.readFileSync('firestore.rules', 'utf8'),
      host: '127.0.0.1',
      port: 8085,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

describe('MochiLife Firestore Security Rules', () => {
  const now = () => new Date();

  const validProfile = (userId) => ({
    userId,
    displayName: 'Alex Rivera',
    email: 'alex@example.com',
    companionName: 'Mochi',
    level: 1,
    xp: 50,
    goldCoins: 100,
    streakDays: 1,
    moneySavedDollars: 25,
    aiCreditsRemaining: 15,
    aiCreditsMax: 15,
    isProSubscriber: false,
    createdAt: now(),
    updatedAt: now(),
  });

  const validQuest = (userId) => ({
    userId,
    title: 'Cook a 15-min dinner from fridge',
    category: 'MEAL',
    xpReward: 150,
    coinsReward: 50,
    isCompleted: false,
    createdAt: now(),
    updatedAt: now(),
  });

  const validMeal = (userId) => ({
    userId,
    title: 'Crispy Garlic Egg Fried Rice',
    ingredientsUsed: 'Eggs, Rice, Garlic, Green Onion',
    prepTimeMinutes: 12,
    calories: 480,
    moneySavedDollars: 22,
    stepsSummary: '1. Scramble eggs\n2. Toss rice with garlic\n3. Combine and garnish',
    isCooked: true,
    createdAt: now(),
    updatedAt: now(),
  });

  test('denies unauthenticated access to profiles, quests, and meals', async () => {
    const unauthedDb = testEnv.unauthenticatedContext().firestore();
    await assertFails(unauthedDb.doc('users/user_1').get());
    await assertFails(unauthedDb.doc('users/user_1').set(validProfile('user_1')));
    await assertFails(unauthedDb.doc('users/user_1/quests/quest_1').get());
    await assertFails(unauthedDb.doc('users/user_1/meals/meal_1').get());
  });

  test('allows owner to create, read, and update valid UserProfile', async () => {
    const aliceDb = testEnv.authenticatedContext('alice_123').firestore();
    const profileRef = aliceDb.doc('users/alice_123');

    await assertSucceeds(profileRef.set(validProfile('alice_123')));
    const snap = await assertSucceeds(profileRef.get());
    assert.strictEqual(snap.data().companionName, 'Mochi');

    await assertSucceeds(
      profileRef.update({
        xp: 200,
        goldCoins: 150,
        updatedAt: now(),
      })
    );
  });

  test('denies cross-user profile read or write', async () => {
    const aliceDb = testEnv.authenticatedContext('alice_123').firestore();
    const bobDb = testEnv.authenticatedContext('bob_456').firestore();

    await assertSucceeds(aliceDb.doc('users/alice_123').set(validProfile('alice_123')));
    await assertFails(bobDb.doc('users/alice_123').get());
    await assertFails(
      bobDb.doc('users/alice_123').update({
        goldCoins: 9999,
        updatedAt: now(),
      })
    );
  });

  test('rejects invalid UserProfile payload (extra keys or negative values)', async () => {
    const aliceDb = testEnv.authenticatedContext('alice_123').firestore();
    await assertFails(
      aliceDb.doc('users/alice_123').set({
        ...validProfile('alice_123'),
        isAdmin: true,
      })
    );
    await assertFails(
      aliceDb.doc('users/alice_123').set({
        ...validProfile('alice_123'),
        aiCreditsRemaining: -5,
      })
    );
  });

  test('allows owner CRUD on quests and meals and blocks immutable field tampering', async () => {
    const aliceDb = testEnv.authenticatedContext('alice_123').firestore();
    const questRef = aliceDb.doc('users/alice_123/quests/quest_1');
    const mealRef = aliceDb.doc('users/alice_123/meals/meal_1');

    await assertSucceeds(questRef.set(validQuest('alice_123')));
    await assertSucceeds(
      questRef.update({
        isCompleted: true,
        updatedAt: now(),
      })
    );
    await assertFails(
      questRef.update({
        createdAt: new Date(1000),
        updatedAt: now(),
      })
    );

    await assertSucceeds(mealRef.set(validMeal('alice_123')));
    await assertSucceeds(mealRef.delete());
  });
});
