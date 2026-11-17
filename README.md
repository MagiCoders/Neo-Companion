# MochiLife

A voice-first AI companion for Android. Say her name, say what you need, and she does it: call someone, send or read a message, set an alarm, open an app, play a video, or just talk. Built with Kotlin and Jetpack Compose, with a 3D character that reacts while she listens, thinks and speaks.

> **Status:** pre-release (version 3.3). Being prepared for Google Play. Tested so far on one phone (Samsung Galaxy S22+). It has no public users yet, so there are no download numbers.

## What it does

- **Hands-free by voice.** An always-on wake phrase ("Hi Elza") opens a request; one microphone feeds wake-word detection, the request and the live conversation, so she never answers her own voice.
- **Real phone actions, confirmed first.** Calls, messages, alarms, timers, opening apps and video search resolve against the user's real contacts and apps. Every action goes through a short countdown the user can cancel. Nothing is hard-coded or simulated.
- **English, Arabic (including everyday dialect) and Persian** for listening, answering and speaking.
- **Two brains.** Cloud (Gemini through Firebase AI Logic, including a low-latency Live conversation) or **on the phone with no internet** (Gemma 4 through LiteRT-LM, with Whisper through sherpa-onnx as the listener). The engine can be set to automatic, cloud or phone.
- **Private by design.** See [SECURITY.md](SECURITY.md): HTTPS only, encrypted personal data (Android Keystore), no AI key in the app, Firebase App Check, strict Firestore rules with emulator tests, and prompt-injection defences for messages from other people.
- **Honest diagnostics.** An on-phone voice log, a crash recorder (why the app last closed, with no speech recorded) and a performance view, all copyable by the user.

## Who it is for

People who find a touch screen slow or hard to use (low vision, limited hand use, driving, cooking, older users) and anyone who wants one companion that can do things instead of only answering. It has been designed around voice, but it has **not yet been evaluated with users who rely on screen readers or other assistive technology**; that is the next piece of work, not a claim.

## Build

Requirements: Android Studio, JDK 21, a phone or emulator (Android 7.0+, API 24).

1. Create a Firebase project and add an Android app with package `com.aistudio.cadence.mxkprz` (or change the application id).
2. Download its `google-services.json` into `app/` (the file is not in this repository; `app/google-services.json.example` shows its shape).
3. Enable Authentication (Google), Cloud Firestore and Firebase AI Logic. Deploy the rules: `firebase deploy --only firestore:rules`.
4. Run `./gradlew :app:assembleDebug`. Debug builds use the App Check debug provider.

There is no Gemini API key in the app or in this repository. The on-phone models (Gemma, Whisper) are downloaded by the user from Hugging Face inside the app (Settings → Gemma Lab).

Do not change the `signingConfigs` in `app/build.gradle.kts`; release signing uses environment variables.

## Tests

```
./gradlew :app:testDebugUnitTest
firebase emulators:exec --only firestore,auth --project demo-test "./gradlew :app:testDebugUnitTest"
```

About 110 unit tests (voice pipeline, command parsing, security rules, diagnostics). They run without a phone.

## More

- [CHANGELOG.md](CHANGELOG.md): every version, in plain words
- [SECURITY.md](SECURITY.md): protections and console steps
- [docs/](docs): launch plan, project record, build briefs
