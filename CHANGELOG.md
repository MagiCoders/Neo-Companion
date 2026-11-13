# MochiLife changelog

Every update gets a new version. Check yours in **Settings** (bottom of the screen): `MochiLife <version> (build <code>)`.

## 3.3 (build 24)
Removes the crashing test voice, fixes a request said right after "Yes?", and gathers evidence for the background pause.
- The Gemma Lab crash is gone: it was the Piper test voice (native crash in its speech engine). The test voice, its downloads and the GPL pronunciation data are removed, and sherpa-onnx is now version 2.0.1, which contains no espeak-ng. Whisper (the phone-mode listener) is unchanged. Voices left on the phone by 3.1 and 3.2 are deleted at start.
- The voice detector no longer takes the first sound it hears as the room's noise. After "Yes?" that first sound was your own voice, so it demanded speech three times louder than you and logged "No speech heard" with a loud sound. It now starts from the room's real level (from the wake listener), capped, and keeps 0.75 s of lead-in so soft first sounds are not cut.
- A microphone that delivers only silence (Android muted it) is noticed and restarted instead of waiting 7 s.
- The echo check ("That was her own voice") now runs only when her voice was loud enough at the microphone to be an echo; otherwise your words are kept.
- Phone mode: Whisper stays loaded when you leave the app (it was unloaded each time and took 3 s to reload); phone mode no longer calls the cloud voice or prepares cloud voice lines; Whisper gets a little silence around each clip. Settings now call "On this phone" basic and "Automatic" recommended.
- The wake listener does not run while the Lab or Voice check is recording.
- Tapping the floating bubble no longer stops the listening service. When you open the app, a service that Android no longer counts as a microphone service is re-registered.
- More evidence: the "Mic details" line adds the service state, the microphone permission mode and how long ago the app left the screen; a CPU watch writes the busiest threads to the app trail when the app uses processor in the background; the 1-second voice tick idles when nothing needs the microphone.
- Licence blocker resolved (see docs/PROJECT_RECORD.md). Checked on the built APK: no espeak file, no espeak-ng or espeak_ symbol in any sherpa-onnx library (the only mentions are v2 warning texts saying it is no longer used).

## 3.2 (build 23)
Crash recorder, Whisper listens in phone mode, and less load while she thinks on the phone.
- Diagnostics → "Last close": why Android last ended the app (app crash, engine crash, killed for memory, frozen), the last app error, and what the app was doing just before. "Copy last close" and "Copy app trail" copy it for you to send. Nothing leaves the phone and no speech is recorded in it.
- A rolling app trail (screens, model loads, downloads, memory every 30 s, voice wake and error events) survives crashes and kills.
- Phone mode now listens with Whisper (base for English, small for Arabic and Persian when downloaded), tries the on-phone commands first (about 1 s) and asks Gemma only when needed. If no Whisper is downloaded Gemma listens itself, and its reply is never dropped silently: the log shows what it said, and an answer without the written-down words is still used. Settings → AI engine shows which of Gemma and Whisper are downloaded.
- Hold to talk in Gemma Lab: the press no longer leaves the microphone held open if the language or chip changes mid-press or the screen closes; clear messages when the microphone permission is off or no sound arrived; all speech-engine calls run on one thread; models are freed when you leave the Lab.
- While Whisper or Gemma works, the 3D character draws at 10 fps and her name is not decoded (a tap still interrupts); the floating bubble shows "Thinking…" in phone mode.
- "No speech heard" log lines now also say how many frames were above the speech level and the longest unbroken run.
- Licence reminder: sherpa-onnx 1.13.8 bundles espeak-ng (GPL). Test builds only; it must not ship on Google Play (see docs/PROJECT_RECORD.md, Release blockers).

