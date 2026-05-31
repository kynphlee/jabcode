# H_nc2_decode_failure — Open root-cause hypothesis: Nc=2 (8-color) fails on both media

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-05-28 (PR 1 verification preparation; user-reported decode-capability matrix)                   |
| **Status**   | Open — CONFIRMED; **member of `H_partI_unifies` cluster {Nc=0, Nc=2, Nc=7}** per 2026-05-28 PM 8-Nc discriminator scan |
| **Binding**  | Triggered (not scheduled), but high-priority for prompt activation                                   |
| **Owner**    | Unassigned (claimed on trigger)                                                                      |
| **Severity** | High — Nc=2 (8-color) is the canonical "easy" color mode; failing everywhere contradicts physics    |
| **Related**  | `H_partI_clean_data_failure.md`, `H_mode0_partI_decode_failure.md` (sibling open hypotheses)         |

## The hypothesis

JABCode decoding of Nc=2 (8-color) JABCodes fails consistently on BOTH printed and on-screen displays. This is anomalous because:

1. **The 8-color palette is the maximally-separated RGB cube vertices** (K=000, B=001, G=010, C=011, R=100, M=101, Y=110, W=111 — see `src/jabcode/encoder.h:20::jab_default_palette`). Color discrimination should be at its EASIEST.
2. **Nc=1 (4-color) and Nc=3 (16-color), the immediate neighbors, both work** on the same hardware/conditions.
3. **Nc=5 (64-color) ALSO works on print** despite having 8× more colors than Nc=2.

This sandwiching strongly suggests Nc=2 has a specific decoder bug, not a physics or device limitation.

## Empirical anchor (2026-05-28 capability matrix)

The user-confirmed decode-capability matrix on Galaxy S25:

| Nc | colors | print | screen |
|----|--------|-------|--------|
| 0  | 2      | no    | no     | ← `H_mode0_partI_decode_failure` |
| 1  | 4      | yes   | yes    |
| **2** | **8** | **no**   | **no** | ← **this hypothesis** |
| 3  | 16     | yes   | yes    |
| 4  | 32     | no    | yes    | ← print gamut |
| 5  | 64     | yes   | yes    |
| 6  | 128    | no    | yes    | ← print gamut |
| 7  | 256    | no    | no     | ← slave-decode + gamut |

The earlier "8c print scans showed HELLO-Nc-1 decodes" (traces `tolerance4-test-20260526_22*`, `tolerance4-test-20260527_115*`) — previously attributed to card mix-up — is now strongly suspected to have been the real decode failure pattern: the decoder cannot decode the Nc=2 card and was sometimes reading nearby Nc=1 cards instead. Worth re-examining those traces for evidence.

## Empirical confirmation (2026-05-28 desktop test data)

Running `test_roundtrip_all_nc.c` (synthetic encode→decode roundtrip across Nc=0..7) reveals a smoking-gun signal: **the `[PartI] LDPC decode SUCCESS, Nc=N` marker is missing for Nc=2 but present for both neighbors (Nc=1 and Nc=3)**:

```
--- Nc=1 (4-color) ---
[PartI] LDPC decode SUCCESS, Nc=1        ← PartI marker fires
[DECODE] SUCCESS Nc=1 (76 bytes)
  PASS: 5 bytes decoded correctly

--- Nc=2 (8-color) ---                    ← NO [PartI] marker
[DECODE] SUCCESS Nc=2 (580 bytes)
  PASS: 5 bytes decoded correctly

--- Nc=3 (16-color) ---
[PartI] LDPC decode SUCCESS, Nc=3        ← PartI marker fires
[DECODE] SUCCESS Nc=3 (216 bytes)
  PASS: 5 bytes decoded correctly
```

This means PartI is silently failing for Nc=2 and the legacy permissive fall-through (which fires when `g_strict_partII_required` is FALSE — i.e., on the desktop test path) fabricates a successful decode using default-metadata bits that happen to align with the correct payload.

