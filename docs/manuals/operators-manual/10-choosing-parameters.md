# 10. Choosing parameters well

<!-- objective: An operator can map four given use cases (durable industrial label, high-capacity document seal, phone-screen ticket, CMYK-printed COA) to a complete parameter set — colour count, ECC level, module size, symbol count — and justify each choice in one sentence -->

**In this chapter you will** turn the concepts of chapters 2 through 5 into decisions: a defaults-first strategy, four tuning dials, and four ready-made recipes you can adapt.

**You should already** understand capacity, side-versions and ECC ([chapter 2](02-capacity-size-robustness.md)), cascading ([chapter 3](03-cascading.md)), colour-mode conformance ([chapter 4](04-colour-modes-conformance.md)), print and scan realities ([chapter 5](05-printing-and-scanning.md)), and the writer flags ([chapter 7](07-encoding-with-jabcodewriter.md)).

## Start from the defaults — they are the safe baseline

Every parameter you do not set falls back to a library default, quoted here verbatim from the public header:

| Macro | Value |
|---|---|
| `DEFAULT_SYMBOL_NUMBER` | `1` <!-- anchor: src/jabcode/include/jabcode.h:31 --> |
| `DEFAULT_MODULE_SIZE` | `12` <!-- anchor: src/jabcode/include/jabcode.h:32 --> |
| `DEFAULT_COLOR_NUMBER` | `8` <!-- anchor: src/jabcode/include/jabcode.h:33 --> |
| `DEFAULT_MODULE_COLOR_MODE` | `2` <!-- anchor: src/jabcode/include/jabcode.h:34 --> |
| `DEFAULT_ECC_LEVEL` | `3` <!-- anchor: src/jabcode/include/jabcode.h:35 --> |
| `DEFAULT_MASKING_REFERENCE` | `7` <!-- anchor: src/jabcode/include/jabcode.h:36 --> |

A bare `jabcodeWriter --input ... --output out.png` therefore produces one 8-colour symbol, modules 12 pixels square, ECC level 3, auto-sized to the smallest side-version that fits your payload. Deviate only when a requirement pushes you off this baseline — each dial below tells you which requirement that is. (The masking reference has no writer flag; it is a library-level default.) <!-- anchor: src/jabcode/encoder.c:2340-2348 -->

## Dial 1 — colour count: density versus interchange

Each module carries `log2(colour count)` bits, so 8 colours give 3 bits per module where 4 colours give 2 — a 50 percent density premium for the default. The full accepted set is 2, 4, 8, 16, 32, 64, 128, 256, but the conformance line from [chapter 4](04-colour-modes-conformance.md) is the one to respect: **only 4 and 8 are ISO-standard**; 16 through 256 are reserved-Nc extensions and 2-colour is a fork-only mode. <!-- anchor: docs/manuals/corpus-model.md §4 (jabcode.h:24-45); src/jabcode/include/jabcode.h:105; src/jabcodeWriter/jabwriter.c:147-155 -->

- Raise to a high-colour extension only inside a closed loop where you control every reader.
- Drop to 4 when the channel degrades colour — print processes, fading, difficult lighting ([chapter 5](05-printing-and-scanning.md)).

The standard frames this dial with exactly three criteria: "The selection of module colours should be determined in relation to: — the required data payload, according to the application requirements; — the expected symbol size, according to the application requirements; — the capability of the technologies used to produce and scan the symbol." The first two you can compute from [chapter 2](02-capacity-size-robustness.md); the third — what your printers and readers can actually reproduce and distinguish — only you can answer, and it is the criterion that most often forces the drop from 8 to 4. <!-- anchor: ISO 23634 Annex A.1 -->

## Dial 2 — ECC level: capacity versus damage margin

ECC levels run 1 to 10; the source comment fixes the frame: "Per ISO/IEC 23634:2022 Table 20. ECC levels run 1..10 (default 3)". Each level selects an LDPC `(wc, wr)` pair, and the header pairs it with an approximate code rate — the fraction of coded bits that carry your data, so lower rate means more redundancy. Selected rows, values quoted from `ecclevel2wcwr` and `ecclevel2coderate`: <!-- anchor: src/jabcode/encoder.h:226, 230, 234 -->

| Level | `(wc, wr)` | Code rate | Reading |
|---|---|---|---|
| 1 | `{3, 8}` | `0.63` | lightest protection, most capacity <!-- anchor: src/jabcode/encoder.h:226, 234 --> |
| 3 (default) | `{4, 9}` | `0.55` | the writer's usage text glosses it "3(6%)" — roughly 6 percent bit recovery <!-- anchor: src/jabcode/encoder.h:226, 234; src/jabcodeWriter/jabwriter.c:40 --> |
| 5 | `{4, 7}` | `0.43` | moderate hardening <!-- anchor: src/jabcode/encoder.h:226, 234 --> |
| 7 | `{3, 4}` | `0.25` | heavy hardening — a quarter of the bits are payload <!-- anchor: src/jabcode/encoder.h:226, 234 --> |
| 10 | `{6, 7}` | `0.14` | maximum redundancy <!-- anchor: src/jabcode/encoder.h:226, 234 --> |

