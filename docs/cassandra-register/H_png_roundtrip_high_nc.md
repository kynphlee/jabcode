# H_png_roundtrip_high_nc — Open root-cause hypothesis: synthetic encode→PNG→decode roundtrip fails for every Nc ≥ 3

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-05-29 (discovered during panama-wrapper JVM baseline benchmark capture)                       |
| **Status**   | Open — CONFIRMED; orthogonal to the `H_partI_unifies` {Nc=0, Nc=2, Nc=7} camera-path cluster      |
| **Binding**  | Triggered (not scheduled)                                                                          |
| **Owner**    | Unassigned (claimed on trigger)                                                                    |
| **Severity** | Medium — synthetic-only, but blocks JVM-side regression detection for Nc ≥ 3                       |
| **Related**  | `H_nc2_decode_failure.md`, `H_partI_clean_data_failure.md`, `H_mode0_partI_decode_failure.md`     |

## The hypothesis

When the JABCode encoder writes Nc ≥ 3 (16/32/64/128/256-color) symbols to PNG and the decoder reads them back **as files, on the JVM, with no camera path involved**, LDPC error correction cannot recover the data. The failure is silent at the encoder layer (the encoder produces bytes that round-trip cleanly to PNG) and surfaces only at the decoder's LDPC stage with errors like:

```
LDPC decoding for data in symbol 0 failed
Too many errors in message. LDPC decoding failed.
```

This is **not** the same as the camera-path Nc=2 / Nc=0 / Nc=7 failures cataloged in `H_partI_unifies`. Those happen on the Android device through the live camera pipeline. This one is reproducible at the JVM level with synthetic PNG inputs the encoder itself produced — no camera, no chroma subsampling, no JPEG compression, no lighting, no print medium. The encode and decode are connected by a lossless byte-perfect PNG file.

## Empirical anchor (2026-05-29 JVM baseline)

