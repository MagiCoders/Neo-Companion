# MochiLife: Project Record

Nov 10, 2026 · MochiLife team

Live version: https://claude.ai/artifact/L8GGnnJFq2QWtBam3SCJTF. Plan: [LAUNCH_PLAN.md](LAUNCH_PLAN.md).

## What MochiLife is

MochiLife is an Android companion app: a 3D character you talk to, who answers by voice and does things on the phone for you. It is at version 2.2 (build 13), working on a test phone, not yet on Google Play.

- **Repository:** `MagiCoders/Neo-Companion` (private), branch `main`. Android package `com.aistudio.cadence.mxkprz`.
- **Built with:** Kotlin and Jetpack Compose, Firebase (sign-in, database, App Check, Gemini through Firebase AI Logic), Filament for 3D, Vosk for the offline wake word.
- **Languages:** English, Arabic (including Iraqi and other dialects) and Persian are the tested ones; AI listening accepts any language Gemini understands.
- **Rules the project follows:** no fake features or sample data, nothing hard coded that should be real, encrypted personal data, protection against interception and tampering, and Google Play policy compliance (accessibility help is rule based, disclosed and opt in).
- **Every update** gets a new version number, a `CHANGELOG.md` entry and an APK named `MochiLife-v<version>-build<number>.apk`.

## What works today

"Confirmed" means you reported it working on your phone. "Built" means it is in the code and passes its tests, but nobody has confirmed it on a real phone yet.

| Feature | State | Notes |
| --- | --- | --- |
| Google sign-in, 100 daily AI energy | Confirmed | Needs the signing fingerprint in Firebase |
| 3D Mochi with reactions and lip movement | Confirmed | Faces front since 1.2 |
| Wake phrase ("Hi Elza"), custom wake names | Confirmed | Works offline, no beeps |
| Conversation mode (keeps listening until goodbye or silence) | Confirmed in English |  |
| Play a YouTube video by voice | Confirmed | 2.2 lists results and asks which one; that part is built, not confirmed |
| Call, WhatsApp call, open an app, read the time | Built, partly confirmed | Handled on the phone, uses no AI energy |
| Contact matching across English and Arabic spelling | Built | "Ali Audit" and "my mother" cases fixed in 2.1 |
| AI listening (the AI hears your voice directly, any language) | Built in 2.2 | Not yet reported on |
| Natural voice with Iraqi, Egyptian, Gulf and Persian accents | Built in 2.2 | Not yet reported on; uses a preview model |
| Alarms and timers | Built | You reported them not working before 2.2; cause not found yet |
| WhatsApp video call, send photo, video or file, play voice notes | Built | Needs the optional WhatsApp helper switched on |
| Read incoming messages aloud and reply by voice | Built | Needs notification access |
| Floating Mochi over other apps | Built |  |
| Driving mode | Built | Rebuilt in 1.2 with real actions |
| Diagnostics screen (self-test, voice log) | Confirmed | Settings, Diagnostics |

Not built yet: paid packages, the server, on-phone AI (Gemini Nano, Gemma 4), file conversion by voice with a Mochi folder, phone setting controls, voice file search, voice training, streaming voice, crash reporting.

## Version history

Thirteen builds so far; each row is one APK you received. Full detail is in `CHANGELOG.md` in the repository.

| Version | Build | What changed |
| --- | --- | --- |
| 2.2 | 13 | AI listening: echo-cancelled recording, the audio goes straight to Gemini in any language. Natural Gemini voice with accents. "No thanks" ends the conversation. YouTube shows a list to pick from. "Open internal storage" opens the Files app. Every action result is logged. |
| 2.1 | 12 | Reliable contact matching (family words, names across scripts). YouTube fallback with a visible reason. Alarms and timers. Dialect kept when switching language. |
| 2.0 | 11 | Everyday commands handled on the phone with no AI energy. Play an exact YouTube video. WhatsApp video call and sending photos, videos, files. |
| 1.9 | 10 | Language switching on the phone (fix for being stuck in Arabic). Names across scripts. Dialect replies. |
| 1.8 | 9 | Silent listening (no beeps). Pauses for calls and other apps using the mic. "Anything else?" on silence. Opens real apps, Google Play if missing. |
| 1.7 | 8 | Fix for silence after waking. Faster wake. Flash-Lite for quick turns with fallback when the daily limit is hit. |
| 1.6 | 7 | Conversation mode. Switch voice language by voice. Arabic dialects. |
| 1.5 | 6 | Echo-cancelled wake mic. Say her name to interrupt. Adaptive thinking. Sound-alike custom wake names. Voice log. |
| 1.4 | 5 | Turn-by-turn voice by default. Voice language setting. Echo guard. Custom wake phrase. |
| 1.3 | 4 | AI self-test in Settings with exact Firebase errors. |
| 1.2 | 3 | Mochi faces front. Offline wake word (no beep loop). Real driving mode. Honest AI errors. |
| 1.1 | 2 | Reads messages aloud, spoken replies, optional WhatsApp helper with disclosure. Version shown in Settings. |
| 1.0 | 1 | Import from AI Studio, all fake data and fake features removed, security hardening, real-time 3D Mochi. |

