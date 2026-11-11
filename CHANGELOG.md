# MochiLife changelog

Every update gets a new version. Check yours in **Settings** (bottom of the screen): `MochiLife <version> (build <code>)`.

## 2.6 (build 17)
Speed and "is she listening?" fixes from the second S22+ log.
- Her voice is streamed: she starts talking when the first sound arrives instead of after the whole sentence is prepared (it was 3.5–3.8 s every time). If streaming is not available she falls back to the old way, then to the phone's voice.
- Fixed lines are kept on the phone: her one-word answer to her name ("Yes?", "تفضل", "جانم؟"), "anything else?" and the goodbyes are prepared once in the background per language and then play instantly, also after restarting the app.
- She answers her name again, with that one word, then the mic opens. Until it is prepared (first minute, offline) she beeps and listens at once.
- The same soft beep every time the mic opens, louder than before, so you know when it is your turn. The pause before the mic reopens after she speaks is shorter (0.35 s).
- "No speech heard" now reports numbers (how loud the loudest sound was and how loud speech had to be) and keeps that recording too, so "Play what she heard" works when she did not react.
- The log line "Her voice started N s after you finished speaking" now measures until sound actually comes out, not until she starts preparing it.
- Wake listener: the room-noise level is now a running average, so ordinary room noise no longer reopens the sound gate (it was still decoding 66% of the time).

## 2.5 (build 16)
Fixes from the first S22+ voice log of 2.4.
- She listens at once after her name: a short chime, then the mic is open in a fraction of a second. The spoken greeting is gone (it took 3–6 s to prepare, so the start of your request was lost and the AI then "heard no request").
- No more spoken "Let me check." while she thinks. It cost a second voice request, and the mic reopened after it while the real answer was still coming, so she recorded herself and looped. The mic now never reopens while an answer is pending.
- The AI no longer throws away clear speech as "other people": the mic was opened for her, so any words it can make out are transcribed and answered. It is told which language you most likely speak now, to judge the language by sound, and never to translate.
- Wake listener really rests now. On the S22+ it was decoding 95% of the time, because steady room noise kept its sound gate open. The gate now closes after 6 s at most and learns that level as the room's noise.
- No false "stuck in Ready" watchdog line while the wake model is still loading.
- Better voice log: seconds from her name to the mic opening, seconds from the end of your speech to the start of her answer, what the AI says an empty recording contained, and 25 lines instead of 12.
- "Play what she heard" in Diagnostics plays back the last recording sent to the AI (kept on the phone only, replaced every turn), so you can hear whether it was cut off, too quiet, or her own voice.

## 2.4 (build 15)
Fixes for heavy CPU use and the false "another app is using the mic" pause.
- Mic check rebuilt. She no longer counts recorders (that counted her own recorders and always-on helpers such as "Hey Google" and Bixby). She steps back only when Android reports that it silenced her recorder for another app, or a call is in progress, and only after it has held for one second. When silenced, the wake listener stays open so she resumes the moment the mic is hers. The voice log says which signal caused a pause.
- Tapping the mic while another app really has it now says so instead of listening to silence.
- Wake listener rests in quiet rooms: a cheap loudness check comes first and speech decoding runs only when there is sound, with a short lead-in so the start of her name is never cut.
- 3D Mochi drawn at 30 frames a second instead of every screen refresh (up to 120 on the S22+); the sparkle layer and pose loop capped at 30 (20 in the floating bubble); the app asks the screen for 60 Hz.
- An unused test model that was loaded every time the home screen opened has been removed.
- Performance meter in Settings → Diagnostics: average and peak CPU over the last minute, memory, 3D frames per second and how often the wake listener decodes. Included when you copy the voice log.
- Language chip on the home screen: tap to switch English → Arabic (your dialect) → Persian.

## 2.3 (build 14)
Phase 1 of the launch plan: reliability.
- Voice watchdog: the voice system now always knows what it is doing (off, ready, waiting for her name, listening, thinking, speaking, paused) and resets anything stuck too long (thinking over 30 s, speaking over 2 min, mic open over 45 s, wake listener missing or left on). Every reset is written to the voice log.
- The home screen shows "Speaking" and "Paused: another app is using the mic" besides listening and thinking.
- Alarms and timers by voice are now understood on the phone (English, Arabic, Persian, Arabic and Persian digits, am/pm, every day, weekdays, weekends, "half an hour"): instant and no AI quota. "Show my alarms" too. Cancelling or changing an alarm still goes to the AI.
- Alarm and timer requests check that a Clock app answers before claiming success, log which app handled the request, and say plainly when none does or permission is missing (the cause of "alarm doesn't work" will now show in the voice log).
- Crash and error reports (Firebase Crashlytics) with a switch in Settings; reports never include what you say. Watchdog resets are sent as non-fatal reports.
- Reliability gate: a corpus of 63 everyday requests in three languages runs on every test run and the build fails below 95%. Add any phrase that fails in real life to `CommandCorpusTest`, then fix the parser.

