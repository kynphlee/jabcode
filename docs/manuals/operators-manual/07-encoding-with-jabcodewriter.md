# 7. Encoding with jabcodeWriter

<!-- objective: An operator can encode messages using every writer flag, reproducing three worked examples — an 8-colour default PNG, a 4-colour CMYK TIFF, and a two-symbol cascade — with correct flag syntax on the first attempt -->

**In this chapter you will** learn the complete flag surface of `jabcodeWriter` and reproduce three worked encodes: a default 8-colour PNG, a 4-colour CMYK TIFF, and a two-symbol cascade.

**You should already** have built the tools ([chapter 6](06-building-the-library.md)) and met the ideas the flags control: side-versions and ECC levels ([chapter 2](02-capacity-size-robustness.md)), cascading ([chapter 3](03-cascading.md)), and colour modes ([chapter 4](04-colour-modes-conformance.md)).

## The command shape

Every invocation follows one pattern, straight from the tool's usage text:

```text
jabcodeWriter --input message-to-encode --output output-image [options]
```

<!-- anchor: src/jabcodeWriter/jabwriter.c:30 -->

Two flags are mandatory in effect: the tool refuses to run without input data ("Input data missing") and without an output filename ("Output file missing"). Everything else has a default. <!-- anchor: src/jabcodeWriter/jabwriter.c:250-264 -->

## What to encode: `--input` and `--input-file`

You provide the payload one of two ways:

| Flag | Usage-text description | Behavior |
|---|---|---|
| `--input` | "Input data (message to be encoded)." | Takes the next argument as the message string. <!-- anchor: src/jabcodeWriter/jabwriter.c:32, 71-88 --> |
| `--input-file` | "Input data file." | Reads the whole file in binary mode — any byte content works, not just text. <!-- anchor: src/jabcodeWriter/jabwriter.c:33, 89-122 --> |

If you pass both, the one appearing **later** on the command line wins: each handler frees any previously stored payload before installing its own. Empty input is rejected ("Input data is empty"). <!-- anchor: src/jabcodeWriter/jabwriter.c:79, 105, 255-259 -->

## Where it goes: `--output` and `--color-space`

`--output` names the image file. What format lands on disk is decided by `--color-space`, whose usage text reads: "Color space of output image (0:RGB,1:CMYK,default:0). RGB image is saved as PNG and CMYK image as TIFF." Any value other than `0` or `1` is rejected ("Invalid color space (must be 0 or 1)."). <!-- anchor: src/jabcodeWriter/jabwriter.c:50-51, 123-131, 226-245 -->

A detail that will matter when you print: the symbol is always generated as an RGB bitmap; with `--color-space 1` the conversion to CMYK happens at save time inside `saveImageCMYK`, which converts the RGB bitmap and writes a TIFF. <!-- anchor: src/jabcodeWriter/jabwriter.c:494-500; src/jabcode/image.c:128-144 -->

## How dense: `--color-number`

Usage text: "Number of colors (2,4,8,16,32,64,128,256,default:8)." Exactly those eight values are accepted; anything else produces "Invalid color number. Supported color numbers are 2, 4, 8, 16, 32, 64, 128, 256." <!-- anchor: src/jabcodeWriter/jabwriter.c:35, 149-155 -->

Choose with your eyes open: only the 4- and 8-colour modes are ISO-standard; the source comment beside the validation marks `2` and `256` as fork extensions ("WS-0: Accept color\_number=2 (Nc=0, Mode 0 monochrome). WS-3: Accept color\_number=256 (Nc=7, max-density mode)."), and 16 through 256 occupy reserved territory. If your codes must be read by other people's software, [chapter 4](04-colour-modes-conformance.md) is the conformance map — read it before leaving 4 or 8. <!-- anchor: src/jabcodeWriter/jabwriter.c:147-148 -->

## How big: `--module-size`, `--symbol-width`, `--symbol-height`

