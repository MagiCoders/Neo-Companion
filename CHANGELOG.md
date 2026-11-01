# MochiLife changelog

Every update gets a new version. Check yours in **Settings** (bottom of the screen): `MochiLife <version> (build <code>)`.

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
