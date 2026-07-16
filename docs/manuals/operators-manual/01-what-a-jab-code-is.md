# 1. What a JAB Code is

<!-- objective: An operator with no barcode background can explain what distinguishes a polychrome symbol from a monochrome 2D code and identify the finder patterns, alignment patterns, palette modules and primary vs secondary symbols on a printed sample, naming all four fixed-pattern roles correctly. -->

**In this chapter you will** learn what sets a JAB Code apart from the black-and-white barcodes you already know, and learn to point at any part of one — finder patterns, alignment patterns, palette modules, metadata modules — and name it correctly, including telling a primary symbol from a secondary one.

**You should already** — nothing. This chapter assumes no barcode background; it is the starting point of the manual.

## Colour is the third dimension

A conventional 2D barcode, such as a QR code, is monochrome: every little square is either dark or light, so each square carries exactly one bit of information. A JAB Code keeps the same idea of a grid of squares — each square is called a **module** — but paints each module in one of several colours instead of just two. If there are N colours to choose from, each module carries log2(N) bits. That is the whole trick, and it is why the format is called a *polychrome* matrix symbology: colour is a third dimension of information, on top of the two spatial ones.
<!-- anchor: docs/manuals/corpus-model.md §4 (polychrome matrix symbology) -->

The library's default is 8 colours, so each module carries 3 bits — three times the data of a monochrome code in the same grid area. The full family of colour counts the implementation accepts is described in its own words as "Valid values: 2, 4, 8, 16, 32, 64, 128, 256 (= 2^(Nc+1) for Nc=0..7)" — `Nc` is a small number stored inside the symbol that tells the reader which colour mode it is looking at. Chapter [04-colour-modes-conformance.md](04-colour-modes-conformance.md) explains which of those counts are standard and which are extensions; for now, the density table is what matters:
<!-- anchor: src/jabcode/include/jabcode.h:105 -->
<!-- anchor: src/jabcode/include/jabcode.h:33 (DEFAULT_COLOR_NUMBER 8) -->

| Colours | Bits per module |
|---|---|
| 2 | 1 |
| 4 | 2 |
| 8 (default) | 3 |
| 16 | 4 |
| 32 | 5 |
| 64 | 6 |
| 128 | 7 |
| 256 | 8 |

<!-- anchor: src/jabcode/include/jabcode.h:105 (colour count = 2^(Nc+1); bits per module = log2(colour count)) -->

## Modules and the default palette

A module is the smallest square cell of the grid. When the encoder renders an image, it draws each module as a block of pixels — 12 × 12 pixels by default (`DEFAULT_MODULE_SIZE 12`). Bigger modules are easier for cameras to read; smaller ones pack more symbol into less space. You will meet this trade-off again in [05-printing-and-scanning.md](05-printing-and-scanning.md).
<!-- anchor: src/jabcode/include/jabcode.h:32 -->

Which colours, exactly? The default 8-colour palette is fixed in the source, in this order (the source comment reads "\[K,B,G,C,R,M,Y,W\] = ISO/IEC 23634 Table 21 (the Fraunhofer reference)"):

| Index | Letter | Colour | RGB |
|---|---|---|---|
| 0 | K | black | 0, 0, 0 |
| 1 | B | blue | 0, 0, 255 |
| 2 | G | green | 0, 255, 0 |
| 3 | C | cyan | 0, 255, 255 |
| 4 | R | red | 255, 0, 0 |
| 5 | M | magenta | 255, 0, 255 |
| 6 | Y | yellow | 255, 255, 0 |
| 7 | W | white | 255, 255, 255 |

<!-- anchor: src/jabcode/encoder.h:26-34 (jab_default_palette) -->

These are the eight corners of the RGB colour cube — the most mutually distinguishable colours a screen or printer can offer, which is exactly what a camera needs when it has to decide which colour a slightly faded module was meant to be.

## How big can a symbol be

Symbol sizes are indexed by a **side-version** from 1 to 32. The side length in modules follows one small formula, taken straight from the header: `VERSION2SIZE(x)` is `(x * 4 + 17)`. Side-version 1 is therefore 21 modules on a side and side-version 32 is 145. The standard says the same thing in words: "The smallest square symbol measures 21 × 21 modules and the largest square symbol measures 145 × 145 modules." Width and height can use different side-versions, so symbols need not be square.
<!-- anchor: src/jabcode/include/jabcode.h:53 -->
<!-- anchor: ISO 23634 4.3.5 -->

## The fixed patterns: a guided tour

Not every module carries your data. Four kinds of *fixed-pattern* modules are reserved so the reader can find, straighten, and colour-correct the symbol before decoding. These are the four roles the chapter objective asks you to name: **finder patterns**, **alignment patterns**, **palette modules**, and **metadata modules**.

### Finder patterns (FP0–FP3)

The four corners of a primary symbol each hold a finder pattern — the JAB Code counterpart of the three bullseye squares on a QR code. They are numbered FP0 through FP3, sitting at the upper-left, upper-right, lower-right and lower-left corners respectively. Each has a distinctive core colour so the detector can tell the corners apart: in the default 8-colour mode, FP0 and FP1 have black cores (palette index 0), FP2 a yellow core (index 6), and FP3 a cyan core (index 3). Their job is to let the reader find the symbol at any rotation, tilt, or distance.
<!-- anchor: src/jabcode/encoder.h:80-83 (FP0-FP3 corner types) -->
<!-- anchor: src/jabcode/encoder.h:50-53 (FP core colours) -->

