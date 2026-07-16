# JC-T Developer's Manual — Stage 4 Verification Report

**Verifier:** fresh-eyes sub-agent (did not write the draft) · **Date:** 2026-07-15
**Book:** 17 chapters, `docs/manuals/developers-manual/` · **Corpus:** `docs/manuals/corpus-model.md` (fork @ `8f76559`, line-drift-corrected 2026-07-15) · **ISO ground truth:** `JABCodeCOA-crypto/.amazonq/rules/memory-bank/documentation/specification/ISO-IEC-23634.txt`
**Grounding method:** direct file tools against the `/mnt` fork tree only. One delegated verification sub-agent read the wrong clone (upstream 2.0.0 on the session-shell mount) and reported mass mismatches; its results were discarded and every affected check redone by hand against the fork — an independent, involuntary confirmation that chapter 1 §1.8's dual-clone warning is load-bearing.

---

## Verdict

**CONDITIONAL PASS — ship after one structural gap is closed.**

All deterministic content checks pass: every interop-critical constant, every spot-checked `file:line` anchor (≈120 anchors across 17 chapters, well above the 5-per-chapter floor), and every ISO-anchored claim string-matched against source or the ISO text. The three parallel Writers agree on all eight cross-cutting findings — no inter-chapter contradiction was found. CoVe on chapters 8 and 15 verified every extracted atomic claim. Three broken cross-book links and three provable prose/anchor slips were repaired under the mechanical-fix budget (logged below). The single unresolved gate item is the **missing `index.md`** for this book (protocol check 1 requires it; the sibling operators-manual has one). Two minor content defects are registered for the Editor; neither is a truth failure that blocks use of the affected chapters.

---

## 1. Deterministic checks

### 1.1 Interop-critical constants — all verified verbatim against source

| Constant / value | Manual claim | Source | Status |
|---|---|---|---|
| `LPDC_METADATA_SEED 38545`, `LPDC_MESSAGE_SEED 785465` (transposed `LPDC_` spelling) | ch. 6, 8, 14 | ldpc.h:17-18 | exact |
| `INTERLEAVE_SEED 226759` | ch. 2, 8, 14 | interleave.c:20 | exact |
| LCG multiplier `6364136223846793005ULL`, increment 1 | ch. 8, 14 | pseudo_random.c:23 | exact |
| Temper constants `0x9D2C5680`, `0xEFC60000` | ch. 8 | pseudo_random.c:15-16 | exact |
| `static _Thread_local uint64_t lcg64_seed = 42` | ch. 8, 14 | pseudo_random.c:10 | exact |
| `pn_index` body (float scaling + clamp) | ch. 8 verbatim block | pseudo_random.h:32-38 | exact |
| `ecclevel2wcwr[10][2] = {{3,8},{3,7},{4,9},{3,6},{4,7},{4,6},{3,4},{4,5},{5,6},{6,7}}` | ch. 6 | encoder.h:234 | exact |
| `ecclevel2coderate[11]` (11 entries, index-by-level) | ch. 6 | encoder.h:226 | exact |
| Mask weights `W1 100 / W2 3 / W3 3` | ch. 7 | mask.c:22-24 | exact; matches ISO Table 23 text ("W1 = 100, W2 = 3 and W3 = 3", ISO txt:1987) |
| All 8 mask generators | ch. 7 table | mask.c:317-340 | exact; all 8 match ISO Table 22 formulas (ISO txt:1969-1980) |
| Metadata geometry `MASTER_METADATA_X 6 / Y 1 / PART1_LENGTH 6 / PART2_LENGTH 38 / PART1_MODULE_NUMBER 4` | ch. 4, 5 | decoder.h:20-25 | exact |
| Error codes `DECODE_METADATA_FAILED -1`, `FATAL_ERROR -2` | ch. 5 | decoder.h:17-18 | exact |
| Table H.1 matrix `{{0,1},{2,4},{3,5}}` | ch. 15 | symbology_id.h:39 | exact; all six cells match ISO Table H.1 (ISO txt:3726-3735) |
| `nc_color_encode_table[8][2]` | ch. 4, 5 | encoder.h:124 | exact |
| Default palette `[K,B,G,C,R,M,Y,W]` | ch. 4 | encoder.h:26-34 | exact |
| `character_size[7]={5,5,4,4,5,6,8}`; `latch_shift_to[14][14]`; `mode_switch[7][16]` | ch. 4 verbatim blocks | encoder.h:207, 186-200, 213-220 | exact, token-for-token |
| `slave_palette_position[64]` boustrophedon x∈[4,11], y∈[5,12] | ch. 5, 16 | decoder.h:36-45 | exact |
| FP/AP core colours + per-mode index tables | ch. 4, 16 | encoder.h:50-75 | exact |
| `MODE0_MEAN_CHROMA_TOLERANCE 30` | ch. 5, 10, 16 | detector.c:80 | exact |
| `NC2_WHITE_DEMOTE_CHROMA 20` | ch. 5 | decoder.c:256 | exact |
| `MAX_MODULES 145`, `MAX_FINDER_PATTERNS 500`, `CROSS_AREA_WIDTH 14` | ch. 5, 9 | detector.h:23-28 | exact |
| Nc ladder `{original_Nc,1,0,2,3,4,5,6}`, `nc_tries = 8`; pin switch 2→0…256→7 | ch. 5, 16 | decoder.c:2119-2120, 2131-2146 | exact |
| `max_iter=25` both LDPC decoders | ch. 6 | ldpc.c:909, 1379 | exact; equals Annex B "L = 25" (ISO txt:3221, 3280) |
| `LDPC_CACHE_SIZE 32`, `_Thread_local` enc/dec caches | ch. 6 | ldpc.c:533, 543-544 | exact |
| `generateJABCode` return contract "0:success \| 1 … \| 4" | ch. 2, 3, 4, 11 | encoder.c:2305, 2443 | exact |

