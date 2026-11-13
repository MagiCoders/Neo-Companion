# MochiLife 3.3 build brief

**For the building model (Sonnet or Opus).** This file is the whole instruction. Do not read the earlier conversation or the transcript. Read only the source files named in each step. Do not redesign; build what is written. If a step cannot be done as written, stop that step, say why in the final message, and finish the others.

Written 2026-11-13 after the user's 3.2 test on a Samsung Galaxy S22+ (8 GB).

---

## 0. Ground rules

Same as `docs/BUILD_BRIEF_3.2.md` section 0 (read that section only, lines 9 to 37): repo location, never-touch list, code rules, build commands, large-file editing rule, delivery steps. Differences for this build:

- Version: `versionCode = 24`, `versionName = "3.3"`. APK name `/home/claude/MochiLife-v3.3-build24.apk`. Build log `/tmp/claude-0/build33.log`.
- 3.2 had 105 tests, all passing.
- Source root `SRC/` = `app/src/main/java/com/example/`.

---

## 1. What the 3.2 test showed (facts and causes)

| # | Observation | Cause | Step |
|---|---|---|---|
| 1 | App closes in Gemma Lab → Hold to talk. "Last close": `engine crash (native code)`, `SIGABRT` in `OfflineTts.generateWithCallbackImpl`, "Doing: OFFLINE voice voice-en loading". Twice. | The Piper test voice (sherpa-onnx + espeak-ng) aborts inside native code. It is the GPL part that could never ship anyway. | A |
| 2 | She says "Yes?", the user speaks, log says "No speech heard in 7 s (loudest sound 9132, speech needs 250, above that for 0 frames)". Repeats. | `VoiceActivity` sets its noise floor from the **first frame it is fed**. After her name the first frames are the user's own voice (seeded frames), so the floor starts at the user's level, the speech threshold becomes 3× the user's voice, and nothing ever counts as speech. Confirmed in code: `if (noiseFloor < 0) noiseFloor = max(rms, MIN_FLOOR)` in `SRC/ai/SpeechCapture.kt`. | B |
| 3 | "No speech heard in 7 s (loudest sound 0 ...)" while the app was on screen. | The microphone delivered pure zeros for 7 s (Android muted it) and nothing noticed. | B4 |
| 4 | "That was her own voice, not you: "Thank you, I'm good."" The user had said it. Her voice reached the mic at level 100 to 240, under the speech level 250. | `EchoGuard` matches on words alone. Since 2.9 nothing is recorded while she speaks, so an echo is only possible when her voice is loud at the mic. | C |
| 5 | Arabic in phone mode: user said "شلونك", Whisper small wrote "فلونيش"; "شكرا جزيلا" became "فكرا جزينة". English also weak. First consonant wrong both times. | Partly Whisper small's limit on short Iraqi-dialect phrases (cannot be fixed here). Partly the start of the word being clipped: only about 300 ms of real lead-in is kept before speech is detected, and soft sounds like "sh" start below the speech level. | D |
| 6 | Phone mode turn took 13.4 s: listener load 2.9 s + Whisper 1.3 s + Gemma 7.5 s + cloud natural voice 1.2 s. Later "Natural voice unavailable (QuotaExceededException)". | (a) `MochiApp.onTrimMemory` releases the listener whenever the app leaves the screen (level 20 is "UI hidden", and the 3.2 check was `level >= 15`), so it reloads every time. (b) Phone mode still calls the cloud voice. (c) Switching language pre-renders voice lines in the cloud (four calls in the log) and burns the quota. | E |
| 7 | During a Lab Hold-to-talk recording: "Wake phrase heard, with your request in the same breath", a conversation started and a second Whisper model loaded. | The wake listener keeps running while the Lab records. | F |
| 8 | Background: "Paused: Android paused her microphone ... Mic details: recorders: hers source voice call/chat (silenced) · app priority: visible". It resumed only when the app came back on screen. | No other app was recording. App priority "visible" (200) instead of "listening service" (125) means Android did **not** count a foreground service for the app at that moment. Why is unknown. | G |
| 9 | "Last close": `killed: used too much, [EXCESSIVE CPU USAGE] excessive cpu 35760 during 300107`, app was 400 (cached), 72 MB. | Something kept using about 12% CPU for five minutes after the app and service were gone. Unknown what. | H |
| 10 | Tapping the bubble to open the app stops the listening service (`openApp()` calls `stopSelf()`); it is then started again by the activity. | Needless stop/start of the one service that holds the microphone right. | G1 |

