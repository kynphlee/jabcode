# 10. Cascade combinatorics and traversal

<!-- objective: A mathematically mature reader can characterize the 61-position docking lattice and its decode order as BFS with a visited set, prove termination and completeness of the layered traversal, and analyze the primary/secondary efficiency asymmetry and the implicit-vs-unbounded ceiling. -->

**Where it lives.** The 61-entry position table: `jab_symbol_pos` (`src/jabcode/encoder.h:111-119`). The cap: `MAX_SYMBOL_NUMBER 61` (`src/jabcode/include/jabcode.h:24`). Host/slave wiring: `assignDockedSymbols` (`src/jabcode/encoder.c:1598`). Spec: ISO/IEC 23634:2022 clause 4.5 (cascading, decode layers 4.5.2, Figures 11-15), Annex A.3 (when to cascade). Overhead ledger: chapter 2 ([02-information-density.md](02-information-density.md)); implementation reference: [JC-T ch. 4](../developers-manual/04-encoder.md).
<!-- anchor: src/jabcode/encoder.h:111-119 -->
<!-- anchor: src/jabcode/include/jabcode.h:24 -->
<!-- anchor: src/jabcode/encoder.c:1598 -->
<!-- anchor: ISO 23634 4.5, Annex A.3 -->

## The problem

One symbol tops out at side-version 32 — 145 × 145 modules — and payloads do not. JAB's answer is composition: dock secondary symbols to a primary, edge to edge, sharing geometry so that one detection pass serves the whole assembly. Composition immediately raises three combinatorial questions. *Where* may symbols sit — what is the address space? *Which* arrangements are legal — what constraints does docking impose? And *in what order* does a decoder that has found only the primary discover the rest? The spec answers all three with one table (Figure 14's position indices), one rule (docked sides share side-version), and one procedure (the layered decode order of 4.5.2). This chapter shows that the three answers are a single mathematical object: breadth-first traversal of the integer lattice.

Traversal is a third-occurrence family in this book (graphs and their traversals were set up in chapter 1; the trellis walk of chapter 7 was the second pass), so per the fade schedule the central proof is stated at exercise grade — full structure, brief steps.

## Theory

### The position table is a lattice ball, enumerated breadth-first

`jab_symbol_pos` (`encoder.h:111-119`) lists 61 offsets (x, y) ∈ Z², beginning

$$
(0,0);\;\; (0,-1), (0,1), (-1,0), (1,0);\;\; (0,-2), (-1,-1), (1,-1), (0,2), (-1,1), (1,1), (-2,0), (2,0);\;\dots
$$

with y negative meaning *above* the primary (the top-neighbour test in `assignDockedSymbols` is y_host − 1, `encoder.c:1625`). Two characterizations, both checkable against the table.

**As a set:** the 61 entries are exactly the closed L1 ball of radius 5,

$$
B_1(5) = \{\, p \in \mathbb{Z}^2 : \|p\|_1 \le 5 \,\}, \qquad
|B_1(5)| = 1 + \sum_{r=1}^{5} 4r = 61,
$$

since the L1 sphere of radius r in Z² contains 4r points. The "lattice spiral" of the chapter outline is this: concentric diamonds around the primary. That 61 = |B₁(5)| is presumably why `MAX_SYMBOL_NUMBER` is 61 and not a rounder number — it is the smallest ball exhausting five docking hops.

**As a sequence:** the enumeration order is breadth-first search from the origin with the fixed neighbour order (top, bottom, left, right) — i.e. (x, y−1), (x, y+1), (x−1, y), (x+1, y). Run BFS by hand: dequeue (0,0), enqueue its four neighbours — indices 1-4 match the table; dequeue (0,−1), enqueue (0,−2), skip visited (0,0), enqueue (−1,−1), (1,−1) — indices 5, 6, 7 match; dequeue (0,1) — indices 8, 9, 10; dequeue (−1,0) — its top (−1,−1) and bottom (−1,1) are already visited, its left (−2,0) is index 11; and so on. The check continues to hold through index 60 = (5,0), the last point of ring 5. (Exercise 2 turns this spot-check into a proof.) So the table is not an arbitrary gazetteer: it is the output of a canonical algorithm, and every property we need below — ring monotonicity, parent-before-child — comes for free from BFS theory.

