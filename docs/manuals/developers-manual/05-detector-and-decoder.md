# 5. `detector.c` and `decoder.c` — detection and decoding

<!-- objective: A maintainer can trace a captured bitmap through detection (finder search with found-counters, missing-finder inference, side-size snap, AP confirmation, per-sub-block perspective sampling) into decoding (metadata Parts I/II, palette read/synthesis, module classification, data decode, slave recursion), and diagnose a failure to its stage. -->

This chapter covers the two largest translation units in the library: `detector.c` (4242 lines — finder/alignment-pattern search, sampling, and the top-level `decodeJABCode(Ex)` drivers) and `decoder.c` (3017 lines — metadata, palette reconstruction, module classification, symbol payload decode, transmitted-data decode). The stage-level map with call sites is [02-codec-pipeline.md](02-codec-pipeline.md) §2.2 (stages D1–D12); this chapter is the per-stage reference. Capture-support primitives the detector consumes (`binarizerRGB`, `getPerspectiveTransform`, `sampleSymbol`) are [09-capture-support.md](09-capture-support.md); fork extensions that hook these stages (Mode 0, LAB classification, calibration, profiling) are [10-fork-extensions.md](10-fork-extensions.md).

Structure below follows the decode pipeline, mapped to the ISO/IEC 23634:2022 decode procedure, clause 6.1 steps a)–p). <!-- anchor: ISO 23634 6.1 -->

## 5.1 ISO 6.1 stage map

| ISO 6.1 step | Implementation | Anchor |
|---|---|---|
| a) "Preprocess the captured image and classify colours." | `balanceRGB` + `binarizerRGB` (preprocess); colour classification proper is deferred to h) | detector.c:4087-4088 |
| b) locate primary via finder patterns | `findMasterSymbol` | detector.c:1811 |
| c) locate alignment patterns if present | `detectFirstAP` / `confirmSymbolSize` / `findAlignmentPattern`; AP grid in `sampleSymbolByAlignmentPattern` | detector.c:3129, 3233, 2623, 3296 |
| d) "Establish the sampling grid... and sample the symbol." | `calculateSideSize` + `getPerspectiveTransform` + `sampleSymbol`; per-sub-block variant `sampleSymbolByAlignmentPattern` | detector.c:3072, 3811, 3827, 3296 |
| e) decode Part I metadata → colour mode; on invalid Part I "use the default metadata values" | `decodeMasterMetadataPartI`; default fall-through `loadDefaultMasterMetadata` | decoder.c:1262, 1846, 2100-2110 |
| f) "Extract and construct the four colour palettes." | `readColorPaletteInMaster` / `readColorPaletteInSlave` | decoder.c:436, 568 |
| g) decode Part II → side-versions, ECC parameters, mask reference | `decodeMasterMetadataPartII` | decoder.c:1470 |
| h) "Decode the data modules by determining their colour index in the nearest colour palette." | `readRawModuleData` → `decodeModuleHD` (+ `getNearestPalette`) | decoder.c:1643, 710, 670 |
| i) release masking | `demaskSymbol` (mask.c:410; see [07-mask.md](07-mask.md)) | decoder.c:1934 |
| j) de-interleave | `deinterleaveData` (interleave.c:42; see [08-interleave-and-prng.md](08-interleave-and-prng.md)) | decoder.c:1967 |
| k) error-correct | `decodeLDPChd` (ldpc.c:906; see [06-ldpc.md](06-ldpc.md)) | decoder.c:1979 |
| l) mode-decode | `decodeData` | decoder.c:2538; detector.c:4188 |
| m) decode Part III → docking positions; Part I/II of docked secondaries in the data stream | docking bits + `decodeSlaveMetadata`, both inside `decodeSymbol` | decoder.c:2013-2035, 1161 |
| n) locate and decode docked secondaries | `findSlaveSymbol` / `detectSlave` / `decodeSlave` | detector.c:2767, 3959; decoder.c:2377 |
| o) recurse per decoding order | `decodeDockedSlaves` loop over the growing symbol list | detector.c:4019, 4122-4132 |
| p) concatenate | bit-stream concatenation in `decodeJABCodeEx` before `decodeData` | detector.c:4162-4183 |

<!-- anchor: ISO 23634 6.1 (a-p) -->

## 5.2 Entry points and status contract

**Public surface.**

| Item | Signature / value | Notes |
|---|---|---|
| `decodeJABCode` | `jab_data* decodeJABCode(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status)` | Thin wrapper: stack `jab_decoded_symbol symbols[MAX_SYMBOL_NUMBER]`, delegates to `decodeJABCodeEx`. <!-- anchor: detector.c:4238-4242 --> |
| `decodeJABCodeEx` | `jab_data* decodeJABCodeEx(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status, jab_decoded_symbol* symbols, jab_int32 max_symbol_number)` | Caller supplies the symbol buffer and receives per-symbol detection/decode results. <!-- anchor: detector.c:4065 --> |
| `mode` | `NORMAL_DECODE 0` / `COMPATIBLE_DECODE 1` | jabcode.h:50-51. `NORMAL_DECODE`: "only output completely decoded data when all symbols are correctly decoded"; `COMPATIBLE_DECODE`: "also output partly decoded data even if some symbols are not correctly decoded". <!-- anchor: detector.c:4058-4059; jabcode.h:50-51 --> |
| `*status` | 0 / 1 / 2 / 3 | "0: not detectable, 1: not decodable, 2: partly decoded with COMPATIBLE_DECODE mode, 3: fully decoded". <!-- anchor: detector.c:4060 --> |
| `DECODE_METADATA_FAILED` | `-1` | Internal error code, decoder.h:17. <!-- anchor: decoder.h:17 --> |
| `FATAL_ERROR` | `-2` | "e.g. out of memory", decoder.h:18. <!-- anchor: decoder.h:18 --> |

Status assignment in `decodeJABCodeEx`: `*status` starts at 0; if `total == 0` (no master detected) or `mode == NORMAL_DECODE && res == 0` (a slave failed), status becomes 1 only when `symbols[0].module_size > 0` — i.e. detection got far enough to measure a module — otherwise it stays 0. Under `COMPATIBLE_DECODE` with a failed slave, status is set to 2 and decode proceeds with the symbols that did decode. On success, status is set to 3 unless it is already 2. <!-- anchor: detector.c:4067, 4135-4160, 4217-4221 --> A `decodeData` failure after successful symbol decode also yields status 1 (`FAIL_ATTR stage=decode_data`). <!-- anchor: detector.c:4190-4216 -->

