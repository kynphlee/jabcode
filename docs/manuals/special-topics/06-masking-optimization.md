# 6. Masking as combinatorial optimization

<!-- objective: A mathematically mature reader can formulate mask selection as minimization over an 8-point design space with the Table 23 penalty functional, explain the XOR-group action of the generators on colour indices, and evaluate the two implementation divergences for their effect on the argmin. -->

**Where it lives.** Weights `W1 100` / `W2 3` / `W3 3` and penalty rules `applyRule1/2/3` (`src/jabcode/mask.c:22-24, 34, 192, 219`); the eight generators inside `maskSymbols` (mask.c:308-341) and the selection loop `maskCode` (mask.c:363-399); decoder-side `demaskSymbol` (mask.c:410). Spec ground: ISO/IEC 23634:2022 clause 5.8, Table 22 (generators) and Table 23 (penalty weights). The line-by-line conformance audit — including the two divergences this chapter analyzes — is [../developers-manual/07-mask.md](../developers-manual/07-mask.md) (JC-T ch. 7); its findings are cited as verified input. <!-- anchor: mask.c:22-24, 308-341, 363-399; ISO 23634 5.8, Tables 22-23 -->

## The problem

The data modules' colours are, from the symbol's point of view, adversarially arbitrary: they are whatever the message, ECC and interleaving happen to produce. Nothing prevents a payload from painting a black-white-black-white-black run that a detector will greet as a finder pattern, or a large same-colour block that starves the sampler of local contrast, or long runs that make grid tracking drift. The classical fix, inherited from monochrome matrix symbologies, is *masking*: before printing, combine the data modules with one of a small family of fixed, data-independent patterns, chosen to make the result look statistically bland; announce the choice in metadata; invert it after sampling.

This is a textbook combinatorial optimization in miniature — a finite design space, an explicit objective, and an exhaustive argmin:

$$
t^\ast \;=\; \operatorname*{arg\,min}_{t \in \{0,\ldots,7\}} \; P\!\left(M \oplus m_t\right),
\qquad
P = W_1 P_1 + W_2 P_2 + W_3 P_3 ,
$$

where M is the full-cascade module matrix, m_t the pattern of generator t applied to data modules only, ⊕ the XOR application of §6.2, and P₁-P₃ the three penalty rules of §6.3. The interesting theory is not the minimization (eight evaluations, done) but the design questions around it: why *this* family of patterns, why *this* objective, why eight points suffice, and what happens to the argmin when an implementation's scoring deviates from the printed rules.

## Theory

### 6.1 The generator family: modular colour patterns

Table 22 defines the mask value at module (x, y) — "(x, y) = (0, 0) for the upper left module" — as an integer expression reduced mod `2^(Nc+1)` (the table prints "2Nc+1" for 2^(Nc+1)):

| Ref | Generator (Table 22) | Character |
|---|---|---|
| 000 | (x + y) mod 2^(Nc+1) | diagonal gradient |
| 001 | x mod 2^(Nc+1) | horizontal gradient |
| 010 | y mod 2^(Nc+1) | vertical gradient |
| 011 | ((x div 2) + (y div 3)) mod 2^(Nc+1) | 2×3-blocked gradient |
| 100 | ((x div 3) + (y div 2)) mod 2^(Nc+1) | 3×2-blocked gradient |
| 101 | ((x+y) div 2 + (x+y) div 3) mod 2^(Nc+1) | coarse diagonal |
| 110 | (((x·x·y) mod 7) + ((2·x·x + 2·y) mod 19)) mod 2^(Nc+1) | polynomial, periods 7/19 |
| 111 (default) | (((x·y·y) mod 5) + ((2·x + y·y) mod 13)) mod 2^(Nc+1) | polynomial, periods 5/13 |

<!-- anchor: ISO 23634 Table 22 -->

The family is stratified by spatial frequency: three exact linear gradients, three integer-divided (hence block-constant) gradients, and two quadratic-polynomial patterns whose inner moduli 5, 7, 13, 19 are chosen coprime to each other and to the row/column strides, so their level sets avoid short periodicities. The source implements all eight verbatim, with `enc->color_number` playing 2^(Nc+1) (mask.c:315-341; cell-by-cell match table in JC-T §7.2). <!-- anchor: mask.c:315-341 -->

### 6.2 Application as a group action — the structure, precisely

Clause 5.8 applies the mask "through the bitwise XOR operation between the colour index of the data module and the colour index of the corresponding module in the mask pattern". Two algebraic remarks make this exact rather than hand-wavy.

**The index space is a GF(2) vector space, not a cyclic group.** The colour count is always a power of two — `2^(Nc+1)` for Nc = 0..7 (jabcode.h:105) — so a colour index is an (Nc+1)-bit vector, and the natural home of XOR is

