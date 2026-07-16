# Stage 0 Corpus Model — jabcode repository

Single source of truth for all manuals generated from this repository. Built 2026-07-15 following the manual-forge corpus-modeling procedure. Rule of construction: **extract, never invent** — every value below is quoted from source with a `file:line` anchor; anything that could not be located is marked **NOT FOUND**.

All paths are relative to the repository root `jabcode/` unless stated otherwise.

---

## 1. Provenance

### 1.1 Repository identity (direct file view — authoritative)

The model was built with direct file tools against `/mnt/b34628fa-d41e-4c37-8caf-f06a6ecbb1ae/projects/practice/barcode/jabcode`, per the corpus-modeling environment caution ("Build the corpus model with direct file tools; use the shell only for git metadata and verify it can actually see the repo before trusting its output").

- Branch (from `.git/HEAD`): `ref: refs/heads/swift-java-poc`
- HEAD commit (from `.git/refs/heads/swift-java-poc`): `8f76559343bbba75bc83b38bbe8bb1002a68dd0a`
- Last reflog entry (`.git/logs/HEAD:259`): fast-forward pull to `8f76559` at Unix time `1783894482 -0400` (≈ 2026-07-12)
- Remotes (`.git/packed-refs`): `origin/swift-java-poc`, `origin/master`, `upstream/master`, plus ~50 `origin/claude/ws-*` work-stream branches
- Model build date: **2026-07-15**

### 1.2 Session-shell git metadata and the mount discrepancy

The session shell (`/sessions/focused-ecstatic-babbage/mnt/jabcode`) was used for git metadata as instructed. It reported:

- `git rev-parse HEAD` → `3b56eef72436c88fc8538dc4d63920e058537874`
- `git branch --show-current` → `master`
- `git status --porcelain` → ` M src/jabcode/encoder.c`, ` M src/jabcodeWriter/jabwriter.c`, plus untracked build artifacts (`src/jabcode/*.o`, `src/jabcode/build/libjabcode.a`, `src/jabcodeWriter/bin/jabcodeWriter`) and sample files (`jabcode-samples/`, two PDFs)

Note explicitly: per that status, `src/jabcode/encoder.c` and `src/jabcodeWriter/jabwriter.c` carry uncommitted working changes, and **this model reflects the working tree, not HEAD**.

However, verification (required by the procedure) showed the shell mount and the direct file view are **two different clones**:

| Evidence | Shell mount | Direct file view |
|---|---|---|
| `.git/HEAD` | `refs/heads/master` | `refs/heads/swift-java-poc` |
| Pack file | `pack-9a295887f79e08d132ce2902445bd43fdf5156e5` | `pack-0ef7fe222eda00e64c54d9cede69359566212172` |
| `LICENSE` | MIT relicense notice (commit 3b56eef, "updated the license to the MIT license", 2026-04-17) | Full LGPL 2.1 text (`LICENSE:1-504`) |
| `src/jabcode` contents | Upstream 11-file layout | Fork layout with 17 library `.c` files, `test/` suite, expanded Makefile |

The shell-mount clone is essentially upstream jabcode 2.0.0 (its HEAD `Makefile` is 19 lines; its `decoder.c` is 1832 lines vs 3017 here). Every content statement in this model therefore comes from the **direct file view** (the `swift-java-poc` fork working tree). Because the shell cannot see this tree, a `git status` for it is **NOT VERIFIABLE**; visible evidence of an uncommitted working layer includes scratch headers (`src/jabcode/include/jabcode.h.bak`, `.bak2`, `jabcode_fixed.h`, `fixed_declaration.txt`, `fixed_line.txt`), a `build-debug/` object directory, and compiled test binaries beside their sources in `src/jabcode/test/`.

### 1.3 Exclusions applied

Noted for existence and license only; not modeled further:

- **Vendored third-party headers** in `src/jabcode/include/`: libpng (`png.h:316` → `PNG_LIBPNG_VER_STRING "1.6.22"`), zlib (`zlib.h:40` → `ZLIB_VERSION "1.2.8"`), libtiff (`tiffvers.h:1` → `"LIBTIFF, Version 4.0.10"`). Each carries its own permissive license (libpng, zlib, libtiff licenses respectively).
- **Vendored prebuilt libraries** `src/jabcode/lib/{libpng16.a, libtiff.a, libz.a}` (+ `lib/win64/` and `License_libpng.txt`, `License_zlib.txt`): tracked in the shell-mount clone's git index, but **absent from this working tree** — the writer/reader Makefiles still pass `-L../jabcode/lib`, so link resolution currently falls to system libraries.
- **Build outputs**: `src/jabcode/build/`, `src/jabcode/build-debug/`, `*.o`, compiled binaries in `src/jabcode/test/`, `src/jabcodeWriter/bin/`, repo-root `output/` (contains legacy JNI/JAR experiments), `test_organized.png`.
- **Scratch/experiment residue**: root-level `test/`, `palette/`, `complete_getOptimalECC.c`, `getOptimalECC_function.txt`, `JABCode_Color_Modes*README*.md`, `.idea/`, `.vscode/`, the scratch headers listed in 1.2.
- **Binary app**: `android_reader/JabCodeApp.apk`.
- `README.md`: present at the shell-mount clone's HEAD; **absent from this working tree**.

---

## 2. Module / dependency graph

### 2.1 Build units

| Unit | Path | Build file | Artifacts | Links against |
|---|---|---|---|---|
| Core library | `src/jabcode/` | `Makefile` | `build/libjabcode.a`, `build/libjabcode.so` (with `-Wl,-soname,libjabcode.so`) | `-lpng16 -lz` (shared lib link, `Makefile:41`) |
| Core library (Windows) | `src/jabcode/` | `Makefile.win` | `build/libjabcode.dll` | `-L./lib/win64 -ltiff -lpng16 -lz -lm` (`Makefile.win:10`) |
| Writer CLI | `src/jabcodeWriter/` | `Makefile` | `bin/jabcodeWriter` | `-L../jabcode/build -ljabcode -L../jabcode/lib -ltiff -lpng16 -lz -lm` (`Makefile:10`) |
| Reader CLI | `src/jabcodeReader/` | `Makefile` | `bin/jabcodeReader` | same link line as writer (`Makefile:10`) |

Core `CFLAGS = -O2 -std=c11 -fPIC -D_POSIX_C_SOURCE=199309L` (`src/jabcode/Makefile:8`; the feature macro exposes `clock_gettime` for the decode profiler). The Makefile also defines `VENDORED_DIR := ../../lib` (`Makefile:18`) — a repo-root `lib/` consumed by an external panama-wrapper Maven build — but no repo-root `lib/` exists in this working tree (**NOT FOUND**; `refresh-lib`/`check-lib` would fail until it is created).