Source: `panama-wrapper/baseline-benchmarks/README.md` and the underlying JMH runs `panama-wrapper/baseline-benchmarks/2026-05-29-decode-baseline.json` / `2026-05-29-encode-baseline.json`, captured on panama-poc commit `d70a684` (post-PR #22).

**Decode results across Nc=0..7 × messageSize ∈ {100B, 1000B}:**

| Nc | colors | 100B          | 1000B         |
| -- | ------ | ------------- | ------------- |
| 0  | 2      | 10.526 ms/op  | 62.381 ms/op  |
| 1  | 4      | 11.229 ms/op  | 78.352 ms/op  |
| 2  | 8      | 10.216 ms/op  | 61.619 ms/op  |
| 3  | 16     | **LDPC FAIL** | **LDPC FAIL** |
| 4  | 32     | **LDPC FAIL** | **LDPC FAIL** |
| 5  | 64     | **LDPC FAIL** | **LDPC FAIL** |
| 6  | 128    | **LDPC FAIL** | **LDPC FAIL** |
| 7  | 256    | (excluded — encoder malloc) | (excluded) |

**102 LDPC errors** total across the higher-Nc decode parameter combinations. The pattern is binary: Nc=0, Nc=1, Nc=2 succeed cleanly; Nc=3 through Nc=6 fail uniformly. Nc=7 is excluded from this dataset because the encoder side has a separate malloc issue tracked under a different open item.

**Encoder results (for contrast — encoder DOES produce bytes for Nc=3..6):**

| Nc | colors | Best score (ms/op) |
| -- | ------ | ------------------ |
| 3  | 16     | 7.551 (100B)       |
| 4  | 32     | 7.397 (100B)       |
| 5  | 64     | 14.959 (100B)      |
| 6  | 128    | 26.496 (100B)      |

The encoder side is healthy across Nc=0..6. The asymmetry is the diagnostic signal: encoder writes bytes → PNG file → decoder reads back → LDPC layer rejects every Nc ≥ 3 case.

## The four-class Nc taxonomy across paths

Combining the JVM baseline with the user's earlier Android camera-path decode capability matrix produces a clean cross-classification:

| Class                          | Members              | JVM synthetic | Android camera |
| ------------------------------ | -------------------- | ------------- | -------------- |
| Universal works                | Nc=1                 | ✅            | ✅ (93% GA)    |
| Camera-pipeline broken only    | Nc=0, Nc=2           | ✅            | ❌ (0% scan)   |
| **PNG-roundtrip broken only**  | **Nc=3, Nc=4, Nc=5, Nc=6** | **❌**  | ✅ varying (35–67%) |
| Both broken                    | Nc=7                 | ❌ LDPC       | ❌ (17% scan)  |

The two boundaries (JVM Nc≥3 fails / Android Nc∈{0,2} fails) are **almost disjoint** — they share only Nc=7. This near-disjointness means the JVM-baseline failure cluster and the camera-path cluster are almost certainly different bugs in different code paths, requiring separate investigation. Closing one does not close the other.

## Sub-hypotheses (candidate root causes)

In rough order of plausibility given the available evidence:

1. **`H_high_nc_palette_quantization_in_png_write`** — the encoder's color palette for Nc ≥ 3 uses interpolated values from `genColorPalette` (rather than the handcrafted 8-vertex `jab_default_palette` for Nc ≤ 2). PNG's quantization or color-profile pipeline may be perturbing those interpolated colors past the decoder's color-classification threshold, producing modules whose classified color differs from what was written. Plausible because the boundary aligns with where the encoder switches from handcrafted to interpolated palettes (Nc ≤ 2 use handcrafted, Nc ≥ 3 use interpolated).

2. **`H_high_nc_metadata_layout_bug`** — Nc ≥ 3 packs more bits per module into the master-symbol metadata. A bit-packing/unpacking mismatch between encoder and decoder at the higher bit densities (4-bit modules for Nc=3, 5-bit for Nc=4, etc.) would manifest as systematic LDPC failures only above the threshold. The boundary at exactly Nc=3 matches the boundary where the per-module bit count exceeds 3.

3. **`H_png_color_profile_collision`** — `libpng16`'s default sRGB profile (or lack of one) interacts with the encoder's raw RGB output to shift module colors. Plausible especially given the `libpng16-16` Debian runtime dependency that was just added (`93210c8`). Would explain why this is reproducible NOW and may not have been before.

4. **`H_ldpc_parameter_mismatch_per_Nc`** — the (wc, wr) LDPC parameters chosen for Nc ≥ 3 do not match what the decoder is configured to expect. Encoder writes with one set, decoder attempts with another. Would manifest as "Too many errors in message" rather than per-bit decode noise — which matches the observed error string.

5. **`H_jmh_harness_artifact`** — the failure is in the panama-wrapper JMH harness or how it serializes test data through `libjabcode.so`, not in the underlying C decoder. Lower priority than #1–4 because the encoder side of the same harness works cleanly across all the same Nc values.

## Reproducible repro

On any host with the panama-wrapper checkout and `libjabcode.so`:

```bash
cd panama-wrapper
bash run-benchmark.sh DecodingBenchmark "-rf json -rff results/probe.json"
# Look for "LDPC decoding for data in symbol 0 failed" across Nc=3..6 entries in probe.json.
```

The 2026-05-29 baseline JSON (`baseline-benchmarks/2026-05-29-decode-baseline.json`) already contains the captured failures; no need to re-run unless probing a hypothesis. To reproduce on a different `libjabcode.so` build, replace `panama-wrapper/lib/libjabcode.so` and re-run.

The C-side desktop test `src/jabcode/test/test_roundtrip_all_nc.c` is the more controlled equivalent — if it passes for Nc=3..6 where the panama harness fails, that points at the panama wrapper (sub-hypothesis #5); if it fails too, that points at the C decoder library itself (sub-hypotheses #1–4).

## Investigation checklist (cold pickup)

1. **Run `test_roundtrip_all_nc.c` at Nc=3, 4, 5, 6** — does the pure-C synthetic roundtrip succeed where the JMH-via-panama roundtrip fails? If C passes and panama fails: the bug is in panama-wrapper's JNI / FFM bridge serialization. If both fail: the bug is in `libjabcode.so` for high-Nc PNG roundtrips.
2. **Compare encoder output bytes at Nc=3 vs Nc=2** — extract the raw module-color sequence the encoder produced, then check whether the bytes the decoder reads back from PNG match the bytes the encoder claimed it wrote. Mismatch implies palette quantization (sub-hypothesis #1) or color-profile collision (sub-hypothesis #3).
3. **Strip libpng's color-profile handling** — rebuild `libjabcode.so` with libpng configured to skip color management. If high-Nc decode then succeeds, the answer is sub-hypothesis #3 and the fix is to pin libpng's sRGB handling explicitly.
4. **Check the (wc, wr) parameter table per Nc** — what does `decoder.c` expect for Nc=3..6 vs what does `encoder.c` produce? An off-by-one mismatch at the Nc=3 boundary would match the symptom exactly.
5. **Verify Nc=7 encoder malloc is a separate issue or the same root cause** — if the Nc=7 malloc failure has the same boundary character, it may be a unified bug; if independent, the existing exclusion is correct and Nc=7 deserves its own entry.

## Triggers (when this hypothesis activates)

- **Trigger A — A panama-wrapper consumer needs Nc ≥ 3 working** (Cloud, Desktop, or Web SDK distribution paths). Currently the JVM-side SDK ships with Nc=0/1/2 working; Nc=3+ is documented as broken at the JVM layer. A customer needing Nc=3+ at the JVM forces investigation.
- **Trigger B — `H_partI_unifies` (Option B) investigation produces a unified fix** that also closes Nc=3..6 PNG roundtrip failures as a side effect. The four-class taxonomy above suggests this is unlikely (the camera-path and PNG-path clusters are nearly disjoint) but the unified mechanism may surprise.
- **Trigger C — The C-side `test_roundtrip_all_nc.c` is found to be passing at Nc=3..6** while panama-wrapper fails — that immediately localizes to the panama JNI/FFM layer and reduces the surface to investigate.
- **Trigger D — A libpng or libjabcode build-system change** disturbs the synthetic baseline and the regression detector at `baseline-benchmarks/2026-05-29-decode-baseline.json` fires.

## Why this is filed (not scheduled)

The bug is currently bypassable for product reasons: the user's Kano-anchored color-mode roadmap prioritizes Nc=1 (near-term) and defers high-Nc until the camera-path issues are resolved. Filing rather than scheduling reserves the investigation for when a stakeholder actually needs Nc ≥ 3 at the JVM layer.

## Cross-references

- `panama-wrapper/baseline-benchmarks/README.md` — the JVM baseline that surfaced this; replace its `(TODO: file the register entry)` note with a link back to this file when next on the panama-poc branch
- `panama-wrapper/baseline-benchmarks/2026-05-29-decode-baseline.json` — the captured LDPC-failure dataset
- `panama-wrapper/baseline-benchmarks/2026-05-29-encode-baseline.json` — the encoder-side data showing the encoder is healthy across Nc=0..6
- `H_nc2_decode_failure.md` — the camera-path cluster ({Nc=0, Nc=2, Nc=7}) which is orthogonal to this PNG-path cluster ({Nc=3, Nc=4, Nc=5, Nc=6})
- `H_partI_clean_data_failure.md` — sibling decoder hypothesis (possibly related if sub-hypothesis #2 turns out to be the root cause)
- `src/jabcode/test/test_roundtrip_all_nc.c` — the C-side equivalent of the panama harness; cross-check
- `src/jabcode/encoder.c::genColorPalette` — the function that produces interpolated palettes for Nc ≥ 3; first suspect for sub-hypothesis #1
- `panama-wrapper/src/main/java/com/jabcode/panama/JABCodeEncoder.java`, `JABCodeDecoder.java` — the panama bridge code, in case sub-hypothesis #5 applies
