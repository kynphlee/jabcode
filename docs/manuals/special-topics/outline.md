# JC-S Special Topics — Stage 1 Outline (awaiting Stage 2 approval)

**Book:** jabcode Special Topics: Math, Logic and Game Theory · **Voice:** Expert with faded scaffolding — full derivation on first occurrence, sketched proof on second, statement-plus-exercise thereafter · **Bloom ceiling:** Analyze → Evaluate → Create
**Audience:** readers with mathematical maturity (linear algebra, discrete probability, basic combinatorics assumed after ch. 1).
**Corpus:** `docs/manuals/corpus-model.md` (fork @ `8f76559`, line-drift-corrected 2026-07-15) · ISO/IEC 23634:2022 via sub-agent · the JC-T findings register (`../developers-manual/index.md`) as verified input.
**Diátaxis:** pure explanation. Every chapter anchors, first thing, to where the theory lives in code and spec (Template C). TOC follows the concept graph: information → coding → permutation → optimization → geometry → estimation → combinatorics → games.

---

### 1. Notation and prerequisites

- **Objective:** A mathematically mature reader can *map* every ISO math symbol (Nc, C, Pn, Pg, Pe, K, H, wc, wr, R) to its code counterpart or NOT-FOUND status, and *state* the GF(2), permutation, graph and information-theoretic conventions the book uses.
- **Content:** symbol table (spec ↔ code ↔ this book); GF(2) linear algebra refresher; permutations as bijections; graphs and traversals; bits, entropy, channel intuition. Fully derived here, faded later.
- **Anchors:** ISO clauses 1–3 symbol list; corpus §5 glossary (`Pg` jabcode.h:162; `Pn`/`Pe` spec-only); `encoder.h:226-241`.

### 2. Information density and the capacity ledger

- **Objective:** The reader can *derive* the net capacity of any symbol from first principles — side-version geometry, fixed-pattern overhead, palette and metadata reservations, ECC rate — and *evaluate* the open Annex D reconciliation item as a worked audit.
- **Content:** density = log2(colour count) bits/module; side = 4v+17 geometry; the overhead ledger (finders, APs by `jab_ap_num`, palettes, metadata reservations); rate R = Pn/Pg; reproduce Table 1 rows from the ledger; **case study (Evaluate):** the JC-T open item — ISO Annex D's Pg = 1071/K = 476 vs `getSymbolCapacity` arithmetic (and the spec's own internal D.3-vs-D.4 inconsistency) — walk the audit, state what would resolve it.
- **Anchors:** `getSymbolCapacity` encoder.c:651; `VERSION2SIZE` jabcode.h:53; `jab_ap_num` encoder.h:285-292; ISO Table 1, 4.3.5, Annex D; JC-T ch. 4 findings.

### 3. LDPC coding theory

- **Objective:** The reader can *derive* the Gallager LDPC construction as implemented — seeded-permutation matrix stacking, Gauss-Jordan systematization, G = (I|C) — *analyze* hard-decision bit-flipping vs soft-decision message passing, and *evaluate* the implementation's choice to hard-decode both metadata and message data against Annex B's assignment.
- **Content:** parity-check codes on sparse bipartite graphs; the seeded construction (why determinism = interop); rate R = 1 − wc/wr and the ten (wc, wr) levels; dmin and correction radius; bit-flipping (full derivation) vs tanh-domain LLR message passing (sketch — second occurrence of iterative decoding); L = 25; Table 20 (encode-time recovery, ≥95 percent) vs Table 29 (verification capacity) — two different questions; **evaluation:** the caller-less soft path (JC-T ch. 6) — what is lost, when it would matter, what reviving it would cost; Annex C's metadata matrix as a constrained special case.
- **Anchors:** ldpc.c:172, 235, 645, 906, 1376; ldpc.h:17-18 seeds; encoder.h:234; ISO 5.4, Annexes B, C; Tables 20, 29.

### 4. The metadata bootstrap: a three-colour robust code

- **Objective:** The reader can *explain* why Part I's Nc field is decodable before the palette is known — the black/cyan/yellow fixed encoding as a code over a known sub-alphabet — and *analyze* its error behavior as a miniature case study in bootstrap design.
- **Content:** the chicken-and-egg problem (colour count needed to read colours); Table 7's construction; `nc_color_encode_table` as its realization; the Part I → palettes → Part II → data dependency chain as a trust ladder; the fork's Path β permissive substitution (magenta→yellow) as a robustness patch and what it trades.
- **Anchors:** encoder.h:124; decoder.c:1262; jabcode.h:93-98; ISO 4.4, Table 7 (spec pull), decode step e) 6.1.

