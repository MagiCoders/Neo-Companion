# MochiLife Redesign Brief for Gemini

Oct 30, 2026 · @Elza

## Why it looks bad

The app looks cheap for four fixable reasons, and none of them is "Gemini didn't try hard enough." Asking it to "make everything ultra realistic" again will get the same result.

1. **The 3D characters are drawn with code, not modeled.** `Realistic3DCharacterView` paints eyes, nose and lips onto a flat Compose Canvas with math. No amount of code makes that look like a real character. Real 3D characters are sculpted meshes with textures, a skeleton and face shapes, made in tools, then loaded by a 3D renderer. A coding AI cannot sculpt; it can only load and animate a model you give it.
2. **The "glass" is not glass.** `GlassCardContainer` is a dark color at 68% opacity. Real glassmorphism blurs whatever is behind the panel. Without blur it reads as grey boxes on a dark background.
3. **Too many features on too few screens.** Ten characters, seven reactions, a mood analyzer, seven converters, a fridge chef, a bill splitter and calling all compete for space. Premium-looking apps give each screen one job and lots of empty space.
4. **Gemini was given adjectives instead of a spec.** "Astonishing", "spatial" and "aurora" produce clichés: glowing orbs, gradient borders, emoji in buttons. It also marked everything "100% Done, Verified" without ever looking at the screen. Compiling is not the same as looking good.

The fix is the same for all four: give Gemini real assets, an exact spec, one screen at a time, and make it show you a screenshot before it calls anything done.

## Decisions to lock first

Lock these five choices before Gemini writes another line, or it will keep redesigning in circles. My recommendation is in each row; change any you disagree with.

| Decision | Recommendation | Why |
| --- | --- | --- |
| Art style | Premium stylized: animated-film or high-end anime-game quality, not photoreal | Photoreal faces on a phone fall into the uncanny valley and need studio-grade face rigs. Stylized hides small flaws, and AI-made 3D models convert well to it. |
| Characters at launch | 3 finished characters, not 10 | Each one needs a model, textures, about 8 animations and mouth shapes. Three polished characters beat ten rough ones. |
| Main renderer | Real 3D models (.glb files) shown with SceneView | SceneView is the maintained Compose library for 3D on Android, built on Google's Filament engine. |
| Floating bubble and PiP | Same model at lower resolution, or a 2D Rive version | The bubble is small; a 2D animated character is lighter on battery and still looks premium. |
| Features in v1 | Companion, converter, fridge chef. Park the mood analyzer, bill splitter and calling | One job per screen is what makes an app look expensive. Parked features can return in v2. |