$$
G \;=\; (\mathbb{Z}_2)^{\,N_c+1},
$$

the elementwise-addition group of bit vectors. The generator formulas are *computed* with integer arithmetic mod 2^(Nc+1) — i.e. in the cyclic group Z_{2^(Nc+1)} — but the resulting value is then *applied* by XOR, i.e. as addition in G. The two groups share the same underlying set {0, ..., 2^(Nc+1) − 1} and differ in structure (carry vs no carry); the design uses the cyclic structure to generate smooth spatial patterns and the GF(2) structure to combine them with data. Because Nc+1 bits reduced mod 2^(Nc+1) always fit in Nc+1 bits, XOR can never produce an out-of-range index — this closure is exactly the "for power-of-two colour counts XOR cannot overflow the index space" invariant recorded in JC-T §7.8, and it is the answer to the natural worry about non-power-of-two counts: there are none; the wire format admits only powers of two. <!-- anchor: jabcode.h:105; ISO 23634 5.8; mask.c:315-341 -->

**Masking is a free action by involutions.** Fix a mask pattern m: the map on symbol matrices is

$$
\Phi_m(M)(x,y) \;=\; M(x,y)\ \oplus\ m(x,y)
\quad\text{(data modules only)},
$$

a translation in G at each cell. Every element of (Z₂)ⁿ is its own inverse, so

$$
\Phi_m \circ \Phi_m = \mathrm{id},
$$

which is why `demaskSymbol` is literally the same XOR as `maskSymbols` (mask.c:423-448) — mask and demask are one function. Moreover the action of G on indices at a single cell is free and transitive: for any observed colour c and any target c′ there is exactly one mask value carrying c to c′. Consequences: (i) masking is information-lossless and bijective on the data-module colour configuration space, so it costs zero capacity beyond the 3-bit MSK field; (ii) no mask value is "special" at the level of a single module — all the differentiation between the eight patterns lives in their *spatial correlation structure*, which is what the penalty functional measures. Non-data modules (finders, alignment, palettes, metadata) are excluded from the action but included in scoring — they are constants of the optimization that the variable part must harmonize with (mask.c:313, 347-351). <!-- anchor: mask.c:313, 347-351, 423-448 -->

### 6.3 The penalty functional: a designed objective

Table 23 fixes the three rules and their weights — "W1 = 100, W2 = 3 and W3 = 3":

1. **Rule 1, finder lookalikes, weight W1 = 100.** Each occurrence of a finder-pattern-like alternating five-module sequence in a row/column scores W1.
2. **Rule 2, blocks, weight W2 = 3.** A same-colour block of size m × n scores W2 × (m − 1) × (n − 1).
3. **Rule 3, runs, weight W3 = 3.** A same-colour run of length (5 + k), k > 0, in row or column scores W3 + k.

<!-- anchor: ISO 23634 Table 23; mask.c:22-24 -->

The 100-vs-3 ratio is the functional's design signature. A single finder lookalike outweighs 33 unit block-windows or run-events; since realistic symbols accumulate rule-2/3 scores in the tens, W1 makes the objective *lexicographic in practice*: first drive lookalike count toward zero, then let texture break ties. "In practice" is the honest qualifier — this is weighted-sum scalarization, not true lexicographic order, and a pattern with one lookalike beats a pattern with 34+ texture events; but within the family's typical score ranges the intended priority almost always binds. The reason for the priority is architectural: rules 2 and 3 degrade *sampling quality* (a graded, recoverable harm — the LDPC layer above absorbs classification noise), while rule 1 attacks *detection* (a binary, unrecoverable harm — a false finder can misregister the whole symbol before any ECC exists to help; [09-detection-robust-estimation.md](09-detection-robust-estimation.md) develops the detection pipeline's fragility, and [11-adversarial-channel.md](11-adversarial-channel.md) prices the same asymmetry from the attacker's side). Cheap harms get linear prices; catastrophic harms get a dominating price.

Rule 2's formula has a pleasant equivalent form used by the source: counting all-equal 2 × 2 windows. A solid m × n block contains exactly (m − 1)(n − 1) such windows, so totals agree on rectangles — an arithmetic identity — while the window form extends the definition canonically to irregular blobs (JC-T §7.4; mask.c:192-210). Rule 3 prices a run of 5 + k at W3 + k: constant entry fee, unit marginal cost per extra module — enough to prevent long runs from being *free*, without letting one long run dominate the texture budget the way it would under, say, quadratic pricing. <!-- anchor: mask.c:192-210, 219-267 -->

### 6.4 One decision variable per code: joint scoring across cascades

