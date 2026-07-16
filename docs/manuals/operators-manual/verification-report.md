# Verification Report — jabcode Operator's Manual (JC-U)

**Verifier pass:** manual-forge Stage 4, fresh-eyes verifier (did not write the draft) · **Date:** 2026-07-15
**Book under test:** 15 files in `docs/manuals/operators-manual/` (ch. 01–12 + appendices A–C) against `docs/manuals/corpus-model.md` (swift-java-poc @ `8f76559`), the framework corpus model (`JABCodeCOA-crypto/docs/manuals/corpus-model.md`, `main` @ `240f771`), and the ISO/IEC 23634:2022 text extraction (`JABCodeCOA-crypto/.amazonq/rules/memory-bank/documentation/specification/ISO-IEC-23634.txt`).

---

## Summary verdict

**PASS — ship after two dispositions.** Every numeric constant, flag, default, signature, exit code, seed-adjacent value, endpoint and ISO quotation checked in this pass matches its source; all 147 relative links resolve; CommonMark lint is clean. Zero blockers. Two majors (no `index.md`; Appendix B delivers a re-scoped objective) and six minors. Three drifted line anchors in ch. 12 were fixed mechanically during this pass. Truthfulness across the book is exceptional — the draft's own "constructed, not executed" and "NOT FOUND" honesty flags are consistently present and accurate.

Dispositions required before ship:

1. Add an `index.md` listing all 15 chapters + outline (protocol §1 requires it; the book has none).
2. Accept or revise Appendix B's re-scope (per-image gallery index → representative-command table), since the gallery assets are genuinely absent from this working tree.

---

## Check 1 — Deterministic checks

### 1(a) Constants, flags, defaults, signatures, exit codes vs source

All verified by reading the cited source lines directly (not via the corpus model alone). Highlights, all **match**:

