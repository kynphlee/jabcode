# 8. Decoding with jabcodeReader

<!-- objective: An operator can decode an image, route output to a file, and interpret every exit code — 0 success, 255 not detectable, other non-zero decode-failure (with the partial-decode status-2 warning) — diagnosing a failing scan to the right cause class -->

**In this chapter you will** decode images with `jabcodeReader`, route the payload to stdout or a file, and — most importantly — learn to read its exit codes, which say more than "it failed".

**You should already** have built the tools ([chapter 6](06-building-the-library.md)) and produced at least one PNG with the writer ([chapter 7](07-encoding-with-jabcodewriter.md)).

## The command shape

The usage text is one line:

```text
jabcodeReader input-image(png) [--output output-file]
```

<!-- anchor: src/jabcodeReader/jabreader.c:14 -->

The input image is a positional argument — and note the "(png)" in the usage line: the image is loaded through `readImage`, which is PNG-based. A CMYK TIFF from [chapter 7](07-encoding-with-jabcodewriter.md)'s print example will not load; expect exit 255. Always keep an RGB/PNG proof copy for verification. <!-- anchor: src/jabcodeReader/jabreader.c:14, 47-49; src/jabcode/image.c:187-197 -->

## Routing the output

By default the decoded bytes print to stdout, followed by a newline. With `--output` ("Output file for decoded data.") the raw bytes are written to the named file instead — the right choice for binary payloads, since stdout appends that newline. <!-- anchor: src/jabcodeReader/jabreader.c:16, 72-88 -->

The argument order is strict, and this catches people: `--output` must be the argument **immediately after** the input image, and the filename must follow it. Anything else in that slot prints `Unknown parameter: ...` and exits 255. So:

```sh
jabcodeReader scan.png --output payload.bin    # correct
jabcodeReader --output payload.bin scan.png    # wrong — 'Unknown parameter'
```

<!-- anchor: src/jabcodeReader/jabreader.c:34-43, 74 -->

## Reading the exit codes

The reader's documented contract: `0: success | 255: not detectable | other non-zero: decoding failed`. <!-- anchor: src/jabcodeReader/jabreader.c:23 -->

Under the hood, the tool calls `decodeJABCodeEx(bitmap, NORMAL_DECODE, &decode_status, symbols, MAX_SYMBOL_NUMBER)`, and the library reports a status code whose meanings are documented at the API: "0: not detectable, 1: not decodable, 2: partly decoded with COMPATIBLE\_DECODE mode, 3: fully decoded". The exit code you see is derived from that status: <!-- anchor: src/jabcodeReader/jabreader.c:53-54; src/jabcode/detector.c:4060 -->

| Exit code | Meaning | How it arises |
|---|---|---|
| `0` | Success — payload delivered | Decode returned data. <!-- anchor: src/jabcodeReader/jabreader.c:92 --> |
| `255` | Not detectable (and all usage/I-O errors) | No arguments or `--help`; unknown parameter; image failed to load; decode failed with status 0; output file could not be opened. <!-- anchor: src/jabcodeReader/jabreader.c:27-31, 40-42, 48-49, 62, 75-79 --> |
| other non-zero | Decoding failed — a symbol **was** detected | Decode failed with status above 0; the exit code is the detected module size, rounded: `return (jab_int32)(symbols[0].module_size + 0.5f);` <!-- anchor: src/jabcodeReader/jabreader.c:59-60 --> |

That third row is the quirk to internalize: the "other non-zero" value is **not** a stable error enum. It is the apparent module size of the detected symbol in pixels, rounded to the nearest integer — a diagnostic smuggled through the exit status. An exit code of, say, 12 tells you a symbol was found whose modules measure about 12 pixels; do not build a lookup table of "error code 12 means X". Test only three classes: zero, 255, and everything else. <!-- anchor: src/jabcodeReader/jabreader.c:60 -->

## The partial-decode warning

When the library reports status 2, the reader emits "The code is only partly decoded. Some slave symbols have not been decoded and are ignored." — and still delivers the partial payload with exit 0. The word *emits* carries a caveat: the message goes through `JAB_REPORT_INFO`, which this fork gates behind diagnostic verbosity (off by default, and the reader never switches it on), so the text does not actually reach your terminal. <!-- anchor: src/jabcodeReader/jabreader.c:66-69; src/jabcode/include/jabcode.h:67, 90 -->