Clause 5.8 requires that "All the symbols in a JAB Code ... shall be evaluated together" and the reference is announced once, in master metadata Part II — a single 3-bit decision variable for a cascade of up to 61 symbols. `maskCode` realizes the joint objective literally: it renders every candidate onto one full-cascade canvas (uncovered cells −1, acting as run/block barriers) and scores the canvas whole, so rule-2 blocks and rule-3 runs *cross symbol boundaries* — adjacent symbols' edge modules interact in the objective, as they do optically on paper (mask.c:368-392; JC-T §7.6). <!-- anchor: ISO 23634 5.8; mask.c:368-392 -->

Optimization-theoretically this is a *shared-variable coupling*: per-symbol optima t₁*, ..., t_N* would generally differ, and the joint argmin trades them off inside one evaluation. The cost of the coupling is bounded and small — the joint optimum is at most the best single pattern's aggregate score, and since every symbol faces the same eight patterns, the loss versus per-symbol freedom is bounded by the spread of the family on each symbol — but the *benefit* is structural: one MSK field, no per-slave mask metadata, and demasking that needs no cross-symbol negotiation. (Slave metadata compactness is a running theme of the cascade design; [10-cascade-combinatorics.md](10-cascade-combinatorics.md).) Complexity: 8 renders and 8 scans of the canvas, O(8 · A) for cascade area A, with rule 1's constant factor dominating (up to 4 colour-pair tests × 10 module reads per interior cell; JC-T §7.11). Exhaustive search is optimal *by construction* here — the design space was sized to make it so, which is the next question.

### 6.5 Why eight patterns suffice — analytical rationale

The spec offers no argument for the family's size; what follows is this book's analysis, labeled as such.

The hard ceiling is the wire format: MSK is a 3-bit field, so 8 is not a tunable (`NUMBER_OF_MASK_PATTERNS`, jabcode.h:29; JC-T §7.10). The design question is whether 8 is *enough*, and the argument has two halves.

*Coverage of the threat classes.* The objective punishes three specific geometries — alternating 5-sequences, blocks, runs — and each generator family member destroys them along a different spatial direction/scale: gradients 001/010 break horizontal/vertical constancy respectively, 000 breaks both but preserves anti-diagonal structure, the div-forms attack at coarser block scales, and the two polynomials inject aperiodicity that no aligned data pattern tracks for long. For any *fixed* damaging structure in the data, most of the eight are incompatible with it; the argmin needs only one survivor.

*A diversity heuristic, honestly stated.* Model the eight scores as draws that are far from perfectly correlated (they respond to different spatial frequencies of the same data). The minimum of eight weakly-correlated scores concentrates well below their common mean; adding a ninth pattern buys a diminishing decrement in the expected minimum while costing metadata width and another full-canvas evaluation. Eight sits where the curve has flattened. This is a plausibility argument, not a theorem — the data distribution induced by LDPC-plus-interleaving output (chs. 3, 5 — effectively high-entropy input to this stage) is what makes "weakly correlated" reasonable, and it is also why pathological messages that defeat all eight patterns are rare rather than impossible. No claim of adversarial completeness is made here; a payload-crafting adversary is the subject of [11-adversarial-channel.md](11-adversarial-channel.md).

### 6.6 The two divergences as perturbations of the argmin

JC-T ch. 7 established two places where the live scoring deviates from a literal reading of Table 23; both are *objective perturbations*, and their consequence is best stated in optimization language.

1. **Rule 1 counts cross-only lookalikes.** The active code requires the alternating 5-sequence horizontally *and* vertically centred on the same module (a per-row/per-column variant exists but is commented out, mask.c:67-129); an isolated horizontal-only lookalike scores 0. This *weakens* the dominant term: P₁^impl ≤ P₁^spec pointwise, so patterns that a literal scorer would heavily penalize may survive to win. (JC-T §7.3.) <!-- anchor: mask.c:62-183 -->
2. **Rule 3 scores exactly-5 runs.** `score += W3 + (same_color_count - 5)` fires at `same_color_count >= 5`, i.e. k = 0 included, where Table 23's "(5 + k), k > 0" excludes it. This *strengthens* a texture term: P₃^impl ≥ P₃^spec pointwise. (JC-T §7.5.) <!-- anchor: mask.c:248-263; ISO 23634 Table 23 -->

Since the eight penalty values move, the argmin can move: an implementation scoring by the letter of Table 23 may select a different reference for the same symbol. The load-bearing fact is that this is **selection divergence, not wire divergence**. The chosen reference travels in metadata Part II and the demask is reference-driven; any decoder demasks whatever reference was announced, so encoder A's symbols decode on decoder B regardless of whose scoring produced the choice (JC-T §§7.3, 7.5). What differing scorers *cannot* do is reproduce each other's encoder output bit-for-bit — mask choice is part of encoder determinism, not of decode correctness. And in default mode the whole question evaporates: evaluation is skipped and reference 7 applied unconditionally, because a default-mode symbol carries no metadata in which a different choice could be announced (JC-T §7.6). The perturbation analysis therefore matters exactly where it should: for symbol *quality* (how well the chosen mask suppresses lookalikes and texture) rather than for interoperability. The residual quality question — does cross-only rule 1 ever let a genuinely detector-confusing pattern through? — is empirical, unresolved in this corpus, and connects to the detection-stage failure analysis of [09-detection-robust-estimation.md](09-detection-robust-estimation.md).

