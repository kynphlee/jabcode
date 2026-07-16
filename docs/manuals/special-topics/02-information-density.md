# 2. Information density and the capacity ledger

<!-- objective: The reader can derive the net capacity of any symbol from first principles — side-version geometry, fixed-pattern overhead, palette and metadata reservations, ECC rate — and evaluate the open Annex D reconciliation item as a worked audit. -->

**Where it lives.** `getSymbolCapacity` (encoder.c:651-689) and `getOptimalECC` (encoder.c:698-714); the side-size macro `VERSION2SIZE` (jabcode.h:53); the alignment-pattern census `jab_ap_num` (encoder.h:285-292); ISO/IEC 23634:2022 4.3.5 (the data-module formulas), Table 1 (the printed capacities), 5.4.1 (the rate identity), Annex D (the worked example); the JC-T findings register entry on the Annex D reconciliation ([../developers-manual/04-encoder.md](../developers-manual/04-encoder.md) §4.4.3). <!-- anchor: encoder.c:651-689 --> <!-- anchor: jabcode.h:53 --> <!-- anchor: encoder.h:285-292 --> <!-- anchor: ISO 23634 4.3.5 -->

## The problem

Every claim anyone makes about JAB Code capacity — the marketing headline, the operator's sizing table, the encoder's decision to accept or reject a payload — reduces to one piece of arithmetic: count the modules, subtract everything that is not data, multiply by bits per module, and take the error-correction toll. We call this the **capacity ledger**. It is short enough to do by hand and consequential enough that the standard, the reference implementation, and the standard's own worked example must all agree on it.

They almost do. The ledger reproduces ISO Table 1's 8-colour column exactly, row for row. It does **not** reproduce the Annex D worked example, and Table 1 itself quietly disagrees with Annex D — an inconsistency the developer's manual logged as an open finding and this chapter now works through as an audit, at Evaluate depth: we lay out both computations, isolate the exact size and location of the gap, and enumerate what evidence would close it. We do not invent a resolution.

## Theory

### Density: bits per module

From chapter [1](01-notation.md)'s density law (stated here, derived there): a module drawn from an alphabet of `q` equiprobable colours carries the base-2 log of `q` bits, and with colour count 2 to the power (Nc+1) that is Nc+1 bits per module — 2 bits at 4 colours, 3 bits at 8, up to 8 bits at 256. The source computes this as `nb_of_bpm` at encoder.c:672. Everything below is therefore counted in **modules** first and converted to bits last. <!-- anchor: encoder.c:672 -->

### Geometry: the 4v+17 lattice

A side-version `v` between 1 and 32 fixes a side length in modules:

$$
s \;=\; 4v + 17
$$

verbatim in source as `VERSION2SIZE(x) (x * 4 + 17)`, and in the spec's words: "The side size increases in steps of 4 modules, from 21 modules in Side-Version 1, to 145 modules in Side-Version 32." Versions are per-axis, so a symbol has `sx × sy` modules; squares have `sx = sy`. <!-- anchor: jabcode.h:53 --> <!-- anchor: ISO 23634 4.3.5 -->

### The overhead ledger — full derivation

ISO 4.3.5 gives the non-data reservations by formula (quoted here from the verified extract; the extraction flattened the floor/ceiling brackets, and we restore the floor reading, which we then validate against both the source table and Table 1):