| Class | Items verified | Result |
|---|---|---|
| Public-header constants | `VERSION "2.0.0"`, `MAX_SYMBOL_NUMBER 61`, all six `DEFAULT_*` (1/12/8/2/3/7), `COLOR_PALETTE_NUMBER 4`, `JAB_SUCCESS 1`/`JAB_FAILURE 0`, `NORMAL_DECODE 0`/`COMPATIBLE_DECODE 1`, `VERSION2SIZE`/`SIZE2VERSION` (`jabcode.h:21-54`) | match |
| Encoder tables | `jab_default_palette` RGB triplets + `[K,B,G,C,R,M,Y,W]` comment (`encoder.h:20-34`); FP cores 0/0/6/3 (`:50-53`); AP cores 3/3/3/3/6 (`:58-62`); `jab_symbol_pos` first ring `{0,-1},{0,1},{-1,0},{1,0}` and ±5 extent (`:111-119`); `ecclevel2coderate[11]` incl. slot-0 `0.55f` (`:226`); `ecclevel2wcwr[10][2]` + Table 20 comment (`:230-234`); `jab_ap_num` 2→9 (`:285-292`) | match |
| Writer CLI | Usage text verbatim (`jabwriter.c:30-58`); all 13 flags, accepted sets, defaults; every quoted error string (`Invalid color number…`, `Invalid symbol number (must be 1 - 61).`, `Invalid error correction level (must be 1 - 10).`, `Invalid symbol side version (must be 1 - 32).`, `Invalid symbol position value (must be 0 - 60).`, `Incorrect symbol position value for master symbol.`, both `…information is incomplete…`, `Invalid color space (must be 0 or 1).`, `Input data missing`/`is empty`, `Output file missing`, `Creating jab code failed`); exit contract `0 | 1` (`:431`); `--help`/no-args exit 1 (`:435-439`); later-input-wins frees (`:79,105`); WS-0/WS-3 comment (`:147-148`) | match |
| Reader CLI | Usage (`jabreader.c:14`); exit contract `0 | 255 | other` (`:23`); `--help`/no-args → 255 (`:27-31`); `Unknown parameter` → 255 (`:40-42`); module-size exit `(jab_int32)(symbols[0].module_size + 0.5f)` (`:60`); status-2 warning text (`:68`); `fopen(argv[3])` strict ordering (`:36,74`); stdout newline vs raw `--output` (`:80-88`); `decodeJABCodeEx(bitmap, NORMAL_DECODE, …, MAX_SYMBOL_NUMBER)` (`:53-54`) | match |
| C API | All six quoted signatures (`jabcode.h:217-220, 287-289`); `jab_data`/`jab_encode` structs verbatim (`:136-139, 172-185`); `generateJABCode` return contract `0…4` (`encoder.c:2305`); `createEncode` silent fallbacks (`encoder.c:191-198, 204`); `destroyEncode` frees `enc->bitmap` (`:267`); auto-version path (`:2341-2348`); decode status docs (`detector.c:4060, 4235`); status 2 only under `COMPATIBLE_DECODE` (`:4156-4160`); `saveImageCMYK` RGB→CMYK conversion + TIFF (`image.c:128-180`); `readImage` PNG-based (`image.c:187-197`); toggles (`jabcode.h:245-271`) incl. verbatim `g_preferred_color_count` comment (`:103-104`) | match |
| Build | `CFLAGS = -O2 -std=c11 -fPIC -D_POSIX_C_SOURCE=199309L` (`Makefile:8`); artifacts (`:11-12`); `VENDORED_DIR := ../../lib` (`:18`); soname comment + `-lpng16 -lz` (`:35-41`); `refresh-lib` "ONLY sanctioned way" (`:47-50`); `check-lib: missing ../../lib/libjabcode.so` (`:62`); tool link line (`jabcodeWriter/Makefile:10` = `jabcodeReader/Makefile:10`); `Makefile.win` → `build/libjabcode.dll`, `-L./lib/win64 …` (`:5,10`) | match |
| Framework (ch. 11–12) | Filter order (`SecurityConfig.java:191-197`); auth table incl. dev-open block (`:153-168`), API-key endpoints (`:172-176`), health permitAll (`:127-133`), `denyAll` (`:182`), "Auto-expires: 2026-12-01" (`:39`); `jabauth.security.api-key` (`application.properties:74`, "REQUIRED in production" `:72`); `JabCodeController.generateJabCode` + 400-on-exception (`:37-45`); `JabCodeRequest` 3 fields (`:12-17, 26-33`); `JabCodeService` default method + pinned `defaultConfig()` comment (`:50-52`); `defaultConfig()` = QUATERNARY/12/3 (`JabCodeConfig.java:241-250`); ECC sentinel quote (`:85-86`); 7-arg ctor (`:140-151`); `cascade(...)` (`:331-349`); record Javadoc blockquote (`:22-32` — verbatim); `PanamaJabCodeService` @Primary (`:33`), fallback messages (`:74-86`), stub 100×100 (`:402-404`), `stub-decoded-data` (`:133`), in-memory PNG / "plaintext-at-rest" (`:105-115`), `createPanamaConfig` surface (`:268-332`), `reconcileSymbolVersions` rules + both candid quotes (`:334-374`), pixel forwarding (`:315-328`), `isPanamaAvailable` (`:409-411`); `validateNativeLib` all eight checks + "STALE or MISMATCHED" message + provenance keys (`jab-auth-jabcode/build.gradle:4-102`); `SimpleJabCodeRequest` fixed moduleSize 12 (`:54`), colorMode map (`:62-77`), errorCorrection low/medium/high/maximum → 1/3/5/7 with silent-default fallthrough (`:79-90`); vendored jar (`build.gradle:116`); `jabauth.jabcode.*` defaults (`JabAuthProperties.java:48-50`) and inert-record verdict (framework corpus §3.1, §7 items 2–3) | match |

Zero numeric mismatches found in the entire book.

### 1(b) Anchor resolution

