# 1. Notation and prerequisites

<!-- objective: A mathematically mature reader can map every ISO math symbol (Nc, C, Pn, Pg, Pe, K, H, wc, wr, R) to its code counterpart or NOT-FOUND status, and state the GF(2), permutation, graph and information-theoretic conventions this book uses. -->

**Where it lives.** ISO/IEC 23634:2022 Clauses 1–3 (symbol list) and 5.4.1 (the rate identity); `src/jabcode/encoder.h:226-241` (the rate and weight tables); `src/jabcode/include/jabcode.h:162` (the one spec symbol that survives as a public struct field); `src/jabcode/ldpc.c:645-651` and `:906-924` (where the payload symbols live as local variables); the corpus glossary at [../corpus-model.md](../corpus-model.md) §5. <!-- anchor: encoder.h:226-241 --> <!-- anchor: jabcode.h:162 --> <!-- anchor: ISO 23634 Clauses 1-3 -->

## The problem

This book reads a C codebase and an international standard side by side, and the two do not speak the same dialect. The standard writes `Pn`, `Pg`, `K`, `H`, `wc`, `wr`; the code writes `capacity`, `nb_pcb`, `matrixA`, `wcwr[2]` — and occasionally the spec's own letters, as local variables that appear in no header. Before any theory can be anchored, we need a translation table with honest gaps: some spec symbols have no code counterpart at all, and the corpus discipline (extract, never invent) requires saying so rather than papering over it.

This chapter is also the book's single point of full setup for the base machinery — linear algebra over the two-element field, permutations as bijections, bipartite graphs, and the entropy accounting behind "bits per module." Every later chapter cites these constructions instead of re-deriving them; that is the faded-scaffolding contract of the whole book. If a later chapter seems terse, the missing steps are here.

One typographic convention up front: the ISO text prints decimal commas. When we quote such a value we reproduce it exactly and tag it, e.g. the level-3 code rate 0,55 (as printed); when we compute, we use ordinary decimal points.

## The symbol table: spec ↔ code ↔ this book

The following table is the book's contract. "NOT FOUND" entries are verified absences, inherited from the corpus model and re-checked against source for this chapter; they are facts about the code, not omissions of ours.

| ISO symbol | Meaning (Clause 3 extract) | Code counterpart | This book |
|---|---|---|---|
| `Nc` | colour-mode index; colour count is 2 to the power (Nc+1) | `jab_metadata.Nc` (jabcode.h:192); `color_number` throughout the encoder | `Nc` |
| `C` | listed in Clause 3; its one-line definition did not survive the spec extraction. In Annex C's row-count formula it plays the role the code calls `capacity` (the number of matrix columns) | `capacity` parameter of the LDPC matrix builders (ldpc.c:172, 430) — contextual match, not a named identifier | `n` (codeword length) where the coding-theory role is meant |
| `Pn` | "symbols net payload (the number of raw data bits)" | no public identifier (corpus §5 records it as spec-only on the API surface); it does, however, appear as a **local variable** `Pn` inside `encodeLDPC` (ldpc.c:648-651) and `decodeLDPChd` (ldpc.c:910) | `k` (message length), or `Pn` when quoting spec or source |
| `Pg` | gross payload length | `jab_symbol.Pg` — "Gross payload length (ecc_encoded_data->length)" (jabcode.h:162); `jab_metadata.Pg` (jabcode.h:197); locals in ldpc.c | `n`, or `Pg` when quoting |
| `Pe` | metadata ECC-expanded length (26 → 44 bits for primary metadata, per the project clause map of ISO 4.4) | **NOT FOUND** as a code identifier (corpus §5) | `Pe`, spec contexts only |
| `K` | number of parity-check symbols; Annex D uses it as such ("Pg = 1071 and K = 476", and 1071 × 4/9 = 476 exactly) | `nb_pcb` in the matrix builders (ldpc.c:174-178, 432); after elimination, `matrix_rank` carries the effective count | `m` (check count) |
| `H` | parity-check matrix | `matrixA` as built (ldpc.c:172, 430), `matrixH` inside Gauss–Jordan (ldpc.c:246) | `H` |
| `wc` | number of ones per column of `H` | `ecclevel2wcwr[level-1][0]` (encoder.h:234); `jab_symbol.wcwr[0]` (jabcode.h:161) | `wc` |
| `wr` | number of ones per row of `H` | `ecclevel2wcwr[level-1][1]`; `jab_symbol.wcwr[1]` | `wr` |
| `R` | code rate; Clause 5.4.1 defines R = Pn/Pg | `ecclevel2coderate[11]` (encoder.h:226) | `R` |

