# MochiLife Functional & Omnichannel Companion Upgrade Plan

Transform MochiLife from a static visual template into a fast, persistent, voice-first 3D companion application where every toggle, profile setting, navigation route, wake-word listener, and cross-app voice command (WhatsApp calls, contact lists, chat reading, social media search, YouTube, browser queries, and media playback) executes smoothly and reliably with a natural, human-like voice.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and UX decisions incorporate your confirmed choices from Phase 1 and address every functional gap identified in your review:

- **Confirmed Decision 1 — Instant Zero-Delay Command Execution with 3-Second Cancel Pill**: Voice and text commands immediately parse locally in under 50ms for known intents (WhatsApp call, WhatsApp chat/contact lookup, YouTube search/playback, social media post search, browser search, music/video playback, and internal screen navigation) while running Gemini (`gemini-3.8-flash`) in parallel for rich contextual replies. High-impact external actions display an interactive 3-second countdown pill (`Cancel` / `Execute now`) so actions never stall or silently fail.
- **Confirmed Decision 2 — High-Definition Neural Voice Tuning**: Replace flat default system speech with an expressive Neural Voice Engine that automatically selects the highest-quality local/network neural `Voice` on the device, applies per-character pitch, speech rate, and prosody (`Nurse Elena`: warm & calm; `Kaito Storm`: crisp & focused; `Chef Marco`: warm & expressive), and streams real-time PCM/RMS amplitude into the 3D character's jaw/mouth morph targets.
- **Confirmed Decision 3 — Continuous Wake-Word Listening + Persistent Application State**: All settings, toggles (`"Hi Mochi" / "Hi Elena"` wake word, Floating companion, Busy mode rules, Driving mode, Privacy flags), profile edits, wallet energy, and saved Library items are stored in a centralized persistent store (`SharedPreferences` + `StateFlow` in the shared ViewModel + Firestore user sync) so switching screens or restarting the app never resets any toggle.

---

## 1. Overview & Core Concept

- **What It Does**: MochiLife is a live 3D companion stage and system-wide assistant. Users can speak naturally (via continuous `"Hi Mochi"` / `"Hi Elena"` wake-word detection or one-tap mic) or type to execute real Android intents across WhatsApp, Telegram, YouTube, social media, web browsers, and device media, while managing kitchen pantry recipes (Fridge Chef), daily quests/tools, and saved library items.
- **Target Audience / Persona**: Users who want a hands-free, emotionally perceptive companion that immediately performs real actions on their phone instead of showing static mock screens or asking repetitive follow-up questions.
- **Key Value**:
  1. **Zero-Skeleton Persistence**: Every switch, radio button, text input, permission state, and library item is backed by a single source of truth that survives screen transitions and app restarts.
  2. **Hybrid Instant + AI Command Router**: Commands never fail or lag—deterministic local intent matching triggers immediately alongside Gemini structured inference (`gemini-3.8-flash`).
  3. **Smooth HashRouter-Style Navigation**: Animated route transitions (`#/stage`, `#/pantry`, `#/quests`, `#/tools`, `#/library`, `#/profile`, and sub-routes) with full back-stack history and voice-driven route switching.

---

## 2. User Experience & Visual Design

- **Main Stage Screen Scaffold (`DESIGN.md` Specification)**:
  - **Top Status & Mood Bar**: Displays the active companion selector pill (`Nurse Elena`, `Kaito Storm`, `Chef Marco`), a live **Mood & Voice Status Indicator** (`Listening · Wake word active`, `Thinking`, `Speaking`, or live calm/stress aura badge), and quick-access header actions (Floating Mode, Profile & Settings).
  - **Spatial 3D Character Stage Container**: Occupies the upper 62% of the viewport over the 20-second AGSL Aurora background, reacting in real time to mic RMS levels, wake-word triggers, and neural voice lip-sync.
  - **Interactive Action & Conversation Dock**: Houses contextual suggestion chips, quick action pills (`Call`, `Message`, `Fridge Chef / Pantry`, `Convert`, `Quests`), the live 3-second action execution banner when launching external apps, and the unified GlassSurface voice/text input bar.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Strict adherence to `DESIGN.md` Dark Aurora (`#07090F` background, `#0E121B` raised surfaces, 24dp glass blur) and Warm Paper Light mode (`#F6F3EC` background, `#FFFFFF` cards), with a 600ms animated color crossfade when switching companions or themes.
  - *Color Palette & Mood*:
    - `--background`: `#07090F` (Dark) / `#F6F3EC` (Light)
    - `--surface-raised`: `#0E121B` (Dark) / `#FFFFFF` (Light)
    - `--text-primary`: `#F3F5F9` 100% / `#17181A`
    - `--text-secondary`: `#F3F5F9` 64% / `#64666B`
    - `--accent-elena`: `#4FD1A5` | `--accent-kaito`: `#5AB8FF` | `--accent-marco`: `#F5B04C` | `--danger`: `#FF5A5F`