### Docking as a compatibility relation

Symbols may only dock edge to edge: symbol at position p can host a symbol at position q only if ‖p − q‖₁ = 1. The legal arrangements are therefore the connected induced subgraphs of the grid graph on B₁(5) that contain the origin — the primary must be present (writer CLI: master must be position 0, `jabwriter.c:397-403`) and every secondary must be reachable through docked neighbours, which the encoder enforces by failing any symbol left hostless ("Slave symbol at position %d has no host", `encoder.c:1668-1675`).

Docking is also a *compatibility* relation, not just adjacency: clause 4.5 requires docked sides to share their side-version — a symbol docked left-right to its host must agree with the host's vertical side-version; top-bottom docking constrains the horizontal one. Model it as a constraint graph: vertices are the occupied positions, each carrying a variable (v_x, v_y) ∈ \{1..32\}²; every horizontal docking edge imposes equality of the two v_y values, every vertical docking edge equality of the two v_x. Constraint propagation over a connected component shows the practical consequence: along any horizontally-docked *row* of the assembly all v_y agree, along any vertically-docked *column* all v_x agree — the assembly is dimensionally coherent by construction, which is precisely what lets the decoder infer every secondary's geometry from its host's metadata plus one docking direction (metadata Part III; decoder side `decodeSlaveMetadata`, `decoder.c:1161`, and `decodeSlave`, `decoder.c:2377`). The spec adds a soft preference with the same graph-theoretic flavour: the primary "should" be the largest symbol (clause 4.5) — put the root where the capacity is.

### The decode order is BFS with a visited set

Clause 4.5.2 prescribes the decode order in layers; from the verified extract, verbatim in structure: the first layer is the primary (0), then its top (1), bottom (2), left (3), right (4); the second layer takes symbol 1's neighbours — top (5), primary (skip), left (6), right (7); the third layer takes symbol 2's — top skipped, bottom (8), left (9), right (10); and in the spec's fourth-layer example, from symbol 3: top (6, skip if decoded), bottom (9, skip), left (11), right (primary, skip). The rule stated in the spec: already-visited symbols are skipped and the cycle proceeds.

**Claim.** The 4.5.2 procedure is breadth-first search over the docking graph, rooted at the primary, with neighbour order (top, bottom, left, right); consequently, on the full ball B₁(5) it reproduces `jab_symbol_pos` index for index, and on any legal (connected, origin-rooted) assembly it visits every symbol exactly once and terminates.

*Proof (exercise-grade).* Identify 4.5.2's "layers" with BFS queue generations: layer 0 is the root; layer k + 1 is produced by scanning layer-k symbols in their discovery order and appending each unvisited neighbour in the fixed side order — which is exactly the specification's cycle, with its skip rule playing the role of the visited set. Three standard facts finish it. **(Termination)** Each iteration either marks one unvisited symbol visited or moves the scan forward; both resources are finite (≤ 61 symbols, ≤ 4 sides each), so the procedure halts after at most 4·n side-inspections. **(No repeats)** A symbol is assigned an index only at the moment it turns from unvisited to visited; that happens at most once. **(Completeness)** Induction on graph distance from the root: if every symbol at docking-distance k receives an index, then any symbol s at distance k + 1 has a docked neighbour h at distance k; when h's sides are scanned, s is either already visited (indexed earlier — fine) or indexed then. Since a legal assembly is connected, every symbol has finite distance and is reached. **(Index agreement)** On B₁(5), BFS from the origin with a fixed neighbour order is deterministic; the hand-check above verified the first two rings and the spec's own fourth-layer example (indices 6, 9, 11 from symbol 3) against `jab_symbol_pos` — determinism does the rest. ∎