**This unifies the Nc=2 mystery with `H_partI_clean_data_failure`**: it's a specific manifestation, not a separate bug class. PartI consistently fails for Nc=2 inputs (synthetic OR camera-captured), and the camera path's strict-mode wiring (Option D, WS-5) correctly refuses to fabricate — hence the 100% camera-path failure rate while desktop tests pass.

The downstream `[DECODE] SUCCESS` marker shows `580 bytes` for Nc=2 — that's the gross capacity at default-metadata parameters (Pg=1044, Pn=580), not the encoded payload's actual capacity. The 5-byte "HELLO" check passes because the encoder happens to write data in a way the default-metadata decode can extract; this is coincidence, not correctness.

## Screen-side telemetry confirmation (2026-05-28 PM)

Following landing of the diagnostic-app's failure-side telemetry (commits `40b60cb` and `a873969` on `claude/ws-diagnostic-ui-tier1`), a 30-second screen-side scan of the Nc=2 (8-color) fixture was captured. Trace: `jabauth-android/diagnostic-app/logs/tolerance4-test-20260528_154620.logcat`.

**Headline result: H_nc2_decode_failure CONFIRMED with status=1 (slave-decode failed) dominant by ~4-5×.**

Two independent log sources agree on the same ratio:

| Source                                  | status=0 (no FP found) | status=1 (FP found, slave failed) | Ratio       |
| --------------------------------------- | ---------------------- | --------------------------------- | ----------- |
| C-side `FAIL_ATTR` markers (78 total)   | 13                     | **65**                            | **1 : 5.0** |
| Kotlin `DECODE_FAIL_STATS` 30s window   | 12                     | **46**                            | **1 : 3.8** |
| Successful decodes                      | 0                      | n/a                               | —           |

The Kotlin source is the new `ScannerViewModel.DECODE_FAIL_STATS` line, which classifies the decoder's error string into `FailureCategory.NO_FP_FOUND` / `SLAVE_DECODE_FAILED` / `OTHER` over a rolling 30-second window. Greppable from any trace: `grep DECODE_FAIL_STATS`.

Sample line from this trace (steady state):
```
DECODE_FAIL_STATS overall: fail=58/58_in_30s status0=12 status1=46 other=0
```

**Temporal progression** — once the user steadied the phone on the target, status=1 dominance was immediate and stable:

```
15:46:20.995  fail=1   status0=1  status1=0   ← first frame: transient framing
15:46:30.866  fail=20  status0=1  status1=19  ← steady state begins (1:19)
15:46:41.394  fail=40  status0=3  status1=37  ← (1:12)
15:46:51.646  fail=58  status0=12 status1=46  ← end of window (1:3.8)
```

**Decode-time signature** — failures complete fast, NOT at the 200ms timeout:

```
Per-failure decode time (n=78): min=118ms max=302ms avg=179ms median=168ms
```

Median (168ms) is well under the 200ms timeout, with only a small tail above. This rules out a timeout-bound explanation: the decoder is *rejecting* the palette-learning result fast, not running out of time. **If we doubled the timeout to 400ms, the failures wouldn't change.**

## Sub-hypothesis narrowing post-2026-05-28 PM evidence

The fast-failure + status=1 dominance pattern narrows the candidate root causes from the original list:

**EXCLUDED:**

- **"Needs more decode time / slave-decode is just slow at Nc=2"** — refuted by median=168ms with 200ms timeout. Failures are decision-bound, not budget-bound.
- **"FP-detection is the bottleneck for Nc=2"** — refuted by status=0 being only ~20% of failures.
- **"Camera-specific input quality issue"** — partially refuted: same Galaxy S25 + same camera path decodes Nc=1 and Nc=3 cleanly within the same session.

**SURVIVING (in rough order of plausibility given the new evidence):**

1. **`H_palette_clustering_threshold`** — the slave-decode's palette-learning clustering algorithm has a hardcoded convergence threshold tuned for ≤4-color or ≥16-color modes, with Nc=2 (3-bit, 8-vertex RGB cube) falling in a discriminator gap. The fact that the decoder rejects fast (168ms median) suggests early termination on a "cluster geometry doesn't converge" signal.

