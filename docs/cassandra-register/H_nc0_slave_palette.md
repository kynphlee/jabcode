# H_nc0_slave_palette — Implemented (validation pending): Mode 0 (Nc=0) cascading — slave encode + detect + decode all assumed colour (W2.9 was master-only)

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-06-02 (register hygiene — documenting the cascade gap explicitly left open by W2.9's master-only scope) |
| **Status**   | **Implemented — awaiting TDD validation** (2026-06-02, worktree `ws-mode0-slave-cascade-fix`; builds clean, `libjabcode.a`/`.so` link). Implementation revealed cascading needs **three** coordinated Mode 0 fixes, not two — the W2.10-b detection trace had correctly found the slave-AP chroma block but under-scoped the **encoder** prerequisite: <br>• **W2.10-enc** (encoder.c:1323–1327) — Mode 0 slave docking APs were drawn **solid black** (both centre k=0 and ring k=1 resolve to index 0), so they carry **no K↔W bullseye** for the detector's *geometric* run-length checks to lock onto — undetectable *before* any chroma test even runs. Fix: alternate K-centre / W-ring, mirroring the master FP Mode 0 fix. <br>• **W2.10-b** (detector.c) — retarget the AP centre-colour gate to **black** in `crossCheckPatternHorizontalAP` (keeps the call + geometry + center.x averaging live) and **skip** the three cyan green-channel `crossCheckColor` confirmations in `crossCheckPatternAP`. <br>• **W2.10** (decoder.c:2241) — slave `{K,W}` palette synthesis, mirroring W2.9 master. <br>All three gate on Mode 0 only (encoder `Nc==0`; detector/decoder `g_mode0_decode`) → byte-for-byte zero impact on colour modes. Ordering is a hard dependency chain: **enc → detect → decode**. Empirical validation still pending — no Mode 0 master+slave fixture exists yet. |
| **Binding**  | Triggered (customer requires all 8 Nc modes AND symbol cascading for large-payload documents) |
| **Owner**    | Unassigned (claimed on trigger)                                                                    |
| **Severity** | Medium — single-symbol Mode 0 decodes end-to-end after W2.9, but any master+slave Mode 0 cascade fails. Blocks Mode 0 for payloads exceeding one symbol's capacity (which for Nc=0 is the lowest of all modes: 1 bit/module). |
| **Related**  | `H_nc0_partII_ldpc.md` (W2.9 — master palette synthesis; this entry is the slave-side sibling), `H_nc0_pair_bits.md` (W2.8 — PartI short-circuit), `H_mode0_decodeModuleNc_classifier.md` (W1.2 — module_color classifier) |

## Provenance — why this entry exists

W2.9 (`H_nc0_partII_ldpc.md`) resolved Mode 0 PartII LDPC by synthesising the implicit
`{K, W}` palette immediately after `readColorPaletteInMaster` returns — but the synthesis
block is gated inside `decodeMaster` **only** (decoder.c:2099). The slave decode path
(`decodeSlave`, decoder.c:2218) reads its palette via `readColorPaletteInSlave`
(decoder.c:2235) and was never given the analogous treatment.

Per the register-hygiene discipline established in the 2026-06-01 cycle (see
`H_nc0_pair_bits.md`'s "Honest provenance" section), this gap gets its own entry rather
than being silently deferred into W2.9's Resolution section. The supersession chain is now:

`H_mode0_partI_decode_failure` (Superseded) → `H_mode0_decodeModuleNc_classifier`
(Resolved, module_color, W1.2) → `H_nc0_pair_bits` (Resolved, PartI, W2.8) →
`H_nc0_partII_ldpc` (Resolved, PartII master, W2.9) → **`H_nc0_slave_palette` (this entry,
slave encode + detect + decode — W2.10-enc / W2.10-b / W2.10).**

## The hypothesis (mechanism — confirmed by code inspection)

For a camera-captured Mode 0 cascade (a Mode 0 master with ≥1 docked slave), after W1.2 +
W2.8 + W2.9 carry the **master** to end-to-end decode, `decodeDockedSlaves`
(decoder.c:3954) invokes `decodeSlave` on each docked slave. `decodeSlave` does:

```
readColorPaletteInSlave(matrix, symbol, data_map)   // decoder.c:2235 — reads palette region
color_number = pow(2, symbol->metadata.Nc + 1)      // decoder.c:2243 — = 2 for Mode 0
normalizeColorPalette(symbol, norm_palette, ...)    // decoder.c:2245 — normalises garbage
getPaletteThreshold(...) / decodeSymbol(...)         // decoder.c:2252/2256 — decode against garbage
```

For Mode 0, the slave's palette region — like the master's — contains **data modules, not
palette modules** (Mode 0 fixtures do not embed a palette per the custom extension
convention). So `readColorPaletteInSlave` populates `symbol->palette` with garbage, and
every downstream lookup classifies against it. This is the **identical mechanism** W2.9
diagnosed for the master via the v11 BITS_COLLECTED Heisenberg evidence (8 distinct
first-16-bit patterns across attempts = palette-lookup-against-garbage). No new mechanism;
same bug, unfixed call site.

## Viability of the W2.10 fix — confirmed

The load-bearing precondition is that a slave inherits `Nc=0` from its host, so that
`color_number` resolves to 2 and the `{K, W}` synthesis target is correct. **Confirmed** at
decoder.c:1058 (`decodeSlaveMetadata`):

```c
host_symbol->slave_metadata[docked_position].Nc = host_symbol->metadata.Nc;
```

For a Mode 0 master (`Nc=0`), the slave inherits `Nc=0` → `color_number = pow(2, 0+1) = 2`.
The synthesis target `{K=(0,0,0), W=(255,255,255)}` is therefore correct for the slave.

## Resolution (W2.10) — IMPLEMENTED (decoder slave palette synthesis)

A Mode 0 palette synthesis block was inserted in `decodeSlave`, immediately after the
`readColorPaletteInSlave` error block (now decoder.c:2241) and before `normalizeColorPalette`,
gated on `g_mode0_decode`. Structurally identical to W2.9 (same byte layout, same diag marker
shape):

```c
if (g_mode0_decode)
{
    const jab_int32 panel_stride = 2 * 3;  // color_number=2 × 3 channels
    for (jab_int32 p = 0; p < COLOR_PALETTE_NUMBER; p++)
    {
        // Index 0: K (black)
        symbol->palette[p * panel_stride + 0 * 3 + 0] = 0;
        symbol->palette[p * panel_stride + 0 * 3 + 1] = 0;
        symbol->palette[p * panel_stride + 0 * 3 + 2] = 0;
        // Index 1: W (white)
        symbol->palette[p * panel_stride + 1 * 3 + 0] = 255;
        symbol->palette[p * panel_stride + 1 * 3 + 1] = 255;
        symbol->palette[p * panel_stride + 1 * 3 + 2] = 255;
    }
    JAB_DIAG_INFO(("DIAG_MODE0_SLAVE_PALETTE_SYNTHESIZED ..."));
}
```

### Three implementation-time checks — all cleared at build time

1. **Slave `metadata.Nc` populated before `decodeSlave`.** Inheritance is wired at
   decoder.c:1058 into `host_symbol->slave_metadata[pos]`; colour-mode cascades work today, so
   the path is live. ✓ Relied upon (not re-traced — colour cascades are the standing proof).
2. **Cursor advance — VERIFIED.** `readColorPaletteInSlave` does **not** mis-advance for Mode 0:
   its metadata-palette `while` loop guard is `color_counter=2 < MIN(color_number=2,64)=2`,
   which is **immediately false**, so the loop body never runs — identical to the master. The
   only `return` paths are a malloc-failure `FATAL_ERROR` and a trailing `JAB_SUCCESS`, so the
   read **succeeds** for Mode 0 (writing a garbage palette we then overwrite), exactly as the
   master's `readColorPaletteInMaster` does. The insertion after the error block is therefore
   reachable. ✓
3. **Regression gate.** `g_mode0_decode == 0` for colour modes (Nc=1..7) → the block is dead
   code → byte-for-byte zero impact on existing colour-mode cascades. ✓ Whole library builds
   and links clean with all three fixes present.

## The necessary-but-not-sufficient caveat — slave DETECTION (CONFIRMED, outcome (ii))

W2.10 (palette synthesis) addresses the slave **decode** stage only. A slave is first
**detected** — geometrically located and perspective-sampled — by `findSlaveSymbol`
(detector.c:2739) and `detectSlave` (detector.c:3905). The 2026-06-02 trace (below)
confirms this path **does** carry its own colour assumption that breaks for monochrome.

The master required **two** Mode 0 interventions; the slave needs **three** — an extra
**encoder** fix because a slave's corners are *alignment patterns* the master (located by
finder patterns) never exercised:
- detector-side (master): the `g_mode0_decode` finder-pattern **quadrant** type-assignment
  hack (detector.c:1536–1546) + the chroma-channel skip in `crossCheckPattern`
  (detector.c:961), because monochrome strips the colour that normally identifies FP0–FP3;
- decoder-side (master): W2.9 palette synthesis;
- **encoder-side (slave): W2.10-enc** — paint a K↔W bullseye into the slave docking APs
  (encoder.c:1323–1327), which Mode 0 was drawing solid-black (no contrast) and therefore
  geometrically undetectable;
- detector-side (slave): **W2.10-b** — retarget the AP centre-colour gate to black + skip the
  cyan green-channel confirmations for *alignment* patterns;
- decoder-side (slave): **W2.10** — slave palette synthesis, the W2.9 sibling.

### The confirmed mechanism — why slave detection fails on monochrome

A docked slave has **no finder patterns of its own**; its four corners are *alignment
patterns* (AP0–AP3), located by `findSlaveSymbol` (detector.c:2856–2900) via
`findAlignmentPattern`. Two are **mandatory** (ap1/ap2 at the docking edge — detector.c:2860/2870
return `JAB_FAILURE` if not found) and at least one of the two far corners (ap3/ap4) must
also be found. So slave detection cannot complete without ≥3 successful AP detections.

`findAlignmentPattern` and its cross-checker are hardcoded to the **cyan** core colour:
- `core_color_r = jab_default_palette[AP*_CORE_COLOR*3]` (detector.c:2602–2620), AP0–AP3
  core = palette index **3 = cyan = (0,255,255)**;
- `crossCheckPatternAP` (detector.c:2503) cross-checks the **green** channel against cyan's
  G=255 via `crossCheckColor(ch[1], 255, …)` at lines 2545/2572/2579, plus a **blue**-channel
  horizontal check at line 2518.

But the encoder draws Mode 0 alignment patterns **black** — and worse, draws the **entire AP
solid black with no internal contrast**: at encoder.c:1323–1327 both the centre (k=0 →
`apn_core_color_index[0]`) and the ring (k=1 → `apx_core_color_index[0]`) resolve to index
**0** (encoder.h:68–69). This breaks slave-AP detection at **two independent layers**, and the
first is the one the original W2.10-b trace under-weighted:

1. **Geometry (the deeper failure — found only at implementation time).** With centre *and*
   ring both black, the AP is a uniform black blob — there is **no** centre→ring→outside
   transition for the *geometric* run-length checks
   (`crossCheckPatternHorizontalAP`/`Vertical`/`Diagonal`) to lock a centre onto. This defeats
   detection **before any colour test runs**, so it cannot be repaired in the detector alone —
   the encoder must paint a real K↔W bullseye (**W2.10-enc**). The original "skip the chroma
   checks" scope would have shipped a detector that still finds nothing.
2. **Chroma.** Even given a detectable bullseye, in pure {K, W} every pixel has **R=G=B**, so
   the cyan signature — R-dark, G-bright, B-bright (channels that *differ*) — is structurally
   unrepresentable. The green cross-check at detector.c:2545 can never pass, and the B-channel
   horizontal centre gate (2518 → 2419) rejects the black centre. These chroma confirmations
   must be neutralised for Mode 0 (**W2.10-b**).

With both unfixed, `findAlignmentPattern` returns not-found and `findSlaveSymbol` aborts at
detector.c:2864 ("first alignment pattern in slave not found"). **No `g_mode0_decode` branch
existed in the encoder AP path or either detector function** (grep-confirmed).

### Why the master never tripped this

`jab_ap_num[0..4] = 2` (encoder.h:266) → versions 1–5 have `(2×2 − 4) = 0` *interior*
alignment patterns (encoder.c:668–670); the four grid corners ARE the finder patterns.
Interior APs first appear at v6 (`jab_ap_num=3`). The master is located by *finder* patterns
(which got the Mode 0 quadrant hack), and every Mode 0 fixture to date is v1 → the AP-detection
colour dependency was **dormant** for the master. It is **not** dormant for slaves: a slave's
corners are alignment patterns at *every* version, so the cyan-keyed AP search is exercised by
the very first cascade.

> **Bottom line: Mode 0 cascading needs THREE coordinated fixes in a hard dependency order —
> enc → detect → decode.** W2.10-enc (encoder bullseye) is the newly-found foundation: without
> it the slave APs are geometrically undetectable, so W2.10-b's chroma-skip would find nothing
> and W2.10's palette synthesis is never reached. The original "two fixes" framing missed the
> encoder because the W2.10-b *detection* trace inspected the decoder's **read** path, not the
> encoder's **write** path — a write/read asymmetry carried forward as a lesson below.

## Investigation (W2.10-b — the slave-detection trace) — COMPLETE 2026-06-02

The trace executed the plan and reached a definitive verdict: **outcome (ii)**.

| Step | Finding |
| ---- | ------- |
| 1. Catalogue colour-dependent ops in `findSlaveSymbol`/`detectSlave` | `detectSlave` (3905) is colour-free; it delegates corner-finding to `findSlaveSymbol` (2739). `findSlaveSymbol` is pure docking geometry **except** its 3–4 calls to `findAlignmentPattern` (2860/2870/2895/2900). Those are the only colour-dependent operations — and they are load-bearing (2 mandatory). |
| 2. Existing `g_mode0_decode` guard? | **None.** `findAlignmentPattern` (2595) and `crossCheckPatternAP` (2503) have no Mode 0 branch. Both demand the **cyan** AP core (index 3): R-scan seeded from cyan, G-channel `crossCheckColor(ch[1],255,…)` (2545/2572/2579), B-channel check (2518). |
| 3. Classify | **(ii)** — slave detection has its own colour-keyed path. Monochrome {K, W} (R=G=B everywhere) cannot satisfy the cyan cross-check; AP detection fails → `findSlaveSymbol` aborts at 2864. **W2.10-b is required.** |
| 4. Status updated | Done (see Status field above). |

## Resolution (W2.10-enc) — IMPLEMENTED (encoder bullseye; the foundation)

`crossCheckPatternAP`'s geometric run-length checks need a real centre→ring transition to
localise the AP. Mode 0 was painting the slave docking APs **solid black** — both centre and
ring index 0 — so there was nothing to localise. The fix at encoder.c:1323–1327 mirrors the
master FP Mode 0 fix (encoder.c ~1273): for `Nc==0`, alternate **K centre (k=0 → index 0) /
W ring (k=1 → index 1)** instead of `apn/apx_core_color_index[0]` (both 0):

```c
if (Nc == 0) {
    jab_byte mode0_color = (k%2) ? 1 /*W*/ : 0 /*K*/;
    ap0_color_index = ap1_color_index = ap2_color_index = ap3_color_index = mode0_color;
} else {
    ap0_color_index = … = (k%2) ? apx_core_color_index[Nc] : apn_core_color_index[Nc];  // unchanged
}
```

The index convention (0=K, 1=W) matches the synthesized **decode** palette (W2.10) and the
detector's black centre-gate (W2.10-b): write side and read side agree on black-centre /
white-ring. The `else` branch is byte-for-byte the original for colour modes.

## Resolution (W2.10-b) — IMPLEMENTED (detector AP de-chroma)

Two changes in `crossCheckPatternAP` / its horizontal helper, both gated on `g_mode0_decode`.
**The mechanism differs from the original scope** ("skip the B-horizontal call at 2518"),
which would have been a *bug* — see mechanism 1.

1. **Centre-gate retarget (NOT a skip), `crossCheckPatternHorizontalAP` ~detector.c:2419.**
   Insert `if(g_mode0_decode) core_color = 0;` after the `ap_type` switch, before the
   `if(row[centerx] != core_color) return -1;` gate. This makes **all four** horizontal AP
   calls (R 2515, B 2518, R-recheck 2553, B-recheck 2561) expect a **black** centre pixel
   instead of the per-channel cyan value, while leaving the run-length state machine — which is
   purely geometric — fully active. *Why not skip the B call at 2518 as first scoped?* Its
   return feeds `center.x = (l_centerx[0] + l_centerx[2]) / 2.0f` (detector.c:2522) and the
   module-size average (2524). Skipping it leaves `l_centerx[2]=0`, **halving** the computed
   centre and destroying localisation. The retarget keeps both channels contributing a real
   centre.
2. **Green chroma-confirmation skip, `crossCheckPatternAP` 2545 / 2572 / 2579.** Prefix each of
   the three `crossCheckColor(ch[1], core_color_in_green_channel, …)` calls with
   `!g_mode0_decode &&`. Unlike the single-pixel horizontal gate, `crossCheckColor` scans a
   **2-module run** demanding a continuous band of one colour; a Mode 0 AP has only a ~1-module
   black centre inside a white ring, so **no** single expected colour (black *or* white) can
   satisfy its consecutive-mismatch tolerance (`max(module_size/7, 3)`) — the check is
   structurally inapplicable to monochrome and is dropped. The R/B horizontal + vertical +
   diagonal geometric checks (all retained) fully localise the bullseye, so no geometric
   assurance is lost — this is why a *retarget* works for the single-pixel gate but a *skip* is
   the only option for the run-scan.

`findAlignmentPattern`'s R-channel scan needs **no change**: `core_color_r` is cyan's R = 0,
which equals black's R = 0, so the dark-R search already locates a black Mode 0 AP core.

### Implementation-time checks (W2.10-b) — cleared

1. **Don't over-skip — and don't over-skip the *call* either.** R/B/diagonal geometric checks
   remain; only the green *chroma confirmation* is dropped, and the horizontal black gate is a
   *retarget*, not a *skip* (mechanism 1). ✓
2. **Regression gate.** `g_mode0_decode == 0` for colour modes → both changes are inert →
   byte-for-byte zero impact on colour-mode AP detection (large colour cascades, v≥6 interior
   APs). ✓ builds + links clean.
3. **Ordering.** enc → detect → decode. W2.10-enc must ship with W2.10-b (an undrawn bullseye
   is undetectable); W2.10 is unobservable until detection succeeds. ✓ all three landed jointly
   in `ws-mode0-slave-cascade-fix`.

## Validation criteria (TDD empirical anchor — code landed, fixture still to be built)

Code for all three fixes is in `ws-mode0-slave-cascade-fix` and builds clean, but **no
cascaded Mode 0 trace exists yet** (all Mode 0 fixtures to date are single-symbol). The TDD
anchor must therefore be created: **re-encode** a Mode 0 master + 1 docked slave fixture *with
the W2.10-enc encoder* (a pre-W2.10-enc fixture would have solid-black, undetectable slave APs
and cannot validate the detector/decoder fixes). Post-W2.10-enc + W2.10-b + W2.10 (**all three
required**) scan should show, **in dependency order**:
- **(geometry — W2.10-enc)** the encoded slave docking APs present a K↔W bullseye (visually
  verifiable in the rendered fixture before any scan).
- **(detection — W2.10-b)** slave alignment patterns located: no "alignment pattern in
  slave … not found" errors at detector.c:2864; `findSlaveSymbol` returns `JAB_SUCCESS`.
  *This gate fails first if the encoder still draws solid-black APs, or if only W2.10 ships.*
- **(decode — W2.10)** `DIAG_MODE0_SLAVE_PALETTE_SYNTHESIZED` markers firing (one per docked slave)
- slave palette hash deterministic across attempts (vs the master's pre-W2.9 noisy hash)
- slave PartII/data decode succeeding
- master + slave payloads concatenated into the final decoded message
- **Regression:** nc=1..7 master+slave fixtures **encode and** decode unchanged — guards all
  three Mode 0 branches (W2.10-enc encoder `else`, W2.10-b detector gates, W2.10 decoder block),
  each inert for colour modes (`Nc!=0` / `g_mode0_decode==0`).

## Lesson — trace the *write* path, not just the *read* path

The W2.10-b detection trace was thorough about the decoder/detector **read** path and correctly
proved slave-AP detection was Mode 0-broken — but it scoped the fix as decoder-side only because
it never crossed over to the encoder's **write** path. The encoder was drawing the very pattern
the detector was failing to find, *also* wrong, in a way no amount of detector patching could
repair (a solid-black AP has no geometry to detect). The two-line cost of reading
encoder.c:1323–1327 before writing the fix surfaced the whole W2.10-enc prerequisite; a naive
"implement the two scoped fixes" pass would have shipped a detector that still finds nothing and
burned a full ~60-min encode→print→scan cycle to discover it empirically. **For any
detector/decoder fix on a symmetric encode/decode pipeline, inspect the encoder that produces the
feature before concluding the fix is read-side only.** (Mirrors the prior-cycle "verify the
mirror claim before building" lesson.)

## Cross-references

- `src/jabcode/encoder.c` (encoder.c:1323–1327 — **W2.10-enc fix site**; `Nc==0` K-centre/W-ring
  bullseye for slave docking APs, replacing the solid-black `apn/apx_core_color_index[0]`)
- `src/jabcode/decoder.c::decodeSlave` (decoder.c:2218 — **W2.10 fix site**; synthesis inserted
  after the `readColorPaletteInSlave` error block at 2241, before `normalizeColorPalette`)
- `src/jabcode/decoder.c::readColorPaletteInSlave` (decoder.c:2235 — Mode 0 garbage source)
- `src/jabcode/decoder.c::decodeSlaveMetadata` (decoder.c:1058 — confirms slave inherits host Nc)
- `src/jabcode/decoder.c::decodeDockedSlaves` (decoder.c:3954 — cascade driver)
- `src/jabcode/decoder.c::decodeMaster` (decoder.c:2099 — W2.9 master synthesis, the template)
- `src/jabcode/detector.c::findSlaveSymbol` (detector.c:2739 — slave corners are APs; calls
  `findAlignmentPattern` at 2860/2870/2895/2900; aborts at 2864 when AP not found)
- `src/jabcode/detector.c::detectSlave` (detector.c:3905 — confirmed colour-free; delegates to findSlaveSymbol)
- `src/jabcode/detector.c::crossCheckPatternAP` (detector.c:2503 — **W2.10-b fix site (part 2)**;
  skip the three G `crossCheckColor` calls 2545/2572/2579 via `!g_mode0_decode &&`. NOTE: the
  B-horizontal call at 2518 is **kept**, not skipped — skipping it would break the center.x
  average at 2522)
- `src/jabcode/detector.c::crossCheckPatternHorizontalAP` (detector.c:2398 — **W2.10-b fix site
  (part 1)**; `if(g_mode0_decode) core_color = 0;` before the centre-pixel gate at ~2419, so all
  four horizontal AP calls expect a black centre while the geometric run-length scan stays live)
- `src/jabcode/detector.c::findAlignmentPattern` (detector.c:2595 — cyan-seeded R-scan 2602–2620;
  needs NO change, R=0 for both cyan and black)
- `src/jabcode/detector.c` (detector.c:961 — master FP chroma-skip; the exact precedent W2.10-b mirrors)
- `src/jabcode/detector.c` (detector.c:1536–1546 — master's Mode 0 quadrant FP-type hack)
- `src/jabcode/encoder.h` (encoder.h:52–56 — `AP*_CORE_COLOR=3`=cyan; encoder.h:68–69
  `apn/apx_core_color_index[0]=0`=black for Mode 0; encoder.h:266 `jab_ap_num` — v1–v5 have 0 interior APs)
- `src/jabcode/encoder.c` (encoder.c:668–670 — `(ap_x*ap_y−4)` interior-AP count; encoder.c:1184–1185 — Mode 0 AP black cores)
- `H_nc0_partII_ldpc.md` — W2.9, the master-side sibling whose master-only scope this entry continues
- ISO/IEC 23634:2022 Section 4.4.1.2 + Table 6 — clause permitting Mode 0 as user-defined colour mode
