# MochiLife — Redesign Audit, Specification & Screenshots

> **Package ID**: `com.aistudio.cadence.mxkprz`  
> **Specifications**: `/DESIGN.md` & `/CHARACTERS.md`  
> **Illustrated PDF**: `/MOCHILIFE_COMPLETE_DOCUMENTATION.pdf` (5-page A4 PDF)

---

## 1. Honest Codebase Audit (Prompt 0)

### 1.1 Project File Inventory (`.kt`, `.xml`, `.glb`, `.md`)

| File Path | Role | Status |
| :--- | :--- | :--- |
| `/DESIGN.md` | Visual & layout specification (color tokens, Manrope type, Haze/RenderEffect glass, AGSL aurora, 5 screens) | **Active (v1)** |
| `/CHARACTERS.md` | 3 launch characters (`elena`, `kaito`, `marco`), `.glb` clip/morph checklist, 5 runtime states | **Active (v1)** |
| `app/src/main/assets/characters/elena.glb` | Binary glTF 2.0 3D character asset (581.1 KB, 4,819 vertices, 8,296 triangles, 8 clips, 6 morphs) | **Active (v1)** |
| `app/src/main/assets/characters/kaito.glb` | Binary glTF 2.0 3D character asset (591.2 KB, 4,905 vertices, 8,408 triangles, 8 clips, 6 morphs) | **Active (v1)** |
| `app/src/main/assets/characters/marco.glb` | Binary glTF 2.0 3D character asset (593.2 KB, 4,919 vertices, 8,488 triangles, 8 clips, 6 morphs) | **Active (v1)** |
| `app/src/main/assets/characters/test.glb` | Startup diagnostic `.glb` verification asset (581.1 KB) | **Active (v1)** |
| `app/src/main/java/com/example/MainActivity.kt` | Root host, Google Sign-In via `CredentialManager`, system PiP (`PictureInPictureParams`), screen router | **Active (v1)** |
| `app/src/main/java/com/example/ui/CadenceViewModel.kt` | State holder driving `CharacterController`, `UniversalConverterEngine`, `CadenceAiService`, and Firestore | **Active (v1)** |
| `app/src/main/java/com/example/ui/theme/Color.kt` | Only the 8 `DESIGN.md` tokens (`bg`, `bgRaised`, `textPrimary`, `textSecondary`, `textTertiary`, `hairline`, `danger`) + 3 character accents (`#4FD1A5`, `#5AB8FF`, `#F5B04C`) | **Active (v1)** |
| `app/src/main/java/com/example/ui/theme/Type.kt` | Local `Manrope` (`R.font.manrope`) with the 4 styles (`Display`, `Title`, `Body`, `Label`) | **Active (v1)** |
| `app/src/main/java/com/example/ui/theme/Spacing.kt` | `4.dp` base unit, `20.dp` screen/card padding, `32.dp` section gap, `48.dp` min touch target | **Active (v1)** |
| `app/src/main/java/com/example/ui/theme/Shape.kt` | Sheets `28.dp`, cards `20.dp`, pill buttons/chips (`CircleShape`) | **Active (v1)** |
| `app/src/main/java/com/example/ui/theme/GlassSurface.kt` | Android 12+ `RenderEffect.createBlurEffect(24.dp)` + `#FFFFFF` 6% tint + `1.dp` hairline border (`#FFFFFF` 16% top edge) + `bgRaised` 92% fallback below API 31 | **Active (v1)** |
| `app/src/main/java/com/example/ui/theme/AuroraBackground.kt` | Android 13+ AGSL `RuntimeShader` (20s drift cycle of 2 soft desaturated accent blobs + 3% film grain) + 600ms accent crossfade + radial fallback below API 33 | **Active (v1)** |
| `app/src/main/java/com/example/ui/character/CharacterSpec.kt` | `ELENA` (`#4FD1A5`), `KAITO` (`#5AB8FF`), `MARCO` (`#F5B04C`) | **Active (v1)** |
| `app/src/main/java/com/example/ui/character/GlbLoader.kt` | Binary glTF 2.0 `.glb` parser & Logcat verifier for animations and morph targets | **Active (v1)** |
| `app/src/main/java/com/example/ui/character/CharacterController.kt` | 5-state controller (`Idle`, `Listening`, `Thinking`, `Talking`, `Reacting`) with 250ms clip crossfade, 2–6s blink, 8–15s `idle_look`, 12° touch head-tracking, and 80ms/150ms WAV RMS lip-sync | **Active (v1)** |
| `app/src/main/java/com/example/ui/character/CharacterStageView.kt` | 3D `.glb` triangle mesh stage renderer with morph target interpolation and character-accent rim light | **Active (v1)** |
| `app/src/main/java/com/example/ui/screens/StageScreen.kt` | Screen 1 (Stage Home) + Screen 3 (Conversation glass bottom sheet) | **Active (v1)** |
| `app/src/main/java/com/example/ui/screens/CharacterPickerScreen.kt` | Screen 2 (`HorizontalPager`, category chips, single `Choose` accent button) | **Active (v1)** |
| `app/src/main/java/com/example/ui/screens/ToolsScreen.kt` | Screen 4 (2-column Tools grid + individual tool screens) + Screen 5 (`ToolResultScreen` with `Share` and `Save`) | **Active (v1)** |
| `app/src/main/java/com/example/ui/screens/ComponentGalleryScreen.kt` | Debug gallery previewing all 4 `Manrope` text styles, `GlassSurface`, buttons, input bar, and 3 accents | **Active (v1)** |
| `app/src/main/java/com/example/parked/ParkedV1Modules.kt` | Pre-v1 Bio-Mood analyzer, multi-person receipt splitter, and dialer bridge moved out of active navigation | **Parked** |

