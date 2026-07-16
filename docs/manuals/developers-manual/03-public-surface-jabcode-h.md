# 3. `include/jabcode.h` — the public surface

<!-- objective: A maintainer can enumerate the complete public contract — every macro, typedef, struct, global and function — and state each function's ownership and threading contract, including the generateJABCode 0-on-success inversion of JAB_SUCCESS. -->

**Responsibility.** `src/jabcode/include/jabcode.h` (298 lines) is the single public header of libjabcode: link-time contract for the CLI tools, the Panama/jextract bindings, and every other consumer. Everything below is quoted from the header and cross-checked against the defining translation units. The header is C++-safe (`extern "C"` guard). <!-- anchor: jabcode.h:14-19, 294-298 -->

Consumer-level API usage is Operator's Manual territory ([../operators-manual/09-embedding-the-c-api.md](../operators-manual/09-embedding-the-c-api.md), JC-U ch. 9); this chapter is the normative fact table.

## 3.1 Macros and constants

| Item | Verbatim definition | Notes |
|---|---|---|
| `VERSION` | `#define VERSION "2.0.0"` | <!-- anchor: jabcode.h:21 --> |
| `BUILD_DATE` | `#define BUILD_DATE __DATE__` | compile-date of the including TU, not of the library <!-- anchor: jabcode.h:22 --> |
| `MAX_SYMBOL_NUMBER` | `#define MAX_SYMBOL_NUMBER       61` | cascade ceiling; sizes decode buffers <!-- anchor: jabcode.h:24 --> |
| `MAX_COLOR_NUMBER` | `#define MAX_COLOR_NUMBER        256` | <!-- anchor: jabcode.h:25 --> |
| `MAX_SIZE_ENCODING_MODE` | `#define MAX_SIZE_ENCODING_MODE  256` | <!-- anchor: jabcode.h:26 --> |
| `JAB_ENCODING_MODES` | `#define JAB_ENCODING_MODES      6` | <!-- anchor: jabcode.h:27 --> |
| `ENC_MAX` | `#define ENC_MAX                 1000000` | sentinel in the mode-analysis DP <!-- anchor: jabcode.h:28; encoder.c:290 --> |
| `NUMBER_OF_MASK_PATTERNS` | `#define NUMBER_OF_MASK_PATTERNS	8` | ISO 23634 Clause 5.8 (clause map) <!-- anchor: jabcode.h:29 --> |
| `DEFAULT_SYMBOL_NUMBER` | `#define DEFAULT_SYMBOL_NUMBER 			1` | <!-- anchor: jabcode.h:31 --> |
| `DEFAULT_MODULE_SIZE` | `#define DEFAULT_MODULE_SIZE				12` | pixels per module <!-- anchor: jabcode.h:32 --> |
| `DEFAULT_COLOR_NUMBER` | `#define DEFAULT_COLOR_NUMBER 			8` | <!-- anchor: jabcode.h:33 --> |
| `DEFAULT_MODULE_COLOR_MODE` | `#define DEFAULT_MODULE_COLOR_MODE 		2` | <!-- anchor: jabcode.h:34 --> |
| `DEFAULT_ECC_LEVEL` | `#define DEFAULT_ECC_LEVEL				3` | <!-- anchor: jabcode.h:35 --> |
| `DEFAULT_MASKING_REFERENCE` | `#define DEFAULT_MASKING_REFERENCE 		7` | <!-- anchor: jabcode.h:36 --> |
| `DISTANCE_TO_BORDER` | `#define DISTANCE_TO_BORDER      4` | <!-- anchor: jabcode.h:39 --> |
| `MAX_ALIGNMENT_NUMBER` | `#define MAX_ALIGNMENT_NUMBER    9` | <!-- anchor: jabcode.h:40 --> |
| `COLOR_PALETTE_NUMBER` | `#define COLOR_PALETTE_NUMBER	4` | palette copies per master symbol <!-- anchor: jabcode.h:41 --> |
| `BITMAP_BITS_PER_PIXEL` | `#define BITMAP_BITS_PER_PIXEL	32` | <!-- anchor: jabcode.h:43 --> |
| `BITMAP_BITS_PER_CHANNEL` | `#define BITMAP_BITS_PER_CHANNEL	8` | <!-- anchor: jabcode.h:44 --> |
| `BITMAP_CHANNEL_COUNT` | `#define BITMAP_CHANNEL_COUNT	4` | RGBA layout of `jab_bitmap` <!-- anchor: jabcode.h:45 --> |
| `JAB_SUCCESS` | `#define	JAB_SUCCESS		1` | <!-- anchor: jabcode.h:47 --> |
| `JAB_FAILURE` | `#define	JAB_FAILURE		0` | <!-- anchor: jabcode.h:48 --> |
| `NORMAL_DECODE` | `#define NORMAL_DECODE		0` | <!-- anchor: jabcode.h:50 --> |
| `COMPATIBLE_DECODE` | `#define COMPATIBLE_DECODE	1` | tolerates partly decoded cascades (status 2) <!-- anchor: jabcode.h:51; detector.c:4156-4160 --> |
| `VERSION2SIZE(x)` | `#define VERSION2SIZE(x)		(x * 4 + 17)` | side-version → modules; argument `x` unparenthesized — a non-atomic argument mis-associates <!-- anchor: jabcode.h:53 --> |
| `SIZE2VERSION(x)` | `#define SIZE2VERSION(x)		((x - 17) / 4)` | <!-- anchor: jabcode.h:54 --> |
| `MAX(a,b)` / `MIN(a,b)` | `({__typeof__ (a) _a = (a); ...})` statement expressions | GNU C extensions in a public header: consumers must compile with a GNU-compatible dialect; unconditional definition collides with consumer-defined `MAX`/`MIN` <!-- anchor: jabcode.h:55-56 --> |

