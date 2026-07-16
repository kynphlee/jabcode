# 5. Printing and scanning well

<!-- objective: An operator can apply the standard's operational guidance to choose colour count, EC level and module size for two given scenarios (CMYK print run; screen display scanned by phone) and interpret what a quality grade means when a verifier reports one. -->

**In this chapter you will** turn the standard's operational guidance into decisions: which colour count, error-correction level and module size to choose for a given production and scanning setup — and how to read a quality grade if a verifier ever hands you one.

**You should already** know the anatomy and density basics ([01-what-a-jab-code-is.md](01-what-a-jab-code-is.md)), how to size a symbol and set its armour ([02-capacity-size-robustness.md](02-capacity-size-robustness.md)), and where the standard draws the conformance lines between colour modes ([04-colour-modes-conformance.md](04-colour-modes-conformance.md)). The 5-pixels-per-module rule from [03-cascading.md](03-cascading.md) returns here too.

## Choosing the colour count for the real world

The standard's Annex A opens with the honest trade: "Using more module colours in a symbol allows higher data capacity, but it also puts higher requirements upon the technologies used to produce and read the symbol." It then says the selection should be determined by three things: the required data payload; the expected symbol size; and the capability of the production and scanning technologies. Notice what is *not* on the list — ambition. Eight colours printed sharply beat sixteen colours printed muddily every time, because the reader has to tell those colours apart on a label that has lived a little.
<!-- anchor: ISO 23634 Annex A.1 -->

## Choosing the error-correction level

You met the ten levels in [02-capacity-size-robustness.md](02-capacity-size-robustness.md); Annex A adds the operational judgement. First the cost: "For a given message length, a higher level of error correction will lead to some increase in symbol size". Then a rule that surprises people pleasantly: "If the symbol size is fixed in the application, regardless of the message length, the highest possible error correction level should be used that achieve the best robustness" (as printed in the standard). In other words — if your label die-cut is 30 mm square no matter what, do not leave capacity on the table as blank comfort; spend every spare bit on armour.
<!-- anchor: ISO 23634 Annex A.2 -->

## When to split into several symbols

Annex A endorses cascading in exactly three situations — message too large for one primary, irregular space, or a preference for small symbols — with the reliability caveat you have already seen. The mechanics live in [03-cascading.md](03-cascading.md); the operational point here is that cascading is a layout tool, not a robustness tool.
<!-- anchor: ISO 23634 Annex A.3 -->

## The print-production checklist

Annex A's production guidance condenses to a checklist worth pinning above the printer:

- **Respect the print process's density tolerance.** Every process gains or loses a little ink at module edges; keep the symbol within what the process can hold.
- **Make the module dimension an integer multiple of the print-head pixel.** If the printer lays down dots at 600 per inch, a module that maps to 7.5 printer dots will be rendered as alternating 7- and 8-dot modules — a built-in grid error. Choose a module size that divides evenly. The encoder's `--module-size` flag (default 12 pixels per module) is where you apply this arithmetic.
  <!-- anchor: src/jabcode/include/jabcode.h:32; docs/manuals/corpus-model.md §3.4 (--module-size "Module size in pixel (default:12 pixels).") -->
- **Match reader resolution to symbol density and quality** — the same pixels-per-module budgeting you did for cascades.
- **Check optical properties against the scanner's wavelength.** Inks that look right to your eye may be near-invisible, or indistinguishable, at the sensor's sensitivities.
- **Keep lighting consistent.** The standard even names a number: "A colour temperature of 6500k for the lighting is recommended."
- **Verify compliance in the final label configuration** — after the laminate overlay, on the actual substrate (watch for show-through from whatever is printed beneath), and on the final shape, including curved surfaces. A symbol that graded well as a flat proof can fail wrapped around a bottle.

<!-- anchor: ISO 23634 Annex A.4 -->

## The CMYK connection

Here is where a choice from [04-colour-modes-conformance.md](04-colour-modes-conformance.md) pays off. The standard recommends for the 4-colour mode: "When using the 4-colour mode, black, cyan, magenta, and yellow should be used." Those are precisely the four process inks of CMYK printing — meaning a 4-colour JAB Code can be printed with each module in a single pure ink, no halftoning, no mixing, no registration-dependent colour edges.
<!-- anchor: ISO 23634 Annex G.1 a) -->

The library supports this pipeline end to end. The public API offers a CMYK-aware save call:

```c
extern jab_boolean saveImageCMYK(jab_bitmap* bitmap, jab_boolean isCMYK, jab_char* filename);
```

<!-- anchor: src/jabcode/include/jabcode.h:288 -->

and the writer tool exposes it as a flag: `--color-space`, documented in its usage text as "Color space of output image (0:RGB,1:CMYK,default:0). RGB image is saved as PNG and CMYK image as TIFF." Values other than 0 or 1 are rejected. So a print-shop-ready run is one flag away: `--color-number 4 --color-space 1` yields a CMYK TIFF.
<!-- anchor: src/jabcodeWriter/jabwriter.c:226-245 -->

## Quality grades: how to read a verifier report

Clause 8 of the standard defines print-quality grading for JAB Code symbols. At operator level you need the shape of it, not the formulas. A verifier measures six parameters:

- Decode
- Unused Error Correction
- Grid non-uniformity
- Fixed Pattern Damage
- Colour Palette Accuracy (clause 8.3.1)
- Colour Variation in Data Modules (clause 8.3.2)

Each is graded on a scale of 0 to 4 (continuously graded parameters reported to one decimal), and the **scan grade is the lowest of them**. That last rule is the one to internalize: a report of five excellent numbers and one poor one *is* a poor grade. The weakest link is the grade, because the weakest link is what fails in the field.
<!-- anchor: ISO 23634 Clause 8 (8.3.1, 8.3.2) -->