Spot-verified ≥4 `file:line` anchors per chapter by reading the cited lines (≈90 anchors total across the 15 files). All resolve to the claimed content **except** three drifted line numbers in ch. 12's field-map table (`symbol_ecc_levels` cited as jabcode.h:178/:182, actual :180; `symbol_versions` cited :180, actual :179; `symbol_positions` cited :182/:183, actual :181). **Fixed mechanically** (see Mechanical fixes).

### 1(c) Links and lint

- **Links:** 147 relative links across the 15 files; every target is one of the 15 existing chapter files. Zero broken links, zero links to outline/index/external paths.
- **Lint:** blank-line-after-header and blank-line-before-list checks pass across all 15 files (the only multiline pattern hits were `#` shell comments inside fenced code blocks — false positives).
- **`index.md`:** protocol requires "the book's `index.md` lists every chapter" — **no `index.md` exists**. Logged as defect D1 (major).
- **Escaping contract:** honored — `\[K,B,G,C,R,M,Y,W\]`, `clock\_gettime`, `LD\_LIBRARY\_PATH`, `color\_number`, `COMPATIBLE\_DECODE` etc. are escaped in prose; `$` appears only inside code spans/fences.

### 1(d) ISO-anchored claims vs ISO-IEC-23634.txt

All quotations checked **verbatim against the extraction**:

- **Table 20** (txt:1748-1761): all ten rows — levels, recovery % (4,5,6,7,8,9,10,11,12,14), wc/wr pairs, R with decimal commas (0,63 … 0,14) — match ch. 2 and Appendix C.5 exactly. 5.4.1 quotes ("shall be selectable between 1 and 10…", "recovery capability of the bit errors in more than 95 % of cases", R = Pn/Pg) verbatim.
- **Table 1** (txt:646-701): every row the book uses (SV 1, 5, 10, 16, 20, 26, 32; 4c/8c data modules and Pn) matches, including secondary values 1167 (SV1, 8c) and 61302 (SV32, 8c max) and the preamble sentence. Ch. 1's 349/1047 and 338/676 arithmetic checks out; Appendix C.6 byte derivations (130/1152/3409/7647) are correct floor(bits/8).
- **4.3.5** "The smallest square symbol measures 21 × 21 modules and the largest square symbol measures 145 × 145 modules." — verbatim (txt:603-604).
- **4.4.1.2** "Colour modes 0, 3, 4, 5, 6 and 7 are reserved for future extensions…" — verbatim (txt:993-994).
- **4.5.1 / 4.5.2** (txt:1292-1343): docking-side quote, primary-largest recommendation, "always start with the primary symbol… top-bottom-left-right", the revisit NOTE, "indices of the first 60 secondary symbols are defined in Figure 14", and the 5-pixels-per-module sentence — all verbatim. ISO's Figure-14 first layer (top 1, bottom 2, left 3, right 4) independently confirms ch. 3's reading of `jab_symbol_pos`.
- **Annex A** (txt:3055-3126): A.1 trade sentence + three selection criteria; A.2 both rules (incl. fixed-size → highest ECC); A.3 lower-overhead bonus, reliability caveat, all three cascading cases, rectangle-symbols sentence; A.4 all five bullets incl. integer-multiple, 6500k, final-configuration — all verbatim in ch. 3, 5, 10.
- **Annex G** (txt:3554-3660): G.1 a) CMYK palette sentence, G.3 a) closed-applications framing, Table G.2 complete mode map (0 reserved, 1→4 … 7→256, 2 default) — all verbatim in ch. 4/5.
- **Clause 8** (txt:2770-2793): scan grade = lowest of the six named parameters; 8.3.1/8.3.2 titles — match ch. 5. "0 to 4, one decimal" is supported by the grade formulas (0.0–4.0, "rounds down to the next 0,1").

## Check 2 — Diátaxis compass

