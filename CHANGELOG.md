# MochiLife changelog

Every update gets a new version. Check yours in **Settings** (bottom of the screen): `MochiLife <version> (build <code>)`.

## 1.5 (build 6)
- **Cleaner microphone**: the wake listener records in voice-call mode with the phone's echo canceller and noise suppressor (when the phone has them), so her voice and room noise are reduced before anything is recognised. Diagnostics shows which ones your phone provides.
- **Interrupt her**: while she talks, say the wake phrase or "stop" (or tap the mic) and she stops at once. Her own voice saying those words is ignored.
- **Faster answers**: quick things ("call Mom", "hi") use light thinking, real tasks (plans, recipes, bill splits, "why/how") use full thinking. The AI-energy check now runs at the same time as the AI call instead of before it. If a spoken answer takes longer than ~2 s she says "let me check" in your language.
- **Custom wake names**: names the offline dictionary doesn't have are listened for through the closest-sounding words it knows ("Hi Elza" → "Hi Elsa" / "Hi Eliza"), or split into known pieces ("Mahsa" → "mah sa"). Close neighbours (e.g. "Ella") are added as decoys so they don't wake her.
- **Voice log** (Settings → Diagnostics): what she heard, what was ignored as echo, wake-ups, interruptions, and how long each AI answer took (and at which thinking level). "Copy voice log" for bug reports.
- Considered and rejected: switching the wake engine to sherpa-onnx. In side-by-side tests with natural-sounding speech it caught "Hey Mochi" only 1–2 times in 8 versus 7–8 for the current engine.

## 1.4 (build 5)
- **Turn-by-turn voice (new default)**: tap the mic or say the wake phrase, speak once, and she answers. The always-open Gemini Live call (which heard background noise as random languages and could hear herself) is now an option: Settings → **Live voice chat (beta)**, off by default.
- **Voice language** (Settings): speech is recognised only in the chosen language (Persian added), so noise can't come back as Japanese. She answers aloud in the language you spoke, with a matching voice. If what she heard is unclear, she asks you to repeat instead of guessing an action.
- **No more talking to herself**: the mic stays closed while she speaks, and anything that matches what she just said is ignored (echo guard).
- **Follow-up**: when she asks you a question, the mic opens once more after she finishes.
- **Wake phrase** (Settings): Hey / Hi / Okay / Hello Mochi, or your own 2–4 English words (checked against the offline vocabulary). Sound-alike words ("hey Mitch", "hello everybody") no longer wake her.

## 1.3 (build 4)
- Settings → Diagnostics → **Run check**: tests each link of the AI chain on its own (this app's package and signing SHA-1, sign-in, App Check token, one real AI request) and shows Firebase's exact error for any failing step. **Copy report** copies everything.
- AI errors are no longer all labelled "App Check": API-key restrictions and other access errors are told apart.

## 1.2 (build 3)
- **Mochi faces the front** (app and floating overlay). The model already faced the camera; the old −90° "correction" was what turned it sideways. Checked by rendering the model from all four sides.
- **Wake word without the beep loop**: "Hey Mochi" is now detected by an offline engine (Vosk, Apache-2.0) that listens quietly. No restarts, no on/off sounds, and no audio leaves the phone. Saying "stop" or "cancel" during a countdown cancels it.
- **Driving mode rebuilt**: the fake "Calling Mom in 3, 2, 1" screen is gone. The screen now shows real state (a countdown only when an action is actually pending) with big Talk / Hang up, Read my messages and Cancel buttons, and keeps the screen on.
- **Honest AI errors**: instead of always "can't reach my brain", the app says the real reason (App Check, quota, network, …). Settings → Diagnostics shows the last error and, in test builds, the App Check debug token to approve in Firebase.
- Smaller app: 32-bit x86 native code dropped; the wake model's unused dictation graph removed (tools/slim_vosk_graph.py).

## 1.1 (build 2)
- Read messages aloud: "read my messages", "any messages from Mom?" (WhatsApp, Telegram, Messages, Signal, Messenger, Instagram). Needs **Read messages** access (Permissions screen). Messages stay in memory only, and they are never saved or sent to the AI.
- Spoken replies: "reply to Sam: on my way" sends the reply through the chat app's own Reply button.
- WhatsApp helper (optional, Accessibility): presses Send for dictated messages, Play for the newest voice note and Voice call, only right after you ask. A prominent disclosure is shown before it is turned on.
- Permissions screen: new "Read messages" and "WhatsApp helper" rows.
- The version is shown in Settings.

## 1.0 (build 1): commits up to 3191dce
- Real 3D Mochi (Filament), facing fix, arm/leg animation, 14 reactions, particle VFX (stardust, sand, lightning, fire, hearts, confetti).
- Smaller floating Mochi with drag-to-close circle; continuous wake word; 100 daily AI energy (needs the updated Firestore rules to be published).
- Real contacts, calls, WhatsApp/Telegram/YouTube actions; security hardening; Gemini Live voice.