Dependency edges: `jabcodeWriter → libjabcode`; `jabcodeReader → libjabcode`; `libjabcode → libpng16, zlib` (image I/O), `libtiff` (CMYK save path, tools link). Internally: encoder → {ldpc, interleave, mask, pseudo_random}; detector → {binarizer, transform, sample, decoder}; decoder → {ldpc, interleave, mask (demask), color_calibration (under the undefined `USE_FP_CALIBRATION` gate), lab_color directly under the undefined `USE_LAB_DISTANCE` gate (kdtree_color serves LAB lookup)}; **adaptive_palette has no in-tree caller — dormant library-for-consumers (corrected 2026-07-15; the earlier decoder→adaptive_palette edge was wrong)**; detector_synthetic → decoder internals. Topological drafting order (leaves first): pseudo_random → {interleave, ldpc} → {mask, encoder} and {binarizer, transform, sample} → detector → decoder → image → CLI tools.

### 2.2 Makefile targets (src/jabcode/Makefile, all `.PHONY` at line 24)

| Target | Line | Builds/runs | Notes (from Makefile comments) |
|---|---|---|---|
| `all` | 26 | `build/libjabcode.a` + `build/libjabcode.so` | default |
| `refresh-lib` | 49 | copies both libs to `../../lib/` | "the ONLY sanctioned way to update lib/libjabcode.{so,a}" |
| `check-lib` | 61 | ABI freshness guard | diffs defined-global dynamic symbol sets via `readelf` against vendored `.so`; run by `codec-regression.yml` |
| `bench` | 79 | `build/bench_codec` ← `test/bench_codec.c` | "Suite A: native codec microbenchmark — encode+decode timing across Nc 0..7"; JSON stdout, table stderr |
| `bench-concurrent` | 90 | `build/bench_concurrent` ← `test/bench_concurrent.c` | concurrent vs serialized throughput; `-lpthread`; JSONL stdout |
| `bench-cascade` | 103 | `build/bench_cascade` ← `test/bench_cascade.c` | cascade size N (1..61) × Nc; modes `[curves\|matrix\|both]`; regression guard for high-colour cascade |
| `profile` | 117 | `build/bench_profile` ← `test/bench_profile.c` | per-stage decode profiling harness (DETECT/PALETTE/COLOR_CLASSIFY/DEINTERLEAVE/LDPC/DATA_DECODE + DETECT sub-stages); plotted by `scripts/plot_stage_profile.py`, `scripts/plot_detect_substage.py` |
| `sweep` | 122 | `build/bench_sweep` ← `test/bench_sweep.c` | "Comprehensive capacity/latency/ECC sweep" |
| `transcode` | 127 | `build/transcode_tool` ← `test/transcode_tool.c` | helper for `benchmarks/transcode_survival.py` (that script is outside this repo — **NOT FOUND** here) |
| `test-pn` | 133 | builds+runs `test/test_pn_index.c` | self-contained regression guard for the `pn_index()` FP-UB fix |
| `test-symid` | 139 | builds+runs `test/test_symbology_id.c` | Annex H Table H.1 identifier guard, header-only |
| `test-eci` | 147 | builds+runs `test/test_eci.c` | bit-level ECI decode guard (ISO/IEC 23634 5.3.9 / 7.3), asserts `"\nnnnnn"` output + `]j1` modifier |
| `test-table15` | 153 | builds+runs `test/test_table15.c` | Table 15 / FNC1 / 7.3 backslash-doubling guard |
| `test-roundtrip` | 158 | builds+runs `test/test_text_roundtrip.c` | multi-mode text encode→decode byte-identical |
| `test-cascade-hv` | 166 | builds+runs `test/test_cascade_highversion.c` | high-version cascade + >8207-byte byte-run guard (numeric shift-to-byte token width 5→6 bits) |
| `test-concurrent` | 182 | builds+runs `test/test_concurrent_roundtrip.c` | ThreadSanitizer reentrancy guard; compiles codec sources directly with `-fsanitize=thread` |
| `clean` | 73 | removes libs, objects, bench/test binaries | |

### 2.3 Source-file inventory — `src/jabcode/*.c`/`*.h` (working tree)

Line counts measured from the direct file view. Files also present at the shell-clone's upstream HEAD are marked U; fork additions are marked F.

| File | Lines | Origin | Responsibility (from `@brief` where present) |
|---|---|---|---|
| `encoder.c` | 2453 | U (heavily extended) | "Symbol encoding" — data analysis, ECC/metadata generation, matrix construction, cascade assignment |
| `decoder.c` | 3017 | U (heavily extended) | "Data decoding" — metadata Parts I/II, palette read/synthesis, module classification, data decode |
| `detector.c` | 4242 | U (heavily extended) | "JABCode detector" — finder/alignment pattern search, sampling, top-level `decodeJABCode(Ex)` |
| `detector_synthetic.c` | 431 | F | "Synthetic bitmap decoder (for encoder-generated perfect images)" |
| `binarizer.c` | 752 | U | "Binarize the image" — RGB balance and per-channel thresholding |
| `ldpc.c` | 1562 | U | "LDPC encoder and decoder" — matrix generation, Gauss-Jordan, hard/soft decision |
| `interleave.c` | 77 | U | "Data interleaving" — seeded Fisher-Yates interleave/deinterleave |
| `mask.c` | 455 | U | "Data module masking" — 8 patterns, penalty rules 1-3, mask/demask |
| `pseudo_random.c` | 30 | U (made `_Thread_local`) | LCG64 + tempering PRNG; per-operation reseeding |
| `sample.c` | 192 | U | "Symbol sampling" — grid resample, cross-area sampling |
| `transform.c` | 234 | U | "Matrix transform" — perspective transform math |
| `image.c` | 334 | U (extended) | "Read and save png image" — plus CMYK TIFF and in-memory PNG I/O |
| `decode_profile.c` | 63 | F | "Process-global state + public API for opt-in decode stage profiling" |
| `adaptive_palette.c` | 443 | F | Runtime palette learning for camera colour shift (LAB + k-d tree) |
| `color_calibration.c` | 270 | F | Static + FP-core-derived colour calibration, forward/inverse remap |
| `lab_color.c` | 357 | F | "CIE LAB color space conversion and perceptual distance calculation" |
| `kdtree_color.c` | 177 | F | K-d tree colour nearest-neighbour lookup in LAB space |
| `encoder.h` | 303 | U (extended) | Encoder tables: palettes, AP/FP colours, cascade positions, mode tables, wc/wr |
| `decoder.h` | 83 | U (extended) | Decoder constants, decoding tables, `jab_encode_mode`, function externs |
| `detector.h` | 90 | U | Detection modes, FP/AP struct, perspective-transform struct, externs |
| `ldpc.h` | 31 | U | LDPC seeds and encode/decode externs |
| `pseudo_random.h` | 40 | U (extended) | PRNG API + `pn_index()` range-clamp helper |
| `decode_profile.h` | 139 | F | Profiling stage enums, accumulator struct, timing macros |
| `adaptive_palette.h` | 156 | F | Adaptive palette API and structs |
| `color_calibration.h` | 62 | F | Calibration struct and API |
| `lab_color.h` | 128 | F | `jab_lab_color`/`jab_xyz_color`/`jab_rgb_color` + conversion API |
| `kdtree_color.h` | 59 | F | `kd_node`/`kdtree_color` + build/nearest/free API |
| `symbology_id.h` | 56 | F | Header-only ISO Annex H `]jm` identifier formatter |
| `include/jabcode.h` | 298 | U (extended; 176 lines at upstream HEAD) | Public API header (Section 3) |
| `include/jabcode_wrapper.h` | 21 | F | Wrapper externs (`createEncodeWrapper` etc.); implementation **NOT FOUND** in this tree |

