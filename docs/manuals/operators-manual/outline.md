# JC-U Operator's Manual — Stage 1 Outline (approved; book drafted and verified 2026-07-15)

**Post-drafting amendments:** Appendix B re-scoped to a representative-commands table — the `jabcode-samples/` gallery and the two reference PDFs are absent from this working tree (disposition D2, verification report). Chapter 1's anatomy figures deferred to a follow-up (minor, tracked in the book index).

**Book:** jabcode Operator's Manual · **Voice:** Mentor · **Bloom ceiling:** Remember → Understand → Apply
**Corpus:** `docs/manuals/corpus-model.md` (swift-java-poc fork @ `8f76559`, built 2026-07-15) · ISO values via sub-agent per the clause map (`JABCodeCOA-crypto/docs/manuals/plan/03-iso-23634-reference-map.md`)
**Structure:** Part I shared concepts · Part II SDK track · Part III service track · Appendices

Objective format: ABCD (Audience, Behavior with Bloom verb, Condition, Degree). Anchors cite the corpus model, which resolves to `file:line`.

---

## Part I — Shared Concepts (Diátaxis: explanation, operator-level)

### 1. What a JAB Code is

- **Objective:** An operator with no barcode background can *explain* what distinguishes a polychrome symbol from a monochrome 2D code and *identify* the finder patterns, alignment patterns, palette modules and primary vs secondary symbols on a printed sample, naming all four fixed-pattern roles correctly.
- **Content:** colour as the third dimension (density = log2(colour count) bits/module); symbol anatomy in pictures; primary vs secondary.
- **Anchors:** corpus §4 (polychrome symbology, module, palette, FP0–FP3, AP/APX, primary/secondary); `jabcode.h:24-45`; default palette `[K,B,G,C,R,M,Y,W]` `encoder.h:26-34`. Spec pulls: ISO 4.1–4.3 Figures 1–4.

### 2. Capacity, size and robustness

- **Objective:** An operator can *select* a side-version and ECC level for a given payload size and damage expectation, using the capacity and robustness tables, such that the payload fits with the required recovery margin.
- **Content:** side-versions 1–32, side = 4·version+17 (21–145 modules); ECC levels 1–10 in plain language (default 3, wc=4/wr=9); what "≈ 6 percent bit recovery" means; the ≈ 7.6 kB single-symbol headline.
- **Anchors:** `VERSION2SIZE` `jabcode.h:53`; `ecclevel2wcwr` `encoder.h:234`; `ecclevel2coderate` `encoder.h:226`; defaults `jabcode.h:31-36`. Spec pulls: ISO Table 1 (capacity rows), Table 20 (recovery percentages).

### 3. Cascading: one message, many symbols

- **Objective:** An operator can *decide* when to cascade and *assign* valid symbol positions and versions for a multi-symbol code, producing a correct position/version assignment for a three-symbol example (master at position 0, docked sides sharing side-version).
- **Content:** why cascade (shape flexibility, capacity); the 61-position map; docking rules; decode order; the 5-pixels-per-module scanning rule.
- **Anchors:** `MAX_SYMBOL_NUMBER 61` `jabcode.h:24`; `jab_symbol_pos[61]` `encoder.h:111-119`; writer rules `jabwriter.c:397-410`. Spec pulls: ISO 4.5, Figures 11–15, Annex A.3.

### 4. Colour modes and conformance

- **Objective:** An operator can *classify* every supported colour count (2, 4, 8, 16, 32, 64, 128, 256) as ISO-standard or implementation extension and *state* the interchange consequence of each choice.
- **Content:** Nc and colour count = 2^(Nc+1); 4- and 8-colour are the standard modes; 16–256 are reserved-Nc extensions; 2-colour (Mode 0) is fork-only, absent even from Annex G; closed-loop vs interchange.
- **Anchors:** `g_preferred_color_count` comment `jabcode.h:105`; Mode 0 `jabwriter.c:147-148`; corpus §4 Nc node. Spec pulls: ISO 4.4.1.2, Annex G.