## 3.2 Logging macros and the `MOBILE_BUILD` variants

Under `#ifdef MOBILE_BUILD` the report macros route to `__android_log_print(ANDROID_LOG_ERROR|ANDROID_LOG_INFO, "JABCode", ...)` via unpacking helpers. <!-- anchor: jabcode.h:58-64 --> Otherwise:

```c
#define JAB_REPORT_ERROR(x)	{ printf("JABCode Error: "); printf x; printf("\n"); }
#define JAB_REPORT_INFO(x)	{ if(g_diag_verbose){ printf("JABCode Info: "); printf x; printf("\n"); } }
```

<!-- anchor: jabcode.h:66-67 -->

**The non-mobile `JAB_REPORT_INFO` is verbose-gated.** Every info-level message in the entire codec — including messages upstream emitted unconditionally, and including the reader CLI's partial-decode warning (see [11-cli-internals.md](11-cli-internals.md), §11.4) — prints only when `g_diag_verbose` is set. Default is 0, so info output is silent in stock builds. <!-- anchor: jabcode.h:67; decoder.c:88 -->

`JAB_DIAG_INFO` adds a second, explicit gate for high-volume markers:

```c
extern unsigned char g_diag_verbose;
#define JAB_DIAG_INFO(x) do { if (g_diag_verbose) JAB_REPORT_INFO(x); } while (0)
```

<!-- anchor: jabcode.h:90-91 -->

The surrounding WS-5 "Heisenberg gate" comment states the intent: use `JAB_DIAG_INFO` "for per-iteration / per-frame markers that are high-volume (Nc\_FALLBACK loop body, palette learning hashes, intermediate detection state)" and "plain JAB\_REPORT\_INFO for terminal markers (FAIL\_ATTR, DECODE\_OK, final result) that should always fire"; default OFF preserves "the camera-thread decode budget in production builds." It also explains why `g_diag_verbose` is declared as raw `unsigned char` rather than `jab_boolean`: the typedef appears later in the header, and the macro must be usable "from sites that include only the early portion of this header." <!-- anchor: jabcode.h:72-89 --> Note the double gate is redundant in non-mobile builds (`JAB_REPORT_INFO` already checks the flag) but load-bearing under `MOBILE_BUILD`, where `JAB_REPORT_INFO` is an unconditional Android log call. <!-- anchor: jabcode.h:63-67, 91 -->

## 3.3 Process-global toggles

