# 4. The metadata bootstrap: a three-colour robust code

<!-- objective: A mathematically mature reader can explain why Part I's Nc field is decodable before the palette is known — the black/cyan/yellow fixed encoding as a code over a known sub-alphabet — and analyze its error behavior as a miniature case study in bootstrap design. -->

**Where it lives.** The encoding table is `nc_color_encode_table` (`src/jabcode/encoder.h:124`); the encoder places it inside `createMatrix` (encoder.c:1405-1440) from bits built by `encodeMasterMetadata` (encoder.c:925-1000). The decoder side is `decodeModuleNc` (decoder.c:927), `decodeNcModuleColor` (decoder.c:1242) and `decodeMasterMetadataPartI` (decoder.c:1262). The fork's permissive-substitution switch is `g_permissive_color_classification` (`include/jabcode.h:93-98`, rationale at decoder.c:98-132). Spec ground: ISO/IEC 23634:2022 clause 4.4.1.2 and Table 7; decode procedure step e) in clause 6.1. <!-- anchor: encoder.h:124; encoder.c:925, 1405-1440; decoder.c:927, 1242, 1262; jabcode.h:93-98; ISO 23634 4.4.1.2, Table 7, 6.1 -->

## The problem

Every data module in a JAB Code symbol carries `log2(colour count)` bits, read by classifying the module against the colour palette ([02-information-density.md](02-information-density.md)). The colour count is `2^(Nc+1)`, and `Nc` lives in metadata Part I. But Part I is itself made of coloured modules. To read a colour index you need the palette; to know how large the palette is — how many colours to even look for, and how many palette modules were embedded — you need `Nc`; and `Nc` is written in colours. This is a genuine circular dependency, not a bookkeeping accident: the decoder cannot classify a single data module until the loop is broken.

The classical way to break such a loop is a *bootstrap*: reserve a small region encoded over an alphabet fixed a priori, independent of every parameter the region announces. Clause 4.4.1.2 does exactly this, and its rationale sentence is worth quoting because it is the whole design in one line:

> "Nc shall be encoded in a three-colour mode which uses only the first, the fourth and the seventh module colour in the colour palette defined in Table 3. The three used colours shall be black, cyan and yellow in all colour modes."

<!-- anchor: ISO 23634 4.4.1.2 -->

Whatever colour mode the symbol uses, these four modules speak a language the receiver already knows. The rest of the symbol is then reached along a chain of trust — clause 4's placement flow reads: "Place Part I using black, cyan and yellow → Place colour palettes → Place Part II, encoded message and Part III using all available colours". This chapter analyzes the bootstrap as a small code with unusual constraints, then walks the chain. <!-- anchor: ISO 23634 4.4 -->

## Theory

### 4.1 A code over a sub-alphabet

Formally: the channel alphabet at Part I positions is not the palette (unknown at this point) but the fixed set

$$
\Sigma = \{\mathrm{K}, \mathrm{C}, \mathrm{Y}\},\qquad
\mathrm{K}=(0,0,0),\ \mathrm{C}=(0,255,255),\ \mathrm{Y}=(255,255,0)
$$

and the receiver's demodulator is a fixed RGB classifier, not a palette lookup. A geometric aside that the spec does not state but that is easy to verify: these three points are pairwise equidistant in RGB space —

$$
\lVert \mathrm{K}-\mathrm{C}\rVert_2
= \lVert \mathrm{K}-\mathrm{Y}\rVert_2
= \lVert \mathrm{C}-\mathrm{Y}\rVert_2
= 255\sqrt{2},
$$