CLI sources: `src/jabcodeWriter/jabwriter.c` (507 lines), `src/jabcodeWriter/jabwriter.h` (empty — 0 content lines), `src/jabcodeReader/jabreader.c` (93 lines).

Test/bench sources (`src/jabcode/test/`, all F): `bench_codec.c` (170), `bench_concurrent.c` (322), `bench_cascade.c` (430), `bench_profile.c` (205), `bench_sweep.c` (160), `transcode_tool.c` (48), `test_pn_index.c` (64), `test_symbology_id.c` (49), `test_eci.c` (77), `test_table15.c` (83), `test_text_roundtrip.c` (53), `test_concurrent_roundtrip.c` (165), `test_cascade_highversion.c` (182), `test_roundtrip_nc0.c` (118), `test_roundtrip_all_nc.c` (109), `test_roundtrip_with_noise.c` (275), `test_mode0_chroma_tolerance.c` (154), `test_mode1_regression.c` (125), `test_lab_color_distance.c` (165), `test_color_calibration.c` (271), `test_multi_frame_decode.c` (197), `test_multi_frame_palette.c` (265), `test_multi_frame_with_noise.c` (246), `test_jab_mobile_with_meta.c` (355), `test_decoder_diagnostic_logging.c` (140); plus `README-bench.md`, `baseline-mode1-output.txt`. Note: several of these (e.g. `test_roundtrip_*`, `test_multi_frame_*`) have **no Makefile target** — they exist as sources (and prebuilt binaries) only. Scripts: `scripts/plot_stage_profile.py`, `scripts/plot_detect_substage.py`, `scripts/ws4_8_threshold_sweep.sh`, `scripts/ws4_9_full_regression.sh`.

---

## 3. Public API inventory (extractive)

Everything in this section is quoted **verbatim** from source. Manual text must quote these, never paraphrase values.

### 3.1 Public constants and macros — `src/jabcode/include/jabcode.h`

| Anchor | Verbatim definition |
|---|---|
| jabcode.h:21 | `#define VERSION "2.0.0"` |
| jabcode.h:22 | `#define BUILD_DATE __DATE__` |
| jabcode.h:24 | `#define MAX_SYMBOL_NUMBER       61` |
| jabcode.h:25 | `#define MAX_COLOR_NUMBER        256` |
| jabcode.h:26 | `#define MAX_SIZE_ENCODING_MODE  256` |
| jabcode.h:27 | `#define JAB_ENCODING_MODES      6` |
| jabcode.h:28 | `#define ENC_MAX                 1000000` |
| jabcode.h:29 | `#define NUMBER_OF_MASK_PATTERNS	8` |
| jabcode.h:31 | `#define DEFAULT_SYMBOL_NUMBER 			1` |
| jabcode.h:32 | `#define DEFAULT_MODULE_SIZE				12` |
| jabcode.h:33 | `#define DEFAULT_COLOR_NUMBER 			8` |
| jabcode.h:34 | `#define DEFAULT_MODULE_COLOR_MODE 		2` |
| jabcode.h:35 | `#define DEFAULT_ECC_LEVEL				3` |
| jabcode.h:36 | `#define DEFAULT_MASKING_REFERENCE 		7` |
| jabcode.h:39 | `#define DISTANCE_TO_BORDER      4` |
| jabcode.h:40 | `#define MAX_ALIGNMENT_NUMBER    9` |
| jabcode.h:41 | `#define COLOR_PALETTE_NUMBER	4` |
| jabcode.h:43 | `#define BITMAP_BITS_PER_PIXEL	32` |
| jabcode.h:44 | `#define BITMAP_BITS_PER_CHANNEL	8` |
| jabcode.h:45 | `#define BITMAP_CHANNEL_COUNT	4` |
| jabcode.h:47 | `#define	JAB_SUCCESS		1` |
| jabcode.h:48 | `#define	JAB_FAILURE		0` |
| jabcode.h:50 | `#define NORMAL_DECODE		0` |
| jabcode.h:51 | `#define COMPATIBLE_DECODE	1` |
| jabcode.h:53 | `#define VERSION2SIZE(x)		(x * 4 + 17)` |
| jabcode.h:54 | `#define SIZE2VERSION(x)		((x - 17) / 4)` |
| jabcode.h:66-67 | `JAB_REPORT_ERROR(x)` / `JAB_REPORT_INFO(x)` printf macros (Android `__android_log_print` variants under `MOBILE_BUILD`, jabcode.h:58-64) |
| jabcode.h:91 | `#define JAB_DIAG_INFO(x) do { if (g_diag_verbose) JAB_REPORT_INFO(x); } while (0)` |

Public globals: `extern unsigned char g_diag_verbose;` (jabcode.h:90), `extern unsigned char g_permissive_color_classification;` (jabcode.h:98), `extern int g_preferred_color_count;` (jabcode.h:105 — comment: "Valid values: 2, 4, 8, 16, 32, 64, 128, 256 (= 2^(Nc+1) for Nc=0..7). 0 = auto (default)").

Public typedefs (jabcode.h:70, 106-115): `jab_byte` (unsigned char), `jab_char` (char), `jab_boolean` (unsigned char), `jab_int32` (int), `jab_uint32` (unsigned int), `jab_int16` (short), `jab_uint16` (unsigned short), `jab_int64` (long long), `jab_uint64` (unsigned long long), `jab_float` (float), `jab_double` (double).

### 3.2 Public structs — verbatim, `src/jabcode/include/jabcode.h`

`jab_vector2d` (jabcode.h:120-123):

```c
typedef struct {
	jab_int32	x;
	jab_int32	y;
}jab_vector2d;
```

`jab_point` (jabcode.h:128-131):

```c
typedef struct {
	jab_float	x;
	jab_float	y;
}jab_point;
```

`jab_data` (jabcode.h:136-139):

```c
typedef struct {
	jab_int32	length;
	jab_char	data[];
}jab_data;
```

`jab_bitmap` (jabcode.h:144-151):

```c
typedef struct {
   jab_int32	width;
   jab_int32	height;
   jab_int32	bits_per_pixel;
   jab_int32	bits_per_channel;
   jab_int32	channel_count;
   jab_byte		pixel[];
}jab_bitmap;
```

`jab_symbol` (jabcode.h:156-167):

