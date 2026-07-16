# jabcode — Manual Outlines (JC-U, JC-T, JC-S)

Corpus sources per chapter in parentheses; ISO references use the clause map in the master plan (`JABCodeCOA-crypto/docs/manuals/plan/03-iso-23634-reference-map.md`).

---

## JC-U — Operator's Manual

### Part I — Shared Concepts

1. **What a JAB Code is.** Colour as the third dimension: density = log2(colour count) bits per module; how a polychrome symbol differs from QR. Symbol anatomy in pictures — finder patterns, alignment patterns, palettes, primary vs secondary symbols. (ISO 4.1–4.3, Figures 1–4; samples gallery)
2. **Capacity, size and robustness.** Side-versions 1–32 (21–145 modules); what error-correction levels 1–10 mean in plain language (default level 3 ≈ 6 percent bit recovery); the capacity headline — a single maximal square symbol holds ≈ 7.6 kB at default EC. (ISO Table 1, Table 20)
3. **Cascading.** Why and when to split a message across up to 61 docked symbols; shape flexibility; the 5-pixels-per-module scanning rule. (ISO 4.5, Annex A.3)
4. **Colour modes and conformance.** 4- and 8-colour are the ISO-standard modes; this implementation's 2/16/32/64/128/256 modes are extensions for closed-loop use — what that means for interchange. (ISO 4.4.1.2, Annex G)
5. **Print and scan guidance.** The standard's own operator advice: choosing colour count and EC level, print technology considerations (CMYK-friendly 4-colour set), lighting and damage tolerance, what quality grades mean. (ISO Annex A, Clause 8 at grade-meaning level)

### Part II — SDK Track

6. **Building the library.** Unix make, Windows MinGW variant, artifacts (`libjabcode.a`/`.so`), vendored dependencies (libpng, libtiff, zlib), the `check-lib` symbol-freshness guard. (Makefiles, README)
7. **Encoding with the CLI.** `jabcodeWriter` walkthrough: `--color-number`, `--symbol-number`, `--ecc-level`, `--module-size`, `--symbol-version`, `--symbol-position`, `--color-space`; worked examples reproducing entries from the samples gallery.
8. **Decoding with the CLI.** `jabcodeReader` usage; interpreting output and failures.
9. **Embedding the C API.** The five-call tour: `createEncode` → `generateJABCode` → `saveImage`; `readImage` → `decodeJABCode`/`decodeJABCodeEx`; memory ownership rules; a complete minimal program. (`include/jabcode.h`)
10. **Choosing parameters well.** Decision guide mapping use cases to colour mode, ECC level, symbol count, module size; recipes for print vs screen.

### Part III — Service Track (the codec inside the jab-auth SaaS)

11. **The binding chain.** How the SaaS reaches this library: Panama FFM wrapper → `libjabcode.so` with build-time provenance validation; where codec parameters surface in the service API (`/api/jabcode/generate`).
12. **Service-side configuration mapping.** `JabCodeConfig` fields vs the C API; current limitations of the service path (symbol width/height not yet wired; cascade exposure) so operators know what is reachable via the API vs the SDK.

### Appendices

A. Troubleshooting scans (symptom → cause → fix). B. Samples gallery cross-index. C. Quick-reference card: CLI flags, defaults, capacity table extract.

---

## JC-T — Developer's Manual

### Part I — Architecture

1. **Repository and build architecture.** Source layout, the three build units (core lib, writer, reader), Makefile targets including the benchmark/regression estate, Windows build differences, vendored library policy.
2. **The codec pipeline.** Encode: data analysis → mode encoding → LDPC → interleave → placement → masking → metadata (ISO Clause 5 order, mapped file-by-file). Decode: binarization → finder detection → perspective transform → sampling → palette reconstruction → classification → unmask → de-interleave → LDPC decode → mode decode → secondary recursion (ISO Clause 6 mapped to `detector.c`/`decoder.c`/`sample.c`).

### Part II — Module Reference (extractive; TOC mirrors the source)

