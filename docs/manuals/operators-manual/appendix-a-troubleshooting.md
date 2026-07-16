# Appendix A. Troubleshooting matrix

Reference only — find the section for what you were doing, match the symptom, apply the fix. Every row cites its source anchor; error strings are quoted verbatim. Concept-level background lives in the chapters each row links to.

## A.1 Building the library and tools

| Symptom | Likely cause | Fix |
|---|---|---|
| Link fails: `cannot find -ltiff` (or `-lpng16`, `-lz`) | The prebuilt archives in `src/jabcode/lib/` are absent from this working tree, so `-L../jabcode/lib` resolves nothing and the linker needs system libraries | Install your distribution's libpng16, libtiff and zlib development packages ([chapter 6](06-building-the-library.md)) <!-- anchor: src/jabcodeWriter/Makefile:10; docs/manuals/corpus-model.md §1.3 --> |
| Tools build but fail to start: loader cannot find `libjabcode.so` | `-ljabcode` resolved to the shared library; the run-time loader has no path to it | `export LD_LIBRARY_PATH="$PWD/src/jabcode/build"` from the repo root ([chapter 6](06-building-the-library.md)) <!-- anchor: src/jabcode/Makefile:35-41 --> |
| `check-lib: missing ../../lib/libjabcode.so`, exit 1 | Repo-root `lib/` (`VENDORED_DIR := ../../lib`) does not exist in this working tree | Create `lib/` at the repo root, then `make -C src/jabcode refresh-lib`; skip both targets entirely if you only use the CLI tools <!-- anchor: src/jabcode/Makefile:18, 62; docs/manuals/corpus-model.md §6 NOT FOUND register --> |
| `refresh-lib` fails at its `cp` step | Same root cause: the destination `../../lib/` is missing | Same fix — create the directory first <!-- anchor: src/jabcode/Makefile:49-50 --> |
| Smoke test treats `--help` as a failure | By design: the writer's `--help` exits 1 and the reader's exits 255 — there is no zero "help" status | Test artifacts, not help exit codes ([chapter 6](06-building-the-library.md)) <!-- anchor: src/jabcodeWriter/jabwriter.c:435-439; src/jabcodeReader/jabreader.c:27-31 --> |

## A.2 Encoding with jabcodeWriter

The writer exits `0: success | 1: failure`; every row below is an exit-1 path with its exact message. <!-- anchor: src/jabcodeWriter/jabwriter.c:431 -->

| Error message (verbatim) | Likely cause | Fix |
|---|---|---|
| `Input data missing` / `Input data is empty` / `Output file missing` | Neither `--input` nor `--input-file` given; empty payload; no `--output` | Supply the two effectively-mandatory flags ([chapter 7](07-encoding-with-jabcodewriter.md)) <!-- anchor: src/jabcodeWriter/jabwriter.c:250-264 --> |
| `Invalid color number. Supported color numbers are 2, 4, 8, 16, 32, 64, 128, 256.` | `--color-number` outside the accepted set | Pick from the set; stay on 4 or 8 for interchange ([chapter 4](04-colour-modes-conformance.md)) <!-- anchor: src/jabcodeWriter/jabwriter.c:149-155 --> |
| `Invalid symbol number (must be 1 - 61).` | `--symbol-number` outside 1-61 | Cascades top out at 61 symbols ([chapter 3](03-cascading.md)) <!-- anchor: src/jabcodeWriter/jabwriter.c:220-224 --> |
| `Invalid error correction level (must be 1 - 10).` | An `--ecc-level` value below 0 or above 10 | Use 1-10, or 0 for default/inherit ([chapter 7](07-encoding-with-jabcodewriter.md)) <!-- anchor: src/jabcodeWriter/jabwriter.c:303-307 --> |
| `Invalid symbol side version (must be 1 - 32).` | A `--symbol-version` axis value outside 1-32 | Side-versions run 1-32 (21-145 modules) ([chapter 2](02-capacity-size-robustness.md)) <!-- anchor: src/jabcodeWriter/jabwriter.c:350-354 --> |
| `Invalid symbol position value (must be 0 - 60).` | A `--symbol-position` outside 0-60 | Use the 61-position map from [chapter 3](03-cascading.md) <!-- anchor: src/jabcodeWriter/jabwriter.c:386-390 --> |
| `Incorrect symbol position value for master symbol.` | Single-symbol run gave the master a nonzero position | The master always takes position 0. In multi-symbol codes this check moves to the encoder, which reorders by position and reports `Master symbol missing` instead <!-- anchor: src/jabcodeWriter/jabwriter.c:397-403; src/jabcode/encoder.c:2181-2200 --> |
| `Symbol position information is incomplete for multi-symbol code` | Fewer positions than `--symbol-number` symbols | Supply one position per symbol <!-- anchor: src/jabcodeWriter/jabwriter.c:405-409 --> |
| `Symbol version information is incomplete for multi-symbol code` | Fewer version pairs than symbols | Supply one `x y` pair per symbol <!-- anchor: src/jabcodeWriter/jabwriter.c:410-414 --> |
| `Invalid color space (must be 0 or 1).` | `--color-space` other than 0 (RGB/PNG) or 1 (CMYK/TIFF) | Use 0 or 1 ([chapter 7](07-encoding-with-jabcodewriter.md)) <!-- anchor: src/jabcodeWriter/jabwriter.c:241-245 --> |
| `Creating jab code failed` | The encode itself failed after valid flags | Payload versus capacity mismatch is the first suspect — recheck against [chapter 2](02-capacity-size-robustness.md) <!-- anchor: src/jabcodeWriter/jabwriter.c:476-479 --> |

