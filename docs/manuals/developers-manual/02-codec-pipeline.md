# 2. The codec pipeline

<!-- objective: A maintainer can trace a payload end-to-end through encode and decode, naming the owning source file and entry function for every stage. -->

Two entry points define the pipeline: `generateJABCode` (`encoder.c:2307`) drives the encode side in a single linear function body; `decodeJABCodeEx` (`detector.c:4065`) drives the decode side, with `decodeJABCode` (`detector.c:4238`) as a thin wrapper that supplies a stack `jab_decoded_symbol[MAX_SYMBOL_NUMBER]` buffer. <!-- anchor: encoder.c:2307; detector.c:4065, 4238-4242 --> The tables below give, for every stage: the owning file, the entry function with its definition line, the call site inside the driving function, and the governing spec clause, cited against the project clause map. Per-stage internals are the subject of chapters [04-encoder.md](04-encoder.md) through [09-capture-support.md](09-capture-support.md).

## 2.1 Encode pipeline map

Stages in actual execution order of `generateJABCode` (`encoder.c:2307-2443`). "Call" = line of the invocation inside `generateJABCode` unless noted.

| # | Stage | Owning file | Entry function (definition) | Call | ISO clause |
|---|---|---|---|---|---|
| E1 | Symbol/cascade initialization, version+position validation, `(wc,wr)` assignment | `encoder.c` | `InitSymbols` (encoder.c:2161); invokes `assignDockedSymbols` (encoder.c:1598) at encoder.c:2217 | 2322 | ISO 23634 Clause 4.3, 4.5 (clause map) |
| E2 | Data analysis — optimal per-character mode sequence | `encoder.c` | `analyzeInputData` (encoder.c:288) | 2327 | ISO 23634 Clause 5.2, Annex E (clause map) |
| E3 | Mode encoding — character stream to bit stream | `encoder.c` | `encodeData` (encoder.c:723) | 2334 | ISO 23634 Clause 5.3 (clause map) |
| E4 | Master auto-sizing (only if `symbol_number == 1` and no version given); uses `getSymbolCapacity` (encoder.c:651) and `getOptimalECC` (encoder.c:698) | `encoder.c` | `setMasterSymbolVersion` (encoder.c:1881) | 2341-2348 | ISO 23634 Clause 4.3 Table 1; 5.4 (clause map) |
| E5 | Slave metadata (Part III / docking parameters) | `encoder.c` | `setSlaveMetadata` (encoder.c:2239) | 2350 | ISO 23634 Clause 4.4 (clause map) |
| E6 | Payload partition across symbols, padding | `encoder.c` | `fitDataIntoSymbols` (encoder.c:2023) | 2356 | ISO 23634 Clause 4.5, 5.4 (clause map) |
| E7 | Master metadata encode (26→44-bit ECC path; skipped in default mode) | `encoder.c` | `encodeMasterMetadata` (encoder.c:925) | 2363-2370 | ISO 23634 Clause 5.9, Annex C normative (clause map) |
| E8 | LDPC encode, per symbol | `ldpc.c` | `encodeLDPC` (ldpc.c:645) | 2376 | ISO 23634 Clause 5.4, Annexes B, C (clause map) |
| E9 | Interleave, per symbol (seeded Fisher-Yates, `INTERLEAVE_SEED 226759`) | `interleave.c` | `interleaveData` (interleave.c:26) | 2387 | ISO 23634 Clause 5.5, Annex F (clause map) |
| E10 | Matrix construction — module placement skipping FP/AP/metadata/palette | `encoder.c` | `createMatrix` (encoder.c:1171) | 2390 | ISO 23634 Clause 5.6-5.7 (clause map) |
| E11 | Mask selection and application, joint across the cascade (`maskCode`), or fixed reference 7 in default mode (`maskSymbols`) | `mask.c` | `maskCode` (mask.c:363); `maskSymbols` (mask.c:289) | 2406-2421 | ISO 23634 Clause 5.8, Tables 22-23 (clause map) |
| E12 | Metadata Part II re-encode + placement with the chosen mask reference | `encoder.c` | `updateMasterMetadataPartII` (encoder.c:1008); `placeMasterMetadataPartII` (encoder.c:1053) | 2428, 2430 | ISO 23634 Clause 5.9 (clause map) |
| E13 | Bitmap render at `module_size` pixels per module (geometry from `getCodePara`, encoder.c:1684, call 2400) | `encoder.c` | `createBitmap` (encoder.c:1781) | 2434 | — (rendering; module geometry per Clause 4.3, clause map) |