## How the voice system works

One spoken turn goes through five steps, and each step has a fallback so a failure never leaves her silent.

1. **Wake.** A small offline speech model (Vosk) listens only for the wake phrase. Nothing leaves the phone. The same listener hears "stop" or her name while she is speaking, so you can interrupt.
2. **Listen.** The microphone opens in call mode with echo cancelling and noise suppression. A voice detector decides when you started and finished (1.3 seconds of quiet ends the turn, 7 seconds of nothing gives up, 25 seconds is the longest turn).
3. **Understand.** Simple commands typed or heard by the phone recogniser (call, open, play, time, language switch) are matched on the phone and cost nothing. Everything else goes to Gemini as audio, and one request returns what you said, her reply and the action to take.
   - Fallback: if the daily AI limit is hit, the phone's own speech recogniser takes over for an hour.
4. **Act.** The action runs through checked code: a real contact is found (or she asks which one), the right app opens, and the result is spoken honestly, including failures.
5. **Speak.** Gemini's speech model makes a natural voice with the accent of your language. Short repeated lines are cached.
   - Fallback: the phone's built-in voice in the reply's language. After a limit error the natural voice rests for 30 minutes.

The conversation then reopens the microphone on its own. It ends on "goodbye" or "no thanks", on a mic tap, on a call or media starting, or after she asks "anything else?" once and hears nothing.

| Job | Model | Why |
| --- | --- | --- |
| Quick turns and audio turns | Gemini 3.5 Flash-Lite | Fast, cheap, higher free limit |
| Hard requests | Gemini 3.6 Flash | Better reasoning; free limit is about 20 a day |
| Natural voice | Gemini 3.1 Flash TTS (preview) | Accents and warmth |
| Streaming voice (optional, beta) | Gemini 3.1 Flash Live (preview) | Off by default; reported slow since September 2026 |

## Console setup

The app depends on settings in Firebase and Google Cloud that live outside the code. These are done:

- [x] Debug signing fingerprint (SHA-1 ending `8C:00`) added to Firebase, so Google sign-in works on test APKs
- [x] App Check debug token registered, so test APKs can reach the AI
- [x] API key settings fixed so Gemini requests are accepted
- [x] YouTube Data API enabled for the project

These are still needed before a public release:

- [ ] Publish the Firestore security rules from the repository (`firebase deploy --only firestore:rules`)
- [ ] Add the Google Play release fingerprints (SHA-1 and SHA-256 from Play App Signing) to Firebase
- [ ] Switch App Check for the release build from reCAPTCHA to Play Integrity and link the Play Console to the same Google Cloud project
- [ ] Turn App Check enforcement on for Firestore and Firebase AI Logic
- [ ] Restrict the Android API key to this package and the release fingerprints
- [ ] Move the project to the Blaze plan with a budget alert (needed for the server and paid AI; see the Launch Plan)
- [ ] Turn on Crashlytics

The Firebase connector you added did not appear in this chat. A new chat should pick it up; without it, console changes stay manual.

## Known problems and untested parts

The biggest gap is that version 2.2 has had no report from a real phone, so its two main changes are unproven.

| Problem | State |
| --- | --- |
| AI listening and natural voice (2.2) | Untested on your phone. The speech model is a preview and its free daily limit is unknown. |
| Alarms and timers | Reported not working before 2.2. Cause unknown. 2.2 logs every action result, so the next voice log should show why. |
| Reply speed | 3 to 6 seconds per turn when the AI is used. On-phone commands are instant. |
| Free AI limit | About 20 requests a day on Gemini 3.6 Flash and roughly 500 on Flash-Lite, for the whole project, not per user. This cannot serve real users. |
| Arabic voice quality on the phone's built-in voice | Robotic and not Iraqi. The natural voice is the fix; needs your confirmation. |
| Accuracy is not measured | There is no recorded test set yet, so "it works" is a feeling, not a number. |
| No crash reporting | If the app fails on someone else's phone, nothing tells us. |
| AI is called straight from the app | Fine for testing. For launch, limits enforced only in the app can be bypassed by a modified copy. |
| Free energy is 100 a day | Too generous for launch; costs real money at scale. |
| WhatsApp helper depends on WhatsApp's screens | A WhatsApp update can break it. It fails safely and says so. |