Not a code problem: Gemma takes 5.6 to 7.5 s per answer on this phone. Phone mode stays a slow offline fallback.

---

## 2. Step A: remove the Piper test voice, move sherpa-onnx off the GPL build

Goal: no crash, no espeak-ng, no GPL code in the APK. Whisper stays.

A1. `gradle/libs.versions.toml`: `sherpaOnnx = "v2.0.1"` (JitPack, same coordinates). v2.0 has no espeak-ng. The Whisper classes used (`OfflineRecognizer`, `OfflineRecognizerConfig`, `OfflineModelConfig`, `OfflineWhisperModelConfig`, `FeatureConfig`, `OfflineStream`) exist in v2.0.1 with the same constructor parameters; fix any named-argument differences the compiler reports (the v2 Kotlin source is in `/home/claude/k2-fsa/sherpa-onnx/sherpa-onnx/kotlin-api/` if a signature is needed; read only the one file).
   **Fallback:** if v2.0.1 cannot be made to compile or its native library clashes at packaging (duplicate `.so`), stay on v1.13.8, still do A2 to A6, and say so in the final message and CHANGELOG.

A2. `SRC/ai/offline/OfflineSpeech.kt`: delete class `OfflineVoice` and every TTS import. Keep `OfflineListener` and `OfflineAudio`.

A3. `SRC/ai/offline/OfflinePacks.kt`: delete `VOICE_EN`, `VOICE_AR`, `VOICE_FA`, `VOICES`, `voiceFor`, and `OfflinePack.Kind.VOICE` (if `Kind` then has one value, delete `Kind` and the `kind` field). `ALL = LISTENERS`.

A4. `SRC/ai/offline/OfflineVoiceTest.kt`: delete `voice`, `voiceKey`, `speakOffline`, `releaseVoice` and the call to `speakOffline`. Path A now ends after "Gemma's whole answer". Add one last line to the report: `"Phone mode total (listener + first sentence): %.1f s"`. In `SRC/ai/gemma/GemmaLab.kt` `LabCleanup.run`: remove the `releaseVoice()` call.

A5. `SRC/ui/screens/OfflineVoiceCard.kt`: delete the "Voices (Piper)" title and rows. Change the card's description to: "Times an offline turn: Whisper writes down what you say, then Gemma answers." Remove the GPL sentence.

A6. Delete the folder `app/src/main/assets/espeak-ng-data/` (`git rm -r`). Add a one-time clean-up of what 3.1/3.2 left on the phone: in `MochiApp.onCreate`, on a background thread (`Thread { ... }.start()`), delete `filesDir/offline-speech/espeak-ng-data`, `voice-en`, `voice-ar`, `voice-fa` if they exist (`deleteRecursively`, inside `runCatching`).

A7. Verify after the build: `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -ci espeak` prints 0, and `strings` on `lib/arm64-v8a/libsherpa-onnx-jni.so` extracted from the APK has no `espeak` (only when on v2.0.1).

A8. `docs/PROJECT_RECORD.md` "Release blockers": replace the text with the result (resolved in 3.3 with v2.0.1, or still open if the fallback was used).

A9. Tests: in `app/src/test/java/com/example/OfflineVoiceTest.kt` remove the `voiceFor` assertions from `catalogPointsAtTheRightFiles`; keep the rest.

---

## 3. Step B: the voice detector must hear a request that starts at once

File `SRC/ai/SpeechCapture.kt`, class `VoiceActivity` (around line 133), and `SpeechCapture` (line ~34 constructs it).

B1. Noise floor no longer starts from the first frame.
- Add constructor parameter `initialFloor: Double = MIN_FLOOR`.
- Replace `private var noiseFloor = -1.0` with `private var noiseFloor = initialFloor.coerceIn(MIN_FLOOR, MAX_FLOOR)` and delete the line `if (noiseFloor < 0) noiseFloor = max(rms, MIN_FLOOR)`.
- Add `const val MAX_FLOOR = 500.0` to the companion. After every floor update add `noiseFloor = noiseFloor.coerceIn(MIN_FLOOR, MAX_FLOOR)` (replacing the existing `max(noiseFloor, MIN_FLOOR)` line). Effect: the speech threshold (floor × 3) is never above 1,500; the user's voice in the log was 3,000 to 10,700.