Two ordering facts matter for wire compatibility. First, E8→E9→E10 run per symbol inside one loop (encoder.c:2373-2397): the interleave permutes the LDPC-coded stream *before* placement, so any change to seed or permutation order changes every module position downstream. Second, E11/E12 run after placement: masking operates on placed data modules via the data map, and the mask reference chosen at E11 is re-encoded into metadata Part II at E12 — the Makefile-level regression suite (chapter 1, §1.4) guards this coupling. Note also the source comment at the E12 call site: "CRITICAL FIX: Always update Part II metadata regardless of mask\_reference value — Skipping this for DEFAULT mask (7) causes LDPC decoding failure". <!-- anchor: encoder.c:2373-2397, 2425-2430 -->

`generateJABCode` returns `0` on success (see the prominent contract note in [03-public-surface-jabcode-h.md](03-public-surface-jabcode-h.md), §3.6). <!-- anchor: encoder.c:2305 -->

## 2.2 Decode pipeline map

Stages in actual execution order of `decodeJABCodeEx` (`detector.c:4065-4228`) and the functions it drives. Master-symbol sampling stages D3-D5 execute inside `detectMaster` (`detector.c:3682`); symbol-payload stages D7-D10 execute inside `decodeSymbol` (`decoder.c:1872`), reached via `decodeMaster` (`decoder.c:2072`).

| # | Stage | Owning file | Entry function (definition) | Call site | ISO clause |
|---|---|---|---|---|---|
| D1 | RGB balance + per-channel binarization | `binarizer.c` | `balanceRGB` (binarizer.c:485); `binarizerRGB` (binarizer.c:602); variants `binarizer` (408), `binarizerHist` (106), `binarizerHard` (184) | detector.c:4087-4088 | ISO 23634 Clause 6 (clause map) |
| D2 | Finder-pattern search (found-counters, missing-finder inference) | `detector.c` | `findMasterSymbol` (detector.c:1811), from `detectMaster` (detector.c:3682) | detector.c:3740, 3773 | ISO 23634 Clause 6 (clause map) |
| D3 | Side-size computation and version snap | `detector.c` | `calculateSideSize` (detector.c:3072) | detector.c:3795-3797 | ISO 23634 Clause 6, Table 24 (clause map) |
| D4 | Perspective transform (homography from FP/AP centers) | `transform.c` | `getPerspectiveTransform` (transform.c:202) | detector.c:3810-3814 | ISO 23634 Clause 6 (clause map) |
| D5 | Grid sampling — whole-symbol or per-sub-block by alignment pattern | `sample.c` / `detector.c` | `sampleSymbol` (sample.c:31); `sampleSymbolByAlignmentPattern` (detector.c:3296) | detector.c:3826-3828, 3929-3931 | ISO 23634 Clause 6 (clause map) |
| D6 | Master metadata Part I (Nc) → palette read → Part II (V, E, MSK) | `decoder.c` | `decodeMaster` (decoder.c:2072); `decodeMasterMetadataPartI` (1262); `readColorPaletteInMaster` (436); `decodeMasterMetadataPartII` (1470) | decoder.c:2094, 2185; PartII within decodeMaster | ISO 23634 Clause 4.4, 6 (clause map) |
| D7 | Module colour classification | `decoder.c` | `readRawModuleData` (decoder.c:1643) → `decodeModuleHD` (decoder.c:710) | via `decodeSymbol` (decoder.c:1872) | ISO 23634 Clause 6 (clause map) |
| D8 | Demask | `mask.c` | `demaskSymbol` (mask.c:410) | decoder.c:1934 | ISO 23634 Clause 5.8 (clause map) |
| D9 | De-interleave | `interleave.c` | `deinterleaveData` (interleave.c:42) | decoder.c:1967 | ISO 23634 Clause 5.5, Annex F (clause map) |
| D10 | LDPC hard-decision decode (soft path `decodeLDPC`, ldpc.c:1376, exists but is not on this call path) | `ldpc.c` | `decodeLDPChd` (ldpc.c:906) | decoder.c:1979; metadata uses at 1434, 1549 | ISO 23634 Clause 5.4, Annex B (clause map) |
| D11 | Docked-slave recursion: find, sample, decode each slave; slave metadata | `detector.c` / `decoder.c` | `decodeDockedSlaves` (detector.c:4019); `findSlaveSymbol` (detector.c:2767); `decodeSlave` (decoder.c:2377); `decodeSlaveMetadata` (decoder.c:1161) | detector.c:4122-4132 | ISO 23634 Clause 4.5, 6 (clause map) |
| D12 | Mode decode of the concatenated bit stream — transmitted data, ECI/FNC1, symbology identifier | `decoder.c` | `decodeData` (decoder.c:2538) | detector.c:4188 | ISO 23634 Clause 5.3, 7 (clause map) |