### 5. Interleaving, burst errors, and the determinism contract

- **Objective:** The reader can *prove* the interleave is a uniform-choice Fisher-Yates permutation given the PRNG, *explain* burst-error decorrelation as the design goal, and *evaluate* the Annex F divergence — what "the same PRNG" must mean for a wire format, and what `pn_index` does and does not preserve.
- **Content:** Fisher-Yates correctness (full derivation — first permutation argument); burst errors vs LDPC's iid-ish error assumption; determinism as a distributed contract (seeds 226759/785465/38545); **the divergence analysis** (JC-T ch. 8, restated at theory depth): reference-ecosystem LCG64+tempering vs Annex F's C89 `rand()` — sequence inequality, the "PRNG is NOT an axis" position, the open interop question against literal-Annex-F implementations; `pn_index` as a floating-point-UB quarantine.
- **Anchors:** interleave.c:20-77; pseudo_random.c:10-30; encoder.h:23-24; ISO Annex F; JC-T ch. 8.

### 6. Masking as combinatorial optimization

- **Objective:** The reader can *formulate* mask selection as minimization over an 8-point design space with the Table 23 penalty functional, *explain* the XOR-group action of the generators on colour indices, and *evaluate* the two implementation divergences (rule-1 cross-only counting; rule-3 exactly-5 scoring) for their equilibrium effect on mask choice.
- **Content:** the generator family (Table 22) as translations/modular patterns over Z_(2^(Nc+1)); XOR application as group action; why finder-lookalike suppression dominates (W1 = 100 vs 3); joint scoring across cascades as a single objective; why 8 patterns suffice (design-space argument, sketched); divergences as perturbations of the argmin — mask-choice-only, wire-safe, and why.
- **Anchors:** mask.c:22-24, 363; ISO 5.8, Tables 22-23; JC-T ch. 7 divergence register.

### 7. Mode selection as shortest path

- **Objective:** The reader can *formulate* encoding-mode selection as a shortest-path problem on a trellis (states = modes, arcs = per-character costs plus latch/shift costs from Tables E.1–E.3), *derive* the dynamic-programming solution, and *analyze* the implementation's data-analysis pass against it.
- **Content:** the seven-mode cost structure; trellis construction; Bellman recursion and optimality (full derivation — first DP occurrence); worked example on the Annex D token stream ("JAB Code 2016!" → 78 bits); the source's analysis pass compared to the trellis ideal (read `analyzeInputData`/`encodeData` region) — optimal, greedy, or heuristic, stated from source.
- **Anchors:** encoder.c:723 region; encoder.h:129-213 (mode tables); ISO 5.3, Annex E, Tables E.1–E.3 (spec pull), Annex D token values.

### 8. Colour-space geometry

