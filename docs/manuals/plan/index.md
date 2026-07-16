# jabcode Manuals Plan — Index

Per-repo plan for the three jabcode manual corpora. The program-wide architecture, ISO/IEC 23634 reference map, pedagogy research, `manual-forge` skill spec and production workflow live in the **master plan** in the jab-auth-framework repo: `JABCodeCOA-crypto/docs/manuals/plan/`.

**Date:** 2026-07-15

## The Three Books

| ID | Book | Audience | Voice |
|---|---|---|---|
| JC-U | Operator's Manual | CLI operators, integrators choosing parameters | Mentor — informative, explanatory prose |
| JC-T | Developer's Manual | Maintainers, performance engineers | Expert — technical prose |
| JC-S | Special Topics | Readers with mathematical maturity | Expert with faded scaffolding |

Structure per book: Part I shared concepts, Part II SDK track (C library, CLI, bindings), Part III service track (the codec as operated inside the jab-auth SaaS). Full tables of contents: [corpus-outlines.md](corpus-outlines.md).

## Corpus Sources

**Authoritative inventory: [../corpus-model.md](../corpus-model.md) (Stage 0, built 2026-07-15).** Note: this mounted working tree is the `swift-java-poc` fork (LGPL-2.1), not upstream master — see Repo-Specific Constraints below.

- `src/jabcode/` — core C library, 17 library source files: the upstream pipeline (`encoder.c`, `decoder.c`, `detector.c`, `ldpc.c`, `mask.c`, `interleave.c`, `sample.c`, `transform.c`, `binarizer.c`, `image.c`) plus the fork extensions (`adaptive_palette.c`, `color_calibration.c`, `lab_color.c`, `kdtree_color.c`, `pseudo_random.c`, `decode_profile`/`detector_synthetic` diagnostics); public header `include/jabcode.h`; `test/` suite (25 files) and `scripts/` regression harnesses.
- `src/jabcodeWriter/`, `src/jabcodeReader/` — CLI tools and their option surfaces.
- Makefiles (Unix + `Makefile.win`), including the benchmark and regression targets (`bench`, `bench-concurrent`, `bench-cascade`, `profile`, `test-pn`, `test-symid`, `test-eci`, `test-table15`, `test-roundtrip`, `test-concurrent`).
- `jabcode-samples/` gallery and the two reference PDFs (samples gallery, supported-variants brief).
- ISO/IEC 23634:2022 — via the clause map and sub-agent protocol in the master plan (`03-iso-23634-reference-map.md` there). The spec copy lives in the framework repo.
- Existing Doxygen HTML under `docs/` is stale API reference; manuals do not duplicate it — JC-T links to regenerated Doxygen for signature-level detail.

## Placement

Books land at `docs/manuals/operators-manual/`, `docs/manuals/developers-manual/`, `docs/manuals/special-topics/`, each with its own `index.md`, `images/`, and chapters as numbered kebab-case files. PDF exports go to `docs/manuals/dist/` (gitignored).

## Repo-Specific Constraints

- **Two clones, one folder (verified 2026-07-15):** the session shell and the direct file view of this folder expose *different* clones. Shell: upstream `master` @ `3b56eef` (jabcode 2.0.0, MIT). Direct file view (authoritative — what the user's folder actually contains): `swift-java-poc` fork @ `8f76559`, LGPL-2.1, with the extended palette/calibration modules and test suite. **All manuals are built from the direct file view.** Verify which clone a git command is talking to before trusting its output.
- Manuals work uses a dedicated worktree and branch (`docs/manuals-plan`, then `docs/manuals-<book-id>`); MRs for tracking; never commit to the mainline directly.
- Only 4- and 8-colour modes are ISO-conformant; this implementation's 2/16/32/64/128/256-colour modes are reserved-Nc extensions (2-colour has no footing even in ISO Annex G). Every book states this precisely.
- Licensing: this fork tree carries LGPL-2.1 (upstream relicensed MIT in 2026-04 — applies to the upstream clone, not this tree). Manuals describe the fork as found.
