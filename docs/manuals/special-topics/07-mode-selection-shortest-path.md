# 7. Mode selection as shortest path

<!-- objective: A mathematically mature reader can formulate encoding-mode selection as a shortest-path problem on a 14-state trellis, derive the dynamic-programming solution with its optimality argument, and analyze the implementation's data-analysis pass against it, divergences included. -->

**Where it lives.** The analysis pass is `analyzeInputData` (`src/jabcode/encoder.c:288-584`); serialization is `encodeData` (encoder.c:723). The cost and token tables are `jab_enconing_table` (`encoder.h:129-181`), `latch_shift_to[14][14]` (encoder.h:186-200), `character_size[7] = {5,5,4,4,5,6,8}` (encoder.h:207) and `mode_switch[7][16]` (encoder.h:213-220); the sentinel is `ENC_MAX 1000000` (`include/jabcode.h:28`). Spec ground: ISO/IEC 23634:2022 clause 5.3, Annex E (Tables E.1-E.3) and Annex D's worked token stream. The maintainer-level account is [../developers-manual/04-encoder.md](../developers-manual/04-encoder.md) §4.4 (JC-T ch. 4), consistent with and extended by this chapter. <!-- anchor: encoder.c:288, 723; encoder.h:129-220; jabcode.h:28; ISO 23634 5.3, Annexes D, E -->

## The problem

JAB Code's message layer has seven encoding modes — the source's own ordering comment: "1.upper, 2.lower, 3.numeric, 4.punct, 5.mixed, 6.alphanumeric, 7.byte" (encoder.h:202-203) — with per-character costs from Table E.1 (bits per character: U 5, L 5, N 4, P 4, M 5, A 6, B 8; identical to `character_size`). Switching modes costs bits too, in two flavours: a **latch** moves the operative mode permanently; a **shift** buys a one-character excursion after which control returns automatically to the pre-shift mode. A message like "JAB Code 2016!" mixes upper case, lower case, digits and punctuation; every segmentation into modes is legal, and their bit costs differ substantially. Choosing the segmentation is not cosmetic — it feeds directly into the capacity ledger of [02-information-density.md](02-information-density.md): wasted switch bits are modules.

Annex E states the intended shape of the solution in one sentence: the algorithm "processes the input data characters, one after another, and outputs a trellis graph. After the last character is processed, the shortest sequence is known." Mode selection is a shortest-path problem, and this chapter derives the dynamic program that solves it — the book's first DP, so it gets the full treatment. <!-- anchor: ISO 23634 Annex E -->

## Theory

### 7.1 Why shift states double the space: restoring the Markov property

Try to build states from the 7 modes alone. The cost to continue from character i depends on the operative mode — but if character i was written under a *shift*, the operative mode afterwards is not the mode that encoded it; it is the mode you shifted *from*. A 7-state formulation would need to remember extra history, breaking the memorylessness that dynamic programming requires.

The repair is standard: enlarge the state. Take

$$
S \;=\; \{U, L, N, P, M, A, B\} \times \{\text{latch}, \text{shift}\},
\qquad |S| = 14,
$$

where the latch copy of mode m means "m is the operative mode" and the shift copy means "this character rides a one-off excursion into m; control returns". On S, the cost of the next step depends only on the current state — the Markov property holds, and Annex E's tables are indexed accordingly: Table E.3 gives latch costs between the 7 modes, Table E.2 gives shift costs, and the free return from an excursion appears in E.2 as the zero-cost row entries out of P, M and B (e.g. row P: 0, 0, 0, 106, 106, 0, 106 — returning to U, L, N or A costs nothing). Three modes are shift-favoured by design: P and M are *only* reachable by shift (their E.3 rows are all 106), and B carries a count header so byte excursions pay their token once per run.

### 7.2 The trellis

Lay the state space out against the input: node (i, s) means "the first i characters are encoded, ending in state s". Arcs go from layer i to layer i+1 only:

$$
(i, s) \longrightarrow (i+1, t)
\quad\text{with cost}\quad
w(s,t) \;+\; c_t(x_{i+1}),
$$

