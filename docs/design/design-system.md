# MochiLife Design Specification

## Goal
One screen, one job. The 3D character is the hero; the UI stays quiet, dark, and out of the way.

## Color tokens
Only use these tokens. Never hardcode hex values in composables.

| Token | Value | Use |
|---|---|---|
| `bg` | `#07090F` | App background |
| `bgRaised` | `#0E121B` | Sheets with no blur behind them |
| `textPrimary` | `#F3F5F9` | Main text |
| `textSecondary` | `#F3F5F9` at 64% | Supporting text |
| `textTertiary` | `#F3F5F9` at 40% | Hints, disabled |
| `hairline` | `#FFFFFF` at 10% | Borders, dividers |
| `accent` | Per character | Nurse `#4FD1A5`, Kaito `#5AB8FF`, Chef `#F5B04C` |
| `danger` | `#FF5A5F` | Errors only |

## Glass
Glass is only glass if it blurs what is behind it.
- Use the **Haze** library (`dev.chrisbanes.haze`) or `RenderEffect.createBlurEffect(24.dp)` on Android 12+.
- Tint: `#FFFFFF` at 6%.
- Border: `1dp` of `hairline`, slightly brighter (`#FFFFFF` at 16%) on the top edge.
- Below Android 12: fall back to `bgRaised` at 92% opacity.
- **At most 2 glass surfaces on screen at once.**

## Background
Behind the character, render a slow aurora from an AGSL `RuntimeShader`: two soft blobs in a desaturated version of `accent`, one full drift cycle every 20 seconds, with 3% film-grain noise on top. Below Android 13, use a static radial gradient.

## Typography
Font: `Manrope` (Google Fonts). One family only.

| Style | Size / Line | Weight | Tracking |
|---|---|---|---|
| `Display` | `34 / 40 sp` | `600` | `-0.5sp` |
| `Title` | `22 / 28 sp` | `600` | `-0.2sp` |
| `Body` | `16 / 24 sp` | `400` | `0sp` |
| `Label` | `13 / 18 sp` | `500` | `+0.2sp` |

Rules:
- Sentence case everywhere. Never ALL CAPS.
- At most 2 text sizes inside one component.

## Spacing & shape
- Base unit: `4dp`. Screen side padding: `20dp`.
- Between sections: `32dp`. Inside cards: `20dp`.
- Corners: sheets `28dp`, cards `20dp`, buttons and chips fully rounded (`CircleShape`).
- Touch targets: at least `48dp`.

## Icons
- Material Symbols Outlined, `24dp`, stroke weight `400`.
- **No emoji anywhere in the UI.**

## Screens (each has one job)

### 1. Stage (Home)
- Character fills the top 62% of the screen over the aurora background.
- Below it: one input bar (mic + text) and up to 3 suggestion chips.
- Top-left: small avatar button (opens Character Picker).
- Top-right: tools button (opens Tools).
- Nothing else on this screen.

### 2. Character Picker
- Full-screen `HorizontalPager`, one character per page.
- Shows: large 3D preview, name (`Title`), one-line role (`Body`, secondary), one accent "Choose" button.
- Category filter chips at the top (`All`, `Medical`, `Anime`, `Culinary`).

### 3. Conversation
- Opens as a glass bottom sheet over the Stage when the user talks or types.
- Character stays visible above the sheet and keeps animating.

### 4. Tools
- Clean 2-column grid of tiles (icon + name).
- Each tool opens its own simple screen with one primary action button at the bottom.

### 5. Tool Result
- Preview of the result, thin accent progress bar (never a spinner), then `Share` and `Save`.

## Never
- Never draw characters or faces with `Canvas` or `Path`.
- Never put emoji in buttons or labels.
- Never put more than one accent button per screen.
- Never put marketing words in the UI ("100%", "ultra", "astonishing", "spatial").
