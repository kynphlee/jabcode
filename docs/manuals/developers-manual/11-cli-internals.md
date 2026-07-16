# 11. CLI internals — `jabwriter.c`, `jabreader.c`

<!-- objective: A maintainer can map every CLI flag to the jab_encode fields it populates and every exit path to its source line, including the validation order, the --help non-zero exits, and the reader's module-size exit-code diagnostic. -->

**Responsibility.** `src/jabcodeWriter/jabwriter.c` (507 lines) and `src/jabcodeReader/jabreader.c` (93 lines) are the two command-line consumers of the public API of [03-public-surface-jabcode-h.md](03-public-surface-jabcode-h.md). Build units and link lines: [01-repository-and-build.md](01-repository-and-build.md), §1.2. Task-level usage lives in the Operator's Manual (JC-U ch. 7-8: [../operators-manual/07-encoding-with-jabcodewriter.md](../operators-manual/07-encoding-with-jabcodewriter.md), [../operators-manual/08-decoding-with-jabcodereader.md](../operators-manual/08-decoding-with-jabcodereader.md)).

`src/jabcodeWriter/jabwriter.h` is **empty** — zero content lines; it exists only to satisfy the `#include "jabwriter.h"` at jabwriter.c:5. <!-- anchor: src/jabcodeWriter/jabwriter.h (0 content lines, corpus §2.3); jabwriter.c:5 -->

## 11.1 Writer structure

State is file-scope globals, zero-initialized: `data` (`jab_data*`), `filename`, `color_number`, `symbol_number`, `module_size`, `master_symbol_width`, `master_symbol_height`, `symbol_positions` + count, `symbol_versions` + count, `symbol_ecc_levels` + count, `color_space`. <!-- anchor: jabwriter.c:7-20 -->

`parseCommandLineParameters` (jabwriter.c:66, `@return 1: success | 0: failure` <!-- anchor: jabwriter.c:62-66 -->) runs **two scans** over `argv`:

- First scan (jabwriter.c:69-247): `--input`, `--input-file`, `--output`, `--color-number`, `--module-size`, `--symbol-width`, `--symbol-height`, `--symbol-number`, `--color-space`.
- Between the scans: presence checks and the `symbol_number` default (jabwriter.c:249-268).
- Second scan (jabwriter.c:271-394): `--ecc-level`, `--symbol-version`, `--symbol-position` — deferred because their array allocations are sized by `symbol_number`, which the first scan must have fixed. <!-- anchor: jabwriter.c:281, 319, 366 -->

Two parse-loop properties worth stating: unrecognized tokens are **silently ignored** (neither scan has a trailing `else`), and values are consumed with `para[++loop]`, so a value that lexically looks like a flag is accepted as a value. <!-- anchor: jabwriter.c:69-247, 271-394 -->

## 11.2 Flag → field mapping