| Section | Expected | Observed | Verdict |
|---|---|---|---|
| Ch. 1–5 | Explanation-leaning teaching | Explanation with Gagné scaffolding (objective → prereqs → content → worked example → self-check); heavier concepts linked, not inlined | pass |
| Ch. 6–10 | Tutorial / how-to | Task-ordered, runnable commands, expected behaviors, traps | pass |
| Ch. 11–12 | Explanation / how-to | Request trace + configuration mapping; no unmotivated theory | pass |
| App. A, C | Reference (neutral) | Symptom→fix matrix and quoted-value cards; no editorializing | pass |
| App. B | Reference | Table is neutral; the preamble is a short explanatory disclosure of missing assets (justified, but slightly narrative for reference — note N1) | pass w/ note |

## Check 3 — Chain-of-Verification, Part I (claims re-verified independently against source/ISO)

- **Ch. 1** (9 claims): bits/module = log2(N); default 8; header valid-values quote; palette order + RGB; VERSION2SIZE and 21–145; FP roles/corners/core colours; AP types + 2→9 per axis; 4 palette copies; 61-symbol ceiling; Table 1 anatomy arithmetic. **9/9 verified.** (Gloss "even if one region is stained… another copy survives" is unanchored rationale — reasonable, low-risk.)
- **Ch. 2** (8 claims): capacity rows; 7.6 kB headline; secondary-capacity bonus incl. 61302; Table 20 complete; 5.4.1 quotes; `ecclevel2wcwr` + comment; `ecclevel2coderate` + off-by-one indexing observation (slot 0 = 0.55 = level-3 rate — confirmed); writer exit contract. **8/8 verified.**
- **Ch. 3** (8 claims): Annex A.3 three cases + caveat + bonus quote; 61 = 1+60; Figure-14 sentence; `jab_symbol_pos` ring/spiral; docking-side rule + axis interpretation; primary-largest; decode order + revisit note; 5 px/module; writer enforcement strings; worked-example arithmetic (49 modules, ≈750 px). **8/8 verified.**
- **Ch. 4** (7 claims): Nc = colour count 2^(Nc+1); header quote; Table G.2 incl. mode-0 "reserved"; 4.4.1.2 reserved/user-defined quote; G.3 a) closed framing; G.1 a) CMYK palette; WS-0/WS-3 fork comment; "no 2-colour mode anywhere in the standard" (confirmed against Table 6 and Table G.2). **7/7 verified.**
- **Ch. 5** (7 claims): A.1 trade + criteria; A.2 both rules; A.3 pointer; A.4 checklist + 6500k; G.1 a) CMYK; `saveImageCMYK` signature + `--color-space` behavior; clause-8 six parameters, lowest-grade rule, 8.3.1/8.3.2; grading NOT FOUND in this tree (confirmed — no verifier code exists). **7/7 verified.**

No divergences requiring revision were found in Part I.

## Check 4 — ABCD objective check

| Ch. | Objective performable from chapter + declared prereqs? | Notes |
|---|---|---|
| 1 | Yes, with caveat | All four fixed-pattern roles named and described; primary/secondary rule crisp. But the outline promised "symbol anatomy in pictures" and the chapter contains **no figures** — identification "on a printed sample" rests on textual cues alone (D3, minor) |
| 2 | Yes | Tables + estimating technique + encoder-as-referee close the loop |
| 3 | Yes | Three-symbol example ends in a complete, valid command |
| 4 | Yes | The classification table *is* the objective, complete |
| 5 | Yes | Both scenarios walked; grade interpretation honest about the unimplemented verifier |
| 6 | Yes | Clean-checkout build, Windows variant named, failing targets pre-explained |
| 7 | Yes | Full 13-flag surface; three worked encodes with correct syntax |
| 8 | Yes | All three exit classes + the module-size-as-exit-code quirk correctly taught |
| 9 | Yes | Complete compilable program, correct ownership rules, convention-flip warning |
| 10 | Yes | Four recipes with per-choice one-line justifications |
| 11 | Yes | Five-hop trace + precise provenance guarantee statement |
| 12 | Yes | Knob-reachability map, reconciliation story, cascade exposure — all stated |
| A | Yes | Symptom rows locatable well under a minute |
| B | Re-scoped | Original objective (per-image index) impossible — assets absent from this tree; replacement (command-per-class) is disclosed and useful (D2, major as outline deviation) |
| C | Yes | All flags/defaults/exit codes/capacity extract present and correct |

