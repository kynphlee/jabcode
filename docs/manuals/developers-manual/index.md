# jabcode Developer's Manual (JC-T)

The maintainer's reference for the JAB Code implementation on the `swift-java-poc` fork — build system, complete public contract, module-by-module internals mirroring the source tree, and the maintenance, performance and conformance surface.

**Voice:** Expert — dense, precise, extractive; task-level tutorials live in the [Operator's Manual](../operators-manual/index.md), theory in Special Topics (JC-S, forthcoming).
**Audience:** maintainers, performance engineers, binding authors. **Corpus:** fork @ `8f76559` (see [../corpus-model.md](../corpus-model.md)) and ISO/IEC 23634:2022.
**Generated:** 2026-07-15 by the manual-forge pipeline; verified — see [verification-report.md](verification-report.md) (conditional pass resolved; C/T/H by part: I 9/9/10, II 9/9/9, III 9/9/9; ~120 anchors spot-verified, all interop-critical constants matched verbatim).

## Part I — Architecture

1. [Repository and build architecture](01-repository-and-build.md) — build units, the full target table, working-tree caveats, and the dual-clone operations warning.
2. [The codec pipeline](02-codec-pipeline.md) — encode and decode stage maps (file → function → ISO clause), the dependency graph, and the fork-extension overlay.

## Part II — Module Reference (mirrors `src/`)

3. [The public surface: `jabcode.h`](03-public-surface-jabcode-h.md) — every macro, struct, function and global, with ownership/threading contracts and the `generateJABCode` 0-on-success inversion.
4. [`encoder.c`](04-encoder.md) — capacity and ECC selection, mode encoding, metadata assembly, placement, cascade assignment; Annex D as the regression vector.
5. [`detector.c` and `decoder.c`](05-detector-and-decoder.md) — the full detection-to-decode path, Table 24 side-version snap, the Nc fallback ladder, the synthetic bypass, failure-to-stage table.
6. [`ldpc.c`](06-ldpc.md) — seeded matrix construction, Gauss-Jordan, hard-decision decoding, and the caller-less soft-decision paths.
7. [`mask.c`](07-mask.md) — the eight generators vs ISO Table 22, penalty scoring, joint cross-symbol evaluation, divergence register.
8. [`interleave.c` and `pseudo_random.c`](08-interleave-and-prng.md) — the exact PRNG contract, the Annex F divergence analysis, and what `pn_index` preserves.
9. [Capture support: `binarizer.c`, `transform.c`, `sample.c`, `image.c`](09-capture-support.md) — live vs dormant binarizer entries, homography, sampling, the full image I/O surface.
10. [Fork extensions](10-fork-extensions.md) — adaptive palettes (dormant), colour calibration, LAB and k-d tree machinery, decode profiling, Mode 0, per-extension interop verdicts.
11. [CLI internals](11-cli-internals.md) — flag-to-field mapping, validation order, every exit path.

## Part III — Maintenance, Performance and Conformance

12. [Benchmark estate](12-benchmark-estate.md) — every bench target, arguments, output formats, profiling scripts.
13. [Regression suite](13-regression-suite.md) — the seven wired gates, the unwired test inventory, CI coverage gaps, extension pattern.
14. [Concurrency](14-concurrency.md) — the `_Thread_local` PRNG posture, TSan guard, and the downstream `jna.protected` livelock lesson.
15. [Conformance testing](15-conformance-testing.md) — Annex H and Clause 7 implementation vs the absent Clause 8 verifier, scoped as a roadmap item.
16. [Extended colour modes](16-extended-colour-modes.md) — reserved-Nc implementation map, the ladder's Nc = 7 omission, interchange consequences.
17. [Downstream bindings](17-downstream-bindings.md) — the ABI guard discipline, wrapper externs, provenance validation, and the pixel-vs-module configuration tension.

## Findings register (what this book established about the tree)

Documented with anchors in the chapters cited: soft-decision LDPC decoders have no callers — both metadata and message data hard-decode (ch. 6); named binarizer variants are pipeline-dormant, `balanceRGB`/`binarizerRGB` are the live entries (ch. 9); detection modes are pinned to `INTENSIVE_DETECT` (ch. 5); the Nc fallback ladder omits mode 7 (ch. 5, 16); `generateJABCode` returns 0 on success (ch. 3); mask rule 1 counts cross-only lookalikes and rule 3 scores exactly-5 runs against Table 23's letter (ch. 7); the PRNG is the reference-ecosystem LCG64, with Annex F's C89 `rand()` treated as informative — an open interop question against literal-Annex-F implementations (ch. 8); Annex D's worked example does not reconcile with `getSymbolCapacity` arithmetic — open verification item, corroborated by the spec's own internal inconsistency (ch. 4); the ECI decode path is implemented while `case FNC1:` remains a TODO (ch. 15); `USE_LAB_DISTANCE`/`USE_FP_CALIBRATION` gates are defined by no build file (ch. 2, 10). Registered from the JC-S verification (2026-07-15): the 4-colour palette ordering diverges from spec — ISO Table 21 orders K, C, M, Y while `setDefaultPalette` builds K, M, Y, C (encoder.c:104-110), the code's ordering being what makes the Part I `{0,3,6}` mod-4 reduction land on black/cyan/yellow (JC-S ch. 4); the encoder's trellis cost table diverges from Annex E Tables E.2/E.3 in six cells, including one dead token (JC-S ch. 7); Table 1's 4-colour column is uniformly 11 modules below the capacity-ledger arithmetic (JC-S ch. 2).

## Conventions

Source anchors ride as HTML comments (`<!-- anchor: file:line -->`, `<!-- anchor: ISO 23634 clause -->`), invisible when rendered, retained for audit and incremental regeneration. All content was verified against the direct file view of this folder — the session shell exposes a different clone (ch. 1 §1.8); never trust shell git output here without confirming the clone.
