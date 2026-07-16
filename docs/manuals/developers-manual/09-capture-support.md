# 9. `binarizer.c`, `transform.c`, `sample.c`, `image.c` — capture support

<!-- objective: A maintainer can identify which binarizer variant, transform, and sampling routine serves each detection mode, and use the image I/O surface (PNG, CMYK TIFF, in-memory PNG) with correct format constraints. -->

Four small translation units supply the detector's capture-side primitives: channel balancing and binarization (`binarizer.c`, 752 lines), the perspective homography (`transform.c`, 234 lines), grid resampling (`sample.c`, 192 lines), and image I/O (`image.c`, 334 lines). All are exported through `detector.h` (I/O through `jabcode.h`); their pipeline positions are stages D1, D4, and D5 of [02-codec-pipeline.md](02-codec-pipeline.md) §2.2. Consumers: [05-detector-and-decoder.md](05-detector-and-decoder.md) (detector), [11-cli-internals.md](11-cli-internals.md) (CLI I/O).

## 9.1 `binarizer.c`

**Responsibility.** Convert the captured RGBA bitmap into three single-channel binary bitmaps (one per RGB channel) that the finder-pattern machinery scans, plus histogram stretching of the input. Five entry points exist; only two are on the live decode path.

**Public surface.**

| Item | Signature | When used |
|---|---|---|
| `balanceRGB` | `void balanceRGB(jab_bitmap* bitmap)` | D1, always: first call of `decodeJABCodeEx`, in place on the input bitmap. <!-- anchor: binarizer.c:485; detector.c:4087 --> |
| `binarizerRGB` | `jab_boolean binarizerRGB(jab_bitmap* bitmap, jab_bitmap* rgb[3], jab_float* blk_ths)` | D1, always: pass 1 with `blk_ths = 0` (per-block average thresholds); pass 2 inside `detectMaster` with `blk_ths = rgb_ave` (fixed FP-neighbourhood thresholds) when pass-1 finder search failed. <!-- anchor: binarizer.c:602; detector.c:4088, 3765 --> |
| `binarizer` | `jab_bitmap* binarizer(jab_bitmap* bitmap, jab_int32 channel)` | No in-tree caller. Local (block-adaptive) single-channel binarization; falls back to `binarizerHist` below `MINIMUM_DIMENSION`. <!-- anchor: binarizer.c:408-448; detector.h:72 --> |
| `binarizerHist` | `jab_bitmap* binarizerHist(jab_bitmap* bitmap, jab_int32 channel)` | Reachable only via `binarizer`'s small-image fallback (binarizer.c:446). Global histogram-valley threshold. <!-- anchor: binarizer.c:106; detector.h:73 --> |
| `binarizerHard` | `jab_bitmap* binarizerHard(jab_bitmap* bitmap, jab_int32 channel, jab_int32 threshold)` | No in-tree caller. Fixed-threshold single-channel binarization. <!-- anchor: binarizer.c:184; detector.h:74 --> |
| helpers | `getAveVar` (binarizer.c:548), `getMinMax` (binarizer.c:579) | Exported via detector.h:68-69; consumed by `decodeModuleNc` (decoder.c:1001, 1005). |

The corpus model's D1 row lists `binarizerHist`/`binarizerHard`/`binarizer` (106/184/408) as the binarizer variants; in this tree those three are pipeline-dormant — the live D1 entries are `balanceRGB` (485) and `binarizerRGB` (602). The detection *mode* (`QUICK/NORMAL/INTENSIVE_DETECT`) does not select a binarizer variant; it only changes the finder-scan stride ([05-detector-and-decoder.md](05-detector-and-decoder.md) §5.3). What varies between detection passes is `binarizerRGB`'s threshold source, per the table above.

**`balanceRGB`** stretches each channel's histogram to full range: per-channel min/max are the outermost histogram bins whose count exceeds `count_ths = 20` (`getHistMaxMin`); values outside clamp to 0/255; values inside scale by `(v − min) / (max − min) * 255.0` in `jab_double`. Two fork-annotated constraints: the three histograms are accumulated in a single pass ("byte-identical to three getHistogram() calls"), and the stretch arithmetic is deliberately kept as divide-then-multiply — "folding these into a single reciprocal scale would change float rounding." <!-- anchor: binarizer.c:485-540, 457-479, 496-517 -->

**`binarizerRGB`** produces the three binary channels in one sweep:

1. Threshold source: with `blk_ths != 0`, the caller's three fixed thresholds; with `blk_ths == 0`, per-block channel averages over a grid of at most 2×2 blocks (`max_block_size = MAX(width, height) / 2`). <!-- anchor: binarizer.c:622-659, 679-704 -->
2. Per pixel: all three channels below threshold → black in all channels; else compute mean (integer division, "as in getAveVar") and normalized standard deviation `std / max`; `std < ths_std (0.08)` *and* all channels above threshold → white in all channels; otherwise sort the channels ("same strict '>' comparison sequence as getMinMax(), so ties resolve identically"), set max-channel 255, min-channel 0, and assign the middle channel by ratio comparison `r1 = mid/min` vs `r2 = max/mid` (closer to max → 255). <!-- anchor: binarizer.c:661-746, 667, 706-745 -->
3. Each output channel is smoothed by `filterBinary`: separable 5-tap horizontal-then-vertical majority filter (`filter_size = 5`, output 255 when more than `half_size = 2` of the taps are set). <!-- anchor: binarizer.c:352-400, 748-750 -->

The per-pixel body is a fork optimization of the reference implementation — "an inlined, branch-hoisted equivalent of the reference getAveVar()/getMinMax() calls (same arithmetic and the same strict-> sort order, hence byte-identical), but in a single sweep with the loop-invariant threshold source pulled out of the hot path." <!-- anchor: binarizer.c:661-666 -->

**`binarizer` / `binarizerHist` / `binarizerHard`** (dormant): `binarizer` implements block-adaptive thresholding with `BLOCK_SIZE_POWER 5` (32-pixel blocks; `MINIMUM_DIMENSION = BLOCK_SIZE * 5` = 160), computing per-block black points (`calculateBlackPoints`, minimum dynamic range 24, low-contrast blocks inherit a neighbour-weighted black point) and thresholding each block against a 5×5 block-neighbourhood average (`getBinaryBitmap`), followed by `filterBinary`. `binarizerHist` builds a per-channel histogram — with channel-specific pixel-skip rules for the green (channel 1: skip white/black/yellow) and blue (channel 2: skip white/black) channels — smooths it until bimodal (trimodal for green; `isBiTrimodal`), capped at 1000 iterations, and thresholds at the valley after the first (second for green) peak (`getMinimumThreshold`). `binarizerHard` applies the caller's fixed threshold. <!-- anchor: binarizer.c:20-24, 215-346, 408-448, 32-98, 126-162, 184-204 -->

**Failure modes.** All allocation failures report and return NULL/`JAB_FAILURE`; `getMinimumThreshold` returns −1 when smoothing does not converge or no valley is found (the subsequent `> ths` comparison then binarizes everything above −1 to 255). <!-- anchor: binarizer.c:80, 97, 166-172 -->

## 9.2 `transform.c`

**Responsibility.** 3×3 projective (homography) transforms between symbol module space and image pixel space, composed square→quad / quad→square per the standard adjugate construction. Theory and derivation: Special Topics (JC-S), forthcoming.

**Public surface.**

| Item | Signature | Notes |
|---|---|---|
| `getPerspectiveTransform` | `jab_perspective_transform* getPerspectiveTransform(jab_point p0, jab_point p1, jab_point p2, jab_point p3, jab_vector2d side_size)` | The detector-facing constructor. <!-- anchor: transform.c:202-217; detector.h:75-77 --> |
| `perspectiveTransform` | 16-float general quad→quad constructor | Used directly by `sampleSymbolByAlignmentPattern` for per-block transforms. <!-- anchor: transform.c:164-191; detector.c:3548-3556 --> |
| `warpPoints` | `void warpPoints(jab_perspective_transform* pt, jab_point* points, jab_int32 length)` | In-place point projection. <!-- anchor: transform.c:225-234 --> |
| `jab_perspective_transform` | struct of nine `jab_float` `a11..a33` | detector.h:56-66. |

**Contract of `getPerspectiveTransform`.** It maps *module-space pattern centers* to the four supplied pixel-space centers, in the FP order UL, UR, LR, LL. The module-space source corners are fixed at `(3.5, 3.5)`, `(side_size.x − 3.5, 3.5)`, `(side_size.x − 3.5, side_size.y − 3.5)`, `(3.5, side_size.y − 3.5)` — 3.5 modules inside each corner, i.e. the FP/AP center positions given `DISTANCE_TO_BORDER 4`. The resulting matrix therefore projects any module-space coordinate (module centers are addressed as index + 0.5 by the samplers) into pixel space. <!-- anchor: transform.c:208-216; jabcode.h:39 -->

**Projection convention** (`warpPoints`): row-vector homogeneous multiply with post-division —