## Check 5 — Triad scores

### Per-part

| Part | Completeness | Truthfulness | Helpfulness | Justification |
|---|---|---|---|---|
| I (ch. 1–5) | 9 | 10 | 9 | Full concept surface; every checked claim verbatim-true; missing promised anatomy figures is the one gap |
| II (ch. 6–10) | 10 | 9 | 10 | Entire CLI/API/build surface covered; two minor over-generalizations (D4, D5); trap-warnings (help exit codes, exit-code quirk, TIFF-not-readable) are exactly operator-grade |
| III (ch. 11–12) | 9 | 10 | 9 | Both objectives fully served; framework quotes all verbatim; one dangling palette pointer (D6) |
| Appendices | 8 | 9 | 9 | B re-scoped, no book index.md; error strings verbatim; A/C inherit the status-2 print nuance (D5) |

### Per-chapter C/T/H

| Chapter | C | T | H |
|---|---|---|---|
| 01 what-a-jab-code-is | 8 | 10 | 9 |
| 02 capacity-size-robustness | 10 | 10 | 10 |
| 03 cascading | 10 | 10 | 10 |
| 04 colour-modes-conformance | 10 | 10 | 10 |
| 05 printing-and-scanning | 9 | 10 | 9 |
| 06 building-the-library | 10 | 10 | 10 |
| 07 encoding-with-jabcodewriter | 10 | 9 | 10 |
| 08 decoding-with-jabcodereader | 10 | 9 | 10 |
| 09 embedding-the-c-api | 9 | 10 | 10 |
| 10 choosing-parameters | 10 | 10 | 10 |
| 11 service-binding-chain | 10 | 10 | 9 |
| 12 service-vs-sdk-configuration | 9 | 9→10 (after fixes) | 9 |
| App. A troubleshooting | 9 | 9 | 10 |
| App. B samples cross-index | 7 | 10 | 8 |
| App. C quick-reference | 10 | 10 | 10 |

---

## Defect register

| ID | Severity | File / section | Defect | Suggested disposition |
|---|---|---|---|---|
| D1 | **Major** | book root | No `index.md`; protocol §1 requires the book's index.md to list every chapter (outline.md is a planning artifact, not a reader TOC) | Author adds `index.md` with Part structure + links to all 15 files |
| D2 | **Major** (outline deviation, honestly disclosed) | appendix-b-samples-cross-index.md | Outline objective was a per-image index of `jabcode-samples/`; assets are absent from this working tree (confirmed: no such directory or PDFs exist here — they are untracked files in the *other* clone). Appendix ships a substitute | Either accept the re-scope (recommended; disclosure is accurate) and amend the outline, or vendor the sample assets into this tree and regenerate the appendix |
| D3 | Minor | 01-what-a-jab-code-is.md | No figures despite outline content plan "symbol anatomy in pictures"; objective targets identification on printed samples | Add an annotated anatomy diagram (SVG) for FP/AP/palette/metadata module positions |
| D4 | Minor | 07-encoding-with-jabcodewriter.md §"Cascading" (also App. A.2 row, App. C.1 row) | "The master symbol's position must be 0" is presented as a writer parse-time rule with the quoted error. In source, that CLI check fires only for `symbol_number == 1` with positions supplied (`jabwriter.c:397-404`); for multi-symbol codes the *encoder* reorders so whichever symbol holds position 0 becomes master and errors `Master symbol missing` only if none does (`encoder.c:2181-2200`) | Reword to: "some symbol must be given position 0 (it becomes the master); a single-symbol code with a non-zero position is rejected with '…'". Ch. 3's phrasing is already correct |
| D5 | Minor | 08-decoding-with-jabcodereader.md §"partial-decode warning"; App. A.3 row; App. C.2 note | "the reader prints: 'The code is only partly decoded…'" — in this fork `JAB_REPORT_INFO` is itself gated on `g_diag_verbose` (`jabcode.h:67`), which the reader never enables; so even under status 2 the message would not print. The chapters already call the path effectively unreachable (NORMAL_DECODE), but for the incomplete reason | Add the second gate to the honest footnote: unreachable via NORMAL_DECODE *and* silenced by the diag-verbose gate on `JAB_REPORT_INFO` in this fork |
| D6 | Minor | 12-service-vs-sdk-configuration.md field map ↔ 09-embedding-the-c-api.md | Ch. 12 routes `palette` to "C-API-only ([chapter 9])", but ch. 9 never covers setting a custom palette (it treats palette as library-initialized) | Either point to the Developer's Manual instead, or add one sentence in ch. 9 noting `jab_encode.palette` is caller-visible but custom palettes are Developer's-Manual territory |
| D7 | Minor | 05-printing-and-scanning.md §"error-correction level" | A.2 quote truncated mid-sentence without ellipsis ("…should be used" vs source "…should be used that achieve the best robustness"); meaning preserved (ch. 10 quotes it in full) | Add ellipsis or complete the quote |
| D8 | Minor (fixed) | 12-service-vs-sdk-configuration.md field map | Three jabcode.h line anchors drifted: `symbol_ecc_levels` (cited :178/:182, actual :180), `symbol_versions` (cited :180, actual :179), `symbol_positions` (cited :182/:183, actual :181) | Fixed mechanically in this pass |