## Decisions and lessons

These were settled the hard way; a later session should not reopen them without new evidence.

| Decision | Reason |
| --- | --- |
| Wake word runs on Vosk, offline | Android's speech recogniser beeps and loops when restarted. sherpa-onnx was tried and was worse. |
| The wake recorder must fully release the mic before listening starts | Otherwise the next recorder gets silence ("conversation ended, no reply"). |
| Audio goes straight to Gemini instead of phone speech-to-text | The phone recogniser handles one language at a time and misheard "Persian" as "Russian". |
| Everyday commands are matched on the phone first | Instant, free, and they keep working when the AI limit is hit. |
| Quick turns use Flash-Lite, hard ones use 3.6 Flash, each falls back to the other | The free limit on 3.6 Flash ran out within minutes of testing. |
| YouTube always lists results and asks | You asked for a choice, not an automatic pick. |
| Family words ("my mother") match whole words only | Partial matching returned five contacts. |
| "Ali Audit" stays "Ali Audit" | Names are matched by sound across scripts, never translated. |
| Google Play is opened only for known apps | "Open internal storage" once opened Google Play. |
| No streaming voice by default | The Live preview model has been slow (9 to 15 seconds reported) since September 2026. |
| Errors name the real cause | Sign-in, App Check, API key and limit errors look alike; lumping them cost two days. |
| Gemma 4 is an optional download, never inside the APK | It is 2.5 GB or more. |
| Signing settings in `app/build.gradle.kts` are not touched | AI Studio needs them. Keystores, `.env` and `local.properties` are never committed. |
| No reverse-engineering of other companies' apps | Declined when asked; public documentation only. |

## Code map

All paths are under `app/src/main/java/com/example/`. A new build session should read these first.

| Area | Files | What they do |
| --- | --- | --- |
| Voice control | `ai/VoiceCommandService.kt` | Owns the microphone: wake, listen, conversation mode, interruptions, pausing for calls |
| Wake word | `ai/OfflineWakeWord.kt` | Vosk listener, wake phrase matching, sound-alike names |
| Recording | `ai/SpeechCapture.kt` | Echo-cancelled recording and the voice detector |
| The AI | `ai/CompanionBrain.kt` | Prompt, models, reply format, text and audio requests |
| Natural voice | `ai/NaturalVoice.kt` | Gemini speech with accents and a small cache |
| On-phone commands | `ai/LocalCommands.kt`, `ai/LanguageSwitch.kt` | Commands and language switching without AI |
| Diagnostics | `ai/AiDiagnostics.kt`, `ai/VoiceLog.kt`, `ai/AppCheckHelper.kt` | Self-test, error naming, voice log |
| Actions | `actions/ActionExecutor.kt` | Runs every action and reports the outcome |
| Contacts | `actions/ContactResolver.kt`, `actions/NameMatcher.kt` | Finding the right person |
| Apps and media | `actions/AppLauncher.kt`, `actions/YouTubeSearch.kt`, `actions/AlarmActions.kt` | Opening apps, YouTube, alarms and timers |
| WhatsApp helper | `actions/WhatsAppHelper.kt` | Opt-in accessibility steps for WhatsApp and YouTube |
| Messages | `bridge/MessageBridge.kt` | Reads notifications aloud |
| Main logic | `ui/CadenceViewModel.kt` | Connects voice, AI, actions and screens |
| Character | `ui/character/` | 3D Mochi, speaking with lip movement, floating overlay |
| Screens | `ui/screens/` | Home, Settings, Diagnostics, driving mode, disclosures |
| Data | `data/Models.kt`, `data/CadenceRepository.kt` | Settings, user record, Firestore access |
| Security | `security/SecureStore.kt`, `firestore.rules`, `SECURITY.md` | Encrypted storage, server rules |
| Tests | `app/src/test/java/com/example/CoreLogicTest.kt` | 73 tests of the logic above |

To build: JDK 21, `./gradlew :app:assembleDebug --max-workers=1`. To test: run the Firebase emulators (Firestore and Auth) around `./gradlew :app:testDebugUnitTest`.

The plan for what comes next is in [MochiLife: Launch Plan](https://claude.ai/artifact/LAfocss7wUB8EpdU9tReMB).
