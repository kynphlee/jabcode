# Diagnostic App — UI/UX Audit (2026-05-28)

Filed in response to: "audit the UI/UX of the diagnostic app — we need to make some refinements, since the camera integration has been improved."

Subject: [`jabauth-android/diagnostic-app/src/main/java/com/jabauth/diagnostic/ui/`](../jabauth-android/diagnostic-app/src/main/java/com/jabauth/diagnostic/ui/) — the Compose UI of the JABCode diagnostic app, particularly the `ScannerScreen` which is the primary user surface for continuous JABCode scanning.

Related: [`docs/camera-control-audit.md`](camera-control-audit.md), [`docs/roi-detection-implementation-plan.md`](roi-detection-implementation-plan.md), [`docs/cassandra-register/H_nc2_decode_failure.md`](cassandra-register/H_nc2_decode_failure.md).

---

## 1. Current app architecture

```
DiagnosticApp (Compose)
├── Dashboard       — entry point; lists cameras
├── Scanner         — primary continuous-scan surface (this audit's focus)
├── CameraDetail    — per-camera characteristics dump
├── ErrorLog        — timestamped error history
├── CaptureTest     — stream validation
└── Settings        — app config
```

The **Scanner screen** is where the user spends 95%+ of their time. Its current implementation is a Compose `Scaffold` with:

- **Top bar**: "JABCode Diagnostic" + `Scans: N` counter
- **Camera preview** (40% of vertical height) — the `Camera2Preview` composable from `ui-components`
- **Results panel** (60% of vertical height) — scrolling list of cards: success indicator, four diagnostic rows (Color Mode / Decode Time / Position / Data Size), decoded data text, hex dump, and optional error card

---

## 2. The core observation

> The camera-control improvements across PRs #5–#16 have introduced rich runtime state that the UI never shows the user. The user has been **flying blind on every PR's empirical effect** — they had to trust logcat traces to see what was happening. The diagnostic app's most pressing UX gap is exactly the gap that diagnostic apps exist to fix: making the invisible visible.

Specifically, the following runtime state is now produced by the camera pipeline but invisible in the UI:

| Runtime state | Source | UI today | Should be on screen? |
|---|---|---|---|
| Current zoom level (PR 1) | `Camera2Preview.currentZoomRatio` | None — invisible | **YES** — critical |
| Max digital zoom | `Camera2Preview.maxDigitalZoom` | None | YES |
| Low Light Boost AE supported (PR #15) | `Camera2Preview.lowLightBoostSupported` | None | YES — once at session open |
| LLB active state | Captured but only logged | None | YES — live state |
| Stream use case (PR #12) | logcat only | None | YES (low priority) |
| Analysis stream resolution (1280×720) | logcat only | None | YES — capability info |
| Module size of last decode | Internal `DECODE_OK Nc=N module_size=X` marker | None | **YES** — drives "move closer" hint |
| Failure attribution (status=0 vs status=1) | logcat `FAIL_ATTR` markers | None | YES — telegraph WHY scans fail |
| Real-time decode rate | implicit in scanCount over time | Static counter | YES — % success on last 30s |
| Last decoded `Nc` | `result.colorMode` | Post-scan only | Add to live HUD |

---

## 3. Specific issues with the current UX

### 3.1 Camera preview is too small (40% vertical)

With pinch-zoom now active (PR 1), the user needs **more** screen real-estate to see what they're zooming on — but the current layout fights against this. At 1× zoom on a Galaxy S25 in portrait orientation, the JABCode card occupies maybe 25% of the preview area; at 4× zoom it's better but still small.

The 60% results panel is mostly empty until a scan succeeds — wasted screen real-estate during the most important interaction (the scan itself).

### 3.2 No HUD overlay on the camera preview

All status information lives in the lower panel, requiring the user to **look away from the camera** to see anything. For a continuous scanner, looking-at-the-target is the primary user activity; the diagnostic info should overlay the camera, not compete with it.

### 3.3 Stale supported-color-modes copy

`ScannerScreen.kt:193` reads:
```kotlin
text = "Supports: 4-color, 8-color, 16-color, 32-color, 64-color, 128-color"
```

This is **wrong now**. Per the empirical capability matrix:

| Nc | Print | Screen |
|----|-------|--------|
| 1 (4-color)  | ✓ | ✓ |
| 2 (8-color)  | ✗ | ✗ — open `H_nc2_decode_failure` |
| 3 (16-color) | ✓ | ✓ |
| 4 (32-color) | ✓ post-PR1 | ✓ |
| 5 (64-color) | ✓ | ✓ |
| 6 (128-color) | ✗ (gamut) | ✓ |
| 7 (256-color) | ✗ | ✗ |

The static "Supports: ..." string should be removed or replaced with a live capability indicator.

### 3.4 Results panel verbosity competes for attention

Once a scan succeeds, the user sees: success card → 4 diagnostic rows → decoded data card → hex dump card. That's a lot of scrolling for what most users only care about ("did it decode? what's the payload?"). The hex dump in particular is rarely useful in live scanning — better as an expandable detail.

### 3.5 No decode history

Each new scan overwrites the previous result. The user has no way to see "I scanned three different cards in the last minute." This makes side-by-side testing (e.g., comparing different Nc modes) much harder.

---

## 4. Before / After Wireframes

### State 1 — Empty / Searching

#### BEFORE (current)
```
┌─────────────────────────────────────────────┐
│  JABCode Diagnostic                         │
│  Scans: 0                                   │
├─────────────────────────────────────────────┤
│                                             │
│              ╔══════════════╗               │
│              ║              ║               │  ← Camera preview
│              ║  ▓▓▓▓▓▓▓▓▓▓  ║               │     40% height
│              ║  ▓▓▓▓▓▓▓▓▓▓  ║               │     no HUD
│              ║              ║               │
│              ╚══════════════╝               │
│                                             │
├─────────────────────────────────────────────┤
│ SCAN RESULTS                                │
│                                             │
│          ╔════════════════════╗             │
│          ║      ◌  loading    ║             │
│          ║ Scanning for...    ║             │
│          ║                    ║             │
│          ║ Supports: 4-color, ║             │  ← STALE COPY
│          ║ 8-color, 16-color, ║             │
│          ║ 32-color, 64-color,║             │
│          ║ 128-color          ║             │
│          ╚════════════════════╝             │
└─────────────────────────────────────────────┘
```

#### AFTER (Tier 1)
```
┌─────────────────────────────────────────────┐
│  ◀  JABCode Scanner         Scans:0   ⚙    │
├─────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────┐ │
│ │                          [LLB inactive] │ │  ← HUD top-right
│ │                                         │ │
│ │              ▓▓▓▓▓▓▓▓▓▓                 │ │
│ │              ▓▓▓▓▓▓▓▓▓▓                 │ │  ← CAMERA ~70% height
│ │              ▓▓▓▓▓▓▓▓▓▓                 │ │
│ │              ▓▓▓▓▓▓▓▓▓▓                 │ │
│ │                                         │ │
│ │ Hold camera over a JABCode              │ │  ← inline guidance
│ │                                         │ │
│ │ [Zoom 1.0×]            [0/0 in 30s]    │ │  ← HUD bottom
│ └─────────────────────────────────────────┘ │
├─────────────────────────────────────────────┤
│ Searching…                          ▲ open  │  ← compact 30%
│ Aim at a JABCode; pinch to zoom             │
│                                             │
│ History: (none yet)                         │
└─────────────────────────────────────────────┘
```

---

### State 2 — Active scanning, intermittent failures

#### BEFORE
```
┌─────────────────────────────────────────────┐
│  JABCode Diagnostic                         │
│  Scans: 0                                   │  ← stuck at 0
├─────────────────────────────────────────────┤
│              ╔══════════════╗               │
│              ║   ▓▓▓▓▓▓▓▓   ║               │  ← user has pinched for
│              ║   ▓███▓███▓  ║               │     20s, no zoom feedback
│              ║   ▓▓▓▓▓▓▓▓   ║               │
│              ╚══════════════╝               │
├─────────────────────────────────────────────┤
│          ╔════════════════════╗             │
│          ║      ◌  loading    ║             │
│          ║ Scanning for...    ║             │  ← still spinning,
│          ║                    ║             │     no clue what's wrong
│          ║ Supports: 4-color, ║             │
│          ║ 8-color, 16-color  ║             │
│          ╚════════════════════╝             │
└─────────────────────────────────────────────┘
```

#### AFTER
```
┌─────────────────────────────────────────────┐
│  ◀  JABCode Scanner         Scans:0   ⚙    │
├─────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────┐ │
│ │ [Zoom 2.5×]                [LLB active] │ │  ← live zoom feedback
│ │                                         │ │
│ │           ▓▓▓▓▓▓▓▓▓                     │ │
│ │           ▓███▓███▓                     │ │  ← zoomed-in barcode
│ │           ▓▓▓▓▓▓▓▓▓                     │ │
│ │                                         │ │
│ │ ⚠ Detected but couldn't decode —        │ │  ← FAILURE ATTRIBUTION
│ │   try pinching closer or moving in      │ │     (status=1 dominant)
│ │                                         │ │
│ │ [Module 8 px ⚠]        [0/14 in 30s]   │ │  ← module size warns
│ └─────────────────────────────────────────┘ │
├─────────────────────────────────────────────┤
│ Searching… (14 attempts)            ▲ open  │
│ Pinch to zoom; module size 8 px (low)       │
│                                             │
│ History: (none yet)                         │
└─────────────────────────────────────────────┘
```

---

### State 3 — Successful decode

#### BEFORE
```
┌─────────────────────────────────────────────┐
│  JABCode Diagnostic                         │
│  Scans: 1                                   │
├─────────────────────────────────────────────┤
│              ╔══════════════╗               │
│              ║   [DECODED]  ║               │  ← preview small (40%)
│              ╚══════════════╝               │
├─────────────────────────────────────────────┤
│ SCAN RESULTS                                │
│   ┌────────────────────────────┐           │
│   │ ✓ JABCode Detected         │           │
│   └────────────────────────────┘           │
│   Color Mode              COLOR_16          │
│   Decode Time             87ms              │
│   Position                1920×1080px       │
│   Data Size               11 bytes          │
│   DECODED DATA                              │
│   ┌────────────────────────────┐           │
│   │ HELLO-Nc-3                 │           │
│   └────────────────────────────┘           │
│   HEX DUMP                                  │
│   ┌────────────────────────────┐           │
│   │ 48 45 4C 4C 4F 2D 4E 63 2D │           │  ← always shown,
│   │ 33                         │           │     rarely useful
│   └────────────────────────────┘           │
└─────────────────────────────────────────────┘
```

#### AFTER — collapsed
```
┌─────────────────────────────────────────────┐
│  ◀  JABCode Scanner         Scans:1   ⚙    │
├─────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────┐ │
│ │ [Zoom 2.5×]              [LLB active]   │ │
│ │                                         │ │
│ │           ▓▓▓▓▓▓▓▓▓                     │ │  ← preview stays
│ │           ▓███▓███▓                     │ │     visible for next
│ │           ▓▓▓▓▓▓▓▓▓                     │ │     scan
│ │                                         │ │
│ │ ✓ HELLO-Nc-3                            │ │  ← inline success
│ │                                         │ │
│ │ [Module 24 px ✓]       [1/14 in 30s]   │ │
│ └─────────────────────────────────────────┘ │
├─────────────────────────────────────────────┤
│ ✓ COLOR_16 · HELLO-Nc-3 · 87ms     ▼ open  │  ← compact summary
│                                             │
│ History: [Nc3 ●]                            │
└─────────────────────────────────────────────┘
```

#### AFTER — expanded (after tapping ▼)
```
┌─────────────────────────────────────────────┐
│  ◀  JABCode Scanner         Scans:1   ⚙    │
├─────────────────────────────────────────────┤
│         ▓▓▓▓▓▓▓▓▓        [Zoom 2.5×]       │  ← preview minimized
│         ▓███▓███▓        [LLB active]      │     ~30%
│         ▓▓▓▓▓▓▓▓▓     [Module 24px ✓]      │
├─────────────────────────────────────────────┤
│ ✓ JABCode Detected                  ▲ close│
│                                             │
│   Color Mode              COLOR_16          │
│   Decode Time             87ms              │
│   Position                1920×1080px       │
│   Data Size               11 bytes          │
│   Module Size             24 px             │  ← new
│   Zoom at decode          2.5×              │  ← new
│   LLB state               active            │  ← new
│                                             │
│   DECODED DATA                              │
│   HELLO-Nc-3                                │
│                                             │
│   HEX DUMP                                  │
│   48 45 4C 4C 4F 2D 4E 63 2D 33             │
│                                             │
│ History: [Nc3 ●]                            │
└─────────────────────────────────────────────┘
```

---

### State 4 — Multi-decode history (NEW)

```
┌─────────────────────────────────────────────┐
│  ◀  JABCode Scanner         Scans:8   ⚙    │
├─────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────┐ │
│ │ [Zoom 1.5×]              [LLB inactive] │ │
│ │           ▓▓▓▓▓▓▓▓▓                     │ │
│ │           ▓███▓███▓                     │ │
│ │           ▓▓▓▓▓▓▓▓▓                     │ │
│ │ [Module 18 px ✓]       [8/12 in 30s]   │ │
│ └─────────────────────────────────────────┘ │
├─────────────────────────────────────────────┤
│ ✓ COLOR_64 · HELLO-Nc-5 · 134ms    ▼ open  │
│                                             │
│ History: [Nc3●][Nc3●][Nc4●][Nc3●][Nc5●]    │  ← tap chip to
│                                             │     re-show that decode
└─────────────────────────────────────────────┘
```

---

## 5. Component breakdown — what's new in each region

```
┌──────────────────────────────────────────────────────────────┐
│ TOP BAR                                                       │
│   - Slim profile (saves vertical space)                       │
│   - Scans counter retained                                    │
│   - NEW: ⓘ capability button (long-press → CameraDetail)    │
│   - NEW: ⚙ settings shortcut                                 │
├──────────────────────────────────────────────────────────────┤
│ CAMERA PREVIEW REGION (~70% of vertical screen)               │
│                                                               │
│   ┌─ Top-right HUD chips ──────────────────────┐             │
│   │   [Zoom Nx]      — hidden at 1.0x          │             │
│   │   [LLB active|inactive]  — when supported  │             │
│   └────────────────────────────────────────────┘             │
│                                                               │
│   ┌─ Inline guidance/result text ──────────────┐             │
│   │   "Hold camera over a JABCode"             │             │
│   │   "✓ HELLO-Nc-3"                           │             │
│   │   "⚠ Detected but couldn't decode..."      │             │
│   └────────────────────────────────────────────┘             │
│                                                               │
│   ┌─ Bottom HUD chips ─────────────────────────┐             │
│   │   [Module Npx ✓|⚠|✗]                       │             │
│   │   [N OK / M fail in 30s]                   │             │
│   └────────────────────────────────────────────┘             │
├──────────────────────────────────────────────────────────────┤
│ COMPACT BOTTOM PANEL (~30%)                                   │
│   - One-line summary of last decode                           │
│   - Tap ▲/▼ to expand/collapse full detail                   │
│   - History strip with last 5 decode chips                    │
│   - Expanded view includes new fields: Module Size, Zoom at  │
│     decode, LLB state at decode                               │
└──────────────────────────────────────────────────────────────┘
```

---

## 6. Color/state legend for chips

| Chip | Green | Yellow | Red | Hidden |
|---|---|---|---|---|
| Module size | ≥ 12 px | 6–11 px ("move closer") | < 6 px | n/a (always show during scan) |
| LLB state | active | — | — | when unsupported |
| Zoom | always present | — | — | hidden at 1.0× |
| Mini-stats | >50% over 30s | 10–50% | < 10% | first 5 seconds (insufficient data) |

---

## 7. Implementation effort estimate

| Chip / Component | Source state | Wiring effort |
|---|---|---|
| Zoom | `Camera2Preview.currentZoomRatio` → expose as `StateFlow<Float>` | ~30 min |
| LLB state | `Camera2Preview.lastReportedLlbState` → expose as `StateFlow<Int>` | ~30 min |
| Module size | New `DecodeResult.moduleSize: Float` field, set from native side | ~2 hours (C wiring + JNI + Kotlin) |
| Mini-stats (recent OK/fail in 30s) | `ScannerViewModel` keeps a deque of last N attempts | ~1 hour |
| History strip | `ScannerViewModel` keeps a list of last 5 `DecodeResult`s | ~1 hour |
| Layout restructure (camera-dominant) | `ScannerScreen.kt` weight changes 0.4/0.6 → 0.7/0.3 | ~30 min |
| HUD overlay composable | New Composable wrapping `Camera2Preview` in a `Box` | ~2 hours |
| Compact bottom panel + collapse/expand | Compose `AnimatedVisibility` + state | ~1 hour |

**Phase 1A (visible UX win, no C-library changes)**: HUD overlay + layout restructure + chips with Kotlin-side data (zoom, LLB, mini-stats, history) + failure-attribution hints. ~1 day.

**Phase 1B (deeper plumbing)**: module size chip requires C library → JNI → Kotlin changes. ~2 hours.

---

## 8. Tier 2 + Tier 3 (deferred)

### Tier 2 — Medium effort

- **[E] Capabilities Panel** accessible from the top bar: shows `maxDigitalZoom`, `streamUseCase`, `CONTROL_AE_AVAILABLE_MODES`, `INFO_SUPPORTED_HARDWARE_LEVEL`. Belongs in CameraDetail screen but also reachable from live scanner.
- **[F] Live decode-rate strip** at the bottom of the results panel — sparkline-style.
- **[G] Tap-to-zoom shortcuts** (1× / 2× / 4× buttons) complementing pinch.
- **[H] Decode history strip** with tappable chips (Tier 1 partial — full interactivity here).

### Tier 3 — After ROI workstream

- **[I] ROI tracking overlay** — bounding box on the preview showing the detected JABCode region (after PRs 3+4 land for ROI).
- **[J] Adaptive camera intelligence indicators** — AE/AWB lock state, manual override (after workstream #4).
- **[K] Settings reorganization** — `defaultZoom`, `aeLockMode`, `showHud`, `experimentalLowLightBoost`.

---

## 9. Recommended sequencing

| Order | Item | Reason |
|---|---|---|
| 1 | **Phase 1A** (HUD + layout) | Unblocks user understanding of PR 1's pinch zoom NOW |
| 2 | **Phase 1B** (module size plumbing) | Closes the "move closer" hint feature loop |
| 3 | **Tier 2 [F] + [G]** | After workstream #4 (AE/AWB lock) lands — needs stable underlying state |
| 4 | **Tier 2 [E] capability panel** | Anytime; useful for diagnostics |
| 5 | **Tier 3 [I] [J]** | After PRs 3+4 land for ROI |

---

## 10. Cross-references

- [`docs/camera-control-audit.md`](camera-control-audit.md) — original audit that catalyzed the camera-control workstream
- [`docs/roi-detection-implementation-plan.md`](roi-detection-implementation-plan.md) — ROI design plan; Tier 3 items in this UI audit land alongside PRs 3+4 of the ROI plan
- [`docs/cassandra-register/H_nc2_decode_failure.md`](cassandra-register/H_nc2_decode_failure.md) — explains the static "Supports: ..." string being empirically wrong
- `project_jabcode_screen_vs_print_physics.md` (memory) — informs the failure-attribution hint copy
- `project_adaptive_camera_intelligence.md` (memory) — long-term vision that Tier 2 + Tier 3 items align with
