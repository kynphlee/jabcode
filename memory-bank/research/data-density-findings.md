# JAB Code Data Density — ISO Spec vs Codebase, and vs QR Code

**Date:** 2026-06-25
**Author:** Analysis pass cross-referencing `ISO-IEC-23634.pdf` (licensed copy, ISO Store order OP-892762) against the codec source (`src/jabcode/encoder.c`, `decoder.c`, `include/jabcode.h`) and ISO/IEC 18004 (QR Code).
**Status:** Findings for review.

---

## 1. Executive summary

- JAB Code's density advantage over QR comes entirely from **colour**: each module
  encodes `log2(colours)` bits, versus QR's fixed **1 bit/module** (monochrome).
- ISO/IEC 23634:2022 standardises **only two colour modes — 4 and 8 colours**
  (2 and 3 bits/module). Every other mode in our codec (2, 16, 32, 64, 128, 256)
  rides a **reserved** `Nc` code point and is a non-standard extension.
- At standardised settings JAB carries **2x–3x** QR's bits/module; the colour
  extensions reach **8x** but decode only on our own reader.
- `COLOR_MODE_AUDIT.md` is **incorrect** where it labels the 16–256 modes
  "ISO-compliant". They are ISO-*derived*, not ISO-conformant. See section 6.

---

## 2. What the spec actually defines (ISO/IEC 23634:2022)

The colour mode lives in a 3-bit metadata field `Nc` (spec Table 6). Only two
values are assigned; the rest are reserved:

| `Nc` (binary) | Colour mode | Module colours | Status |
|:---:|:---:|:---:|:---|
| 000 | 0 | — | reserved |
| 001 | 1 | 4 | **standard** |
| 010 | 2 | 8 | **standard (default)** |
| 011 | 3 | — | reserved |
| 100 | 4 | — | reserved |
| 101 | 5 | — | reserved |
| 110 | 6 | — | reserved |
| 111 | 7 | — | reserved |

Spec section 4.3.5(e): *"The number of module colours is configurable in two
modes: 4 or 8 colours."* Section 4.3.5(f): *"A module represents log2(Nc) binary
bits."*

Other relevant spec parameters:

- **Symbol size:** side 21 to 145 modules; `side = version * 4 + 17`
  (version 1 = 21x21, version 32 = 145x145). Single primary symbol only.
- **Default error correction:** level 3, `E = (001101)`, giving `wc = 4`,
  `wr = 9` — an LDPC design rate of `1 - wc/wr = 5/9 ≈ 0.556` (~44% spent on parity).
- **Net payload `Pn`** (spec Table 1) = data-region modules x bits/module, after
  finder/alignment/colour-palette/metadata overhead is removed.

---

## 3. What the codebase implements

`createEncode()` (`encoder.c`) accepts `color_number` in
`{2, 4, 8, 16, 32, 64, 128, 256}`; `MAX_COLOR_NUMBER = 256`. The colour-mode index
is computed as `Nc = round(log2(color_number)) - 1`, and the decoder uses
`bits_per_module = Nc + 1` (`decoder.c`). Palettes above 8 colours are generated
by `genColorPalette()` via an R/G/B channel-level product (e.g. 256 = 8x8x4).

Mapping our modes onto the spec's `Nc` field:

| Colours | Codec `Nc` | Bits/module | ISO/IEC 23634 status |
|:---:|:---:|:---:|:---|
| 2 | 0 | 1 | reserved (our WS-0 monochrome) |
| 4 | 1 | 2 | **standard** |
| 8 | 2 | 3 | **standard (default)** |
| 16 | 3 | 4 | reserved (extension) |
| 32 | 4 | 5 | reserved (extension) |
| 64 | 5 | 6 | reserved (extension) |
| 128 | 6 | 7 | reserved (extension) |
| 256 | 7 | 8 | reserved (extension) |

The bits/module relationship is identical in spec and code — the disagreement is
purely about **which `Nc` values are legal**. Our extensions are faithful
extrapolations of the spec's Annex G palette construction, but a conformant
third-party ISO reader will treat `Nc ∈ {0,3,4,5,6,7}` as reserved/undecodable.

---

## 4. Data density per colour mode (JAB)

Raw structural density is `bits/module = log2(colours)`:

| Colours | Bits/module | Density vs 8-colour | In ISO Table 1? |
|:---:|:---:|:---:|:---:|
| 2 | 1 | 0.33x | no (extension) |
| 4 | 2 | 0.67x | **yes** |
| 8 | 3 | 1.00x (baseline) | **yes** |
| 16 | 4 | 1.33x | no (extension) |
| 32 | 5 | 1.67x | no (extension) |
| 64 | 6 | 2.00x | no (extension) |
| 128 | 7 | 2.33x | no (extension) |
| 256 | 8 | 2.67x | no (extension) |

Net payload `Pn` (ISO Table 1, default metadata, standard modes only):

| Side-version | Side size | `Pn` @ 4-colour | `Pn` @ 8-colour |
|:---:|:---:|:---:|:---:|
| 1 | 21x21 | 676 bits | 1,047 bits |
| 8 | 49x49 | 4,526 bits | 6,822 bits |
| 32 (max) | 145x145 | 40,766 bits | 61,182 bits |