<!-- anchor: encoder.h:226, 234 --> <!-- anchor: jabcode.h:161-162, 192, 197 --> <!-- anchor: ldpc.c:648-651, 910 --> <!-- anchor: ISO 23634 5.4.1 --> <!-- anchor: ISO 23634 Annex C --> <!-- anchor: ISO 23634 Annex D -->

Two refinements over the corpus glossary, established while building this table. First, `Pn` is not purely spec-side: it exists as a local in both LDPC entry points, so a maintainer grepping for it will get hits — just never in a header. Second, the declaration comment at ldpc.c:648 labels its four variables "number of '1' in column / number of '1' in row / gross message length / number of parity check symbols" — positionally, that last comment falls on `Pn`, which the very next line assigns `data->length`, the net message. The comment is misaligned; the assignment is authoritative. <!-- anchor: ldpc.c:648-651 -->

## Theory

### Linear algebra over GF(2)

The field with two elements, written GF(2), is the set with elements 0 and 1 under addition modulo 2 (which is XOR) and ordinary multiplication (which is AND). Every element is its own additive inverse, so subtraction **is** addition — a fact the source exploits directly: the Gauss–Jordan comment "subtract pivot row GF(2)" sits above an XOR loop. <!-- anchor: ldpc.c:318-320 -->

A length-`n` bit vector is an element of the vector space of n-tuples over GF(2). Linear combinations are XORs of subsets; a set of vectors is linearly independent when no nonempty subset XORs to zero. The **rank** of a matrix is the maximum number of linearly independent rows (equivalently columns — the standard proof carries over verbatim since GF(2) is a field).

**Row reduction.** Gaussian elimination works over any field: the three row operations (swap two rows, scale a row by a nonzero scalar, add a multiple of one row to another) preserve the row space. Over GF(2) the middle operation is vacuous (the only nonzero scalar is 1) and the third is a row XOR, so elimination is: find a pivot 1, then XOR the pivot row into every other row that has a 1 in the pivot column. Repeating per row either finds a pivot or exposes a zero row; the number of pivots is the rank. This is precisely the loop structure of `GaussJordan` (ldpc.c:235), including its bookkeeping for zero rows (`zero_lines_nb`) and for pivot columns found out of order (`swap_col`). <!-- anchor: ldpc.c:235-328 -->

**Linear codes.** A binary linear code of length `n` is a subspace; we describe it as the kernel of an `m × n` parity-check matrix `H`:

$$
\mathcal{C} \;=\; \{\, c \in \mathrm{GF}(2)^n \;:\; H c = 0 \,\}
$$

By rank–nullity the dimension is

$$
k \;=\; n - \operatorname{rank}(H)
$$

and the **rate** is `R = k/n` — this is exactly the spec's R = Pn/Pg once you identify net payload with message dimension and gross payload with codeword length. <!-- anchor: ISO 23634 5.4.1 -->

**Systematic form and the generator matrix — full derivation.** Suppose elimination (with column swaps recorded, as `GaussJordan` records them) brings `H` to the form

$$
H' \;=\; \begin{pmatrix} I_m & P \end{pmatrix}
$$

with `I` the `m × m` identity and `P` an `m × k` block. Split a codeword as `c = (c_head, c_tail)` with `c_head` of length `m`. The parity condition reads

$$
I_m\, c_{\text{head}} + P\, c_{\text{tail}} = 0
\quad\Longleftrightarrow\quad
c_{\text{head}} = P\, c_{\text{tail}}
$$

(the sign vanishes because minus is plus in GF(2)). So `c_tail` ranges freely over all `k`-bit messages and determines `c_head`. Writing the message as `x`, every codeword is

$$
c \;=\; G x, \qquad G \;=\; \begin{pmatrix} P \\ I_k \end{pmatrix}
$$

and conversely `H' G = P + P = 0`, so the image of `G` is exactly the code. This `G` is what `createGeneratorMatrix` builds — its comment reads "remember matrixA is now A = \[I CT\], now use it and create G=\[CT / I\]" — with the message bits sitting verbatim in the last `k` codeword positions (a **systematic** encoding; the decoder harvests them from position `matrix_rank` onward). Annex C states the same thing multiplicatively: "c = m ⊗ G over GF(2)". <!-- anchor: ldpc.c:476-513 --> <!-- anchor: ldpc.c:1043-1046 --> <!-- anchor: ISO 23634 Annex C -->

One caution that matters later: if `H` has dependent rows, `rank(H) < m` and the code is **larger** than the nominal count of checks suggests. The source tracks this with `matrix_rank`; chapter [3](03-ldpc-coding-theory.md) exhibits a natural parity-check matrix whose rows always XOR to zero.