## 3.1 (build 22)
"On this phone" really stays on the phone, Gemma hears you directly, the Gemma test works, and a timed offline-voice test.
- "On this phone" never starts a Live (cloud) conversation any more. The mic button started Live whenever "Live conversation" was on, whatever the AI engine; it now checks the engine (and being offline) first.
- In phone mode Gemma hears your recording itself (its audio part is loaded with the model) and writes down what you said, then answers. The phone's speech recogniser is no longer used for that. It had been taking her microphone after a tap or after her name, so Android silenced her own recorder ("Android paused her microphone" in the 3.0 log). If a model can't hear on a phone, it is loaded for text only and the recogniser is the fallback.
- The 16-request quality test failed on every request because LiteRT-LM allows only one open conversation per loaded model and the Lab chat already had it open. The chat is now opened only when needed and closed before each test request or on-phone answer. The same clash made Gemma's own answers fail. Errors now show the library's message.
- When Android silences her microphone, the voice log adds a "Mic details" line: every active recorder (hers or another app's, by type), her app's priority and the audio mode, so the cause can be seen.
- Gemma Lab → Offline voice (test only): checks Google's on-phone speech for English and Arabic (listening and voices); downloads the open Whisper listener (base or small) and Piper voices (English, Arabic, Persian) with resume and fingerprint checks; and "Hold to talk" times a full offline turn: Whisper text → Gemma's first sentence (streamed) → Piper's first sound, plus Gemma hearing the same recording directly. The report can be copied. The Piper voices use espeak-ng (GPL), so this stays a test feature and is not part of a Play release as it is.

## 3.0 (build 21)
Listening over other apps, Live ends when she hands over, and a choice of AI engine.
- She keeps listening when another app is on screen. Android only lets a background app use the microphone through a visible "microphone" service started while the app was still on screen; Mochi's started only as she left, too late when she opened YouTube herself. Her listening service now starts while the app is on screen and keeps running ("Mochi is listening · Stop listening" notification); the floating bubble is only shown or hidden as you leave or return. If the microphone was refused earlier (permission granted later), opening the app asks again.
- Pause messages tell the real reason: a call is in progress, another app is recording (system "Hey Google"/Bixby helpers don't count), Mochi is in the background without her listening service, or Android paused her microphone. The old message always blamed "another app".
- A Live conversation ends after she has confirmed an action that opens another app or starts media (video, call, music), so a video's sound is never answered ("Avanza…" in the 2.9 log). Her name starts a new conversation.
- AI engine (Settings): Automatic (cloud when online, on the phone when offline), Cloud, or On this phone. "On this phone" answers with Gemma (downloaded in Gemma Lab, loaded on first use), turn by turn: the phone turns speech into text, Gemma replies, any action goes through the same checks and countdown, and the phone's voice speaks. Calls, WhatsApp calls, opening apps, YouTube and reading messages work through Gemma; alarms, timers and the time are handled by the on-phone commands as before.
- The home screen shows what answered the last turn: Live, Cloud, On phone (commands) or Gemma.
- Offline, she uses the phone's voice at once instead of waiting for the natural voice to time out.
- Performance: the 19.7% CPU in the 2.9 log was measured while a video played; most of it is the wake listener decoding the video's sound to catch her name (67% of the time). That is the cost of hearing her name over media; it stops when the video stops. Not changed in this version.

## 2.9 (build 20)
She can no longer answer herself, and Live conversation is back as the fast mode.
- Rule, enforced in code: her voice can never become a request. No request is recorded while her voice plays (any attempt is refused and logged), and in Live mode the microphone is not sent to the AI at all while she speaks. Only her name, "stop" or a tap interrupt her. The loudness-based "talk over her" from 2.7, which mistook her own voice for you, is removed.
- Turn by turn: listening starts again 0.3 s after her voice has really stopped, from fresh sound only (nothing recorded while she spoke is used).
- Echo guard checks every transcript: two or more of her words in a row making up half of what was heard ("I'm listening…") is her own voice and is ignored without a reply.
- Loop breaker: three very short turns in a row, each within 12 s, with nobody saying her name or tapping, ends the conversation and says so in the voice log.
- Live conversation (Settings → Live conversation, on by default): after her name a Gemini Live session opens on the same microphone. She starts talking while the answer is still being made, phone actions still go through the app's checks and 3-second countdown, and a request said in one breath with her name is included. Every session measures how long her voice takes to start; if the connection fails, or the first answer takes more than 2.5 s, she continues turn by turn by herself without losing what you said. 25 s of silence ends the conversation. A tap on the mic starts or ends it.
- Voice check (Settings → Diagnostics): about a minute in a quiet room. Measures room noise and how much of her voice reaches the mic, confirms nothing after her voice is taken as speech and that the echo guard knows her words, and times three AI answers, three natural-voice starts and three Live voice answers from this phone. Prints PASS/FAIL with numbers and can be copied.
- Gemma Lab: the quality test now shows "understood, detail missing" (≈) separately from real misses.
- Fixed: the "Send crash reports" switch from 2.3 was not saved between app starts.
- Not in this version: on-phone commands (calls, alarms…) are not handled on the phone first in Live mode; Live does them through its own action call.

## 2.8 (build 19)
Fixes from the 2.7 voice log, and Gemma Lab.
- No more 30-second waits. The AI service sometimes held a request for 11–30 s while the same request sent again answered in 2 s. Now, if no answer has come after 4 s, the same request goes out a second time in parallel and the first answer wins; a model that is out of quota or unavailable hands over to the other model at once; nothing waits longer than 15 s (28 s for long tasks), and then she says plainly that the AI service is slow. Every try is in the voice log.
- Spoken turns ask the AI to think as little as possible ("minimal"), which is what made some short answers slow; if the model refuses that setting it uses "low" instead.
- Her voice no longer switches from female to male. When the natural voice is not ready in time the phone's voice takes over, and it was only set to female for Elena. Now the phone voice always matches her (female for Mochi and Elena, male for Kaito and Marco), using the gender codes in Samsung and Google voice names. The voice log says when the phone's voice was used.
- Gemma Lab (Settings → Gemma Lab): download Gemma 4 E2B (2.6 GB) or E4B (3.7 GB) from Google's public model page, Wi-Fi only unless allowed, with pause and resume and a SHA-256 check against the published fingerprint before use; run it fully offline on CPU or GPU; chat by text or voice (English, Arabic, Persian); see load time, time to first word, tokens per second, memory and battery temperature; run a 16-request quality test in three languages. On an 8 GB phone such as the S22+, E2B fits and E4B is marked experimental.
- The APK is about 22 MB larger (Gemma's engine). Models are never inside the APK.

## 2.7 (build 18)
One microphone, no waiting (Phase "voice rebuild" of the Fix and Upgrade Plan).
- One recorder stays open while voice is on, instead of a wake-word recorder and a request recorder that took turns. The wake detector, the request recorder and the talk-over detector all read the same microphone, so nothing you say falls into a gap. The last 8 seconds are kept in memory only (never stored or sent), so a request can start before she noticed it.
- "Hi Elsa, open WhatsApp" in one breath works: the request is taken from the same recording, and she does not answer "Yes?" first. The AI is told the recording may start with her name.
- No beep. After her name alone she answers with one word ("Yes?", "تفضل", "جانم؟", kept on the phone so it plays at once), then listens. The very first time, before that word is prepared, she just listens.
- You can talk over her. While she is thinking, speaking clearly drops the answer she was preparing and she takes your new request from its first word. While she is speaking, she stops when you speak clearly above her own voice (her voice level after echo cancelling is measured and written to the voice log). If her own voice is mistaken for you twice, this switches itself off until the app restarts and saying her name still interrupts her.
- Shorter end-of-turn pause: 0.9 s of quiet ends a short request, 1.3 s once you have talked for more than 2.5 s. After she speaks, listening resumes within 0.25 s and includes anything you said meanwhile (minus her 0.2 s echo tail).
- The language chip opens a small menu (English, العربية, فارسی) instead of cycling, and a double tap counts once.
- Live voice chat is hidden until it is rebuilt on the one-microphone design.
- Voice log: "Microphone on/off", "Listening for your request (including N s you had already said)", "Wake phrase heard, with your request in the same breath", talk-over events.
- Not in this version: sending her first sentence to the voice while the rest of the reply is still arriving (the reply comes as one structured answer, so this needs a different request format; planned with the server work), and warming the connection on her name.

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