| Flag | Usage-text description | Validation |
|---|---|---|
| `--module-size` | "Module size in pixel (default:12 pixels)." | Rejects negative values; `0` keeps the default. <!-- anchor: src/jabcodeWriter/jabwriter.c:36, 157-172 --> |
| `--symbol-width` | "Master symbol width in pixel." | Rejects negative values. <!-- anchor: src/jabcodeWriter/jabwriter.c:37, 173-188 --> |
| `--symbol-height` | "Master symbol height in pixel." | Rejects negative values. <!-- anchor: src/jabcodeWriter/jabwriter.c:38, 189-204 --> |

`--module-size` scales every module; the width/height pair instead fixes the master symbol's overall pixel footprint. All three are applied to the encoder only when positive, so omitting them keeps library defaults. <!-- anchor: src/jabcodeWriter/jabwriter.c:453-464 -->

## How robust: `--ecc-level`

The usage text is worth quoting in full, because it defines the per-symbol list syntax and the special value `0`:

> "Error correction levels (1-10, default:3(6%)). If different for each symbol, starting from master and then slave symbols (ecc0 ecc1 ecc2...). For master symbol, level 0 means using the default level, for slaves, it means using the same level as its host."

Values outside the range are rejected ("Invalid error correction level (must be 1 - 10)."). If you list fewer values than you have symbols, the remaining symbols keep `0` — that is, default-or-inherit. <!-- anchor: src/jabcodeWriter/jabwriter.c:40-44, 281-309 -->

What the levels mean in capacity-versus-recovery terms is [chapter 2](02-capacity-size-robustness.md)'s subject; [chapter 10](10-choosing-parameters.md) turns it into recipes.

## Cascading: `--symbol-number`, `--symbol-version`, `--symbol-position`

Three flags work together when one symbol is not enough (the *why* and the position map live in [chapter 3](03-cascading.md)):

| Flag | Usage-text description | Validation |
|---|---|---|
| `--symbol-number` | "Number of symbols (1-61, default:1)." | "Invalid symbol number (must be 1 - 61)." <!-- anchor: src/jabcodeWriter/jabwriter.c:39, 205-224 --> |
| `--symbol-version` | "Side-Version of each symbol, starting from master and then slave symbols (x0 y0 x1 y1 x2 y2...)." | Each side must be 1-32: "Invalid symbol side version (must be 1 - 32)." <!-- anchor: src/jabcodeWriter/jabwriter.c:45-46, 311-356 --> |
| `--symbol-position` | "Symbol positions (0-60), starting from master and then slave symbols (p0 p1 p2...). Only required for multi-symbol code." | Each position must be 0-60. <!-- anchor: src/jabcodeWriter/jabwriter.c:47-49, 358-393 --> |

The writer enforces three completeness rules after parsing:

- Position `0` is the master. The writer's own check ("Incorrect symbol position value for master symbol.") covers the single-symbol case; in multi-symbol codes the encoder reorders symbols by position and stops with "Master symbol missing" if no symbol claims position 0. <!-- anchor: src/jabcodeWriter/jabwriter.c:397-404; src/jabcode/encoder.c:2181-2200 -->
- With more than one symbol, you must supply a position for **every** symbol ("Symbol position information is incomplete for multi-symbol code"). <!-- anchor: src/jabcodeWriter/jabwriter.c:405-409 -->
- Likewise a version pair for every symbol ("Symbol version information is incomplete for multi-symbol code"). <!-- anchor: src/jabcodeWriter/jabwriter.c:410-414 -->

For a single symbol you may omit `--symbol-version` entirely — the library then picks the smallest side-version that fits your payload. <!-- anchor: src/jabcode/encoder.c:2340-2348 -->

## Exit codes

The writer's contract is simple: `0: success | 1: failure`. Every failure path — bad flag values, encode failure ("Creating jab code failed"), or image-save failure — exits 1. So does `--help`, and so does running with no arguments at all: the usage text prints, and the exit code is still 1. Script accordingly. <!-- anchor: src/jabcodeWriter/jabwriter.c:431, 435-443, 476-501 -->

## Worked examples

