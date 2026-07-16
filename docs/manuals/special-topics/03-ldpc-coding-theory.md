# 3. LDPC coding theory

<!-- objective: The reader can derive the Gallager LDPC construction as implemented — seeded-permutation matrix stacking, Gauss-Jordan systematization, G = (I|C) — analyze hard-decision bit-flipping vs soft-decision message passing, and evaluate the implementation's choice to hard-decode both metadata and message data against Annex B's assignment. -->

**Where it lives.** `createMatrixA` (ldpc.c:172), `GaussJordan` (ldpc.c:235), `createMetadataMatrixA` (ldpc.c:430), `createGeneratorMatrix` (ldpc.c:476), `encodeLDPC` (ldpc.c:645), `decodeMessage` (ldpc.c:770), `decodeLDPChd` (ldpc.c:906), the soft engines `decodeMessageILL` (ldpc.c:1066) and `decodeMessageBP` (ldpc.c:1209), and the caller-less soft entry `decodeLDPC` (ldpc.c:1376); the seeds `LPDC_METADATA_SEED 38545` and `LPDC_MESSAGE_SEED 785465` (ldpc.h:17-18 — the source spells the macros `LPDC_`, preserved verbatim); the level tables (encoder.h:226-241); ISO/IEC 23634:2022 5.4, Table 20, Table 29 (8.2.2), Annex B, and normative Annex C. Code-level reference: JC-T's [../developers-manual/06-ldpc.md](../developers-manual/06-ldpc.md), whose findings this chapter consumes rather than re-derives. <!-- anchor: ldpc.c:172, 235, 430, 476, 645, 770, 906, 1066, 1209, 1376 --> <!-- anchor: ldpc.h:17-18 --> <!-- anchor: encoder.h:226-241 --> <!-- anchor: ISO 23634 5.4 -->

## The problem

Chapter [2](02-information-density.md) priced redundancy without saying what it buys or how it is spent. This chapter opens the box. JAB Code protects both its message data and its metadata with low-density parity-check codes — Gallager's 1963 construction, essentially unmodified: a sparse random-looking parity matrix built from a seeded permutation generator, reduced to systematic form by elimination, encoded by one matrix multiply, and decoded iteratively.

Three tensions drive the chapter. First, *randomness versus reproducibility*: LDPC theory wants random-like matrices, but a wire format needs both endpoints to build the **same** matrix — the implementation resolves this with fixed seeds, making determinism itself an interoperability contract. Second, *two decoders*: the standard's Annex B assigns an iterative log-likelihood (soft) decoder to metadata and a bit-flipping (hard) decoder to message data, while the implementation hard-decodes both — its soft machinery is present but caller-less, a verified JC-T finding this chapter evaluates. Third, *two tables*: Table 20 (choose your redundancy) and Table 29 (grade what survived) look similar and answer entirely different questions.

## Theory

### Parity checks on a sparse bipartite graph

Fix the chapter's code parameters: codeword length `n` (the spec's `Pg`, the code's `capacity`), message length `k` (`Pn`), check count `m` (`K`), all per chapter [1](01-notation.md)'s table. A **(wc, wr)-regular LDPC code** is the kernel of an `m × n` matrix `H` with exactly `wc` ones per column and `wr` ones per row — equivalently, a bipartite Tanner graph (chapter 1) whose `n` variable nodes have degree `wc` and whose `m` check nodes have degree `wr`. *Low density* means `wc` and `wr` stay small constants while `n` grows: here `wc` runs 3–6 and `wr` 4–9 across the ten levels, against block lengths up to 2700 bits per sub-block. <!-- anchor: encoder.h:234 --> <!-- anchor: ldpc.c:666-673 -->

### The rate — full derivation

Count the ones in `H` two ways: by columns, `n·wc`; by rows, `m·wr`. Hence

$$
m \;=\; n\,\frac{w_c}{w_r}
$$

The code dimension is `k = n − rank(H)`, and since the rank is at most `m`:

$$
R \;=\; \frac{k}{n} \;\ge\; 1 - \frac{m}{n} \;=\; 1 - \frac{w_c}{w_r}
$$

