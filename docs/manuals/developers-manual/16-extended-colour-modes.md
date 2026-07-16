# 16. Extended colour modes

<!-- objective: A maintainer can explain the reserved-Nc implementation — Mode 0 (2-colour, fork-only) through Nc = 7 (256) — including palette handling beyond 8 colours (slave palette positions extended 32→64, per-mode FP/AP colour indices), decoder fallback ladder and g_preferred_color_count pinning, and state interchange consequences precisely. -->

**Scope.** The standard defines eight Nc codepoints but gives normative content to only two of them. This fork implements all eight — including a 2-colour mode the standard does not contain at all — and this chapter maps where each mode touches the encoder, detector and decoder, what Annex G actually licenses, and what that does to interchange. Operator-level treatment: [../operators-manual/04-colour-modes-conformance.md](../operators-manual/04-colour-modes-conformance.md).

## 16.1 The normative baseline

Table G.2 maps the 3-bit Nc field: 0 "reserved", 1 = 4 colours, 2 (default) = 8, 3 = 16, 4 = 32, 5 = 64, 6 = 128, 7 = 256. Clause 4.4.1.2: "Colour modes 0, 3, 4, 5, 6 and 7 are reserved for future extensions. These colour modes can also be used for user-defined colour modes." <!-- anchor: ISO 23634 4.4.1.2, Annex G Table G.2 --> Annex G.3 a) frames the >8-colour territory as guidance for closed systems: "If more than eight colours are used for closed, user defined applications, the following guideline should be considered." <!-- anchor: ISO 23634 G.3 --> **No 2-colour mode exists anywhere in the standard** — Nc = 0 is "reserved", with no colour count assigned; the 2-colour interpretation is purely this reference implementation's extension (project clause map: "A 2-colour mode does not exist anywhere in the standard"). <!-- anchor: JABCodeCOA-crypto/docs/manuals/plan/03-iso-23634-reference-map.md -->

The code's own convention: colour count = `2^(Nc+1)` for Nc = 0..7, i.e. `{2, 4, 8, 16, 32, 64, 128, 256}`. <!-- anchor: src/jabcode/include/jabcode.h:100-105 -->

## 16.2 Implementation map — where each mode touches the code

### Entry gate (writer CLI)

`--color-number` accepts exactly `{2, 4, 8, 16, 32, 64, 128, 256}`; the admitting comment records the extensions' provenance: "WS-0: Accept color_number=2 (Nc=0, Mode 0 monochrome). WS-3: Accept color_number=256 (Nc=7, max-density mode)." <!-- anchor: src/jabcodeWriter/jabwriter.c:147-155 -->

### Encoder — palette generation

`setDefaultPalette(color_number, palette)` branches four ways:

| `color_number` | Palette | Anchor |
|---|---|---|
| 2 | explicit K `(0,0,0)` index 0, W `(255,255,255)` index 1 — "WS-0: Mode 0 monochrome palette \[K, W\]" | encoder.c:97-103 |
| 4 | Table 21 subset K/M/Y/C, chosen to coincide with FP core colours (comments: "black 000 for 00 … cyan 011 for 11") | encoder.c:104-110 |
| 8 | the full Table 21 default palette `[K,B,G,C,R,M,Y,W]` | encoder.c:111-117; encoder.h:26-34 |
| 16..256 | `genColorPalette` — RGB-cube subdivision with per-mode channel counts `(vr, vg, vb)`: 16 → (4,2,2), 32 → (4,4,2), 64 → (4,4,4), 128 → (8,4,4), 256 → (8,8,4) | encoder.c:29-88, 118-121 |

<!-- anchor: src/jabcode/encoder.c:29-122 --> `genColorPalette` returns immediately for `color_number < 8` — the extended generator exists *only* for the reserved modes 3..7. <!-- anchor: src/jabcode/encoder.c:31-32 --> These generated palettes are an implementation choice under the G.3 a) guideline, not a normative table: another implementation could subdivide the cube differently and be equally "conformant", which is precisely the interchange problem of §16.5.

### Encoder/detector — per-mode FP/AP core colours

Finder- and alignment-pattern core colours are indexed **by colour mode** (array index = Nc):

```c
static const jab_byte fp0_core_color_index[] = {0, 0, FP0_CORE_COLOR, 0, 0,  0,   0,   0};
static const jab_byte fp1_core_color_index[] = {0, 0, FP1_CORE_COLOR, 0, 0,  0,   0,   0};
static const jab_byte fp2_core_color_index[] = {0, 2, FP2_CORE_COLOR, 14, 30, 60, 124, 252};
static const jab_byte fp3_core_color_index[] = {0, 3, FP3_CORE_COLOR, 3,  7,  15,  15,  31};
static const jab_byte apn_core_color_index[] = {0, 3, AP0_CORE_COLOR, 3,  7,  15,  15,  31};
static const jab_byte apx_core_color_index[] = {0, 2, APX_CORE_COLOR, 14, 30, 60, 124, 252};
```