- **Objective:** The reader can *analyze* palette design as vertex/lattice selection in the RGB cube maximizing minimum pairwise distance, *derive* nearest-neighbour classification regions (L2 vs the fork's CIELAB ΔE), and *evaluate* the extended-mode interpolation scheme (Annex G.3 R-channel tiers; embedded palettes capped at 64) for its tolerance-window consequences.
- **Content:** the 8-colour palette as cube vertices; distance-to-tolerance link (Clause 8 CPA/CVDM normalization halves the inter-colour gap); 16→256-mode construction and palette interpolation; Euclidean vs perceptual metrics — CIELAB, ΔE, and the k-d tree as sublinear nearest-neighbour (fork machinery, gate-disabled: analyze as designed, note dormancy); density-tolerance frontier: log2(Nc) bits vs shrinking Voronoi cells.
- **Anchors:** encoder.h:26-34, 67-75; decoder.h:36-45; lab_color.c/h; kdtree_color.c/h; ISO Annex G (incl. G.3 spec pull), Clause 8 colour parameters; JC-T ch. 10 gate statuses.

### 9. Detection as robust estimation

- **Objective:** The reader can *model* finder detection as hypothesis testing over scanline profiles with tolerance bands, *explain* found-counters as vote aggregation and missing-finder inference as constraint propagation, and *derive* the homography estimation and the Table 24 side-size snap as quantization to the 4v+17 lattice.
- **Content:** p:1:1:1:q profile matching with 50-percent-class tolerances; voting and its false-positive/false-negative trade; three-of-four finder recovery as solving for the fourth vertex; perspective transform from point correspondences (derivation sketch — second geometry occurrence); Formula (5)'s floor(+7.5) and the snap rules as lattice quantization with tie-flags; failure-probability reasoning per stage (qualitative, anchored to the JC-T failure-to-stage table).
- **Anchors:** detector.c:1811 (`findMasterSymbol`), 3296, 3682; transform.c:202; ISO Clause 6, Table 24, Formulas (5)-(8); JC-T ch. 5.

### 10. Cascade combinatorics and traversal

- **Objective:** The reader can *characterize* the 61-position docking lattice and its decode order as BFS with a visited set, *prove* termination and completeness of the layered traversal, and *analyze* the primary/secondary efficiency asymmetry and the implicit-vs-unbounded ceiling (Figure 14 indexes 60; the metadata recursion itself is unbounded).
- **Content:** `jab_symbol_pos` as a lattice spiral; docking constraints (shared side-version on the docked side) as a matching/compatibility relation; decode order top-bottom-left-right with skip-if-visited — BFS equivalence proof (exercise-grade, third traversal occurrence); overhead asymmetry quantified (secondary symbols carry no finders, shorter metadata — Annex A.3); the 5 px/module resolution bound as the physical cap on useful cascade depth.
- **Anchors:** encoder.h:111-119; encoder.c:1598 (`assignDockedSymbols`); ISO 4.5, Figures 11–15, Annex A.3.

### 11. The adversarial channel

- **Objective:** The reader can *construct* an attacker–defender model of symbol damage — defender allocates redundancy (ECC level, masking, interleaving, cascade layout), attacker allocates damage under a cost budget — *analyze* where LDPC, interleaving and masking each move the equilibrium, and *evaluate* ECC-level choice as a resource-allocation decision under both random and adversarial damage.
- **Content:** random channel vs adversarial channel (why Table 20's ≥95-percent framing is a random-damage statement); interleaving as forcing the attacker from cheap bursts to expensive scatter; W1's finder protection as denying the cheapest detection-kill; fixed-pattern damage as the soft underbelly (Clause 8's FPD grading as evidence); a simple two-player resource game (defender: rate R; attacker: fraction p of modules) solved for illustrative parameters; **scope honesty:** the game-theoretic framing is this book's analytical synthesis — every code/spec fact is anchored, the models are labeled as original framing, and the crypto-layer game (cloning economics, key extraction) is explicitly deferred to the framework's Special Topics book (AF-S, forthcoming).
- **Anchors:** ISO Table 20 qualifier, Clause 8.2.4, 5.5, 5.8; mask.c:22; interleave.c; JC-U ch. 2/5 for operator-level restatements.

---

## Stage 3 notes (for drafting, after approval)

- **Template C** per chapter (Where it lives → The problem → Theory → Back to the code → Exercises → Further reading); faded scaffolding tracked across the book (first/second/nth occurrence schedule noted above per derivation family: iterative decoding, permutation arguments, geometry, traversal).
- **Math:** LaTeX in fenced math blocks; prose escaping per house rules. Export note for Stage 5: pandoc → MathML/WeasyPrint rendering must be verified on a sample chapter before the full build.
- **Spec pulls required (one ISO batch):** Annex E complete (trellis algorithm, Tables E.1–E.3); Annex G.3 mechanics (64-colour palette cap, 128/256 interpolation, R-channel tiers); Table 7 (Part I three-colour encoding values); 4.3.5 data-module counting context; Table 29 values reconfirmation; Clause 8 CPA/CVDM normalization sentences (dR/dG/dB halves).
- **Verified inputs from JC-T** (do not re-derive, cite): dead soft decoders; PRNG divergence framing; mask rule divergences; Annex D open item; gate statuses; Nc = 7 ladder omission (ch. 8 material if relevant).
- **Exercises:** 2–4 per chapter, faded (guided → open); solutions or hints in `<details>` blocks.
- **Original-synthesis discipline (ch. 11 especially):** analytical models beyond code/spec are explicitly labeled as this book's framing; no invented empirical claims; illustrative game solutions use stated toy parameters only.
- **Drafting order:** 1 → 2 → 3 → 5 → 4 → 6 → 7 → 8 → 9 → 10 → 11 (derivation-dependency order; ch. 11 last, consuming all prior machinery).
- **Cross-book:** cite JC-U/JC-T by relative path; AF-S "forthcoming".