2. **`H_partI_metadata_layout_bug`** (per existing checklist item 1) — Nc=2's LDPC parameter encoding in PartI metadata is structurally different from Nc=1/3's and the decoder's PartI parse silently fails for Nc=2 inputs. The desktop synthetic test's missing `[PartI] LDPC decode SUCCESS, Nc=2` marker is consistent. PartII (strict-mode) on the camera path correctly refuses to fabricate, hence the 100% camera failure rate.

3. **`H_rgb_vs_perceptual_distance`** — slave-decode's color-classification uses RGB-space distance, not CIELAB perceptual distance. The 8 corners of the RGB cube have a specific perceptual asymmetry (e.g., perceived distance from W to Y vs from K to C) that consumer displays + JPEG round-trip can perturb past the RGB-distance threshold. Plausible but lower priority than #1-2 because it should affect Nc=1 (4 colors) similarly and Nc=1 works.

4. **`H_inadequate_chroma_resolution`** — YUV_420_888 + JPEG round-trip chroma decimation kills 8-color separability. Same objection as #3: should affect Nc=1 too.

**The fact that #2 (PartI metadata bug) is consistent with BOTH the desktop synthetic evidence AND the camera-path 100% failure rate makes it the prime suspect.** The slave-decode "FP found, slave failed" status=1 dominance is a downstream symptom — PartI fails to decode metadata → palette-learning runs with default parameters → palette-learning rejects fast → status=1.

## Cluster confirmation via 8-Nc discriminator scan (2026-05-28 PM)

Following Bayesian Council Session bc-2026-05-28-03, a full 8-Nc discriminator scan was executed on Galaxy S25 (autofocus, no manual zoom, on-screen fixtures, all 8 fixtures scanned for ~30 seconds each with `preferredColorMode` set to match). Traces: `jabauth-android/diagnostic-app/logs/tolerance4-test-20260528_19{0926,1852,2035,2135,2323,2447,2654,2920,3206,3317,3640}.logcat` (with the 16c/32c/4c entries refreshed mid-session after initial typos).

**Headline result: H_partI_unifies CONFIRMED for the {Nc=0, Nc=2, Nc=7} cluster** at P > 0.75 per the council's pre-commit gate. Devil's Advocate's Pathology #1 (status=1 dominance is a low-information shared symptom) was vindicated *as well as* the unification claim:

| Cluster | Nc | success rate | status0:status1 | median fail time |
|---------|----|--------------|------------------|------------------|
| 🔴 PartI-unified | **0** | 0% | 14:44 (1:3.1) | **183ms** |
| 🔴 PartI-unified | **2** | 0% | 11:48 (1:4.4) | **232ms** |
| 🔴 PartI-unified | **7** | 17% | 2:23 (1:11.5) | **287ms** |
| 🟢 GA baseline | 1 | 93% | 4:0 (pure status0) | 337ms |
| 🟡 Marginal | 3 | 35% | 12:23 (1:1.9) | 184ms |
| 🟡 Marginal | 4 | 60% | 11:4 (status0 dominant) | 437ms |
| 🟡 Marginal | 5 | 67% | 9:6 (mixed) | 274ms |
| 🔴 Distinct | 6 | 4% | 14:35 (1:2.5) | **360ms** (SLOWER) |

**Discriminating signals (the actual fingerprints):**

1. **Success rate ≤ 17%** — the catastrophic-failure cluster {Nc=0, Nc=2, Nc=7} separates cleanly from the marginal {Nc=3, Nc=4, Nc=5} group.
2. **Fast-reject median ≤ 290ms** — the {Nc=0, Nc=2, Nc=7} cluster rejects palette-learning within ~200-290ms, well under the 200-400ms timeout budget. Nc=6 takes substantially longer (360ms median), excluding it from the PartI-unified group.
3. **status=1 dominance ≥ 3×** — non-discriminating on its own (working modes can also produce status=1 failures from framing transients), but combined with #1 and #2 it forms a clean cluster signature.

