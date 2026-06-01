# H_nc6_partII_palette_degeneracy — Open root-cause hypothesis: `decodeMasterMetadataPartII` palette learning degenerates specifically at Nc=6, while Nc=5 and Nc=7 work

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-06-01 (Wave 1 v7 validation traces under Path β pinning)                                       |
| **Status**   | Open — CONFIRMED at PartII stage; mechanism unspecified pending instrumentation                     |
| **Binding**  | Triggered (customer need expressed 2026-05-31 — all 8 Nc modes required)                            |
| **Owner**    | Unassigned (claimed on trigger)                                                                    |
| **Severity** | Medium — Nc=6 (128-color) is 100% broken at PartII while neighbours Nc=5 and Nc=7 sometimes work   |
| **Related**  | `H_partI_nc_extraction_bias.md`, `H_nc2_decode_failure.md`, `H_png_roundtrip_high_nc.md` (orthogonal — JVM-side) |

## The hypothesis

When the JABCode decoder is forced (via Path β `g_preferred_color_count=128`) to attempt decode at Nc=6 on a camera-captured Nc=6 fixture, PartI metadata extraction succeeds 99% of the time but PartII palette learning fails 100% of the time. The same decoder under the same camera conditions, pinned to Nc=7, succeeds at PartII 22 times out of 126 attempts (~17%). The asymmetry rules out generic "high-Nc PartII collapse" and localizes the bug specifically at Nc=6.

Candidate sub-mechanisms:
1. **Clustering threshold sized for Nc≤5 OR Nc=7, with Nc=6 in the gap**: K-means or similar palette-learning algorithm may use threshold parameters tuned for "small palettes" (Nc≤5, ≤32 colors) or "maximum palette" (Nc=7, 256 colors) but degenerate at Nc=6 (128 colors).
2. **Palette interpolation degeneracy at 128 colors**: `genColorPalette` generates interpolated palettes for Nc≥3 (vs handcrafted for Nc≤2). The interpolation formula may produce colinear or near-degenerate clusters at exactly 128 colors that the decoder cannot distinguish.
3. **EC-level / interleaving mismatch**: Nc=6 may use a different error-correction parameter set than Nc=5 or Nc=7, and the decoder's PartII assumes the wrong parameters.
4. **Numerical precision degeneracy at 128**: 128 = 2^7. Palette-distance calculations may have specific floating-point degeneracy at this color count that doesn't apply at 64 (2^6) or 256 (2^8).

## Empirical anchor (2026-06-01 v7 stacked-fix traces)

Both fixtures captured 2026-06-01 with the same device (Galaxy S25), same APK (post-Wave 1 build, pre-haptic), same ambient conditions. Both pinned via Path β with the dropdown set to the matching color count.

| Trace | Pin | PartI BEGIN | PartI SUCCESS | PartII attempts | PartII OK | Native success | Decoded data |
|---|---|---|---|---|---|---|---|
| `trace-20260601_011843-nc6.logcat` | 128 (Nc=6) | 79 | **78 (99%)** | 40 | **0 (0%)** | 0 | none |
| `trace-20260601_012025-nc7.logcat` | 256 (Nc=7) | 126 | **126 (100%)** | 40+ | **22 (~55% of attempts logged)** | 22 | "HELLO-Nc-7" |

Identical PartI patterns; divergent PartII outcomes. The mechanism is downstream of PartI metadata extraction and upstream of decoded-symbol return.

Sample PartII failure for Nc=6 (representative — repeats 40 times):

```
[PartI_DIAG] SUCCESS Nc=6
Nc_PIN: preferred_color_count=128 pinned_Nc=6 (fallback ladder collapsed)
DIAG_PALETTE_LEARNED Nc=6 colors=128 hash=0xba419e97
DIAG_PARTII_RESULT result=-1 Nc=6 ok=0
❌ Native decode FAILED: JABCode found but not decodable
```

Sample PartII success for Nc=7 (representative — repeats 22 times):

```
[PartI] LDPC decode SUCCESS, Nc=7
[DECODE] SUCCESS Nc=7 (266 bytes)
DIAG_SYMBOL_DECODE result=ok Nc=7 Pg=2394 Pn=266 checksum=0x...
✅ Native decode SUCCESS: 10 bytes received, colorNumber=256
Decoded data preview: "HELLO-Nc-7"
```

Note the `DIAG_PALETTE_LEARNED Nc=6 colors=128 hash=0xba419e97` — palette learning IS producing a 128-color palette structure; the hash is deterministic across the 40 attempts; the SAME palette is being learned every time but the PartII LDPC consistently rejects it.