The payoff is conceptual economy: Figure 14's indices need not be stored, only *derived* — encoder and decoder can agree on the numbering of any assembly by running the same three-line traversal. (The implementation stores the table anyway, as static data — constant lookup beats recomputation, and the table doubles as the definition for conformance.)

### The ceiling: 60, 61, and unbounded

How many symbols can a cascade hold? The spec is deliberately open-ended in one direction and concrete in another: "The indices of the first 60 secondary symbols are defined in Figure 14." *The first 60* — the metadata recursion itself (each symbol's Part III can declare further slaves on its free sides) has no intrinsic bound; nothing in the docking algebra stops ring 6. The 61 is thus an *implicit* limit — the largest assembly whose addresses the standard bothers to define — rather than a hard one. The implementation makes it hard: `MAX_SYMBOL_NUMBER 61` (`jabcode.h:24`) sizes the position table, the writer's validation ("must be 1 - 61", `jabwriter.c:220-224`), and the decoder's symbol slots (`jabreader.c:53-54`). An implementation choosing to extend the enumeration could do so compatibly in geometry (BFS generates ring 6 as readily as ring 5) but not in interchange — indices past 60 name positions no conforming decoder is required to know.

### The economics: what a secondary saves and what it risks

Secondaries are cheaper per module than primaries, and the spec quantifies the headline term: a primary spends FPrimary = 4 × 17 = 68 modules on finder patterns; a secondary, which has none and is located purely by its host's geometry, spends FSecondary = 4 × 7 = 28 modules on its corner-marker substitutes — a saving of 40 modules per symbol before the smaller items are counted. The remaining deltas (metadata: a primary's Part I + Part II against a slave's shorter metadata, `decoder.h:20-25` vs `decodeSlaveMetadata`; palette blocks; alignment-pattern counts by side-version, `jab_ap_num`, `encoder.h:285-292`) are itemized in chapter 2's capacity ledger ([02-information-density.md](02-information-density.md)) and not re-derived here.

Annex A.3 frames when to spend that saving: cascade when the message exceeds a single primary's capacity, when the available space is irregularly shaped, or when several small symbols are preferred over one large one — and it attaches the caveat that cascading "may consequently decrease decoding reliability." The reliability caveat has a combinatorial reading this book will sharpen in chapter 11: the docking graph is a *tree of dependencies rooted at the primary* — a secondary is decodable only if the chain of hosts back to the primary decoded (each link needs its host's Part III), so symbol failures are not independent, and the primary's four finder patterns are load-bearing for all n symbols at once.

Finally, the physical cap. Clause 4.5 requires "at least 5 pixels per module" for cascades. Fix a capture budget of W × H pixels: an assembly of n symbols at side s modules spans roughly (a√n·s)·5 pixels per axis at the minimum (a the aspect of the arrangement), so pixel budget bounds n·s² — total module count — regardless of how the docking graph arranges it. Cascade depth is bought with resolution, and the detector enforces a floor of its own well before 5 px is reached in bad light: patterns whose modules image below ~3 scanlines are discarded by the vote threshold (`detector.c:1323-1325`; chapter 9). Between the spec's 5 and the detector's 3 there is slack, not safety.

## Back to the code

`assignDockedSymbols` (`encoder.c:1598-1677`) *is* the traversal, disguised as three nested loops. The user supplies positions in arbitrary order (`--symbol-position`, `jabwriter.c:358`); the function initializes every symbol hostless (`host = -1` — the visited set, `encoder.c:1601-1608`), then scans hosts i in increasing slave-index order, sides j in the order top, bottom, left, right (`encoder.c:1622-1655`, the four adjacency tests against `jab_symbol_pos`), and unassigned candidates k. On a match it swaps the found slave into slot `assigned_slave_index` (`swap_symbols`, `encoder.c:1580`) and increments. Because newly discovered slaves are appended at the end of the very array that i is scanning, the array functions as the BFS queue: i is the dequeue pointer, `assigned_slave_index` the enqueue pointer. The loop structure differs from textbook BFS in one harmless way — it iterates j (sides) outside k (candidates) and re-scans, but since each (host, side) pair matches at most one position, the discovery order is the same. Afterwards, any symbol still hostless triggers the connectivity failure (`encoder.c:1668-1675`). The slaves' docking metadata (Part III flags, `slaves[]`/`host` fields, `jabcode.h:158-159`) records the tree edges; the decoder replays the same order from the primary outward (`decodeSlave` per docked side, `decoder.c:2377`; status 2 "partly decoded" when a subtree fails, `jabreader.c:66-69` — the dependency-tree reading made operational).