```c
typedef struct {
	jab_int32		index;
	jab_vector2d	side_size;
	jab_int32		host;
	jab_int32		slaves[4];
	jab_int32 		wcwr[2];
	jab_int32		Pg;					///< Gross payload length (ecc_encoded_data->length)
	jab_data*		data;
	jab_byte*		data_map;
	jab_data*		metadata;
	jab_byte*		matrix;
}jab_symbol;
```

`jab_encode` (jabcode.h:172-185):

```c
typedef struct {
	jab_int32		color_number;
	jab_int32		symbol_number;
	jab_int32		module_size;
	jab_int32		master_symbol_width;
	jab_int32		master_symbol_height;
	jab_byte*		palette;				///< Palette holding used module colors in format RGB
	jab_vector2d*	symbol_versions;
	jab_byte* 		symbol_ecc_levels;
	jab_int32*		symbol_positions;
	jab_symbol*		symbols;				///< Pointer to internal representation of JAB Code symbols
	jab_bitmap*		bitmap;
	jab_int32		mask_type;				///< Mask pattern type used (0-7)
}jab_encode;
```

`jab_metadata` (jabcode.h:190-198):

```c
typedef struct {
	jab_boolean default_mode;
	jab_byte Nc;
	jab_byte mask_type;
	jab_byte docked_position;
	jab_vector2d side_version;
	jab_vector2d ecl;
	jab_int32 Pg;				///< Gross payload length (for synthetic decoder)
}jab_metadata;
```

`jab_decoded_symbol` (jabcode.h:203-214):

```c
typedef struct {
	jab_int32 index;
	jab_int32 host_index;
	jab_int32 host_position;
	jab_vector2d side_size;
	jab_float module_size;
	jab_point pattern_positions[4];
	jab_metadata metadata;
	jab_metadata slave_metadata[4];
	jab_byte* palette;
	jab_data* data;
}jab_decoded_symbol;
```

### 3.3 Public functions — exact signatures, `src/jabcode/include/jabcode.h`

| Anchor | Verbatim signature |
|---|---|
| jabcode.h:217 | `extern jab_encode* createEncode(jab_int32 color_number, jab_int32 symbol_number);` |
| jabcode.h:218 | `extern void destroyEncode(jab_encode* enc);` |
| jabcode.h:219 | `extern jab_int32 generateJABCode(jab_encode* enc, jab_data* data);` |
| jabcode.h:220 | `extern jab_data* decodeJABCode(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status);` |
| jabcode.h:221 | `extern jab_data* decodeJABCodeEx(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status, jab_decoded_symbol* symbols, jab_int32 max_symbol_number);` |
| jabcode.h:222 | `extern jab_data* decodeJABCodeSynthetic(jab_bitmap* bitmap, jab_int32 color_number, jab_int32 ecc_level, jab_int32 module_size, jab_int32 symbol_width, jab_int32 symbol_height, jab_int32 mask_type, jab_byte* encoder_data_map, jab_int32* encoder_wcwr, jab_int32 encoder_Pg, jab_int32 mode, jab_int32* status);` |
| jabcode.h:231 | `extern jab_char* jabGetSymbologyIdentifier(void);` |
| jabcode.h:236 | `extern void resetDecoderState(void);` (documented ABI-compat no-op) |
| jabcode.h:245 | `extern void jabSetStrictPartIIRequired(jab_boolean strict);` |
| jabcode.h:255 | `extern void jabSetDiagVerbose(jab_boolean verbose);` |
| jabcode.h:256 | `extern jab_boolean jabIsDiagVerbose(void);` |
| jabcode.h:261 | `extern void jabSetPermissiveColorClassification(jab_boolean permissive);` |
| jabcode.h:262 | `extern jab_boolean jabIsPermissiveColorClassification(void);` |
| jabcode.h:270 | `extern void jabSetPreferredColorCount(jab_int32 count);` |
| jabcode.h:271 | `extern jab_int32 jabGetPreferredColorCount(void);` |
| jabcode.h:282 | `extern void jabSetProfileStages(jab_boolean profile);` |
| jabcode.h:283 | `extern jab_boolean jabIsProfileStages(void);` |
| jabcode.h:284 | `extern const struct jab_decode_profile* jabGetDecodeProfile(void);` |
| jabcode.h:285 | `extern void jabResetDecodeProfile(void);` |
| jabcode.h:287 | `extern jab_boolean saveImage(jab_bitmap* bitmap, jab_char* filename);` |
| jabcode.h:288 | `extern jab_boolean saveImageCMYK(jab_bitmap* bitmap, jab_boolean isCMYK, jab_char* filename);` |
| jabcode.h:289 | `extern jab_bitmap* readImage(jab_char* filename);` |
| jabcode.h:290 | `extern jab_byte* saveImageToMemory(jab_bitmap* bitmap, jab_int32* out_length);` |
| jabcode.h:291 | `extern jab_bitmap* readImageFromMemory(jab_byte* buffer, jab_int32 length);` |
| jabcode.h:292 | `extern void reportError(jab_char* message);` |

Auxiliary fork headers (public to library consumers that include them): `include/jabcode_wrapper.h:10-15` (`createEncodeWrapper`, `destroyEncodeWrapper`, `generateJABCodeWrapper`, `decodeJABCodeWrapper`, `decodeJABCodeExWrapper`, `saveImageWrapper` — implementations **NOT FOUND** in this tree); `color_calibration.h:12-60` (`jabLoadCalibrationFromJSON`, `jabApplyCalibration`, `jabRemapColor`, `jabClearCalibration`, `jabHasCalibration`, `jabCalibrateFromObservedRGB`, `jabRemapColorInverse`, `jabBuildCalibrationFromFPCores`); `adaptive_palette.h:73-154` (`adaptive_palette_init/add_sample/learn_transform/apply_transform/match/match_with_confidence/reset/free/get_expected/get_adapted`); `decode_profile.h:55-133` (`jab_decode_stage`, `jab_detect_substage`, `jab_decode_profile`, `JAB_PROF_BEGIN/END/DET_END`); `symbology_id.h:26-54` (`JAB_FNC1_NONE/PRECEDING/FOLLOWING`, `jab_symbology_modifier`, `jab_format_symbology_identifier`); `lab_color.h:26-55` (`jab_lab_color`, `jab_xyz_color`, `jab_rgb_color`, `rgb_to_lab`, …); `kdtree_color.h:17-57` (`kd_node`, `kdtree_color`, `kdtree_build`, `kdtree_nearest`, `kdtree_free`).

### 3.4 CLI surface — `jabcodeWriter` (`src/jabcodeWriter/jabwriter.c`)

Usage (jabwriter.c:30): `jabcodeWriter --input message-to-encode --output output-image [options]`

