# jabcode Operator's Manual (JC-U)

An operator's guide to the JAB Code reference implementation — what a polychrome symbol is, how to encode and decode with the CLI tools and C API, how to choose parameters, and how the codec is operated inside the jab-auth service.

**Voice:** Mentor — informative and explanatory prose. **Audience:** operators and integrators; SDK chapters assume basic developer literacy, service chapters none.
**Corpus:** `swift-java-poc` fork @ `8f76559` (see [../corpus-model.md](../corpus-model.md)) and ISO/IEC 23634:2022. **Generated:** 2026-07-15 by the manual-forge pipeline; verified — see [verification-report.md](verification-report.md) (pass; C/T/H by part: I 9/10/9, II 10/9/10, III 9/10/9, appendices 8/9/9).

## Part I — Shared Concepts

1. [What a JAB Code is](01-what-a-jab-code-is.md) — colour as the third dimension; symbol anatomy; primary vs secondary.
2. [Capacity, size and robustness](02-capacity-size-robustness.md) — side-versions, the ten error-correction levels, real capacity numbers.
3. [Cascading: one message, many symbols](03-cascading.md) — the 61-position map, docking rules, decode order, the five-pixels-per-module rule.
4. [Colour modes and conformance](04-colour-modes-conformance.md) — which of the eight colour counts are ISO-standard, and what the rest mean for interchange.
5. [Printing and scanning well](05-printing-and-scanning.md) — the standard's own operational guidance; what quality grades mean.

## Part II — SDK Track

6. [Building the library and tools](06-building-the-library.md) — make targets, system-library prerequisites, the Windows variant.
7. [Encoding with jabcodeWriter](07-encoding-with-jabcodewriter.md) — the full flag surface with worked examples.
8. [Decoding with jabcodeReader](08-decoding-with-jabcodereader.md) — exit-code contract and the diagnostics hiding inside it.
9. [Embedding the C API](09-embedding-the-c-api.md) — the five-call round trip, memory ownership, and the success-convention trap.
10. [Choosing parameters well](10-choosing-parameters.md) — the four dials, with the standard's selection criteria.

## Part III — Service Track

11. [How the service reaches this library](11-service-binding-chain.md) — REST → Panama FFM → native codec; provenance validation; the stub-fallback behavior.
12. [Service configuration vs SDK configuration](12-service-vs-sdk-configuration.md) — field-by-field knob reachability across the three surfaces.

## Appendices

- [A. Troubleshooting](appendix-a-troubleshooting.md) — symptom → cause → fix, from documented behavior only.
- [B. Representative commands](appendix-b-samples-cross-index.md) — output classes and the commands that produce them (re-scoped: the sample gallery assets are not present in this working tree).
- [C. Quick-reference card](appendix-c-quick-reference.md) — flags, defaults, exit codes, ECC and capacity tables.

## Conventions and known gaps

- Claims carry source anchors as HTML comments (`<!-- anchor: file:line -->` or `<!-- anchor: ISO 23634 ... -->`); they are invisible when rendered and deliberately retained in the Markdown source for incremental regeneration and audit.
- Worked examples are constructed from the documented option surface and source behavior; they were not executed in the authoring session (noted in-chapter where relevant).
- Known gap (tracked): chapter 1's symbol-anatomy figures are deferred — prose descriptions stand in until accurate diagrams are produced.
- The Developer's Manual (JC-T) and Special Topics (JC-S) referenced by "deeper" pointers are forthcoming; see [plan/index.md](../plan/index.md).
