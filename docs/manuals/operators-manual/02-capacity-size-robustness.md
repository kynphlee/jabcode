# 2. Capacity, size and robustness

<!-- objective: An operator can select a side-version and ECC level for a given payload size and damage expectation, using the capacity and robustness tables, such that the payload fits with the required recovery margin. -->

**In this chapter you will** learn to choose a symbol size (side-version) and an error-correction level for a real payload — so that the message fits, and keeps fitting even after the label meets a forklift.

**You should already** know what a module is, how colour count sets bits per module, and what a side-version is — all from [01-what-a-jab-code-is.md](01-what-a-jab-code-is.md).

## Side-versions: how big is the canvas

Recall the size formula: side length in modules = `4 × version + 17`, for versions 1 through 32 (`VERSION2SIZE(x)` in the public header). That gives you a canvas from 21 × 21 up to 145 × 145 modules. Width and height versions are chosen independently, but this chapter works with square symbols, because that is what the standard's capacity table describes.
<!-- anchor: src/jabcode/include/jabcode.h:53 -->
<!-- anchor: ISO 23634 4.3.5 -->

## How much fits: the capacity table

The standard's Table 1 lists, for each side-version, how many data modules a square symbol has and what net payload `Pn` (in bits) that yields, at 4 and at 8 colours. Its preamble is worth quoting, because it states the assumptions: "The capacities listed in Table 1 are based on the recommended error correction level 3 for square symbols, and a default of 8 colours." Here are the rows you will use most, for primary symbols:

| Side-version | Side (modules) | Data modules (4c) | Data modules (8c) | `Pn` bits (4c) | `Pn` bits (8c) |
|---|---|---|---|---|---|
| 1 | 21 | 338 | 349 | 676 | 1047 |
| 5 | 37 | 1266 | 1277 | 2532 | 3831 |
| 10 | 57 | 3062 | 3073 | 6124 | 9219 |
| 16 | 81 | 6311 | 6322 | 12622 | 18966 |
| 20 | 97 | 9082 | 9093 | 18164 | 27279 |
| 26 | 121 | 14118 | 14129 | 28236 | 42387 |
| 32 | 145 | 20383 | 20394 | 40766 | 61182 |

<!-- anchor: ISO 23634 Table 1 (default metadata, EC level 3, square primary symbols) -->

Two headline numbers to keep in your pocket:

- The largest single primary symbol carries 61182 bits ≈ 7.6 kB. One symbol can hold a serious document, not just a URL.
- Secondary symbols carry slightly *more* than a primary of the same size, because they have less overhead — for example, a side-version 1 secondary at 8 colours holds 1167 bits against the primary's 1047, and the largest tabulated value overall is a secondary's 61302 bits. This becomes useful in [03-cascading.md](03-cascading.md).

<!-- anchor: ISO 23634 Table 1 -->

## How tough it is: error-correction levels

