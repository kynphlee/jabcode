# jabcode Special Topics: Math, Logic and Game Theory (JC-S)

The theory book of the jabcode manual corpus — pure explanation, math-heavy, for readers with mathematical maturity (linear algebra, discrete probability, basic combinatorics assumed after chapter 1). Every chapter anchors its theory to where it lives in the source and in ISO/IEC 23634:2022 before deriving anything.

**Voice:** Expert with faded scaffolding — full derivation on first occurrence, sketched proof on second, statement-plus-exercise thereafter. **Bloom ceiling:** Analyze → Evaluate → Create.
**Corpus:** fork @ `8f76559` (see [../corpus-model.md](../corpus-model.md)) and ISO/IEC 23634:2022; the [JC-T findings register](../developers-manual/index.md) is consumed as verified input.
**Generated:** 2026-07-16 by the manual-forge pipeline; verified — see [verification-report.md](verification-report.md) (conditional pass, all dispositions applied; C/T/H by cluster: ch. 1–3 9/9/9, ch. 4–7 9/9/9, ch. 8–11 9/9/8 after fixes; every worked computation independently re-derived, including a compiled-C re-simulation of the PRNG chain).

## Chapters

1. [Notation and prerequisites](01-notation.md) — the spec ↔ code ↔ book symbol table with honest NOT-FOUND gaps; GF(2) linear algebra, permutations, graphs, entropy — the book's single point of full setup.
2. [Information density and the capacity ledger](02-information-density.md) — the 4v+17 geometry, the overhead ledger reproducing Table 1 bit-for-bit, the rate toll, and the Annex D reconciliation worked as an audit.
3. [LDPC coding theory](03-ldpc-coding-theory.md) — the seeded Gallager construction as implemented, bit-flipping derived in full, soft decoding sketched, Table 20 vs Table 29, and the caller-less soft path evaluated.
4. [The metadata bootstrap: a three-colour robust code](04-metadata-bootstrap.md) — why Nc is decodable before the palette is known; Table 7 as a code over a fixed sub-alphabet; the trust ladder; Path β and Mode 0 as boundary cases.
5. [Interleaving, burst errors, and the determinism contract](05-interleaving-and-determinism.md) — Fisher-Yates uniformity proved in full; what determinism must mean on a wire; the Annex F divergence at theory depth; what `pn_index` preserves.
6. [Masking as combinatorial optimization](06-masking-optimization.md) — the 8-point design space, the XOR group action, the W1-dominated penalty functional, joint cascade scoring, and the two divergences as argmin perturbations.
7. [Mode selection as shortest path](07-mode-selection-shortest-path.md) — the 14-state trellis, the Bellman recursion derived in full, the Annex D prefix worked by hand, and the six cost-table divergences from Tables E.2/E.3.
8. [Colour-space geometry](08-colour-space-geometry.md) — cube vertices, lattice refinements, the density-tolerance staircase, half-gap normalization, embedded-palette compression, and the dormant perceptual machinery.
9. [Detection as robust estimation](09-detection-robust-estimation.md) — scanline hypothesis tests, vote aggregation, the three-of-four finder solve, the closed-form homography, and the Table 24 lattice snap.
10. [Cascade combinatorics and traversal](10-cascade-combinatorics.md) — the 61-position table as the L1 ball of radius 5 enumerated by BFS; docking as a compatibility relation; the ceiling and the economics.
11. [The adversarial channel](11-adversarial-channel.md) — the book's original game-theoretic synthesis (scope-noted): defender instruments, the fixed-pattern soft underbelly, and a toy resource game solved end to end.

## Findings register (what this book established)

Documented with anchors in the chapters cited, and mirrored into the [JC-T findings register](../developers-manual/index.md) where maintainer-relevant: the capacity ledger reproduces every extracted Table 1 8-colour row exactly, while the 4-colour column sits uniformly 11 modules below ledger arithmetic (ch. 2); the Annex D worked example is internally inconsistent and reconciles with source arithmetic only under a single-term palette-count hypothesis — open item, candidate not resolution (ch. 2); the spec's 4-colour palette order (Table 21: K, C, M, Y) diverges from the reference implementation's (K, M, Y, C), and the code's ordering is what makes the Part I mod-4 reduction land on black/cyan/yellow (ch. 4); Table 7's unused (yellow, yellow) pair acts as the bootstrap's tripwire (ch. 4); the encoder's trellis is a genuine Annex E dynamic program driven by a cost table that diverges from Tables E.2/E.3 in six cells, one of them a dead token costing 4 bits of optimality on isolated digits in lowercase text (ch. 7); the seed-226759 permutation at N = 4 is the identity (ch. 5); `Pn` does exist in code as a local, mislabeled by an adjacent comment (ch. 1–2); Table 20's printed R column follows no uniform rounding of 1 − wc/wr (ch. 3).

## Conventions

Source anchors ride as HTML comments (`<!-- anchor: file:line -->`, `<!-- anchor: ISO 23634 clause -->`). Display math uses `$$` fences. ISO decimal commas are reproduced as printed and tagged. Exercises are faded (guided → open) with solutions or hints in `<details>` folds. Cross-book references: operator-level restatements in [JC-U](../operators-manual/index.md), maintainer-level registers in [JC-T](../developers-manual/index.md); the authentication-layer game book (AF-S) is forthcoming.