an equilateral triangle inscribed in the RGB cube, so no pair of bootstrap colours is more confusable than any other under an isotropic noise model. (Analytical observation of this book; the spec's stated rationale is only the first/fourth/seventh palette-position rule. Colour-space geometry is developed properly in [08-colour-space-geometry.md](08-colour-space-geometry.md).)

Table 7 then defines a block code of length 2 over Σ: each 3-bit value maps to an ordered pair of module colours,

| bits | pair | bits | pair |
|---|---|---|---|
| 000 | (black, black) | 100 | (cyan, cyan) |
| 001 | (black, cyan) | 101 | (cyan, yellow) |
| 010 | (black, yellow) | 110 | (yellow, black) |
| 011 | (cyan, black) | 111 | (yellow, cyan) |

<!-- anchor: ISO 23634 Table 7 -->

Capacity accounting: a pair of trits carries

$$
2\log_2 3 \approx 3.17\ \text{bits},
$$

and Table 7 spends 3 of them. Eight codewords sit inside a space of `3 × 3 = 9` possible pairs — the code uses 8 of 9 combinations.

### 4.2 The unused pair, verified

Which pair is unused? Read the table: the pairs with first colour black exhaust `{K,C,Y}` in second position; first colour cyan likewise; first colour yellow appears only with second colour black or cyan. The single absent combination is **(yellow, yellow)**. This is verifiable from Table 7 as extracted (8 rows, none equal to (Y, Y)) and independently from the source: `nc_color_encode_table` is `{{0,0}, {0,3}, {0,6}, {3,0}, {3,3}, {3,6}, {6,0}, {6,3}}` — no `{6,6}` row — and the fork's own commentary states that "the JABCode metadata pair lookup reserves (Y, Y) as structurally invalid" (decoder.c:1336-1338). The spec gives no stated rationale for *which* pair to omit; that (Y, Y) rather than, say, (K, K) is the hole is a fact of Table 7, not a derivable necessity — **NOT FOUND** as a justified choice in the extracted spec text. <!-- anchor: encoder.h:124; decoder.c:1332-1340; ISO 23634 Table 7 -->

What does one unused codeword buy? Exactly one forbidden pattern: the code has minimum Hamming distance 1 over Σ² (e.g. (K, K) and (K, C) are both codewords and differ in one position), so it corrects nothing and detects only those errors that land on (Y, Y) or leave the alphabet altogether. Treated in isolation it is a nearly-trivial code — 8/9 of the space is valid. The design only makes sense as the bottom layer of a stack (§4.4): its job is not correction but *cheap, palette-free demodulation with a tripwire*, with correction delegated upward.

### 4.3 Palette-position invariance: why {0, 3, 6}

The table's values 0, 3 and 6 are palette *indices*, and in the 8-colour reference palette `[K,B,G,C,R,M,Y,W]` (`encoder.h:26-34`) index 0 is black, index 3 is cyan, index 6 is yellow — precisely the spec's "first, the fourth and the seventh module colour". The subtlety is that Part I module colours are fixed *as colours*, while the table stores them *as indices into one particular palette*. The two views must be reconciled in every colour mode: <!-- anchor: encoder.h:26-34, 124; ISO 23634 4.4.1.2 -->

- **8 colours (Nc = 2, default).** Indices 0/3/6 are K/C/Y directly.
- **4 colours (Nc = 1).** The encoder reduces indices modulo the colour count: `{0,3,6} mod 4 = {0,3,2}` (encoder.c:1421-1422). This lands on the right colours only because the 4-colour palette is *ordered to make it land*: `setDefaultPalette` builds it as black (0), magenta (1), yellow (2), cyan (3) (encoder.c:104-110) — so index 3 is cyan and index 2 is yellow. The mod trick and the palette ordering are two halves of one invariant. This ordering does **not** match the spec: ISO Table 21 orders the 4-colour palette K, C, M, Y, while the code builds K, M, Y, C — a real spec-vs-reference divergence (verified 2026-07-15). Under the spec's ordering the mod trick would land on K/Y/M, not K/C/Y; the code's reordering is precisely what makes the reduction land correctly, and how the spec itself intends 4-colour Part I placement to work is beyond the extract. <!-- anchor: encoder.c:104-110, 1421-1422; ISO 23634 Table 21 -->
- **16-256 colours (Nc ≥ 3).** Index arithmetic no longer works, so the encoder searches the actual palette for the *RGB values* of default-palette entries 0/3/6 (encoder.c:1423-1433). This terminates correctly because every palette `genColorPalette` produces contains exact K, C and Y: the channel value sets always include 0 and 255 in each channel (encoder.c:29-88 — e.g. 16-colour mode uses r ∈ {0, 85, 170, 255}, g, b ∈ {0, 255}), so (0,0,0), (0,255,255) and (255,255,0) are always present. The invariant "any palette must contain black/cyan/yellow equivalents" is the bootstrap's one demand on palette designers (cf. [../developers-manual/04-encoder.md](../developers-manual/04-encoder.md) §4.11). <!-- anchor: encoder.c:29-88, 1423-1433 -->

On the decoder side the invariance is even cleaner: `decodeModuleNc` never touches a palette at all. It classifies raw pixel RGB into the canonical 8-colour buckets with fixed thresholds (decoder.c:927-1000), and `decodeMasterMetadataPartI` accepts only `{0, 3, 6}` (decoder.c:1381-1383). The bootstrap alphabet is hard-wired at both ends; the palette is bypassed entirely on the receive path. <!-- anchor: decoder.c:927, 1381-1383 -->

### 4.4 The full stack: three error surfaces

Part I is not two pairs of raw bits. The net payload is the 3-bit `Nc`; it is first LDPC-encoded at rate 1/2 into `MASTER_METADATA_PART1_LENGTH = 6` bits (`wcwr = {2, -1}`, encoder.c:967-969; the metadata matrix is Annex C's constrained special case — see [03-ldpc-coding-theory.md](03-ldpc-coding-theory.md)), then split into two 3-bit groups, each mapped through Table 7 onto a pair of modules: 4 modules total (`MASTER_METADATA_PART1_MODULE_NUMBER`, `decoder.h:25`), starting at module position (6, 1) (`decoder.h:20-21`). <!-- anchor: encoder.c:925-1000; decoder.h:20-25; ISO 23634 Annex C -->

$$
\underbrace{3\ \text{bits}}_{N_c}
\ \xrightarrow{\ \text{LDPC } (6,3)\ }
\ \underbrace{6\ \text{bits}}_{\text{2 groups of 3}}
\ \xrightarrow{\ \text{Table 7}\ }
\ \underbrace{2\ \text{pairs}}_{\text{4 modules over } \{K,C,Y\}}
$$

The decode path (decoder.c:1320-1455) therefore checks a corrupted read at three successive surfaces, each strictly cheaper than the next:

1. **Geometric (per module).** The classified colour must lie in `{0, 3, 6}`; anything else — white, magenta, an ambiguous read — fails immediately with `DECODE_METADATA_FAILED` (decoder.c:1381-1392). This surface catches all errors that leave the alphabet.
2. **Combinatorial (per pair).** `decodeNcModuleColor` returns the sentinel 8 for any pair outside Table 7 — in-alphabet, that means exactly (Y, Y) (decoder.c:1242-1250, 1410-1417). One codeword of detection redundancy, as derived in §4.2.
3. **Algebraic (per field).** The surviving 6 bits go to `decodeLDPChd` (decoder.c:1434); errors that forge a *valid but wrong* pair must be caught here or not at all. <!-- anchor: decoder.c:1242-1250, 1381-1392, 1410-1417, 1434 -->

Note the mismatch of error models between layers: one damaged *module* flips up to 3 consecutive *bits* of the 6-bit word (a within-group burst), because Table 7 pairs are not Gray-coded against module substitutions. The (6,3) metadata code therefore faces short bursts, not independent bit flips; its actual correction behaviour under that model is a property of the Annex C matrix, analyzed in ch. 3.

### 4.5 The trust ladder

The bootstrap is rung one of a dependency chain that the placement flow fixes in order: <!-- anchor: ISO 23634 4.4 -->

| Rung | Region | What it needs | What it yields |
|---|---|---|---|
| 1 | Part I (4 modules, fixed alphabet) | nothing beyond detection/sampling | `Nc`, hence colour count and palette size |
| 2 | 4 embedded palette copies | `Nc` (how many palette modules exist) | the working palette for this print/capture |
| 3 | Part II (38 encoded bits) | palette (full-alphabet modules) | `V` (side version), `E` (wc, wr), `MSK` |
| 4 | Data modules | `V` for geometry, `MSK` for demask, `E` for LDPC, palette for classification | the message |

Each rung's demodulator is parameterized entirely by outputs of earlier rungs — the chain is a topological order on the parameter-dependency DAG, and Part I is the unique source node, which is why it alone must be self-describing. Rung 3 and 4 machinery is covered in [../developers-manual/05-detector-and-decoder.md](../developers-manual/05-detector-and-decoder.md); the geometry of rung 2 classification in [08-colour-space-geometry.md](08-colour-space-geometry.md).

One spec-level escape hatch closes the ladder from the top: per clause 6.1 step e), if Part I is invalid, defaults are used — the decoder may proceed under the default-mode assumption instead of aborting. A bootstrap failure thus degrades to a guess with a known prior (8 colours, default ECC), rather than a hard stop. <!-- anchor: ISO 23634 6.1 step e) -->