### 5. Printing and scanning well

- **Objective:** An operator can *apply* the standard's operational guidance to choose colour count, EC level and module size for two given scenarios (CMYK print run; screen display scanned by phone) and *interpret* what a quality grade means when a verifier reports one.
- **Content:** Annex A guidance in Mentor prose; print technology notes (CMYK-friendly 4-colour); lighting/damage folklore grounded in the ECC chapter; grades at meaning-level only (grading is unimplemented in this tree — say so honestly, point to Developer's Manual ch. 15).
- **Anchors:** `saveImageCMYK` `jabcode.h:288`; `--color-space` `jabwriter.c:226-245`; corpus §6 NOT FOUND register (clause 8). Spec pulls: ISO Annex A, Clause 8 grade names only.

## Part II — SDK Track (Diátaxis: tutorial / how-to)

### 6. Building the library and tools

- **Objective:** A developer-literate operator can *build* `libjabcode` (static + shared) and both CLI tools from a clean checkout on Linux, and *name* the Windows variant, producing `build/libjabcode.so`, `bin/jabcodeWriter`, `bin/jabcodeReader`.
- **Content:** make targets; CFLAGS facts; the vendored-library caveat (prebuilt `src/jabcode/lib/` archives absent from this tree — linking falls to system libpng16/tiff/zlib, so those dev packages must be installed); `Makefile.win`/MinGW; what `refresh-lib`/`check-lib` are for and why they currently require creating the repo-root `lib/`.
- **Anchors:** `src/jabcode/Makefile:8,18,24-73`; `Makefile.win:5-13`; writer/reader link lines `Makefile:10` each; corpus §1.3, §2.1-2.2.

### 7. Encoding with jabcodeWriter

- **Objective:** An operator can *encode* messages using every writer flag, reproducing three worked examples — an 8-colour default PNG, a 4-colour CMYK TIFF, and a two-symbol cascade — with correct flag syntax on the first attempt.
- **Content:** full flag surface: `--input`, `--input-file`, `--output`, `--color-number` (2,4,8,…,256; default 8), `--module-size` (default 12), `--symbol-width`/`--symbol-height`, `--symbol-number` (1–61), `--ecc-level` (1–10, default 3; per-symbol lists, 0 = inherit), `--symbol-version`, `--symbol-position` (master = 0), `--color-space` (0 RGB/PNG, 1 CMYK/TIFF); exit codes 0/1.
- **Anchors:** corpus §3.4 (all flags with `jabwriter.c` lines); usage text `jabwriter.c:25-60`.

### 8. Decoding with jabcodeReader

- **Objective:** An operator can *decode* an image, *route* output to a file, and *interpret* every exit code — 0 success, 255 not detectable, other non-zero decode-failure (with the partial-decode status-2 warning) — diagnosing a failing scan to the right cause class.
- **Content:** usage; `--output`; exit-code semantics verbatim; the partial-decode message for cascades; what "not detectable" vs "decoding failed" tells you (links to ch. 5 and Developer's Manual detection chapters).
- **Anchors:** corpus §3.5; `jabreader.c:14-69`.

### 9. Embedding the C API

- **Objective:** A developer-literate operator can *write and run* a minimal C program that encodes a string and decodes it back (round trip), using the five-call flow with correct memory ownership (`createEncode` → `generateJABCode` → `saveImage`; `readImage` → `decodeJABCode`; `destroyEncode`/`free`).
- **Content:** the five-call tour with a complete worked program; `jab_encode` fields an SDK user may set; `jab_data` handling; success/failure macros; a one-paragraph tour of the fork's opt-in toggles (`jabSetDiagVerbose`, `jabSetPreferredColorCount`, `jabSetPermissiveColorClassification`) with pointers to the Developer's Manual.
- **Anchors:** signatures `jabcode.h:217-292`; structs `jabcode.h:136-214`; impl anchors corpus §6 table.

### 10. Choosing parameters well

- **Objective:** An operator can *map* four given use cases (durable industrial label, high-capacity document seal, phone-screen ticket, CMYK-printed COA) to a complete parameter set — colour count, ECC level, module size, symbol count — and justify each choice in one sentence.
- **Content:** decision guide synthesizing ch. 2–5; recipes; defaults as the safe baseline; when to deviate.
- **Anchors:** defaults `jabcode.h:31-36`; ch. 2–5 content; Annex A criteria (spec pull shared with ch. 5).

## Part III — Service Track: the codec inside the jab-auth SaaS (Diátaxis: explanation / how-to)

### 11. How the service reaches this library

- **Objective:** A SaaS operator can *trace* a `/api/jabcode/generate` request from the REST surface through the Panama FFM binding to the native `libjabcode.so` encode call, and *state* what the build-time provenance validation guarantees.
- **Content:** the binding chain (REST controller → `PanamaJabCodeService` → Panama wrapper → native lib); provenance validation (ELF header, SHA-256); where the codec's defaults differ from the service's (`JabCodeConfig.defaultConfig()` is 4-colour/ECC 3 vs library default 8-colour).
- **Anchors:** framework corpus model (`JABCodeCOA-crypto/docs/manuals/corpus-model.md`) — `JabCodeService`/`PanamaJabCodeService`, `/api/jabcode/*` endpoint table, provenance constants; this repo corpus §2.1 (`VENDORED_DIR`, `check-lib`).

### 12. Service configuration vs SDK configuration

- **Objective:** A SaaS operator can *state* which codec knobs are reachable through the service API versus SDK-only, including the reconciled `symbolWidth`/`symbolHeight` mapping to `masterSymbolWidth`/`masterSymbolHeight` and current cascade exposure.
- **Content:** `JabCodeConfig` fields ↔ C API mapping; the legacy reconciliation story (one paragraph, case study lives in AF-T); open question flagged honestly (`jabauth.jabcode.*` properties have no found codec-path consumer).
- **Anchors:** framework corpus model `JabCodeConfig` entry (`JabCodeConfig.java:22-38`); this repo `jab_encode` fields `jabcode.h:172-185`.

## Appendices (Diátaxis: reference, operator-scoped)

- **A. Troubleshooting matrix.** Symptom → likely cause → fix; built from exit codes, partial-decode status, common print/scan failures (ch. 5, 8 content). Objective: given a symptom, locate the fix row in under a minute.
- **B. Samples gallery cross-index.** Each `jabcode-samples/` image → the writer command that produces its class of output (ties to ch. 7 worked examples).
- **C. Quick-reference card.** All writer/reader flags with defaults; the defaults table (`jabcode.h:31-36`); capacity extract (spec pull, Table 1 selected rows); exit codes.

---

## Stage 3 notes (for drafting, after approval)

- **Spec pulls required (one ISO sub-agent batch):** Table 1 selected capacity rows; Table 20 recovery percentages; Annex A operator guidance; 4.5 cascade rules + 5 px/module rule; Annex G colour-mode table; Clause 8 grade names. Sub-agent quotes values with clause citations; writer never paraphrases from memory.
- **Framework corpus pulls:** `/api/jabcode/*` endpoints, `PanamaJabCodeService`, `JabCodeConfig` (ch. 11–12 only).
- **Worked examples** run against the real CLI where possible (build permitting in session sandbox — shell holds a different clone, so command outputs must be marked as constructed from source, not executed, unless a matching build is available).
- **Drafting order** (dependency-respecting): ch. 1 → 2 → 4 → 3 → 5 (Part I), then 6 → 7 → 8 → 9 → 10 (Part II), then 11 → 12, appendices last.
- Every chapter follows the Gagné teaching template (objective → prerequisites → content → worked example → self-check with answers → next/deeper pointers) per the skill's chapter-templates reference.