`Pn` still includes LDPC parity. Usable data at the default level-3 ECC is
roughly `Pn x 5/9`. Example: V32/8-colour `Pn` 61,182 bits → ~34,000 bits ≈ 4.2 KB
of real payload.

---

## 5. Comparison vs QR Code (ISO/IEC 18004)

QR is monochrome — **always 1 bit/module**, every version. That makes each JAB
colour mode a clean multiple of QR's structural density.

### 5.1 Bits per module

| Symbology / mode | Bits/module | Density vs QR |
|:---|:---:|:---:|
| QR Code (any version) | 1 | 1.00x (baseline) |
| JAB 4-colour (standard) | 2 | 2x |
| JAB 8-colour (standard) | 3 | 3x |
| JAB 16-colour (extension) | 4 | 4x |
| JAB 32-colour (extension) | 5 | 5x |
| JAB 64-colour (extension) | 6 | 6x |
| JAB 128-colour (extension) | 7 | 7x |
| JAB 256-colour (extension) | 8 | 8x |

### 5.2 Effective density (capacity / total modules)

| Symbol | Grid (modules) | Channel bits | Effective bits/module |
|:---|:---:|:---:|:---:|
| QR V40 | 177x177 = 31,329 | ~29,648 | ~0.95 |
| JAB V32 @ 8-colour | 145x145 = 21,025 | 61,182 | ~2.91 (~3.1x QR) |

Function-pattern overhead is proportionally small at large sizes, so the effective
ratio tracks the raw bits/module ratio.

### 5.3 Maximum capacity, single symbol

| Metric | QR Code (V40) | JAB single symbol (V32) |
|:---|:---|:---|
| Max grid | 177x177 (31,329 modules) | 145x145 (21,025 modules) |
| Channel bits | ~29,648 | 61,182 @ 8c (~163,000 @ 256c) |
| Max user data | **2,953 bytes** (byte mode, ECC-L) | ~4.2 KB @ 8c, default ECC-L3 |
| Multi-symbol chaining | Structured Append: 16 symbols | Docked cascade: 61 symbols |

JAB wins on capacity **despite a smaller maximum grid** (145 vs 177 a side),
purely because each cell holds more — then cascades far further for effectively
unbounded payload.

### 5.4 Three caveats before over-rotating on the multipliers

1. **ECC is not held equal.** QR's 2,953-byte max uses its *lightest* ECC (level
   L, ~7% recovery); JAB's default level 3 spends ~44% on parity. Match the error
   correction and JAB's net-data lead *widens* — it is simply more robust by default.
2. **Bits/module ≠ bits/cm².** Colour modules generally must be printed/scanned
   larger than QR cells to keep hues separable, so the per-area gain is less than
   the per-module gain. This is what the codec's colour-calibration / white-balance
   work exists to recover.
3. **Only 2x and 3x are interoperable.** QR is a universally decodable ISO
   standard; JAB's 4x–8x modes decode only on our reader. The fair "JAB vs QR in
   the wild" figure is **2x–3x**, not 8x.

---

## 6. Correction to `COLOR_MODE_AUDIT.md`

`COLOR_MODE_AUDIT.md` (validation table and conclusion) marks the 16, 32, 64, 128,
and 256-colour modes as "ISO Compliance: Yes". This is **inaccurate**:

- ISO/IEC 23634:2022 Table 6 assigns colours only to `Nc = 1` (4) and `Nc = 2` (8).
- `Nc ∈ {0, 3, 4, 5, 6, 7}` are **reserved** — our 2/16/32/64/128/256 modes occupy
  reserved code points.

**Recommended doc change:** relabel those rows "ISO-derived extension (reserved
`Nc`)" rather than "ISO-compliant", and add a one-line note that symbols emitted in
these modes are not decodable by a conformant third-party ISO reader. The
palette-generation *maths* remains a correct Annex G extrapolation; only the
conformance claim is wrong.

---

## 7. Lessons learned

- **"Spec-derived" is not "spec-conformant."** A correct extrapolation of a spec's
  algorithm onto its reserved fields is still off-standard and breaks
  interoperability. Track conformance separately from correctness.
- **Quote density as bits/module first.** It is the one figure that is identical
  across spec and code and directly comparable to QR; capacity-per-symbol and
  per-area figures are downstream of it and carry ECC/optics caveats.
- **Density headlines need their ECC baseline stated.** QR's max-byte figure and
  JAB's `Pn` use very different default error correction; comparing them without
  saying so overstates QR and understates JAB.

---

## References

- ISO/IEC 23634:2022 — `memory-bank/documentation/specification/ISO-IEC-23634.pdf`
  (Table 1 capacities, Table 6 `Nc` modes, section 4.3.5, default ECC level 3).
- Codec source — `src/jabcode/encoder.c` (`genColorPalette`, `createEncode`, `Nc`),
  `src/jabcode/decoder.c` (`bits_per_module = Nc + 1`),
  `src/jabcode/include/jabcode.h` (`MAX_COLOR_NUMBER`, `VERSION2SIZE`).
- `COLOR_MODE_AUDIT.md` — prior audit (conformance claim corrected here).
- QR Code: ISO/IEC 18004. Capacity figures — DENSO WAVE
  (https://www.qrcode.com/en/about/version.html), QR Planet
  (https://qrplanet.com/help/article/what-storage-capacity-does-a-qr-code-have).