Three extern globals, each with setter/getter functions (§3.6.4). All three are defined in `decoder.c` and carry the same defining-TU concurrency contract, verbatim: "Process-global configuration. Set once before spawning worker threads; read-only during concurrent encode/decode. Mutating it concurrently with a decode is a data race. (Deliberately NOT \_Thread\_local so a single setter call propagates to pool threads.)" <!-- anchor: decoder.c:83-87, 128-131, 177-180 -->

| Global | Declaration | Default | Exact semantics |
|---|---|---|---|
| `g_diag_verbose` | `extern unsigned char g_diag_verbose;` | `0` | Gates `JAB_REPORT_INFO` (non-mobile) and `JAB_DIAG_INFO` everywhere. <!-- anchor: jabcode.h:90; decoder.c:88 --> |
| `g_permissive_color_classification` | `extern unsigned char g_permissive_color_classification;` | `0` | "Path β": in `decodeMasterMetadataPartI`'s module-colour stage, substitutes rgb=5 (Magenta) with rgb=6 (Yellow) before the `{0, 3, 6}` validity check, compensating camera green-channel under-capture; mechanically a no-op in Mode 0 (metadata rgb ∈ `{0, 7}`). <!-- anchor: jabcode.h:93-98; decoder.c:98-132 --> |
| `g_preferred_color_count` | `extern int g_preferred_color_count;` | `0` | Non-zero collapses `decodeMaster`'s 8-iteration Nc fallback ladder to a single try at `Nc = log2(count) - 1`. Header comment: "Valid values: 2, 4, 8, 16, 32, 64, 128, 256 (= 2^(Nc+1) for Nc=0..7). 0 = auto (default)." Invalid counts fall through to auto-detect. <!-- anchor: jabcode.h:100-105; decoder.c:157-181, 2122-2150 --> |

A fourth process-global, `g_profile_stages` (with accumulator `g_decode_profile`), lives in `decode_profile.h`/`decode_profile.c` and is controlled by the profiling functions in §3.6.5. <!-- anchor: decode_profile.h:93-94; decode_profile.c:24-28 -->

**Documented contradiction.** The header comment for `jabSetDiagVerbose` ends "Thread-local." <!-- anchor: jabcode.h:247-254 --> The implementation is process-global — deliberately: the defining comment records that the previous `__thread` design was "broken in practice" (UI thread set the flag, CameraX analyzer thread never saw it; 2026-05-30 `H_partI_unifies` trace capture) and that a torn read on a single boolean is benign. <!-- anchor: decoder.c:65-88 --> Trust the implementation, not the header comment. The same header-comment claim ("Thread-local") appears for `jabSetStrictPartIIRequired` <!-- anchor: jabcode.h:238-245 --> and there it is **true**: `__thread jab_boolean g_strict_partII_required = 0;`. <!-- anchor: decoder.c:43 -->

## 3.4 Scalar typedefs

| Typedef | Underlying type |
|---|---|
| `jab_byte` | `unsigned char` <!-- anchor: jabcode.h:70 --> |
| `jab_char` | `char` <!-- anchor: jabcode.h:106 --> |
| `jab_boolean` | `unsigned char` <!-- anchor: jabcode.h:107 --> |
| `jab_int32` | `int` <!-- anchor: jabcode.h:108 --> |
| `jab_uint32` | `unsigned int` <!-- anchor: jabcode.h:109 --> |
| `jab_int16` | `short` <!-- anchor: jabcode.h:110 --> |
| `jab_uint16` | `unsigned short` <!-- anchor: jabcode.h:111 --> |
| `jab_int64` | `long long` <!-- anchor: jabcode.h:112 --> |
| `jab_uint64` | `unsigned long long` <!-- anchor: jabcode.h:113 --> |
| `jab_float` | `float` <!-- anchor: jabcode.h:114 --> |
| `jab_double` | `double` <!-- anchor: jabcode.h:115 --> |

The typedef block is interrupted: `jab_byte` is defined at line 70, then the diagnostic-flag block (72-105) intervenes, then the remaining typedefs (106-115). This ordering is deliberate (see §3.2). <!-- anchor: jabcode.h:70-115 -->

## 3.5 Structs — verbatim