```c
jab_float denominator = pt->a13 * x + pt->a23 * y + pt->a33;
points[i].x = (pt->a11 * x + pt->a21 * y + pt->a31) / denominator;
points[i].y = (pt->a12 * x + pt->a22 * y + pt->a32) / denominator;
```

<!-- anchor: transform.c:225-234 -->

**Construction.** `square2Quad` builds unit-square→quad directly, with the affine short-circuit when `dx3 == 0 && dy3 == 0`; `quad2Square` is its adjugate ("calculate the adjugate matrix of s2q" — inversion up to scale, sufficient because projection divides by the homogeneous term); `perspectiveTransform` composes `multiply(q2s, s2q)`. <!-- anchor: transform.c:33-78, 92-116, 124-142, 164-191 -->

**Ownership.** Every constructor `malloc`s the returned matrix; the caller frees. Composition intermediates are freed internally on the success path. <!-- anchor: transform.c:38, 188-189 -->

**Known defects** (allocation-failure edges only): `quad2Square` dereferences the `square2Quad` result without a NULL check; `perspectiveTransform` returns NULL without freeing `q2s` when the second constructor fails — a leak on a path only reachable under `malloc` failure. <!-- anchor: transform.c:103-105, 178-182 -->

## 9.3 `sample.c`

**Responsibility.** Resample the image into a per-module matrix under a perspective transform: one output pixel per module, each the 3×3-neighbourhood average around the projected module center.

**Public surface.**

| Item | Signature | When used |
|---|---|---|
| `sampleSymbol` | `jab_bitmap* sampleSymbol(jab_bitmap* bitmap, jab_perspective_transform* pt, jab_vector2d side_size)` | D5: whole-symbol sampling for master and slave; per-block sampling inside `sampleSymbolByAlignmentPattern`. <!-- anchor: sample.c:31; detector.c:3827, 3997, 3567 --> |
| `sampleCrossArea` | `jab_bitmap* sampleCrossArea(jab_bitmap* bitmap, jab_perspective_transform* pt)` | No in-tree caller (exported via detector.h:88; dormant). <!-- anchor: sample.c:124 --> |
| `SAMPLE_AREA_WIDTH` | `(CROSS_AREA_WIDTH / 2 - 2)` = 5 | "width of the columns where the metadata and palette in slave symbol are located". <!-- anchor: sample.c:21; detector.h:28 --> |
| `SAMPLE_AREA_HEIGHT` | `20` | "height of the metadata rows including the first row, though it does not contain metadata". <!-- anchor: sample.c:22 --> |

**`sampleSymbol` mechanics.** For each output row, module centers `(j + 0.5, i + 0.5)` are projected via `warpPoints` (one `jab_point points[side_size.x]` VLA per row); projected coordinates are rounded and clamped by at most one pixel at the image border (`mapped_x == -1 → 0`, `== width → width−1`); any coordinate further outside aborts the whole sample with NULL. Each output channel value is the mean of the 3×3 pixel neighbourhood, with out-of-image neighbours replaced by the center pixel. The output matrix inherits the input's channel layout (RGBA in the library pipeline), so `matrix->pixel` is indexed by the decoder at 4 bytes per module. <!-- anchor: sample.c:31-116, 50-58, 61-74, 92-104 -->

Two notes, factual: the `#ifdef MOBILE_BUILD` arms of the sampling loop are byte-identical (historical residue of a mobile-only averaging experiment); and the early-abort NULL returns leak the already-allocated `matrix` (allocation at sample.c:35, returns at 67/73 without free). <!-- anchor: sample.c:77-105, 35, 63-74 -->

**Cross-area sampling.** `sampleCrossArea` samples a fixed 5×20-module strip at module-space x offset `CROSS_AREA_WIDTH / 2 + 0.5` — the strip crossing the host/slave boundary where a slave's metadata and palette live (`CROSS_AREA_WIDTH 14` is "the width of the area across the host and slave symbols"). Same projection, clamping, and 3×3 averaging as `sampleSymbol`; same leak pattern on early abort. In this fork the slave path samples the whole slave symbol instead ([05-detector-and-decoder.md](05-detector-and-decoder.md) §5.9), leaving this routine dormant. <!-- anchor: sample.c:124-191, 143-151; detector.h:28 -->

## 9.4 `image.c`

**Responsibility.** Bitmap ↔ file/memory conversion: PNG read/write (file and memory) via libpng's simplified API, CMYK TIFF write via libtiff. The vendored headers are `png.h` (libpng 1.6.22) and `tiffvers.h`/`tiffio.h` (libtiff 4.0.10); link resolution is chapter 1 territory. <!-- anchor: image.c:17-19; corpus §1.3 -->