with equality exactly when the checks are independent. Annex B states the design rate as R = 1 − wc/wr, and adds: "It is recommended to select wc ≥ 3 and wr ≥ wc+1" — every one of the ten level pairs satisfies both. Rank deficiency is not hypothetical: the implementation measures the true rank (`matrix_rank`) during elimination and sizes everything downstream by it, and exercise 2 constructs an `H` whose rows *always* carry a dependency. <!-- anchor: ISO 23634 Annex B --> <!-- anchor: ldpc.c:235 -->

The encoder's arithmetic follows the same identity from the other end: given `Pn` message bits, `encodeLDPC` computes the gross length as Pg = ceil(Pn·wr/(wr − wc)) rounded up to a multiple of wr (ldpc.c:654-655) — the inverse of R = 1 − wc/wr plus alignment, so that `m = Pg·wc/wr` is an integer. Chapter [2](02-information-density.md) met the same flooring from the capacity side. <!-- anchor: ldpc.c:648-658 -->

### The seeded stacking construction, as implemented

`createMatrixA` (ldpc.c:172) builds `H` in Gallager's original style — a band submatrix plus permuted copies:

1. **The base stack.** The first `n/wr` rows tile the columns with disjoint runs of `wr` consecutive ones: row `i` covers columns `i·wr` through `i·wr + wr − 1` (the index expression `i*(effwidth+wr)+j` walks exactly this diagonal band through the bit-packed words). Each column meets exactly one of these rows. <!-- anchor: ldpc.c:199-204 -->
2. **The permuted stacks.** After `setSeed(LPDC_MESSAGE_SEED)`, for each of the remaining `wc − 1` stacks the code draws a fresh column permutation by Fisher–Yates steps (`pos = pn_index(lcg64_temper(), capacity − j)`, then a swap) and writes column `permutation[pos]` of the base stack into column `j` of the new stack. Stack `i` is the base stack with columns permuted — so every stack contributes exactly one 1 per column, giving column weight `wc` and row weight `wr` overall:

$$
H \;=\;
\begin{pmatrix}
A_0 \\
\pi_1(A_0) \\
\vdots \\
\pi_{w_c-1}(A_0)
\end{pmatrix}
$$

<!-- anchor: ldpc.c:205-220 --> <!-- anchor: ldpc.h:18 -->

The permutations are "random" in the ensemble-argument sense and **fixed** in the engineering sense: the seed is a constant, the PRNG (`lcg64_temper`, chapter 5) is part of the wire contract, and the fork's memoization layer is correct precisely because the whole construction "produce\[s\] byte-identical matrices on every call" as a deterministic function of (wc, wr, capacity) — the source says so in as many words. Permutation machinery: chapter [1](01-notation.md); the uniformity proof for Fisher–Yates and the Annex F PRNG divergence question: chapter 5. <!-- anchor: ldpc.c:515-519 --> <!-- anchor: pseudo_random.c:10-30 -->

### Systematic form: Gauss–Jordan and the generator

Chapter [1](01-notation.md) derived, in full, how elimination over GF(2) brings `H` to the form (I | P) and why

$$
G \;=\; \begin{pmatrix} P \\ I_k \end{pmatrix},
\qquad H\,G \;=\; 0
$$

Here only the sketch, tied to source: `GaussJordan` (ldpc.c:235) scans each row for its first pivot, XORs the pivot row out of every other row carrying that column ("subtract pivot row GF(2)"), records out-of-place pivots for column swapping and counts zero rows — the rank comes out as a by-product. `createGeneratorMatrix` (ldpc.c:476) then assembles the generator from the eliminated matrix; its comment is the derivation in one breath: "remember matrixA is now A = \[I CT\], now use it and create G=\[CT / I\]". Encoding is the single multiply `c = G·x` (the XOR-and-mask loop of ldpc.c:709-723), systematic: the `k` message bits ride in the last codeword positions, which is why the decoder extracts `data[i + matrix_rank]` after correction. Annex C states the multiplicative form: "c = m ⊗ G over GF(2)". <!-- anchor: ldpc.c:235-328, 476-513, 709-723, 1043-1046 --> <!-- anchor: ISO 23634 Annex C -->