An honest note about this repository: **no grading or verifier implementation exists anywhere in this tree** — the corpus model records ISO clause 8 (including the two colour parameters) as NOT FOUND in the code. `jabcodeReader` tells you *whether* a symbol decoded, never how gracefully. If your process requires graded verification, you need external verifier equipment; the gap and its implications for this codebase are taken up in the Developer's Manual (JC-T), forthcoming, ch. 15.
<!-- anchor: docs/manuals/corpus-model.md §6 NOT FOUND register (quality grading; CPA/CVDM) -->

## Worked example: two scenarios, two parameter sets

Both walk-throughs are constructed from the quoted standard guidance and source values — this session cannot execute the fork's tools, so no output is reproduced.

### Scenario A: a CMYK label print run

Certificates of authenticity, offset-printed, laminated, 1200 dpi press.

1. **Colour count: 4.** The recommended 4-colour palette is black, cyan, magenta, yellow — the process inks themselves, so every module is a solid single-ink patch. As a bonus, mode 1 is ISO-defined, so any conforming reader in the supply chain can decode the labels.
   <!-- anchor: ISO 23634 Annex G.1 a); ISO 23634 4.4.1.2 -->
2. **Output format:** `--color-number 4 --color-space 1` — a CMYK TIFF, which is what the prepress workflow expects.
   <!-- anchor: src/jabcodeWriter/jabwriter.c:226-245 -->
3. **Module size: integer-multiple arithmetic.** At 1200 dots per inch, a module rendered as, say, exactly 12 press dots measures 12/1200 inch = 0.254 mm — and, being a whole number of dots, every module edge lands on a dot boundary.
   <!-- anchor: ISO 23634 Annex A.4; src/jabcode/include/jabcode.h:32 -->
4. **EC level: as high as fits.** The die-cut fixes the label size, so Annex A.2's rule applies — after the payload is known, raise `--ecc-level` until the message no longer fits, then step back one. The writer's exit status (0 success, 1 failure) makes that search mechanical.
   <!-- anchor: ISO 23634 Annex A.2; src/jabcodeWriter/jabwriter.c:431 -->
5. **Verify in final configuration:** grade (or at minimum test-scan) the laminated label, on the curved jar it will wrap, under the warehouse's actual lights — the standard recommends 6500k lighting as the reference condition.
   <!-- anchor: ISO 23634 Annex A.4 -->

### Scenario B: a ticket on a phone screen, scanned by another phone

1. **Colour count: 8 (the default).** A screen renders the eight RGB-corner palette colours natively and brilliantly; production capability is not the constraint here, so take the free 3 bits per module. Mode 2 keeps the ticket readable by any conforming scanner app.
   <!-- anchor: src/jabcode/include/jabcode.h:33; ISO 23634 Annex A.1 -->
2. **Module size: think camera pixels, not screen pixels.** The default 12 screen pixels per module is generous; what decides success is that the *scanning* phone's camera resolves at least 5 pixels per module at natural holding distance. Keep the symbol small enough to fit the screen but large enough on screen that a camera at 20–30 cm meets that budget — and prefer a smaller side-version over shrinking modules.
   <!-- anchor: src/jabcode/include/jabcode.h:32; ISO 23634 4.5.2 (5 pixels per module) -->
3. **EC level: moderate.** A screen suffers no scuffing or ink spread; its enemies are glare and auto-brightness. The default level 3 (≈ 6 % bit recovery) is usually right; raise it only if capacity is spare — on a fixed-size display area, A.2's "highest that fits" logic applies just as it does on paper.
   <!-- anchor: ISO 23634 Table 20; ISO 23634 Annex A.2 -->
4. **Lighting still matters.** The A.4 checklist reads differently for screens — glare and reflections take the role of bad lamps — but the principle is the same: test the scan under the lighting where tickets will actually be checked, not at your desk.
   <!-- anchor: ISO 23634 Annex A.4 -->

## Try it

1. Name the six clause 8 grading parameters.
2. A verifier reports Decode 4.0, Unused Error Correction 3.8, Grid non-uniformity 3.5, Fixed Pattern Damage 3.9, Colour Palette Accuracy 1.5, Colour Variation in Data Modules 3.2. What is the scan grade?
3. Which file format does `--color-space 1` produce, and which save call in the public API is behind it?
4. Why is the 4-colour mode a natural fit for CMYK printing?

<details><summary>Answers</summary>

1. Decode; Unused Error Correction; Grid non-uniformity; Fixed Pattern Damage; Colour Palette Accuracy; Colour Variation in Data Modules.
   <!-- anchor: ISO 23634 Clause 8 -->
2. 1.5 — the scan grade is the lowest individual parameter, here Colour Palette Accuracy.
   <!-- anchor: ISO 23634 Clause 8 -->
3. A CMYK TIFF ("RGB image is saved as PNG and CMYK image as TIFF."), via `saveImageCMYK`.
   <!-- anchor: src/jabcodeWriter/jabwriter.c:226-245; src/jabcode/include/jabcode.h:288 -->
4. The recommended 4-colour palette — black, cyan, magenta, yellow — matches the four process inks, so each module prints as a single pure ink with no mixing or halftoning.
   <!-- anchor: ISO 23634 Annex G.1 a) -->

</details>

## Where to go next

Part I ends here — you now hold all the shared concepts. Next: [06-building-the-library.md](06-building-the-library.md) opens Part II, where you build `libjabcode` and the CLI tools from source. Deeper: the unimplemented clause 8 grading surface, and what a conforming verifier would need from this codebase, is analysed in the Developer's Manual (JC-T), forthcoming, ch. 15.