## Back to the code

The selection loop is compact enough to audit against §6.1-§6.4 in one read: canvas allocation and −1 fill (mask.c:369-375), the t = 0..7 loop rendering via `maskSymbols(enc, t, masked, cp)` and scoring via `evaluateMask` = rule 1 + rule 2 + rule 3 (mask.c:377-392, 277-280), strict `<` selection from an initial `min_penalty_score` of 10000 — so ties resolve to the *lowest* reference, a deterministic tie-break that keeps encoder output reproducible — and in-place application of the winner (mask.c:387-395). The caller then re-encodes Part II with the winning reference (encoder.c:2413-2430 via JC-T §7.6), closing the loop that makes the divergences of §6.6 wire-safe: the decision is *transported*, never re-derived. Coordinates are per-symbol local — each cascade symbol is masked as if (0, 0) were its own upper-left module — while scoring is global on the canvas; generation is local, evaluation is joint (mask.c:293-343; JC-T §7.2, §7.6). <!-- anchor: mask.c:277-280, 293-343, 363-399 -->

## Exercises

**1 (guided).** Take a toy 5 × 5 all-data canvas, 8 colours, every module colour 0. Compute rule-2 and rule-3 penalties (implementation scoring) for masks 000 and 001, and the resulting argmin. Then rescore rule 3 by the letter of Table 23 (k > 0 only) and check whether the argmin moves.

<details><summary>Answers</summary>

Mask 000: masked value (x+y) mod 8 — every row and column strictly increments, so no 2×2 window is uniform (rule 2 = 0) and no run reaches 5 (rule 3 = 0). Total 0.
Mask 001: masked value x mod 8 — rows increment (no horizontal runs), but each of the 5 columns is constant: five vertical runs of exactly 5. Implementation: 5 × (W3 + 0) = 15; rule 2 = 0 (horizontal neighbours always differ). Total 15.
Argmin: 000 (0 < 15). Spec-literal rule 3: exactly-5 runs score nothing, so mask 001 rescores to 0 — a tie at 0, which the strict-< / lowest-reference tie-break still resolves to 000. Here the divergence changes scores but not the choice; exercise 3 asks you to break that.
</details>

**2 (guided).** Prove the two group-theoretic claims of §6.2: (a) Φ_m is an involution on the data-module configuration space; (b) the per-cell action is free and transitive. State exactly where the power-of-two colour count is used.

<details><summary>Hint</summary>

(a) is v ⊕ m ⊕ m = v, valid because (Z₂)ⁿ has exponent 2. (b) given c, c′ the unique carrier is m = c ⊕ c′. The power of two enters only through closure: the generator value, reduced mod 2^(Nc+1), fits in Nc+1 bits, so XOR stays inside the index range — with a non-power-of-two palette size, mod-reduced values could XOR to indices ≥ the colour count and the action would leave the alphabet (mask.c invariant; JC-T §7.8).
</details>

**3 (open).** Construct a matrix (any size, 8 colours, all-data) on which the cross-only rule 1 and a literal per-row/per-column rule 1 select *different* masks — i.e. a configuration where some pattern's masked result contains horizontal-only finder lookalikes that the implementation does not price. Verify your construction by hand-scoring both variants for at least two mask references, and state which choice a strict-Table-23 encoder announces versus this one. (The commented-out per-row/column code at mask.c:67-129 is a usable specification of the literal variant.)

## Further reading

- ISO/IEC 23634:2022 clause 5.8, Tables 22-23.
- ISO/IEC 18004:2015 (QR Code), clause 7.8.3 — the monochrome ancestor of penalty-scored masking; instructive as comparison, not authority, for this symbology.
- Weighted-sum scalarization vs lexicographic ordering: R. E. Steuer, *Multiple Criteria Optimization: Theory, Computation, and Application*, Wiley, 1986, ch. 7 — the formal frame behind §6.3's "lexicographic in practice".
- Siblings: [09-detection-robust-estimation.md](09-detection-robust-estimation.md) (why finder lookalikes are the catastrophic class), [11-adversarial-channel.md](11-adversarial-channel.md) (W1 as denial of the cheapest detection-kill), [03-ldpc-coding-theory.md](03-ldpc-coding-theory.md) and [05-interleaving-and-determinism.md](05-interleaving-and-determinism.md) (why this stage's input is effectively high-entropy); implementation register: [../developers-manual/07-mask.md](../developers-manual/07-mask.md).
