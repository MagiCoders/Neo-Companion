# MochiLife changelog

Every update gets a new version. Check yours in **Settings** (bottom of the screen): `MochiLife <version> (build <code>)`.

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