B2. Give it the room's real level. `SRC/ai/SoundGate.kt` has `private var floor` (running average while the gate is closed). Add `val currentFloor: Double get() = if (floor > 0) floor else 0.0`. Find how `VoiceCommandService` reaches the wake decoder's gate (Grep `SoundGate(` in `SRC/ai/`); expose the value up to the service as `wakeEngine.ambientFloor` (add a pass-through property in `OfflineWakeWord.kt`). `SpeechCapture` gets a new constructor parameter `ambientFloor: Double = 0.0` and passes `initialFloor = if (ambientFloor > 0) ambientFloor else 60.0` to `VoiceActivity`. In `VoiceCommandService.startCapture` (Grep `SpeechCapture(`) pass `ambientFloor = wakeEngine.ambientFloor`. If the gate's level and the detector's level are not on the same scale (both should be raw 16-bit RMS; confirm by reading how each computes RMS), skip B2 and leave the default.

B3. Longer lead-in so soft first sounds are kept: `PRE_ROLL_FRAMES = 15` → `25` (750 ms). Update its comment.

B4. Muted microphone is noticed. In `VoiceCommandService`, the `onNoSpeech` handler (Grep `No speech heard in`): when `loudest < 5.0`, log instead
`VoiceLog.add(VoiceLog.Kind.ERROR, "The microphone gave only silence for %.0f s (Android muted it). Restarting it.")`, then stop and restart the hub: call the existing recovery used by the watchdog for a stuck microphone (Grep `Watchdog:` and reuse that reset function; do not write a second one). Keep the rest of the handler.

B5. Tests (add to `DiagTest.kt` or the file that tests `VoiceActivity`, Grep `VoiceActivity(` in tests):
- Speech from the very first frame is detected: feed 40 frames of 5,000 then 50 frames of 80 with `VoiceActivity(endSilenceMs = 300)`; expect a `SPEECH_START` within the first 6 frames and a `SPEECH_END` later.
- Floor cap: feed 300 frames of 4,000 to a fresh detector that is forced out of speech (use a detector with `initialFloor = 500.0`), then check `lastThreshold <= 1500.0`.
- The existing `voiceActivityCountsLoudFrames` test must still pass; adjust only if B1 changes its counts, and keep its intent.

---

## 4. Step C: echo guard only when an echo is physically possible

`SRC/ai/VoiceCommandService.kt`.
- Where "Her voice reached the mic at level" is logged (line ~750), store the value: `@Volatile private var lastHerLevel = 0.0` set to `herLevelSum / herLevelFrames` (set it to 0.0 when `herLevelFrames <= 20`).
- In `onUserSaid` (line ~1098) change the first condition to `if (lastHerLevel >= ECHO_POSSIBLE_LEVEL && echoGuard.isEcho(transcript))`, with `private const val ECHO_POSSIBLE_LEVEL = 250.0` in the companion and this comment: "Nothing is recorded while she speaks, so her words can only come back as an echo tail, and only if her voice was loud at the microphone. Below the speech level it cannot be her."
- When the guard is skipped but `echoGuard.isEcho(transcript)` is true, log once: `VoiceLog.add(VoiceLog.Kind.HEARD, "Same words as hers, but her voice was too quiet at the mic to be an echo: kept")`.
- Do not change `EchoGuard` itself or its tests.

---

## 5. Step D: Whisper gets a cleaner clip

`SRC/ai/offline/PhoneListener.kt`, in `transcribe` before `listener!!.transcribe(wav)`: Whisper is more accurate with a little silence around short clips. Build the samples with 300 ms of zeros before and 500 ms after. Simplest: add to `OfflineAudio` (in `OfflineSpeech.kt`) a pure function
`fun padded(wav: ByteArray, leadMs: Int = 300, tailMs: Int = 500, rate: Int = 16_000): FloatArray` that calls `wavToFloats` and returns a new array with zeros added; add `OfflineListener.transcribe(samples: FloatArray)` and make the `ByteArray` overload call it with `padded(wav)`. Unit test: a 1,600-sample clip becomes 1,600 + 4,800 + 8,000 samples with the original in the middle.