The trade is mechanical: every step up in level shrinks net capacity at a fixed symbol size, or grows the symbol at fixed capacity. [Chapter 2](02-capacity-size-robustness.md) quantifies what each recovery margin buys you.

One rule from the standard turns this dial on its head. For the common case it advises that "The recommended error correction level for normal use should be set as the default level" — the defaults-first strategy above. But: "If the symbol size is fixed in the application, regardless of the message length, the highest possible error correction level should be used that achieve the best robustness." So when the label footprint is fixed and your payload leaves capacity to spare, do not leave that capacity idle — spend all of it on ECC. <!-- anchor: ISO 23634 Annex A.2 -->

## Dial 3 — module size: pixels on the page and in the camera

`--module-size` (default 12 pixels) sets how large each module renders. It changes nothing about capacity — only physical robustness: bigger modules survive print imperfections and, at scan time, keep you clear of the five-pixels-per-module capture rule from [chapter 3](03-cascading.md). Raise it for small print, distant capture, or degraded surfaces; the cost is physical footprint. <!-- anchor: src/jabcode/include/jabcode.h:32; src/jabcodeWriter/jabwriter.c:36 -->

## Dial 4 — symbols and versions: one big, or several docked

Side-versions run 1 to 32, giving 21 to 145 modules per side (`VERSION2SIZE(x)` is `(x * 4 + 17)`). For a single symbol, leave `--symbol-version` unset and the encoder auto-picks the smallest fit. Reach for cascading — up to `MAX_SYMBOL_NUMBER` (61) docked symbols — when the payload outgrows one symbol or the physical space is the wrong shape for one square; [chapter 3](03-cascading.md) has the position map and docking rules, and [chapter 7](07-encoding-with-jabcodewriter.md) the flag syntax. <!-- anchor: src/jabcode/include/jabcode.h:24, 53; src/jabcode/encoder.c:2340-2348 -->

The standard draws this dial's boundary sharply. Cascading "increases the reading complexity of JAB Code, and may consequently decrease decoding reliability. Therefore, symbol cascading should only be used in the following cases: — the data message cannot be accommodated by a single primary symbol; — the available space to place the code has an irregular shape, which cannot be fully utilized by a single square or rectangle symbol, — small symbols (small side-version) are preferred due to the application requirements." If your situation is not one of those three, one symbol is the right answer — and for a non-square space, note that "rectangle symbols can be used to accommodate more data than square symbols by making the most of the available space" before you reach for a cascade at all. <!-- anchor: ISO 23634 Annex A.3 -->

## Before the press run: the standard's print-and-scan checklist

Annex A closes with system-level guidance worth running down once your four dials are set — "Any JAB Code application is intended to be viewed as a total system solution." The bullets, with their operative phrases quoted: <!-- anchor: ISO 23634 Annex A.4 -->

- Print density: "Ensure that the module dimension is an integer multiple of the print head pixel dimension" — the parameter-time face of Dial 3's `--module-size`.
- Reader match: "Choose a reader with a resolution compatible with the symbol density and quality produced by the printing technology."
- Optics: "Ensure that the optical properties of the printed symbol are compatible with the wavelength of the scanner light source or sensor."
- Lighting: "Ensure that the lighting condition is consistent over the whole symbol when scanning the printed symbol. A colour temperature of 6500k for the lighting is recommended."
- Final configuration: "Verify symbol compliance in the final label or package configuration. Overlays, show-through, and curved or irregular surfaces can all affect symbol readability."

[Chapter 5](05-printing-and-scanning.md) carries the operational side of these points; this checklist is the parameter-selection version — settle module size, colour count and ECC against it before a single label is printed.

## Four recipes

Each recipe is a full parameter set with a one-sentence justification per choice. Adapt the payload and filenames; the flags are the documented surface from [chapter 7](07-encoding-with-jabcodewriter.md).

### Durable industrial label

```sh
jabcodeWriter --input-file label.txt --output label.png \
    --color-number 4 --ecc-level 7 --module-size 16
```

- 4 colours: the ISO-standard mode that keeps colour classification easiest when the label abrades and fades ([chapter 5](05-printing-and-scanning.md), [chapter 4](04-colour-modes-conformance.md)).
- ECC 7: code rate `0.25` trades most of the capacity for damage margin, the right side of the trade for a small payload on a hard life ([chapter 2](02-capacity-size-robustness.md)). <!-- anchor: src/jabcode/encoder.h:226 -->
- Module size 16: extra pixels per module keep a worn, obliquely scanned label above the five-pixels-per-module capture rule ([chapter 3](03-cascading.md)).
- One symbol: nothing here needs cascade geometry.

