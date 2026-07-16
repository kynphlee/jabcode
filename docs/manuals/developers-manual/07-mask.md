# 7. `mask.c` — masking

<!-- objective: A maintainer can apply the mask-selection algorithm — 8 generators, penalty rules 1-3, joint evaluation across cascades — and predict the selected reference for a given matrix. -->

**Responsibility.** `src/jabcode/mask.c` (455 lines) implements clause 5.8: the eight mask-pattern generators, penalty rules 1-3, joint mask evaluation across all symbols of a cascade, mask application, and the decoder-side demask. ISO ground: 5.8, Tables 22 (generators) and 23 (penalty weights). <!-- anchor: mask.c:10-11; corpus-model.md §2.3 -->

## 7.1 Public surface

| Item | Signature / value | Notes |
|---|---|---|
| `W1` / `W2` / `W3` | `#define W1	100` / `#define W2	3` / `#define W3	3` | ISO Table 23, verbatim: "W1 = 100, W2 = 3 and W3 = 3" — exact match <!-- anchor: mask.c:22-24; ISO 23634 Table 23 --> |
| `maskCode` | `jab_int32 maskCode(jab_encode* enc, jab_code* cp)` | evaluates all 8 patterns jointly, applies the winner, returns its reference or −1 <!-- anchor: mask.c:363 --> |
| `maskSymbols` | `void maskSymbols(jab_encode* enc, jab_int32 mask_type, jab_int32* masked, jab_code* cp)` | dual-use: with `(masked, cp)` writes a trial canvas; with `(0, 0)` masks the symbol matrices in place <!-- anchor: mask.c:289 --> |
| `demaskSymbol` | `void demaskSymbol(jab_data* data, jab_byte* data_map, jab_vector2d symbol_size, jab_int32 mask_type, jab_int32 color_number)` | decoder side, in-place over the serialized module stream <!-- anchor: mask.c:410 --> |
| `evaluateMask` | `jab_int32 evaluateMask(jab_int32* matrix, jab_int32 width, jab_int32 height, jab_int32 color_number)` | sum of rules 1-3 <!-- anchor: mask.c:277-280 --> |
| `applyRule1/2/3` | `jab_int32 applyRule1(jab_int32* matrix, jab_int32 width, jab_int32 height, jab_int32 color_number)` etc. | rule 1 takes `color_number`; rules 2-3 are colour-count-agnostic <!-- anchor: mask.c:34, 192, 219 --> |
| pattern count | `#define NUMBER_OF_MASK_PATTERNS	8` | <!-- anchor: jabcode.h:29 --> |
| default reference | `#define DEFAULT_MASKING_REFERENCE 		7` | <!-- anchor: jabcode.h:36 --> |

## 7.2 The eight generators vs ISO Table 22

