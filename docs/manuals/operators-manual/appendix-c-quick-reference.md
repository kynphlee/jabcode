# Appendix C. Quick-reference card

Pure reference. Values are quoted from source or the ISO extract; each table cites its anchor. Task context and explanations live in the chapters.

## C.1 jabcodeWriter flags

Usage: `jabcodeWriter --input message-to-encode --output output-image [options]` — exit `0: success | 1: failure` (note: `--help` also exits 1). <!-- anchor: src/jabcodeWriter/jabwriter.c:30, 431, 435-439 -->

| Flag | Values / rules | Default |
|---|---|---|
| `--input` | message string; required unless `--input-file` given | — <!-- anchor: src/jabcodeWriter/jabwriter.c:71, 250 --> |
| `--input-file` | payload file, read whole in binary mode | — <!-- anchor: src/jabcodeWriter/jabwriter.c:89 --> |
| `--output` | output image file; required | — <!-- anchor: src/jabcodeWriter/jabwriter.c:123, 260 --> |
| `--color-number` | 2, 4, 8, 16, 32, 64, 128, 256 (ISO-standard: 4 and 8 only) | 8 <!-- anchor: src/jabcodeWriter/jabwriter.c:132, 149-155 --> |
| `--module-size` | module size in pixels; rejects negative | 12 <!-- anchor: src/jabcodeWriter/jabwriter.c:157, 167 --> |
| `--symbol-width` | master symbol width in pixels; rejects negative | auto <!-- anchor: src/jabcodeWriter/jabwriter.c:173 --> |
| `--symbol-height` | master symbol height in pixels; rejects negative | auto <!-- anchor: src/jabcodeWriter/jabwriter.c:189 --> |
| `--symbol-number` | 1-61 | 1 <!-- anchor: src/jabcodeWriter/jabwriter.c:205, 220-224 --> |
| `--ecc-level` | 1-10; per-symbol list (master first); 0 = default (master) / inherit from host (slave) | 3, glossed "3(6%)" <!-- anchor: src/jabcodeWriter/jabwriter.c:273, 303-307, 40-44 --> |
| `--symbol-version` | `x y` pair per symbol, each 1-32; required for every symbol when `symbol-number` > 1 | auto (single symbol) <!-- anchor: src/jabcodeWriter/jabwriter.c:311, 350-354, 410 --> |
| `--symbol-position` | 0-60 per symbol; one symbol must hold position 0 (writer checks this for single-symbol runs; otherwise the encoder enforces it as `Master symbol missing`); required complete when `symbol-number` > 1 | — <!-- anchor: src/jabcodeWriter/jabwriter.c:358, 386-390, 397-405; src/jabcode/encoder.c:2181-2200 --> |
| `--color-space` | 0 = RGB, saved as PNG; 1 = CMYK, saved as TIFF | 0 <!-- anchor: src/jabcodeWriter/jabwriter.c:226, 241-245 --> |
| `--help` | prints usage; exits 1 | — <!-- anchor: src/jabcodeWriter/jabwriter.c:435 --> |

## C.2 jabcodeReader usage and exit codes

Usage: `jabcodeReader input-image(png) [--output output-file]` — `--output` must immediately follow the input image. <!-- anchor: src/jabcodeReader/jabreader.c:14, 34-42 -->

| Exit code | Meaning |
|---|---|
| `0` | Success — payload to stdout (plus trailing newline) or raw to `--output` file <!-- anchor: src/jabcodeReader/jabreader.c:80-88, 92 --> |
| `255` | Not detectable — also all usage, unknown-parameter and file I/O errors <!-- anchor: src/jabcodeReader/jabreader.c:23, 27-49, 62, 75-79 --> |
| other non-zero | Decoding failed on a **detected** symbol; the value is the detected module size in pixels, rounded — a diagnostic, not an error enum <!-- anchor: src/jabcodeReader/jabreader.c:23, 59-60 --> |

Status-2 partial cascades carry the warning "The code is only partly decoded. Some slave symbols have not been decoded and are ignored." — reachable in `COMPATIBLE_DECODE` only, and printed only with diagnostic verbosity enabled; the shipped reader uses `NORMAL_DECODE` with verbosity off. <!-- anchor: src/jabcodeReader/jabreader.c:54, 66-69; src/jabcode/include/jabcode.h:67 -->