### 1.2 Anchor spot-verification (≥5 per chapter)

Every spot-checked anchor resolved to the cited line with the cited content. Highlights per chapter (all verified by direct read):

- **ch. 1:** Makefile:8 (CFLAGS), 18-19 (VENDORED_DIR), 24 (.PHONY), 35-41 (soname comment + link line, quote verbatim), 46-51/53-71 (refresh-lib/check-lib comments verbatim), 62 (`test -f` guard), 74 (clean list); Makefile.win:3, 5-10.
- **ch. 2:** all 13 encode-stage call sites (encoder.c:2322-2434) and decode-stage anchors (detector.c:4087-4088, 3740/3773, 4122-4132, 4188; decoder.c:2094/2185, 1934, 1967, 1979) exact; include-edge list matches source includes; status protocol at detector.c:4060, 4135-4160, 4217-4221 exact.
- **ch. 3:** every macro/typedef/struct/extern line in jabcode.h verified (21-115, 120-214, 217-292); `__thread … g_strict_partII_required = 0` at decoder.c:43; "Thread-local." header-comment contradiction real (jabcode.h:253-254 vs decoder.c:65-88); `resetDecoderState` no-op comment verbatim at decoder.c:3016.
- **ch. 4:** getSymbolCapacity terms (encoder.c:653-687), getOptimalECC loop (700-713), `@return JAB_SUCCESS | JAB_FAILURE` on a void function (696-698), encodeMasterMetadata block incl. ARM-glibc `round()` comment (927-941), numeric shift-to-byte 6-bit fix comment (856-861), factor reset (841-845), CRITICAL-FIX Part II comment (2425-2426), genColorPalette grid (29-88), setDefaultPalette branches (95-122), mode-table representative rows (`[74][0]=10`, `[32]`, `[33][3]=0`, `[13][4]=-19`, `[44]`) all exact.
- **ch. 5:** function definition lines all exact — findMasterSymbol 1811 (doc 1804), seekPatternHorizontal 319, checkPatternCross 125, crossCheckColor 766, saveFinderPattern 1143, selectBestPatterns 1312, scanPatternVertical 1427, seekMissingFinderPattern 1577, findAlignmentPattern 2623, findSlaveSymbol 2767, getSideSize 2997, chooseSideSize 3034, calculateSideSize 3072, sampleSymbolByAlignmentPattern/detectMaster/decode entries; decoder-side decodeModuleNc 927, decodeModuleHD 710, PartI 1262 (Mode 0 short-circuit 1307-1318), PartII 1470 (bits_per_module = Nc+1 fix), Mode 0 palette synthesis W2.9/W2.10 at 2206/2404; min_module_size quote at detector.c:1814-1815 exact (height/870 arithmetic checks: 2·3·145).
- **ch. 6:** createMatrixA 172, GaussJordan 235, createMetadataMatrixA 430, createGeneratorMatrix 476, encodeLDPC 645, decodeMessage 770, decodeLDPChd 906, decodeMessageILL 1066, decodeMessageBP 1209, decodeLDPC 1376 — all exact (the chapter's own corpus line-drift corrections are right); commented-out soft-path `reportError` at 1497/1546 exact.
- **ch. 7:** all rule/function anchors exact (see 1.1); commented-out row/column rule-1 variant really spans mask.c:67-129; canvas memset -1 at 375; strict `<` selection at 387.
- **ch. 8:** everything (see 1.1 and CoVe §4).
- **ch. 9:** binarizerHist 106, binarizerHard 184, filterBinary region, binarizer 408 (Hist fallback at 446), balanceRGB 485, getAveVar 548, binarizerRGB 602; transform.c square2Quad 33, quad2Square 92 (unchecked deref at 103), perspectiveTransform 164 (q2s leak path ~178), getPerspectiveTransform 202, warpPoints 225; sample.c SAMPLE_AREA_* 21-22, sampleSymbol 31, sampleCrossArea 124; image.c saveImage 27, convertRGB2CMYK 65, saveImageCMYK 128, readImage 187, saveImageToMemory 244 (single-pass/PNG_IMAGE_PNG_SIZE_MAX comments at 238-265), readImageFromMemory 294.
- **ch. 10:** gate-status audit reproduced: `USE_LAB_DISTANCE`/`USE_FP_CALIBRATION` appear only in decoder.c (gated includes/call sites), test_roundtrip_with_noise.c, and scripts/ws4_8_threshold_sweep.sh — no Makefile defines either; `adaptive_palette_*` symbols appear only in adaptive_palette.c/.h (zero callers, including tests); `jabApplyCalibration` has no encoder-path caller (only its own test); decode_profile.h fallback guard at 44, macros with re-check at 111-127.
- **ch. 11:** writer usage text, both scans' flag/validation anchors, all error strings, exit paths (help exit 1 at 435-439; parse-failure return without `cleanMemory` at 440-443 — confirmed by direct read; `cleanMemory` at 421-427), `generateJABCode != 0` at 476; reader verified line-by-line (93-line file read in full): all exits, module-size diagnostic at 60, unreachable partial-decode warning, fopen(argv[3]) hazard, leak on unopenable-output path.
- **ch. 12:** Makefile bench targets/lines/comments exact; bench source headers, defaults, payloads, ladders, output schemas, gating semantics all verified at cited lines (bench_codec.c, bench_concurrent.c, bench_cascade.c, bench_profile.c, bench_sweep.c, transcode_tool.c, README-bench.md); `benchmarks/transcode_survival.py` confirmed absent.
- **ch. 13:** exactly **seven** make-target tests (the chapter's correction of the outline's "eight" is right — .PHONY at Makefile:24); `codec-regression.yml` exists with the cited steps at the cited lines; `test-cascade-hv` confirmed absent from the workflow; `ws4_9_full_regression.sh` exists with a 9-entry `GATE_TESTS` array (lines 112-122) matching the chapter's list, `INFO_TESTS` = only `test_roundtrip_all_nc` (128-130), and the "all eight regression-gate tests" header inconsistency at line 6 — exactly as the chapter reports; `test_multi_frame_with_noise.c` / `test_decoder_diagnostic_logging.c` appear in no driver; `baseline-mode1-output.txt` has no code consumer.
- **ch. 14:** pseudo_random.c design comment quoted verbatim; `g_mode0_decode` at detector.c:104 with the every-TU `_Thread_local` note; `g_symbology_identifier[4]` at decoder.c:203; test-concurrent build block verbatim (Makefile:182-186); framework-side claims match the framework corpus (JNA 5.14.0 @ jab-auth-abe/build.gradle:23; `Native.load("rabe_kem",…)` @ RabeJna.java:94; `--enable-native-access=ALL-UNNAMED`; `RabeCodecConcurrencySoakTest`); the jna.protected livelock lesson matches the memory-bank record.
- **ch. 15:** see CoVe §4.
- **ch. 16:** Table G.2 mapping, 4.4.1.2 sentence, G.3 a) sentence all verbatim in the ISO txt (993-996, 3615-3618, 3648-3660); genColorPalette `(vr,vg,vb)` grids match G.3 b)-e) values; `--color-number` gate and WS-0/WS-3 comments at jabwriter.c:147-155; framework `ColorMode COLOR_2…COLOR_256` and discrepancy-log qualification confirmed in the framework corpus (§3.5, log #10).
- **ch. 17:** Makefile vendored-lib quotes verbatim; `jabcode_wrapper.h:10-15` externs verbatim, implementations absent; `validateNativeLib` checks (symlink/size/ELF/SHA-256 provenance keys/error text) verified at jab-auth-jabcode/build.gradle:4-102; `JabCodeConfig` record fields verbatim (JabCodeConfig.java:34-44), pixel-denominated Javadoc (22-32) vs `reconcileSymbolVersions` module-count Javadoc (PanamaJabCodeService.java:350-354) — the contradiction is real and both texts current; `(modules − 17) / 4` at 376-379; `enablePooling`/`optimizedSaving` never referenced in PanamaJabCodeService (grep-confirmed); `jabauth.jabcode.*` inertness matches framework corpus §3.1 + discrepancy log #3.

### 1.3 Links, structure, lint

- **Relative links:** after fixes, all sibling links (01–17) and all `../operators-manual/*` links resolve. Three were broken before fixing (see Mechanical fixes).
- **`index.md`: MISSING.** The developers-manual folder has no index; protocol check 1 requires "the book's `index.md` lists every chapter". → Defect D-1 (major, structural).
- **CommonMark lint:** spot-checked all 17 chapters during full reads — headers followed by blank lines, lists and tables preceded by blank lines, fenced code blocks balanced. No violations found.
- **Escaping contract:** honored — literal underscores/asterisks/braces escaped inside prose and quotes (e.g. `\_POSIX\_C\_SOURCE`, `\{2, 4, 8, …\}`); no unescaped `$` in financial-like contexts (none exist).

### 1.4 ISO-anchored claims vs the ISO txt

All checked and matching, including: clause 5.1 step texts (ch. 4 table); clause 6.1 steps a)–p) verbatim (ch. 5 §5.1); Formulas (5)–(8) `floor(… + 7.5)` and Table 24 rules incl. "The side size with a bigger flag value shall be chosen" (ch. 5 §5.5); Annex B `R = 1−wc/wr`, "wc ≥ 3 and wr ≥ wc+1", soft-ILL-for-metadata / hard-for-message assignment, "flips those bits with the maximum λ[l]v > 0", tentative decision "cv = 1, if λ > 0", "L = 25" (ch. 4, 6); Annex C rules 1-3 + `c = m ⊗ G` (ch. 6 §6.8 — the extract genuinely lost the floor/ceiling brackets in rule 2); Annex D token table, 78 bits, "Pg = 1071 and K = 476", and D.4 "no metadata needs to be encoded" (ch. 4); Annex F steps a)–f) and the `rand()` routine `next*1103515245+12345 … %32768` with no numeric seed specified (ch. 8); Annex G Table G.1/G.2/G.3 (ch. 16); Annex H Table H.1 (ch. 15); clauses 7.2/7.3 sentences incl. backslash-doubling (ch. 15); clause 8.1 six-parameter lowest-grade rule, 8.2.1 decode 4/0, "round(x) rounds down to the next 0,1", 8.2.5 deviation sentence (ch. 15); Table 22/23 (ch. 7); the Table 20/21 editorial trap is **real** — ISO txt:1035 reads "corresponding to level 3 in Table 21" where Table 20 is meant (ch. 15 §15.5 confirmed).