ISO Table 22 defines the mask value at module (x, y), "(x, y) = (0, 0) for the upper left module", applied "through the bitwise XOR operation between the colour index of the data module and the colour index of the corresponding module in the mask pattern". (In the extract "2Nc+1" denotes 2^(Nc+1); superscripts were lost.) Source (encoder side, `maskSymbols`; the decoder's `demaskSymbol` cases are identical):

| Ref | ISO Table 22 formula (extract) | Source expression | Verdict |
|---|---|---|---|
| 000 | "(x+y) mod 2^(Nc+1)" | `index ^= (x + y) % enc->color_number;` | match <!-- anchor: ISO 23634 Table 22; mask.c:317-319 --> |
| 001 | "x mod 2^(Nc+1)" | `index ^= x % enc->color_number;` | match <!-- anchor: mask.c:320-322 --> |
| 010 | "y mod 2^(Nc+1)" | `index ^= y % enc->color_number;` | match <!-- anchor: mask.c:323-325 --> |
| 011 | "((x div 2) + (y div 3)) mod 2^(Nc+1)" | `index ^= (x / 2 + y / 3) % enc->color_number;` | match <!-- anchor: mask.c:326-328 --> |
| 100 | "((x div 3) + (y div 2)) mod 2^(Nc+1)" | `index ^= (x / 3 + y / 2) % enc->color_number;` | match <!-- anchor: mask.c:329-331 --> |
| 101 | "((x+y) div 2 + (x+y) div 3) mod 2^(Nc+1)" | `index ^= ((x + y) / 2 + (x + y) / 3) % enc->color_number;` | match <!-- anchor: mask.c:332-334 --> |
| 110 | "(((x × x × y) mod 7) + ((2 × x × x + 2 × y) mod 19)) mod 2^(Nc+1)" | `index ^= ((x*x * y) % 7 + (2*x*x + 2*y) % 19) % enc->color_number;` | match <!-- anchor: mask.c:335-337 --> |
| 111 (default) | "(((x × y × y) mod 5) + ((2 × x + y × y) mod 13)) mod 2^(Nc+1)" | `index ^= ((x * y*y) % 5 + (2*x + y*y) % 13) % enc->color_number;` | match <!-- anchor: mask.c:338-340 --> |

All eight generators match Table 22 exactly; `enc->color_number` *is* 2^(Nc+1), integer division *is* div, and application is XOR on the colour index. Coordinates are **per-symbol local**: `maskSymbols` iterates each symbol's own (x, y) from its own origin, so every symbol of a cascade is masked as if (0, 0) were its own upper-left module. <!-- anchor: mask.c:308-341 -->

**Only data modules are masked**: the encoder masks where `data_map != 0` (encoder convention: 1 = data — [04-encoder.md](04-encoder.md) §4.6) and, on the trial canvas, copies non-data modules through unmasked. FP/AP/palette/metadata modules therefore keep their placed colours in every trial, but still participate in penalty scoring. <!-- anchor: mask.c:312-351 -->

## 7.3 Penalty rule 1 vs Table 23 — the divergent one

ISO Table 23 rule 1: a finder-pattern-lookalike "in row/column" scores W1. The live implementation counts only *simultaneous* row-and-column lookalikes: for each interior module it requires the alternating 5-module sequence `c1,c2,c1,c2,c1` **both** horizontally and vertically centred on the same module (a cross), for any of the four FP colour pairs; each hit scores 1, total × W1. A per-row/per-column variant — separate horizontal and vertical 5-module window checks — exists immediately above **but is commented out** (mask.c:67-129). The active rule is therefore strictly laxer than a literal per-row/per-column reading of Table 23: an isolated horizontal-only FP lookalike scores 0 here. Both encoder and reference decoders see identical symbols regardless, so this affects mask *choice* quality, not interoperability. <!-- anchor: ISO 23634 Table 23; mask.c:62-183, 67-129 -->

The FP colour pairs per colour mode, verbatim from the function head:

- `color_number == 2` — "two colors: black(000) white(111)": fp0 = (0,1); fp1 = fp2 = fp3 = (1,0). <!-- anchor: mask.c:40-46 -->
- `color_number == 4`: fp0 = (0,3), fp1 = (1,2), fp2 = (2,1), fp3 = (3,0). <!-- anchor: mask.c:47-53 -->
- otherwise: `fpN_c1 = FPN_CORE_COLOR; fpN_c2 = 7 - FPN_CORE_COLOR;` — i.e. (0,7), (0,7), (6,1), (3,4). <!-- anchor: mask.c:54-60; encoder.h:50-53 -->

Note for Nc ≥ 3: the rule keeps the 8-colour core pairs (indices ≤ 7), while the FPs actually placed in those modes use the per-mode indices (`fp2_core_color_index` = 14/30/60/124/252, ...; [04-encoder.md](04-encoder.md) §4.6). Rule 1 in high-colour modes therefore penalizes lookalikes of the *8-colour* finder pattern, not of the patterns actually printed. Stated as an observed property of the source; the extended modes are reserved/non-standard anyway ([16-extended-colour-modes.md](16-extended-colour-modes.md)). <!-- anchor: mask.c:54-60; encoder.h:67-70 -->

## 7.4 Penalty rule 2 — equivalent formulation

ISO: a same-colour block of m × n scores "W2 × (m – 1) × (n – 1)". Source: every 2 × 2 window whose four modules are valid (≠ −1) and same-coloured scores 1; total × W2. A solid m × n block contains exactly (m−1)(n−1) such windows, so the two formulations produce identical totals — an arithmetic identity, and the source form also defines behaviour for non-rectangular blobs (each fully-same 2 × 2 counts once). <!-- anchor: ISO 23634 Table 23; mask.c:192-210 -->

## 7.5 Penalty rule 3 — boundary note

ISO (extract): a same-colour run of "(5+k), k > 0" in row or column scores "W3 + k". Source scans horizontally and vertically; a run counter resets on colour change or on a −1 (outside-canvas) cell, and scores whenever `same_color_count >= 5`:

```c
if(same_color_count >= 5)
    score += W3 + (same_color_count - 5);
```

i.e. it also scores k = 0 (a run of exactly 5 scores W3). Table 23's condition "(5+k), k > 0" excludes exactly-5 runs, so this is a real divergence from the printed rule, not an extraction ambiguity. Its effect is confined to mask *selection*: penalty scoring influences which reference wins, but the chosen reference travels in metadata, so implementations with differing rule-3 thresholds remain mutually decodable — no wire-format impact. Runs are also scored at line end (the trailing-run check), and a broken run's segments score independently. <!-- anchor: ISO 23634 Table 23; mask.c:219-267, 248-249, 262-263 -->

Note rule 3 counts each *maximal* run once per scan direction: the counter accumulates until the colour changes, so a 7-run scores W3 + 2, not three overlapping 5-windows.

## 7.6 Joint cross-symbol evaluation and selection — `maskCode` (mask.c:363)

ISO 5.8: "Apply every available data mask pattern... select the masking pattern, which results in the most balanced module colour distribution and minimizes the occurrence of undesirable patterns", and "All the symbols in a JAB Code... shall be evaluated together"; lowest penalty wins. Source realization:

1. Allocate a full-cascade canvas `masked` of `cp->code_size.x * cp->code_size.y` int32 cells, initialized to −1 (`memset(masked, -1, ...)`; comment "set all bytes in masked as 0xFF"). Cells never covered by a symbol stay −1 and act as run/block barriers in rules 2-3. <!-- anchor: mask.c:368-375 -->
2. For each `t` in 0..7: `maskSymbols(enc, t, masked, cp)` writes every symbol — masked data modules and unmasked non-data modules — into the canvas at its cascade offset (computed from `jab_symbol_pos` and `cp->col_width`/`row_height`), then `evaluateMask` scores the whole canvas. Adjacent symbols are contiguous on the canvas, so rule 2 blocks and rule 3 runs **cross symbol boundaries** — the "evaluated together" requirement. <!-- anchor: ISO 23634 5.8; mask.c:377-392, 293-303 -->
3. Selection: strict `<` against `min_penalty_score` starting at 10000 — the lowest-penalty pattern wins, ties resolve to the lowest reference, and if every pattern scores ≥ 10000, reference 0 wins by default of the initial value. <!-- anchor: mask.c:365-366, 387-391 -->
4. The winner is applied for real: `maskSymbols(enc, mask_type, 0, 0)` mutates the symbol matrices in place. <!-- anchor: mask.c:394-395 -->

The caller stores the reference and re-encodes metadata: `enc->mask_type = mask_reference;` then `updateMasterMetadataPartII` / `placeMasterMetadataPartII` write the 3-bit MSK field ([04-encoder.md](04-encoder.md) §4.5). <!-- anchor: encoder.c:2413-2430 -->

**Default reference 7 reconciled with ISO "111 (default)".** Table 22 marks generator 111 — binary 7 — as the default; `DEFAULT_MASKING_REFERENCE` is 7. In default mode (8 colours, default ECC) the encoder skips evaluation entirely and applies reference 7 unconditionally (`maskSymbols(enc, DEFAULT_MASKING_REFERENCE, 0, 0)`), because a default-mode symbol carries no metadata in which a different reference could be announced; the decoder assumes 7. Numeric identity, no divergence. <!-- anchor: ISO 23634 Table 22; jabcode.h:36; encoder.c:2406-2410 -->

## 7.7 Demask path and the `data_map` interaction

`demaskSymbol` operates on the decoder's serialized module stream, not on a matrix: it walks the symbol grid **column-major** (`for x { for y ... } }`) — the same order the decoder reads raw modules and the encoder places data ([04-encoder.md](04-encoder.md) §4.6 step 7) — and XORs the generator value into `data->data[count]` for every cell with `data_map[y * symbol_width + x] == 0`. **Convention inversion:** decoder-side `data_map` marks data modules with 0 (filled by `fillDataMap`), the exact opposite of the encoder's 1-means-data convention. A guard aborts when `count` exceeds `data->length - 1`. Call site: `decodeSymbol` demasks `raw_module_data` with `color_number = 2^(Nc+1)` before bit expansion and de-interleaving. <!-- anchor: mask.c:410-455, 415-421; decoder.c:1914-1934 -->

## 7.8 Invariants

- Masking is an involution: mask and demask are the same XOR, so applying the generator twice restores the module (`x ^ m ^ m = x`). Generators never produce values ≥ `color_number`, so XOR keeps indices in range only because palette indices and mask values are both < 2^(Nc+1) — for power-of-two colour counts XOR cannot overflow the index space. <!-- anchor: mask.c:315-341, 423-448 -->
- Non-data modules are never masked, in either direction. <!-- anchor: mask.c:313, 419 -->
- The selected reference is always in 0..7 and is the one announced in metadata (or implicitly 7 in default mode). <!-- anchor: mask.c:378; encoder.c:2408, 2428 -->

## 7.9 Failure modes

- `maskCode`: canvas allocation failure → `"Memory allocation for masked code failed"`, returns −1; `generateJABCode` maps it to return 1. <!-- anchor: mask.c:369-374; encoder.c:2413-2420 -->
- `demaskSymbol`: silent early `return` when the data map contains more data cells than the stream has entries — truncation is the caller's problem to detect. <!-- anchor: mask.c:421 -->

## 7.10 Extension points

Penalty policy is fully contained in `applyRule1/2/3` + the weights; generator set and count are pinned by `NUMBER_OF_MASK_PATTERNS` and metadata's 3-bit MSK field — 8 is a wire-format ceiling, not a tunable. Re-enabling the commented per-row/column rule 1 variant (mask.c:67-129) changes only mask selection, never decodability. <!-- anchor: mask.c:22-24, 67-129; jabcode.h:29 -->

## 7.11 Performance notes

`maskCode` renders and scores the full cascade canvas 8 times per encode: O(8 × code area) plus rule costs (rule 1 is the constant-factor heavyweight: up to 4 pair tests × 10 module reads per interior cell). Not on any benchmark's hot list; `bench-cascade` covers it implicitly at N up to 61. <!-- anchor: mask.c:377-392; Makefile:103 -->

## 7.12 Known defects

- Rule 1 diverges from a literal Table 23 row/column reading (cross-only counting; §7.3) and, for Nc ≥ 3, tests 8-colour FP pairs that do not correspond to the placed high-Nc finder colours (§7.3).
- Rule 3 scores runs of exactly 5 (k = 0), which Table 23's "k > 0" condition excludes — a real divergence, mask-choice-only in effect (§7.5).
- The `memset(masked, -1, ...)` comment says "0xFF" while the semantic intent is the int32 value −1 — true on two's-complement (every byte 0xFF), a latent portability footnote only. <!-- anchor: mask.c:375 -->

Mask-selection theory (why these generators, penalty design): Special Topics (JC-S), forthcoming. Pipeline position: [02-codec-pipeline.md](02-codec-pipeline.md); encoder integration: [04-encoder.md](04-encoder.md) §4.1 step 7.