All three use only the documented option surface above. Run them with the built tools and `LD_LIBRARY_PATH` set as in [chapter 6](06-building-the-library.md).

### 1. Default 8-colour PNG

This is the writer's own built-in example:

```sh
jabcodeWriter --input 'Hello world' --output test.png
```

Expected behavior: with every option defaulted — 8 colours, module size 12, ECC level 3, one symbol, auto-sized version, RGB/PNG — the tool writes `test.png` and exits 0. <!-- anchor: src/jabcodeWriter/jabwriter.c:55 -->

### 2. 4-colour CMYK TIFF

A print-shop deliverable: ISO-standard 4-colour mode, a stronger ECC level, CMYK output.

```sh
jabcodeWriter --input-file payload.bin --output coa.tif --color-number 4 --ecc-level 5 --color-space 1
```

Expected behavior: the payload file is read whole in binary mode, and `coa.tif` is written as a CMYK TIFF (converted from the RGB bitmap at save time); exit 0. Keep in mind for verification that `jabcodeReader` reads PNG input — proof your parameters with a `--color-space 0` PNG copy first ([chapter 8](08-decoding-with-jabcodereader.md)). <!-- anchor: src/jabcodeWriter/jabwriter.c:89-122, 494-500 -->

### 3. Two-symbol cascade

The writer's usage text documents a three-symbol example (`--symbol-position 0 3 2 --symbol-version 3 2 4 2 3 2`). Taking its first two symbols — a valid docking chain, since the position-3 slave docks directly to the master — gives the minimal cascade: <!-- anchor: src/jabcodeWriter/jabwriter.c:58 -->

```sh
jabcodeWriter --input 'Hello world' --output cascade.png --symbol-number 2 --symbol-position 0 3 --symbol-version 3 2 4 2
```

Expected behavior: a two-symbol code is written to `cascade.png`, exit 0. Read the values as: master at position 0 with side-version pair 3 2; slave at position 3 with side-version pair 4 2. The two versions share the value 2 on the docked axis — docked sides must share a side-version, the rule from [chapter 3](03-cascading.md). Omit any position or version pair and the writer stops with one of the completeness errors quoted above. <!-- anchor: src/jabcodeWriter/jabwriter.c:397-414 -->

## Try it

1. What happens if you pass both `--input 'A'` and `--input-file b.bin` in that order?
2. Which colour counts are accepted, and which of them would you avoid for interchange with non-fork readers?
3. Write the flag list for a three-symbol cascade where the master uses ECC level 5 and both slaves inherit it.
4. Your script treats any non-zero exit as an error and reports a failure every time it runs `jabcodeWriter --help`. Why?

<details><summary>Answers</summary>

1. The file wins: `--input-file` appears later, and each input handler frees the previously stored payload before installing its own. <!-- anchor: src/jabcodeWriter/jabwriter.c:79, 105 -->
2. Accepted: 2, 4, 8, 16, 32, 64, 128, 256 (default 8). For interchange, stay with 4 or 8 — the only ISO-standard modes; 2 and 256 are marked in-source as fork extensions (WS-0/WS-3) and 16-256 sit in reserved territory (see [chapter 4](04-colour-modes-conformance.md)). <!-- anchor: src/jabcodeWriter/jabwriter.c:147-155 -->
3. `--symbol-number 3 --symbol-position 0 3 2 --symbol-version 3 2 4 2 3 2 --ecc-level 5 0 0` — per the usage text, level 0 for a slave "means using the same level as its host". <!-- anchor: src/jabcodeWriter/jabwriter.c:40-44, 58 -->
4. `--help` prints the usage text but returns 1, the writer's generic failure code — there is no distinct "help" exit status. <!-- anchor: src/jabcodeWriter/jabwriter.c:435-439 -->

</details>

## Where to go next

- Next: [chapter 8](08-decoding-with-jabcodereader.md) closes the loop — decoding your images and reading the exit codes correctly.
- Deeper: how the encoder chooses encoding modes, versions, and masks behind these flags is covered in the Developer's Manual (JC-T), forthcoming.