## Exercises

**1 (guided).** A cascade docks: symbol A at the primary's right, B at A's right, C at the primary's top, D at C's left. Using the layer procedure (equivalently, BFS with side order top-bottom-left-right), assign all decode indices and give each symbol's `jab_symbol_pos` offset.

<details><summary>Answer</summary>

Primary (0,0) → index 0. Layer 1, scanning primary's sides: top (0,−1) = C → index 1; bottom — absent; left — absent; right (1,0) = A → index 2? Careful: the *position-table* indices and the *decode* indices differ when the assembly is sparse. Decode indices number only present symbols in discovery order: primary 0; C 1 (top before right); A 2. Layer 2: from C (0,−1): top absent, bottom = primary (visited), left (−1,−1) = D → 3, right absent; from A (1,0): top/bottom absent, left = primary (visited), right (2,0) = B → 4. Offsets: C (0,−1) = `jab_symbol_pos[1]`, A (1,0) = `jab_symbol_pos[4]`, D (−1,−1) = `jab_symbol_pos[6]`, B (2,0) = `jab_symbol_pos[12]`. Note D and B swap order relative to their position-table indices (6 vs 12 but decode order 3 then 4 — consistent) — sparse assemblies preserve BFS order but compress the numbering.

</details>

**2 (guided).** Prove no position is enumerated twice in `jab_symbol_pos` — i.e. that BFS from the origin on Z² with a fixed neighbour order and a visited set emits pairwise-distinct lattice points, and that the first 61 emitted are exactly B₁(5).

<details><summary>Hint</summary>

Distinctness is the visited-set invariant from the theory section: a point is emitted only on its unvisited→visited transition. For the ball: prove by induction that BFS on Z² dequeues points in non-decreasing L1 norm (each emitted neighbour has norm within ±1 of its parent, and the queue is FIFO — the standard BFS level property), so the emission order lists ring 0, then ring 1, …; rings 0-5 total 1 + 4 + 8 + 12 + 16 + 20 = 61, hence entries 0-60 are precisely B₁(5). No coordinate arithmetic on the table itself is needed — that is the point of characterizing it as an algorithm's output.

</details>

**3 (open).** Using chapter 2's capacity ledger, compare the net payload of a 5-symbol "plus" cascade — primary at (0,0), secondaries at the four ring-1 positions, all at side-version v — against a single symbol of comparable printed area (side-version ≈ v·√5 ≈ 2.24 v, subject to the 4v + 17 lattice and the version cap 32). Account for: four finder sets saved thrice over (28 vs 68), five metadata blocks vs one, per-symbol palettes, and the 5 px/module floor at fixed capture resolution. For which (v, Nc) does the cascade win on payload, and what does it pay in the dependency-tree reliability terms of this chapter? (The adversarial version of this question is chapter 11, exercise 3's cousin.)

## Further reading

- T. H. Cormen, C. E. Leiserson, R. L. Rivest, C. Stein, *Introduction to Algorithms*, 4th ed., MIT Press, 2022 — ch. 20 (elementary graph algorithms): BFS, the level property, and the visited-set invariants used verbatim above.
- D. E. Knuth, *The Art of Computer Programming*, vol. 1, 3rd ed., Addison-Wesley, 1997 — §2.2.1 for queues and the traversal-as-data-structure view (`assignDockedSymbols`' array-as-queue is exercise material there).
- J. H. Conway, N. J. A. Sloane, *Sphere Packings, Lattices and Groups*, 3rd ed., Springer, 1999 — ch. 4 on Z^n and its norms, for L1 balls and lattice enumeration.