### High-capacity document seal

```sh
jabcodeWriter --input-file seal-payload.bin --output seal.png
```

- 8 colours (default): 3 bits per module is the densest ISO-standard mode ([chapter 4](04-colour-modes-conformance.md)).
- ECC 3 (default): a controlled document channel does not justify paying capacity for extra recovery ([chapter 2](02-capacity-size-robustness.md)).
- Auto version: the encoder sizes the symbol to the payload — [chapter 2](02-capacity-size-robustness.md)'s capacity tables tell you how far one symbol stretches before you cascade with `--symbol-number` ([chapter 3](03-cascading.md)). <!-- anchor: src/jabcode/encoder.c:2340-2348 -->

### Phone-screen ticket

```sh
jabcodeWriter --input 'TICKET-2026-000123' --output ticket.png \
    --ecc-level 4
```

- 8 colours (default): an emissive screen presents stable, saturated colour, so the dense default is safe ([chapter 5](05-printing-and-scanning.md)).
- ECC 4: code rate `0.50`, one notch of margin over the default for handheld camera capture — reflections, angle, motion. <!-- anchor: src/jabcode/encoder.h:226 -->
- Module size 12 (default): screens render exact pixels; ensure the displayed size keeps captured modules above five pixels ([chapter 3](03-cascading.md)).

### CMYK-printed certificate of authenticity

```sh
jabcodeWriter --input-file coa.json --output coa.tif \
    --color-number 4 --ecc-level 5 --color-space 1
```

- 4 colours: the CMYK-friendly ISO-standard mode for press output ([chapter 5](05-printing-and-scanning.md)).
- ECC 5: code rate `0.43` absorbs press-run variability without the footprint cost of level 7. <!-- anchor: src/jabcode/encoder.h:226 -->
- `--color-space 1`: "RGB image is saved as PNG and CMYK image as TIFF" — this is the flag that delivers press-ready CMYK. <!-- anchor: src/jabcodeWriter/jabwriter.c:50-51 -->

## Worked example: the COA, end to end

The COA recipe, with the verification step that catches parameter mistakes before the press run. Recall from [chapter 8](08-decoding-with-jabcodereader.md) that `jabcodeReader` reads PNG, so you proof with an RGB copy of the identical parameter set:

```sh
# 1. Proof copy (identical parameters, PNG output)
jabcodeWriter --input-file coa.json --output coa-proof.png \
    --color-number 4 --ecc-level 5 --color-space 0

# 2. Verify the round trip
jabcodeReader coa-proof.png --output coa-check.json
cmp coa.json coa-check.json

# 3. Press-ready CMYK TIFF
jabcodeWriter --input-file coa.json --output coa.tif \
    --color-number 4 --ecc-level 5 --color-space 1
```

Expected behavior: both writer commands exit 0; the reader exits 0 and `cmp` reports no difference, confirming the payload survives the chosen parameters byte-for-byte. Only then produce the TIFF. If the reader instead exits with a small non-zero code, [chapter 8](08-decoding-with-jabcodereader.md)'s diagnosis section applies before you touch the parameters. <!-- anchor: src/jabcodeReader/jabreader.c:23, 80-88; src/jabcodeWriter/jabwriter.c:431 -->

## Try it

1. A thermal printer prints black-and-white only. Which colour count is tempting, and what is the interchange consequence?
2. Your payload doubles but the printed symbol must stay the same physical size. Name two dials that can absorb the growth, and the cost of each.
3. Which single flag change buys the most damage tolerance for a fixed payload, and what do you pay?

<details><summary>Answers</summary>

1. `--color-number 2` — Mode 0 monochrome. But it is a fork-only extension, absent from the standard entirely, so only this implementation's tools can read it; for interchange you need a standard mode, which requires colour ([chapter 4](04-colour-modes-conformance.md)). <!-- anchor: src/jabcodeWriter/jabwriter.c:147-148 -->
2. Raise the colour count (8 to a high-colour extension adds bits per module, at the price of conformance and colour-channel fragility), or lower the ECC level (more of the symbol carries payload, at the price of damage margin — level 1's rate is `0.63` versus the default's `0.55`). A third option if the shape may change: cascade with more symbols. <!-- anchor: src/jabcode/encoder.h:226 -->
3. Raising `--ecc-level`: it directly lowers the code rate (down to `0.14` at level 10), and you pay in capacity — at fixed payload, the symbol grows. <!-- anchor: src/jabcode/encoder.h:226 -->

</details>

## Where to go next

- Next: Part III begins with chapter 11, "How the service reaches this library" — the same codec, reached through the jab-auth service instead of your shell.
- Deeper: how `getSymbolCapacity` and `getOptimalECC` compute the fits these recipes rely on is covered in the Developer's Manual (JC-T), forthcoming.