Status protocol at the D11/D12 boundary: `*status` is 0 ("not detectable") if no finder pattern resolved, 1 ("not decodable") if detection succeeded but decode failed, 2 ("partly decoded" — only reachable when `mode == COMPATIBLE_DECODE`), 3 ("fully decoded"). <!-- anchor: detector.c:4060, 4135-4160, 4217-4221 --> Detection-mode selection (`QUICK_DETECT`/`NORMAL_DETECT`/`INTENSIVE_DETECT`, detector.h:35-40) and the binarizer variants are chapter 9 territory; `detectMaster` currently calls `findMasterSymbol` with `INTENSIVE_DETECT` on both its attempts. <!-- anchor: detector.h:35-40; detector.c:3740, 3773 -->

## 2.3 Internal dependency graph

Include-verified edges between library translation units (arrow = "depends on"):

- `encoder.c` → `encoder.h`, `ldpc.h`, `detector.h`, `decoder.h` <!-- anchor: encoder.c:18-22 -->
- `decoder.c` → `detector.h`, `decoder.h`, `ldpc.h`, `encoder.h`, `symbology_id.h`, `decode_profile.h`; conditionally `lab_color.h` (under `USE_LAB_DISTANCE`) and `color_calibration.h` (under `USE_FP_CALIBRATION`) <!-- anchor: decoder.c:18-24, 222-224, 238-240 -->
- `detector.c` → `detector.h`, `decoder.h`, `encoder.h`, `decode_profile.h` <!-- anchor: detector.c:18-22 -->
- `detector_synthetic.c` → `detector.h`, `decoder.h` <!-- anchor: detector_synthetic.c:18-20 -->
- `mask.c` → `encoder.h`, `detector.h` <!-- anchor: mask.c:18-20 -->
- `interleave.c` → `encoder.h`, `pseudo_random.h` <!-- anchor: interleave.c:16-18 -->
- `ldpc.c` → `ldpc.h`, `pseudo_random.h`, `detector.h` <!-- anchor: ldpc.c:17-23 -->
- `binarizer.c`, `sample.c`, `transform.c` → `detector.h` (`sample.c` also → `decoder.h`) <!-- anchor: binarizer.c:17; sample.c:17-19; transform.c:18-19 -->
- `image.c` → `png.h`, `tiffio.h` (vendored headers) <!-- anchor: image.c:17-19 -->
- `adaptive_palette.c` → `adaptive_palette.h` → `lab_color.h`, `kdtree_color.h`; `kdtree_color.h` → `lab_color.h` <!-- anchor: adaptive_palette.c:8; adaptive_palette.h:17-19; kdtree_color.h:11-12 -->
- `decode_profile.c` → `decode_profile.h`; `pseudo_random.c` → `pseudo_random.h`; `color_calibration.c` → `color_calibration.h`; `lab_color.c` → `lab_color.h` <!-- anchor: decode_profile.c:16; pseudo_random.c:1; color_calibration.c:1; lab_color.c:15 -->

Refinement over the corpus model's edge list (corpus §2.1): the drawn edge `decoder → adaptive_palette` does not exist as a call edge — no translation unit outside `adaptive_palette.c` references any `adaptive_palette_*` symbol. The module is compiled into the library by the `$(wildcard *.c)` rule and its API is public to consumers that include `adaptive_palette.h`, but the in-library decode pipeline never calls it in this tree. <!-- anchor: grep of adaptive_palette_ across src/: definitions/declarations only; src/jabcode/Makefile:21 -->

## 2.4 Fork-extension overlay

The following are fork additions layered over the upstream pipeline. Each is marked with its integration mechanism — this distinction (runtime toggle vs compile-time gate vs parallel path vs dormant module) is the load-bearing fact for interop analysis. Full treatment: [10-fork-extensions.md](10-fork-extensions.md).