**Public surface.**

| Item | Signature | Format contract |
|---|---|---|
| `saveImage` | `jab_boolean saveImage(jab_bitmap* bitmap, jab_char* filename)` | PNG. `channel_count == 4` → `PNG_FORMAT_RGBA` with alpha+colour flags; anything else → `PNG_FORMAT_GRAY`. <!-- anchor: image.c:27-58 --> |
| `saveImageCMYK` | `jab_boolean saveImageCMYK(jab_bitmap* bitmap, jab_boolean isCMYK, jab_char* filename)` | TIFF, `PHOTOMETRIC_SEPARATED`, contiguous planar, per-scanline write. `isCMYK` false → converts via `convertRGB2CMYK` first. <!-- anchor: image.c:128-180 --> |
| `readImage` | `jab_bitmap* readImage(jab_char* filename)` | **PNG only** — `png_image_begin_read_from_file`; any non-PNG input fails with the libpng message plus "Opening png image failed". Output is always forced to `PNG_FORMAT_RGBA`. <!-- anchor: image.c:187-231 --> |
| `saveImageToMemory` | `jab_byte* saveImageToMemory(jab_bitmap* bitmap, jab_int32* out_length)` | In-memory PNG; caller owns the buffer (`free()`). <!-- anchor: image.c:244-283 --> |
| `readImageFromMemory` | `jab_bitmap* readImageFromMemory(jab_byte* buffer, jab_int32 length)` | In-memory counterpart of `readImage`; PNG only, forced RGBA. <!-- anchor: image.c:294-334 --> |

**Bitmap layout constants.** Every bitmap produced by the readers carries `bits_per_pixel = BITMAP_BITS_PER_PIXEL 32`, `bits_per_channel = BITMAP_BITS_PER_CHANNEL 8`, `channel_count = BITMAP_CHANNEL_COUNT 4` — the RGBA layout every downstream consumer (binarizer, sampler, decoder) assumes when computing `bytes_per_pixel = bits_per_pixel / 8`. <!-- anchor: jabcode.h:43-45; image.c:208-210, 315-317 -->

**CMYK conversion.** `convertRGB2CMYK` implements the standard `k = 1 − max(r', g', b')` mapping with the pure-black special case (`k == 1` → C=M=Y=0, K=255), producing a 4-channel bitmap with the same layout constants. Requires `channel_count >= 3` ("Not true color RGB bitmap"). <!-- anchor: image.c:65-119 -->

**In-memory write path.** `saveImageToMemory` is single-pass by design: it allocates `PNG_IMAGE_PNG_SIZE_MAX(image)` ("libpng's guaranteed upper bound for the encoded PNG"), writes once, then `realloc`s down to the actual size — "avoiding the NULL-buffer sizing pass, which also compresses the image (the old two-pass path compressed every frame twice)." The doc comment records the motivating consumer: "for the auth/COA server ... sensitive payloads (the PNG decodes back to the token/signature) never touch the filesystem." If the shrinking `realloc` fails, the original (oversized but valid) buffer is returned. <!-- anchor: image.c:234-283, 261-266, 280-282 -->

**Failure modes.** All paths report the libpng/libtiff message and a fixed error string, then return `JAB_FAILURE`/NULL; `readImage`/`readImageFromMemory` free the partially built bitmap on read failure. Consumer-visible consequence: `jabcodeReader` accepts PNG input only (usage string "input-image(png)"), and `--color-space 1` output from the writer is TIFF — the operator-facing statements of these constraints are [../operators-manual/08-decoding-with-jabcodereader.md](../operators-manual/08-decoding-with-jabcodereader.md) and [../operators-manual/07-encoding-with-jabcodewriter.md](../operators-manual/07-encoding-with-jabcodewriter.md). <!-- anchor: image.c:52-57, 217-229, 148-153; src/jabcodeReader/jabreader.c:14 -->

## 9.5 Performance notes

The D1 stage (balance + binarize) is timed as the `DETECT_BINARIZE` sub-stage of the decode profiler; `DETECT_SAMPLE` covers `sampleSymbol`/`sampleSymbolByAlignmentPattern`, `DETECT_TRANSFORM` covers `calculateSideSize` + `getPerspectiveTransform`. The `profile` make target plus `scripts/plot_detect_substage.py` renders the sub-stage split per colour mode; harness and methodology are chapter 12 territory, hook reference is [10-fork-extensions.md](10-fork-extensions.md) §10.5. <!-- anchor: decode_profile.h:29-32; detector.c:4086-4089, 3795-3828; src/jabcode/Makefile:107-119 -->
