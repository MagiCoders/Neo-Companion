# Security

How MochiLife protects users, and what still has to be switched on in the Firebase / Google Cloud consoles before release.

## Built into the app

| Area | Protection |
| --- | --- |
| Network (MITM, downgrade) | HTTPS only (`usesCleartextTraffic=false`, `network_security_config.xml`). Release builds trust only the phone's system certificate authorities, so certificates a user or attacker installs are ignored. |
| Data on the phone | Name, nickname and "about me" are stored with AES-256-GCM using a key that lives inside the Android Keystore (`security/SecureStore.kt`). No personal data in plain SharedPreferences. |
| Backups / device transfer | Disabled (`allowBackup=false`, `data_extraction_rules.xml`, `backup_rules.xml`), so app data can't be pulled through `adb backup` or cloud backup. |
| Sign-out | Wipes encrypted personal data, chat history and the user's local Library files on that device. |
| Shared files | The FileProvider exposes only `cache/shared/` (generated attachments) and `files/library/` (the user's own files). |
| Reverse engineering | R8 shrinking + obfuscation in release; debug/info logs are stripped; enum names kept only where Firestore needs them. |
| Logging | Firestore errors log the error code and a masked path only — no emails, names or user IDs. |
| AI prompt injection | Everything users or other people wrote (messages, history, names) is passed to Gemini as quoted data, bidi-override characters are removed, and the system prompt forbids acting on instructions inside that data. Actions are re-checked in code (real contact, allowed screen, https-only links) and always go through the 3-second countdown. |
| Fake actions | No hard-coded contacts or numbers. Calls/messages resolve real contacts; ambiguous names ask the user; failures are reported honestly. |
| Firestore rules | Users can read/write only their own documents. Clients can't grant themselves Pro, raise AI credits, change their email, or jump XP/coins/level by more than a small step per write. Covered by emulator tests (`CadenceRepositoryRuleTest`). |
| App attestation | Firebase App Check: reCAPTCHA provider in release, debug provider only in debug builds. |
| Overlay / services | Service and FileProvider are not exported; PendingIntents are immutable; overlay mic only when started from the foreground. |

## To do in the consoles before publishing

1. **App Check → Enforce** for Firestore and Firebase AI Logic, so requests without a valid attestation are rejected. Consider switching the release provider to **Play Integrity** once the app is on Google Play.
2. **Restrict the Android API key** in Google Cloud Console → Credentials: Android apps only, package `com.aistudio.cadence.mxkprz` + your release SHA-1/SHA-256.
3. **Add your release (Play App Signing) SHA-1 and SHA-256** in Firebase → Project settings, or Google sign-in will fail for store builds.
4. **Pro and AI credit refills must be server-side.** The app can no longer grant them. Add a Cloud Function that verifies Google Play purchases and refills `aiCreditsRemaining` daily with the Admin SDK.
5. Deploy the rules: `firebase deploy --only firestore:rules`.

## Testing

```
# unit + screenshot tests
./gradlew :app:testDebugUnitTest
# Firestore rules against the emulator
firebase emulators:exec --only firestore,auth --project demo-test "./gradlew :app:testDebugUnitTest"
```

Report vulnerabilities privately to the repository owner, not in public issues.