`jab_vector2d`: <!-- anchor: jabcode.h:120-123 -->

```c
typedef struct {
	jab_int32	x;
	jab_int32	y;
}jab_vector2d;
```

`jab_point`: <!-- anchor: jabcode.h:128-131 -->

```c
typedef struct {
	jab_float	x;
	jab_float	y;
}jab_point;
```

`jab_data` — flexible array member; allocate `sizeof(jab_data) + length`: <!-- anchor: jabcode.h:136-139 -->

```c
typedef struct {
	jab_int32	length;
	jab_char	data[];
}jab_data;
```

`jab_bitmap` — flexible array member; pixel layout fixed by the `BITMAP_*` constants (32 bpp, 8 bpc, 4 channels): <!-- anchor: jabcode.h:144-151 -->

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

`jab_symbol`: <!-- anchor: jabcode.h:156-167 -->

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

`jab_encode`: <!-- anchor: jabcode.h:172-185 -->

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

`jab_metadata`: <!-- anchor: jabcode.h:190-198 -->

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

`jab_decoded_symbol`: <!-- anchor: jabcode.h:203-214 -->

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

`jab_encode.mask_type` and `jab_metadata.Pg`/`jab_symbol.Pg` are fork additions serving external mask/payload observability and the synthetic decoder respectively (upstream header is 176 lines; this one is 298). <!-- anchor: corpus §2.3 (jabcode.h row); jabcode.h:162, 184, 197 -->

## 3.6 Functions

### 3.6.1 Encoder lifecycle

| Item | Signature (verbatim) | Ownership / threading contract |
|---|---|---|
| `createEncode` | `extern jab_encode* createEncode(jab_int32 color_number, jab_int32 symbol_number);` | Returns a `calloc`'d object owning `palette`, `symbol_versions`, `symbol_ecc_levels`, `symbol_positions`, `symbols`; release only via `destroyEncode`. Invalid `color_number` (not in `{2,4,8,16,32,64,128,256}`) is silently coerced to `DEFAULT_COLOR_NUMBER`; `symbol_number` outside 1..61 silently coerced to 1 — no error is reported for either. Palette is allocated for `COLOR_PALETTE_NUMBER` (4) panels and the default palette replicated into all four. <!-- anchor: jabcode.h:217; encoder.c:182-254 --> |
| `destroyEncode` | `extern void destroyEncode(jab_encode* enc);` | Frees `enc->palette`, `symbol_versions`, `symbol_ecc_levels`, `symbol_positions`, **`enc->bitmap`**, each symbol's `data`/`data_map`/`metadata`/`matrix`, `enc->symbols`, then `enc`. Consequently the caller must **not** free `enc->bitmap` (the `generateJABCode` output image) separately, and must copy pixel data out before destroying if it outlives the encode object. No NULL guard: `destroyEncode(NULL)` dereferences immediately. <!-- anchor: jabcode.h:218; encoder.c:261-280 --> |
| `generateJABCode` | `extern jab_int32 generateJABCode(jab_encode* enc, jab_data* data);` | `data` is borrowed (read-only, not retained, not freed). On success populates `enc->bitmap` and `enc->mask_type`. See return-convention callout below. <!-- anchor: jabcode.h:219; encoder.c:2307-2443 --> |

> **`generateJABCode` returns 0 on success — the inverse of `JAB_SUCCESS`.**
>
> The function's own contract, verbatim: `@return 0:success | 1: out of memory | 2:no input data | 3:incorrect symbol version or position | 4: input data too long`. <!-- anchor: encoder.c:2305 -->
>
> The header defines `JAB_SUCCESS` as `1` and `JAB_FAILURE` as `0` <!-- anchor: jabcode.h:47-48 -->, and every other status-returning function in the API follows that convention. `generateJABCode` does not: `0` means success, non-zero is an error code. A caller who writes `if (generateJABCode(enc, data) == JAB_SUCCESS)` treats *out-of-memory* as success. The correct idiom is the writer CLI's: `if(generateJABCode(enc, data) != 0)`. <!-- anchor: src/jabcodeWriter/jabwriter.c:476 --> The final success path returns literal `0`; failure paths return 1-4. <!-- anchor: encoder.c:2313, 2323, 2331, 2346, 2443 --> Binding authors must preserve this inversion or translate it explicitly at the boundary.