## C.3 Library defaults

| Macro | Value |
|---|---|
| `DEFAULT_SYMBOL_NUMBER` | `1` <!-- anchor: src/jabcode/include/jabcode.h:31 --> |
| `DEFAULT_MODULE_SIZE` | `12` <!-- anchor: src/jabcode/include/jabcode.h:32 --> |
| `DEFAULT_COLOR_NUMBER` | `8` <!-- anchor: src/jabcode/include/jabcode.h:33 --> |
| `DEFAULT_MODULE_COLOR_MODE` | `2` <!-- anchor: src/jabcode/include/jabcode.h:34 --> |
| `DEFAULT_ECC_LEVEL` | `3` <!-- anchor: src/jabcode/include/jabcode.h:35 --> |
| `DEFAULT_MASKING_REFERENCE` | `7` <!-- anchor: src/jabcode/include/jabcode.h:36 --> |

## C.4 Size formula

`VERSION2SIZE(x)` is `(x * 4 + 17)`; inverse `SIZE2VERSION(x)` is `((x - 17) / 4)`. Side-versions 1-32 give 21-145 modules per side. <!-- anchor: src/jabcode/include/jabcode.h:53-54 -->

| Side-version | 1 | 6 | 10 | 20 | 32 |
|---|---|---|---|---|---|
| Modules per side | 21 | 41 | 57 | 97 | 145 |

## C.5 ECC levels

Recovery percentages from the ISO extract; `(wc, wr)` pairs and code rates quoted from the encoder tables (the two sources agree pair-for-pair).

| Level | Recovery | `(wc, wr)` | Code rate |
|---|---|---|---|
| 1 | 4% | `{3, 8}` | `0.63` |
| 2 | 5% | `{3, 7}` | `0.57` |
| 3 (default) | 6% | `{4, 9}` | `0.55` |
| 4 | 7% | `{3, 6}` | `0.50` |
| 5 | 8% | `{4, 7}` | `0.43` |
| 6 | 9% | `{4, 6}` | `0.34` |
| 7 | 10% | `{3, 4}` | `0.25` |
| 8 | 11% | `{4, 5}` | `0.20` |
| 9 | 12% | `{5, 6}` | `0.17` |
| 10 | 14% | `{6, 7}` | `0.14` |

<!-- anchor: ISO 23634 Table 20 -->
<!-- anchor: src/jabcode/encoder.h:234 (wcwr), 226 (code rates; index 1..10) -->

## C.6 Capacity extract — 8-colour primary symbol, net payload Pn

| Side-version | Modules per side | Pn (bits) | ≈ bytes |
|---|---|---|---|
| 1 | 21 | 1047 | ≈ 130 |
| 10 | 57 | 9219 | ≈ 1152 |
| 20 | 97 | 27279 | ≈ 3409 |
| 32 | 145 | 61182 | ≈ 7647 |

<!-- anchor: ISO 23634 Table 1 -->

Byte figures are derived (bits / 8, rounded down). Full capacity reasoning: [chapter 2](02-capacity-size-robustness.md).

## C.7 Colour modes at a glance

Accepted counts 2, 4, 8, 16, 32, 64, 128, 256 = `2^(Nc+1)`, Nc 0-7. ISO-standard: **4 and 8 only**; 16-256 are reserved-Nc extensions; 2-colour is fork-only. Details: [chapter 4](04-colour-modes-conformance.md). <!-- anchor: src/jabcode/include/jabcode.h:105; src/jabcodeWriter/jabwriter.c:147-155 -->

## C.8 Service endpoints

`POST /api/jabcode/generate` (API key) and its siblings reach this same codec through the jab-auth service, pinned to 4-colour/ECC-3 service defaults — endpoint table, auth modes and the full binding chain in [chapter 11](11-service-binding-chain.md); knob reachability in [chapter 12](12-service-vs-sdk-configuration.md). <!-- anchor: JABCodeCOA-crypto corpus §3.9 -->