| Flag | Parse anchor | Populates (local) | Reaches the codec as | Populate anchor |
|---|---|---|---|---|
| `--input` | jabwriter.c:71-88 | `data` (malloc'd `jab_data`, `length = strlen`) | `generateJABCode(enc, data)` argument | jabwriter.c:476 |
| `--input-file` | jabwriter.c:89-122 | `data` (whole file, binary read) | same | jabwriter.c:476 |
| `--output` | jabwriter.c:123-131 | `filename` (points into `argv`) | `saveImage(enc->bitmap, filename)` / `saveImageCMYK(enc->bitmap, 0, filename)` | jabwriter.c:488, 496 |
| `--color-number` | jabwriter.c:132-156 | `color_number` | `createEncode(color_number, symbol_number)` first argument → `enc->color_number` | jabwriter.c:446 |
| `--module-size` | jabwriter.c:157-172 | `module_size` | `enc->module_size` (only if `> 0`) | jabwriter.c:453-456 |
| `--symbol-width` | jabwriter.c:173-188 | `master_symbol_width` | `enc->master_symbol_width` (only if `> 0`) | jabwriter.c:457-460 |
| `--symbol-height` | jabwriter.c:189-204 | `master_symbol_height` | `enc->master_symbol_height` (only if `> 0`) | jabwriter.c:461-464 |
| `--symbol-number` | jabwriter.c:205-225 | `symbol_number` | `createEncode(...)` second argument → `enc->symbol_number`; also sizes the second-scan arrays | jabwriter.c:446 |
| `--ecc-level` | jabwriter.c:273-310 | `symbol_ecc_levels[]` (`calloc(symbol_number)`) | `enc->symbol_ecc_levels[loop]` per symbol | jabwriter.c:467-468 |
| `--symbol-version` | jabwriter.c:311-357 | `symbol_versions[]` (`jab_vector2d`, x/y pairs) | `enc->symbol_versions[loop]` per symbol | jabwriter.c:469-470 |
| `--symbol-position` | jabwriter.c:358-393 | `symbol_positions[]` | `enc->symbol_positions[loop]` per symbol | jabwriter.c:471-472 |
| `--color-space` | jabwriter.c:226-246 | `color_space` | save-path selection only (0 → PNG via `saveImage`; 1 → TIFF via `saveImageCMYK`); never touches `jab_encode` | jabwriter.c:486-501 |
| `--help` | jabwriter.c:435 | — | `printUsage()`; exit 1 | jabwriter.c:435-439 |

`--symbol-width`/`--symbol-height` are documented as pixels in the usage text ("Master symbol width in pixel") <!-- anchor: jabwriter.c:37-38 --> — the pixel-vs-module tension this creates for binding consumers is chapter 17 territory ([17-downstream-bindings.md](17-downstream-bindings.md)).

Note what the CLI never sets: `enc->palette` (default palette from `createEncode`), and Mode-related toggles — the writer has no flag for any §3.3 process global.

## 11.3 Writer validation order, as executed

Numbered in execution order; error strings verbatim. All `reportError` output goes to stdout with prefix `JABCode Error: ` (encoder.c:2450-2453).

1. `argc < 2` or `argv[1] == "--help"` → usage, exit 1 (§11.5). <!-- anchor: jabwriter.c:435-439 -->
2. First scan, per flag, in `argv` order: missing value → `Value for option '%s' missing.`; non-numeric → `Invalid or missing values for option '%s'.`. <!-- anchor: jabwriter.c:73-77, 142-146 (pattern repeats per flag) -->
3. `--color-number` domain: not in `{2,4,8,16,32,64,128,256}` → `"Invalid color number. Supported color numbers are 2, 4, 8, 16, 32, 64, 128, 256."` (WS-0 admitted 2; WS-3 admitted 256 — comment at jabwriter.c:147-148). <!-- anchor: jabwriter.c:149-155 -->
4. `--module-size` / `--symbol-width` / `--symbol-height`: negative values rejected via the `*endptr || value < 0` check (`Invalid or missing values...`). <!-- anchor: jabwriter.c:167, 183, 199 -->
5. `--symbol-number` domain: `< 1 || > MAX_SYMBOL_NUMBER` → `"Invalid symbol number (must be 1 - 61)."`. <!-- anchor: jabwriter.c:220-224 -->
6. `--color-space` domain: not 0/1 → `"Invalid color space (must be 0 or 1)."`. <!-- anchor: jabwriter.c:241-245 -->
7. Input presence: no `data` → `"Input data missing"`; `data->length == 0` → `"Input data is empty"`. <!-- anchor: jabwriter.c:250-259 -->
8. Output presence: no `filename` → `"Output file missing"`. <!-- anchor: jabwriter.c:260-264 -->
9. `symbol_number == 0` → defaulted to 1 (no error). <!-- anchor: jabwriter.c:265-268 -->
10. Second scan `--ecc-level`: fewer values than symbols is tolerated (loop breaks; a non-numeric token backtracks with `loop--` so the token is re-examined as a flag); domain check `< 0 || > 10` → `"Invalid error correction level (must be 1 - 10)."` — note the check **admits 0**, which the usage text defines as "using the default level" for the master and "the same level as its host" for slaves. <!-- anchor: jabwriter.c:287-309, 40-44 -->
11. Second scan `--symbol-version`: exactly `2 * symbol_number` values required (`Too few values for option '%s'.`); domain `x`/`y` in 1..32 else `"Invalid symbol side version (must be 1 - 32)."`. <!-- anchor: jabwriter.c:325-356 -->
12. Second scan `--symbol-position`: exactly `symbol_number` values (`Too few values...`); domain 0..60 else `"Invalid symbol position value (must be 0 - 60)."`. <!-- anchor: jabwriter.c:372-392 -->
13. Post-scan: single-symbol code with an explicit position other than 0 → `"Incorrect symbol position value for master symbol."`. This is the *only* CLI-side master-position check; for multi-symbol codes, master-position enforcement happens inside the encoder (the reorder + `Master symbol missing` split, [04-encoder.md](04-encoder.md)). <!-- anchor: jabwriter.c:397-404 -->
14. Post-scan completeness for `symbol_number > 1`: positions → `"Symbol position information is incomplete for multi-symbol code"`; versions → `"Symbol version information is incomplete for multi-symbol code"`. <!-- anchor: jabwriter.c:405-414 -->

After parsing, `main` applies the fields (§11.2 populate anchors), guarded so that zero-valued locals never overwrite `createEncode` defaults. <!-- anchor: jabwriter.c:446-473 -->

## 11.4 Writer exit paths

Documented contract: `@return 0: success | 1: failure`. <!-- anchor: jabwriter.c:429-432 -->

| Path | Condition | Output | Exit | Anchor |
|---|---|---|---|---|
| Help/no-args | `argc < 2 \|\| argv[1]=="--help"` | usage text | `1` | jabwriter.c:435-439 |
| Parse failure | `parseCommandLineParameters` returns 0 | per §11.3 | `1` | jabwriter.c:440-443 |
| Encode-object failure | `createEncode` NULL | `"Creating encode parameter failed"` | `1` | jabwriter.c:446-452 |
| Encode failure | `generateJABCode(enc, data) != 0` — the 0-on-success inversion, consumed correctly here | `"Creating jab code failed"` | `1` | jabwriter.c:476-482 |
| PNG save failure | `color_space == 0` and `!saveImage(...)` | `"Saving png image failed"` | `1` | jabwriter.c:486-492 |
| TIFF save failure | `color_space == 1` and `!saveImageCMYK(enc->bitmap, 0, filename)` | `"Saving tiff image failed"` | `1` | jabwriter.c:494-500 |
| Success | — | — | `0` | jabwriter.c:485, 503-505 |

Memory behavior: the success and encode-failure paths run `destroyEncode` + `cleanMemory` (which frees `data`, `symbol_positions`, `symbol_versions`, `symbol_ecc_levels`); the parse-failure path returns without `cleanMemory`, leaking whatever the scans allocated — reclaimed by process exit, but relevant to anyone embedding this `main` logic. <!-- anchor: jabwriter.c:421-427, 440-443, 479-481, 503-504 -->

## 11.5 Reader structure and argument-ordering strictness

Usage: `jabcodeReader input-image(png) [--output output-file]`. <!-- anchor: jabreader.c:14 --> The reader is **positional and strict**: `argv[1]` must be the input image; if `argv[2]` exists it must be exactly the string `--output`, otherwise `Unknown parameter: %s` and exit 255. The output filename is taken from `argv[3]` — which is passed to `fopen` **without an existence check**: `jabcodeReader img.png --output` (no filename) reaches `fopen(argv[3], "wb")` with a NULL/out-of-range argument. <!-- anchor: jabreader.c:33-43, 74 -->

Decode call: `decodeJABCodeEx(bitmap, NORMAL_DECODE, &decode_status, symbols, MAX_SYMBOL_NUMBER)` with a stack array of 61 `jab_decoded_symbol`. <!-- anchor: jabreader.c:52-54 -->

Output modes: with `--output`, decoded bytes are `fwrite`-ed to the file; otherwise they print to stdout character-by-character followed by a newline. <!-- anchor: jabreader.c:72-88 -->

## 11.6 Reader exit paths and the module-size diagnostic

Documented contract: `@return 0: success | 255: not detectable | other non-zero: decoding failed`. <!-- anchor: jabreader.c:21-24 -->

| Path | Condition | Exit | Anchor |
|---|---|---|---|
| Help/no-args | `argc < 2 \|\| argv[1]=="--help"` | `255` | jabreader.c:27-31 |
| Unknown parameter | `argv[2]` present, not `--output` | `255` | jabreader.c:38-42 |
| Image load failure | `readImage(argv[1])` NULL | `255` | jabreader.c:47-49 |
| Decode failed, symbol detected | decode NULL, `decode_status > 0` | `(jab_int32)(symbols[0].module_size + 0.5f)` — the detected module size in pixels, rounded, as the exit code | jabreader.c:55-60 |
| Decode failed, nothing detected | decode NULL, `decode_status <= 0` | `255` | jabreader.c:61-62 |
| Output file unopenable | `fopen(argv[3], "wb")` NULL | `255` (`"Can not open the output file"`; `bitmap`/`decoded_data` leak on this path) | jabreader.c:74-79 |
| Success | — | `0` | jabreader.c:90-92 |

The module-size exit code is a deliberate diagnostic channel: a failed-but-detected scan reports the resolved module pixel pitch to the calling process through the exit status. It collides with the 255 sentinel only if the module size itself rounds to 255, and with 0 only if `module_size < 0.5` — both outside practical print geometry. This is why the documented contract reads "other non-zero: decoding failed". <!-- anchor: jabreader.c:23, 59-60 -->

Two behavioral notes, both facts of the current source:

- The partial-decode warning is doubly unreachable. `decode_status == 2` triggers `JAB_REPORT_INFO(("The code is only partly decoded. Some slave symbols have not been decoded and are ignored."))` <!-- anchor: jabreader.c:66-69 --> — but (a) status 2 is only ever set under `COMPATIBLE_DECODE` <!-- anchor: detector.c:4156-4160 --> and the reader hard-codes `NORMAL_DECODE` <!-- anchor: jabreader.c:54 -->, and (b) even if reached, non-mobile `JAB_REPORT_INFO` is gated on `g_diag_verbose`, which the reader never sets ([03-public-surface-jabcode-h.md](03-public-surface-jabcode-h.md), §3.2). <!-- anchor: jabcode.h:67 -->
- On success the reader frees `bitmap` and `decoded_data` with plain `free()` — the correct ownership contract for both (§3.6.2, §3.6.6). <!-- anchor: jabreader.c:90-91 -->

## 11.7 `--help` exit codes

Neither tool exits 0 on `--help`:

- **Writer: exit 1.** The help path shares the failure return of `main`. <!-- anchor: jabwriter.c:435-439 -->
- **Reader: exit 255.** The help path shares the "not detectable" return. <!-- anchor: jabreader.c:27-31 -->

Any script that probes tool availability with `jabcodeWriter --help && ...` misclassifies a working installation as broken. This is long-standing observable behavior; treat the exit codes above as the contract.

## Known defects

| Defect | Evidence |
|---|---|
| `--help` exits non-zero (writer 1, reader 255) | jabwriter.c:435-439; jabreader.c:27-31 |
| Unknown writer flags silently ignored | jabwriter.c:69-247, 271-394 (no else clause) |
| `--ecc-level` accepts 0 while its error message says "must be 1 - 10" (0 is documented default-level semantics, but the message and check disagree) | jabwriter.c:303-306, 40-44 |
| Reader `--output` without a filename passes unchecked `argv[3]` to `fopen` | jabreader.c:36-37, 74 |
| Reader leaks `bitmap`/`decoded_data` on the unopenable-output path | jabreader.c:74-79 |
| Writer parse-failure path skips `cleanMemory` | jabwriter.c:440-443 |
| Partial-decode warning unreachable in the stock reader (NORMAL\_DECODE + verbose gate) | jabreader.c:54, 66-69; jabcode.h:67 |
| `jabwriter.h` is an empty file | src/jabcodeWriter/jabwriter.h |

---

The `jab_encode` fields these flags populate are specified in [03-public-surface-jabcode-h.md](03-public-surface-jabcode-h.md), §3.5; what the encoder does with them is [04-encoder.md](04-encoder.md). Operator-level walkthroughs: JC-U ch. 7-8.