### Alignment patterns (AP0–AP3, APX)

Larger symbols bend and stretch more in a photograph, so as the side-version grows, extra anchor points called alignment patterns appear across the grid. AP0 through AP3 are the corner types (cyan cores, index 3) and APX is the type used at inner grid crossings (yellow core, index 6). The number of anchor positions per axis grows from 2 at the smallest versions to 9 at side-version 32.
<!-- anchor: src/jabcode/encoder.h:58-62 (AP core colours) -->
<!-- anchor: src/jabcode/encoder.h:285-292 (jab_ap_num: 2..9 per axis) -->

### Palette modules

The reader cannot trust colours in a photograph — lighting, ink, and screens all shift them. So every primary symbol embeds its own colour reference chart: the palette itself, written into known module positions. Four copies of the palette are placed in the symbol (`COLOR_PALETTE_NUMBER 4`), spread out so that even if one region is stained or glared, another copy survives. When you look at a printed JAB Code and see small orderly runs of all the colours near the corners, you are looking at palette modules.
<!-- anchor: src/jabcode/include/jabcode.h:41 -->

### Metadata modules

Finally, a small number of modules near the upper-left finder pattern store the symbol's self-description: which colour mode it uses (the `Nc` field, in metadata Part I), and its side-version, error-correction parameters, and mask pattern (Part II). Part I is encoded using only colour pairs that exist in every mode, so a reader can learn the colour count before it knows the colour count — a neat bootstrap you will never have to think about again.
<!-- anchor: docs/manuals/corpus-model.md §4 (Metadata Part I / Part II); src/jabcode/decoder.h:20-25 -->

## Primary and secondary symbols

Everything so far described one symbol. A JAB Code can be a team of them: one **primary** (also called *master*) symbol, and up to 60 **secondary** (*slave*) symbols docked to it, for a maximum of 61 (`MAX_SYMBOL_NUMBER 61`).
<!-- anchor: src/jabcode/include/jabcode.h:24 -->

Telling them apart on a printed sample is easy once you know the rule:

- The **primary** symbol is the one with all four finder patterns in its corners. It hosts the metadata and is where every decode starts.
- A **secondary** symbol has no finder patterns at all — its corners look like ordinary data. It inherits its parameters through metadata carried by the symbol it docks to.

<!-- anchor: docs/manuals/corpus-model.md §4 (primary/secondary); src/jabcode/detector.c:3682 (detectMaster); src/jabcode/decoder.c:2377 (decodeSlave) -->

Why teams of symbols exist, how they dock, and how the reader walks through them is the subject of [03-cascading.md](03-cascading.md).

## Worked example: touring a default symbol

Let us walk the anatomy of the smallest symbol the library produces with all defaults: 8 colours, side-version 1.

1. **Grid.** Side-version 1 means `1 * 4 + 17 = 21` modules per side — a 21 × 21 grid, 441 modules in total.
   <!-- anchor: src/jabcode/include/jabcode.h:53 -->
2. **Fixed patterns.** The four corners hold FP0 (black core, upper-left), FP1 (black, upper-right), FP2 (yellow, lower-right) and FP3 (cyan, lower-left); four copies of the 8-colour palette and the metadata modules occupy further known positions.
   <!-- anchor: src/jabcode/encoder.h:50-53; src/jabcode/include/jabcode.h:41 -->
3. **Data modules.** After the fixed patterns take their share, the standard's capacity table lists 349 data modules for a side-version 1 primary symbol in 8-colour mode.
   <!-- anchor: ISO 23634 Table 1 -->
4. **Density arithmetic.** Each data module carries log2(8) = 3 bits, and 349 × 3 = 1047 — exactly the net payload in bits that the same table row lists for this symbol. In 4-colour mode the identical grid offers 338 data modules × 2 bits = 676 bits. Same paper, same size; the extra colours did the work.
   <!-- anchor: ISO 23634 Table 1 -->

A note on method: this walk-through is constructed from the quoted source values and standard tables — the manual-building session cannot execute this fork's tools, so no command output is shown here. You will generate and inspect real symbols yourself in [07-encoding-with-jabcodewriter.md](07-encoding-with-jabcodewriter.md).

## Try it

1. A JAB Code is printed in 16-colour mode. How many bits does each data module carry?
2. Name the four fixed-pattern roles, and say which one exists in four copies specifically so the reader can recover true colours under bad lighting.
3. You are handed a printout showing two module grids docked side by side. The left grid has distinctive corner patterns; the right grid has none. Which is the primary symbol, and how do you know?

<details><summary>Answers</summary>

1. log2(16) = 4 bits per module.
   <!-- anchor: src/jabcode/include/jabcode.h:105 -->
2. Finder patterns, alignment patterns, palette modules, metadata modules. The palette modules exist in four copies (`COLOR_PALETTE_NUMBER 4`) as the embedded colour reference.
   <!-- anchor: src/jabcode/include/jabcode.h:41 -->
3. The left grid: only the primary symbol carries finder patterns in its corners; secondary symbols have none and inherit their parameters through the host's metadata.
   <!-- anchor: docs/manuals/corpus-model.md §4 (primary/secondary) -->

</details>

## Where to go next

Next: [02-capacity-size-robustness.md](02-capacity-size-robustness.md) turns this chapter's grid-and-density picture into numbers you can plan with — how much fits in each size, and how much damage a symbol can absorb. Deeper: the Developer's Manual (JC-T), forthcoming, covers how the encoder actually places fixed patterns and data module by module.
