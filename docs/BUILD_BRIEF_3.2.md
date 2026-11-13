# MochiLife 3.2 build brief

**For the building model (Sonnet or Opus).** This file is the whole instruction. Do not read the earlier conversation or the transcript; everything needed is here. Read only the source files named in each step. Do not redesign; build what is written. If a step cannot be done as written, stop that step, say why in the final message, and finish the others.

Written 2026-11-13 after the user's 3.1 test on a Samsung Galaxy S22+ (8 GB).

---

## 0. Ground rules (apply to every step)

**Repo.** `/home/claude/neo-companion`, branch `main`, remote `MagiCoders/Neo-Companion`. Source root: `app/src/main/java/com/example/` (called `SRC/` below). Tests: `app/src/test/java/com/example/`.

**Never touch.** `signingConfigs` / `signingConfig` in `app/build.gradle.kts`; keystores; `.env`; `local.properties`.

**Code rules from the user.** No mock code, no placeholder data, no hard-coded fake results, no dead code. Every catch either handles or logs. No user speech, contact names or message text in any crash or trail record (events only).

**Build environment.**
```
cd /home/claude/neo-companion
PORT=$(echo $JAVA_TOOL_OPTIONS | grep -o "https.proxyPort=[0-9]*" | cut -d= -f2); sed -i "s/proxyPort=[0-9]*/proxyPort=$PORT/g" ~/.gradle/gradle.properties
# quick compile check while working
timeout 900 ./gradlew :app:compileDebugKotlin --console=plain --max-workers=1 -q 2>&1 | grep -E "^e:|error:" | head -20
# full run at the end (tests need the Firebase emulators)
./gradlew --stop -q
timeout 1700 /home/claude/fbtools/node_modules/.bin/firebase emulators:exec --only firestore,auth --project demo-test "./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain --max-workers=1" > /tmp/claude-0/build32.log 2>&1; echo exit=$?
```
Count tests from the XML in `app/build/test-results/testDebugUnitTest/` (3.1 had 98, all passing). Tests that call `GemmaEngine` JSON parsing must run under Robolectric (`@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])`) because `org.json` is an Android class.

**Editing large files.** `VoiceCommandService.kt` (1,667 lines), `CadenceViewModel.kt` (1,612), `MochiSuiteScreens.kt` (3,175): never read them whole. Use Grep for the anchor named in the step, then Read 40 to 80 lines around it.

