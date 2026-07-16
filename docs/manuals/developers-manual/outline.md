# JC-T Developer's Manual — Stage 1 Outline (awaiting Stage 2 approval)

**Book:** jabcode Developer's Manual · **Voice:** Expert — dense, precise, extractive; zero hand-holding · **Bloom ceiling:** Apply → Analyze
**Audience:** maintainers, performance engineers, binding authors working on the `swift-java-poc` fork.
**Corpus:** `docs/manuals/corpus-model.md` (fork @ `8f76559`) · ISO/IEC 23634:2022 via sub-agent (clause map: `JABCodeCOA-crypto/docs/manuals/plan/03-iso-23634-reference-map.md`)
**Structure rule:** Part II's TOC mirrors the source tree (Diátaxis map principle). Reference sections use Template B (extractive tables); pipeline/maintenance chapters may carry analysis but no tutorials — task-level material lives in JC-U, theory in JC-S (forthcoming).

Objectives in ABCD form. Anchors cite the corpus model (resolving to `file:line`).

---

## Part I — Architecture

### 1. Repository and build architecture

- **Objective:** A maintainer can *build* every artifact and *run* every make target from a clean checkout, and *explain* the four build units, their link lines, and the vendored-vs-system dependency posture, including why `refresh-lib`/`check-lib` currently fail (absent repo-root `lib/`).
- **Content:** build units and artifacts; `CFLAGS` and the `_POSIX_C_SOURCE` rationale; full target table (all, refresh-lib, check-lib, bench\*, profile, sweep, transcode, test-\*, clean); Windows build; dependency edges; working-tree caveats (scratch headers, missing prebuilt archives, absent README); the dual-clone warning for anyone using a shell against this folder.
- **Anchors:** corpus §2.1–2.3; `Makefile:8,18,24-186`; `Makefile.win:5-13`; §1.2–1.3.

### 2. The codec pipeline

- **Objective:** A maintainer can *trace* a payload end-to-end — encode: data analysis → mode encoding → LDPC → interleave → placement → masking → metadata; decode: binarize → finder search → perspective transform → sample → palette/classification → unmask → de-interleave → LDPC decode → mode decode → secondary recursion — naming the owning source file and entry function for every stage.
- **Content:** the two pipelines as file-by-file maps with ISO clause correspondences; internal dependency graph and the topological order used throughout this book; where the fork extends upstream (adaptive palette, calibration, synthetic path, profiling hooks).
- **Anchors:** corpus §2.1 (dependency edges), §4 (ordering consequence); entry points `encoder.c:2307`, `detector.c:4238/4065`, `decoder.c:2072`. Spec pulls: Clause 5/6 stage order.

## Part II — Module Reference (TOC mirrors `src/`)

### 3. `include/jabcode.h` — the public surface

- **Objective:** A maintainer can *enumerate* the complete public contract — every macro, typedef, struct, global and function — and *state* each function's ownership and threading contract, including the `generateJABCode` 0-on-success inversion of `JAB_SUCCESS`.
- **Content:** Template B tables for all constants (corpus §3.1), structs verbatim (§3.2), functions (§3.3); the three process-global toggles and their semantics; `MOBILE_BUILD` logging variants and the verbose-gated `JAB_REPORT_INFO`; auxiliary public fork headers (wrapper externs — implementations NOT FOUND — calibration, adaptive palette, profiling, symbology id, LAB, k-d tree).
- **Anchors:** corpus §3.1–3.3; `jabcode.h:21-292`; `encoder.c:2305` (return-convention evidence).

### 4. `encoder.c` — symbol encoding

- **Objective:** A maintainer can *analyze* the encode path — capacity computation, optimal-ECC selection, data encoding, metadata assembly (26→44-bit ECC), matrix construction, cascade assignment — and *modify* one stage without breaking wire compatibility.
- **Content:** `createEncode`/`destroyEncode`; `getSymbolCapacity`/`getOptimalECC`; `encodeData` and the mode tables (`jab_enconing_table` — sic, source spelling — `latch_shift_to`, `mode_switch`, `character_size`); master metadata encode/update/place; `createMatrix` placement; `assignDockedSymbols`/`swap_symbols`; `setMasterSymbolVersion` auto-sizing; the master-position-0 enforcement split (encoder reorder + `Master symbol missing`); palette generation and placement indices; per-mode FP/AP colour index tables.
- **Anchors:** corpus §6 (encoder.c rows), `encoder.h:26-292`, `encoder.c:29-2453` regions per §6. Spec pulls: 5.2–5.9 correspondences, Annex D worked example (regression vector Pg = 1071, K = 476).