Notes (no action required):

- N1: Appendix B's preamble is explanatory prose inside a reference appendix — justified by the disclosure duty; keep.
- N2: Ch. 1's "grows from 2 at the smallest versions to 9" reads `jab_ap_num` correctly; at SV1–5 the two per-axis entries are the finder-pattern positions themselves (ISO formula yields zero AP modules there). Harmless at operator level.
- N3: Ch. 1's palette-comment quote drops the leading word "Order" from `encoder.h:20`; content otherwise verbatim.

## Mechanical fixes applied

1. `12-service-vs-sdk-configuration.md` — anchor `jabcode.h:178 (field at :182)` → `jabcode.h:180` (row `eccLevel`/`symbol_ecc_levels`).
2. `12-service-vs-sdk-configuration.md` — anchor `jabcode.h:180` → `jabcode.h:179` (row `symbolVersions`/`symbol_versions`).
3. `12-service-vs-sdk-configuration.md` — anchor `jabcode.h:182 (field at :183)` → `jabcode.h:181` (row `symbol_positions`).

No link fixes needed (none broken); no lint fixes needed (none failing); no identifier-typo fixes needed.

## Unverifiable-claim register

All items below are **already flagged as such in the book** — the honesty contract is honored; none require cutting.

| Claim | Location | Status |
|---|---|---|
| All worked-example "Expected behavior" outputs (writer/reader runs, C round trip status 3 / 11 bytes, service trace) | ch. 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12 | Constructed from source, not executed — each chapter states this explicitly; session cannot run this fork's tools |
| Panama wrapper jar internals ("the shipped Panama JAR's encoder ignores the pixel-based masterSymbolWidth"; symbol-position assignment inside the wrapper) | ch. 12 | Quoted from framework source comments; the vendored jar's own source is outside both corpora — ch. 12 marks the position-assignment behavior NOT FOUND |
| Quality grading semantics beyond quotation (verifier practice) | ch. 5 | Grading unimplemented in this tree — chapter states it; corpus NOT FOUND register confirms |
| Linker's default preference of `.so` over `.a` for `-ljabcode` | ch. 6 | Standard GNU ld behavior, not source-anchored; low risk, operationally correct (the `LD_LIBRARY_PATH` advice holds either way) |
| `jabcode-samples/` and companion PDFs existing in the *other* clone | App. B | Attested by corpus model §1.2 (shell-clone `git status`); not verifiable from this working tree — appendix says exactly this |
| Four-copies-of-palette damage rationale ("another copy survives") | ch. 1 | Explanatory gloss, not source-quoted; consistent with design intent |