### 3.6.2 Decoder entry points

| Item | Signature (verbatim) | Ownership / threading contract |
|---|---|---|
| `decodeJABCode` | `extern jab_data* decodeJABCode(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status);` | Wrapper: stack-allocates `jab_decoded_symbol symbols[MAX_SYMBOL_NUMBER]` and delegates to `decodeJABCodeEx`. <!-- anchor: jabcode.h:220; detector.c:4238-4242 --> |
| `decodeJABCodeEx` | `extern jab_data* decodeJABCodeEx(jab_bitmap* bitmap, jab_int32 mode, jab_int32* status, jab_decoded_symbol* symbols, jab_int32 max_symbol_number);` | Returns a `malloc`'d `jab_data*` the caller releases with `free()`, or NULL. `bitmap` is borrowed **and mutated**: `balanceRGB` rewrites its pixels in place. `symbols` must be non-NULL (else error `"Invalid symbol buffer"`, return NULL); it is `memset` to zero on entry. `status` may be NULL; when set: 0 not detectable, 1 not decodable, 2 partly decoded (COMPATIBLE\_DECODE only), 3 fully decoded. Before returning — on success *and* failure — the function frees `symbols[i].palette` and `symbols[i].data` for every touched symbol: those two fields dangle after return; the value fields (`metadata`, `slave_metadata`, `side_size`, `module_size`, `pattern_positions`, indices) remain valid. <!-- anchor: jabcode.h:221; detector.c:4055-4227 (buffer check 4068-4072, memset 4112, mutation 4087, frees 4148-4153/4199-4203, status doc 4060) --> |
| `decodeJABCodeSynthetic` | `extern jab_data* decodeJABCodeSynthetic(jab_bitmap* bitmap, jab_int32 color_number, jab_int32 ecc_level, jab_int32 module_size, jab_int32 symbol_width, jab_int32 symbol_height, jab_int32 mask_type, jab_byte* encoder_data_map, jab_int32* encoder_wcwr, jab_int32 encoder_Pg, jab_int32 mode, jab_int32* status);` | Fork test instrument: bypasses camera detection using known encoder parameters. Rejects `color_number` outside `{2,4,8,16,32,64,128,256}` with `"Invalid color_number for synthetic decode (must be 2, 4, 8, 16, 32, 64, 128, or 256)"`. Not a conformant decoder — see [10-fork-extensions.md](10-fork-extensions.md). <!-- anchor: jabcode.h:222; detector_synthetic.c:128-160 --> |

### 3.6.3 Symbology identifier and ABI stub

| Item | Signature (verbatim) | Contract |
|---|---|---|
| `jabGetSymbologyIdentifier` | `extern jab_char* jabGetSymbologyIdentifier(void);` | Returns a pointer to a `_Thread_local` 4-byte static buffer — do not free. Per the header comment: the identifier of the most recent successful decode, `"]jm"` per Annex H Table H.1 ("currently always \"]j0\"; ECI/FNC1 are not yet decoded" — header text; the decoder writes the modifier via `jab_format_symbology_identifier` at decode end), empty string until the first successful decode *on that thread*; it is transmission metadata, the decoded payload is left unmodified. <!-- anchor: jabcode.h:224-231; decoder.c:196-208, 2987 --> |
| `resetDecoderState` | `extern void resetDecoderState(void);` | Documented ABI-compat no-op ("WS-6 Option F"): exported so the panama-poc-generated Panama JAR's `SymbolLookup.findOrThrow("resetDecoderState")` succeeds; "swift-java-poc has no observation context to reset." <!-- anchor: jabcode.h:232-236; decoder.c:2991-3017 --> |

### 3.6.4 Runtime toggles