| Flag | Anchor (parse / validate) | Values and default (verbatim from source) |
|---|---|---|
| `--input` | jabwriter.c:71 | "Input data (message to be encoded)." Required unless `--input-file` given (checked at :250). |
| `--input-file` | jabwriter.c:89 | "Input data file." Read binary, whole file. |
| `--output` | jabwriter.c:123 | "Output image file." Required (:260). |
| `--color-number` | jabwriter.c:132, 149-155 | Valid: `2, 4, 8, 16, 32, 64, 128, 256`; usage text: "(2,4,8,16,32,64,128,256,default:8)". Comment at :147-148: "WS-0: Accept color_number=2 (Nc=0, Mode 0 monochrome). WS-3: Accept color_number=256 (Nc=7, max-density mode)." |
| `--module-size` | jabwriter.c:157, 167 | "Module size in pixel (default:12 pixels)."; rejects `< 0`. |
| `--symbol-width` | jabwriter.c:173 | "Master symbol width in pixel."; rejects `< 0`. |
| `--symbol-height` | jabwriter.c:189 | "Master symbol height in pixel."; rejects `< 0`. |
| `--symbol-number` | jabwriter.c:205, 220-224 | "Number of symbols (1-61, default:1)."; error "Invalid symbol number (must be 1 - 61)." |
| `--ecc-level` | jabwriter.c:273, 303-307 | "Error correction levels (1-10, default:3(6%))."; per-symbol list; "level 0 means using the default level, for slaves, it means using the same level as its host."; rejects `< 0 || > 10`. |
| `--symbol-version` | jabwriter.c:311, 350-354 | "Side-Version of each symbol ... (x0 y0 x1 y1 x2 y2...)."; rejects outside `1 - 32`. Required for every symbol when `symbol_number > 1` (:410). |
| `--symbol-position` | jabwriter.c:358, 386-390 | "Symbol positions (0-60) ... Only required for multi-symbol code."; master must be position `0` (:397-403); required complete when `symbol_number > 1` (:405). |
| `--color-space` | jabwriter.c:226, 241-245 | "Color space of output image (0:RGB,1:CMYK,default:0). RGB image is saved as PNG and CMYK image as TIFF."; rejects values other than `0`/`1`. |
| `--help` | jabwriter.c:435 | prints usage. |

Exit status (jabwriter.c:431): "0: success | 1: failure".

### 3.5 CLI surface — `jabcodeReader` (`src/jabcodeReader/jabreader.c`)

Usage (jabreader.c:14): `jabcodeReader input-image(png) [--output output-file]`

- Positional `input-image(png)` — loaded via `readImage` (jabreader.c:47).
- `--output` (jabreader.c:16, 36): "Output file for decoded data."; otherwise decoded bytes print to stdout.
- `--help` (jabreader.c:17).
- Return semantics (jabreader.c:23): "0: success | 255: not detectable | other non-zero: decoding failed"; on failed decode with `decode_status > 0` it returns `(jab_int32)(symbols[0].module_size + 0.5f)` (jabreader.c:60); `decode_status == 2` warns "The code is only partly decoded..." (jabreader.c:66-69). Decoding uses `decodeJABCodeEx(bitmap, NORMAL_DECODE, ...)` with `MAX_SYMBOL_NUMBER` slots (jabreader.c:53-54).

### 3.6 Interop-critical internal constants (quoted exactly)

| Constant | Anchor | Verbatim value |
|---|---|---|
| LDPC metadata seed | ldpc.h:17 | `#define LPDC_METADATA_SEED 	38545` — note the source spells the macro `LPDC_` (transposed), not `LDPC_` |
| LDPC message seed | ldpc.h:18 | `#define LPDC_MESSAGE_SEED 	785465` — same transposed spelling |
| Interleave seed | interleave.c:20 | `#define INTERLEAVE_SEED 226759` |
| Mask pattern count | jabcode.h:29 | `#define NUMBER_OF_MASK_PATTERNS	8` |
| Mask penalty weights | mask.c:22-24 | `#define W1	100` / `#define W2	3` / `#define W3	3` |
| Palette count in master | jabcode.h:41 | `#define COLOR_PALETTE_NUMBER	4` |
| Default palette | encoder.h:26-34 | `jab_default_palette[] = {0,0,0, 0,0,255, 0,255,0, 0,255,255, 255,0,0, 255,0,255, 255,255,0, 255,255,255}` — order comment: "[K,B,G,C,R,M,Y,W] = ISO/IEC 23634 Table 21 (the Fraunhofer reference)" |
| ECC (wc, wr) table | encoder.h:234 | `ecclevel2wcwr[10][2] = {{3, 8}, {3, 7}, {4, 9}, {3, 6}, {4, 7}, {4, 6}, {3, 4}, {4, 5}, {5, 6}, {6, 7}}` — "Per ISO/IEC 23634:2022 Table 20. ECC levels run 1..10 (default 3)" (encoder.h:230) |
| Code rates | encoder.h:226 | `ecclevel2coderate[11] = {0.55f, 0.63f, 0.57f, 0.55f, 0.50f, 0.43f, 0.34f, 0.25f, 0.20f, 0.17f, 0.14f}` |
| Nc colour encoding (metadata Part I) | encoder.h:124 | `nc_color_encode_table[8][2] = {{0,0}, {0,3}, {0,6}, {3,0}, {3,3}, {3,6}, {6,0}, {6,3}}` |
| FP core colours | encoder.h:50-53 | `FP0_CORE_COLOR 0`, `FP1_CORE_COLOR 0`, `FP2_CORE_COLOR 6`, `FP3_CORE_COLOR 3` |
| AP core colours | encoder.h:58-62 | `AP0..AP3_CORE_COLOR 3`, `APX_CORE_COLOR 6` |
| Per-mode FP/AP colour indices | encoder.h:67-75 | e.g. `fp2_core_color_index[] = {0, 2, FP2_CORE_COLOR, 14, 30, 60, 124, 252}` |
| Cascade decode order | encoder.h:111-119 | `jab_symbol_pos[MAX_SYMBOL_NUMBER]` — 61 `(x,y)` docking offsets |
| AP positions per side-version | encoder.h:249-281 | `jab_ap_pos[32][9]` (first row `{4, 18, 0, ...}`) |
| AP count per side-version | encoder.h:285-292 | `jab_ap_num[32] = {2,2,2,2,2, 3,3,3,3, 4,4,4,4, 5,5,5,5, 6,6,6,6, 7,7,7,7, 8,8,8,8, 9,9,9}` |
| Metadata geometry | decoder.h:20-25 | `MASTER_METADATA_X 6`, `MASTER_METADATA_Y 1`, `MASTER_METADATA_PART1_LENGTH 6`, `MASTER_METADATA_PART2_LENGTH 38`, `MASTER_METADATA_PART1_MODULE_NUMBER 4` |
| Decode error codes | decoder.h:17-18 | `DECODE_METADATA_FAILED -1`, `FATAL_ERROR -2` |
| Slave palette positions | decoder.h:36-45 | `slave_palette_position[64]` boustrophedon over `x in [4,11], y in [5,12]` (extended 32→64 for high Nc) |
| Character sizes per mode | encoder.h:207 | `character_size[7]={5,5,4,4,5,6,8}` |
| Mode order comment | encoder.h:202-203 | "1.upper, 2.lower, 3.numeric, 4.punct, 5.mixed, 6.alphanumeric, 7.byte" |
| Detection limits | detector.h:23-28 | `MAX_MODULES 145` ("the number of modules in side-version 32"), `MAX_FINDER_PATTERNS 500`, `CROSS_AREA_WIDTH 14` |
| PRNG core | pseudo_random.c:10, 23 | `static _Thread_local uint64_t lcg64_seed = 42;`; `lcg64_seed = 6364136223846793005ULL * lcg64_seed + 1;` with temper constants `0x9D2C5680`, `0xEFC60000` (pseudo_random.c:15-16) |