| Extension | Mechanism | Pipeline touch points |
|---|---|---|
| **Synthetic decode path** | Parallel entry point, bypasses D1-D5 entirely | `decodeJABCodeSynthetic` (detector_synthetic.c:128) constructs `symbols[0]` directly from known encoder parameters (Nc from `color_number`, `(wc,wr)` from `encoder_wcwr` or `wcwr_for_level`, geometry from arguments) and enters decoder internals. <!-- anchor: detector_synthetic.c:128-179 --> |
| **Decode-stage profiling** | Runtime toggle (`jabSetProfileStages`), default OFF; single-global-read short-circuit when off | `JAB_PROF_*` macro sites: detector.c:4086-4089 (binarize), 3739-3741/3772-3774 (finder), 3756-3766 (re-binarize), 3795-3797/3810-3814 (transform), 3826-3828/3929-3931 (sample), 3970-3998 (slave detect/transform/sample), 4187-4189 (data decode); decoder.c:2184-2186/2394-2396 (palette), 1661-1685 (classify), 1966-1968 (deinterleave), 1978-1980 (LDPC). <!-- anchor: decode_profile.h:110-133; decode_profile.c:24-28 --> |
| **FP-core colour calibration** | Compile-time gate `USE_FP_CALIBRATION` — **not defined by any Makefile in this tree**, so dormant in default builds | `jabBuildCalibrationFromFPCores` before module sampling (decoder.c:1874-1886); `jabHasCalibration`/`jabRemapColorInverse` in `decodeModuleHD` (decoder.c:725-736). The `color_calibration.c` API itself is always compiled and exported. <!-- anchor: decoder.c:226-240, 725-736, 1874-1886; src/jabcode/Makefile:8 --> |
| **LAB perceptual classification** | Compile-time gate `USE_LAB_DISTANCE` — not defined by any Makefile in this tree | `decodeModuleHD` uses `rgb_to_lab` + `delta_e_2000` instead of RGB distance, only for `color_number > 8` (decoder.c:752-792). <!-- anchor: decoder.c:215-224, 752-792 --> |
| **Adaptive palette (LAB + k-d tree)** | Dormant module — compiled, exported, **no call site in the library pipeline** (see §2.3) | `adaptive_palette.c` (443 lines), `lab_color.c`, `kdtree_color.c` as its support libraries. <!-- anchor: corpus §2.3 F-marked rows; adaptive_palette.h:73-154 --> |
| **Mode 0 (2-colour) extension** | Runtime, driven by the detector's `_Thread_local jab_boolean g_mode0_decode` chroma probe | Part I bypass (decoder.c:1283 comment block: Nc already known, `{K,C,Y}` pair-lookup would fail on `{K,W}`); master palette synthesis overwrite (decoder.c:2206); slave palette synthesis (decoder.c:2404). <!-- anchor: decoder.c:134-142, 1283, 2206, 2404-2427 --> |
| **Nc fallback ladder + preferred-count pinning** | Runtime; ladder is fork behavior in `decodeMaster`, pinning via `g_preferred_color_count` | Ladder order `{original_Nc, 1, 0, 2, 3, 4, 5, 6}` with 8 tries, state snapshot/restore after Part I; pinning collapses to a single try at `Nc = log2(count) - 1`. <!-- anchor: decoder.c:2113-2152 --> |
| **Permissive colour classification (Path β)** | Runtime toggle `g_permissive_color_classification`, default OFF | Part I module-colour stage substitutes rgb=5 (Magenta) with rgb=6 (Yellow) before the `{0, 3, 6}` validity check. <!-- anchor: decoder.c:98-132; jabcode.h:93-98 -->  |
| **Failure attribution + gated diagnostics** | Runtime toggle `g_diag_verbose` | `FAIL_ATTR` / `DECODE_OK` terminal markers in `decodeJABCodeEx` (detector.c:4139-4146, 4210-4215, 4222-4226); high-volume markers behind `JAB_DIAG_INFO`. <!-- anchor: jabcode.h:72-91; detector.c:4139-4226 --> |

Interop consequence, stated once: none of the runtime extensions alters the wire format — they alter decoder tolerance and observability. The compile-time gates alter classification behavior only when explicitly built in. The synthetic path is a test instrument, not a conformant decoder. Interop evaluation against the ISO reference decoder is chapter 10 and 15 territory ([10-fork-extensions.md](10-fork-extensions.md), 15 conformance testing); the underlying colour-theory arguments belong to Special Topics (JC-S), forthcoming.

## 2.5 Topological order of this book

Part II of this book presents modules in source-tree order, but its content was drafted — and is best read for comprehension — in the dependency order established by §2.3, leaves first:

`pseudo_random` → { `interleave`, `ldpc` } → `mask` → `encoder`; in parallel { `binarizer`, `transform`, `sample` } → `detector` ⇄ `decoder` (the detector invokes the decoder; data flows detector → decoder) → `image` → CLI tools. <!-- anchor: corpus §2.1 (topological drafting order) -->

Chapter mapping: [08-interleave-and-prng.md](08-interleave-and-prng.md) → [06-ldpc.md](06-ldpc.md) → [07-mask.md](07-mask.md) → [04-encoder.md](04-encoder.md) → [09-capture-support.md](09-capture-support.md) → [05-detector-and-decoder.md](05-detector-and-decoder.md) → [10-fork-extensions.md](10-fork-extensions.md), with the public surface ([03-public-surface-jabcode-h.md](03-public-surface-jabcode-h.md)) and CLI internals ([11-cli-internals.md](11-cli-internals.md)) closing the reference. Every cross-module claim in later chapters resolves against the two maps above.