<!-- anchor: src/jabcode/encoder.h:64-75 --> Column 2 is the standard 8-colour mode (`FP2_CORE_COLOR 6` = yellow, `FP3_CORE_COLOR 3` = cyan <!-- anchor: src/jabcode/encoder.h:50-62 -->); columns 0, 1 and 3..7 define what "the yellow-ish corner" and "the cyan-ish corner" *mean* in each extended palette (e.g. index 252 in the 256-colour cube plays FP2's role). Every extended mode therefore touches detection geometry through these tables — a detector expecting only the mode-2 values would reject every reserved-mode symbol at the finder stage.

### Encoder/decoder — Nc transport and slave palettes

- Metadata Part I encodes Nc via `nc_color_encode_table[8][2]` — colour-pair bootstrap over `{K=0, C=3, Y=6}` so Nc is decodable before the palette is known. <!-- anchor: src/jabcode/encoder.h:124; src/jabcode/decoder.c:1292-1296 -->
- Slave symbols embed their palette at `slave_palette_position[64]` — a boustrophedon over `x ∈ [4,11], y ∈ [5,12]`, "extended from 32 to 64 for high Nc; the slave places palette colours 2..MIN(cn,64)-1 at index \[colour-2\], needing 62 entries at cn=64; >64 colours are interpolated". The same header comment preserves the open edge: "at high colour, cascade still fails at slave versions == 0 (mod 5) (v10/v15/v20...) -- a separate pre-existing slave capacity/alignment-geometry resonance … NOT the palette sizing fixed here." <!-- anchor: src/jabcode/decoder.h:27-45 --> The 32→64 extension is the PR #113 fix that `bench-cascade`'s success matrix guards ([12-benchmark-estate.md](12-benchmark-estate.md) §12.3).

### Decoder — the Nc fallback ladder and pinning

When metadata Part I yields an Nc, `decodeMaster` still prepares an 8-try ladder in case downstream stages fail under that reading:

```c
jab_byte nc_order[] = {original_Nc, 1, 0, 2, 3, 4, 5, 6};
jab_int32 nc_tries = 8;
```

<!-- anchor: src/jabcode/decoder.c:2118-2120 --> Retries restore the post-Part-I cursor/data-map snapshot, clear any partially populated `symbol->data` (the "WS-5 round-6 memory hygiene" note), and re-run palette read → Part II → data decode at the substituted Nc; success on a retry logs `Nc_FALLBACK: SUCCESS`. <!-- anchor: src/jabcode/decoder.c:2112-2181, 2359 -->

`g_preferred_color_count` collapses this: "Path β: pin the fallback ladder to the user-selected Nc when set. Collapses the 8-iteration walk into a single deterministic attempt at the chosen Nc. Mapping: count → Nc index where Nc = log2(count) - 1 … Invalid counts … fall through to default auto-detect behaviour, preserving safety against bad input." The switch maps `{2,4,8,16,32,64,128,256}` → Nc 0..7, sets `nc_order[0] = pinned_Nc; nc_tries = 1`. <!-- anchor: src/jabcode/decoder.c:2122-2150; src/jabcode/include/jabcode.h:100-105 --> It is a process-global toggle with the thread contract of [14-concurrency.md](14-concurrency.md) §14.3.

### Detector + decoder — Mode 0 (fork-only)

Mode 0 has no in-band signal at all; its "metadata" is a scene-level heuristic plus three decode-path overrides:

1. **Chroma probe** (detector): `detectMaster` samples a ~16x16 pixel grid, computes mean chroma `|r-g| + |g-b| + |r-b|`, and sets `g_mode0_decode` iff `mean_chroma <= MODE0_MEAN_CHROMA_TOLERANCE` (30). The 40-line constant comment records why *mean* replaced per-pixel-strict (camera chroma noise has a long tail; all 118 test frames failed the per-pixel rule) and the empirical separations (greyscale + Bayer noise mean ~10-20; 4-colour code ~250). <!-- anchor: src/jabcode/detector.c:41-80, 3691-3731 --> The flag also relaxes FP scanning and skips the colour-channel cross-checks that assume Y/C cores. <!-- anchor: src/jabcode/detector.c:82-88, 2571-2607 -->
2. **Part I short-circuit** (decoder): standard Part I pair-bit decoding is impossible in Mode 0 — the `{K, W}` palette produces pairs containing W (rgb=7), absent from the `{K=0, C=3, Y=6}` table, so "every lookup returns the invalid-sentinel 8". The fork advances the cursor past the 4 Part I modules, asserts `Nc = 0` directly, and cites the licensing clause: "Custom Mode 0 extension per ISO/IEC 23634:2022 Table 6 clause: 'These colour modes can also be used for user-defined colour modes.'" <!-- anchor: src/jabcode/decoder.c:1283-1318 -->
3. **Palette synthesis, master and slave** (decoder): Mode 0 symbols embed no palette; `readColorPaletteInMaster/Slave` write garbage from pattern positions, which is then overwritten with the implicit `{K=(0,0,0), W=(255,255,255)}` across all 4 panels "so the downstream pipeline … sees a valid palette and works without per-function Mode 0 awareness" — W2.9 (master) and its cascade counterpart W2.10 (slave). <!-- anchor: src/jabcode/decoder.c:2206-2248, 2404-2437 -->

## 16.3 Permissive classification interplay

`g_permissive_color_classification` (Path β, default OFF) substitutes magenta (rgb=5) with yellow (rgb=6) at the `module_color` stage of `decodeMasterMetadataPartI`, compensating "camera green-channel under-capture observed in the H_nc2 investigation". <!-- anchor: src/jabcode/include/jabcode.h:93-98 --> Its interplay with the extended modes is upstream of the ladder: it changes the *initial* Nc reading that seeds `nc_order[0]`, while `g_preferred_color_count` overrides that seed entirely. Pinning therefore subsumes permissiveness for the Nc field itself (a pinned decode never consults the Part I Nc), but permissiveness still matters when auto-detecting. Both toggles are process-global (§14.3).

## 16.4 Annex G guidance vs this implementation's choices

Annex G is guidance, not palette normativity. Where the standard says "the following guideline should be considered" for >8 colours <!-- anchor: ISO 23634 G.3 -->, this implementation commits to specific answers:

- **Palette construction:** fixed RGB-cube subdivisions per mode (§16.2), biased to more red/green than blue levels at 128/256 (`(8,4,4)`, `(8,8,4)`). <!-- anchor: src/jabcode/encoder.c:52-61 -->
- **Palette transport:** master symbols embed 4 palette copies (`COLOR_PALETTE_NUMBER 4`); slaves embed up to 64 entries at `slave_palette_position` and interpolate beyond. <!-- anchor: src/jabcode/include/jabcode.h:41; src/jabcode/decoder.h:27-45 -->
- **Pattern semantics:** the per-mode FP/AP core-colour tables of §16.2 define detectability in each extended palette. <!-- anchor: src/jabcode/encoder.h:67-75 -->
- **Classification aids:** the runtime side compensates capture shift with the fork's adaptive palette (LAB + k-d tree) and FP-core calibration — implementation aids with no spec counterpart ([10-fork-extensions.md](10-fork-extensions.md)).

Each bullet is a *convention*, binding only on implementations that share this codebase or deliberately clone its tables.

## 16.5 Interchange consequences, stated precisely

- **Open interchange exists only at Nc = 1 and Nc = 2** (4- and 8-colour): these are the only modes whose palettes and semantics the standard defines. A symbol in either mode is decodable by any conformant ISO/IEC 23634 decoder. <!-- anchor: ISO 23634 4.4.1.2, Annex G Table G.2 -->
- **Nc = 3..7 (16-256 colours) are closed-system modes.** The Nc field transports correctly (it is a normative metadata field), but the standard assigns those codepoints no palette; a receiving decoder must share this implementation's `genColorPalette` subdivisions, FP/AP core-colour tables, and 64-entry slave-palette convention, or decoding fails after Part I. Deploy them only where both ends run this codec lineage — the G.3 "closed, user defined applications" case. <!-- anchor: ISO 23634 G.3; src/jabcode/encoder.c:29-88; src/jabcode/encoder.h:67-75; src/jabcode/decoder.h:27-45 -->
- **Mode 0 (2-colour) is fork-only, one level stricter.** It is not merely an unassigned palette: the mode is *detected by a chroma heuristic* (a greyscale scene trips it — `MODE0_MEAN_CHROMA_TOLERANCE`), Part I is skipped rather than read, and the palette is synthesised rather than embedded. No other decoder — including the Fraunhofer reference this fork descends from — has any of these paths; and a marketing claim of a "2-colour JAB Code mode" must be qualified per the clause map ("does not exist anywhere in the standard"). <!-- anchor: src/jabcode/detector.c:41-80; src/jabcode/decoder.c:1283-1318; JABCodeCOA-crypto/docs/manuals/plan/03-iso-23634-reference-map.md -->
- **Downstream mirror:** the framework's `ColorMode` enum exposes `COLOR_2` … `COLOR_256` and works against this codec, with the same conformance qualification recorded in the framework corpus (discrepancy log: `COLOR_2` "exists and works in the codec" while the standard has no such mode). <!-- anchor: JABCodeCOA-crypto corpus §3.5, §7.10 -->

## 16.6 Capacity vs tolerance

Density scales as `log2(colour count)` bits/module — 1 bit at Mode 0 up to 8 bits at Nc = 7 — while the colour-quality margins the Clause 8 parameters measure (colour palette accuracy, colour variation in data modules; names in [15-conformance-testing.md](15-conformance-testing.md) §15.4) shrink as palette points pack the RGB cube. The empirical shape of that trade in this tree: `bench` decode medians rising 1.86 → 7.01 ms across 2 → 256 colours ([12-benchmark-estate.md](12-benchmark-estate.md) §12.8) and the per-medium (Nc, ECC) profiles the framework derived from it (FIELD = 16-colour/ECC 5 … SERVER = 256-colour/ECC 3). <!-- anchor: JABCodeCOA-crypto corpus §3.6 (CoaProfile) --> The quantitative treatment — how CPA/CVDM margins decay with Nc and where the capacity/tolerance optimum sits per capture channel — is JC-S material (forthcoming).