Every JAB Code is protected by an error-correcting code (an LDPC code — the details live in the Developer's Manual; you only need its dial). The dial is the **ECC level**, 1 through 10. The standard is direct about it: "The error correction level shall be selectable between 1 and 10, as listed in Table 20. The default error correction level shall be 3." And on what the percentages mean: "Table 20 shows the recovery capability of the bit errors in more than 95 % of cases."
<!-- anchor: ISO 23634 5.4.1 -->

Here is Table 20 in full. `wc` and `wr` are the internal code parameters each level sets; R is the code rate — the fraction of the symbol's gross bit capacity left for your payload, defined as R = `Pn`/`Pg` (net over gross). Values such as 0,63 keep the decimal commas as printed in the standard.

| Level | Recovery ≈ % | `wc` | `wr` | R |
|---|---|---|---|---|
| 1 | 4 | 3 | 8 | 0,63 |
| 2 | 5 | 3 | 7 | 0,57 |
| 3 (default) | 6 | 4 | 9 | 0,55 |
| 4 | 7 | 3 | 6 | 0,50 |
| 5 | 8 | 4 | 7 | 0,43 |
| 6 | 9 | 4 | 6 | 0,34 |
| 7 | 10 | 3 | 4 | 0,25 |
| 8 | 11 | 4 | 5 | 0,20 |
| 9 | 12 | 5 | 6 | 0,17 |
| 10 | 14 | 6 | 7 | 0,14 |

<!-- anchor: ISO 23634 Table 20 -->

### Reading the table like an operator

At the default level 3, roughly 6 % of the symbol's bits can be read wrongly — scratches, stains, glare — and the message still comes back intact, in more than 95 % of cases. Turn the dial up and you buy more tolerance, but you pay in capacity: notice how R falls from 0,63 at level 1 to 0,14 at level 10. At level 10, only about a seventh of the symbol's raw bits are your payload; the rest is armour. There is no free lunch — only a well-chosen trade.
<!-- anchor: ISO 23634 Table 20; ISO 23634 5.4.1 -->

## The same tables inside the code

You never need to read the source to use the dial, but it is reassuring to see that the implementation and the standard agree. The encoder's level table is:

```c
static const jab_int32 ecclevel2wcwr[10][2] = {{3, 8}, {3, 7}, {4, 9}, {3, 6}, {4, 7}, {4, 6}, {3, 4}, {4, 5}, {5, 6}, {6, 7}};
```

with the source comment "Per ISO/IEC 23634:2022 Table 20. ECC levels run 1..10 (default 3)". Row by row this matches Table 20's `wc`/`wr` columns exactly, with zero-based indexing: row 0 is level 1, row 2 is the default level 3 pair (4, 9).
<!-- anchor: src/jabcode/encoder.h:234 (table), encoder.h:230 (comment) -->

The companion rate table is:

```c
static const jab_float ecclevel2coderate[11] = {0.55f, 0.63f, 0.57f, 0.55f, 0.50f, 0.43f, 0.34f, 0.25f, 0.20f, 0.17f, 0.14f};
```

One honest flag for the curious: **the two code tables are indexed differently.** `ecclevel2wcwr` has ten rows indexed by level − 1, while `ecclevel2coderate` has eleven entries indexed by the level itself, with an extra slot 0 holding 0.55 — the same rate as level 3, consistent with the writer tool's convention that "level 0 means using the default level". No numeric mismatch exists against Table 20 — entries 1 through 10 reproduce the R column exactly — but if you ever read the source, keep the off-by-one convention in mind.
<!-- anchor: src/jabcode/encoder.h:226 -->
<!-- anchor: docs/manuals/corpus-model.md §3.4 (--ecc-level: "level 0 means using the default level"); src/jabcode/include/jabcode.h:35 (DEFAULT_ECC_LEVEL 3) -->

## Worked example: fitting a 2000-byte payload

Suppose you must encode a 2000-byte record — 16000 bits — on a label, in the default 8-colour mode.

1. **Baseline at the default level 3.** Scan the 8-colour `Pn` column of the capacity table for the first value ≥ 16000: side-version 16 offers 18966 bits. It fits with about 18 % headroom. Decision so far: side-version 16 (81 × 81 modules), ECC level 3.
   <!-- anchor: ISO 23634 Table 1 -->
2. **Now assume a harsher life** — outdoor exposure, so you want level 5 (≈ 8 % recovery). Table 1 only tabulates level 3, but the rate column of Table 20 lets you estimate: capacity scales roughly with R, and 0,43 / 0,55 ≈ 0.78. Side-version 16 then offers roughly 18966 × 0.78 ≈ 14800 bits — no longer enough for 16000.
   <!-- anchor: ISO 23634 Table 20 (R column) -->
3. **Step the size up.** Side-version 20 at level 3 holds 27279 bits; scaled by 0.78 that is roughly 21300 bits at level 5 — a comfortable fit. Decision: side-version 20 (97 × 97 modules), ECC level 5.
   <!-- anchor: ISO 23634 Table 1 -->
4. **Let the encoder be the referee.** The scaling in steps 2–3 is an estimating technique, not gospel — metadata overhead shifts the exact numbers. The final authority is the encoder itself: `jabcodeWriter` exits with status 1 if the message does not fit the parameters you chose, and 0 on success, so an over-tight choice fails loudly rather than silently.
   <!-- anchor: src/jabcodeWriter/jabwriter.c:431 (exit status "0: success | 1: failure") -->

This walk-through is constructed from the quoted tables and source; the manual-building session cannot execute this fork's tools, so no console output is reproduced.

## Try it

1. How many modules per side does side-version 10 give you?
2. You need roughly 10 % bit-error recovery. Which ECC level do you pick, and what `(wc, wr)` pair does it set?
3. Will a 5000-byte payload fit a side-version 20 primary symbol at 8 colours and ECC level 3?
4. In one sentence: what does R = `Pn`/`Pg` tell you?

<details><summary>Answers</summary>

1. `4 × 10 + 17 = 57` modules per side.
   <!-- anchor: src/jabcode/include/jabcode.h:53 -->
2. Level 7 (≈ 10 % recovery), which sets `(wc, wr)` = (3, 4).
   <!-- anchor: ISO 23634 Table 20 -->
3. No. 5000 bytes = 40000 bits, but side-version 20 at 8 colours offers 27279 bits. The next tabulated fit is side-version 26 with 42387 bits.
   <!-- anchor: ISO 23634 Table 1 -->
4. R is the fraction of the symbol's gross bit capacity that remains for your payload after error-correction overhead — the price tag of robustness.
   <!-- anchor: ISO 23634 5.4.1 (R = Pn/Pg) -->

</details>

## Where to go next

Next: [03-cascading.md](03-cascading.md) — what to do when even side-version 32 is not enough, or when the space on your label is anything but square. Deeper: the LDPC machinery behind Table 20 is covered in the Developer's Manual (JC-T), forthcoming.