If you still want photoreal people, the realistic route is MetaHuman or Reallusion Character Creator, exported to .glb. MetaHumans can now be used outside Unreal Engine, free for anyone under $1 million in yearly revenue ([MetaHuman license](https://www.metahuman.com/en-US/license)). Expect much bigger files and far more tuning work.

Keep every character original. Ask image and 3D tools for your own designs, never for a known anime or movie character.

## The 3D character pipeline

Every character is made in tools by you, saved as one .glb file, and dropped into `app/src/main/assets/characters/`. Gemini's only job is to load and animate those files. This is the single biggest fix.

**Make one character (repeat for each of the 3)**

1. **Concept sheet.** Generate front, side and back views in an A-pose (arms angled down), plain grey background, even lighting, hands visible, nothing covering the face. Gemini's image model can do this. One sheet per character.
2. **Image to 3D.** Upload the sheet to an image-to-3D tool such as [Meshy](https://www.meshy.ai/3d-tools/ai-character-generator), Tripo, Hunyuan 3D or Rodin. Multi-view input gives cleaner results than one image. Generate a few versions and keep the best face.
3. **Clean up in Blender (free).** Fix the face and hands, reduce to roughly 30,000 to 60,000 triangles, and bake textures to 2048 px (1024 px for the bubble version).
4. **Rig.** Meshy's auto-rigger builds the skeleton and uses Mixamo bone names, so Mixamo animations apply directly ([Meshy auto-rigging](https://www.meshy.ai/features/ai-auto-rigging)). Mixamo or Reallusion AccuRIG also work.
5. **Body animations.** Add about 8 clips from Mixamo or Meshy's library, named exactly `idle`, `idle_look`, `listen`, `think`, `talk_a`, `talk_b`, `wave`, `happy`.
6. **Face shapes.** This is the step people skip, and why faces look dead. AI-made models arrive with no mouth or blink shapes. In Blender, add shape keys named `jawOpen`, `mouthO`, `mouthE`, `mouthSmile`, `eyeBlinkLeft`, `eyeBlinkRight`. If that is too hard, hire a freelance character rigger for this step only.
7. **Export.** glTF Binary (.glb) with textures, animations and shape keys included. Aim for under 15 MB per character.
8. **Check before handing over.** Open the .glb in a free online glTF viewer. If the animations and blink don't play there, they won't play in the app.

**Shortcut for anime characters.** [VRoid Studio](https://vroid.pixiv.net) is free and builds anime-style characters with sliders. Its VRM export already carries facial expressions and lip-sync shapes. Convert VRM to .glb with the free VRM add-on for Blender, keeping the shape keys.

**Don't use Ready Player Me.** It shut down on January 31, 2026 after Netflix bought it, so any tutorial Gemini suggests with it is outdated ([migration notes](https://avatarsdk.com/blog/2026/07/07/ready-player-me-migration-guide/)).

**How the app shows them**

- **Library:** [SceneView](https://github.com/sceneview/sceneview), which loads .glb files with skeletal and morph animations through `ModelNode`. Tell Gemini to use the latest version from Maven Central.
- **Lighting:** one HDR environment map, a soft key light, and a rim light tinted with the character's accent color. That rim light does more for "premium" than any particle effect.
- **Animation:** 250 ms crossfade between clips, blink on a random 2 to 6 second timer, mouth shapes driven by the loudness of the speech audio and smoothed so lips don't jitter.
- **Camera:** fixed, slightly below eye level, head-and-shoulders framing on the main stage.

The character should always be in one of five states, so it never freezes or snaps:

&#91;embedded content: character states · 5 states, all returning to Idle\]

Gemini builds this as the CharacterController in prompt 4. Idle is home: every other state plays out and comes back to it.

## Design spec (DESIGN.md)

Save the block below as `DESIGN.md` in the root of your project, and start every Gemini prompt with "Follow DESIGN.md exactly." A written spec with exact numbers is what stops Gemini from inventing a new look each time.

```markdown
# DESIGN.md: MochiLife visual spec
Follow this file exactly. Do not add colors, fonts, sizes or effects not listed here. If something is missing, ask before inventing it.

## Feel
Calm, cinematic, expensive. The character is the hero and the UI steps back.
Dark stage, one accent color at a time, lots of empty space.
Think premium camera or music app, not a gaming dashboard.

## Color tokens
| Token         | Value                    | Use                                   |
|---------------|--------------------------|---------------------------------------|
| bg            | #07090F                  | App background                        |
| bgRaised      | #0E121B                  | Sheets with no blur behind them       |
| textPrimary   | #F3F5F9                  | Main text                             |
| textSecondary | #F3F5F9 at 64%           | Supporting text                       |
| textTertiary  | #F3F5F9 at 40%           | Hints, disabled                       |
| hairline      | #FFFFFF at 10%           | Borders, dividers                     |
| accent        | per character (CHARACTERS.md) | Primary button, rim light, focus |
| danger        | #FF5A5F                  | Errors only                           |
No other colors. No gradient text. No neon glow on UI elements.
Only one accent-colored element per screen, besides the character's rim light.

## Glass
- Real backdrop blur with the Haze library (dev.chrisbanes.haze).
  Never fake glass with a semi-transparent color alone.
- Blur 24dp, tint #FFFFFF at 6%, 1dp hairline border, top edge highlight #FFFFFF at 16%.
- At most 2 glass surfaces on screen. Glass only over the character or the aurora;
  over plain background use bgRaised instead.
- Below Android 12: fall back to bgRaised at 92% opacity.

## Background
- Slow aurora from an AGSL RuntimeShader (Android 13+): two soft blobs in a
  desaturated version of the accent, one full drift cycle every 20 seconds.
- Fallback below Android 13: a static gradient image.
- 3% film-grain noise on top to stop color banding.

## Typography
Font: Manrope (Google Fonts). One family only.
| Style   | Size / line | Weight | Letter spacing |
|---------|-------------|--------|----------------|
| Display | 34 / 40 sp  | 600    | -0.5           |
| Title   | 22 / 28 sp  | 600    | -0.2           |
| Body    | 16 / 24 sp  | 400    | 0              |
| Label   | 13 / 18 sp  | 500    | +0.2           |
Sentence case everywhere. At most 2 text sizes inside one component.

## Spacing and shape
- 4dp grid. Screen side padding 20dp. 32dp between sections. 20dp inside cards.
- Corners: sheets 28dp, cards 20dp, buttons and chips fully rounded.
- Touch targets at least 48dp.

## Icons
Material Symbols Rounded, weight 300, 24dp. No emoji anywhere in the UI.

## Screens (one job each)
1. Stage (home): character fills the top 62%. Below it: one input bar
   (mic button + text field) and up to 3 suggestion chips.
   Avatar button top-left opens the character picker; tools button top-right.
   Nothing else on this screen.
2. Character picker: full-screen pager, one character per page, large 3D preview,
   name (Title), one-line role (Body, secondary), one accent "Choose" button.
   Category filter chips at the top.
3. Conversation: transcript in a glass bottom sheet over the stage.
   The character stays visible and keeps animating.
4. Tools: 2-column grid of tiles (icon + name). Each tool opens its own screen
   with one primary button at the bottom.
5. Tool result: preview of the result, then Share and Save.
   Progress is a thin accent bar, not a spinner.

## Never
- Characters or faces drawn with Canvas or Path.
- Emoji in buttons or labels.
- More than one accent button per screen.
- Text over a busy background without glass behind it.
- Marketing words in the UI ("100%", "ultra", "astonishing", "spatial").
```

Save this second block as `CHARACTERS.md`, and fill in the real file names once your models exist. These three cover your Medical, Anime and Chef categories.

```markdown
# CHARACTERS.md
| id     | Name         | Category | File                     | Accent  | Personality in one line            |
|--------|--------------|----------|--------------------------|---------|------------------------------------|
| elena  | Nurse Elena  | Medical  | characters/elena.glb     | #4FD1A5 | Warm, calm, speaks slowly          |
| kaito  | Kaito Storm  | Anime    | characters/kaito.glb     | #5AB8FF | Energetic, playful, quick replies  |
| marco  | Chef Marco   | Culinary | characters/marco.glb     | #F5B04C | Cheerful, practical, loves food    |
Every file contains clips: idle, idle_look, listen, think, talk_a, talk_b, wave, happy
and shape keys: jawOpen, mouthO, mouthE, mouthSmile, eyeBlinkLeft, eyeBlinkRight.
```

## Motion and effects spec

The "wow" in apps you admire comes from smooth, physical motion and one well-lit hero, not from more particles. Paste this block at the end of `DESIGN.md`.

```markdown
## Motion
Everything moves on springs, never linear. Nothing pops in or out instantly.
One thing moves at a time, and the character's motion always has priority.

| Use                         | Jetpack Compose spec                                  |
|-----------------------------|-------------------------------------------------------|
| Sheets, panels enter/exit   | spring(dampingRatio = 0.85f, stiffness = 380f)        |
| Button press                | scale to 0.96 while pressed, spring(0.6f, 600f) back  |
| Chips, character switch     | spring(0.7f, 300f)                                    |
| Fades                       | tween(200 ms, FastOutSlowInEasing)                    |
| List items appearing        | 35 ms stagger, fade in + 12dp rise                    |
| Picker -> Stage             | SharedTransitionLayout on the character preview      |
| Back gesture                | Predictive back enabled on every screen               |

## Character behaviour
- Never frozen: idle loop always running, blink every 2-6 s at random,
  slow head turn toward the last touch point (max 12 degrees).
- 250 ms crossfade between every clip. No snapping between poses.
- Talking: alternate talk_a / talk_b, and drive jawOpen + mouthO + mouthE from
  speech loudness (RMS), smoothed with 80 ms attack and 150 ms release.
- Reactions: wave, happy, think only. A single soft particle burst
  (max 40 particles, 600 ms) on happy, nowhere else.
- Remove the old Split 3x, Vortex, Burst and Dance reactions.

## Performance budget
- 60 fps on a mid-range phone with the character on screen (frame time under 16 ms).
- At most 2 blurred surfaces and 1 particle system at a time.
- Pause the 3D scene whenever it is not visible.
- PiP and floating bubble: render at half resolution, 30 fps.
- Respect the system "Remove animations" setting: keep the idle loop,
  turn transitions into plain fades.

## Haptics
- Light tick on every button and chip tap.
- HapticFeedbackConstants.CONFIRM when a task succeeds (Android 11+).
```

## How to work with Gemini

Gemini does good work when each request is small, specific and checkable. These rules matter more than any single prompt.

- **One screen or one system per prompt.** Never "fix the whole app." Big requests are where it cuts corners and then reports "100% done."
- **Attach pictures, not adjectives.** Add 2 or 3 screenshots of apps you love and say what to take from each: "spacing from image 1, bottom sheet from image 2." Mobbin and Dribbble are good places to find them.
- **Make it show you.** End every prompt with "run it on the emulator and show me a screenshot." Judge the screenshot, not its summary.
- **Give precise feedback.** Not "this is garbage", but "the title is too big, three cards compete, delete the second card, move the button 24dp lower."
- **Ask what is fake.** End each task with "list anything stubbed, mocked or not yet working." This catches features that only compile.
- **Commit after every good step.** If a later prompt breaks the look, you can roll back instead of arguing with it.
- **Repeat the ban list.** No Canvas-drawn characters, no emoji in UI, no colors outside DESIGN.md, no new features you didn't ask for.

## Copy-paste prompts, in order

Send these one at a time, and only move on when the screenshot looks right. Put `DESIGN.md` and `CHARACTERS.md` in the project before prompt 1.

**Prompt 0: honest audit (no code changes)**

```text
Do not change any code yet.
1. List every screen in the app, what is on it, and its file.
2. For every feature, say honestly whether it fully works, partly works, or is stubbed or mocked. Name the file and function.
3. List every place that draws a character or face with Canvas, Path or DrawScope.
4. List every color, font size and corner radius used in the app, with how often each appears.
Reply as tables. Don't fix anything.
```

**Prompt 1: strip it back**

```text
Follow DESIGN.md exactly.
Remove from navigation: the Canvas character renderer, the Bio-Mood analyzer, the receipt splitter and the calling bridge. Move their code into a "parked" package; don't delete it.
Remove every emoji from UI text and buttons.
Navigation should now contain only: Stage, Character picker, Tools.
Build, run on the emulator, and show me a screenshot of each screen.
Then list anything stubbed or not working.
```

**Prompt 2: design system**

```text
Follow DESIGN.md exactly.
Create the theme in ui/theme: color tokens, Manrope typography, shapes and spacing as constants. Replace every hard-coded color, size and radius in the app with these tokens.
Replace GlassCardContainer with real backdrop-blur glass using the Haze library (latest version on Maven Central), including the fallback rules in DESIGN.md.
Add a debug-only "Component gallery" screen showing every color, text style, button, chip, glass card and sheet.
Run it and show me a screenshot of the gallery.
```

**Prompt 3: load a real 3D model.** Use your first exported character, or any free rigged .glb, as `test.glb`.

```text
Add SceneView (io.github.sceneview:sceneview, latest version on Maven Central).
Build a test screen that loads assets/characters/test.glb and:
- plays its animations by name with a 250 ms crossfade,
- prints all animation names and morph target (shape key) names to Logcat,
- lights it with an HDR environment map, a soft key light and a rim light in the accent color,
- uses a fixed camera slightly below eye level with head-and-shoulders framing.
Do not draw anything with Canvas. Do not build model geometry in code.
Run it and show me a screenshot and the Logcat output.
```

**Prompt 4: make the character feel alive**

```text
Follow DESIGN.md and CHARACTERS.md exactly.
Create a CharacterController with five states: Idle, Listening, Thinking, Talking, Reacting.
- Idle: loop "idle"; play "idle_look" every 8-15 s at random.
- Blink with eyeBlinkLeft/eyeBlinkRight every 2-6 s at random (120 ms close, 160 ms open).
- Listening while the mic is open; Thinking while waiting for the AI reply; Talking while speech audio plays; Reacting plays "wave" or "happy" once, then returns to Idle.
- Talking: alternate talk_a and talk_b; drive jawOpen, mouthO and mouthE from speech loudness (RMS), smoothed with 80 ms attack and 150 ms release. If TextToSpeech can't expose its audio, synthesize to a file, play it with AudioTrack, and read RMS from the buffer.
- Turn the head toward the last touch point, max 12 degrees, spring-smoothed.
- 250 ms crossfade on every clip change.
API: controller.setState(...) and controller.react("wave").
Show me a screenshot of each state.
```

**Prompt 5: Stage screen.** Attach 2 or 3 reference screenshots with this one.

```text
Follow DESIGN.md exactly. Rebuild only the Stage screen.
Character fills the top 62% over the aurora background. Below it: the input bar (mic + text field) and up to 3 suggestion chips. Avatar button top-left opens the picker; tools button top-right. Nothing else.
The transcript opens as a glass bottom sheet over the stage while the character keeps animating.
From the attached references take: [say what to take from each image].
Use the motion values in DESIGN.md.
Run it and show me three screenshots: empty, listening, talking with the sheet open.
```

**Prompt 6: character picker**

```text
Follow DESIGN.md exactly. Build only the Character picker.
Full-screen pager, one character per page from CHARACTERS.md: live 3D preview playing "idle", name, one-line role, one accent "Choose" button. Category chips at the top filter the pager.
Choosing uses SharedTransitionLayout so the preview flies into the Stage. The aurora crossfades to the new accent color over 600 ms.
Show me screenshots and describe the transition frame by frame.
```

**Prompt 7: tools, one at a time**

```text
Follow DESIGN.md exactly. Build only the Tools grid and one tool: Image to PDF.
Grid: 2 columns, tiles with icon + name.
Tool screen: pick images, preview, one primary button, thin accent progress bar, then Share and Save.
I will ask for the other tools one at a time using this same template.
```

**Prompt 8: floating bubble and PiP**

```text
Follow DESIGN.md exactly. Polish only the floating bubble and Picture-in-Picture.
Use the same character model at half resolution and 30 fps, Idle and Talking states only. Pause rendering whenever the bubble is hidden.
Run for 5 minutes and report frame times from the Android Studio profiler.
```

**For the image model: character concept sheet (step 1 of the pipeline)**

```text
Character turnaround sheet of an original character for a mobile app: [name], [age range, outfit, hair, main colors].
Three views side by side: front, side, back. A-pose, arms 45 degrees down, hands open and visible.
Premium stylized 3D animated-film look, clean shapes, no tiny accessories.
Plain mid-grey background, flat even studio lighting, no shadows, no text, no props, full body visible.
```

## Checklist for each round

Tick these off from Gemini's screenshots before accepting its work. Any unticked box means "not done," whatever its summary says.

- [ ] Every character comes from a .glb file; nothing face-like is drawn in code
- [ ] The character is never frozen: it breathes, blinks and glances while idle
- [ ] Clip changes crossfade smoothly, with no snapping between poses
- [ ] The mouth moves with the voice and stops when the voice stops
- [ ] Only colors, fonts and sizes from DESIGN.md appear on screen
- [ ] Glass panels visibly blur what is behind them
- [ ] No emoji or marketing words anywhere in the UI
- [ ] Each screen has one obvious job and at most one accent button
- [ ] Screens move with springs and the shared transition, not hard cuts
- [ ] Scrolling and character animation stay smooth on a mid-range phone
- [ ] Gemini listed what is stubbed or mocked, and you agree with the list