### 5. `detector.c` and `decoder.c` — detection and decoding

- **Objective:** A maintainer can *trace* a captured bitmap through detection (finder search with found-counters, missing-finder inference, side-size snap, AP confirmation, per-sub-block perspective sampling) into decoding (metadata Parts I/II, palette read/synthesis, module classification, data decode, slave recursion), and *diagnose* a failure to its stage.
- **Content:** detection modes (`QUICK/NORMAL/INTENSIVE_DETECT`); `detectMaster`, `sampleSymbolByAlignmentPattern`; binarizer variants; decoder constants (`MASTER_METADATA_*`, error codes −1/−2); `decodeMasterMetadataPartI/II`, `decodeSlaveMetadata`, `decodeMaster/Slave`, `decodeData`; slave palette positions (extended 32→64); `COMPATIBLE_DECODE` status semantics; the fork's Path β permissive colour classification and preferred-colour-count pinning; `detector_synthetic.c` as the perfect-image bypass.
- **Anchors:** corpus §6 (detector/decoder rows), `detector.h:23-66`, `decoder.h:17-81`, `jabcode.h:93-105`; `detector_synthetic.c:128`. Spec pulls: Clause 6 procedures, Table 24 side-version snap.

### 6. `ldpc.c` — error correction

- **Objective:** A maintainer can *explain* matrix generation from the seeds (`LPDC_METADATA_SEED 38545`, `LPDC_MESSAGE_SEED 785465` — transposed macro spelling preserved), Gauss-Jordan systematization, and the hard-/soft-decision decode paths, and *verify* wire compatibility against the Annex D vector after any change.
- **Content:** seed constants and why they are interop-critical; `(wc, wr)` per level (`ecclevel2wcwr`, cross-checked with ISO Table 20 — pairs agree; the `ecclevel2coderate` indexing asymmetry documented in JC-U ch. 2 restated precisely); matrix gen / Gauss-Jordan / `decodeLDPChd` / soft path; metadata ECC (Annex C normative construction); iteration caps.
- **Anchors:** `ldpc.h:17-28`; `ldpc.c:166, 226, 640, 906, 1368`; `encoder.h:226-241`. Spec pulls: 5.4, Annexes B, C, D.

### 7. `mask.c` — masking

- **Objective:** A maintainer can *apply* the mask-selection algorithm — 8 generators, penalty rules 1–3 with `W1 100 / W2 3 / W3 3`, joint evaluation across cascades — and *predict* the selected reference for a given matrix.
- **Content:** pattern generators vs ISO Table 22; penalty scoring vs Table 23; `maskCode`, demask path; default reference 7; interaction with data map.
- **Anchors:** `mask.c:22-24, 363`; `jabcode.h:29, 36`. Spec pulls: 5.8, Tables 22–23.

### 8. `interleave.c` and `pseudo_random.c` — permutation machinery