## Back to the code

The reference realization is faithful to the theory, with three fork-local deviations worth analyzing.

**Encoder placement.** Inside `createMatrix`, every 3 metadata bits become `val`, and the two modules are placed from `nc_color_encode_table[val][i]` — with the mod-reduction branch for ≤ 8 colours and the RGB-search branch for > 8 (encoder.c:1410-1433), exactly the invariance mechanics of §4.3. The in-code comment marks the high-colour branch as a fix: "Part I must use base 8-color palette RGB values (black, cyan, yellow)" (encoder.c:1417-1419). <!-- anchor: encoder.c:1410-1433 -->

**Path β: permissive substitution.** With `g_permissive_color_classification` set, a Part I module classified as magenta (5) is silently rewritten to yellow (6) before the validity check (decoder.c:1341-1345). The in-source justification is a conditional-probability argument: "only {K=0, C=3, Y=6} are legitimately encoded at metadata positions, so any rgb=5 read at a metadata module is unambiguously a misclassified Y under camera noise" (decoder.c:107-113) — i.e. the substitution is sound exactly when green-channel under-capture (Y → M) is the only plausible generator of M reads at these positions. As a piece of bootstrap design this trades surfaces: it deletes part of the *geometric* tripwire (an M read no longer fails fast) and re-routes those events to the combinatorial and algebraic layers, which are weaker per §4.2. If the true colour was not Y — a blue-cast black, a mis-sampled neighbouring module, an adversarial paint-over ([11-adversarial-channel.md](11-adversarial-channel.md)) — the remap manufactures a plausible-looking pair and defers detection to a distance-1 pair code plus a (6,3) LDPC word: a real false-accept channel, opt-in and default-off for that reason (jabcode.h:93-98). <!-- anchor: decoder.c:98-132, 1341-1345; jabcode.h:93-98 -->