Cross-check against the ISO clause map (section 4): interleave seed 226759, LDPC message seed 785465, metadata seed 38545, and W1=100/W2=3/W3=3 all match the normative values recorded there.

---

## 4. Concept graph

Nodes with one-line definitions, prerequisite edges, code anchor, and ISO/IEC 23634:2022 clause per the project clause map (`JABCodeCOA-crypto/docs/manuals/plan/03-iso-23634-reference-map.md`; only Annexes C and H are normative among annexes).

| Concept | Definition | Prerequisites | Code anchor | ISO clause |
|---|---|---|---|---|
| Polychrome matrix symbology | Colour 2D code whose modules carry `log2(colour count)` bits each | — | jabcode.h:24-45 | 4.1-4.2 |
| Module | Smallest square cell; rendered at `module_size` pixels (default 12) | polychrome symbology | jabcode.h:32; encoder.h:98 | 4.3 |
| Colour palette | Ordered RGB set defining module colours; default 8-colour `[K,B,G,C,R,M,Y,W]`; 4 palette copies placed in a master symbol | module | encoder.h:26-34; jabcode.h:41; encoder.c:29 (`genColorPalette`), encoder.c:95 (`setDefaultPalette`) | 4.3, Tables 3-4, Annex G |
| Nc / colour mode | 3-bit metadata field selecting colour count = `2^(Nc+1)`; only 4- and 8-colour modes are standard-defined, others reserved/user-defined | colour palette, metadata Part I | jabcode.h:100-105; encoder.h:124 | 4.4, Annex G (map: "Only mode 1 (4-colour) and mode 2 (8-colour, default) are defined"; 2-colour is purely a reference-implementation extension) |
| Primary (master) symbol | Symbol with four finder patterns at corners; hosts metadata | module, finder pattern | detector.c:1811 (`findMasterSymbol`; doc block at 1804), detector.c:3682 (`detectMaster`) | 4.3 |
| Secondary (slave) symbol | Finder-pattern-free symbol docked to a host; inherits parameters via metadata | primary symbol, cascading | detector.c:2759 ("Find a docked slave symbol"); decoder.c:2377 (`decodeSlave`) | 4.3, 4.5 |
| Side-version | Per-axis size index 1-32; side length in modules = `version * 4 + 17` (21-145) | module | jabcode.h:53-54; encoder.c:1881 (`setMasterSymbolVersion`) | 4.3 |
| Finder pattern (FP0-FP3) | Four corner locator patterns with per-mode core colours (UL/UR/LR/LL) | colour palette | encoder.h:50-53, 67-70; detector.c:189-1105 | 4.3 |
| Alignment pattern (AP, APX) | Grid-correction patterns at `jab_ap_pos` crossings for larger versions | finder pattern, side-version | encoder.h:58-62, 249-292; detector.c:2161-2615 | 4.3 |
| Metadata Part I | Nc field, colour-pair encoded (black/cyan/yellow bootstrap so Nc is decodable in any mode) | finder pattern | decoder.h:25; decoder.c:1262 (`decodeMasterMetadataPartI`); encoder.c:925 (`encodeMasterMetadata`) | 4.4, Table 7 |
| Metadata Part II | V (side-version), E (ECC wc/wr), MSK (mask reference); 38 encoded modules-bits | Part I | decoder.h:24; decoder.c:1470 (`decodeMasterMetadataPartII`); encoder.c:1008/1053 (update/place PartII) | 4.4 |
| Metadata Part III | S (slave/docking flags) for cascaded symbols | Part II, cascading | encoder.c (slave metadata in `assignDockedSymbols`, encoder.c:1598); decoder.c:1161 (`decodeSlaveMetadata`) | 4.4 |
| Encoding modes | Upper/Lower/Numeric/Punct/Mixed/Alphanumeric/Byte (+ ECI, FNC1) with latch/shift switching | — | decoder.h:61-72 (`jab_encode_mode`); encoder.h:129 (`jab_enconing_table`), 186 (`latch_shift_to`), 213 (`mode_switch`); encoder.c:723 (`encodeData`); decoder.c:2538 (`decodeData`) | 5.1-5.3, Annex E |
| LDPC error correction | Seeded-random LDPC over the bit stream; 10 levels via `(wc, wr)`; hard- and soft-decision decoders | encoding modes, PRNG | ldpc.h:17-28; ldpc.c:645 (`encodeLDPC`), 906 (`decodeLDPChd`), 1376 (soft `decodeLDPC` — no in-tree callers; both metadata and message data hard-decode, verified 2026-07-15); encoder.h:234 | 5.4, Table 20, Annexes B, C (normative) |
| Interleaving | Fixed-seed Fisher-Yates permutation of the ECC-coded bit stream | LDPC, PRNG | interleave.c:20-77; pseudo_random.h:32 (`pn_index`) | 5.5, Annex F |
| Masking | XOR-style pattern 0-7 selection minimizing penalty score (rules 1-3 weighted W1/W2/W3) | data placement | mask.c:22-24, 363 (`maskCode`); jabcode.h:36 | 5.8, Tables 22-23 |
| Data placement / data map | Module-by-module layout skipping FP/AP/metadata/palette modules | metadata, masking | encoder.c:1171 (`createMatrix`); jab_symbol.data_map (jabcode.h:164) | 5.6-5.7 |
| Cascading | Docking up to 61 symbols (1 primary + 60 secondary) in fixed position/decode order; docked sides share side-version | primary/secondary, metadata Part III | encoder.h:111-119 (`jab_symbol_pos`); encoder.c:1598 (`assignDockedSymbols`); jabwriter.c:358 (`--symbol-position`) | 4.5, Figures 11-15 |
| Detection pipeline | RGB balance → binarize → finder search → (AP search) → perspective transform → sample → decode | binarization, FP/AP | detector.c:4065/4238 (`decodeJABCodeEx`/`decodeJABCode`); detector.h:35-40 (`QUICK_DETECT/NORMAL_DETECT/INTENSIVE_DETECT`) | 6 |
| Binarization | Per-channel histogram/threshold binarization of the captured image | — | binarizer.c:106, 184, 408; detector.h:70-74 | 6 |
| Sampling / perspective transform | Homography from FP/AP centers, then grid resampling of module centers | detection | transform.c:202 (`getPerspectiveTransform`); sample.c:31 (`sampleSymbol`); detector.c:3296 (`sampleSymbolByAlignmentPattern`) | 6 |
| Palette reconstruction & colour classification | Reads embedded palette modules, then classifies each data module to a palette index | sampling, colour palette | decoder.h:80-81 (`readColorPaletteInMaster/Slave`); decoder.c:2072 (`decodeMaster`) | 6 |
| Quality grading | ISO/IEC 15415-based grading plus colour parameters (CPA, CVDM) | — | **NOT FOUND** in this codebase (no verifier/grading implementation) | 8 |
| Symbology identifier | `]jm` transmission preamble, modifier per Table H.1 | decode result | symbology_id.h:36-54; jabcode.h:231 | 7, Annex H (normative) |
| ECI / FNC1 | Extended Channel Interpretation escapes and GS1 FNC1 flagging in transmitted data | encoding modes | decoder.h:70-71; test/test_eci.c, test/test_table15.c | 5.3.9, 7, Table 15 |
| Mode 0 (2-colour) extension | Fork-specific monochrome mode (Nc=0), K/W palette synthesis, custom AP handling | Nc, palette reconstruction | jabwriter.c:147-148; decoder.c:1283, 2206, 2404 | none — reference-implementation extension (clause map: "A 2-colour mode does not exist anywhere in the standard") |
| Adaptive palette / calibration (fork) | Camera-shift compensation: LAB k-d tree matching, FP-core-derived calibration | palette reconstruction | adaptive_palette.h:53-64; color_calibration.h:36-60 | none (implementation aid) |
| Decode profiling (fork) | Opt-in per-stage `CLOCK_MONOTONIC` accumulators over the pipeline stages | detection pipeline | decode_profile.h:55-88; jabcode.h:282-285 | none (tooling) |
| Synthetic decode path (fork) | Decoder for perfect encoder-output bitmaps, bypassing camera detection | detection pipeline | detector_synthetic.c:128 | none (testing aid) |