where w(s, t) is the switch cost (0 on the diagonal, Table E.3 for latch targets, Table E.2 for shift targets, 106 for impossible transitions) and c_t(x) is the Table E.1 cost of character x in the mode underlying t — infinite if x is not encodable in that mode; byte encodes everything, so column B is always finite. The result is a **layered directed acyclic graph**: (input length) + 1 layers of 14 nodes, arcs only between consecutive layers. A mode-selection decision for the whole message is exactly a source-to-final-layer path, and its bit length is the path cost. Shortest path on this graph is the optimization problem.

### 7.3 The Bellman recursion — full derivation

Define the value function

$$
V_{i}(t) \;=\; \min\{\, \text{cost of encoding } x_1 \ldots x_i \text{ ending in state } t \,\},
$$

with the convention min ∅ = ∞. Initialization pins the start: the initial mode is Uppercase, so V₀(U-latch) = 0 and V₀(s) = ∞ otherwise. (Annex E's printed Step 1 initializes "CharSize0[0 106 106 106 106 106 106 0 106 106 106 106 106 106]" — zeros at both the latch-U and shift-U positions; since the transition rows out of shift-U and latch-U are identical and shift states are transient, the two initializations yield the same values everywhere. §Back-to-the-code returns to this.) <!-- anchor: ISO 23634 Annex E; encoder.c:336-342 -->

**Recursion.**

$$
V_{i+1}(t) \;=\; c_t(x_{i+1}) \;+\; \min_{s \in S}\;\bigl[\, V_i(s) + w(s,t) \,\bigr].
$$

**Claim (correctness).** V as computed equals the true minimum path cost to every node.

**Proof.** By induction on the layer. The base layer is exact by definition. Assume V_i(s) is the true minimum for all s. Any path P reaching (i+1, t) decomposes uniquely as a path P′ to some (i, s) followed by the single arc (i, s) → (i+1, t) — uniquely, because the graph is layered: arcs advance the layer by exactly one, so P visits layer i exactly once. Hence

$$
\mathrm{cost}(P) \;=\; \mathrm{cost}(P') + w(s,t) + c_t(x_{i+1})
\;\ge\; V_i(s) + w(s,t) + c_t(x_{i+1})
\;\ge\; V_{i+1}(t),
$$

so V_{i+1}(t) lower-bounds every path; and it is attained, because a minimizing s in the recursion together with an optimal path to (i, s) (induction hypothesis) assembles a real path of exactly that cost. ∎

The middle inequality *is* the principle of optimality: a prefix of an optimal path must itself be optimal to its endpoint, else splicing a better prefix improves the whole — the exchange being legal precisely because cost is additive over arcs and arcs depend only on the endpoint states, i.e. the Markov property that §7.1 bought by doubling the space. Note what the layered-DAG structure contributes: no cycles, so no negative-cycle pathologies and no need for Dijkstra-style ordering — one left-to-right sweep suffices, and the total work is

$$
O\!\left(n \cdot |S|^2\right) \;=\; O(196\,n)
$$

min-plus updates for n input characters. Recording the argmin s for each (i+1, t) — the backpointer — turns the value table into the optimal *sequence* by backtracking from the cheapest final state. This is the first and cleanest instance of a pattern the book will reuse in sketched form (Viterbi-style decoding is the same recursion over a different trellis).

### 7.4 106 as +infinity: how big must a sentinel be?

Annex E prices impossible transitions at 106 rather than a true infinity, which is sound only if no optimal path could ever rationally pay 106 for one transition. The bound is comfortable: from *any* state, the next character can always be handled by falling back through legal arcs — at worst latch to U (≤ 8 from any latchable mode per Table E.3) and shift from U into byte (11, count header included), i.e. a universal continuation costing at most about 15 switch bits plus the 8-bit byte character, ≈ 23 bits per character. A path taking a 106-arc is therefore beaten by at least 80 bits by its own byte-fallback variant at that step, so arcs priced 106 are never selected: 106 behaves exactly as +∞ for this graph. (Bound argument is this book's; the spec states no rationale for the value.) The implementation, unburdened by table typography, uses `ENC_MAX = 1000000` — and initializes its bookkeeping arrays to `ENC_MAX/2` (encoder.c:309-332) so that *sums* of two sentinels stay far from `INT32_MAX`: a sentinel must be large enough to dominate and small enough that arithmetic on it cannot overflow into legitimacy. <!-- anchor: jabcode.h:28; encoder.c:309-332; ISO 23634 Tables E.2-E.3 -->

### 7.5 Worked example: the Annex D prefix "JAB Co"

Annex D encodes "JAB Code 2016!" as: Uppercase (J, A, B, SP, C = 10, 1, 2, 0, 3) → L/L (28, "11100") → Lowercase (o, d, e = 15, 4, 5) → N/L (29, "11101") → Numeric → P/S (13, "1101") → Punctuation (! = 0, "0000"), for 78 bits total. Running the recursion on the six-character prefix "JAB Co" is enough to watch the first latch decision fall out. Costs from Tables E.1-E.3; states shown are the latch states that ever become finite (P, M are unreachable for these characters; the byte column stays dominated — e.g. a byte shift at 'J' would cost 0 + 11 + 8 = 19 against U's 5). <!-- anchor: ISO 23634 Annexes D, E -->

| after | U | L | N | A | reasoning at this layer |
|---|---|---|---|---|---|
| — | 0 | ∞ | ∞ | ∞ | start in U |
| J | **5** | ∞ | ∞ | 11 | U: 0+0+5; A: 0+5+6 ('J' is not in L or N) |
| A | **10** | ∞ | ∞ | 16 | A: min(5+5 via U-latch, 11+0 via stay) + 6 = 16 |
| B | **15** | ∞ | ∞ | 21 | same shape |
| SP | **20** | 25 | 24 | 26 | space is in U, L, N, A: L = 15+5+5, N = 15+5+4 |
| C | **25** | ∞ | ∞ | 31 | 'C' kills L and N again; U: min(20+0, 25+7, 24+4, 26+8)+5 = 25 |
| o | ∞ | **35** | ∞ | 36 | 'o' is not in U; L = 25 + w(U→L)=5 + 5; A = 25+5+6 |

Minimum at the final layer: V₆(L) = 35. Backtracking: L at 'o' points to U at 'C', and U points to U all the way to the start. The optimal prefix encoding is therefore five uppercase characters (25 bits), a U→L latch (5 bits, token 28 = "11100"), then 'o' (5 bits) — exactly Annex D's opening, 35 bits. Note the near-miss at layer 'SP': N briefly costs 24 < L's 25, and a greedy per-character chooser might latch to numeric there; the DP keeps both alive and lets 'C' (unencodable in N) retire the numeric branch a layer later — the whole argument for global optimization in one row of the table.

Extending by hand through "de 2016!" reproduces the rest (the ledger: 25 + 5 + 3×5 + 5 + 5×4 + 4 + 4 = 78 bits); we cite Annex D for those layers rather than printing eight more rows. One discrepancy discovered while cross-checking tokens: the extract of Annex D's numeric segment reads "SP,2,0,1,6 = 0,3,1,2,5", but the source's encoding table gives '6' the numeric value 7 (`jab_enconing_table[54][2]` = 7, consistent with '0' → 1 ... '9' → 10; encoder.h:139-141), which would make the list 0, 3, 1, 2, 7. The bit-length arithmetic is unaffected (4 bits either way); whether the printed 5 is a spec typo or an extraction slip is **NOT FOUND** — flagged for the next ISO pull. All other Annex D tokens verify against the source tables (JC-T §4.4.3). <!-- anchor: encoder.h:139-141; ISO 23634 Annex D -->

## Back to the code

**It is the trellis.** `analyzeInputData` implements §7.3's recursion with Annex E's own four-variable vocabulary in recognizable form: `curr_seq_len` is CurrSeqLen (indexed `(i+1)*14 + j`), `prev_mode` is PrevMode (the backpointers), `switch_mode`/`temp_switch_mode` carry Annex E's SwitchMode bookkeeping across layers, and the per-layer assignment of `character_size[j]` from `jab_enconing_table[tmp][j]` plays CharSizeC (encoder.c:296-372). The per-layer min-update is the double loop over j and k at encoder.c:374-392; byte's universal encodability is hard-coded (`curr_seq_len[...+6] = ... = character_size[6]`, encoder.c:372); the final argmin over states and the backtrack through `prev_mode` are encoder.c:456-576. Shift states are handled transiently, as §7.1 predicts they can be: computed each layer, folded back into their return mode by walking the backpointer chain (the `j > 6` block, encoder.c:394-432), then reset to the sentinel — except byte-shift, which persists so that a run of shifted bytes pays one token and one count field (the `k == 13 && prev == j` clause, encoder.c:381-388, and see below). Initialization zeroes only latch-U (encoder.c:336-342) where the Annex's printed Step 1 zeroes latch-U *and* shift-U; the two are value-equivalent because rows 0 and 7 of the transition table are identical and shift states are cleared per layer — an implementation freedom, not a divergence. This confirms and sharpens JC-T §4.4.2's one-line characterization ("a shortest-path DP over the 14 mode states per character"). <!-- anchor: encoder.c:288-584, 336-342, 372, 374-432, 456-576 -->

**But the cost table is not Tables E.2/E.3.** Cell-by-cell comparison of `latch_shift_to` (whose rows 7-13 duplicate rows 0-6 — transitions out of a shift state cost the same as out of its base mode) against the extracted Annex E tables shows agreement everywhere except six cells. The column mapping is verified by the three Annex-D-exercised cells (U→L latch 5, L→N latch 5, N→P shift 4), which all match. The exceptions: <!-- anchor: encoder.h:186-200; ISO 23634 Tables E.2-E.3 -->

| transition | Annex E (extract) | `latch_shift_to` | token in `mode_switch` |
|---|---|---|---|
| L→N shift | 7 (E.2) | `ENC_MAX` | 127 (7 bits) — present but unreachable: a dead token |
| N→A latch | 106 (E.3) | 9 | 478 (9 bits) |
| N→B shift | 106 (E.2) | 10 | 60 (6 bits) + 4-bit count |
| A→L latch | 106 (E.3) | 13 | 8188 (13 bits) |
| A→N latch | 106 (E.3) | 13 | 8189 (13 bits) |
| B→B shift | 106 (E.2) | 0 | — (byte-run continuation, priced by the count machinery) |

<!-- anchor: encoder.h:186-220; ISO 23634 Tables E.2-E.3 -->

Three readings, in decreasing confidence. (i) The B→B zero is bookkeeping, not a switch: it lets the DP extend a byte excursion at marginal cost 8 bits/character, with run-length costs handled separately — Annex E's tables simply have no cell for "stay in the excursion", and the code repurposes one. (ii) The four extra finite transitions (N→A, N→B, A→L, A→N) *widen* the encoder's option set relative to the extract, and each is backed by a concrete token; N→B shift in particular is load-bearing for long numeric-adjacent byte runs and is regression-guarded (`test-cascade-hv`; the 6-bit token was itself a fork fix — JC-T §4.4.2). Whether these tokens are part of clause 5.3's normative switch grammar — in which case Annex E's 106s understate the standard — or fork extensions a strict decoder would reject, cannot be resolved from the extract: clause 5.3's own switch tables are **NOT FOUND** in the corpus pull. What is verifiable is one-sided: every token Annex D exercises, this table reproduces. (iii) The missing L→N shift is the mirror case: the token exists (127), the cost row forbids it, so the DP can never emit it — for an isolated digit inside lowercase text the encoder pays latch-out-latch-back (5 + 4 + 6 = 15 bits) where E.2's shift would pay 7 + 4 = 11. A four-bit optimality gap on a legal-per-the-extract transition: mode *selection* quality, not decodability, since nothing invalid is emitted. <!-- anchor: encoder.h:186-220; corpus §2.2 test-cascade-hv -->

**One honest asterisk on optimality.** The byte-run corrections — +13 bits when a byte run exceeds 15 characters, and per-8207-block re-shift costs beyond that ("2^13+15", encoder.c:497-524) — are applied during the backtrack, *after* the argmin over final states. The forward recursion prices byte characters at a flat 8 bits, so for messages with long byte runs the selected sequence is optimal with respect to slightly understated byte costs, and the reported length is corrected post hoc. The pass is therefore exactly optimal on the trellis it prices, and approximately optimal on the true emitted-length objective when the >15-byte-run regime engages. (Analysis from source; consistent with the mechanism JC-T §4.4.2 describes.) `encodeData` (encoder.c:723) then serializes the chosen sequence — switch tokens in `latch_shift_to[from][to]` bits, characters in `character_size[mode]` bits, byte count headers separately — and the byte-identical round trip of the Annex D message is the operative regression for all of this machinery (`test-roundtrip`; JC-T §4.4.3). <!-- anchor: encoder.c:478-576, 497-524, 723; corpus §2.2 -->

**Verdict.** `analyzeInputData` implements Annex E's 14-state trellis dynamic program — genuinely, with value table, backpointers and backtrack, not a greedy scan — driven by a cost table that deviates from the extracted Tables E.2/E.3 in the six cells above, plus post-hoc byte-run cost corrections outside the forward recursion.

## Exercises

**1 (guided).** Hand-run the recursion on the two-character input "A1" ('A' is U-value 1 / A-value 11; '1' is N-value 2 / A-value 2; neither is in L). Give the final state values, the winner, and the emitted bit stream.

<details><summary>Answer</summary>

Layer 1 ('A'): U = 0+0+5 = 5; A = 0+5+6 = 11. Layer 2 ('1'): '1' is not in U, so U gets only the byte fold (5+11+8 = 24); N = 5 + w(U→N)=5 + 4 = 14; A = min(5+5, 11+0) + 6 = 16. Winner: N at 14. Backtrack: N ← U. Stream: "00001" ('A' = 1 in U, 5 bits) + "11101" (U→N latch, token 29, 5 bits) + "0010" ('1' = 2 in N, 4 bits) = 14 bits.
</details>

**2 (guided).** Prove that in any layered DAG (arcs only from layer i to layer i+1, nonnegative arc costs) the recursion of §7.3 computes exact shortest paths in one sweep, and exhibit where the proof uses (a) layering, (b) additivity. Then say precisely why the same one-sweep argument fails on a graph with intra-layer arcs.

<details><summary>Hint</summary>

Follow §7.3's induction; layering enters where the path decomposition "visits layer i exactly once" is claimed, additivity in the splice inequality. With intra-layer arcs, a node's value can depend on same-layer nodes not yet finalized — you then need an ordering by value (Dijkstra) or repeated relaxation (Bellman-Ford). The mode trellis avoids all of that by construction.
</details>

**3 (open).** Count the exact work of `analyzeInputData` per input character from the source: the 14 × 14 min-update (encoder.c:374-392), the CharSize layer fill (encoder.c:360-372), and the shift-back pass (encoder.c:394-432) — expressing the total as α·n + β additions/comparisons with explicit α. Then determine how much of the 14 × 14 product is provably wasted given that rows 7-13 of `latch_shift_to` duplicate rows 0-6, and sketch the 7-state-plus-annotations variant that exploits it. Would its output ever differ?

## Further reading

- ISO/IEC 23634:2022 clause 5.3, Annex E (Tables E.1-E.3), Annex D.
- R. Bellman, *Dynamic Programming*, Princeton University Press, 1957 — the principle of optimality, stated and named.
- R. Bellman, "On a routing problem", *Quarterly of Applied Mathematics* 16, 1958 — the shortest-path recursion itself.
- A. Viterbi, "Error bounds for convolutional codes and an asymptotically optimum decoding algorithm", *IEEE Trans. Information Theory* 13(2), 1967 — the same min-plus trellis sweep in its most famous costume; useful contrast because there the trellis decodes and here it encodes.
- Siblings: [02-information-density.md](02-information-density.md) (where the saved bits go), [01-notation.md](01-notation.md) (graph and DP conventions); maintainer view and regression vectors: [../developers-manual/04-encoder.md](../developers-manual/04-encoder.md) §4.4.