### Permutations as bijections

A permutation of the index set with `n` elements is a bijection from that set to itself; under composition they form the symmetric group. In code a permutation is an array `perm` with `perm[i]` the image of `i` — exactly the `permutation` arrays initialized to the identity in the LDPC matrix builders. <!-- anchor: ldpc.c:189-197 -->

Facts we use, stated once:

- The inverse of a permutation is a permutation; applying a permutation to the columns of a matrix is multiplication by a permutation matrix `Q`, and over GF(2) the transpose of `Q` is its inverse.
- Column-permuting a parity-check matrix produces an **equivalent** code: the same weights, rank and minimum distance, with coordinates relabelled.
- A sequence of swaps ("draw a position, swap it to the end") built from a deterministic pseudo-random stream defines one fixed permutation per seed. The construction used throughout this codebase is the Fisher–Yates shuffle driven by `lcg64_temper` and `pn_index`; the proof that it induces the uniform distribution over permutations — and the analysis of what determinism buys and costs — is chapter 5's first full permutation argument. Here we only need that a fixed seed yields a fixed, reproducible bijection. <!-- anchor: pseudo_random.c:10-30 -->

### Graphs and traversals

A graph is a pair of vertex and edge sets; a **bipartite** graph splits vertices into two classes with edges only across classes. The bipartite graph attached to an `m × n` matrix `H` — one **variable node** per column, one **check node** per row, an edge where the entry is 1 — is the **Tanner graph** of the code. Column weight `wc` and row weight `wr` are the two degree sequences; a code whose Tanner graph is biregular with small constant degrees is *low-density*, which is the L and D of LDPC. Short cycles in the Tanner graph degrade iterative decoding (the messages of chapter [3](03-ldpc-coding-theory.md) stop being independent); we use this only as a stated fact.

Traversals: breadth-first search visits vertices in non-decreasing distance from a root, maintaining a visited set; depth-first search recurses. We fix the definitions here because chapter 10 proves the cascade decode order is a BFS with a visited set; no traversal theory beyond the definitions is needed until then.

### Bits, entropy and the channel

The **self-information** of an outcome with probability `p` is the negative base-2 logarithm of `p`, in bits. The **entropy** of a discrete random variable with distribution `p` over `q` outcomes is

$$
H(X) \;=\; -\sum_{i=1}^{q} p_i \log_2 p_i
$$

**The uniform distribution maximizes entropy — full but compact.** By Gibbs' inequality, for any distributions `p`, `u`:

$$
-\sum_i p_i \log_2 p_i \;\le\; -\sum_i p_i \log_2 u_i
$$

with equality iff `p = u` (proof: the difference is the relative entropy, nonnegative because the natural log satisfies ln x ≤ x − 1). Take `u` uniform, so every `u_i` equals `1/q`; the right side becomes `log_2 q` regardless of `p`. Hence

$$
H(X) \;\le\; \log_2 q
$$

with equality exactly at the uniform distribution.

This single line is the density law of the whole symbology: a module drawn from an alphabet of `q` equiprobable colours carries `log_2 q` bits, and no assignment of colour frequencies can do better. With colour count 2 to the power (Nc+1), density is Nc+1 bits per module. The source computes it as `nb_of_bpm = round(log(color_number)/log(2))` — the change-of-base identity, with a defensive `round` for libm implementations that return values like 5.999… for the base-2 log of 64. <!-- anchor: encoder.c:672 -->

**Channel intuition.** A printed-and-scanned module is a use of a noisy channel: the classifier outputs a colour index that differs from the written one with some probability. The simplest model, the binary symmetric channel with crossover probability `p`, has capacity 1 − H₂(p) where H₂ is the binary entropy function; Shannon's noisy-channel coding theorem (stated, not proved — this is classical background, see Further reading) says rates below capacity are achievable with vanishing error probability by coding. That theorem is the *permission slip* for chapters [2](02-information-density.md) and [3](03-ldpc-coding-theory.md): it is why spending a fraction 1 − R of the symbol's raw bits on redundancy is not waste but the price of reliability, and why the interesting engineering question is only ever *which* code and *which* R.

## Back to the code

How the conventions above actually surface in the source, verbatim:

- The rate table — one float per level, plus a level-0 placeholder: `static const jab_float ecclevel2coderate[11] = {0.55f, 0.63f, 0.57f, 0.55f, 0.50f, 0.43f, 0.34f, 0.25f, 0.20f, 0.17f, 0.14f};`. Entries 1–10 mirror ISO Table 20's R column (level 3 printed there as 0,55). <!-- anchor: encoder.h:226 --> <!-- anchor: ISO 23634 Table 20 -->
- The weight table — `static const jab_int32 ecclevel2wcwr[10][2] = {{3, 8}, {3, 7}, {4, 9}, {3, 6}, {4, 7}, {4, 6}, {3, 4}, {4, 5}, {5, 6}, {6, 7}};`, indexed level minus 1, with `wcwr_for_level` normalizing an unset level 0 to `DEFAULT_ECC_LEVEL`. <!-- anchor: encoder.h:234, 241-244 -->
- The gross payload is the one spec symbol promoted to the public ABI: `jab_symbol.Pg` and `jab_metadata.Pg`, both documented as gross payload length. Everything else in the table above lives (at most) in function scope.
- `H` never exists as a dense named matrix object with that letter: it is a bit-packed `jab_int32` array, 32 columns per word, MSB first — the packing that every index expression of the form `matrix[j*offset + k/32] >> (31 - k%32)` unwinds. Reading chapter [3](03-ldpc-coding-theory.md)'s code walks requires exactly this one convention. <!-- anchor: ldpc.c:203, 296 -->

Deeper code-level treatment of the LDPC surface is JC-T's [../developers-manual/06-ldpc.md](../developers-manual/06-ldpc.md); the public struct contracts are in [../developers-manual/03-public-surface-jabcode-h.md](../developers-manual/03-public-surface-jabcode-h.md).

## Exercises

Faded from guided to open; solutions in the fold.

**1 (guided).** Using the density law, how many bits does one module carry at Nc = 5 (64 colours)? At Nc = 0 (the fork's 2-colour Mode 0)? Which line of `encoder.c` computes this, and why does it call `round`?

<details><summary>Solution</summary>

Density is the base-2 log of the colour count: 6 bits per module at 64 colours, 1 bit per module at 2 colours. The computation is `nb_of_bpm = (jab_int32)round(log(enc->color_number) / log(2))` at encoder.c:672; `round` defends against libm builds whose `log`-quotient lands just below the integer (the fork's comment records ARM glibc returning 5.999… for the base-2 log of 64).

</details>

**2 (guided → open).** Let `H' = (I_m | P)` be a systematic parity-check matrix and `G = (P over I_k)` the generator derived above. (a) Verify H'G = 0 by block multiplication. (b) Now suppose Gauss–Jordan had to swap columns to reach systematic form, as `GaussJordan` sometimes does (the `swap_col` array). State precisely what object the swaps must also be applied to, so that encoder and decoder agree on which codeword positions are message bits.

<details><summary>Hints and solution</summary>

(a) Block multiply: the product is I·P + P·I = P + P = 0 over GF(2). (b) The swaps define a permutation of the `n` codeword coordinates. Encoder and decoder each run the same deterministic elimination on the same seeded matrix, so each independently derives the same permutation — determinism substitutes for transmitting it. This is a first instance of the determinism-as-interop contract that exercise 3 of chapter [3](03-ldpc-coding-theory.md) develops.

</details>

**3 (open).** The symbol table records `Pe` as NOT FOUND in code and `Pn` as local-only. Search the tree yourself and either confirm or refute both statuses. Then argue, in one paragraph, why a codebase can be a faithful implementation of a standard while sharing almost none of its variable names — and what artifact (table, test, or document) has to exist for that to remain safe under maintenance.

<details><summary>Discussion</summary>

Both statuses hold at fork commit `8f76559` (`Pn` appears at ldpc.c:648-651 and 910 only; `Pe` nowhere). Faithfulness is behavioral — wire-format equality on shared vectors — not lexical. What must exist is exactly a maintained translation table (this chapter) plus executable interchange vectors (JC-T treats the Annex D message as that regression vector); without them, renames and refactors silently sever the spec linkage.

</details>

## Further reading

- C. E. Shannon, *A Mathematical Theory of Communication*, Bell System Technical Journal, 1948 — entropy, the channel, and the coding theorem this chapter states.
- R. G. Gallager, *Low-Density Parity-Check Codes*, MIT Press, 1963 — the codes of chapter 3; listed in the ISO/IEC 23634 bibliography.
- T. K. Moon, *Error Correction Coding: Mathematical Methods and Algorithms*, Wiley, 2005 — also listed in the ISO bibliography; covers GF(2) linear algebra and systematic encoding at textbook depth.
- D. J. C. MacKay, *Information Theory, Inference, and Learning Algorithms*, Cambridge University Press, 2003 — the standard modern treatment of both the entropy material and LDPC codes.