## A.3 Decoding with jabcodeReader

The reader's contract: `0: success | 255: not detectable | other non-zero: decoding failed`. <!-- anchor: src/jabcodeReader/jabreader.c:23 -->

| Symptom | Likely cause | Fix |
|---|---|---|
| Exit 255, message `Unknown parameter: ...` | Argument order: `--output` must be the argument immediately after the input image | `jabcodeReader scan.png --output out.bin` — image first ([chapter 8](08-decoding-with-jabcodereader.md)) <!-- anchor: src/jabcodeReader/jabreader.c:34-42 --> |
| Exit 255, no decode output | File never loaded (wrong path, or not a PNG — `readImage` is PNG-based, so a CMYK TIFF will not load) **or** no symbol detected: absent, cropped, or captured too small | Rule out the file first; then capture geometry — the five-pixels-per-module rule ([chapter 3](03-cascading.md)) and print/scan quality ([chapter 5](05-printing-and-scanning.md)) <!-- anchor: src/jabcodeReader/jabreader.c:47-49, 62 --> |
| Exit code is some other small number (7, 12, 19...) | A symbol **was** detected but decoding failed; the exit code is the detected module size, rounded: `return (jab_int32)(symbols[0].module_size + 0.5f);` — a diagnostic, not an error enum | Pursue content fixes: lighting and print fidelity ([chapter 5](05-printing-and-scanning.md)), ECC margin ([chapter 2](02-capacity-size-robustness.md)). Never build a lookup table of these codes — script only the three classes 0 / 255 / other <!-- anchor: src/jabcodeReader/jabreader.c:59-60 --> |
| Exit 0 but the payload is incomplete (partial cascade) | Library decode status 2 — only reachable in `COMPATIBLE_DECODE` mode (API callers; the shipped reader uses `NORMAL_DECODE`); its warning text is additionally gated behind diagnostic verbosity, so expect silence | Treat the payload as incomplete; see [chapter 8](08-decoding-with-jabcodereader.md) and [chapter 9](09-embedding-the-c-api.md) <!-- anchor: src/jabcodeReader/jabreader.c:54, 66-69; src/jabcode/include/jabcode.h:67 --> |
| Exit 255, message `Can not open the output file` | `--output` path not writable | Fix the destination path or permissions <!-- anchor: src/jabcodeReader/jabreader.c:74-79 --> |
| Binary payload corrupted after stdout capture | Stdout appends a newline after the payload bytes | Use `--output` for binary payloads ([chapter 8](08-decoding-with-jabcodereader.md)) <!-- anchor: src/jabcodeReader/jabreader.c:80-88 --> |

## A.4 Scanning and printing in the field

These rows route physical-world symptoms to the chapters that carry the operational guidance — the criteria themselves are already taught there, so this table only points.

| Symptom | Likely cause | Where the fix lives |
|---|---|---|
| Symbol physically present, reader says not detectable (255) | Captured too few pixels per module (distance, resolution, small print) | Five-pixels-per-module rule in [chapter 3](03-cascading.md); module-size dial in [chapter 10](10-choosing-parameters.md) |
| Detected but never decodes (module-size exit codes) | Colour degradation past the ECC margin: fading, poor lighting, press variability | Print/scan guidance in [chapter 5](05-printing-and-scanning.md); ECC selection in [chapter 2](02-capacity-size-robustness.md) and [chapter 10](10-choosing-parameters.md) |
| Reads on your bench, fails under warehouse or retail lighting | Inconsistent illumination shifting colour classification | Lighting guidance (consistent illumination, 6500k recommendation) in [chapter 5](05-printing-and-scanning.md) and the print-and-scan checklist in [chapter 10](10-choosing-parameters.md) |
| Reads with this repo's tools, fails on third-party readers | Non-standard colour mode: 2-colour is fork-only; 16-256 are reserved-Nc extensions | Conformance map in [chapter 4](04-colour-modes-conformance.md) |
| CMYK-printed code cannot be verified with `jabcodeReader` | The reader loads PNG; the TIFF is a press deliverable, not a verification input | Proof with an identical-parameter PNG copy first — worked example in [chapter 10](10-choosing-parameters.md), reader note in [chapter 8](08-decoding-with-jabcodereader.md) |

## A.5 Service-side (jab-auth) quick rows

| Symptom | Likely cause | Fix |
|---|---|---|
| Every generated symbol is a blank 100 by 100 image; decode returns `stub-decoded-data` | `PanamaJabCodeService` fell back to its stub at startup — wrapper jar or `libjabcode.so` not loadable | Check startup logs for the "Falling back to stub implementation" warnings; see [chapter 11](11-service-binding-chain.md) <!-- anchor: PanamaJabCodeService.java:74-91, 402-404, 133 --> |
| Framework build fails: `libjabcode.so is STALE or MISMATCHED — provenance assertion failed` | The vendored `.so` no longer matches the SHA-256 in `libjabcode.so.provenance` | Deliberate re-vendor: regenerate the provenance record; otherwise restore the certified binary ([chapter 11](11-service-binding-chain.md)) <!-- anchor: jab-auth-jabcode/build.gradle:73-83 --> |
| REST symbols are 4-colour though "the default is 8" | The service pins `JabCodeConfig.defaultConfig()` (4-colour, ECC 3), not the library default | Expected behavior — defaults divergence table in [chapter 11](11-service-binding-chain.md) <!-- anchor: JabCodeService.java:50-52 --> |