The fork's own empirical postscript is a textbook falsification note: on the motivating fixture the camera read *all four* modules as magenta, the remap produced (Y, Y) pairs, and decode still failed — at the pair layer instead of the module layer (decoder.c:1332-1340). The unused codeword of §4.2 is what caught it: the one forbidden pair earned its keep against precisely the failure Path β was built for. <!-- anchor: decoder.c:1332-1340 -->

**Mode 0: the limiting case.** The fork's 2-colour extension cannot satisfy Table 7 at all — a K/W palette contains no cyan or yellow, so every pair lookup would return the sentinel (decoder.c:1292-1299). The fork's resolution is to bypass the rung: when the detector's chroma probe has already established monochrome, Part I is skipped, the module cursor advanced, and `Nc = 0` asserted directly (decoder.c:1307-1318). Read as bootstrap theory: the trust that rung 1 normally earns from a fixed alphabet is instead imported from an out-of-band classifier (the chroma probe), which is only possible because Mode 0 is detectable *before* metadata. It marks the boundary of Table 7's design envelope — the scheme presumes the bootstrap colours exist in the print gamut, and the standard's own colour modes all satisfy that; the monochrome extension does not (no 2-colour mode exists in the standard — [../developers-manual/16-extended-colour-modes.md](../developers-manual/16-extended-colour-modes.md)). <!-- anchor: decoder.c:1292-1318 -->