| Item | Signature (verbatim) | Contract |
|---|---|---|
| `jabSetStrictPartIIRequired` | `extern void jabSetStrictPartIIRequired(jab_boolean strict);` | Sets `__thread jab_boolean g_strict_partII_required` — genuinely thread-local. TRUE makes `decodeMaster` refuse the optimistic Part II fall-through when Part I failed (anti-fabrication on degraded camera input); FALSE (default) preserves the legacy behavior that multi-frame averaging callers rely on. Mobile camera entry points set it per decode and reset after. No getter is declared. <!-- anchor: jabcode.h:238-245; decoder.c:26-48, 2290-2311 --> |
| `jabSetDiagVerbose` / `jabIsDiagVerbose` | `extern void jabSetDiagVerbose(jab_boolean verbose);` / `extern jab_boolean jabIsDiagVerbose(void);` | Writes/reads `g_diag_verbose`. Process-global despite the header comment (§3.3). Set before spawning workers; mutating mid-decode is a documented (benign, single-boolean) race. <!-- anchor: jabcode.h:247-256; decoder.c:88-96, 210-213 --> |
| `jabSetPermissiveColorClassification` / `jabIsPermissiveColorClassification` | `extern void jabSetPermissiveColorClassification(jab_boolean permissive);` / `extern jab_boolean jabIsPermissiveColorClassification(void);` | Writes/reads `g_permissive_color_classification` (§3.3). Header points at `decoder.c:80-127` for the empirical basis and `decoder.c:1086` for the call site applying the rgb=5 → rgb=6 remap. <!-- anchor: jabcode.h:258-262; decoder.c:147-155 --> |
| `jabSetPreferredColorCount` / `jabGetPreferredColorCount` | `extern void jabSetPreferredColorCount(jab_int32 count);` / `extern jab_int32 jabGetPreferredColorCount(void);` | Writes/reads `g_preferred_color_count` (§3.3): pins the Nc fallback ladder. <!-- anchor: jabcode.h:264-271; decoder.c:186-194 --> |

### 3.6.5 Decode profiling

| Item | Signature (verbatim) | Contract |
|---|---|---|
| `jabSetProfileStages` | `extern void jabSetProfileStages(jab_boolean profile);` | Sets `g_profile_stages` (process-global, default OFF; when off every timing site short-circuits on a single global read). <!-- anchor: jabcode.h:282; decode_profile.c:18-37 --> |
| `jabIsProfileStages` | `extern jab_boolean jabIsProfileStages(void);` | Reads the flag. <!-- anchor: jabcode.h:283; decode_profile.c:43-46 --> |
| `jabGetDecodeProfile` | `extern const struct jab_decode_profile* jabGetDecodeProfile(void);` | Returns a pointer to the process-global accumulator — "do not free". Field access requires including `decode_profile.h` (the header only forward-declares the tagged struct). Fields: `stage_us[JAB_STAGE_COUNT]`, `detect_us[JAB_DET_COUNT]`, `decode_count`. <!-- anchor: jabcode.h:273-284; decode_profile.c:48-55; decode_profile.h:79-88 --> |
| `jabResetDecodeProfile` | `extern void jabResetDecodeProfile(void);` | `memset`s the accumulator to zero. <!-- anchor: jabcode.h:285; decode_profile.c:60-63 --> |

### 3.6.6 Image I/O and error reporting

| Item | Signature (verbatim) | Ownership / format contract |
|---|---|---|
| `saveImage` | `extern jab_boolean saveImage(jab_bitmap* bitmap, jab_char* filename);` | PNG writer. <!-- anchor: jabcode.h:287; image.c:27 --> |
| `saveImageCMYK` | `extern jab_boolean saveImageCMYK(jab_bitmap* bitmap, jab_boolean isCMYK, jab_char* filename);` | TIFF writer; `isCMYK` selects whether the input bitmap is already CMYK (the writer CLI passes `0` for its RGB-sourced bitmaps). <!-- anchor: jabcode.h:288; image.c:128; src/jabcodeWriter/jabwriter.c:496 --> |
| `readImage` | `extern jab_bitmap* readImage(jab_char* filename);` | **PNG-based** file reader; returns a `malloc`'d `jab_bitmap*` the caller releases with `free()` (the reader CLI does exactly that). NULL on failure. <!-- anchor: jabcode.h:289; image.c:187; src/jabcodeReader/jabreader.c:47, 90 --> |
| `saveImageToMemory` | `extern jab_byte* saveImageToMemory(jab_bitmap* bitmap, jab_int32* out_length);` | In-memory PNG encode; returns a caller-freed buffer, length via `out_length`. <!-- anchor: jabcode.h:290; image.c:244 --> |
| `readImageFromMemory` | `extern jab_bitmap* readImageFromMemory(jab_byte* buffer, jab_int32 length);` | In-memory PNG decode; `buffer` borrowed; returns a caller-freed `jab_bitmap*`. <!-- anchor: jabcode.h:291; image.c:294 --> |
| `reportError` | `extern void reportError(jab_char* message);` | `printf("JABCode Error: %s\n", message);` — stdout, not stderr. <!-- anchor: jabcode.h:292; encoder.c:2450-2453 --> |