An honest footnote for this tree: status 2 is only ever set by the library in `COMPATIBLE_DECODE` mode, and the shipped reader always calls `NORMAL_DECODE` — in normal mode, a cascade with undecodable slaves fails outright instead. So with this exact tool you should not expect the warning at all; it is a defensive path that becomes live for API users who choose `COMPATIBLE_DECODE` and enable verbosity with `jabSetDiagVerbose(1)` ([chapter 9](09-embedding-the-c-api.md)). <!-- anchor: src/jabcode/detector.c:4156-4160; src/jabcodeReader/jabreader.c:54; src/jabcode/include/jabcode.h:255 -->

## Diagnosing a failing scan

The two failure classes point at different culprits — use the exit code to pick your repair path:

- **255 — "not detectable."** Either the file never loaded (wrong path, not a PNG) or no finder patterns were found: the symbol is absent, cropped, or captured too small. Rule out the file first, then revisit capture geometry — [chapter 5](05-printing-and-scanning.md) covers module-size-at-capture and print quality, and [chapter 3](03-cascading.md) states the five-pixels-per-module scanning rule.
- **Other non-zero — "detected but not decodable."** The geometry was good enough to find and measure the symbol (you even got its module size as the exit code), but the content did not survive: colour classification, damage beyond the ECC margin, or palette trouble. Revisit lighting and print fidelity ([chapter 5](05-printing-and-scanning.md)) and the ECC level you encoded with ([chapter 2](02-capacity-size-robustness.md)); the detection-and-decode internals live in the Developer's Manual.

<!-- anchor: src/jabcodeReader/jabreader.c:23, 59-62; src/jabcode/detector.c:4060 -->

## Worked example: round trip with both output routes

Using `hello.png` from [chapter 7](07-encoding-with-jabcodewriter.md)'s first example (and `LD_LIBRARY_PATH` set per [chapter 6](06-building-the-library.md)):

```sh
jabcodeReader hello.png
echo $?

jabcodeReader hello.png --output decoded.bin
echo $?
```

Expected behavior: the first command prints the decoded message bytes to stdout followed by a newline and exits 0. The second writes exactly the payload bytes (no trailing newline) to `decoded.bin` and exits 0. Then break it on purpose: pass a filename that does not exist and confirm you get exit 255 — that is the "not detectable" class doing double duty for I/O errors. <!-- anchor: src/jabcodeReader/jabreader.c:47-49, 80-88, 92 -->

## Try it

1. A batch job logs exit code 9 for one image. What do you now know about that scan, and what class of fix do you pursue?
2. Why does `jabcodeReader --output out.bin scan.png` fail, and with what exit code?
3. Your payload is binary. Why is `--output` the only safe route?
4. Under what conditions would the "only partly decoded" warning actually appear with the shipped reader binary?

<details><summary>Answers</summary>

1. A symbol **was** detected — its modules measure about 9 pixels — but decoding failed. Pursue the content class: print/scan fidelity and ECC margin ([chapter 5](05-printing-and-scanning.md), [chapter 2](02-capacity-size-robustness.md)), not detection geometry. <!-- anchor: src/jabcodeReader/jabreader.c:59-60 -->
2. The reader treats the first argument as the input image and checks the **second** argument against the literal `--output`. Here the second argument is `out.bin`, so it prints `Unknown parameter: out.bin` and exits 255 before ever trying to load an image. <!-- anchor: src/jabcodeReader/jabreader.c:34-42 -->
3. Stdout output appends a newline after the payload bytes; `--output` writes the raw bytes only. <!-- anchor: src/jabcodeReader/jabreader.c:80-88 -->
4. Effectively never — for two independent reasons: status 2 is only set in `COMPATIBLE_DECODE` mode while the shipped reader always decodes with `NORMAL_DECODE`; and the message itself goes through `JAB_REPORT_INFO`, which this fork gates behind diagnostic verbosity (off by default, never enabled by the reader). It appears only for API callers who pass `COMPATIBLE_DECODE` *and* call `jabSetDiagVerbose(1)`. <!-- anchor: src/jabcodeReader/jabreader.c:54, 66-69; src/jabcode/detector.c:4156-4160; src/jabcode/include/jabcode.h:67, 255 -->

</details>

## Where to go next

- Next: [chapter 9](09-embedding-the-c-api.md) moves from the CLI to the C API — the same encode and decode, five calls, in your own program.
- Deeper: the detection pipeline behind "not detectable" (binarization, finder-pattern search, sampling) is covered in the Developer's Manual (JC-T), forthcoming.