## Exercises

**1 (guided).** A master symbol's Part I modules truly encode the pairs (K, C) and (Y, K) — encoded bits 001 110. For each single-module corruption below, state at which surface (§4.4: module, pair, LDPC) the decoder first notices, and how many *bits* of the 6-bit word are wrong if it reaches LDPC.

a) module 1 (K) reads as white; b) module 3 (Y) reads as cyan; c) module 4 (K) reads as yellow; d) module 3 (Y) reads as black.

<details><summary>Hints and answers</summary>

a) White ∉ {0, 3, 6}: caught at the module surface (decoder.c:1381-1392); no bits reach the pair layer.
b) Second pair becomes (C, K) = 011, a valid codeword: passes module and pair surfaces; bits go 110 → 011, a 2-bit error inside one 3-bit group, left for LDPC.
c) Second pair becomes (Y, Y): in-alphabet but not in Table 7 — caught at the pair surface with sentinel 8 (decoder.c:1242-1250).
d) Second pair becomes (K, K) = 000: valid; 110 → 000, again a 2-bit error for LDPC.

Moral: of the 8 possible in-alphabet single-module substitutions on this codeword, only those manufacturing (Y, Y) are pair-detected; the rest convert one module error into a 1-3-bit burst for the algebraic layer.
</details>

**2 (guided).** Explain why the index triple {0, 3, 6} survives every colour mode — give the three mechanisms (one for ≤ 8 colours, one for > 8, one decoder-side) and identify the invariant each relies on.

<details><summary>Hint</summary>

§4.3: (i) mod reduction plus deliberate 4-colour palette ordering (encoder.c:104-110, 1421-1422); (ii) RGB search plus the fact that every generated palette contains exact K/C/Y because channel value sets include 0 and 255 (encoder.c:29-88); (iii) the decoder's fixed palette-free classifier and its {0, 3, 6} acceptance set (decoder.c:927, 1381-1383). The shared invariant: black, cyan, yellow exist as exact printable colours in every mode, at positions the encoder can compute.
</details>

**3 (open).** Table 7 spends 9 − 8 = 1 pair on detection. Design the alternative: a Part I encoding of 3 bits into 2 modules over {K, C, Y} that maximizes the *number of detectable single-module substitutions*, and compare it with Table 7's choice. Then argue (or refute): given the LDPC layer above, the identity of the unused pair is irrelevant to the stack's overall failure probability under an i.i.d. module-substitution model.

## Further reading

- ISO/IEC 23634:2022, 4.4.1.2 and Table 7; clause 6.1 step e).
- F. J. MacWilliams and N. J. A. Sloane, *The Theory of Error-Correcting Codes*, North-Holland, 1977 — distance, detection, and codes over non-binary alphabets (ch. 1).
- R. G. Gallager, *Low-Density Parity-Check Codes*, MIT Press, 1963 — the algebraic layer above this bootstrap; developed in [03-ldpc-coding-theory.md](03-ldpc-coding-theory.md).
- Siblings: [02-information-density.md](02-information-density.md) (what `Nc` buys), [08-colour-space-geometry.md](08-colour-space-geometry.md) (the classifier this chapter treats as fixed), [11-adversarial-channel.md](11-adversarial-channel.md) (Part I as an attack surface).