**Delivery (the user's standing rule).**
1. `app/build.gradle.kts`: `versionCode = 23`, `versionName = "3.2"`.
2. New entry at the top of `CHANGELOG.md` (format: `## 3.2 (build 23)`, one summary line, then bullets in plain words; see the 3.1 entry).
3. Full run passes; copy `app/build/outputs/apk/debug/app-debug.apk` to `/home/claude/MochiLife-v3.2-build23.apk`.
4. Commit and push: `git -c http.postBuffer=524288000 push -q origin main`. End the commit message with the attribution lines from the session's current system reminder.
5. Send the APK to the user with SendUserFile.
6. Final message: what changed, what is untested on a phone, and the test steps from section 9. Plain words, short.

---

## 1. What the user saw in 3.1 (the facts this build answers)

| # | Observation (from the user's phone) | Cause |
|---|---|---|
| 1 | Phone mode: she says "Yes?", 1.4 to 1.7 s of sound goes to Gemma, 5 s later "Gemma heard nothing". Three times in a row. | `GemmaEngine.hear` asks Gemma to transcribe, answer and format JSON in one call, then discards the whole reply when the `heard` field is empty or missing. The raw reply is not logged, so the exact failure is unknown. |
| 2 | While this ran: memory 1,640 MB (normal 722), 3D at 4 fps (normal 34), wake listener busy 75% (normal 21%). | Gemma on CPU, plus Whisper and Piper test models left loaded, with 3D and wake decoding running at full rate. |
| 3 | "No speech heard in 7 s (loudest sound 4232, speech needs 250)". | Unknown. Loud sound but the voice detector never fired. Needs one more number in the log (step 7). |
| 4 | Gemma Lab → Hold to talk often closes the app. | Unknown. The app has no on-phone crash record. Engine crashes and low-memory kills are recorded nowhere. |
| 5 | Offline test: Whisper base wrote "Hi, how are you?" correctly in 0.9 s for 3.4 s of speech. | Measured. Good. |
| 6 | 16-request Gemma test: 16 of 16 understood, 3.0 s average. | Measured. Text path works. |

**Decision.** Phone mode listens with Whisper and answers with the text path: on-phone commands first, Gemma second. Gemma hearing audio directly becomes the fallback only. Expected turn: about 1 s for commands, about 4 s for Gemma answers.

---

## 2. Step A: crash recorder and app event trail (build this first)

Goal: after any unexpected close, Diagnostics shows why, with enough context to fix it. Everything stays on the phone; the user copies it by hand.

### A1. New file `SRC/diag/AppTrail.kt`

```kotlin
package com.example.diag

/** Pure ring of trail lines (unit-tested). */
class TrailBuffer(private val capacity: Int = 200) {
    @Synchronized fun add(line: String)
    @Synchronized fun tail(n: Int): List<String>
}

/**
 * A short rolling record of what the app was doing: screens, model loads, downloads, memory.
 * Kept in memory and appended to a small file so it survives a crash or a kill.
 * Events only: never what the user said, typed, or any contact or message.
 */
object AppTrail {
    const val START_MARK = "---- app start"
    fun init(context: Context)              // idempotent; creates filesDir/diag/, writes "$START_MARK v<versionName> (<versionCode>) ----"
    fun add(tag: String, text: String)      // "HH:mm:ss.SSS TAG text" (text cut to 160 chars)
    fun tail(n: Int = 60): List<String>
    fun previousRunTail(n: Int = 40): List<String>   // lines before the latest START_MARK
}
```
Details:
- File `filesDir/diag/trail.log`. Writes go through one single-thread executor (`Executors.newSingleThreadExecutor`, thread name `trail`), opened in append mode per write, no buffering kept open. When the file passes 64 KB, rename it to `trail.prev.log` (replacing any old one) and start a new file.
- `previousRunTail` reads `trail.log` (and `trail.prev.log` if the start mark is not found in the first), splits on `START_MARK`, returns the last `n` lines of the run before the current one. Put the splitting in a pure function `splitRuns(lines: List<String>): List<List<String>>` and unit-test it.
- For tags `SCREEN`, `LAB`, `GEMMA`, `OFFLINE`, `LIVE` also call (API 30+) `ActivityManager.setProcessStateSummary("<tag> <text>".toByteArray().copyOf(min(size,128)))` inside `runCatching`. Android returns this with the exit reason even after an engine crash or a kill.

### A2. New file `SRC/diag/CrashRecorder.kt`

```kotlin
object CrashRecorder {
    fun install(context: Context)     // call once, first thing in the Application
    fun lastCrashText(context: Context): String?   // contents of filesDir/diag/last_crash.txt or null
    fun clear(context: Context)
}
```
- `install`: keep `val previous = Thread.getDefaultUncaughtExceptionHandler()` (Crashlytics is already installed by its content provider; it must still run). Set a handler that, inside `try`, writes `filesDir/diag/last_crash.txt` synchronously (`FileOutputStream`, then `fd.sync()`), then always calls `previous?.uncaughtException(t, e)`.
- File contents: time (ISO), app version and build, Android version and device model, thread name, `Log.getStackTraceString(e)` cut to 6,000 chars, then `AppTrail.tail(40)`.
- Do not catch `Error` differently; record everything.

### A3. New file `SRC/diag/ExitReasons.kt`

```kotlin
object ExitReasons {
    /** Pure, unit-tested. */
    fun reasonName(reason: Int): String
    /** Pure, unit-tested: readable lines from a native crash record (protobuf bytes). */
    fun readableFrames(bytes: ByteArray, max: Int = 40): List<String>
    /** The last few times the app's process ended, newest first, as text. API 30+. */
    fun report(context: Context, count: Int = 3): String
}
```
- `report`: below API 30 return "Exit reasons need Android 11 or newer." Otherwise `activityManager.getHistoricalProcessExitReasons(context.packageName, 0, count)`. For each `ApplicationExitInfo`: time, `reasonName(reason)`, `description`, `importance` (map 100 → "on screen", 125 → "listening service", 200 → "visible", others → number), `pss` and `rss` in MB, and `processStateSummary` decoded as text ("Doing: ...").
- `reasonName` mapping (use the `ApplicationExitInfo.REASON_*` constants): `CRASH` → "app crash (Kotlin/Java)", `CRASH_NATIVE` → "engine crash (native code)", `LOW_MEMORY` → "killed: phone out of memory", `ANR` → "frozen (not responding)", `EXCESSIVE_RESOURCE_USAGE` → "killed: used too much", `SIGNALED` → "killed by the system (signal)", `USER_REQUESTED` → "closed by you", `USER_STOPPED` → "force-stopped", `DEPENDENCY_DIED` → "a system part it needed stopped", `INITIALIZATION_FAILURE` → "failed to start", `PERMISSION_CHANGE` → "a permission changed", `EXIT_SELF` → "closed itself", `FREEZER` → "killed while frozen", `OTHER` and anything else → "other (<number>)".
- For `CRASH_NATIVE` and `ANR` on API 31+: read `info.traceInputStream` (may be null) fully, capped at 512 KB. For ANR the stream is text: keep the first 30 lines. For native it is a protobuf tombstone: pass to `readableFrames`.
- `readableFrames`: walk the bytes, collect runs of printable ASCII (0x20..0x7E) of length ≥ 6, keep those that contain any of `.so`, `SIG`, `signal`, `abort`, `Abort`, `fault`, `sherpa`, `onnx`, `litert`, `vosk`, `filament`, de-duplicate, return the first `max`. This needs no protobuf dependency.

### A4. Application class

There is no `Application` subclass today. Create `SRC/MochiApp.kt`:
```kotlin
class MochiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashRecorder.install(this)
        AppTrail.init(this)
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppTrail.add("MEMORY", "system asks to trim, level $level")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) PhoneListener.release()   // step B
    }
}
```
Register it: in `app/src/main/AndroidManifest.xml` add `android:name=".MochiApp"` to the `<application` tag (line ~99; namespace is `com.example`). Check nothing else declares an Application.

### A5. Where to add trail events (one line each; `AppTrail.add(tag, text)`)

| Tag | Where | Text |
|---|---|---|
| `SCREEN` | `CadenceViewModel.navigateHash(route)` (line ~79) | the route |
| `APP` | `MainActivity.onStart`, `onStop` | "on screen" / "left screen" |
| `SERVICE` | `FloatingCompanionService.onCreate`, `onDestroy` | "listening service started/stopped" |
| `GEMMA` | `GemmaRunner.load` (start, loaded with seconds and `hearsAudio`, failed with reason), `unload` | as described |
| `OFFLINE` | `OfflinePackDownloader.start`, on `Ready`, on `Failed`; `PhoneListener` load/release; `OfflineVoice.load` | pack id and outcome |
| `LAB` | `OfflineVoiceTest.startRecording`, `stopAndRun`, each `line(...)` first 80 chars **except** lines that quote speech (those start with `A · ` and contain `wrote "` or `first sentence`, and `B · Gemma heard`): for those log only the timing part, not the quoted text |
| `LIVE` | `VoiceCommandService.beginLive`, `endLive` | "started" / "ended" |
| `MEMORY` | In `VoiceCommandService` next to the existing `PerfMeter.sample()` call (line ~202), every 6th sample (about every 30 s) | `PerfMeter.read()` memory MB, plus `ActivityManager.MemoryInfo.availMem` MB and `lowMemory` |
| `VOICE` | `VoiceLog.add`: for kinds `WAKE`, `ERROR` only | the text (these kinds never contain speech; verify by reading the `VoiceLog.add(VoiceLog.Kind.WAKE` and `ERROR` call sites with Grep and skip mirroring if any quotes user speech) |

### A6. Diagnostics UI

In `SRC/ui/screens/MochiSuiteScreens.kt`, Diagnostics screen, insert a "Last close" section just above the existing `Text("Performance", ...)` (line ~3081, after a `HairlineDivider(p.hairline)`), using the existing `DiagnosticsButton` and `clipboard`:
- Title "Last close".
- On open (`LaunchedEffect(Unit)` on `Dispatchers.IO`) build one string:
  ```
  Why the app last closed
  <ExitReasons.report(context)>

  Last app error
  <CrashRecorder.lastCrashText(context) ?: "None recorded.">

  What it was doing before
  <AppTrail.previousRunTail(40) joined by newline, or "No earlier run recorded.">
  ```
- Show the first 25 lines in `MochiType.Label`, `p.textSecondary`; buttons "Copy last close" (copies the full string) and "Clear" (calls `CrashRecorder.clear`).
- Second button row: "Copy app trail" copies `AppTrail.tail(120)`.

### A7. Tests (`app/src/test/java/com/example/DiagTest.kt`, plain JUnit)
- `TrailBuffer` keeps only the newest `capacity` lines, `tail(n)` order is oldest to newest.
- `splitRuns` with two start marks returns two runs; previous run is the first.
- `ExitReasons.reasonName` for `CRASH`, `CRASH_NATIVE`, `LOW_MEMORY`, `ANR`, and an unknown number.
- `readableFrames` on bytes containing `"\u0001\u0002libsherpa-onnx-jni.so\u0000xx\u0003SIGSEGV fault addr"` returns both strings and drops short or unrelated runs.

---

## 3. Step B: Whisper is the phone-mode listener

### B1. New file `SRC/ai/offline/PhoneListener.kt`

```kotlin
/**
 * The on-phone listener for "On this phone" mode: Whisper turns Mochi's own recording into text.
 * One model loaded at a time, all engine calls on one thread (the engine is not thread-safe).
 */
object PhoneListener {
    sealed class Result {
        data class Text(val text: String, val seconds: Double, val pack: OfflinePack) : Result()   // text may be ""
        data class Unavailable(val reason: String) : Result()
    }
    /** Pure, unit-tested. English: base if ready, else small. Arabic/Persian/other: small if ready, else base. null if none. */
    fun choosePack(language: String, readyIds: Set<String>): OfflinePack?
    fun isAvailable(context: Context): Boolean          // any LISTENER pack fully downloaded
    suspend fun transcribe(context: Context, wav: ByteArray, languageTag: String, forcePack: OfflinePack? = null): Result
    fun release()                                       // frees the model; safe from any thread
}
```
Details:
- Dispatcher: `Executors.newSingleThreadExecutor { Thread(it, "offline-speech") }.asCoroutineDispatcher()`. `load`, `transcribe` and `release` bodies all run on it (`release` posts to it).
- Whisper language code: `languageTag.substringBefore('-').lowercase()`; use it if it is one of `en, ar, fa, tr, ur, fr, de, es, hi, ru, it, pt, id, ku`; otherwise `"en"`.
- Cache key `"<pack.id>|<lang>"`; on a different key release the old `OfflineListener` first, then `OfflineListener.load(dir, pack, lang)` (existing, in `OfflineSpeech.kt`). Pack directory: `File(context.filesDir, "offline-speech/<pack.id>")` and readiness: all `pack.files` exist there. Expose these two as small helpers so `OfflinePackDownloader` and `PhoneListener` share them (move `dir`/`isReady` logic into `OfflineCatalog` functions taking `filesDir`; keep `OfflinePackDownloader.dir/isReady` delegating).
- After `transcribe`, pass the text through `WhisperText.clean` (B2).
- Idle unload: keep a `Job` that calls `release()` 5 minutes after the last use; cancel and restart it on each use (use a `CoroutineScope(SupervisorJob() + Dispatchers.Default)`).
- Wrap the engine call in `PhoneLoad.during { ... }` (step D1).
- Trail: `AppTrail.add("OFFLINE", "listener <pack.id> <lang> loaded in x s")`, and on release.
- Any `Throwable` from load or decode → `Result.Unavailable("The on-phone listener failed (<class>).")` and release the model.

### B2. Pure text clean-up, same file or `WhisperText.kt`

```kotlin
object WhisperText {
    /** "" when Whisper produced nothing real: blank, bracketed tags, or its known silence phrases. Unit-tested. */
    fun clean(raw: String): String
}
```
Rules, in order: trim; remove any `[...]` and `(...)` segments (e.g. `[BLANK_AUDIO]`, `(music)`); trim again; if empty or only punctuation → `""`; if the lower-cased text without trailing punctuation equals one of `"you"`, `"thank you"`, `"thanks for watching"`, `"thank you for watching"`, `"bye"`, `"."`, `"ترجمة نانسي قنقر"`, `"اشتركوا في القناة"`, `"شكرا"`, `"موسیقی"` **and** nothing else was said → `""`. Otherwise return the trimmed text. (These are Whisper's well-known outputs on silence or noise.) Note `"thank you"` and `"bye"` are real things a user may say; that is accepted for now and is logged, see B3.

### B3. `VoiceCommandService.kt` changes

Anchor: `suspend fun understandAudio(` (line ~1414). Replace the `if (phoneOnlyNow()) { ... }` block with:

```kotlin
if (phoneOnlyNow()) {
    if (com.example.ai.offline.PhoneListener.isAvailable(appContext)) {
        when (val r = com.example.ai.offline.PhoneListener.transcribe(appContext, wav, recognitionLanguageTag)) {
            is PhoneListener.Result.Unavailable -> throw AiUnavailableException(r.reason)
            is PhoneListener.Result.Text -> {
                if (r.text.isEmpty()) {
                    VoiceLog.add(VoiceLog.Kind.IGNORED, "Whisper found no words (%.1f s)".format(r.seconds))
                    return "" to null
                }
                VoiceLog.add(VoiceLog.Kind.HEARD, "Whisper wrote \"${r.text.take(80)}\" in %.1f s (${r.pack.id})".format(r.seconds))
                LocalCommands.parse(r.text, recognitionLanguageTag)?.let { local ->
                    VoiceLog.add(VoiceLog.Kind.AI, "Understood on the phone (no AI used): ${local.actionType.label}")
                    lastEngine = "On phone"
                    return r.text to local
                }
                lastEngine = "Gemma"
                return when (val a = GemmaEngine.answer(appContext, r.text, companion.displayName, preferredLanguage, history)) {
                    is GemmaEngine.Result.Answer -> r.text to a.turn
                    is GemmaEngine.Result.Unavailable -> throw AiUnavailableException(a.reason)
                }
            }
        }
    }
    // No Whisper downloaded: Gemma hears the recording itself (slower, less reliable).
    lastEngine = "Gemma"
    ... existing GemmaEngine.hear(...) branch unchanged ...
}
```
`recognitionLanguageTag` is an existing property of the service (the floating service reads `voice.recognitionLanguageTag`). Confirm its name with Grep before use.

Anchor: `private fun useAiListening()` (line ~498). Phone branch becomes:
`PhoneListener.isAvailable(appContext) || GemmaEngine.canHear(appContext)`. Update the KDoc: "in phone mode Whisper writes it down (or Gemma hears it)".

Callers need no change: `CadenceViewModel.sendAudioToMochi` (line ~1329) and `FloatingCompanionService` (line ~351) already take `(heard, turn)` and already set the engine label from `voiceCommandService.lastEngine` (the ViewModel does at "_lastEngine.value = voiceCommandService.lastEngine"). Check that the Stage engine label shows "On phone" for the new `lastEngine` value "On phone" (Grep `lastEngine` in `CadenceViewModel.kt` and `engineLabel` in `StageScreen.kt`); add the mapping if it only handles "Gemma" and "Cloud".

### B4. `GemmaEngine.hear` never drops a reply silently (`SRC/ai/gemma/GemmaEngine.kt`)

- Change `parseHeard(answer: String): String` to return `String?`: `null` when there is no JSON object or the object has no `heard` key; the trimmed value (possibly `""`) when the key exists. Use `JSONObject.has("heard")`.
- In `hear`, right after the model call: `VoiceLog.add(VoiceLog.Kind.AI, "Gemma replied: ${raw.replace('\n',' ').take(120)}")`.
- Then:
  - `heard == ""` (key present, empty) → nothing for her (existing behaviour).
  - `heard == null` and `parse(raw).first` (the reply) is not blank → return `Heard.Turn("(voice)", toTurn(raw), seconds)` and log `"Gemma answered without writing down the words"`.
  - `heard == null` and reply blank → nothing for her.
  - otherwise as today.
- Update the existing test `gemmaHeardIsRead` in `OfflineVoiceTest.kt` (test file): expects `"call ali"`, `""` for `{"heard": ""}`, and `null` for `"no json here"` and for `{"reply":"hi"}`.

### B5. Settings shows what phone mode will use

`MochiSuiteScreens.kt`, AI engine chips (Grep `settings.aiEngine`, line ~831). Under the chips, when the selected value is `"phone"` or `"auto"`, add one `MochiType.Label` line built on `Dispatchers.IO` via `produceState`:
- Brain: "Gemma 4 E2B downloaded" / "E4B downloaded" / "No on-phone AI downloaded" (`GemmaEngine.isDownloaded`, `downloadedModel` label; make `downloadedModel` internal if it is private).
- Listener: "Whisper base/small downloaded" / "No listener downloaded: Gemma will listen itself (slower)".
- If either is missing: "Download them in Gemma Lab." in the warning colour `Color(0xFFE0A050)`.

### B6. The Lab test uses the same listener

`SRC/ai/offline/OfflineVoiceTest.kt`: remove its own `listener`/`listenerKey` fields and the `OfflineListener.load` call. Path A calls `PhoneListener.transcribe(appContext, wav, language, forcePack = listenerPack)` and prints seconds from the result (note first-use load time is included; print "includes loading" when the call took over 2× the audio length... simpler: `PhoneListener` returns `loadSeconds` in `Result.Text`; add that field, 0.0 when already loaded, and print "Listener loaded in x s" when > 0). Voice (`OfflineVoice`) load and `speak` move onto the same `offline-speech` dispatcher (expose it as `PhoneListener.dispatcher` internal, or a shared `OfflineSpeechThread.dispatcher` object; prefer the shared object and use it from both).

### B7. Tests (`OfflineVoiceTest.kt` test file)
- `choosePack`: en + {base, small} → base; ar + {base, small} → small; ar + {base} → base; fa + {} → null.
- `WhisperText.clean`: `" [BLANK_AUDIO] "` → `""`; `"(music)"` → `""`; `"Thank you."` → `""`; `"Thank you, call Ali"` → unchanged; `"شلونك اليوم؟"` → unchanged; `"..."` → `""`.

---

## 4. Step C: fix the Hold to talk button (known defects, whatever the crash turns out to be)

File `SRC/ui/screens/OfflineVoiceCard.kt`, the hold-to-talk `Text` with `.pointerInput(language, listenerId, busy)`.

Defect 1: the gesture block is keyed on `language`, `listenerId`, `busy`. If any changes during a press the gesture coroutine is cancelled, `stopAndRun` never runs, and `voiceService.holdMicForCheck` stays `true` (microphone held open for good).

Fix:
```kotlin
val currentLanguage by rememberUpdatedState(language)
val currentListener by rememberUpdatedState(listener)
val currentBusy by rememberUpdatedState(busy)
...
.pointerInput(Unit) {
    detectTapGestures(onPress = {
        if (currentBusy) return@detectTapGestures
        test.startRecording()
        val released = try { tryAwaitRelease() } catch (c: CancellationException) { test.cancelRecording(); throw c }
        if (released) test.stopAndRun(currentLanguage, currentListener) else test.cancelRecording()
    })
}
```
Add `OfflineVoiceTest.cancelRecording()`: remove the probe, `holdMicForCheck = false`, `_recording.value = false`, reset the buffer, report "Cancelled." Also add `DisposableEffect(Unit) { onDispose { test.cancelRecording() } }` in the card.

Defect 2: engine objects used from several threads (`Dispatchers.IO` for load, `Dispatchers.Default` for transcribe, UI thread for `release`). Fixed by B6 (one thread).

Defect 3: no microphone permission or no frames arriving gives "Too short" with no reason. In `startRecording`, if `ContextCompat.checkSelfPermission(RECORD_AUDIO)` is not granted, set the report to "Microphone permission is off. Allow it in the home screen first." and return. In `stopAndRun`, when fewer than 0.5 s of sound arrived, report "No sound reached the test (%.1f s). Open the home screen once so her microphone is running, then try again."

Defect 4: every exception in the probe is unguarded on the microphone thread. Wrap the probe body in `try { ... } catch (t: Throwable) { AppTrail.add("LAB", "probe error ${t.javaClass.simpleName}") }`.

Leave on dispose of the Lab screen (`GemmaLabScreen`): `DisposableEffect(Unit) { onDispose { ... } }` that (a) calls `OfflineVoiceLab.get(context).releaseVoice()` (new: stops and releases the Piper voice only), (b) calls `PhoneListener.release()` unless `VoiceCommandService.shared(context).phoneOnlyNow()`, (c) unloads Gemma (`scope` is gone on dispose, so launch on `GlobalScope`-free alternative: add `GemmaLab.unloadIfIdle(context)` that launches on its own `CoroutineScope(SupervisorJob() + Dispatchers.Default)`) unless `phoneOnlyNow()`.

---

## 5. Step D: less load while she thinks on the phone

### D1. New file `SRC/ai/PhoneLoad.kt`
```kotlin
/** True while a heavy on-phone model (Whisper or Gemma) is working. Lets the 3D drawing and the wake listener step aside. */
object PhoneLoad {
    private val count = java.util.concurrent.atomic.AtomicInteger(0)
    val busy: Boolean get() = count.get() > 0
    inline fun <T> during(block: () -> T): T { enter(); try { return block() } finally { exit() } }
    fun enter(); fun exit()      // exit never goes below 0
}
```
Use it in: `PhoneListener.transcribe` (around the engine call), `GemmaRunner.oneOff` (around `block(conv)`), `GemmaRunner.send` and `stream` (enter before `emitAll`, exit in `finally`), and `GemmaRunner.load`.

### D2. 3D drawing
`SRC/ui/character/CharacterController.kt`, render loop (Grep `delay(frameDelayMs)`, line ~125): `delay(if (com.example.ai.PhoneLoad.busy) maxOf(frameDelayMs, 100L) else frameDelayMs)`. That is 10 fps while thinking; the Thinking animation still moves.

### D3. Wake listener
`SRC/ai/VoiceCommandService.kt`, in `frameSink` (Grep `wakeFeeding`): feed the wake decoder only when `wakeFeeding && (!PhoneLoad.busy || assistantSpeaking)`. Effect: while Whisper or Gemma is working, her name does not interrupt (a tap still does); while she is speaking it still does. Add this sentence to the KDoc there.

### D4. "Thinking" is visible
- `FloatingCompanionService` (Grep `isThinking = true`, line ~344): when `voice.phoneOnlyNow()`, call the existing `showBubble("Thinking…", autoHideMs = 15_000L)` instead of leaving `bubbleText = null`. Localise with the service's existing language helper if one is used for other bubble texts in that function; otherwise English only and note it.
- Stage: confirm `VoicePhase.THINKING` ("Thinking") is shown during `sendAudioToMochi` (it sets `_isCompanionThinking`). No change expected; verify with Grep `VoicePhase.THINKING` only if cheap.
- No beep and no filler speech (the user's rule).

---

## 6. Step E: one more number for the "loud but no speech" case

`SRC/ai/SpeechCapture.kt`, class `VoiceActivity` (line ~131): add `var framesAbove = 0` (count of frames with `rms > threshold`) and `var longestRunMs = 0` (longest run of consecutive frames above threshold × frame length in ms; the frame length is already known to the class or to `SpeechCapture`, Grep `FRAME_MS` or the 30 ms constant). Pass both through the existing `onNoSpeech` callback (line ~76: add two parameters) to `VoiceCommandService` (Grep `No speech heard in`, line ~1050) and extend that log line: `"... (loudest sound 4232, speech needs 250, above that for N frames, longest run M ms)"`. Unit test in `OneMicTest.kt` style: feed 10 quiet, 5 loud, 3 quiet, 8 loud frames; expect `framesAbove == 13`, `longestRunMs == 8 * frameMs`. Diagnostics only; do not change the detector's decisions.

---

## 7. Step F: licence note that must stay visible

`gradle/libs.versions.toml` uses `sherpaOnnx = "v1.13.8"`. That version's `libsherpa-onnx-jni.so` links espeak-ng (GPL-3.0). Whisper itself (MIT) and sherpa-onnx (Apache-2.0) are fine, but **this binary must not ship on Google Play.** Keep 3.2 on v1.13.8 (test builds only). Add to `CHANGELOG.md` under 3.2 and to `docs/PROJECT_RECORD.md` under a "Release blockers" heading (create it if missing):

> Before any Play release: move sherpa-onnx to v2.0.x (no espeak-ng; the Whisper API used here is the same) and remove the Piper voice test, or replace its pronunciation step with a non-GPL one. Verify with `unzip -l` that no `espeak` file or symbol is in the APK.

Do not do the migration in 3.2.

---

## 8. Out of scope for 3.2 (do not build)

- Fixing the cause of the Hold-to-talk crash beyond step C: the cause is unknown until the user sends "Copy last close" from 3.2.
- Crashlytics NDK, Play Integrity, redesign, new features.
- The three "detail missing" results in the Gemma test (Spotify, 6:30, Persian 7): alarms and app opening go through on-phone commands in real use.
- Background microphone pauses: 3.1 added "Mic details" logging; wait for the user's log.
- Any change to Live, wake phrase matching, echo guard or the cloud path.

---

## 9. Acceptance checklist and what to tell the user

Before sending:
- [ ] Compiles; all tests pass (98 old + new: DiagTest ≥ 4, PhoneListener/WhisperText ≥ 2, VAD counters 1, updated `gemmaHeardIsRead`).
- [ ] `AndroidManifest.xml` has `android:name=".MochiApp"`; app class installs recorder before anything else.
- [ ] Grep shows no `AppTrail.add` call passing user speech (`heard`, `text` of a request, contact names).
- [ ] `OfflineVoiceTest` no longer loads its own listener; all sherpa-onnx calls run on the `offline-speech` thread.
- [ ] `git status` clean after commit; pushed.

Tell the user to test, in this order, and send back the copied text:
1. Settings → AI engine → On this phone. Check the new line under it says Gemma and Whisper are downloaded.
2. Home screen: say "Hi Elza", then "what time is it" (expect about 1 s, engine label "On phone"), then "Hi Elza", "how are you" (expect about 4 s, label "Gemma"). Send the voice log.
3. Same in Arabic if they use it.
4. Gemma Lab → Hold to talk several times. If the app closes: reopen it, Diagnostics → "Copy last close", paste it. That text is what identifies the crash.
5. Diagnostics → Performance line during a phone-mode turn (memory and 3D fps).

State plainly in the final message: nothing in 3.2 was run on a phone; Whisper base accuracy in Arabic and Persian is unmeasured; the crash is not fixed, only recorded and the known button defects removed.