## 2.2 (build 13)
**New listening and speaking design.**
- **AI listening (new default)**: the phone records your turn with echo cancellation and noise suppression, a voice detector notices when you start and finish (it waits through short thinking pauses), and the recording goes straight to Gemini, which hears it in any language or mix (Arabic dialects, Persian, English) and answers in the same request. Android's speech recogniser is no longer used for this: no single language to get stuck in ("Persian" can't turn into "Russian" any more), no beeps, no cut-offs, no "not understood" loops. Names are written the way you say them. If the AI quota runs out, she switches to the phone's recogniser for an hour automatically. Settings → AI listening.
- **Natural voice (new default)**: replies are spoken by Gemini's speech model with a real accent for your language (Iraqi/Baghdadi Arabic, Egyptian, Saudi, Emirati, Tehrani Persian…), instead of the robotic phone voice. Short repeated lines are remembered so they play instantly. If it's unavailable or out of quota, the phone voice is used. Settings → Natural voice.
- Noise or other people's voices in the recording are ignored quietly (no "sorry, could you repeat that?").
- "No thanks", "nope", "لا شكرا", "نه ممنون" now end the conversation instead of starting another "anything else?" loop.
- YouTube: she shows you the results and you pick (one result plays straight away).
- "Open internal storage" / "files" opens your file manager; unknown names no longer open Google Play.
- The voice log now records every action and its result (e.g. why an alarm didn't set).
- Removed a bad example from the AI instructions that taught it to spell "علي اوديت" as "Ali Odeh".

## 2.1 (build 12)
- **Calling the right person**:
  - "my mother" no longer lists five random people. Words like "ma", "أم" used to match inside other names ("Mahmoud", "Omar"); now only a whole word of the name or a contact nickname counts. Tip: give her contact the nickname "Mom" or "أمي".
  - Names are matched by spelling and by sound, across English and Arabic letters ("Ali audits" or "علي اوديت" → "Ali Odeh"), and a clear winner is called without asking.
  - The speech recogniser is given your contact and app names to favour (Android 13+).
  - If a quick on-phone command can't find the person for sure, the AI is asked once for other spellings before she asks you to choose.
- **YouTube**: if the YouTube search service is unavailable, the chat now says exactly why, and with the helper on (now "MochiLife helper (WhatsApp & YouTube)") she taps the first real video in the results so it plays. Re-enable the helper once after updating so Android picks up YouTube.
- **Alarms and timers**: "wake me up at 6:30 every weekday", "set an alarm tomorrow at 9 pm called Gym", "timer 10 minutes", "show my alarms". Set silently in your Clock app. A specific date further away becomes a calendar reminder, because the Clock app only knows times and weekdays.
- **Languages**:
  - "Speak Arabic" keeps your Arabic dialect (Iraqi stays Iraqi).
  - Replies stick to one dialect (Iraqi/Egyptian/Gulf) and Persian replies use Persian words, not Arabic.
  - If your phone has no voice for a language (common for Persian), she tells you once and opens the voice download screen instead of reading it with an Arabic voice.
- The AI now knows the current date and time.

## 2.0 (build 11)
- **Everyday commands without AI** (instant, no AI quota): "call X", "call X on WhatsApp", "video call X", "play X on YouTube", "open X", "read my messages", "what time is it", in English, Arabic and Persian (e.g. "اتصل بعلي على الواتساب", "شغللي فيروز على اليوتيوب", "به مامان زنگ بزن"). Multi-step requests still go to the AI. The voice log shows "Understood on the phone (no AI used)".
- **Plays the actual YouTube video**: finds it with YouTube's free search service (not Gemini). If the top result clearly matches, it plays; if not, she reads the top 4 and you answer "the second one" / "الثاني" / "دومی" (or "none"). Needs "YouTube Data API v3" enabled once in Google Cloud. Settings → Diagnostics → Run check tells you if it's working; until then she opens a YouTube search as before.
- **WhatsApp video calls** ("video call Ali").
- **Send a photo, video or file on WhatsApp** ("send Ali a photo"): the phone's photo or file picker opens (no storage permission needed), you choose, it goes straight into Ali's chat and the WhatsApp helper presses Send (or you tap Send if the helper is off).
- **No more nagging after a false wake-up**: if noise wakes her and you never actually say anything, she goes back to sleep quietly. "Anything else?" is only asked after a real exchange.

## 1.9 (build 10)
- **Fixed: stuck in Arabic.** After switching to Arabic, "switch to English" was written down in Arabic letters and the AI didn't switch back. Language switching is now recognised on the phone itself (English, Arabic, Persian words and their Arabic-letter spellings, e.g. "سويتش تو انجلش", "حولي فارسي", "احجي عراقي"), switches instantly and doesn't use AI quota. Works from the floating companion too.
- **Fixed: names said in Arabic not found** ("اتصل بعلي اوديت" when the contact is saved as "Ali Odeh"). The AI now also gives the name's spellings in Latin and Arabic script, and each is tried. If only the first name matches, she asks which one you mean instead of saying "not found".
- **Dialect**: replies in Arabic (Iraq) and other regional settings are written in that country's everyday dialect instead of formal Arabic.

## 1.8 (build 9)
- **No beeps**: the microphone's open/close beep is hidden by muting the sound for a split second (Settings → Silent listening, on by default). Anything left muted by a crash is restored the next time the app starts.
- **Steps aside for other apps**: while a call is in progress or another app is recording (WhatsApp voice note, recorder, another assistant), she stops listening and ends the conversation; when the mic is free again, wake listening resumes.
- **Doesn't just go silent**: if you don't answer, she asks "Is there anything else I can do for you?" (or "Sorry, could you repeat that?" if she couldn't understand), listens once more, and only then says a polite goodbye ("Okay, I'll be right here if you need me.") and goes back to waiting for her wake phrase. In English, Arabic, Persian, French, Spanish, German and Turkish.
- **Lets you finish**: she waits for a longer pause (1.8 s) before deciding you're done talking.
- **The conversation continues after most requests**: only calls, YouTube, music and voice notes end it (so she doesn't hear the other app's sound). Sending a message, opening an app or reading messages keeps the conversation going.
- **Opens real apps**: YouTube searches open in the YouTube app; Instagram/TikTok/X/Facebook/Reddit searches open in their apps when installed. New "open <any app>" by name (also in Arabic/Persian, e.g. "افتح واتساب"). If the app isn't installed she says so and opens its Google Play page. Google Play doesn't let apps install other apps by themselves, so you tap Install.
- **Fixed**: your spoken words stayed in the text box after a voice turn.

## 1.7 (build 8)
- **Fixed: "Conversation ended (no reply)" right after waking.** While she talked, the wake listener held the microphone; when she finished, the speech recogniser started before the mic was really released and Android gave it silence. The wake listener now waits until the mic is free, and no longer marks its recording as "privacy sensitive" (which made Android silence other recorders).
- **Faster wake-up**: the wake listener decides after a shorter pause.
- **AI quota**: quick turns now use gemini-3.5-flash-lite (about 500 free requests/day) and real tasks gemini-3.6-flash (about 20/day). If one is out of quota, the other answers automatically. The quota message now says what's happening and how to remove the limit.
- **Voice log** now also shows what she said, when the mic opened for you, and recogniser errors, plus which model answered.
- "Hi Elsa" (a sound-alike of your wake phrase) is removed from what you say before it goes to the AI.

## 1.6 (build 7)
- **Real conversations**: say the wake phrase (or tap the mic) and she answers right away ("Yes? I'm listening."), then keeps listening after every reply, so you can talk back and forth without repeating her name. It ends when you say goodbye ("bye", "that's all", "خلاص", "خداحافظ"…), stay quiet after her reply, tap the mic, or an action opens another app (e.g. a WhatsApp call).
- **Switch language by voice**: "switch to Arabic", "talk in Persian", "speak Iraqi": she changes the voice language (recognition and voice) and answers in it. New Arabic dialect options: Iraqi, Saudi, Egyptian, Emirati.
- **Echo check fixed**: a question was wrongly dropped as her echo because it shared words with her answer ("can you hear me" after "yes, I can hear you"). Now only a word-for-word repeat of what she said counts as echo.

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