Ordering consequence for manuals (leaves first): module → palette → Nc → side-version → FP/AP → symbol anatomy → encoding modes → LDPC → interleave → placement → masking → metadata → cascading → detection → sampling → classification → data decode → transmitted data/identifier.

---

## 5. Glossary

| Term | One-line definition | Anchor |
|---|---|---|
| `Nc` | Colour-mode index (3 bits, metadata Part I); colour count = `2^(Nc+1)`, Nc = 0..7 | jabcode.h:100-105; ISO clauses 1-3, 4.4 |
| `wc` | LDPC column weight (checks per bit) for the chosen ECC level | encoder.h:229-234 |
| `wr` | LDPC row weight for the chosen ECC level; `(wc, wr)` pairs per Table 20 | encoder.h:229-234 |
| `Pn` | Net payload length (spec math symbol; clause 1-3) — appears in code only via capacity math, `getSymbolCapacity` (encoder.c:651) | ISO clauses 1-3 |
| `Pg` | Gross payload length — "Gross payload length (ecc_encoded_data->length)" | jabcode.h:162, 197 |
| `Pe` | Metadata ECC-expanded length (spec symbol; 26→44 bits for primary metadata) | ISO 4.4 (map) — **NOT FOUND** as a code identifier |
| `K`, `H` | LDPC message length and parity-check matrix (spec symbols; Annexes B/C); code equivalents inside `ldpc.c` matrix generation (ldpc.c:166, 425, 470) | ISO 5.4, Annex B/C |
| Side-version | Per-axis symbol size index 1-32; side = `4*v + 17` modules | jabcode.h:53 |
| Module | One coloured square cell of the symbol grid | jabcode.h:32 |
| Master / primary symbol | The one symbol with finder patterns; decode entry point | detector.c:3682 |
| Slave / secondary symbol | Docked symbol without finder patterns | decoder.c:2377 |
| Host / docked position | The symbol a slave docks to; `docked_position` records which of 4 sides | jabcode.h:159, 194 |
| Cascading | Multi-symbol composition, up to `MAX_SYMBOL_NUMBER 61` | jabcode.h:24; encoder.h:111 |
| FP0-FP3 | The four finder-pattern types (corners UL, UR, LR, LL) | encoder.h:80-83 |
| AP0-AP3, APX | Alignment-pattern types; APX = inner grid crossings | encoder.h:88-92 |
| MSK / mask reference | Chosen mask pattern id 0-7 (default reference 7) | jabcode.h:36, 184 |
| `ecl` | Error-correction level as an `(x, y)` = `(wc, wr)` vector in decoded metadata | jabcode.h:196 |
| ECI | Extended Channel Interpretation — charset/interpretation escape, transmitted as `"\nnnnnn"` | decoder.h:70; Makefile:143-149 |
| FNC1 | GS1 function character; position drives symbology-identifier modifier | symbology_id.h:26-30 |
| Symbology identifier | `]jm` preamble per ISO/IEC 15424 / Annex H Table H.1 | symbology_id.h:2-16 |
| LDPC | Low-density parity-check code used for both data and metadata ECC | ldpc.c:10-11 |
| LCG | 64-bit linear congruential generator + tempering; drives interleave and LDPC matrix permutations | pseudo_random.c:21-25 |
| `pn_index` | Clamped PRNG-draw-to-range mapping preserving reference wire compatibility | pseudo_random.h:32-38 |
| Data map | Per-module occupancy map marking non-data modules during placement/decode | jabcode.h:164 |
| Palette placement index | Order in which palette colours are written into master/slave palette modules | encoder.h:39-45 |
| `NORMAL_DECODE` / `COMPATIBLE_DECODE` | Decode modes 0/1; compatible mode tolerates partly decoded cascades (status 2) | jabcode.h:50-51; jabreader.c:66 |
| Mode 0 | Fork extension: 2-colour (K/W) monochrome mode at Nc=0 | jabwriter.c:147 |
| `g_diag_verbose` | Process-global gate for high-volume diagnostic markers (`JAB_DIAG_INFO`) | jabcode.h:90-91 |
| Preferred colour count | Pin the decoder to one Nc instead of the 8-step fallback ladder | jabcode.h:100-105 |
| CPA / CVDM | Colour Palette Accuracy / Colour Variation in Data Modules — clause 8 grading parameters | clause map only; **NOT FOUND** in code |

---

## 6. Source-anchor index

Compact citation table for manual writers. Format: item → `file:line` (paths relative to repo root; `jabcode.h` = `src/jabcode/include/jabcode.h`, all others in `src/jabcode/` unless noted).