## 3.7 Auxiliary public fork headers

These headers are public to any consumer that includes them (they live in the library source directory, on the CLIs' include path — chapter 1, §1.2). They are not part of `jabcode.h`.

| Header | Public surface | Notes |
|---|---|---|
| `include/jabcode_wrapper.h` | `createEncodeWrapper`, `destroyEncodeWrapper`, `generateJABCodeWrapper`, `decodeJABCodeWrapper`, `decodeJABCodeExWrapper`, `saveImageWrapper` | **Implementations NOT FOUND in this tree.** The six externs mirror the core API signatures one-for-one; no `.c` in `src/` defines them, so any consumer linking against these symbols fails at link time against libraries built from this tree. Binding-side consequences: [17-downstream-bindings.md](17-downstream-bindings.md). <!-- anchor: include/jabcode_wrapper.h:10-15; corpus §6 NOT FOUND register --> |
| `color_calibration.h` | `jab_color_calibration` struct; `jabLoadCalibrationFromJSON`, `jabApplyCalibration`, `jabRemapColor`, `jabClearCalibration`, `jabHasCalibration`, `jabCalibrateFromObservedRGB`, `jabRemapColorInverse`, `jabBuildCalibrationFromFPCores` | Always compiled/exported; its decode-path consumers are gated behind `USE_FP_CALIBRATION` (chapter 2, §2.4). Observed-slot order for `jabCalibrateFromObservedRGB`: `[0]=K, [1]=W, [2]=R, [3]=G, [4]=B, [5]=Y, [6]=C, [7]=M`. <!-- anchor: color_calibration.h:6-60 --> |
| `adaptive_palette.h` | `jab_color_transform`, `jab_color_sample`, `jab_adaptive_palette`; `adaptive_palette_init/add_sample/learn_transform/apply_transform/match/match_with_confidence/reset/free/get_expected/get_adapted`; `MAX_PALETTE_SIZE 256`, `MAX_REFERENCE_SAMPLES 8` | Compiled and exported, no in-library call site (chapter 2, §2.3/2.4). <!-- anchor: adaptive_palette.h:14-156 --> |
| `decode_profile.h` | `jab_decode_stage` (DETECT, PALETTE, COLOR\_CLASSIFY, DEINTERLEAVE, LDPC, DATA\_DECODE), `jab_detect_substage` (BINARIZE, FINDER, TRANSFORM, SAMPLE), `jab_decode_profile`; `JAB_PROF_BEGIN`/`JAB_PROF_END`/`JAB_PROF_DET_END`; externs `g_profile_stages`, `g_decode_profile` | Required to read the struct returned by `jabGetDecodeProfile`. `JAB_PROF_DET_END` folds one interval into both the sub-stage and the DETECT roll-up, so sub-stages sum to DETECT by construction. <!-- anchor: decode_profile.h:54-133 --> |
| `symbology_id.h` | `JAB_FNC1_NONE/PRECEDING/FOLLOWING`; `jab_symbology_modifier(int eci_used, int fnc1_mode)`; `jab_format_symbology_identifier(int, int, char*)` | Header-only (static inline), no library link needed; Table H.1 modifier matrix `{{0,1},{2,4},{3,5}}`; output buffer must hold ≥ 4 bytes. <!-- anchor: symbology_id.h:26-54 --> |
| `lab_color.h` | `jab_lab_color`, `jab_xyz_color`, `jab_rgb_color`; `rgb_to_lab`, `lab_to_rgb`, `delta_e_76`, `delta_e_2000`, `find_nearest_color_lab`, `rgb_to_xyz`, `xyz_to_lab`, `lab_to_xyz`, `xyz_to_rgb` | <!-- anchor: lab_color.h:26-126 --> |
| `kdtree_color.h` | `kd_node`, `kdtree_color`; `kdtree_build(jab_byte* palette, jab_int32 color_number, jab_int32 palette_index)`, `kdtree_nearest`, `kdtree_free` | <!-- anchor: kdtree_color.h:17-57 --> |

## Invariants

- Every `jab_bitmap` produced or consumed by the API uses the fixed layout 32 bpp / 8 bpc / 4 channels. <!-- anchor: jabcode.h:43-45 -->
- `jab_data`/`jab_bitmap` are flexible-array structs: `sizeof(struct) + payload` allocation, single `free()`.
- Side length in modules = `version * 4 + 17` for version 1..32 (21..145). <!-- anchor: jabcode.h:53-54 -->
- All status-returning API functions use `JAB_SUCCESS`(1)/`JAB_FAILURE`(0) **except** `generateJABCode` (§3.6.1) and the decode `status` out-parameter's four-value protocol (§3.6.2).

## Failure modes

- `createEncode` inner allocation failure returns NULL after `reportError(...)` **without freeing already-allocated members or `enc` itself** — a one-shot leak on the OOM path. <!-- anchor: encoder.c:210-253 -->
- `destroyEncode(NULL)` is undefined behavior (no guard). <!-- anchor: encoder.c:261-263 -->
- Misreading `generateJABCode`'s return as `JAB_SUCCESS`-conventioned inverts success/failure (§3.6.1).
- Using `symbols[i].data` or `symbols[i].palette` after `decodeJABCodeEx` returns: dangling (§3.6.2).
- Passing a bitmap you still need unmodified to `decodeJABCode(Ex)`: `balanceRGB` mutates it in place. <!-- anchor: detector.c:4087 -->

## Extension points

- The process-global setter/getter idiom (§3.3-3.6.5) is the established pattern for new opt-in behavior; `decode_profile.c` documents it as deliberately mirrored. <!-- anchor: decode_profile.c:10-23 -->
- New public functions extend the exported symbol set and therefore trip `check-lib` until `refresh-lib` is run (chapter 1, §1.5).

## Performance notes

- Profiling off-path cost is one global read per timing site (§3.6.5). Benchmarks exercising this surface: `bench`, `bench-concurrent`, `bench-cascade`, `profile` ([12-benchmark-estate.md](12-benchmark-estate.md)).
- `jabGetSymbologyIdentifier` and the strict-Part-II flag are thread-local; the three §3.3 toggles are process-global set-before-threads configuration — the codec's overall reentrancy contract is chapter 14's subject.

## Known defects

| Defect | Evidence |
|---|---|
| `generateJABCode` 0-on-success inversion | encoder.c:2305; jabcode.h:47-48 |
| Header comment claims `jabSetDiagVerbose` is "Thread-local"; implementation is process-global | jabcode.h:254 vs decoder.c:65-88 |
| Non-mobile `JAB_REPORT_INFO` silently gated on `g_diag_verbose` — info messages (including the reader CLI's partial-decode warning) never print by default | jabcode.h:67; src/jabcodeReader/jabreader.c:66-69 |
| `MAX`/`MIN` GNU statement expressions and unconditional definition in the public header | jabcode.h:55-56 |
| `VERSION2SIZE(x)` unparenthesized macro argument | jabcode.h:53 |
| `createEncode` OOM-path leak; `destroyEncode` NULL dereference | encoder.c:210-253, 261-263 |
| `jabcode_wrapper.h` declares six externs with no implementation in this tree | include/jabcode_wrapper.h:10-15 |
| `resetDecoderState` is a no-op kept for foreign-JAR symbol lookup | decoder.c:2991-3017 |

---

Encoder internals behind this surface: [04-encoder.md](04-encoder.md). Decode internals: [05-detector-and-decoder.md](05-detector-and-decoder.md). The CLIs that consume exactly this surface: [11-cli-internals.md](11-cli-internals.md).