### Minimum distance and the correction radius

The **minimum distance** `dmin` of a linear code is the smallest Hamming weight of a nonzero codeword. Two facts, with the standard proof compressed to its core since this is its only load-bearing appearance:

- Any error pattern of weight up to dmin − 1 is **detectable**: it cannot carry one codeword onto another.
- A nearest-codeword decoder **corrects** any pattern of weight `t` with 2t < dmin: if two codewords both lay within `t` of the received word, the triangle inequality would put them within 2t < dmin of each other — contradiction. Hence the correction radius is the floor of (dmin − 1)/2.

Annex B states exactly this pair — dmin − 1 detectable, (dmin − 1)/2 correctable. For LDPC codes dmin is not designed in explicitly; it emerges from the ensemble, and the iterative decoders below do not guarantee nearest-codeword decoding anyway. That is why the standard's operational promises (Table 20) are probabilistic rather than radius-shaped. <!-- anchor: ISO 23634 Annex B -->

### Decoding I: bit flipping — full derivation

This is the book's first iterative decoder, so we derive it fully; the soft decoder afterward gets the faded sketch.

**Setup.** The received hard-decision word is y = c ⊕ e for an unknown error pattern `e`. The decoder can see only the **syndrome** s = Hy = He: check `j` is *unsatisfied* when an odd number of erroneous bits participate in it. Define, for each bit `v`, the failure count

$$
f(v) \;=\; \big|\{\, j : H_{jv} = 1 \text{ and check } j \text{ unsatisfied} \,\}\big|
$$

**Why the max-f bit is the culprit — the separation lemma.** Model the channel as independent bit errors with probability `p`. First a classical identity: the probability that an odd number among `ℓ` independent error events occur satisfies the recursion P(ℓ+1) = p + (1 − 2p)·P(ℓ) with P(0) = 0, whose closed form (immediate by induction) is

$$
P_{\text{odd}}(\ell) \;=\; \frac{1 - (1-2p)^{\ell}}{2}
$$

Now condition on bit `v`. Each of its `wc` checks contains `wr − 1` *other* bits. If `v` is in error, that check is unsatisfied when the others hold an **even** number of errors; if `v` is correct, when they hold an **odd** number. Writing δ = (1 − 2p) to the power (wr − 1):

$$
\mathbb{E}\,[f(v) \mid v \text{ in error}] \;=\; w_c\,\frac{1+\delta}{2},
\qquad
\mathbb{E}\,[f(v) \mid v \text{ correct}] \;=\; w_c\,\frac{1-\delta}{2}
$$

The erroneous bits sit `wc·δ` above the correct ones in expectation. This gap is the entire engine of the algorithm — and it is why *low density* is not an implementation nicety but the design principle: δ decays geometrically in `wr`, so dense rows (large `wr`) wash the evidence out, while sparse rows keep individual checks informative.

**Why flipping the max helps — the descent identity.** Let Φ(y) be the number of unsatisfied checks. Flipping bit `v` toggles the parity of precisely its `wc` checks, so

$$
\Delta\Phi \;=\; w_c - 2 f(v)
$$

Flipping any bit with f(v) > wc/2 strictly decreases Φ; Φ is a nonnegative integer; so greedy flipping of maximal-f bits descends until either Φ = 0 (success: the syndrome test passes) or no bit clears the threshold — a local minimum, where iteration caps take over.

**The algorithm as shipped.** `decodeMessage` (ldpc.c:770) runs up to `max_iter = 25` rounds: accumulate `max_val[k]` — precisely f(k) — over all unsatisfied checks; find the maximal value; flip **every** bit attaining it (for blocks shorter than 36 bits, flip one uniformly chosen tie instead — the draw comes from the project's deterministic LCG, a fork fix replacing stdlib `rand()` so the tie-break reproduces across processes); exclude this round's flipped bits from the next round's candidates (`prev_index`); stop on a clean syndrome or at 25 iterations, with a final syndrome verdict deciding success. Two engineering deviations from the pure greedy derivation deserve note: simultaneous flipping of a whole tie set can overshoot (two flipped bits sharing a check cancel each other's toggle there), and the one-round exclusion list is the damping that keeps such moves from oscillating. Annex B describes this decoder for message data — it "flips those bits with the maximum λ\[l\]v > 0 in each iteration step" (its λ is our f) — terminates on Hĉ = 0 or l = L, and advises "It is recommended to use L = 25"; the source hard-codes `max_iter=25` in both decode entry points. <!-- anchor: ldpc.c:770-896, 814-886, 855-867, 909, 1379 --> <!-- anchor: ISO 23634 Annex B -->