Corroborating find (supports ch. 4's open item): Annex D is internally inconsistent — D.3 (ISO txt:3362) says "the metadata length is 10 modules" for the same default-mode symbol that D.4 (txt:3381-3382) says needs no metadata. The chapter's decision to leave Pg = 1071 unreconciled and pin wire behaviour to `test-roundtrip` is the correct disposition.

---

## 2. Diátaxis compass

- **Part II (ch. 3–11): reference — PASS.** Every chapter opens with a Responsibility statement and carries Template B extractive tables (public-surface tables in ch. 3–9, 11; per-module tables in ch. 10). Tone is neutral-fact throughout; opinions are confined to sourced quotes or "stated as an observed property" phrasing (e.g. ch. 7 §7.3). No tutorial creep found.
- **Part I (ch. 1–2): reference/architecture — PASS.** Ch. 1 §1.8 is operational guidance but states verifiable facts of this environment (and was independently validated when a verification sub-agent fell into exactly that trap).
- **Part III (ch. 12–17): reference + analysis — PASS with one note.** Ch. 13 §13.5 ("Extension pattern") is a numbered procedure — the closest the book comes to a how-to. It is objective-mandated ("extend the suite … following the existing self-contained pattern") and stays at pattern level, so it is accepted; flagging for Editor awareness only. Ch. 15 §15.4's roadmap sentence is likewise objective-mandated.

---

## 3. Inter-chapter consistency (three parallel Writers)

| Item | Chapters | Finding |
|---|---|---|
| (a) Soft-decision LDPC decoders caller-less | 2 §2.2 D10 / 5 §5.1 k), §5.15 / 6 §6.2, §6.6 | **Consistent.** All three say the pipeline hard-decodes metadata *and* message data; soft `decodeLDPC` (ldpc.c:1376) + `decodeMessageILL` dead. Independently confirmed: grep finds zero call sites in src/ (ldpc.h:28 declaration only; `decodeMessageBP` is called only from the dead `decodeLDPC` at 1473/1525). |
| (b) Binarizer live vs dormant | 2 §2.2 D1 / 5 §5.8 / 9 §9.1 | **Consistent.** All name `balanceRGB`(485)+`binarizerRGB`(602) as the live D1 entries and `binarizer`/`binarizerHist`/`binarizerHard` (408/106/184) as pipeline-dormant. Confirmed: no caller of the trio outside binarizer.c (Hist reachable only via binarizer's fallback at 446); pass-2 re-binarize is `binarizerRGB` with fixed thresholds (detector.c:3765). |
| (c) adaptive_palette dormant | 2 §2.3, §2.4 / 10 §10.1 | **Consistent** on the finding (compiled, exported, zero call sites — grep-confirmed, tests included). One wording defect in ch. 10: it describes the corpus's *retracted* `decoder → adaptive_palette` edge as "an include/aspiration edge" — the corrected corpus (2026-07-15) no longer asserts the edge, and decoder.c does not include adaptive_palette.h, so "include edge" is wrong. → Defect D-2 (minor). |
| (d) Detection modes pinned to INTENSIVE_DETECT | 2 §2.2 / 5 §5.3 | **Consistent.** Both cite detector.c:3740 and 3773; confirmed — both `findMasterSymbol` calls hard-code `INTENSIVE_DETECT`; the single behavioural use of the mode is the scan stride at 1814-1815. |
| (e) `_Thread_local` PRNG + per-operation reseeding | 3 (perf notes) / 8 §8.4 / 14 §14.1 | **Consistent.** Same reseed-site inventory (interleave.c:28, 55; ldpc.c:207, 450), same wire-compatibility argument, same single non-reseeding decode-side draw (ldpc.c:~864). All confirmed at source. |
| (f) `generateJABCode` 0-on-success | 2 §2.1 / 3 §3.6.1 / 4 §4.1 / 11 §11.4 | **Consistent.** All four state the inversion identically; encoder.c:2305/2443 and the writer's `!= 0` idiom at jabwriter.c:476 confirmed. |
| (g) Pixel-vs-module `symbolWidth` | 11 §11.2 / 17 §17.5 | **Consistent.** Ch. 11 states the CLI's pixel semantics ("Master symbol width in pixel", jabwriter.c:37-38, confirmed) and defers the binding tension to ch. 17, which documents both current Javadocs and the executing module-count reconciliation. Ch. 2 makes no conflicting claim. |
| (h) Compile-time gate statuses | 2 §2.4 / 5 §5.13 / 10 (head) | **Consistent.** All three: `USE_FP_CALIBRATION` / `USE_LAB_DISTANCE` undefined in every build file; ch. 10 adds the ws4_8 sweep-script variant builds as the only place the flags are ever passed. Grep-confirmed (decoder.c, test_roundtrip_with_noise.c, ws4_8_threshold_sweep.sh only). |

**No contradictions found.** Shared numbers repeated across chapters (seeds, defaults, ladder, wcwr table, status codes, exit codes) are identical everywhere they appear.

---

## 4. Chain-of-Verification — high-stakes analytical chapters

### 4.1 Ch. 8 (PRNG divergence analysis) — 13 atomic claims, all independently verified

1. LCG is `seed = 6364136223846793005ULL·seed + 1` → pseudo_random.c:23. ✔
2. Temper constants `0x9D2C5680`/`0xEFC60000` → :15-16. ✔
3. State is `_Thread_local`, init 42, pre-first-use only → :10 + reseed sites. ✔
4. `pn_index` preserved bit-for-bit in range, clamps only the ~top-128 float-rounding edge → pseudo_random.h:13-38 verbatim. ✔
5. `interleaveData` is exactly Annex F's Fisher-Yates (steps a–f) → interleave.c:26-36 vs ISO txt:3516-3523, step-for-step correct. ✔
6. Annex F's sample PRNG is C89 `rand()` (`1103515245`/`12345`, 15-bit, RAND_MAX 32767) → ISO txt:3524-3534. ✔
7. Annex F names **no numeric seed** ("Give an initial seed…"; `srand` shown seedless) → ISO txt:3518, 3531-3534. ✔
8. Exactly three wire-relevant seeded streams (226759 / 785465 / 38545) at exactly four reseed sites → interleave.c:28, 55; ldpc.c:207, 450. ✔ (exhaustive grep)
9. One non-reseeding consumer: `decodeMessage` small-block tie-break, decode-side only → ldpc.c:857-864 (WS-4.5.3 comment verbatim). ✔
10. In-source interop posture quotes → pseudo_random.h:18-21 and encoder.h:23-24, both verbatim. ✔
11. `deinterleaveData` inverse-by-replay; silent-failure-plus-`index`-leak path → interleave.c:42-77 (tmp_data failure returns without `free(index)`). ✔
12. `test-pn` coverage (top-4096 dense sweep, prime stride 65521, 8 ranges, `oob_legacy > 0` pass condition) → test_pn_index.c:29, 36-56, 61. ✔
13. Provenance: LCG64+temper inherited upstream, fork changed storage class + clamp only → corpus §2.3 "U (made `_Thread_local`)". ✔

The chapter's one open question (whether Fraunhofer/any independent implementation ever matched Annex F literally) is properly marked NOT FOUND, not softened. **Ch. 8 passes CoVe clean.**

### 4.2 Ch. 15 (conformance surface) — 12 atomic claims, all independently verified

1. Annex H normative; `]jm` component glosses → ISO txt:3717-3724. ✔
2. Modifier matrix maps all six Table H.1 cells correctly → symbology_id.h:39-41 vs ISO txt:3730-3735, cell-by-cell. ✔
3. Out-of-range `fnc1_mode` clamps to 0; 4-byte output buffer → symbology_id.h:40, 48-54. ✔
4. `_Thread_local` identifier, reset per decode, payload untouched per 7.4 → decoder.c:196-208, 2982-2988. ✔
5. ECI decode: indicator-selected 7/14/20 value bits (= 8/16/22-bit strings), `\` + 6 zero-padded digits, `eci_used`/`eci_active`, return to Upper → decoder.c:2908-2956, matches 7.3 text (ISO txt:2729-2740). ✔
6. Backslash doubling in `emitDataByte`, applied on the Punct/Mixed/Byte paths → decoder.c:2480, calls at 2773/2808/2903; ISO "two bytes of that value shall be transmitted" ✔
7. Encoder emits no ECI (decode-side conformance only) → Makefile:143-146 comment. ✔
8. FNC1 via Table 15 only; `case FNC1:` is a TODO stub → decoder.c:2493-2531, 2958-2962; 7.2 sentences verbatim (ISO txt:2717-2727). ✔
9. Stale "not yet decoded" init comment flagged honestly → decoder.c:2551. ✔
10. Clause 8 grading absent: no CPA/CVDM/scan-grade code anywhere in src/ (grep: only README-bench.md matches, non-normative context). ✔ Clause 8 requirement statements (six parameters, lowest-grade rule, decode 4/0, truncate-to-0,1 rounding, 8.2.5 deviation) all verbatim → ISO txt:2775-2795, 2781-2783, 2958-2959. ✔
11. Table 20/21 editorial trap real → ISO txt:1035; both source annotations on the correct side (encoder.h:20, 230). ✔
12. Guards exist and run in CI (test-symid/test-eci/test-table15 at Makefile:139/147/153; workflow step at codec-regression.yml:87-96). ✔

**Ch. 15 passes CoVe clean.**

---

## 5. ABCD objective check (outline objectives)

| Ch. | Objective satisfied by this chapter alone (+ declared prereqs)? |
|---|---|
| 1 | Yes — build units, link lines, full target table, vendored discipline + why refresh/check fail, Windows, working-tree caveats, dual-clone warning. |
| 2 | Yes — both pipelines as file/function/line/clause maps; every stage has an owner and entry point; fork overlay with integration mechanisms. |
| 3 | Yes — complete public contract with ownership/threading per function; 0-on-success prominently boxed. |
| 4 | Yes — capacity/ECC/encoding/metadata/matrix/cascade all analyzable at wire level; Annex D vector reproduced token-for-token; open capacity-figure item honestly scoped with the operative regression named. |
| 5 | Yes — end-to-end trace plus a failure-to-stage diagnosis table (§5.18) that directly serves the "diagnose a failure to its stage" behaviour. |
| 6 | Yes — seeds → matrices → Gauss-Jordan → both decode paths; wire-compat set enumerated; Annex D/`test-roundtrip` as the verification instrument. |
| 7 | Yes — generators and penalties reconciled rule-by-rule with ISO; §7.6 gives the exact selection semantics needed to predict the reference. |
| 8 | Yes — the exact PRNG contract, the divergence analysis, and precisely what the clamp preserves. |
| 9 | Yes — live-vs-dormant mapping per detection pass (correcting the corpus), I/O format constraints incl. PNG-only `readImage`. |
| 10 | Yes — each fork module with status, API and interop consequence table (§10.8). |
| 11 | Yes — flag→field table, validation order as executed, every exit path with line, `--help` non-zero, module-size diagnostic. |
| 12 | Yes — every target with invocation, arguments, output schema, and what it guards; NOT FOUND driver documented. |
| 13 | Yes — seven targets (outline's "eight" corrected against source), CI wiring, unwired inventory, extension pattern. |
| 14 | Yes — what upstream shared / fork isolated, both guards, the four shared toggles' contract, the JNA livelock lesson with its boundary of applicability. |
| 15 | Yes — implemented surface with guards; absent surface with a precise Clause 8 scope statement. |
| 16 | Yes — per-mode touch-point map, Annex G reconciliation, interchange consequences stated with exact strictness levels. |
| 17 | Yes — producer contract, ABI guard, unbacked wrapper header, consumer provenance, mapping table with the pixel/module hazard quantified, reconciliation case history. |

---

## 6. Triad scores per Part

| Part | Completeness | Truthfulness | Helpfulness | Justification |
|---|---|---|---|---|
| I (ch. 1–2) | 9 | 9 | 10 | Declared surface fully covered (all targets, all stages); one prose miscount found and fixed (clean list); the dual-clone note demonstrably prevents a real failure mode — it caught this verification's own sub-agent. |
| II (ch. 3–11) | 9 | 9 | 9 | Complete API/module surface incl. corrected corpus drift; ~80 anchors spot-checked, all exact after two mechanical fixes; one stale corpus-edge sentence (D-2) and one broken link (fixed) are the only truth blemishes; dense, extractive, maintainer-usable. |
| III (ch. 12–17) | 9 | 9 | 9 | All six benches, full regression estate incl. CI divergences (gating disagreement, uncovered target), conformance surface verified against ISO to the sentence, framework mapping verified at line level; missing only the book index (structural, registered). |

No part scores below threshold. Grounding rate across all spot-checked claims: ≈99% verified; the remainder are the explicitly-marked NOT FOUND/open items (properly labelled, per protocol §3).

---

## 7. Defect register

| ID | Severity | Location | Defect | Disposition |
|---|---|---|---|---|
| D-1 | **Major** (structural, ship gate) | book root | No `index.md` listing the 17 chapters (protocol check 1; operators-manual precedent exists). | Writer/Editor: create `index.md` before ship. Content generation is outside the Verifier's mechanical-fix mandate. |
| D-2 | Minor | 10-fork-extensions.md §10.1 | "The corpus model's dependency edge 'decoder → adaptive_palette' is an include/aspiration edge" — the corrected corpus (2026-07-15) no longer asserts that edge, and decoder.c does not include adaptive_palette.h, so "include edge" is doubly wrong. The chapter's actual finding (dormant, zero callers) is correct. | Editor: reword to "the pre-correction corpus drew a decoder → adaptive_palette edge; it was retracted 2026-07-15 — there is neither a call edge nor an include edge." |
| D-3 | Minor (observation) | 07-mask.md §7.5, §7.12 | The chapter hedges rule 3's k = 0 divergence as "cannot be certified (formatting loss)", but the ISO extract is clean here ("Number of modules = (5+k), k > 0", txt:2001): under the extract, a run of exactly 5 scores nothing while the source scores W3. Runs ≥ 6 agree. Divergence affects mask choice only, never interop — same class the chapter already assigns to rule 1. | Editor (optional): tighten the hedge to state the k = 0 divergence as real-per-extract. Not blocking. |
| D-4 | Minor (observation) | 13-regression-suite.md §13.5 | Numbered extension procedure is the book's closest approach to how-to material; accepted as objective-mandated. | No action; noted for Diátaxis audit trail. |

No blockers. No truth failures in any interop-critical statement.

---

## 8. Mechanical fixes applied (logged)

| # | File | Fix | Proof |
|---|---|---|---|
| F-1 | 03-public-surface-jabcode-h.md | Broken link `../operators-manual/09-sdk-surface.md` → `09-embedding-the-c-api.md` | operators-manual directory listing (no file named 09-sdk-surface.md) |
| F-2 | 11-cli-internals.md | Broken links `07-encoding-with-the-writer.md` / `08-decoding-with-the-reader.md` → `07-encoding-with-jabcodewriter.md` / `08-decoding-with-jabcodereader.md` | same listing |
| F-3 | 06-ldpc.md §6.14 | Line-anchor correction: leak-path returns "974, 980, 996, 1008" → "973, 980, 996, 1008" | ldpc.c:970-974 read: `return 0;` is line 973 |
| F-4 | 06-ldpc.md (head) | "1563 lines" → "1562 lines" | file content ends at 1562; aligns with corpus §2.3 |
| F-5 | 01-repository-and-build.md §1.4 | Miscount "the six library-linked test binaries plus test_pn_index/test_symbology_id" → "six test binaries (…)" naming the actual six removed by `clean` | Makefile:74 (four lib-linked + two header-only = six test binaries in the rm list) |

No content was rewritten beyond these provable corrections.

---

## 9. Unverifiable-claims register (properly marked in the book; verified as genuinely unresolvable from this corpus)

| Claim | Chapter | Status |
|---|---|---|
| Whether the Fraunhofer reference (or any independent implementation) ever matched Annex F's `rand()` literally | 8 §8.3 | NOT FOUND — requires upstream history or ecosystem survey; correctly flagged. |
| Soft-decoder λ sign convention vs Annex B's "cv = 1 if λ > 0" | 6 §6.6 | Flagged, unresolved — the extract genuinely reads λ>0→1 (ISO txt:3218) while the source assigns 1 on λ<0; polarity of the reliability value is not defined in the extract. Dead code either way. |
| Annex C rule 2 exact rounding (floor/ceiling brackets lost in extraction) | 6 §6.8 | Confirmed lost in the extract (ISO txt:3307); source truncation documented as de-facto wire behaviour. Correct handling. |
| Annex D Pg = 1071 / K = 476 vs computed 1044/464 | 4 §4.4.3 | Open, correctly pinned to `test-roundtrip` instead. Verifier corroboration: the standard itself is internally inconsistent here (D.3 "metadata length is 10 modules" vs D.4 "no metadata needs to be encoded"). |
| A `git status` for this working tree | 1 §1.8 | Not obtainable through the session shell (different clone) — re-confirmed during this verification. |

---

## 10. Ship checklist

1. Create `index.md` (D-1) — only gate item.
2. Optional Editor passes: D-2 rewording, D-3 hedge tightening.
3. Re-run link check after index creation.