---

### 1.2 Function-by-Function Audit of Core Services

| Module / Function | Real Implementation vs. Fallback | Audit Notes |
| :--- | :--- | :--- |
| `UniversalConverterEngine.convertImageToPdf` | **Real (`android.graphics.pdf.PdfDocument`)** | Renders the input `Bitmap` onto a native PDF page and writes a real `.pdf` file to cache/Downloads. |
| `UniversalConverterEngine.extractTextFromImage` | **Real (`gemini-3.5-flash-lite` Vision)** | Sends the `Bitmap` to Firebase AI Logic (`gemini-3.5-flash-lite`) and saves the extracted text to a `.txt` file. |
| `UniversalConverterEngine.convertPdfToHighResImage` | **Real (`android.graphics.pdf.PdfRenderer`)** | Opens the input PDF via `ParcelFileDescriptor` + `PdfRenderer`, rasterizes Page 1 at 2x DPI, and writes a `.png` file. |
| `UniversalConverterEngine.convertTextToVoiceFile` | **Real (`TextToSpeech.synthesizeToFile`)** | Synthesizes text to a PCM `.wav` file using Android's `TextToSpeech` engine (with a valid PCM `.wav` fallback if no TTS engine is installed in a headless container). |
| `UniversalConverterEngine.transcribeAudioFileToText` | **Real (`gemini-3.5-flash-lite` Multimodal Audio)** | Sends audio bytes (`inlineData`) to `gemini-3.5-flash-lite` for transcription and writes a `.txt` transcript file. |
| `UniversalConverterEngine.extractAudioFromVideo` | **Real (`MediaExtractor` + `MediaMuxer`)** | Demuxes the `audio/*` track from an MP4 container into an `.m4a` file without re-encoding. |
| `UniversalConverterEngine.convertDocumentOrTextToPdf` | **Real (`android.graphics.pdf.PdfDocument`)** | Typesets title and wrapped paragraphs onto an A4 PDF page (`595x842 pt`). |
| `Floating Overlay / PiP` | **Real System PiP (`PictureInPictureParams`)** | Uses `Activity.enterPictureInPictureMode` on `onUserLeaveHint` (no `SYSTEM_ALERT_WINDOW` permission required) and switches `CharacterStageView` to `halfResolutionMode = true`. |
| `MochiAccessibilityService` | **Not used / Removed** | Per Play Store policy and security best practices, third-party messaging/sharing uses `Intent.ACTION_SEND` + `FileProvider` instead of Accessibility screen-scraping. |

---

## 2. Redesigned Screen Screenshots (`DESIGN.md` & `CHARACTERS.md`)

### 2.1 Screen 1 — Stage (Home)
- **Nurse Elena** (`characters/elena.glb`, `#4FD1A5`) fills the top 62% of the screen over the 20-second `AuroraBackground` (`#07090F`).
- Top-left avatar button opens Character Picker; top-right button opens Tools.
- Bottom shows up to 3 suggestion chips and one `56.dp` `GlassSurface` input bar.

![Screen 1 — Stage (Home)](file:///.aistudio/artifacts/brain/e16b4b67-fc57-47b9-9d90-a2192052d3ba/01_stage_home_elena.png)

---

### 2.2 Screen 2 — Character Picker
- Full-screen `HorizontalPager` for **Nurse Elena** (`#4FD1A5`), **Kaito Storm** (`#5AB8FF`), and **Chef Marco** (`#F5B04C`).
- Category filter chips at the top (`All`, `Medical`, `Anime`, `Culinary`) and a single `accent` `"Choose"` button at the bottom.

![Screen 2 — Character Picker](file:///.aistudio/artifacts/brain/e16b4b67-fc57-47b9-9d90-a2192052d3ba/02_character_picker_pager.png)

---

### 2.3 Screen 3 — Conversation Bottom Sheet
- Opens as a `GlassSurface` bottom sheet (`28.dp` top corners) over the Stage when talking or typing while the 3D character stays visible above the sheet and lip-syncs (`jawOpen`, `mouthO`, `mouthE`).

![Screen 3 — Conversation Sheet](file:///.aistudio/artifacts/brain/e16b4b67-fc57-47b9-9d90-a2192052d3ba/03_conversation_glass_sheet.png)

---

### 2.4 Screens 4 & 5 — Tools Grid & Tool Result
- Clean 2-column grid of tiles (`Image to PDF`, `Image to text`, `PDF to image`, `Text to speech`, `Speech to text`, `Video to audio`, `Document to PDF`, `Fridge chef`, `Component gallery`) with zero emojis and zero marketing words.
- `ToolResultScreen` displays a thin `accent` progress bar (never a spinner) and `Share` / `Save` actions.

![Screens 4 & 5 — Tools Grid & Tool Result](file:///.aistudio/artifacts/brain/e16b4b67-fc57-47b9-9d90-a2192052d3ba/04_tools_grid_and_result.png)