- **Finder patterns.** FPrimary = 4×17 for a primary symbol (four corner patterns of 17 modules each); FSecondary = 4×7 for a secondary symbol.
- **Alignment patterns.** With DFCB = 4 (the distance-to-border constant; compare `DISTANCE_TO_BORDER 4`, jabcode.h:39) and MDBA = 16, the per-axis count is ax = max(0, (SideSizex − DFCB×2 + 1)/MDBA − 1), read with a floor on the division; ay analogously; and the module cost is a = ((ax+2)×(ay+2) − 4)×7. The structure is transparent: ax+2 counts alignment columns including the two finder columns, the product counts all lattice crossings, the four corners are already paid for as finders, and each remaining pattern costs 7 modules. <!-- anchor: ISO 23634 4.3.5 --> <!-- anchor: jabcode.h:39 -->
- **Embedded palettes.** CPalette = (NumberOfModuleColor − 2)×4: four copies, each showing every colour except two (the two colours already exhibited by metadata Part I's fixed alphabet), capped in code at 64 colours — `enc->color_number > 64 ? (64-2)*COLOR_PALETTE_NUMBER : ...` (encoder.c:664), a cap that chapter [8](08-colour-space-geometry.md) treats as geometry. <!-- anchor: encoder.c:664 -->
- **Metadata.** Metadata = MetadataLength / log2(NumberOfModuleColor) × log2(2) in the flattened extract — a bits-to-modules conversion whose exact bracketing did not survive extraction. The code's version is precise: Part II bits divided by bits-per-module, rounded up, plus exactly `MASTER_METADATA_PART1_MODULE_NUMBER` = 4 modules for Part I (encoder.c:676-685) — and, crucially, **zero** when the symbol is in default mode, because `isDefaultMode` (8 colours and ECC level 3 or unset) suppresses master metadata entirely (encoder.c:591-598, 613-616). <!-- anchor: encoder.c:676-685 --> <!-- anchor: encoder.c:591-598 -->

The ledger, then, for a primary symbol:

$$
\text{data modules} \;=\; s_x s_y \;-\; a \;-\; C_{\text{Palette}} \;-\; F_{\text{Primary}} \;-\; \text{Metadata}
$$

and raw data bits are data modules times Nc+1. This is precisely the return expression of `getSymbolCapacity` (encoder.c:687), term for term. <!-- anchor: encoder.c:687 -->

### Reproducing Table 1: the SV1 8-colour row, then all of them

Table 1's preamble reads: "The capacities listed in Table 1 are based on the recommended error correction level 3 for square symbols, and a default of 8 colours." Note what that buys: 8 colours plus level 3 **is** the default mode, so the metadata term is zero — the preamble's ECC clause is doing module accounting, not rate arithmetic. <!-- anchor: ISO 23634 Table 1 --> <!-- anchor: encoder.c:591-598 -->

Side-version 1, 8 colours, worked in full:

$$
s = 21,\quad s^2 = 441,\qquad
a_x = \max\!\left(0,\ \left\lfloor \tfrac{21 - 8 + 1}{16} \right\rfloor - 1\right) = 0,\qquad
a = \big((0+2)(0+2) - 4\big)\times 7 = 0
$$

$$
\text{data modules} = 441 - 0 - \underbrace{(8-2)\times 4}_{24} - \underbrace{4 \times 17}_{68} - 0 \;=\; 349,
\qquad
\text{bits} = 349 \times 3 = 1047
$$

Table 1 prints 349 data modules and 1047 bits for this row. **Exact agreement.** And not only for this row — every extracted 8-colour row reproduces:

| SV | side | modules | a | palette | finder | metadata | data (computed) | data (Table 1) | bits (computed) | bits (Table 1) |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 21 | 441 | 0 | 24 | 68 | 0 | 349 | 349 | 1047 | 1047 |
| 5 | 37 | 1369 | 0 | 24 | 68 | 0 | 1277 | 1277 | 3831 | 3831 |
| 10 | 57 | 3249 | 84 | 24 | 68 | 0 | 3073 | 3073 | 9219 | 9219 |
| 16 | 81 | 6561 | 147 | 24 | 68 | 0 | 6322 | 6322 | 18966 | 18966 |
| 20 | 97 | 9409 | 224 | 24 | 68 | 0 | 9093 | 9093 | 27279 | 27279 |
| 26 | 121 | 14641 | 420 | 24 | 68 | 0 | 14129 | 14129 | 42387 | 42387 |
| 32 | 145 | 21025 | 539 | 24 | 68 | 0 | 20394 | 20394 | 61182 | 61182 |

(The `a` column doubles as a check that the floored ax formula and the source's `jab_ap_num` table agree: ax+2 equals `jab_ap_num[v-1]` at every extracted version.) <!-- anchor: encoder.h:285-292 --> <!-- anchor: ISO 23634 Table 1 -->

The 4-colour column is a different story, and we report it as found rather than force agreement. Every extracted 4-colour entry sits exactly 11 modules below its 8-colour sibling (338 vs 349, 1266 vs 1277, …, 20383 vs 20394). Under the ledger, moving from 8 to 4 colours returns 16 palette modules and — since 4 colours is not the default mode — spends M metadata modules, so the tabled difference forces M = 27. The source's arithmetic spends M = 4 + ⌈38/2⌉ = 23 (Part I four modules, Part II 38 encoded bits at 2 bits per module). A uniform 4-module gap, in the metadata term, across the whole column; the flattened 4.3.5 metadata formula is the natural suspect locus, but the extraction cannot arbitrate. We flag it and move on — the audit below shows Annex D has a sibling problem in the 8-colour default mode itself, where no metadata term can hide it. <!-- anchor: encoder.c:676-685 --> <!-- anchor: ISO 23634 Table 1 -->

One naming tension, recorded honestly: Clause 3 defines `Pn` as net payload, and 5.4.1 sets R = Pn/Pg — yet Table 1's bit column, labelled with Pn, holds the **pre-ECC** raw bit counts our ledger produces. Whichever way the spec intends the label, the numbers are unambiguous, and they are the ledger's.

### The rate toll, and the 7.6 kB headline

Error correction converts raw capacity into deliverable payload. At level 3 the weights are (wc, wr) = (4, 9) (encoder.h:234; Table 20 prints the level-3 row as recovery 6, wc 4, wr 9, R 0,55 (as printed) — chapter [3](03-ldpc-coding-theory.md) derives why R = 1 − wc/wr = 5/9 ≈ 0.556 and examines the printed rounding). The gross payload must be a multiple of wr that fits the capacity — the source comment says it directly: "max_gross_payload = floor(capacity / wr) * wr" (encoder.c:705). So for SV1 at 8 colours, level 3:

$$
P_g = \left\lfloor \tfrac{1047}{9} \right\rfloor \times 9 = 1044,
\qquad
K = P_g \cdot \tfrac{w_c}{w_r} = 464,
\qquad
P_n = P_g - K = 580 \ \text{bits} \approx 72\ \text{bytes}
$$

<!-- anchor: encoder.c:698-714 --> <!-- anchor: encoder.h:234 --> <!-- anchor: ISO 23634 Table 20 -->

At the top of the range, SV32 at 8 colours carries 61182 raw bits — 7647.75 bytes, the familiar **"about 7.6 kB per symbol"** headline. That headline is the *raw* Table 1 figure. Apply the level-3 toll and the deliverable message is

$$
P_g = 61182,\qquad K = 27192,\qquad P_n = 33990 \ \text{bits} \approx 4.2\ \text{kB}
$$

(61182 happens to be an exact multiple of 9, so nothing is lost to the flooring). Both numbers are true; they answer different questions — what the modules hold versus what the user gets back — and precision about which one is being quoted is most of the capacity conversation. Operator-level sizing guidance built on these figures is JC-U's [../operators-manual/02-capacity-size-robustness.md](../operators-manual/02-capacity-size-robustness.md).

## Back to the code

`getSymbolCapacity` (encoder.c:651) is the ledger verbatim: `nb_modules_fp` is 4×17 for the master and 4×7 for a slave (the FSecondary case); `nb_modules_palette` applies the 64-colour cap; `nb_modules_ap` computes `(number_of_aps_x * number_of_aps_y - 4) * 7` from the `jab_ap_num` census rather than the ax formula (the two agree, per the table above); the metadata block implements the ceiling division plus the four Part I modules; and the return multiplies by `nb_of_bpm`. `getOptimalECC` then searches (wc, wr) pairs with wc from 3 to 8 and wr from wc+1 to 9 — exactly Annex B's recommendation "It is recommended to select wc ≥ 3 and wr ≥ wc+1" as search bounds — minimizing slack over the flooring identity above. <!-- anchor: encoder.c:651-689, 698-714 --> <!-- anchor: ISO 23634 Annex B -->

### Case study: the Annex D audit

Annex D encodes the message "JAB Code 2016!" — 78 message bits — into a side-version 1, 8-colour, default-mode symbol, and states "Pg = 1071 and K = 476". The developer's manual verified the 78-bit token stream against the source tables symbol by symbol and logged the payload figures as an open reconciliation item ([../developers-manual/04-encoder.md](../developers-manual/04-encoder.md) §4.4.3). Here is the full audit. <!-- anchor: ISO 23634 Annex D -->

**The code's side.** Default mode, so metadata is zero; the ledger gives 349 data modules, capacity 1047 bits; level 3 gives Pg = 1044 and K = 464, as derived above.

**Annex D's side, taken seriously on its own terms.** Its figures are *internally* coherent: 1071 is an exact multiple of 9, and 1071 × 4/9 = 476 exactly — so Pg = 1071, K = 476 is a legitimate level-3 gross/check pair. Moreover 1071 = 357 × 3: Annex D is implicitly claiming **357 data modules** where the ledger, the source, and Table 1 all say 349.

**The gap, located.** Eight modules, 24 bits. Working backward through the ledger, one — and only one — single-term change produces 357: count the palette as 16 modules instead of 24, i.e. 441 − 68 − 16 = 357. Sixteen is what four palette copies of four modules each would cost — as if each copy exhibited only four colours rather than the six that CPalette = (8−2)×4 prescribes. We state this as the arithmetically unique single-term candidate, not as a claim about what Annex D's authors did.

**Corroboration that Annex D, not the extraction, is shaky.** The annex is internally inconsistent on its own ledger: D.3 states a metadata length of 10 modules while D.4 says default mode needs none — a verified JC-T finding. An annex that cannot agree with itself about the metadata term is a weak witness against a ledger that Table 1 (in the same document) confirms to the bit at every extracted version.

**What would resolve it** — evidence, in decreasing order of decisiveness:

1. A bit-level symbol interchange test: encode the Annex D message with an independent, conforming implementation and compare emitted symbols against this codebase. (Weak in practice: the ecosystem's implementations descend from the same reference code that computes 1044.)
2. An unflattened, authoritative reading of Annex D's module accounting and of the 4.3.5 metadata formula — specifically whether any draft lineage counted CPalette as 4×4.
3. An ISO defect report or errata against Annex D, which the D.3/D.4 contradiction alone would justify.

**The operative position**, inherited from JC-T and endorsed here: wire compatibility is defined by the reference ecosystem's arithmetic, so the regression contract for any encoder change is the byte-identical round trip of the Annex D *message* (guarded by `test-roundtrip`), not reproduction of the printed Pg. The printed figure is retained as an open item, not as a target. <!-- anchor: ISO 23634 Annex D --> <!-- anchor: encoder.c:651-689 -->

## Exercises

**1 (guided).** Run the ledger for side-version 5 at 4 colours, ECC level 3, using the source's metadata arithmetic (Part I: 4 modules; Part II: 38 encoded bits). Compare with Table 1's printed 1266 data modules / 2532 bits. What do you find, and where have you seen the gap before?

<details><summary>Solution</summary>

Side 37, 1369 modules; a = 0 (two alignment positions per axis, all four crossings are finders); palette (4−2)×4 = 8; finders 68; metadata 4 + ⌈38/2⌉ = 23. Data modules 1369 − 0 − 8 − 68 − 23 = 1270, bits 2540. Table 1 prints 1266/2532 — four modules fewer, the same uniform 4-module metadata gap the chapter found across the whole 4-colour column (Table 1 behaves as if metadata costs 27 modules). Both computations are internally consistent; the flattened 4.3.5 metadata formula prevents arbitration from the extract alone.

</details>

**2 (guided → open).** Rectangles: run the ledger for symbol version (x, y) = (2, 1) at 8 colours, level 3. Then decide — from `isDefaultMode`'s actual test (encoder.c:591-598) — whether this rectangular symbol is in default mode, and justify how a decoder can learn the side sizes if no metadata is placed.

<details><summary>Solution sketch</summary>

Sides 25 × 21 = 525 modules; per-axis alignment counts are 2 and 2, so a = 0; data = 525 − 68 − 24 − 0 = 433 modules = 1299 bits; Pg = ⌊1299/9⌋×9 = 1296, K = 576, net 720 bits = 90 bytes. `isDefaultMode` tests only colour count and ECC level — shape does not disqualify — so no metadata is placed (note Table 1's preamble says its capacities assume *square* symbols, a presentational restriction, not a gate in code). The decoder recovers side sizes geometrically during detection (finder/alignment positions fix the grid; chapter [9](09-detection-robust-estimation.md) derives the side-size snap), which is exactly why V can be absent.

</details>

**3 (open — the audit as an exercise).** Annex D implies 357 data modules; the ledger yields 349. The chapter exhibited "palette counted as 16" as the unique single-term reconciliation. Explore multi-term reconciliations: enumerate all ways to distribute the 8-module discrepancy across two or more ledger terms such that each term change has a plausible structural reading (e.g., finder patterns at 15 modules each? a nonzero alignment term at SV1?), and argue why each is or is not more credible than the single-term candidate. State clearly which parts of your argument are arithmetic and which are conjecture.

<details><summary>Discussion</summary>

There is no checked solution — the item is genuinely open. A strong answer will observe that FPrimary = 4×17 and a = 0 at SV1 are each confirmed independently (the finder geometry by Clause 4.3 and the source's placement code; the alignment count by both the formula and `jab_ap_num`), which concentrates suspicion on the palette and metadata terms; and that D.3's phantom 10-module metadata plus a 16-module palette overshoots (441 − 68 − 16 − 10 = 347 ≠ 357), so Annex D's own D.3 figure cannot be combined with the palette candidate — the annex's numbers do not form any consistent ledger.

</details>

## Further reading

- C. E. Shannon, *A Mathematical Theory of Communication*, 1948 — why capacity minus redundancy is the right frame in the first place.
- R. G. Gallager, *Low-Density Parity-Check Codes*, 1963 — the source of the rate arithmetic applied here; the derivation is chapter [3](03-ldpc-coding-theory.md).
- D. J. C. MacKay, *Information Theory, Inference, and Learning Algorithms*, 2003 — chapter-length treatments of both the counting and the coding sides of this ledger.