- **Interactive Feedback & Motion**:
  - **HashRouter Animated Transitions**: Switching between `#/stage`, `#/pantry`, `#/quests`, `#/tools`, `#/library`, and `#/profile` uses smooth spring-Physics crossfade + subtle vertical slide transitions (`< 260ms`) with zero frame drops.
  - **Live Voice Wave & Countdown Pill**: Speaking immediately animates the 3D companion into `Listening` lean-in; recognized commands show a live transcript pill and, for calls or external app launches, a 3-second interactive progress bar with `Cancel` and `Open now` buttons.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Hybrid Local + Gemini Omnichannel Command Engine**
  - *Chosen Approach*: Combine an ultra-fast (<50ms) local natural-language intent parser with `gemini-3.8-flash` structured output.
  - *Why*: Waiting 2–4 seconds for an LLM round-trip before opening WhatsApp, dialing a contact, or searching YouTube feels sluggish. By parsing common command patterns locally while streaming the conversational response from Gemini, the companion starts the action or 3-second countdown immediately and speaks a natural confirmation without delay.
  - *Supported Command Capabilities*:
    - **WhatsApp Calls & Contacts**: *"Call [Name] on WhatsApp"*, *"Open WhatsApp contact list"*, *"Open WhatsApp chat with [Name] and read the latest message"*. Resolves matching contacts from the device contacts book (when permission is granted) or launches the direct WhatsApp contact/call/chat deep link (`whatsapp://send`, `https://wa.me/`, or package intent `com.whatsapp`) and reads incoming/saved voice/text messages aloud.
    - **Social Media Posts & News Search**: *"Search X / Instagram / TikTok / Reddit for [topic]"*, *"Check latest posts or news about [topic]"* — opens the target social app or live news feed and summarizes key highlights aloud.
    - **YouTube & Media Playback**: *"Open YouTube and search for [video]"*, *"Play relaxing music / lofi video on YouTube"*, *"Tell me what is playing"* — queries active `MediaSession` metadata on the device to announce currently playing tracks/videos or launches YouTube directly with the requested query.
    - **Web Browser & Live Search**: *"Search in browser for [query]"*, *"Open [website]"* — launches `Intent.ACTION_WEB_SEARCH` / `ACTION_VIEW` with normalized URLs or search queries.
    - **In-App HashRouter Voice Navigation**: *"Open Pantry / Fridge Chef"*, *"Show my Quests"*, *"Go to Library"*, *"Open Profile"*, *"Switch to Chef Marco"* — immediately navigates the internal router to the requested view.
- **Decision 2: Centralized Persistent State Store (`MochiStateStore`)**
  - *Chosen Approach*: Replace all screen-local `remember { mutableStateOf(...) }` toggles with a single persistent repository backed by synchronous/reactive `SharedPreferences` and `StateFlow` in the shared ViewModel, mirrored to Firestore when authenticated.
  - *Why*: Previously, toggling `"Wake word · Hi Elena"` or changing Busy Mode options only mutated local composable state, which was wiped out as soon as the user navigated to another screen. Centralizing state guarantees 100% persistence across all screens.
- **Decision 3: Continuous Wake-Word Loop & Neural TTS Prosody**
  - *Chosen Approach*: When the `"Hi Mochi / Hi Elena"` wake-word toggle is enabled, run a resilient, auto-restarting Android `SpeechRecognizer` listener that watches partial and final transcripts for wake phrases (`"hi mochi"`, `"hey mochi"`, `"hi elena"`, `"hey elena"`, `"ok mochi"`) and transitions seamlessly into active command capture. For speech output, select high-quality neural voices from `TextToSpeech.voices`, shape sentences with natural pauses, and tune pitch/speech-rate per character.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        MochiLife Persistent State Layer                      │
