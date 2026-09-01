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

- **One clone, not two (corrected 2026-09-01; the 2026-07-15 note was wrong):** this folder is a single clone whose working tree sits on the `swift-java-poc` fork, plus worktrees. `origin/HEAD -> origin/master` is what a fresh clone or `git ls-remote` reports, which is the likely source of the earlier "two clones" reading — `git worktree list` and `git rev-parse --show-toplevel` settle it. **All manuals are built from the fork, which remains correct:** it is what ships, and its palette/calibration modules are load-bearing (11 of the 13 symbols the Panama bindings require are absent from upstream). Only the licensing conclusion drawn from the "two clones" story changed — see below.
- Manuals work uses a dedicated worktree and branch (`docs/manuals-plan`, then `docs/manuals-<book-id>`); MRs for tracking; never commit to the mainline directly.
- Only 4- and 8-colour modes are ISO-conformant; this implementation's 2/16/32/64/128/256-colour modes are reserved-Nc extensions (2-colour has no footing even in ISO Annex G). Every book states this precisely.
- Licensing (corrected 2026-09-01): this tree is **MIT**, not LGPL-2.1. The earlier note said the 2026-04 relicense "applies to the upstream clone, not this tree" — that reasoning was wrong. A licence is granted over a *work*, not over a clone, and upstream changed **nothing** under `src/` between the 2022 fork point (`ee0e4c8`) and the relicense (`3b56eef`): all three intervening commits touch only `LICENSE`, and `git diff ee0e4c8 upstream/master -- src/` returns empty. The code this fork inherited is the identical work Fraunhofer now offers under MIT, so the LGPL-2.1 text was a stale copy of a superseded offer. Root `LICENSE` now states MIT with both copyright holders. **Exception:** `jabauth-android/` is proprietary and expressly carved out — see `jabauth-android/LICENSE`. Manuals must state this, not the old LGPL claim.
