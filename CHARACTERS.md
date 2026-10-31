# Characters (v1)

Ship **3 working characters** first. Add more only after these 3 work end-to-end.

| ID | Name | Category | File | Accent | Voice style |
|---|---|---|---|---|---|
| `elena` | Nurse Elena | Medical | `characters/elena.glb` | `#4FD1A5` | Warm, calm, speaks slowly |
| `kaito` | Kaito Storm | Anime | `characters/kaito.glb` | `#5AB8FF` | Energetic, playful, quick replies |
| `marco` | Chef Marco | Culinary | `characters/marco.glb` | `#F5B04C` | Cheerful, practical, loves food |

## Model checklist (per `.glb` file)
- Under 8 MB, 15k–35k triangles, Draco compressed, 1024–2048 textures.
- **Required animation clips** (exact names):
  `idle`, `idle_look`, `listen`, `think`, `talk_a`, `talk_b`, `wave`, `happy`
- **Required face morph targets** (exact names):
  `jawOpen`, `mouthO`, `mouthE`, `mouthSmile`, `eyeBlinkLeft`, `eyeBlinkRight`

## Runtime states
Every character is driven by one `CharacterController` with 5 states:
- `Idle` — loops `idle`, plays `idle_look` every 8–15s, blinks every 2–6s.
- `Listening` — plays `listen`, subtle nod.
- `Thinking` — plays `think`.
- `Talking` — alternates `talk_a` / `talk_b`; drives `jawOpen`, `mouthO`, `mouthE` from speech audio amplitude (with 80ms attack / 150ms release smoothing).
- `Reacting(clip)` — plays `wave` or `happy` once, then returns to `Idle`.