### Decoding II: log-likelihood message passing — sketch

Second appearance of iterative decoding, so per the book's fade schedule: structure and quotes, no re-derivation (full treatments: MacKay ch. 47, Moon ch. 15).

Soft decoding replaces each received bit with a **log-likelihood ratio** λ — the signed confidence that the bit is 0 — and passes messages on the Tanner graph. The variable-node update sums the channel LLR with incoming check messages; the check-node update is the **tanh rule**: the reliability a check `j` forwards to bit `i` is

$$
\nu_{j\to i} \;=\; -2\,\operatorname{atanh}\!\Big(\prod_{i' \in N(j)\setminus\{i\}} \tanh\big(-\tfrac{\lambda_{i'} - \nu_{j\to i'}}{2}\big)\Big)
$$

which is (up to the sign convention) the parity-of-independent-bits identity from the bit-flipping derivation, promoted from probabilities to LLRs. Annex B assigns exactly this machinery to **metadata**: an "iterative Log Likelihood decoding algorithm" with the tanh-product check-node update and a tentative decision at λ > 0, terminating on Hĉ = 0 or l = L with L = 25 recommended.

The implementation carries two faithful engines — `decodeMessageILL` (ldpc.c:1066) and a belief-propagation variant `decodeMessageBP` (ldpc.c:1209) — with the tanh product and atanh update recognizable line for line (ldpc.c:1139-1151), the channel LLR initialized as 2·y/σ² with σ² **estimated empirically** from the received reliabilities (ldpc.c:1108-1125), and the same 25-iteration cap. One open question rides along, inherited from JC-T rather than resolved here: the source takes its tentative decision at λ < 0 (ldpc.c:1162-1165) where the Annex B extract says λ > 0 — a sign-convention discrepancy that is moot while the code is caller-less, and a mandatory pre-flight check for anyone reviving it (see the evaluation below). <!-- anchor: ldpc.c:1066-1193, 1139-1151, 1162-1165 --> <!-- anchor: ISO 23634 Annex B -->

### Two tables, two questions

**Table 20 (Clause 5.4.1) answers the encoder's question:** *how much redundancy should I buy?* Ten levels, each a (wc, wr) pair with its design rate and a claimed recovery percentage, qualified as "recovery capability of the bit errors in more than 95 % of cases" — a statistical statement about random damage, not a guaranteed radius (the dmin discussion above is why no radius is on offer). Values as printed (decimal commas reproduced as printed):

| Level | Recovery (%) | wc | wr | R (as printed) | 1 − wc/wr |
|---|---|---|---|---|---|
| 1 | 4 | 3 | 8 | 0,63 | 0.625 |
| 2 | 5 | 3 | 7 | 0,57 | 0.5714… |
| 3 (default) | 6 | 4 | 9 | 0,55 | 0.5555… |
| 4 | 7 | 3 | 6 | 0,50 | 0.5 |
| 5 | 8 | 4 | 7 | 0,43 | 0.4285… |
| 6 | 9 | 4 | 6 | 0,34 | 0.3333… |
| 7 | 10 | 3 | 4 | 0,25 | 0.25 |
| 8 | 11 | 4 | 5 | 0,20 | 0.2 |
| 9 | 12 | 5 | 6 | 0,17 | 0.1666… |
| 10 | 14 | 6 | 7 | 0,14 | 0.1428… |

The last column is ours; note the printed R is **not** a uniform rounding of it (0,63 rounds 0.625 up; 0,55 truncates 0.556 down; 0,34 rounds 0.333 up) — exercise 1 chases this. The source mirrors the pairs verbatim in `ecclevel2wcwr` and the printed rates in `ecclevel2coderate`; the writer's usage text "default:3(6%)" is this table's level-3 row surfacing in the CLI. <!-- anchor: ISO 23634 Table 20 --> <!-- anchor: encoder.h:226, 234 -->

**Table 29 (Clause 8.2.2) answers the verifier's question:** *of the correction capacity this symbol shipped with, how much did the physical channel already spend?* It assigns each level an error-correction capacity fraction Ecap — 8, 9, 10, 11, 14, 17, 22, 24, 26, 29 percent for levels 1–10 — and grades the **unused error correction** as

$$
UEC \;=\; 1 - \frac{e + 2t}{P_g \times E_{cap}}
$$

with `e` and `t` the erasure and error counts the reference decode observed (errors cost double — the correction-radius factor of two from the dmin section, now wearing a grading uniform). Table 20 is consulted once, at design time, against a *predicted* channel; Table 29 is consulted per specimen, at verification time, against the *actual* one; conflating them — e.g. reading level 3's "6" and "10" as competing estimates of the same quantity — is a category error. Note honestly: no Clause 8 verifier exists in this codebase (corpus §4, quality grading: NOT FOUND); Table 29 governs conformance tooling, not this decoder. <!-- anchor: ISO 23634 Table 29 --> <!-- anchor: ISO 23634 8.2.2 -->

## Back to the code

### Call graph and seeds

Everything LDPC funnels through two entries. Encode: `encodeLDPC(data, wcwr)` — message path when wr > 0, metadata path (`createMetadataMatrixA`, seed 38545) when the caller passes wcwr = \{2, −1\}. Decode: `decodeLDPChd(data, length, wc, wr)` in place — metadata Part II arrives as `decodeLDPChd(part2, 38, 2, 0)`, message data as `decodeLDPChd(raw, Pg, ecl.x, ecl.y)` (JC-T §6.2, decoder.c:1549 and 1979). The wr ≤ 3 branch of `decodeLDPChd` *overrides* the caller's wc — 2, or 3 when the net exceeds 36 bits — mirroring Annex C's rule from the decode side. Both seeds, the PRNG, and `pn_index` jointly determine every matrix; that quadruple is the interoperability surface (exercise 3). <!-- anchor: ldpc.c:645, 906-923 --> <!-- anchor: ldpc.h:17-18 -->

### The Annex C metadata matrix: a constrained special case

Normative Annex C prescribes the metadata code separately, and `createMetadataMatrixA` (ldpc.c:430) is its realization — the general construction with three constraints tightened:

- **Column weight:** "wc = 2 if metadata < 36 bits else 3" (Annex C) — deliberately *below* Annex B's wc ≥ 3 recommendation, a concession to blocks as short as 6 and 38 encoded bits, where a weight-3 column would implicate most of the code in every error. The source's boundary sits at strictly-greater-than 36 (`if(Pn>36) wc=3`, ldpc.c:921), so a hypothetical exactly-36-bit metadata block would get wc = 2 from the code and 3 from the extracted rule — a divergence JC-T logs as unreachable today and a trap for future metadata layouts (exercise 4).
- **No row-weight structure:** rather than the band-plus-stacks shape, each of the m = n/2 rows receives a fixed count of ones — Annex C says the count is "C × K / wc + 3 / K" with the extraction's floor/ceiling brackets lost; the source computes `nb_once = (capacity·nb_pcb/wc + 3)/nb_pcb` in integer arithmetic, a bracketing consistent with the flattened text — placed at positions drawn by the same Fisher–Yates process, per Annex C "ones equally distributed, positions via Annex F permutation", under `LPDC_METADATA_SEED`. <!-- anchor: ldpc.c:450-463 --> <!-- anchor: ISO 23634 Annex C -->
- **Rate one half:** m = n/2 hard-codes R = 1/2 — redundancy equal to Table 20's level 4 and heavier than the default message level. The highest-stakes bits in the symbol carry the near-heaviest protection.

### Evaluation: the caller-less soft path

The finding (JC-T ch. 6, cited as verified input): `decodeLDPC` (ldpc.c:1376), `decodeMessageILL`, and `decodeMessageBP` have **no in-tree callers**; both metadata and message data hard-decode through `decodeLDPChd`. Annex B, by contrast, assigns the soft decoder to metadata. Three questions decide whether that gap matters.

**What would soft information buy?** The decoder's raw material is richer than bits: every module classification is a nearest-palette-colour decision with a margin (chapter [8](08-colour-space-geometry.md)'s geometry), which is exactly the per-bit reliability a `jab_float` array wants — and `decodeLDPC`'s signature already asks for it. The classical literature (MacKay; Moon) puts soft-decision gains over hard decision on Gaussian-like channels at a substantial fraction of the available coding gain — the kind of margin that matters most for **metadata**: the shortest blocks (6 and 38 bits, where every bit of reliability counts), decoded first, with a single failure killing the whole symbol before message decode begins (`DECODE_METADATA_FAILED`). Annex B's assignment of the expensive decoder to the small, critical block and the cheap one to the bulk data is a sensible cost allocation, not an oversight.

**Why does hard-decoding metadata still work?** Defense in depth upstream and down: rate-1/2 protection on tiny blocks; Part I's bootstrap alphabet using widely separated colours (chapter [4](04-metadata-bootstrap.md)); 25 flipping iterations being generous for a 38-bit block; and the fork's classification aids (calibration, permissive substitution) reducing the error rate before the decoder ever sees a bit. The reference ecosystem has evidently found this stack sufficient — the soft path's dormancy is inherited from upstream, not a fork regression.

**What would revival cost?** (i) *Plumbing*: threading per-module reliabilities from the colour classifier through `decodeSymbol` into float arrays — a real but bounded refactor, since the entry point exists. (ii) *Correctness debt*: the λ sign-convention question must be settled against an authoritative Annex B reading first, and dead-until-now code needs tests and vectors it has never had. (iii) *Runtime*: double-precision tanh/atanh per edge per iteration, against `decodeLDPChd`'s word-packed XOR-and-popcount loops that the fork just finished optimizing. (iv) *Interop*: none — decoding strategy is receiver-local, which is both why revival is safe to attempt and why nothing has forced it. The honest cost-benefit: revival is attractive exactly in proportion to how often symbols currently die at `DECODE_METADATA_FAILED` in marginal captures — a question for the profiling harness (JC-T ch. 12), not for theory. <!-- anchor: ldpc.c:1376 --> <!-- anchor: decoder.h:17 --> <!-- anchor: ISO 23634 Annex B -->

## Exercises

**1 (guided).** Verify the level-3 rate three ways: from Table 20's (wc, wr); from `ecclevel2wcwr[2]`; and as chapter [2](02-information-density.md)'s SV1 quotient Pn/Pg = 580/1044. Compare all three with the printed 0,55 and with `ecclevel2coderate[3]`. Then classify each printed R in Table 20 as a round-up, round-down, or exact rendering of 1 − wc/wr. What does the inconsistency tell you about how the printed column was produced?

<details><summary>Solution</summary>

All three computations give 5/9 = 0.5556; the printed value and the code's float are 0.55. Classification: levels 4, 7, 8 exact; 2 and 10 consistent with either truncation or round-to-nearest; 3 truncated down (nearest would print 0,56); 1, 5 and 9 rounded up to nearest (0.625 → 0,63; 0.4286 → 0,43; 0.1667 → 0,17); 6 rounded up past even the nearest value (0.333 → 0,34). No single rounding rule reproduces the column, so it was plausibly computed from something other than 1 − wc/wr alone — e.g. averaged realized Pn/Pg over symbol sizes, where flooring and rank effects perturb the quotient — but the extract cannot confirm this; treat the mechanism as NOT FOUND and the (wc, wr) pairs as the normative content.

</details>

**2.** Build the parity-check matrix of the complete graph on five vertices: n = 10 columns indexed by the pairs \{i, j\} of five checks r1…r5, with column \{i, j\} carrying ones in rows i and j. (a) Verify wc = 2, wr = 4. (b) Transmit the all-zero codeword and flip the bit \{1, 2\}; run one bit-flipping iteration by hand and show the decoder corrects it. (c) Show the five rows always XOR to zero, conclude the rank is at most 4, prove it is exactly 4, and compute the true rate. Which variable in `GaussJordan` absorbs this?

<details><summary>Solution</summary>

(a) Each column names two rows; each row contains the 4 pairs naming it. (b) Failed checks: r1, r2. Failure counts: f(\{1,2\}) = 2; every other bit shares at most one failed check, so f ≤ 1. Unique maximum → flip \{1,2\} → syndrome clean in one iteration. (Being a 10-bit block, the code would take the under-36 tie-break branch, but there is no tie.) (c) Every column has exactly two ones, so the full row-sum is zero: rank ≤ 4. Any proper nonempty dependent row set S would need every edge of K5 to touch S in an even count, i.e. no edge crossing the cut — impossible in a connected graph — so rank = 4, k = 10 − 4 = 6, R = 0.6 > 1 − wc/wr = 0.5. In source, the dependency surfaces as a zero line during elimination (`zero_lines_nb`) and the reduced check count comes back as `matrix_rank`.

</details>

**3 (guided).** Explain why determinism of `H` is an interoperability requirement, not an implementation convenience. Structure the argument as: (i) what the decoder must possess to decode at all; (ii) what is actually transmitted in the symbol; (iii) therefore what must be reconstructible from constants alone; (iv) the blast radius of changing `LPDC_MESSAGE_SEED`, `pn_index`, or one PRNG constant. Connect to the memoization comment at ldpc.c:515-519 and to chapter 5's Annex F divergence question.

<details><summary>Hints</summary>

(i) The decoder needs `H` itself — the syndrome, the flipping counts, everything is defined by it. (ii) The symbol carries only (wc, wr) via metadata E, never a matrix. (iii) So `H` must be a pure function of (wc, wr, length) and shared constants: seeds, PRNG, and the draw-to-range reduction — exactly the tuple the memoization comment certifies as deterministic. (iv) Any change yields a decoder-valid but *different* code: every existing symbol becomes undecodable noise to the new build and vice versa, with no error message better than "Too many errors in message." Chapter 5 sharpens this into the open question of what "the same PRNG" must mean across independent implementations of Annex F.

</details>

**4 (open).** The 36-bit boundary: the extracted Annex C rule assigns wc = 3 at exactly 36 net metadata bits; `decodeLDPChd` assigns wc = 2 there (`Pn > 36` test, ldpc.c:921), and the encoder's metadata path makes the matching choice. Today no metadata structure hits 36 exactly. Suppose a future revision adds one. Trace precisely what happens end-to-end between a literal-Annex-C implementation and this one, identify the first observable failure, and propose the minimal change (to code, or to a hypothetical errata) that closes the trap — arguing for your choice of which side should move.

<details><summary>Discussion</summary>

No checked solution. Strong answers will notice the failure is silent and total: both sides build internally consistent but different matrices (different wc changes `nb_pcb`, hence the whole elimination), the syndrome check fails generically, and the symptom is an ordinary metadata decode failure with nothing pointing at the boundary. On which side should move: the code's `>` matches the *reference ecosystem's* deployed behavior, and chapter 2's audit already established that deployed arithmetic, not printed text, is the operative interchange contract — but a defensible answer may weigh normative Annex C's letter more heavily. The grading rubric is the quality of the argument, not the verdict.

</details>

## Further reading

- R. G. Gallager, *Low-Density Parity-Check Codes*, MIT Press, 1963 — the construction of this chapter, including the stacked-permutation ensemble and the bit-flipping decoder; listed in the ISO/IEC 23634 bibliography.
- T. K. Moon, *Error Correction Coding: Mathematical Methods and Algorithms*, Wiley, 2005 — also in the ISO bibliography; systematic encoding, hard- and soft-decision LDPC decoding with worked algorithms.
- D. J. C. MacKay, *Information Theory, Inference, and Learning Algorithms*, Cambridge University Press, 2003 — the modern standard treatment of message-passing decoding and LDPC performance.
- C. E. Shannon, *A Mathematical Theory of Communication*, 1948 — the theorem that makes the whole enterprise rational.