| Item | Anchor |
|---|---|
| `VERSION "2.0.0"` | jabcode.h:21 |
| `MAX_SYMBOL_NUMBER 61` | jabcode.h:24 |
| `NUMBER_OF_MASK_PATTERNS 8` | jabcode.h:29 |
| Defaults (symbol/module/colour/ECC/mask) | jabcode.h:31-36 |
| `COLOR_PALETTE_NUMBER 4` | jabcode.h:41 |
| `JAB_SUCCESS` / `JAB_FAILURE` | jabcode.h:47-48 |
| `NORMAL_DECODE` / `COMPATIBLE_DECODE` | jabcode.h:50-51 |
| `VERSION2SIZE` / `SIZE2VERSION` | jabcode.h:53-54 |
| `g_diag_verbose`, `JAB_DIAG_INFO` | jabcode.h:90-91 |
| `g_permissive_color_classification` | jabcode.h:98 |
| `g_preferred_color_count` | jabcode.h:105 |
| `jab_data`, `jab_bitmap` | jabcode.h:136-139, 144-151 |
| `jab_symbol`, `jab_encode` | jabcode.h:156-167, 172-185 |
| `jab_metadata`, `jab_decoded_symbol` | jabcode.h:190-198, 203-214 |
| Core API externs | jabcode.h:217-292 |
| `createEncode` / `destroyEncode` impl | encoder.c:182 / encoder.c:261 |
| `generateJABCode` impl | encoder.c:2307 |
| `decodeJABCode` / `decodeJABCodeEx` impl | detector.c:4238 / detector.c:4065 |
| `decodeJABCodeSynthetic` impl | detector_synthetic.c:128 |
| `genColorPalette` / `setDefaultPalette` | encoder.c:29 / encoder.c:95 |
| `getSymbolCapacity` / `getOptimalECC` | encoder.c:651 / encoder.c:698 |
| `encodeData` | encoder.c:723 |
| `encodeMasterMetadata` / `updateMasterMetadataPartII` / `placeMasterMetadataPartII` | encoder.c:925 / 1008 / 1053 |
| `createMatrix` | encoder.c:1171 |
| `assignDockedSymbols` / `swap_symbols` | encoder.c:1598 / 1580 |
| `setMasterSymbolVersion` | encoder.c:1881 |
| `decodeMaster` / `decodeSlave` | decoder.c:2072 / decoder.c:2377 |
| `decodeMasterMetadataPartI` / `PartII` | decoder.c:1262 / decoder.c:1470 |
| `decodeSlaveMetadata` | decoder.c:1161 |
| `decodeData` | decoder.c:2538 |
| Metadata geometry constants | decoder.h:20-25 |
| Decoding tables (upper/lower/numeric/punct/mixed/alphanumeric) | decoder.h:50-56 |
| `jab_encode_mode` enum | decoder.h:61-72 |
| `slave_palette_position[64]` | decoder.h:36-45 |
| Default palette `[K,B,G,C,R,M,Y,W]` | encoder.h:26-34 |
| Palette placement indices | encoder.h:39-45 |
| FP/AP core colours (all modes) | encoder.h:50-75 |
| `jab_code` struct | encoder.h:97-106 |
| `jab_symbol_pos[61]` cascade order | encoder.h:111-119 |
| `nc_color_encode_table` | encoder.h:124 |
| `jab_enconing_table` (sic — source spelling) | encoder.h:129 |
| `latch_shift_to`, `mode_switch`, `character_size` | encoder.h:186, 213, 207 |
| `ecclevel2coderate`, `ecclevel2wcwr`, `wcwr_for_level` | encoder.h:226, 234, 241 |
| `jab_ap_pos`, `jab_ap_num` | encoder.h:249-281, 285-292 |
| `LPDC_METADATA_SEED 38545` / `LPDC_MESSAGE_SEED 785465` | ldpc.h:17-18 |
| `encodeLDPC` / `decodeLDPChd` / `decodeLDPC` externs | ldpc.h:26-28 |
| LDPC matrix gen (`createMatrixA`) / Gauss-Jordan / hard / soft decode | ldpc.c:172, 235, 906, 1376 (soft path caller-less) |
| `INTERLEAVE_SEED 226759`; `interleaveData` / `deinterleaveData` | interleave.c:20, 26, 42 |
| Mask weights `W1 100`, `W2 3`, `W3 3`; `maskCode` | mask.c:22-24, 363 |
| LCG multiplier/increment, temper constants, `_Thread_local` seed | pseudo_random.c:23, 15-16, 10 |
| `pn_index` clamp | pseudo_random.h:32-38 |
| Detection modes enum; FP/AP struct; perspective struct | detector.h:35-40, 45-51, 56-66 |
| `MAX_MODULES 145`, `MAX_FINDER_PATTERNS 500`, `CROSS_AREA_WIDTH 14` | detector.h:23-28 |
| `detectMaster`, `sampleSymbolByAlignmentPattern` | detector.c:3682, 3296 |
| Binarizer variants (named, pipeline-dormant) / live D1 entries `balanceRGB`, `binarizerRGB` | binarizer.c:106, 184, 408 / 485, 602 |
| `getPerspectiveTransform` / `sampleSymbol` | transform.c:202 / sample.c:31 |
| Symbology-identifier formatter + Table H.1 mapping | symbology_id.h:36-54 |
| Decode-profile stages/struct/macros | decode_profile.h:55-88, 110-133 |
| Calibration API (incl. FP-core builder) | color_calibration.h:12-60 |
| Adaptive palette state/API | adaptive_palette.h:53-64, 73-154 |
| LAB/RGB/XYZ colour types | lab_color.h:26-48 |
| K-d tree types/API | kdtree_color.h:17-57 |
| Wrapper externs (impl NOT FOUND) | include/jabcode_wrapper.h:10-15 |
| Writer CLI parse loop | src/jabcodeWriter/jabwriter.c:66-416 |
| Writer usage text | src/jabcodeWriter/jabwriter.c:25-60 |
| Reader CLI + exit codes | src/jabcodeReader/jabreader.c:9-93 |
| Core Makefile targets | src/jabcode/Makefile:24-186 |
| Windows DLL build | src/jabcode/Makefile.win:5-13 |
| Writer/reader link lines | src/jabcodeWriter/Makefile:10; src/jabcodeReader/Makefile:10 |
| LGPL 2.1 license (working tree) | LICENSE:1-504 |
| ISO/IEC 23634 clause map (external) | `JABCodeCOA-crypto/docs/manuals/plan/03-iso-23634-reference-map.md` |

### NOT FOUND / open items

- Symbol quality grading (ISO clause 8, CPA/CVDM): no implementation anywhere in `src/`.
- `Pe` and explicit `Pn` as code identifiers: spec symbols only.
- Repo-root `lib/` (`VENDORED_DIR` of `refresh-lib`/`check-lib`): directory absent from this working tree.
- `src/jabcode/lib/` prebuilt libpng/tiff/zlib archives and their license text files: referenced by Makefiles, absent from this working tree.
- Implementations of the six `*Wrapper` functions declared in `include/jabcode_wrapper.h`.
- `README.md` at repo root: absent from this working tree (exists at the shell-clone HEAD).
- `benchmarks/transcode_survival.py` (referenced by the `transcode` target): not in this repository.
- A `git status` for this working tree: not obtainable (session shell holds a different clone; see 1.2).