Settings text (`SRC/ui/screens/MochiSuiteScreens.kt`, Grep `"phone" -> "On this phone:`): change to
`"On this phone (basic): works with no internet, but slower and it mishears more, especially Arabic and Persian. Download Gemma and Whisper in Gemma Lab first."`
and the Automatic text to `"Automatic (recommended): cloud when online, on the phone when offline."`

Do not add other speech recognisers in this build.

---

## 6. Step E: phone mode stops wasting time and quota

E1. `SRC/MochiApp.kt` `onTrimMemory`: release the listener only when memory is really short:
`if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) PhoneListener.release()`. (Level 20 "UI hidden" and 40 "background" arrive every time the app leaves the screen.)

E2. `SRC/ai/NaturalVoice.kt`: add `@Volatile var phoneOnly = false`. In `speak`, **after** the kept-line check (`memory`/`readDisk` block, so lines already on the phone such as "Yes?" still play instantly) and before the `restingUntil` check, add `if (phoneOnly) return@flow`. In `prepare`, return at once when `phoneOnly`.
`SRC/ui/CadenceViewModel.kt`, where `NaturalVoice.enabled = s.naturalVoice` is set (line ~245): add `com.example.ai.NaturalVoice.phoneOnly = s.aiEngine == "phone"`.

E3. `CadenceViewModel.prepareVoiceLines` (line ~615): return early when `voiceCommandService.phoneOnlyNow()`.

E4. `SRC/ui/character/CharacterController.kt` line ~327 logs "Using the phone's voice for this reply (natural voice not ready in time)" whenever the natural voice gave nothing. When `NaturalVoice.phoneOnly` is true, skip that log line (it is the plan, not a failure).

---

## 7. Step F: the Lab recording is not a conversation

`SRC/ai/VoiceCommandService.kt`, `frameSink` (Grep `wakeFeeding &&`): add `&& !holdMicForCheck` to the wake-feed condition, and add to the comment: "and not while the Lab or the Voice check is recording". Check `VoiceCheck.kt` does not rely on the wake decoder hearing anything while `holdMicForCheck` is true (Grep `holdMicForCheck` there; it measures levels through probes only). If it does rely on it, add a separate `@Volatile var suspendWakeForLab` flag set only by `OfflineVoiceTest.startRecording`/`stopAndRun`/`cancelRecording` and use that instead.

---

## 8. Step G: background microphone, one fix and the missing evidence

G1. `SRC/ui/character/FloatingCompanionService.kt`, `openApp()` (line ~459): remove `stopSelf()`; call `setWindowShown(false)` instead. The activity's `ensureRunning` hides the bubble anyway; the service must keep running because it holds the microphone right. Leave the drag-to-dismiss `stopSelf()` (the user asked her to go away).

G2. More evidence in "Mic details" (`VoiceCommandService.micDetails`, Grep `private fun micDetails`). Append:
- `service: <types>`: from the running service. Add to `FloatingCompanionService.Companion`: `fun foregroundState(): String` returning "not running" when `instance == null`; otherwise on API 29+ `instance.foregroundServiceType` rendered as "microphone", "special use", "microphone + special use", or "not foreground (0)"; plus `, mic right: <micHostAcquired>`.
- `mic permission mode: <mode>`: `AppOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_RECORD_AUDIO, Process.myUid(), packageName)` (API 29+) rendered as allowed / while in use only (`MODE_FOREGROUND`) / ignored / denied.
- `left screen <n> s ago`: keep `@Volatile var lastLeftScreenAt = 0L` in the service companion of `VoiceCommandService`, set from `MainActivity.onStop` next to the existing trail line; 0 → "on screen".

G3. Trail the service start: in `startForegroundCompat` after each `startForeground` attempt add `AppTrail.add("SERVICE", "foreground started: microphone=<true/false>, app priority <importance>")`, where importance comes from `ActivityManager.getMyMemoryState` rendered as in `micDetails` (move that rendering into a small shared function `com.example.diag.Importance.name(Int)` and use it in both places and in `ExitReasons`).

G4. Self-heal when possible: in `MainActivity.onStart` (after `ensureRunning`), if `FloatingCompanionService.foregroundState()` says the service is running but not foreground or without the microphone type, call a new `FloatingCompanionService.reassertForeground()` that runs `instance?.startForegroundCompat()` (make that function internal). The app is on screen at that moment, so Android allows it. Trail it.