The internal error codes −1 and −2 never escape through `*status`; they discriminate failure classes between `decodeMaster`/`decodeSlave`/metadata functions and their callers: `DECODE_METADATA_FAILED` (−1) means "structured read failed, retry/fall back is legitimate" (e.g. Part I invalid → default metadata; Part II `wc >= wr` → next Nc candidate), while `FATAL_ERROR` (−2) means allocation failure and aborts the current decode attempt. <!-- anchor: decoder.h:17-18; decoder.c:2095-2110, 1618-1625 -->

The consumer-visible use of these semantics: `jabcodeReader` returns `(jab_int32)(symbols[0].module_size + 0.5f)` as its exit code when `decode_status > 0` but decode failed — the module-size diagnostic documented in [11-cli-internals.md](11-cli-internals.md). <!-- anchor: src/jabcodeReader/jabreader.c:60 -->

Per-call profiling hooks (`JAB_PROF_*`) instrument every stage of this driver; they compile to a single global read when profiling is off. Full treatment: [10-fork-extensions.md](10-fork-extensions.md) §10.5. <!-- anchor: detector.c:4078, 4086-4089 -->

## 5.3 Detection modes

```c
typedef enum
{
	QUICK_DETECT = 0,
	NORMAL_DETECT,
	INTENSIVE_DETECT
}jab_detect_mode;
```

<!-- anchor: detector.h:35-40 -->

The mode parameter has exactly one effect in the entire codebase, inside `findMasterSymbol`:

```c
jab_int32 min_module_size = ch[0]->height / (2 * MAX_SYMBOL_ROWS * MAX_MODULES);
if(min_module_size < 1 || mode == INTENSIVE_DETECT) min_module_size = 1;
```

<!-- anchor: detector.c:1814-1815 -->

`min_module_size` is the row stride of the horizontal scan (and the column stride of the vertical rescan): under `QUICK_DETECT`/`NORMAL_DETECT` the detector skips rows on the assumption that a module is at least `height / (2 · MAX_SYMBOL_ROWS · MAX_MODULES)` = `height / 870` pixels tall (`MAX_SYMBOL_ROWS 3`, `MAX_MODULES 145` — "the number of modules in side-version 32"); under `INTENSIVE_DETECT` every row is scanned. <!-- anchor: detector.h:23-25; detector.c:1813-1815, 1840, 1431 --> `QUICK_DETECT` and `NORMAL_DETECT` are behaviourally identical — no code path distinguishes them.

In this fork the mode is not caller-selectable: `detectMaster` hard-codes `INTENSIVE_DETECT` on both of its `findMasterSymbol` calls, and no other call site exists. The `mode` argument of `decodeJABCode(Ex)` is the *decode* mode (`NORMAL_DECODE`/`COMPATIBLE_DECODE`), not the detect mode. `QUICK_DETECT`/`NORMAL_DETECT` are therefore dead at runtime. <!-- anchor: detector.c:3740, 3773 -->

## 5.4 Finder-pattern search — `findMasterSymbol`

