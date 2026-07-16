# 3. Cascading: one message, many symbols

<!-- objective: An operator can decide when to cascade and assign valid symbol positions and versions for a multi-symbol code, producing a correct position/version assignment for a three-symbol example (master at position 0, docked sides sharing side-version). -->

**In this chapter you will** learn when it pays to split one message across several docked symbols, and how to assign each symbol a valid position and side-version — ending with a complete three-symbol example you could hand to the writer tool.

**You should already** be able to name the parts of a symbol and tell a primary from a secondary ([01-what-a-jab-code-is.md](01-what-a-jab-code-is.md)), and know how side-versions and capacity relate ([02-capacity-size-robustness.md](02-capacity-size-robustness.md)).

## Why cascade at all

A single primary symbol tops out at about 7.6 kB ([02-capacity-size-robustness.md](02-capacity-size-robustness.md)). Cascading — docking up to 60 secondary symbols onto one primary — is the standard's answer to three situations, and its Annex A names exactly these: the message exceeds what a single primary can hold; the available space is irregular, so one big square will not fit but several smaller symbols will; or the application simply prefers small symbols. There is also a quiet bonus: secondary symbols "may accommodate more data than primary symbols, as they have lower overhead". And one honest cost, in the standard's own words: cascading "may consequently decrease decoding reliability" — more symbols means more places for a scan to go wrong.
<!-- anchor: ISO 23634 Annex A.3 -->

The implementation's ceiling matches the standard's: `MAX_SYMBOL_NUMBER 61` — one primary plus up to 60 secondaries.
<!-- anchor: src/jabcode/include/jabcode.h:24 -->

## The 61 positions

Every symbol in a cascade occupies a numbered **position** on an imaginary grid of symbol-sized cells around the primary. The standard fixes the layout: "The indices of the first 60 secondary symbols are defined in Figure 14."
<!-- anchor: ISO 23634 4.5.2 -->

The implementation carries that map as a table of (x, y) offsets, in units of whole symbols, with the primary at the origin. Here is its beginning:

```c
static const jab_vector2d jab_symbol_pos[MAX_SYMBOL_NUMBER] =
        {   { 0, 0},
            { 0,-1}, { 0, 1}, {-1, 0}, { 1, 0}, { 0,-2}, {-1,-1}, { 1,-1}, { 0, 2}, {-1, 1}, { 1, 1},
            ...
```

Position 0 is the primary at the origin. Read alongside the standard's decode order (below), the first ring falls into place: position 1 is `{0,-1}` — the cell directly **above** the primary; position 2 is `{0,1}`, directly **below**; position 3 is `{-1,0}`, to the **left**; position 4 is `{1,0}`, to the **right**. Higher positions spiral outward in the same disciplined pattern, out to five symbol-cells in each direction.
<!-- anchor: src/jabcode/encoder.h:111-119 (jab_symbol_pos) -->

## Docking rules

Two rules from the standard govern which assignments are legal and sensible:

- **Shared side.** "the docking side between two adjacent symbols shall share the same Side-Version." If two symbols touch left-to-right, the edge they share is a vertical side — so their *height* side-versions must match; their widths are free. Touch top-to-bottom, and the *width* side-versions must match.
- **Primary first among equals.** "It is recommended that the primary symbol possess the largest symbol size."

<!-- anchor: ISO 23634 4.5.1 -->

The easiest way to satisfy both, and the one this chapter's example uses: give every symbol the same side-version.

## Decode order

You do not choose the order in which a reader visits the symbols — the standard does: the decoding shall "always start with the primary symbol. If more than one secondary symbol is docked to the primary symbol, the decoding shall follow the order top-bottom-left-right". A note in the same clause handles revisits: "Whenever the top-bottom-left-right decode cycle hits a previously decoded symbol, it does not count that module and proceeds to the next step in the cycle." Your message is stitched back together in exactly this walking order, which is why position numbers matter: they *are* the message order.
<!-- anchor: ISO 23634 4.5.2 -->

## The 5-pixels-per-module rule

Cascades tempt you to keep adding symbols, but the reader's camera has finite pixels. The standard draws the line: "However, it is important to note that cascading should be limited such that the reader is able to resolve at least 5 pixels per module." A wall of symbols photographed from across the room fails not because cascading broke, but because each module landed on fewer pixels than a reader can classify. Budget your pixels before you budget your symbols.
<!-- anchor: ISO 23634 4.5.2 -->

## What the writer tool enforces

The `jabcodeWriter` CLI (fully covered in [07-encoding-with-jabcodewriter.md](07-encoding-with-jabcodewriter.md)) implements these rules as hard checks:

- `--symbol-number` accepts 1 to 61; anything else is rejected with "Invalid symbol number (must be 1 - 61)."
  <!-- anchor: src/jabcodeWriter/jabwriter.c:220-224 -->
- `--symbol-position` takes values 0 to 60 and is "Only required for multi-symbol code". For a single-symbol code, a position other than 0 for the master is rejected: "Incorrect symbol position value for master symbol."
  <!-- anchor: src/jabcodeWriter/jabwriter.c:358, 386-390, 397-404 -->
- For a multi-symbol code, *every* symbol needs a position and a side-version, or the writer stops with "Symbol position information is incomplete for multi-symbol code" / "Symbol version information is incomplete for multi-symbol code".
  <!-- anchor: src/jabcodeWriter/jabwriter.c:405-414 -->
- `--symbol-version` supplies side-versions as x y pairs per symbol — "(x0 y0 x1 y1 x2 y2...)" in the usage text — each between 1 and 32.
  <!-- anchor: src/jabcodeWriter/jabwriter.c:311, 350-354 -->

## Worked example: a three-symbol vertical stack

Say your label space is a tall narrow strip — a wine-crate edge, a cable tag. One wide symbol will not fit, but three stacked ones will. Here is a valid assignment, then the command it becomes.

1. **Positions.** The primary must sit at position 0. For a vertical stack, dock one secondary directly above (position 1, offset `{0,-1}`) and one directly below (position 2, offset `{0,1}`).
   <!-- anchor: src/jabcode/encoder.h:111-119 -->
2. **Side-versions.** Top-to-bottom docking means the shared edges are horizontal, so width side-versions must match across each docked pair. Simplest legal choice: side-version 8 × 8 for all three symbols — every shared side matches, and the primary ties for largest, satisfying the recommendation.
   <!-- anchor: ISO 23634 4.5.1 -->
3. **The command** (constructed from the source's flag definitions — this session cannot execute the fork's tools, so no output is shown):

   ```sh
   jabcodeWriter --input "your message here" --output stack.png \
     --symbol-number 3 \
     --symbol-position 0 1 2 \
     --symbol-version 8 8 8 8 8 8
   ```

   Three symbols, three positions, and three x y version pairs — six numbers — exactly the completeness the writer checks for.
   <!-- anchor: src/jabcodeWriter/jabwriter.c:205, 358, 311 (flags); 405-414 (completeness checks) -->
4. **Sanity-check the scan.** Side-version 8 means `4 × 8 + 17 = 49` modules per side, so the stack is roughly 3 × 49 ≈ 150 modules tall. The 5-pixels-per-module rule then asks the capturing camera for roughly 5 × 150 ≈ 750 pixels across the stack's height. A phone camera manages that easily at label distance; a fixed scanner across a conveyor belt may not — check yours.
   <!-- anchor: src/jabcode/include/jabcode.h:53; ISO 23634 4.5.2 (5 pixels per module) -->
5. **Decode order.** The reader starts at the primary, then follows top-bottom-left-right: position 1 (above), then position 2 (below). Your message is therefore split across the symbols in the order primary → top → bottom.
   <!-- anchor: ISO 23634 4.5.2 -->

## Try it

1. What is the maximum number of symbols in one JAB Code, and how does it split between primary and secondary?
2. Where does position 4 sit relative to the primary?
3. Two symbols are docked side by side, left and right. One is side-version 6 × 5, the other 9 × 5. Is this assignment legal?
4. A primary has secondaries at positions 3 and 4 only. In what order are the three symbols decoded?

<details><summary>Answers</summary>

1. 61 in total: 1 primary + up to 60 secondaries (`MAX_SYMBOL_NUMBER 61`; "The indices of the first 60 secondary symbols are defined in Figure 14.").
   <!-- anchor: src/jabcode/include/jabcode.h:24; ISO 23634 4.5.2 -->
2. Offset `{1,0}`: the symbol-cell directly to the right of the primary.
   <!-- anchor: src/jabcode/encoder.h:111-119 -->
3. Yes. The docking side is vertical, so the *height* side-versions must match — both are 5. The differing widths (6 and 9) are allowed.
   <!-- anchor: ISO 23634 4.5.1 -->
4. Primary first, then top-bottom-left-right among the docked symbols: with nothing at top or bottom, position 3 (left) comes next, then position 4 (right).
   <!-- anchor: ISO 23634 4.5.2 -->

</details>

## Where to go next

Next: [04-colour-modes-conformance.md](04-colour-modes-conformance.md) — which colour counts are standard, which are extensions, and what each choice means for whoever scans your codes. Deeper: cascade encoding — how the encoder assigns docked symbols internally — belongs to the Developer's Manual (JC-T), forthcoming.