Do not change anything else about the service lifecycle.

---

## 9. Step H: find what burns CPU when the app is gone

New file `SRC/diag/CpuWatch.kt`:
```kotlin
/** Every 30 s: this process's CPU use; when it is high while the app is not on screen, names the busiest threads in the trail. */
object CpuWatch {
    /** Pure, unit-tested: parses one /proc/<pid>/task/<tid>/stat line into (thread name, utime + stime ticks). */
    fun parseStat(line: String): Pair<String, Long>?
    fun sample(onScreen: Boolean)   // call from the existing 30 s MEMORY trail tick
}
```
- `sample`: `Process.getElapsedCpuTime()` delta ÷ wall-clock delta = percent of one core. Always append to the MEMORY trail line: `, cpu <n>%`.
- When `!onScreen` and percent ≥ 8: read `/proc/self/task/*/stat` (each inside `runCatching`), compute per-thread tick deltas against the previous sample (keep a `HashMap<Int, Long>` by tid), and trail the top 4: `AppTrail.add("CPU", "busy in background <n>%: <name> <ticks>, ...")`.
- `parseStat`: the name is between the first `(` and the **last** `)`; fields after it are space-separated; utime is field 14 and stime field 15 of the whole line (indexes 11 and 12 after the `)`).
- `onScreen` = `VoiceCommandService.lastLeftScreenAt == 0L` (set it back to 0 in `MainActivity.onStart`).
- Hook: in `VoiceCommandService`, the block that writes the `MEMORY` trail line (Grep `AppTrail.add("MEMORY", "app`).
- Test in `DiagTest.kt`: `parseStat("123 (Default Dispatcher (1)) S 1 2 3 4 5 6 7 8 9 10 50 25 0 0")` → `("Default Dispatcher (1)", 75)`.

Also check two likely sources and fix only what is plainly wrong:
- `FloatingCompanionService.onDestroy` cancels `serviceScope` (it does, line ~706). Check that the service's `CharacterController` render loop runs in a scope that is cancelled there too (Grep `CharacterController(` in the service). If its scope outlives the service, cancel it in `onDestroy`.
- `VoiceCommandService` 1-second tick loop: when `hosts.isEmpty()` it should do nothing except the 30 s trail. Wrap `checkOtherRecorders(); syncHub(); tickPhase()` in `if (hosts.isNotEmpty() || hub.isRunning)`.

---

## 10. Out of scope (do not build)

- Any new offline recogniser or voice; Gemma speed; Live; wake phrase matching; redesign.
- The reason Android stopped counting the service as foreground (step G gathers the evidence).
- The three "detail missing" Gemma test results.

---

## 11. Acceptance checklist and what to tell the user

Before sending:
- [ ] Compiles; all tests pass (105 old, minus removed voice assertions, plus: 2 detector tests, 1 padding test, 1 `parseStat` test).
- [ ] No `OfflineVoice`, `OfflineTts`, `espeak` anywhere in `SRC/` or assets (Grep). APK check from A7 done; result stated.
- [ ] `PhoneListener.release()` is not called for trim levels 20 and 40.
- [ ] CHANGELOG 3.3 entry in plain words; `docs/PROJECT_RECORD.md` release blocker updated.
- [ ] Committed and pushed; APK sent.

Tell the user, in plain words and short:
- What changed (crash removed with the test voice; she now hears a request said right after "Yes?"; echo guard no longer drops quiet-room replies; phone mode faster by not reloading and not calling the cloud voice; more evidence gathered for the background pause and the CPU kill).
- What is not fixed: Whisper's Arabic accuracy (limit of the model; phone mode is labelled basic), Gemma's 5 to 7 s answers, the background pause itself.
- Nothing in 3.3 was run on a phone.
- Test steps, in order:
  1. Engine on Automatic (cloud): say "Hi Elza", wait for "Yes?", speak at once. She should answer. Send the voice log.
  2. Engine "On this phone": same, in English then Arabic. Send the voice log.
  3. Gemma Lab → Hold to talk three times. It should not close. Send the copied report.
  4. Leave the app for one minute with another app open, say "Hi Elza". If she does not answer, come back and send the voice log (the "Mic details" line) and Diagnostics → "Copy app trail".
  5. Next day: Diagnostics → "Copy last close".