- **Objective:** A maintainer can *state* the exact PRNG contract (LCG64 multiplier `6364136223846793005ULL`, increment 1, temper constants `0x9D2C5680`/`0xEFC60000`, `_Thread_local` seed) and the `INTERLEAVE_SEED 226759` Fisher-Yates interleave, and *explain* why any deviation breaks cross-implementation decode and what the `pn_index` clamp preserves.
- **Content:** interleave/deinterleave; per-operation reseeding; the fork's `_Thread_local` change (reentrancy) and its wire-compatibility argument; `pn_index` FP-UB fix and its regression guard (`test-pn`).
- **Anchors:** `interleave.c:20-77`; `pseudo_random.c:10-30`; `pseudo_random.h:32-38`; `Makefile:133`. Spec pulls: Annex F (LCG spec: next·1103515245+12345 — note and reconcile the documented divergence between the ISO reference PRNG and this fork's LCG64: what is actually wire-relevant is `pn_index` behavior; state findings from source, flag NOT FOUND if unresolvable).

### 9. `binarizer.c`, `transform.c`, `sample.c`, `image.c` — capture support

- **Objective:** A maintainer can *identify* which binarizer variant, transform, and sampling routine serves each detection mode, and *use* the image I/O surface (PNG, CMYK TIFF, in-memory PNG) with correct format constraints (`readImage` is PNG-based).
- **Content:** RGB balance + per-channel thresholding variants; `getPerspectiveTransform` homography; `sampleSymbol` and cross-area sampling (`CROSS_AREA_WIDTH 14`); PNG/TIFF/in-memory functions with bitmap layout constants (32 bpp, 8 bpc, 4 channels).
- **Anchors:** `binarizer.c:106, 184, 408`; `transform.c:202`; `sample.c:31`; `image.c` region; `jabcode.h:43-45, 287-291`.

### 10. Fork extensions — adaptive palettes, calibration, profiling, diagnostics

- **Objective:** A maintainer can *explain* what each fork module adds over upstream — runtime palette learning (LAB + k-d tree), static/FP-core colour calibration with forward/inverse remap, opt-in decode-stage profiling, the synthetic decoder, verbose diagnostics — and *evaluate* interop consequences versus the ISO reference decoder.
- **Content:** `adaptive_palette.c/h` API and state machine; `color_calibration.c/h` (JSON load, FP-core builder, remap/inverse); `lab_color` conversions and perceptual distance; `kdtree_color` nearest-neighbour; `decode_profile` stages/accumulators/macros and the plotting scripts; the WS-5 "Heisenberg gate" rationale (quoted from `jabcode.h:72-89`); Mode 0 (2-colour) implementation points.
- **Anchors:** corpus §2.3 (F-marked files), §3.3 auxiliary headers; `jabcode.h:72-105`; `decoder.c:1283, 2206, 2404`.

### 11. CLI internals — `jabwriter.c`, `jabreader.c`

- **Objective:** A maintainer can *map* every CLI flag to the `jab_encode` fields it populates and every exit path to its source line, including the validation order, the `--help` non-zero exits, and the reader's module-size exit-code diagnostic.
- **Content:** parse loop structure; validation rules with exact error strings; flag→field mapping table; reader's strict argument ordering and output modes; empty `jabwriter.h`.
- **Anchors:** corpus §3.4–3.5; `jabwriter.c:25-507`; `jabreader.c:9-93`.

## Part III — Maintenance, Performance and Conformance

### 12. Benchmark estate

- **Objective:** A performance engineer can *run* every benchmark target with correct arguments and output formats (JSON/JSONL/table), *profile* decode stages via the `decode_profile` hooks and plotting scripts, and *interpret* results against the cascade and concurrency regressions the benches guard.
- **Content:** `bench` (Nc 0..7 microbench), `bench-concurrent` (pthread throughput), `bench-cascade` (N 1..61 × Nc; curves/matrix modes), `profile` + `scripts/plot_stage_profile.py`/`plot_detect_substage.py`, `sweep`, `transcode` (external script NOT FOUND — documented); methodology notes from `test/README-bench.md`.
- **Anchors:** `Makefile:79-127`; corpus §2.2; bench sources §2.3.

### 13. Regression suite

- **Objective:** A maintainer can *run* the full regression set, *state* what each target guards (pn FP-UB, Annex H identifiers, ECI bit-level, Table 15/FNC1 backslash-doubling, text round-trip, high-version cascade byte-run width fix, TSan reentrancy), and *extend* the suite for a new fix following the existing self-contained pattern.
- **Content:** the eight make-target tests plus the target-less test sources (roundtrip variants, multi-frame, calibration, mode-specific — documented as present-but-unwired, with the `ws4_9_full_regression.sh` script's role); baseline files.
- **Anchors:** `Makefile:133-186`; corpus §2.3 test inventory; `scripts/ws4_8_threshold_sweep.sh`, `scripts/ws4_9_full_regression.sh`.

### 14. Concurrency

- **Objective:** A maintainer can *explain* the fork's reentrancy posture — `_Thread_local` PRNG state, per-operation reseeding, TSan-guarded round trips — and *avoid* the downstream JNA pitfall (`-Djna.protected=true` livelocks concurrent benchmarks; use a JMH timeout instead).
- **Content:** what upstream shared and the fork isolated; `test-concurrent` build (compiles codec sources with `-fsanitize=thread`); `bench-concurrent` serialized-vs-concurrent methodology; the framework-side JNA/Panama boundary notes with the livelock lesson.
- **Anchors:** `pseudo_random.c:6-10`; `Makefile:90, 182`; framework corpus model (JNA boundary); memory-bank lesson (jna.protected).

### 15. Conformance testing

- **Objective:** A maintainer can *assess* this fork's ISO conformance surface: what is implemented (Annex H `]jm` via `symbology_id.h`, ECI/FNC1 protocols with their regression guards) and what is absent (Clause 8 grading — CPA/CVDM and the six-parameter scan grade — NOT FOUND in the tree), and *scope* a grading implementation as a roadmap item.
- **Content:** Annex H mapping table; transmitted-data protocol (Clause 7) implementation points; the Table 20/21 editorial trap; the grading gap with a precise statement of what Clause 8 requires (names, scale, lowest-grade rule — quoted from the JC-U spec extract lineage).
- **Anchors:** `symbology_id.h:26-54`; `Makefile:139-153`; corpus §6 NOT FOUND register. Spec pulls: Clause 7–8, Annex H Table H.1.

### 16. Extended colour modes

- **Objective:** A maintainer can *explain* the reserved-Nc implementation — Mode 0 (2-colour, fork-only) through Nc = 7 (256) — including palette handling beyond 8 colours (slave palette positions extended 32→64, per-mode FP/AP colour indices), decoder fallback ladder and `g_preferred_color_count` pinning, and *state* interchange consequences precisely.
- **Content:** where each mode touches encoder/decoder/detector; Annex G guidance vs this implementation's choices; permissive classification interplay; capacity-vs-tolerance trade referencing Clause 8 metrics (names only; math in JC-S).
- **Anchors:** `jabcode.h:98-105`; `encoder.h:67-75`; `decoder.h:36-45`; `jabwriter.c:147-155`; `decoder.c:1283, 2206, 2404`. Spec pulls: 4.4.1.2, Annex G.3.

### 17. Downstream bindings

- **Objective:** A binding author can *state* the contract the Panama wrapper consumes — the public API of ch. 3 plus the `VENDORED_DIR` refresh/check discipline and ABI symbol-set guard — and *account for* the framework-side mapping (pixel-vs-module `symbolWidth` tension, `enablePooling`/`optimizedSaving` not forwarded, cascade exposure), including the historical `symbolWidth`/`symbolHeight` reconciliation.
- **Content:** `refresh-lib`/`check-lib` semantics and the codec-regression workflow; wrapper externs with NOT FOUND implementations; provenance validation on the consumer side; the framework mapping table (from framework corpus + JC-U ch. 12, restated at maintainer depth).
- **Anchors:** `Makefile:49-61`; `include/jabcode_wrapper.h:10-15`; framework corpus model (`JabCodeConfig`, `PanamaJabCodeService`); JC-U ch. 12 findings.

---

## Stage 3 notes (for drafting, after approval)

- **Templates:** Part II chapters use Template B (extractive reference tables) with a brief Expert-voice preamble per module; Parts I and III are technical prose with analysis, no tutorials, no self-checks (teaching devices belong to JC-U; deep theory pointers go to JC-S "forthcoming").
- **Spec pulls required (one ISO sub-agent batch):** Clause 5/6 stage-order statements; Table 24; Tables 22–23 generator/penalty definitions; Annex B decode algorithms (names/parameters only — math belongs to JC-S); Annex C metadata matrix rule; Annex D vector values; Annex F PRNG spec (for the ch. 8 divergence analysis); Annex H Table H.1; Clause 7 protocol sentences; Clause 8 requirement names.
- **Mid-draft retrieval is mandatory:** Part II writers read the actual module sources beyond the corpus model — line-anchored, extractive; anything ambiguous is quoted, not summarized.
- **Known findings to restate at maintainer depth** (already verified): `generateJABCode` 0-on-success; verbose-gated `JAB_REPORT_INFO`; master-position enforcement split; `ecclevel2coderate` indexing asymmetry; absent prebuilt archives; unwired test sources.
- **Drafting order:** ch. 1–2 first (frame), then Part II in source-topological order (8 → 6 → 7 → 4 → 9 → 5 → 10 → 3 → 11), then Part III (12–17). Book order remains as numbered.
- **Cross-book contract:** link JC-U chapters where task-level treatment exists; mark JC-S pointers "forthcoming".