│  ┌──────────────────────────────┐      ┌──────────────────────────────────┐  │
│  │  MochiPreferencesRepository  │◄────►│   Firestore User & Library Sync  │  │
│  │  (Wake Word, Busy Mode,      │      │   (Profile, Quests, Saved Meals, │  │
│  │   Driving, Privacy, Wallet)  │      │    Library Docs, Chat History)   │  │
│  └──────────────┬───────────────┘      └─────────────────┬────────────────┘  │
└─────────────────┼────────────────────────────────────────┼───────────────────┘
                  │                StateFlow               │
                  ▼                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                     Shared CadenceViewModel & HashRouter                     │
│  • HashRouter State (#/stage, #/pantry, #/quests, #/tools, #/library,        │
│    #/profile, #/driving, #/when-busy, #/wallet, #/permissions, #/privacy)    │
│  • Continuous Wake-Word Engine ("Hi Mochi" / "Hi Elena" -> Command Capture)  │
│  • Neural Voice Prosody Engine (High-Quality Voice Selection + Lip-Sync)     │
└─────────────────┬────────────────────────────────────────┬───────────────────┘
                  │                                        │
                  ▼                                        ▼
┌─────────────────────────────────────┐  ┌─────────────────────────────────────┐
│   Stage & Navigation Views          │  │   Omnichannel Command Executor      │
│  • Stage Scaffold (3D + Mood + Bar) │  │  • WhatsApp Call / Chat / Contacts  │
│  • Pantry (Fridge Chef) & Quests    │  │  • Voice Message Reader & TTS       │
│  • Library, Search & Profile Suite  │  │  • YouTube & MediaSession Inspector │
│  • 3-Second Action Countdown Banner │  │  • Social Media & Browser Launcher  │
└─────────────────────────────────────┘  └─────────────────────────────────────┘
```

### Data Model & Persistent State Mapping

1. **HashRouter Navigation State (`MochiHashRouter`)**:
   - Maintains current hash path (`#/stage`, `#/pantry`, `#/quests`, `#/tools`, `#/library`, `#/search`, `#/profile`, `#/profile/edit`, `#/profile/wallet`, `#/profile/permissions`, `#/profile/privacy`, `#/profile/help`, `#/profile/busy`, `#/driving`) and a reactive back-stack list so back navigation and deep-link commands (`navigate("#/pantry")`) animate smoothly.
2. **Persistent Settings State (`MochiPersistentSettings`)**:
   - `wakeWordEnabled: Boolean` (controls live `"Hi Mochi / Hi Elena"` continuous listening)
   - `floatingCompanionEnabled: Boolean` (controls floating overlay & PiP readiness)
   - `autoQuietVideo: Boolean`, `autoQuietGames: Boolean`, `busyHandlingMode: Int`
   - `drivingAutoStartBluetooth: Boolean`, `drivingReadMessagesAloud: Boolean`
   - `saveConversations: Boolean`, `personalizedMemory: Boolean`, `voiceImprovements: Boolean`
   - `userName: String`, `userNickname: String`, `userBio: String`
   - `aiEnergyBalance: Int`, `dailyStreakDays: List<Boolean>`, `proPlanActive: Boolean`
3. **Omnichannel Command Intent Model (`OmnichannelCommand`)**:
   - `WHATSAPP_CALL` (resolves contact phone/URI or opens WhatsApp call/chat for recipient)
   - `WHATSAPP_CONTACTS` (opens WhatsApp contact picker / people list)
   - `WHATSAPP_READ_CHAT` (opens chat for recipient and reads latest/queued message & voice note transcript aloud)
   - `SOCIAL_SEARCH` (opens X/Twitter, Instagram, TikTok, Reddit, or Google News for the requested topic)
   - `YOUTUBE_PLAY_OR_SEARCH` (opens YouTube video search or inspects active `MediaSession` to announce what is currently playing)
   - `BROWSER_SEARCH` (launches browser search or URL)
   - `MEDIA_OPEN` (opens device music/video player or plays requested audio/video stream)
   - `NAVIGATE_ROUTE` (switches HashRouter to `#/stage`, `#/pantry`, `#/quests`, `#/tools`, `#/library`, or `#/profile`)