This rules out sub-hypothesis #2 (palette interpolation degeneracy) at least partially — the encoder produces a stable palette. The issue is in the decoder's USE of that palette during PartII.

## Distinguishability against neighbouring hypotheses

| Sibling | Distinguishability | Verdict |
|---|---|---|
| `H_partI_nc_extraction_bias` | Bias affected Nc reading at PartI. This entry: PartI works fine, PartII fails. | Distinct mechanism, different stage. |
| `H_nc2_decode_failure` | Nc=2 fails at PartI's pair_bits stage. This entry: PartI succeeds, PartII fails. | Distinct stage. |
| `H_png_roundtrip_high_nc` | JVM-side Nc≥3 LDPC fails on synthetic PNG. This entry: camera-path Nc=6 only. | Different path (camera vs PNG); plausibly related at the LDPC layer but separate empirical signal. |

## Mechanism candidates — distinguishability tests

| Sub-hypothesis | Cheapest test |
|---|---|
| #1 (Clustering threshold gap) | Add instrumentation logging clustering convergence iterations + variance at PartII stage. Compare Nc=5/6/7 traces. If Nc=6 hits max-iterations without converging while neighbours converge in N<max iterations, this candidate is supported. |
| #2 (Palette interpolation degeneracy) | Dump the `norm_palette` array for Nc=5/6/7 from camera traces. If Nc=6's palette has near-coincident entries (small pairwise distances), this candidate is supported. The trace already shows palette hash is stable for Nc=6 — so it's the decoder's TREATMENT not the palette generation. |
| #3 (EC-level / interleaving mismatch) | Cross-reference `decoder.c::decodeMasterMetadataPartII` parameter table per Nc against encoder.c. If Nc=6 uses parameters not symmetric with Nc=5/7, this is supported. |
| #4 (Numerical precision at 128) | Inspect floating-point computations in PartII palette-distance code. Look for divisions by small numbers, near-singular matrix inversions, or normalization steps that could degenerate at 128 specifically. |

## Investigation plan (cold pickup)

1. **Add per-iteration palette-clustering markers** to `decoder.c::readColorPaletteInMaster` and the downstream `normalizeColorPalette`. Log convergence iterations, variance, and palette-distance statistics per attempt.
2. **Pin and scan Nc=5, Nc=6, Nc=7 fixtures** (using Path β). Capture clean traces for each.
3. **Compare convergence profiles**. If Nc=6 fails to converge or hits max-iterations consistently, sub-hypothesis #1 is supported.
4. **Inspect `pal_ths` (palette thresholds) per Nc** at the entry to PartII. Look for degenerate threshold values at Nc=6.
5. **Cross-reference encoder.c::genColorPalette and decoder.c::decodeMasterMetadataPartII for Nc=6 vs Nc=7 parameter differences**.

Estimated effort: ~4-6 hours instrumentation + 2-3 hours analysis + variable fix time depending on which sub-hypothesis lands.

## Triggers

- **Trigger A** (FIRED 2026-05-31): customer requires all 8 Nc modes to ship reliably
- **Trigger B**: a customer expresses preference for Nc=6 (128-color) specifically — high data density at the highest "still-printable" palette size
- **Trigger C**: Nc=5 or Nc=7 investigation surfaces the same convergence/degeneracy pattern at PartII

## Why this is filed

The asymmetry between Nc=6 (0% PartII) and Nc=7 (~17% PartII) is empirical, novel (surfaced 2026-06-01), and high-leverage for hypothesis-narrowing in Wave 2. Capturing it now ensures the v7 trace evidence is anchored to a mechanism layer before downstream investigations begin.

## Cross-references

- `jabauth-android/diagnostic-app/logs/trace-20260601_011843-nc6.logcat` — empirical anchor (Nc=6 PartII fail)
- `jabauth-android/diagnostic-app/logs/trace-20260601_012025-nc7.logcat` — counter-evidence (Nc=7 PartII success)
- `src/jabcode/decoder.c::decodeMasterMetadataPartII` — the function to investigate
- `src/jabcode/decoder.c::readColorPaletteInMaster` — palette learning entry point
- `src/jabcode/decoder.c::normalizeColorPalette` — palette normalization
- `src/jabcode/decoder.c::getPaletteThreshold` — threshold computation (note: only handles color_number == 4 or 8 currently per the WS-4.5.4 comment in decoder.c:1929)
- Bayesian Council Session bc-2026-06-01-04 — synthesis recommending W2.1 investigation