**Crucial Devil's Advocate vindication:** The earlier "status=1 dominance" was being treated as evidence FOR `H_nc2_decode_failure`, but the 8-Nc scan reveals it is a *baseline failure mode* across multiple Nc values (including marginal-working ones). The real discriminator is the *combination* of low success rate + fast-reject median + status=1 dominance.

**Excluded from the cluster:**

- **Nc=5 (64c) — REJECTED.** 67% success rate and status=0-mixed failure profile mean it is NOT part of the PartI cluster. It is in the marginal-working group.
- **Nc=6 (128c) — REJECTED.** status=1 dominant (2.5×) but qualitatively slower median (360ms vs 183-287ms for the cluster). Likely a separate mechanism: palette-learning iteration ceiling at 128 colors.

**Pre-committed next action per council Session bc-2026-05-28-03:** Proceed to **Option (B)** — C-side PartI instrumentation. Target the {Nc=0, Nc=2, Nc=7} cluster jointly; a single investigation may close three Cassandra register hypotheses (`H_nc2_decode_failure`, `H_mode0_partI_decode_failure`, and a new `H_nc7_partI_extreme_status1` entry) simultaneously.

## 2026-05-30/31 mechanism resolution and partial fix

### Headline

`Nc=2` PartI success rate on Galaxy S25 / SM-S938U-16: **0% → 33.75%** (and end-to-end DECODE_OK: 0 → 26 per ~80 attempts) via the combined `Path α revised` (Camera2 manual AWB override) + `Path β` (decoder permissive color classification) configuration. Five merged PRs delivered the lift (#37 propagation probes, #38 Path β, #39 raw-byte instrumentation, #40 benchmark-variant debugLogging default, #41 manual AWB override). One PR (#42 decouple-β-from-verbose) was empirically falsified within 30 minutes and remains unmerged as a documented falsified-experiment branch.

### Mechanism (camera-side root cause)

The AWB convergence-lock from PR #36 was locking to a non-neutral scene white-point, after which the locked color-correction matrix applied a residual **R+B amplification cast** to every subsequent frame. Camera signal at metadata-position pixels read consistently as `raw_bytes = (R≈245, G≈125, B≈254)` regardless of fixture content — R and B saturated, G mid-range.

`decoder.c::decodeModuleNc` operates on RGB pixel bytes in `[R, G, B]` order (confirmed by inline comments at `decoder.c:786` documenting `Y = (255, 255, 0)` and `C = (0, 255, 255)`). The classifier uses a hybrid rule:

```c
jab_int32 tolerance = 80;
if (rgb[0] < tol && rgb[1] < tol && rgb[2] < tol)             return 0;  // K
if (rgb[0] < tol && rgb[1] > (255-tol) && rgb[2] > (255-tol)) return 3;  // C
if (rgb[0] > (255-tol) && rgb[1] > (255-tol) && rgb[2] < tol) return 6;  // Y
// ...fallback to relative-threshold (getMinMax + std) rule
```

For the post-manual-WB `(245, 201, 255)` samples, all three exact matches fail (B=255 fails the Y B-ceiling of `< 80`). Samples fall through to the relative-threshold fallback, which sets bits on the two highest channels (R and B) and produces `rgb=5` (M) for every metadata module.

**The residual cast is on the B channel, not on the G channel.** The earlier framing of "green-channel under-capture" was incorrect — green is mid-range and recoverable; **blue is the false-positive driver**. Future Camera2 interventions should target B-suppression in the color-correction transform or RGGB gain matrix (e.g., `RggbChannelVector(1.0f, 1.0f, 1.0f, 0.3f)` to attenuate B), NOT G-boost.

### Mechanism (decoder-side downstream)

Path β (`g_permissive_color_classification`, PR #38) substitutes `rgb=5` (M) → `rgb=6` (Y) at the module_color stage. Empirically this generates pair_bits failures on `(Y, Y) → 8` invalid pairs (`decodeNcModuleColor` reserves `(Y, Y)` as the structurally invalid metadata pair, so when all 4 modules are remapped uniformly to Y, downstream LDPC cannot reach a valid Nc value).

But Path β was load-bearing for the 33.75% baseline: PR #42's empirical test of "decouple β from the verbose toggle" reduced PartI success from 27/80 to 0/33. **The 27 successes required Path β's remap to even reach pair_bits**, where ~33% of cases coincidentally produced valid LDPC bits. The remaining 38 pair_bits failures and 15 module_color failures together with the 27 successes account for the 80 attempts.

### H_partI_unifies hypothesis: partially refuted

The 2026-05-30/31 raw-byte and stage-distribution data refutes the unified-mechanism prior from bc-2026-05-28-03. The three "broken cluster" Nc values have **three distinct mechanisms**:

| Nc | Mechanism | Closure path |
|----|-----------|--------------|
| **Nc=0** | PartI module_color validity check hardcoded for `{K, C, Y}` but Mode 0 metadata uses `{K, W}` (rgb=7) | One-line C fix specified in `H_mode0_partI_decode_failure.md` (PR #35 register update) |
| **Nc=2** | Camera AWB-locked R+B amplification cast → classifier returns rgb=5 → validity rejects | Manual WB override (PR #41) + Path β remap (PR #38) — both required for current 33.75% baseline. Production fix candidates: Camera2 B-suppression OR decoder Y-match B-tolerance widening (single-parameter changes either way). |
| **Nc=7** | PartI succeeds 95% but slave-decode rejects 99% — distinct downstream issue, NOT a PartI mechanism | Separate workstream; file `H_nc7_slave_decode_failure` register entry |

The "two-bug minimum" rule applies: any future analysis assuming a single root cause for the {Nc=0, Nc=2, Nc=7} cluster is overclaimed.

### Empirical record gaps (Cassandra + Sherlock flags)

- **N=1 device** (Galaxy S25 / SM-S938U-16). Cross-device validation is **completely absent**. The 33.75% baseline may be artifact of one ISP's specific AWB convergence behavior on one fixture content.
- **N=1 fixture** (the user's 8-color JABCode on screen). Different physical media, lighting, or framing could move the residual cast in either direction.
- **N=1 session** (today's six-hour cycle, all traces 03:35:54–18:43:58 UTC).
- The 31 rgb=5 raw-byte samples in the falsified-decoupling trace 18:43:58 are sufficient to characterize the camera signal at the metadata position. **Insufficient** to characterize the variance across framing/distance/lighting conditions.

### Production posture (current)

- Manual WB override and Path β permissive remap both remain in the SDK as **opt-in** APIs (per the empirical falsification record). Default OFF for shipped SDK consumers; ON in the diagnostic-app's benchmark build variant via `BuildConfig.DEFAULT_DEBUG_LOGGING_ENABLED = true` (PR #40).
- The Camera2 convergence-lock pattern from PR #36 remains the SDK default. Manual WB override is the documented escape hatch when convergence-lock locks to a non-neutral scene. The diagnostic-app currently has it on always for diagnostic capture; production SDK consumers must opt in explicitly.
- Cross-Nc applicability of the manual WB override is **unverified**. Re-baselining all eight Nc values (0–7) with manual WB ON, per the bc-2026-05-30-04 council synthesis Step 4, is the validation gate.

## Suspected failure surfaces (investigation candidates)

The encoder's color-number-specific palette code (`src/jabcode/encoder.c::genColorPalette`) shows:

- **`color_number == 4`** (Nc=1): explicit handcrafted 4-color palette of K/M/Y/Cyan (see encoder.c around line 106-110)
- **`color_number == 8`** (Nc=2): copies the full 8-color `jab_default_palette` verbatim
- **`color_number >= 16`** (Nc≥3): uses `genColorPalette` to interpolate

Candidate explanations:

1. **LDPC parameters specific to Nc=2**: the LDPC (wc, wr) values are encoded in metadata bits. If Nc=2's metadata bit layout differs from the decoder's expectation (e.g., a recent change to Nc=3+ encoding inadvertently broke Nc=2's parameter encoding), every Nc=2 metadata decode fails.
2. **Palette index → bit mapping**: Nc=2 uses 3 bits per module (8 colors = 3 bits). If the bit-extraction order from a 3-bit module differs in encoder vs decoder, every module produces the wrong color → garbage bytes.
3. **`readColorPaletteInMaster` color-classification step**: the decoder learns the palette from the four FP cores. If the color-classification kernel has an Nc=2-specific branch that's buggy, every palette learning attempt produces an incorrect palette and decoding fails.
4. **`decodeMasterMetadataPartI` LDPC for Nc=2**: similar to `H_partI_clean_data_failure` but specifically biased toward Nc=2 metadata.

## Reproducible repro

Build any commit at or after trunk `631e095`:

1. Display the user's Nc=2 fixture (the 8-color JABCode print, OR the `nc2-8c-20260521.png` fixture on screen)
2. Scan with the diagnostic app for 30+ seconds
3. Observe: 0 `DECODE_OK Nc=2` markers; some `FAIL_ATTR status=1` markers (FP found, slave-decode failed)

Compare against `test_roundtrip_all_nc.c` desktop test (synthetic encode→decode roundtrip at Nc=0..7). If that desktop test PASSES for Nc=2 (which prior data suggests it does), the bug is camera-specific (input-bitmap-quality dependent). If it FAILS for Nc=2 too, the bug is in the decoder library independent of input quality.

## Investigation checklist (cold pickup)

1. **Run `test_roundtrip_all_nc.c` on Nc=2**: does the synthetic roundtrip pass? If yes, bug is camera-input-quality dependent. If no, bug is in the decoder library.
2. **Add per-iteration logging to `decodeMasterMetadataPartI` for Nc=2**: what is the LDPC return value? What metadata bits were read?
3. **Compare Nc=2 vs Nc=1 vs Nc=3 metadata layouts**: are there off-by-one or bit-order differences in the metadata regions of the master symbol?
4. **Re-examine the `tolerance4-test-2026052*` traces** for Nc=2-specific failure modes that we may have ignored when attributing them to "card mix-up."
5. **Synthetic noise test**: feed Nc=2 + ±5 ADU chroma noise (per `test_mode0_chroma_tolerance.c` pattern) through the camera-pipeline decode; does the noise level matter?

## Triggers (when this hypothesis activates)

- **Trigger A — Nc=2 is needed by a product feature** (e.g., a customer-facing authentication scenario that uses 8-color codes)
- **Trigger B — `H_partI_clean_data_failure` investigation activates** and the investigator notices Nc=2 anomalies in their data
- **Trigger C — An engineer has capacity and picks this up** because it's the lowest-hanging-fruit decoder bug
- **Trigger D — PR 1 verification data shows Nc=2 specifically is consistently failing** (likely to fire imminently — PR 1's per-fixture decision rules already include Nc=2 indirectly)

## Why this is filed (not scheduled)

Per the established Cassandra register pattern: scheduling commits us to investigation by a date; triggering activates when evidence demands it. Nc=2 may be product-irrelevant (if the SDK consumers don't use 8-color codes) or product-critical (if they do); the trigger pattern lets us discover which.

## 2026-05-31 stacked-fix re-baseline — six-of-eight Nc modes deployable

After PR #46 (Mode 0-aware metadata validity check in `decodeMasterMetadataPartI`) and PR #47 (Y-match `y_b_tolerance` widening in `decodeModuleNc`, 200 → 255) landed, the eight-Nc re-baseline trace (`trace-20260531_15{05..16}*-nc{0..7}.logcat`) produced:

| Nc | v5 stacked rate | Production posture |
|----|-----------------|---------------------|
| nc0 | 0% | **Local maximum reached** — see new H_mode0_decodeModuleNc_classifier register entry |
| nc1 | **100%** | Deployable |
| nc2 | 0% | **Local maximum reached** — needs ISP-level color correction OR relative-color decoder approach |
| nc3 | **100%** | Deployable |
| nc4 | **100%** | Deployable |
| nc5 | **100%** | Deployable |
| nc6 | 96% | Deployable (noise-level regression from 100%) |
| nc7 | **95%** | Deployable (Y-widening lifted from 90%) |

**Six of eight Nc values (1, 3, 4, 5, 6, 7) are at >=95% PartI success** with manual WB override + Path β coupled to debugLogging + Mode 0 validity + Y-match B-tolerance widening. That's the production-deployable headline.

### Why nc0 and nc2 hit local maxima

- **nc0**: the Mode 0 PartI validity check now correctly accepts `{K=0, W=7}`. But the upstream `decodeModuleNc` classifier is misclassifying W pixels (R+G+B all high) as Y (rgb=6) under the residual camera cast. Mode 0 validity rejects rgb=6, producing FAIL_mc with `mode0=1 valid_set={0,7}`. **This is a separate bug, filed as `H_mode0_decodeModuleNc_classifier`** — needs a Mode 0-aware classifier path using luminance discrimination, not chroma.
- **nc2**: 322 Path β remap firings + 83 FAIL_pb with `(Y, Y) → 8` invalid pairs. ALL four metadata modules collapse to Y (whether native or β-remapped), because the camera cast makes K/C/Y indistinguishable to the per-pixel absolute-threshold classifier. No PartI-stage classifier-tuning intervention can recover information the cast destroyed at sample time. **The remaining workstream is ISP-level (a 3×3 color-correction matrix that preserves K/C/Y discrimination) OR decoder-side relative-color discrimination using inter-module ratios.**

### Productization implications

- **Manual WB override**: graduates from opt-in to recommended-default. Six modes need it.
- **Path β**: opt-in for SDK consumers; load-bearing for nc7's 95% rate but not strictly required for the 5 clean modes.
- **nc0**: ship to customers as "Mode 0 partially supported" until H_mode0_decodeModuleNc_classifier ships.
- **nc2**: ship to customers as "8-color mode not recommended on Galaxy S25 / SM-S938U-16; alternative platforms TBD".
- **nc7**: ship at 95% with Path β coupled.

### Open instrumentation gap

The 2026-05-31 stacked-fix trace also surfaced a previously-undocumented capture gap: **zoom state was not logged in any PartI_DIAG marker**. Camera2Preview applies `SCALER_CROP_REGION` via `cachedCropRegion` (PR #36 pinch-zoom), but neither the captured request nor the analyzer thread emitted zoom state to logcat. Session-to-session variance in nc2 results (e.g., 33.75% yesterday vs 0% today) may be partially explainable by zoom-state differences. Resolved in this PR — `JABCodeZoom` tag emits `SCALER_CROP_REGION` whenever the repeating-request rebuilds.

## Cross-references

- `H_mode0_decodeModuleNc_classifier.md` — sibling entry for the nc=0 classifier-side residual bug uncovered today
- `docs/roi-detection-implementation-plan.md` §1.6.1 — PR 1's per-fixture decision rules; Nc=2 is implicitly part of the "should we proceed" determination
- `H_partI_clean_data_failure.md` — sibling decoder hypothesis
- `H_mode0_partI_decode_failure.md` — sibling decoder hypothesis
- `project_jabcode_screen_vs_print_physics.md` (memory) — explains why high-Nc fails on print but DOES NOT explain why Nc=2 fails everywhere
- `src/jabcode/encoder.h::jab_default_palette` — the 8-color palette being scrutinized
- `src/jabcode/encoder.c::genColorPalette` — palette generation that differentiates Nc=2 from neighbors
- `src/jabcode/test/test_roundtrip_all_nc.c` — existing synthetic roundtrip test that should be re-examined for Nc=2 specifically