3. **`jabcode.h` public surface.** Every public function, struct (`jab_encode`, `jab_metadata`, `jab_decoded_symbol`, `jab_bitmap`, `jab_data`), constant and error status; ownership and threading contracts.
4. **`encoder.c`.** Symbol version selection, metadata assembly (26→44-bit ECC path), data placement order, palette embedding; ISO 4.4/5.9 cross-refs.
5. **`decoder.c` and `detector.c`.** Colour classification thresholds, finder search (p:1:1:1:q profiles, found-counter logic, missing-finder inference), side-size snap (ISO Table 24), per-sub-block perspective sampling, palette normalization tables.
6. **`ldpc.c`.** Matrix construction from seeds (message 785465, metadata 38545), wc/wr per ECC level, hard-decision vs soft-decision paths, iteration cap; the Annex C normative metadata matrix; regression vector from ISO Annex D (Pg = 1071, K = 476).
7. **`mask.c`.** Eight generators (ISO Table 22), penalty scoring (W1 = 100, W2 = 3, W3 = 3), joint evaluation across cascaded symbols.
8. **`interleave.c`.** Seed 226759, the C89 LCG PRNG (interop-critical: next·1103515245 + 12345), in-place permutation; why any deviation breaks cross-implementation decode. (ISO Annex F)
9. **`sample.c`, `transform.c`, `binarizer.c`, `image.c`.** Homography math, histogram thresholding, PNG/TIFF I/O, CMYK output path.
10. **Fork extensions: adaptive palettes and colour calibration.** `adaptive_palette.c`, `color_calibration.c`, `lab_color.c` (CIELAB distance), `kdtree_color.c` (nearest-neighbour search), `pseudo_random.c` (`_Thread_local` PRNG state for concurrent encode/decode), `decode_profile`/`detector_synthetic` diagnostics — what the fork adds over upstream, why, and interop consequences vs the ISO reference decoder.
11. **CLI internals.** `jabwriter.c` option parsing and validation; `jabreader.c`.

### Part III — Maintenance, Performance and Conformance

12. **Benchmark estate.** `bench`, `bench-concurrent`, `bench-cascade`, `profile` targets; the detector sub-stage profiling hooks (`detect_profile.h`); methodology and current baselines.
13. **Regression suite.** `test-pn`, `test-symid`, `test-eci`, `test-table15`, `test-roundtrip`, `test-concurrent`, plus the fork's `test/` suite and `scripts/` regression harnesses — what each guards and how to extend.
14. **Concurrency notes.** `_Thread_local` PRNG state (`pseudo_random.c`); behavior under multithreaded consumers; the downstream JNA/Panama binding pitfalls (including the `-Djna.protected=true` livelock observed in framework benchmarks — documented here because the root cause is boundary behavior of this library).
15. **Conformance testing.** ISO Clause 8 grading (CPA and CVDM colour parameters, Table 29 UEC capacities) — noting the corpus-model finding that Clause 8 grading is currently unimplemented in this tree (a documented gap, candidate roadmap item); the `]jm` symbology identifier requirement (Annex H); the Table 20/21 editorial trap.
16. **Extended colour modes.** Implementation of reserved-Nc modes beyond the standard (palette truncation at 64, interpolation for 128/256 per Annex G.3); interop consequences; the 2-colour mode as pure extension.
17. **Downstream bindings.** The Panama wrapper contract, provenance validation (ELF/SHA-256), symbol-cascade capabilities exposed vs consumed; framework-side config mapping (the historical `symbolWidth`/`symbolHeight` gap, since reconciled — see the framework corpus model).

---

## JC-S — Special Topics: Math, Logic and Game Theory

1. **Notation.** GF(2) linear algebra, permutations, graphs, basic information theory.
2. **Information density of polychrome symbols.** log2(Nc) bits per module; capacity derivation from side-version geometry (ISO 4.3 formulas); overhead accounting (finders, palettes, metadata); the density-vs-tolerance trade as colour count grows (Clause 8 colour metrics shrink proportionally).
3. **LDPC coding theory.** Gallager codes; parity-check matrix construction as seeded permutation stacking; Gauss-Jordan to systematic form; rate R = 1 − wc/wr; hard-decision bit-flipping vs soft-decision LLR message passing (Annex B); why levels 1–10 sit where they do (Tables 20, 29).
4. **The metadata bootstrap code.** The 3-colour Part I Nc encoding (Table 7) as a miniature robust-coding case study: decodable before the palette is known.
5. **Interleaving and burst errors.** The LCG-driven in-place permutation as decorrelation; determinism as an interop contract; permutation-theory view. (Annex F)
6. **Masking as combinatorial optimization.** The XOR group structure over colour indices; penalty function design (finder-lookalike suppression, block and run penalties); joint optimization across cascades; why eight patterns suffice.
7. **Mode selection as shortest path.** The Annex E trellis: states, per-character costs, switch/latch costs (Tables E.1–E.3); dynamic programming formulation and optimality.
8. **Colour-space geometry.** RGB cube vertex selection; maximizing pairwise colour distance; palette interpolation for extended modes; L2 nearest-neighbour classification and normalization (Clause 6, Annex G).
9. **Detection as robust estimation.** Scanline profile matching with tolerance; found-counters as voting; inferring a missing finder from three; homography estimation; failure-probability reasoning.
10. **Cascade combinatorics and traversal.** The 61-position lattice (Figure 14); decode order as BFS with a visited set; docking constraints as a matching problem; efficiency asymmetry between primary and secondary symbols (Annex A.3 quantified).
11. **The adversarial channel.** Game-theoretic framing: damage (accidental or adversarial) vs ECC level as a defender resource-allocation problem; attacker cost of forcing misreads vs defender cost of redundancy; where masking, interleaving and LDPC each move the equilibrium; connection to the anti-counterfeiting game in the framework's Special Topics book (AF-S owns the crypto layers; this book owns the channel).