**Responsibility.** Locate the four finder patterns (FP0 UL, FP1 UR, FP2 LR, FP3 LL) in the binarized channel triple, accumulating candidate sightings with found-counters, and survive one missing pattern by extrapolation plus local re-search. Definition at detector.c:1811 (the `@brief` doc comment begins at 1804 — the corpus model's "findMaster ... 1804" points at the comment line). <!-- anchor: detector.c:1804-1811 -->

**Candidate structure.** Both finder and alignment patterns share one struct with a found-counter:

```c
typedef struct {
	jab_int32		type;
	jab_float		module_size;
	jab_point		center;			//coordinates of the center
	jab_int32		found_count;
	jab_int32 		direction;
}jab_finder_pattern, jab_alignment_pattern;
```

<!-- anchor: detector.h:45-51 -->

**Scanline profile machine.** Each image row of the green channel is run through `seekPatternHorizontal`, a 5-state run-length machine (`state_number = 5`). A run shorter than 3 pixels is merged into the previous state ("a module shall not be smaller than 3 pixels" is enforced later at selection; here short runs are treated as noise). When five states fill, `checkPatternCross` validates the layer proportions: the three inner layers must each be within `layer_tolerance = layer_size / 2` of `layer_size = inside_layer_size / 3`, the two outer layers must exceed `0.5 * layer_tolerance` ("layer size proportion must be n-1-1-1-m where n>1, m>1"), and layers 1 and 3 "shall be of the same size" within tolerance. `layer_size` becomes the module-size estimate; the center is back-computed from the run boundaries. On rejection the state window shifts by one state and scanning continues. <!-- anchor: detector.c:319-411, 125-150, 288, 392 -->

**Channel cross-checking.** A green hit is cross-checked in the other channels to classify the FP type by core colour, using the encoder's core-colour indices `FP0_CORE_COLOR 0` (K), `FP1_CORE_COLOR 0` (K), `FP2_CORE_COLOR 6` (Y), `FP3_CORE_COLOR 3` (C) into `jab_default_palette`. <!-- anchor: encoder.h:50-53, 26-34 --> Branch structure per green hit: <!-- anchor: detector.c:1872-1993 -->

- blue-channel `crossCheckPatternHorizontal` succeeds → UL/LL arm; red channel must then pass `crossCheckColor` (all-red-channel-zero along the core, `module_number = 5`) → candidate is FP0 (K core) or FP3 (C core) by the `(type_r, type_g, type_b)` triple;
- else red-channel `crossCheckPatternHorizontal` succeeds → UR/LR arm; blue channel must pass `crossCheckColor` → candidate is FP1 (K core) or FP2 (Y core);
- module sizes of the two confirming channels must agree per `checkModuleSize2` (tolerance `mean / 2.5`). <!-- anchor: detector.c:177-186, 1932, 1964 -->

`crossCheckColor` scans `module_size * (module_number - 1)` pixels through the pattern core in horizontal, vertical, or diagonal direction, tolerating up to `tolerance` consecutive mismatches. The fork made this tolerance scale-adaptive: `jab_int32 tolerance = (module_size / 7 > 3) ? module_size / 7 : 3;` — the WS-5 fix for large-module prints, where the original fixed 3-pixel bound shrank proportionally ("module_size=40 → 3/160 = 1.88% (fails — Nc=5 prints, trace 002302)") and anti-aliased boundaries "span 3-4 pixels naturally". Breakeven at module size 21 keeps small-module behaviour identical. <!-- anchor: detector.c:766-789, 768-788 -->

Each surviving candidate then passes the full `crossCheckPattern`: the green channel is re-confirmed in vertical/horizontal/diagonal directions via `crossCheckPatternCh` (diagonal may substitute when one direction fails, `dcc == 2`); FP1/FP2 additionally require the red channel plus three-direction `crossCheckColor` in blue; FP0/FP3 require blue plus three-direction `crossCheckColor` in red; the refined center and module size are averaged from the passing channels. Under Mode 0 all colour-specific checks are skipped after the green structural check (see [10-fork-extensions.md](10-fork-extensions.md) §10.7). <!-- anchor: detector.c:880-933, 942-1102, 968-972, 1035-1039 -->

**Found-counter accumulation.** `saveFinderPattern` merges a new sighting into an existing list entry when centers differ by at most one module size, module sizes are compatible, and the types match — averaging center and module size weighted by `found_count`, then incrementing it. Distinct sightings append until `MAX_FINDER_PATTERNS 500` stops the scan. <!-- anchor: detector.c:1143-1167, 2018; detector.h:26 -->

**Vertical rescan.** If after the horizontal pass only the top pair (FP0+FP1) or only the bottom pair (FP2+FP3) has candidates, `scanPatternVertical` repeats the search column-wise (green seek via `seekPattern` in vertical orientation, same cross-check cascade). <!-- anchor: detector.c:2041-2047, 1427-1568 -->

**Selection.** `selectBestPatterns` drops candidates with `found_count < 3` ("abandon the finder patterns which are founds less than 3 times, which means a module shall not be smaller than 3 pixels"), picks the best per type via `getBestPattern` (max found-count; ties broken by module size closest to the mean), then discards any selected FP whose found-count is below half the maximum among the four. Returns the number of missing types. <!-- anchor: detector.c:1312-1417, 1323-1325, 1263-1303, 1381-1402 -->

**Missing-finder inference.** More than one missing FP fails detection ("Too few finder pattern found"). Exactly one missing FP is first *estimated* by similar-triangle extrapolation from the other three (module-size-ratio-scaled vector arithmetic, e.g. for FP0: `fps[0].center.x = (fps[3].center.x - fps[2].center.x) / ave_size_fp23 * ave_size_fp13 + fps[1].center.x`), bounds-checked against the image, then *confirmed* by `seekMissingFinderPattern`: a local search in a radius of `module_size * 5` around the estimate, in which the area is re-quantized to black/cyan/yellow using the area-average pixel value as threshold (`R < B` → cyan, `R > B` → yellow) and the scanline machine re-runs with the expected core colour for that FP index (FP0/FP1 → K, FP2 → Y, FP3 → C). If the local search finds nothing, the extrapolated estimate stands. <!-- anchor: detector.c:2070-2075, 2078-2144, 1577-1801, 1580, 1623-1649, 1652-1671 -->

**Failure modes.** `FATAL_ERROR` on candidate-buffer allocation; `JAB_FAILURE` with `reportError("Too few finder pattern found")` on ≥2 missing FPs; `JAB_FAILURE` with `"Finder pattern %d out of image"` when the extrapolated FP falls outside the bitmap. <!-- anchor: detector.c:1817-1823, 2070-2075, 2131-2138 -->

## 5.5 Side-size determination vs ISO Table 24

The module count between two patterns divides the center distance by the scanline-projected mean module size:

```c
jab_float dist = DIST(fp1.center.x, fp1.center.y, fp2.center.x, fp2.center.y);
jab_float cos_theta = MAX(fabs(fp2.center.x - fp1.center.x), fabs(fp2.center.y - fp1.center.y)) / dist;
jab_float mean = (fp1.module_size + fp2.module_size)*cos_theta / 2.0f;
jab_int32 number = (jab_int32)(dist / mean + 0.5f);
```

<!-- anchor: detector.c:3057-3065 -->

`calculateSideSize` computes `size_x_top = calculateModuleNumber(fps[0], fps[1]) + 7` (and bottom/left/right analogues). This is the code counterpart of ISO Formula (5), "side\_size\_x\_top = floor( dist\_ul\_ur\_x / ((ul\_module\_size + ur\_module\_size)/2 × cosθ1) + 7.5 )" — algebraically identical, since round(x) + 7 = floor(x + 0.5) + 7 = floor(x + 7.5); the `cos_theta` factor projects the module size (measured along scanlines) onto the FP-to-FP axis exactly as the spec's cosθ1 does. <!-- anchor: detector.c:3072-3097; ISO 23634 6.3 Formulas (5)-(8) -->

`getSideSize` is the Table 24 snap:

| `size mod 4` | Code action | ISO Table 24 rule |
|---|---|---|
| 0 | `size++`, flag 1 | "If res = 0: side_size = side_size + 1" |
| 1 | unchanged, flag 1 | (already valid: sizes are `4v + 17` ≡ 1 mod 4) |
| 2 | `size--`, flag 1 | "Else if res = 2: side_size = side_size − 1" |
| 3 | `size += 2`, flag 0 — source comment: "error is bigger than 1, guess the next version and try anyway" | "Else if res = 3: flag = 0; side_size = side_size + 2" |

<!-- anchor: detector.c:2997-3024; ISO 23634 6.3 Table 24 -->

The code adds a range clamp the extract does not show: results outside 21–145 (side-versions 1–32, `VERSION2SIZE`) set size −1, flag −1. <!-- anchor: detector.c:3013-3022; jabcode.h:53 --> `chooseSideSize` then arbitrates the two independent estimates per axis: both flags −1 → −1 (detection fails); equal flags → `MAX(size1, size2)`; else the size with the bigger flag — matching "The side size with a bigger flag value shall be chosen." The equal-flag `MAX` is an implementation choice for the tie case the extract leaves open. <!-- anchor: detector.c:3034-3049; ISO 23634 6.3 Table 24 -->

## 5.6 Alignment-pattern search and side-version confirmation

`findAlignmentPattern` searches around an estimated position with an expanding-radius window: `radius = 4 * module_size`, doubling per iteration up to `radius_max = 4 * radius`. Rows are visited middle-out from the estimate. Within a row the red channel is walked left and right for runs of the AP core colour, each run cross-checked by `crossCheckPatternAP`; `saveAlignmentPattern` merges repeat sightings, and the search terminates on the first candidate confirmed twice ("if found twice, done!"). Core colours come from `AP0..AP3_CORE_COLOR 3` (C) and `APX_CORE_COLOR 6` (Y). <!-- anchor: detector.c:2623-2756, 2651-2654, 2673-2674, 2744-2749; encoder.h:58-62 -->

Side-version confirmation, used only when the master decoded in *default mode* (Part I absent, side version inferred from pixel geometry rather than metadata): `detectFirstAP` estimates the first APX between two FPs from the `jab_ap_pos` spacing at the assumed version, searching the assumed version ±5 alternately; the found AP's module offset is snapped by `getFirstAPPos` (mod-3 snap, valid range 14–26). `confirmSideVersion` then walks versions outward from the assumption until `first_ap_pos == jab_ap_pos[v-1][1]` matches. `confirmSymbolSize` applies this per axis, trying the opposite symbol edge when the first edge fails. <!-- anchor: detector.c:3129-3187, 3105-3119, 3197-3224, 3233-3286; encoder.h:249-292 -->

## 5.7 Per-sub-block perspective sampling — `sampleSymbolByAlignmentPattern`

**Responsibility.** Resample a symbol whose single-homography (FP-only) sample failed to decode, correcting local geometric distortion by sampling each inter-AP block under its own perspective transform. Entry: detector.c:3296. Aborts if both side-versions are below 6 ("No alignment pattern is available" — versions 1–5 have `jab_ap_num` = 2, i.e. corner FPs only). <!-- anchor: detector.c:3296-3303; encoder.h:285-292 -->

Procedure:

1. Default-mode symbols first pass `confirmSymbolSize` (§5.6). <!-- anchor: detector.c:3306-3316 -->
2. An `ap_num_x × ap_num_y` grid is built: the four corners are the FPs; first-row and first-column entries are extrapolated along the FP0→FP1 / FP0→FP3 directions at `jab_ap_pos` spacings; interior entries are estimated from the upper-left/upper/left neighbours by the same module-size-ratio arithmetic used for missing FPs. Every estimate is confirmed by `findAlignmentPattern`; an unconfirmed AP keeps its estimated coordinates ("recover the estimated one"). <!-- anchor: detector.c:3331-3397, 3386-3394 -->
3. For each of the `(ap_num_x−1) × (ap_num_y−1)` blocks, the smallest enclosing rectangle whose four corner APs were all actually *found* is computed by expanding a search delta outward; duplicate rectangles are dropped; rectangles are sorted descending by area so large well-anchored blocks are sampled (and written into the output matrix) before small ones. <!-- anchor: detector.c:3403-3480 -->
4. Per rectangle, module-space corner coordinates are offset by 0.5 (module centers), and blocks on symbol borders extend by `DISTANCE_TO_BORDER − 1` = 3 modules with 3.5-offset corners (the FP/AP centers sit 3.5 modules inside the border); `perspectiveTransform` maps these to the four AP centers; `sampleSymbol` resamples the block; the block is copied into the full matrix at the `jab_ap_pos`-derived origin. <!-- anchor: detector.c:3499-3597; jabcode.h:39 -->

Failure modes: allocation failures and per-block `sampleSymbol` NULL ("Sampling block failed") abort with NULL. <!-- anchor: detector.c:3324-3329, 3557-3575 -->

## 5.8 `detectMaster` orchestration

Order of operations: <!-- anchor: detector.c:3682-3947 -->

1. **Mode 0 chroma probe.** A ~16×16 grid of pixels is sampled; per-pixel chroma is `|r−g| + |g−b| + |r−b|`; `g_mode0_decode = (mean_chroma <= MODE0_MEAN_CHROMA_TOLERANCE)` with the tolerance fixed at 30. The flag is `_Thread_local` and read by the decoder's Part I path. Rationale, empirical data, and downstream effects: [10-fork-extensions.md](10-fork-extensions.md) §10.7. <!-- anchor: detector.c:41-104, 3695-3732 -->
2. **Finder search, pass 1** — `findMasterSymbol(bitmap, ch, INTENSIVE_DETECT, &status)`. <!-- anchor: detector.c:3740 -->
3. **Adaptive re-binarization, pass 2** — only on pass-1 failure: `getAveragePixelValue` computes per-channel averages in a `module_size * 4` radius around whatever FPs were found; the three channels are re-binarized with those averages as fixed thresholds (`binarizerRGB(bitmap, ch, rgb_ave)` — the `blk_ths` path of [09-capture-support.md](09-capture-support.md) §9.1); `findMasterSymbol` runs again. <!-- anchor: detector.c:3743-3785, 3613-3673 -->
4. **Geometry** — `calculateSideSize` (§5.5), then `getPerspectiveTransform` over the four FP centers, then whole-symbol `sampleSymbol`. <!-- anchor: detector.c:3793-3827 -->
5. **Decode attempt 1** — `decodeMaster` on the FP-only sample. Success returns; `FATAL_ERROR` aborts. <!-- anchor: detector.c:3908-3919 -->
6. **Decode attempt 2** — on soft failure, side sizes are recomputed from the metadata side-versions that Part II (or the default path) established, `sampleSymbolByAlignmentPattern` resamples per sub-block (§5.7), and `decodeMaster` runs once more. <!-- anchor: detector.c:3920-3946 -->

A verbose-gated diagnostic block dumps a 21×21 sampled grid as K/C/M/Y/W letters and compares it against a hard-coded reference matrix (`hello_ref`) when `side_size` is exactly 21×21 — a fixture-comparison instrument, active only under `g_diag_verbose`. <!-- anchor: detector.c:3829-3885 -->

## 5.9 Slave detection — `findSlaveSymbol`, `detectSlave`, `decodeDockedSlaves`

`decodeDockedSlaves` expands the host's `docked_position` bits (0x08/0x04/0x02/0x01 → positions 0–3), seeds each new symbol's metadata from `slave_metadata[j]` of the host, and calls `detectSlave` + `decodeSlave`; any slave failure fails the whole call (the `COMPATIBLE_DECODE` tolerance is applied by the caller). <!-- anchor: detector.c:4019-4053 -->

`findSlaveSymbol` locates the slave's four APs without any finder pattern: for the given docking side it selects the two host edge patterns (`hp1`, `hp2`), the AP type layout, and the direction sign; the first two slave APs are estimated at exactly 7 modules beyond the host edge along the host-edge direction (`host FP + sign * 7 * host module_size * (cos α, sin α)`) and must both be confirmed by `findAlignmentPattern` — either missing fails the slave. Side sizes come from the slave metadata (`VERSION2SIZE`); the slave module size is estimated as AP1–AP2 distance divided by `side_size − 7`; the far two APs are estimated one slave-side away and searched; if *both* far APs are missing the slave fails, if one is missing it is extrapolated (with bounds check). `host_position` records which slave side faces the host. <!-- anchor: detector.c:2767-2988, 2885-2903, 2906-2917, 2920-2968 -->

`detectSlave` wraps this with `getPerspectiveTransform` over the four AP centers and a whole-symbol `sampleSymbol`; there is no per-sub-block fallback for slaves. <!-- anchor: detector.c:3959-4008 -->

The cross-area machinery sized by `CROSS_AREA_WIDTH 14` (`sampleCrossArea`) is not on this path — see [09-capture-support.md](09-capture-support.md) §9.3. <!-- anchor: detector.h:28 -->

## 5.10 Master metadata Part I — the Nc bootstrap

**Responsibility.** Read the colour-mode index Nc before any palette exists. Entry: `decodeMasterMetadataPartI`, decoder.c:1262. The metadata cursor starts at `(MASTER_METADATA_X, MASTER_METADATA_Y)` = (6, 1) and advances via `getNextMetadataModuleInMaster`, a four-quadrant boustrophedon whose range tables extend to module count 524 for the high-Nc palette reads. <!-- anchor: decoder.c:1262, 2089-2091, 1080-1151; decoder.h:20-25 -->

Part I is self-describing by construction: `MASTER_METADATA_PART1_MODULE_NUMBER 4` modules are classified not against a palette but by `decodeModuleNc`, which recognizes only the bootstrap set {K=0, C=3, Y=6} — colours present identically in every colour mode. Classification rules: black if all channels < 80 (`tolerance = 80`, "Camera-captured blacks read up to ~(60,40,50)"); cyan if R < 80 ∧ G > 175 ∧ B > 175; yellow if R > 175 ∧ G > 175 ∧ B < `y_b_tolerance` — widened to 255 ("effectively means 'any B value'") after camera traces showed Y metadata modules reading B = 235–254; otherwise a normalized-standard-deviation classifier (`ths_std 0.08`) produces a 3-bit R/G/B pattern, with std below threshold returning 7 (white). Under Mode 0 the classifier is pure luminance: `< 127 → 0, else 7`. <!-- anchor: decoder.c:927-1034, 953, 988, 945-949 -->

Each classified module must be in the validity set — {0, 3, 6}, or {0, 7} under Mode 0 — else `DECODE_METADATA_FAILED`. Path β, when enabled, remaps rgb=5 (M) to rgb=6 (Y) before this check ("any rgb=5 read at a metadata module is unambiguously a misclassified Y under camera noise"); the source also records the empirical falsification that on the nc2 fixture all four modules read rgb=5, so the remap produced structurally invalid (Y, Y) pairs — the toggle remains available but is not a fix for that failure class. <!-- anchor: decoder.c:1381-1392, 1326-1345, 98-132; jabcode.h:93-98 -->

The four module colours form two pairs, each looked up in `nc_color_encode_table[8][2] = {{0,0}, {0,3}, {0,6}, {3,0}, {3,3}, {3,6}, {6,0}, {6,3}}` to yield 3 bits each (invalid pair → sentinel 8 → `DECODE_METADATA_FAILED`); the 6 bits pass `decodeLDPChd(part1, MASTER_METADATA_PART1_LENGTH, 2, 0)` (metadata LDPC, wc=2); LDPC failure returns `JAB_FAILURE` (unrecoverable for this matrix, no default fall-through); on success `Nc = (part1[0] << 2) + (part1[1] << 1) + part1[2]`. <!-- anchor: decoder.c:1242-1250, 1403-1455; decoder.h:23-25; encoder.h:124 -->

Mode 0 short-circuits the whole procedure: the four Part I positions are only marked in the data map and skipped, and Nc is asserted 0 — the chroma probe *is* the Mode 0 detection mechanism, and `{K, W}` pairs are absent from the pair table by design. <!-- anchor: decoder.c:1283-1318, 1307-1318 -->

**Default fall-through (ISO 6.1 e).** On `DECODE_METADATA_FAILED`, `decodeMaster` resets the cursor and data map and installs defaults per the spec sentence "If Part I is invalid, skip decoding the metadata in the next steps and use the default metadata values": `default_mode = 1`, `Nc = DEFAULT_MODULE_COLOR_MODE 2`, `ecl = wcwr_for_level(DEFAULT_ECC_LEVEL 3)` = (4, 9), `mask_type = DEFAULT_MASKING_REFERENCE 7`, side-versions from the sampled matrix dimensions. <!-- anchor: decoder.c:2100-2110, 1846-1860; jabcode.h:34-36; ISO 23634 6.1 e -->

## 5.11 Palette read and synthesis (ISO 6.1 f)

**Master** — `readColorPaletteInMaster` (decoder.c:436). `color_number = 2^(Nc+1)`. The palette buffer is `calloc`'d — required, per the WS-4.5.4 note, because for Nc≥3 the FP and metadata loops leave palette index 1 unwritten and `malloc` garbage there caused "~22% non-deterministic decode failure". Colours 0 and the FP-core colour of each corner are read from two fixed modules inside each finder pattern (`getColorPalettePosInFP`, positions derived from `DISTANCE_TO_BORDER 4`), indexed through `master_palette_placement_index[4][8]`; colours 2..`MIN(color_number, 64)−1` are read from the metadata spiral, one module per palette panel per colour, with sequential indexing for >8 colours ("FIX: For 16+ colors, use sequential indexing instead of placement mapping"). For >8 colours, palette index 1 is synthesized by calling the encoder's own `genColorPalette` ("Bug E" fix — the encoder's `palette[1]` differs by colour count, e.g. (0,0,255) at 16/32 but (0,0,85) at 64/128/256); for >64 colours `interpolatePalette` expands the 64 transmitted colours to 128/256 by block copy + linear interpolation. All four palette panels (`COLOR_PALETTE_NUMBER 4`) are populated. <!-- anchor: decoder.c:436-559, 442-452, 461-471, 474-516, 518-550, 552-556, 264-363, 395-424; encoder.h:39-40; jabcode.h:41 -->

**Slave** — `readColorPaletteInSlave` (decoder.c:568). Colours 0/1 per panel come from two modules of each corner alignment pattern via `slave_palette_placement_index[8] = {3, 6, 5, 0, 1, 2, 4, 7}`; colours 2..63 come from `slave_palette_position[64]`, a boustrophedon over the corner band x ∈ \[4, 11\], y ∈ \[5, 12\], applied at four rotations (one per panel). The table was extended from 32 to 64 entries for high Nc: "the slave places palette colours 2..MIN(cn,64)-1 at index \[colour-2\], needing 62 entries at cn=64; >64 colours are interpolated". The same header note records a known open defect: "at high colour, cascade still fails at slave versions == 0 (mod 5) (v10/v15/v20...) — a separate pre-existing slave capacity/alignment-geometry resonance, tracked as a follow-up; NOT the palette sizing fixed here." Palette-index-1 synthesis and >64 interpolation mirror the master. <!-- anchor: decoder.c:568-661; decoder.h:27-45; encoder.h:45 -->

Mode 0 overwrites whatever these functions produced with the implicit `{K=(0,0,0), W=(255,255,255)}` palette in all four panels — master at decoder.c:2232-2248 (comment block from 2206), slave at decoder.c:2421-2437 (comment block from 2404). <!-- anchor: decoder.c:2206-2248, 2404-2437 -->

Post-processing common to both paths in `decodeMaster`/`decodeSlave`: `normalizeColorPalette` derives per-colour normalized R/G/B (divided by the channel max) plus a luminance term; `getPaletteThreshold` computes per-channel black thresholds — but only for colour counts 4 and 8; for all other counts the zero-initialized `pal_ths` deliberately disables the early black check (WS-4.5.4: "Zero-init makes that comparison evaluate to false for any non-negative rgb byte"). <!-- anchor: decoder.c:2054-2064, 1042-1070, 2274-2286, 2444-2450 -->

## 5.12 Master metadata Part II (ISO 6.1 g)

`decodeMasterMetadataPartII` (decoder.c:1470) reads `MASTER_METADATA_PART2_LENGTH 38` encoded bits at `bits_per_module = Nc + 1` bits per module — computed spec-directly, replacing a `log(color_number)/log(2)` expression whose float truncation on ARM glibc yielded 5 instead of 6 at Nc=5 and 6 instead of 7 at Nc=6, "making LDPC uncorrectable at PartII for Nc=5 and Nc=6 specifically". `modules_needed = ceil(38 / bits_per_module)`; whole modules are read (padding bits included) but LDPC runs over exactly 38 bits (`decodeLDPChd(part2, MASTER_METADATA_PART2_LENGTH, 2, 0)`). Field layout after LDPC: V = 5+5 bits → `side_version = V + 1` per axis; E = 3+3 bits → `ecl.x = E + 3` (wc), `ecl.y = E + 4` (wr); MSK = 3 bits → `mask_type`. Validity checks: decoded side sizes must equal the sampled matrix dimensions (`JAB_FAILURE` — "Primary symbol matrix size does not match the metadata"), and `wc < wr` (`DECODE_METADATA_FAILED` — "Incorrect error correction parameter in primary symbol metadata"). <!-- anchor: decoder.c:1470-1631, 1476-1493, 1496-1497, 1548-1557, 1562-1597, 1601-1625; decoder.h:24 -->

## 5.13 Module classification — `decodeModuleHD` (ISO 6.1 h)

For each data module, `getNearestPalette` selects one of the four palette panels by Euclidean distance from the module to four fixed anchor positions just inside the FPs — the "nearest colour palette" of ISO 6.1 h. <!-- anchor: decoder.c:670-697; ISO 23634 6.1 h -->

Classification order inside `decodeModuleHD` (decoder.c:710):

1. Optional calibration remap of the sampled RGB (`USE_FP_CALIBRATION`, compiled out by default — [10-fork-extensions.md](10-fork-extensions.md) §10.2). <!-- anchor: decoder.c:725-736 -->
2. Early black: all three channels below the panel's `pal_ths` → index 0 (only effective at colour counts 4/8, per §5.11). <!-- anchor: decoder.c:741-745 -->
3. Nearest-palette scan keeping best and second-best (`index1`/`index2`, `min1`/`min2`). Two arithmetic paths, switched by `use_direct_rgb = (color_number > 8)`: for ≤8 colours the sample and palette are max-normalized before squared-Euclidean comparison; for >8 colours raw RGB squared-Euclidean is used ("normalized comparison fails for same-hue colors"), with the panel offset applied ("CRITICAL FIX: Must include palette slot offset (p_index) for multi-palette support"). Under `USE_LAB_DISTANCE` the >8 path is replaced by CIE Lab ΔE2000 (`rgb_to_lab` + `delta_e_2000`) with identical best/second-best semantics; the gate is not defined by any Makefile in this tree — [10-fork-extensions.md](10-fork-extensions.md) §10.3. <!-- anchor: decoder.c:746-835, 750, 752-792, 804-815 -->
4. Black/white disambiguation: if the winner is index 0 or `color_number − 1`, re-decide by comparing the sample's channel sum against the midpoint of the two palette sums. <!-- anchor: decoder.c:837-853 -->
5. nc2 white-demote (8-colour only): a module filed as white whose chroma (`max − min` channel spread) exceeds `NC2_WHITE_DEMOTE_CHROMA 20` is re-filed to the nearest non-black, non-white palette colour — the "magenta-rescue" for washed saturated colours ("measured 2026-06-10: 0% clean magenta, 91% white"). <!-- anchor: decoder.c:250-256, 855-889 -->
6. No-palette fallback (used only when `palette == NULL`): majority of channels above 100 → 1, else 0. <!-- anchor: decoder.c:915-918 -->

An inherited near-tie refinement block (comparing channel-difference signatures of the two best candidates) is present but commented out. <!-- anchor: decoder.c:891-913 -->

## 5.14 `decodeMaster` — the Nc fallback ladder, pinning, and strict mode

`decodeMaster` (decoder.c:2072) wraps §§5.10–5.13 in a retry loop over candidate colour modes. After Part I (§5.10), the cursor, module count, and data map are snapshotted; the ladder is

```c
jab_byte nc_order[] = {original_Nc, 1, 0, 2, 3, 4, 5, 6};
jab_int32 nc_tries = 8;
```

<!-- anchor: decoder.c:2112-2119 -->

`original_Nc` is the Part I result or the installed default (Nc=2). Observation, stated factually: Nc=7 does not appear in the retry tail — a 256-colour symbol whose Part I failed is never retried as Nc=7 unless pinned. Duplicate of `original_Nc` in the tail is skipped; each retry restores the post-Part-I snapshot and frees any `symbol->data` left by the previous iteration (WS-5 round-6 hygiene against returning a stale iteration's payload). <!-- anchor: decoder.c:2119, 2152-2181 -->

**Pinning.** `g_preferred_color_count` (settable via `jabSetPreferredColorCount`; "Valid values: 2, 4, 8, 16, 32, 64, 128, 256 (= 2^(Nc+1) for Nc=0..7). 0 = auto (default)") collapses the ladder: the count maps through an explicit switch to `pinned_Nc = log2(count) − 1`, `nc_order[0] = pinned_Nc`, `nc_tries = 1`. "Invalid counts (anything not in the canonical set) fall through to default auto-detect behaviour." The global is process-global by design ("decoder-instance-wide configuration, not per-call") with a documented concurrency contract: set before spawning workers, read-only during decode. <!-- anchor: decoder.c:2122-2150, 157-194; jabcode.h:100-105, 264-271 -->

**Per-iteration body.** `readColorPaletteInMaster` → (Mode 0 palette overwrite) → `normalizeColorPalette`/`getPaletteThreshold` → Part II — but Part II runs only when Part I succeeded. When Part I failed, behaviour splits on the `__thread jab_boolean g_strict_partII_required` flag (set via `jabSetStrictPartIIRequired`, default FALSE, thread-local, "Reset to FALSE after each decode" by callers per the header contract):

- strict: the fall-through is permitted only for the genuine default case — Part I returned `DECODE_METADATA_FAILED` *and* the current candidate is `DEFAULT_MODULE_COLOR_MODE` — because default-mode (Nc=2) symbols "OMIT PartI by design ... for them PartI is legitimately ABSENT, not corrupted"; every other candidate is skipped, preventing "fabricated decodes from default-metadata Nc=2 state on degraded camera input";
- non-strict (legacy): every candidate proceeds optimistically with the installed metadata, the behaviour multi-frame-averaging callers rely on.

<!-- anchor: decoder.c:2288-2350, 26-48; jabcode.h:238-245 -->

A candidate that passes gets `decodeSymbol` (§5.15) on a *copy* of the data map ("decodeSymbol frees data_map internally"); `JAB_SUCCESS` exits the loop, anything else advances the ladder. Loop exhaustion returns `JAB_FAILURE`. <!-- anchor: decoder.c:2352-2368 -->

## 5.15 `decodeSymbol` — demask, de-interleave, LDPC, Part III (ISO 6.1 h–m)

Common payload path for master and slave (decoder.c:1872):

1. `fillDataMap` marks FP/AP cross-and-diagonal modules for every `jab_ap_pos` grid point — skipped when the synthetic decoder supplied the encoder's own map (`symbol->metadata.Pg > 0` is the discriminator). <!-- anchor: decoder.c:1763-1839, 1914-1917 -->
2. `readRawModuleData` classifies every unmarked module via `decodeModuleHD`, column-major (`for j < width { for i < height ... }`), producing one palette index per module (`COLOR_CLASSIFY` profile stage). <!-- anchor: decoder.c:1643-1686 -->
3. `demaskSymbol` releases masking (ISO 6.1 i; algorithm in [07-mask.md](07-mask.md)). <!-- anchor: decoder.c:1934 -->
4. `rawModuleData2RawData` expands indices to `Nc + 1` bits per module. <!-- anchor: decoder.c:1736-1754, 1944 -->
5. Gross/net payload: `Pg = (raw_data->length / wr) * wr` ("max_gross_payload = floor(capacity / wr) * wr"; the synthetic path substitutes the encoder's exact `Pg`), `Pn = Pg * (wr - wc) / wr`. Padding beyond `Pg` is dropped, then `deinterleaveData` (ISO 6.1 j). <!-- anchor: decoder.c:1952-1968 -->
6. `decodeLDPChd((jab_byte*)raw_data->data, Pg, wc, wr)` must return exactly `Pn` (ISO 6.1 k); mismatch is the `ldpc_fail` diagnostic and `JAB_FAILURE`. <!-- anchor: decoder.c:1977-1989 -->
7. Part III (ISO 6.1 m): scanning backwards from `Pn − 1`, trailing zeros are skipped and the first 1 is the metadata start flag; the next 4 bits (again backwards) are the `docked_position` mask — when the symbol is itself a slave, its own `host_position` bit is skipped; for each docked side, `decodeSlaveMetadata` consumes the variable-length slave record. <!-- anchor: decoder.c:2004-2035 -->
8. The remaining `metadata_offset + 1` bits are the symbol's net data, copied to `symbol->data`. <!-- anchor: decoder.c:2037-2047 -->

**Slave metadata records** (`decodeSlaveMetadata`, decoder.c:1161) are read backwards from the offset: 1 bit SS (0 → inherit host side-version), 1 bit SE (0 → inherit host `ecl`); if SS=1, 5 bits V → `side_version = V + 1`, applied to the free axis (x for docked positions 2/3, y for 0/1); if SE=1, 3+3 bits E → `wc = E + 3`, `wr = E + 4`, with `wc >= wr` rejected ("Incorrect error correction parameter in slave metadata"). Nc and mask type always inherit from the host. Returns bits consumed, or `DECODE_METADATA_FAILED` on stream underrun. <!-- anchor: decoder.c:1161-1234 -->

**`decodeSlave`** (decoder.c:2377) is `decodeMaster` minus metadata and minus the ladder: data map → `readColorPaletteInSlave` → Mode 0 overwrite → normalize/thresholds → `decodeSymbol(type=1)`. Metadata arrived through the host (Part III). <!-- anchor: decoder.c:2377-2454 -->

## 5.16 `decodeData` — mode decode of the transmitted stream (ISO 6.1 l)

`decodeData` (decoder.c:2538) is a state machine over `jab_encode_mode` {`Upper`, `Lower`, `Numeric`, `Punct`, `Mixed`, `Alphanumeric`, `Byte`, `ECI`, `FNC1`} with character widths `character_size[7] = {5, 5, 4, 4, 5, 6, 8}` and the static decoding tables of decoder.h:50-56. Latch vs shift is tracked via `pre_mode` (shift modes return after one character). <!-- anchor: decoder.c:2538-2969; decoder.h:61-72, 50-56; encoder.h:207 -->

Non-obvious specifics, all fork-verified against the spec:

- Upper "11111 11" escapes to Table 15 (`decodeTable15`): 3 selector bits → ISO/IEC 15434 header `[)>` + RS, `https://` / `http://` / `www.` expansions, FNC1 (first occurrence sets the symbology-identifier mode and is not emitted; subsequent ones emit GS 0x1D), EoT 0x04; selectors 6/7 are "reserved — forward-compatible no-op". <!-- anchor: decoder.c:2487-2531, 2633-2639 -->
- Lower "11111 11" is a shift to Numeric — "(NOT FNC1). Conformance fix." <!-- anchor: decoder.c:2700-2704 -->
- Byte mode: 4-bit length; length 0 → 13 more bits with `value += 15+1`. <!-- anchor: decoder.c:2864-2907 -->
- ECI: indicator-prefixed 7/14/20-bit assignment number, transmitted as `\` + 6 zero-padded digits per clause 7.3, after which "encoding returns to the invoking (uppercase) mode"; sets `eci_used` (Annex H modifier → `]j1`) and `eci_active`, which makes `emitDataByte` double literal backslashes in the Punct/Mixed/Byte paths. <!-- anchor: decoder.c:2908-2956, 2476-2485 -->
- The FNC1 *mode* case is a stub ("TODO: not implemented"); FNC1 semantics are reached via Table 15 instead. <!-- anchor: decoder.c:2958-2962 -->
- On success the Annex H symbology identifier is formatted into the `_Thread_local` `g_symbology_identifier` ("reset per-decode ... Each thread reads back its OWN last decode"); the payload itself is never modified — "per ISO/IEC 23634 7.4 the identifier is a transmission-layer preamble the host prepends". <!-- anchor: decoder.c:196-208, 2550-2557, 2982-2988 -->

Invalid table values abort with `reportError("Invalid value decoded")` and NULL — the `decode_data` failure attribution of §5.2. <!-- anchor: decoder.c:2642-2646, 2707-2711 -->

## 5.17 Synthetic bypass — `decodeJABCodeSynthetic`

`decodeJABCodeSynthetic` (detector_synthetic.c:128) is the perfect-image path for encoder-generated bitmaps: "This function bypasses camera-specific detection logic (Nc detection, palette learning, pattern detection) by using known encoding parameters and spatial metadata. This solves the 'too perfect' problem where camera-tuned detectors fail on synthetic images." <!-- anchor: detector_synthetic.c:124-127 -->

What it skips, relative to §§5.4–5.14: `balanceRGB`/`binarizerRGB` (D1), the Mode 0 chroma probe, `findMasterSymbol` (D2), side-size computation and Table 24 snap (D3), the perspective transform (D4 — modules are read by direct center-pixel indexing at `0.5 * module_size + n * module_size`), AP search and per-sub-block sampling, `decodeMaster` entirely — Part I, palette read, Part II, the Nc ladder, strict mode. Instead it constructs `symbols[0]` from arguments: Nc via an explicit `color_number` switch (2→0 ... 256→7; the WS-0/WS-3 cases that make Nc=0 and Nc=7 synthetic decode possible), `(wc, wr)` from `encoder_wcwr` or a local `ecclevel2wcwr` copy ("MUST match encoder.h exactly!") via `wcwr_for_level`, side-versions via `SIZE2VERSION`, FP positions from geometry with no quiet zone, the default palette replicated into all four panels, and the encoder's `data_map` with inverted convention ("Encoder: data_map\[i\]==0 means metadata/palette ... Decoder: data_map\[i\]==0 means DATA ... So we invert"). It then calls `decodeSymbol` directly, followed by `decodeData`; status is 3 on success, 1 on any failure past allocation. Because `symbol->metadata.Pg` is set to the encoder's value, `decodeSymbol` skips `fillDataMap` and uses the exact `Pg` (§5.15). <!-- anchor: detector_synthetic.c:128-224, 147-160, 172-186, 226-270, 287-298, 333, 429-430; decoder.c:1914-1917, 1957-1961 -->

Cascades are out of scope for the synthetic path: `total` is fixed at 1 and no slave recursion exists. <!-- anchor: detector_synthetic.c:136, 332 -->

## 5.18 Failure-to-stage diagnosis

| Symptom | Stage | Where to look |
|---|---|---|
| status 0, `FAIL_ATTR stage=detect_or_slave`, `module_size == 0` | Finder search found <3 FPs | §5.4; `Too few finder pattern found` (detector.c:2072); scale-adaptive `crossCheckColor` tolerance for large prints (detector.c:789) |
| status 1 after detection | Metadata/palette/LDPC | Part I validity set (decoder.c:1381-1392); Part II side-size mismatch (1604-1614) or `wc >= wr` (1618-1625); data LDPC `ldpc_fail` (1981-1989) |
| Wrong side size accepted, later `side_version` mismatch | Table 24 snap chose the flag-0 guess | §5.5; `getSideSize` case 3 (detector.c:3008-3011) |
| Decodes as Nc=2 with wrong payload on camera input | Non-strict default fall-through | §5.14; enable `jabSetStrictPartIIRequired(1)` (decoder.c:26-48) |
| Nc=7 symbol with damaged Part I never decodes | Ladder omits 7 in the retry tail | §5.14; pin via `jabSetPreferredColorCount(256)` (decoder.c:2119, 2128-2150) |
| High-colour cascade fails at slave version ≡ 0 (mod 5) | Known open defect | decoder.h:32-34 note |
| Slave "first/second alignment pattern ... not found" | Slave AP seed estimate off host edge | §5.9; detector.c:2889-2903 |

Stage-attribution log lines (`FAIL_ATTR`, `DECODE_OK`) are Android-only (`__android_log_print`); desktop builds compile them to no-ops. High-volume per-stage markers require `jabSetDiagVerbose(1)` — the WS-5 Heisenberg gate, quoted in full in [10-fork-extensions.md](10-fork-extensions.md) §10.6. <!-- anchor: detector.c:24-37; jabcode.h:72-91 -->
