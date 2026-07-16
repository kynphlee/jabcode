# 15. Conformance testing

<!-- objective: A maintainer can assess this fork's ISO conformance surface: what is implemented (Annex H ]jm via symbology_id.h, ECI/FNC1 protocols with their regression guards) and what is absent (Clause 8 grading — CPA/CVDM and the six-parameter scan grade — NOT FOUND in the tree), and scope a grading implementation as a roadmap item. -->

**Scope.** This chapter maps the fork's ISO/IEC 23634:2022 *transmitted-data* conformance surface (Annex H symbology identifier, Clause 7 ECI/FNC1 protocols) against the standard's text, names the regression guards pinning each piece, and states precisely what a Clause 8 print-quality verifier would have to compute — because no grading code exists anywhere in this tree. Operator-level treatment of conformance claims: [../operators-manual/04-colour-modes-conformance.md](../operators-manual/04-colour-modes-conformance.md); colour-mode conformance itself is [16-extended-colour-modes.md](16-extended-colour-modes.md).

## 15.1 Annex H — the symbology identifier (implemented)

Annex H is **normative**. The identifier is `]jm`: "] is the symbology identifier flag (ASCII value 93)", "j is the code character for JAB Code symbology", `m` a modifier digit from Table H.1. <!-- anchor: ISO 23634 Annex H --> The fork implements the formatter header-only in `symbology_id.h` so it is "callable from the decoder and directly unit-testable". <!-- anchor: src/jabcode/symbology_id.h:18-20 -->

The complete source mapping is one table:

```c
/* rows: [none, preceding, following]; cols: [no ECI, ECI] -- Table H.1 */
static const int m[3][2] = { {0, 1}, {2, 4}, {3, 5} };
```

<!-- anchor: src/jabcode/symbology_id.h:38-39 --> Comparison against Table H.1, all six modifiers:

| (FNC1 state, ECI state) | Source yields | Table H.1 says | Agree |
|---|---|---|---|
| none, no ECI | `m[0][0]` = 0 | 0 "No options" | yes |
| none, ECI | `m[0][1]` = 1 | 1 "ECI protocol implemented" | yes |
| preceding, no ECI | `m[1][0]` = 2 | 2 "FNC1 preceding 1st message character" | yes |
| preceding, ECI | `m[1][1]` = 4 | 4 "FNC1 preceding 1st message character, ECI protocol implemented" | yes |
| following, no ECI | `m[2][0]` = 3 | 3 "FNC1 following an initial letter or pair of digits" | yes |
| following, ECI | `m[2][1]` = 5 | 5 "FNC1 following an initial letter or pair of digits, ECI protocol implemented" | yes |

<!-- anchor: src/jabcode/symbology_id.h:36-42; ISO 23634 Annex H Table H.1 --> The `JAB_FNC1_NONE/PRECEDING/FOLLOWING` enum mirrors the rows; out-of-range `fnc1_mode` clamps to 0 ("defaults to 0"), and `jab_format_symbology_identifier` writes `]j<m>` plus NUL into a 4-byte buffer. <!-- anchor: src/jabcode/symbology_id.h:26-30, 33-54 -->

Lifecycle in the decoder: the `_Thread_local` buffer `g_symbology_identifier[4]` is cleared at `decodeData` entry and populated only on successful decode; the payload itself is never touched — "per ISO/IEC 23634 7.4 the identifier is a transmission-layer preamble the host prepends, never part of the decoded message, so payload hashing/verification is unaffected". Hosts query it via `jabGetSymbologyIdentifier()`. <!-- anchor: src/jabcode/decoder.c:198-207, 2553, 2982-2987; src/jabcode/include/jabcode.h:231 -->

**Guard:** `make test-symid` pins all six rows plus the base case, header-only. <!-- anchor: src/jabcode/Makefile:137-141; src/jabcode/test/test_symbology_id.c:19-27 -->

## 15.2 Clause 7.3 / 5.3.9 — ECI (implemented, decode side)

The standard's transmission rule: the ECI is transmitted as the escape 92DEC `\`; "Application software recognizing \nnnnnn shall interpret all subsequent characters as being from the ECI defined by the 6-digit sequence". Backslash data must be doubled: "If the backslash (byte 5CHEX) needs to be used as encoded data, two bytes of that value shall be transmitted", with the standard's own EXAMPLE "Encoded data: A\\B\C / Transmission: A\\\\B\\C". <!-- anchor: ISO 23634 7.3 -->

Implementation points, decode side:

- **Assignment-number parse** (`decodeData`, `case ECI`): the leading indicator bits select the Table 19 width class — `0` → 7 value bits, `10` → 14, `11` → 20 — then the value is emitted as `0x5C` plus six zero-padded decimal digits, `eci_used` is set (→ modifier 1), `eci_active` is set (→ doubling), and "encoding returns to the invoking (uppercase) mode" per 5.3.9. <!-- anchor: src/jabcode/decoder.c:2908-2956 -->
- **Backslash doubling** (`emitDataByte`): "emit one decoded data byte, doubling a literal backslash (0x5C) while an ECI is active … Only the Punct/Mixed/Byte decode paths can emit 0x5C (Table 13), so this is applied there." <!-- anchor: src/jabcode/decoder.c:2476-2485, 2773, 2808, 2903 -->

The **encoder emits no ECI** — conformance here is decode/transmission-side only, which is exactly why the guards feed `decodeData` hand-crafted bit streams. <!-- anchor: src/jabcode/Makefile:143-146 -->

**Guards:** `make test-eci` asserts the `"\nnnnnn"` output and the `]j1` modifier across all three Table 19 width classes (ECI 26 → `\000026`, 1000 → `\001000`, 123456 → `\123456`); the `eci-backslash` case of `make test-table15` asserts the doubling (`\000001` followed by `\\` for one literal 0x5C — 9 transmitted bytes). <!-- anchor: src/jabcode/test/test_eci.c:5-13, 71-73; src/jabcode/test/test_table15.c:76-78 -->

## 15.3 Clause 7.2 — FNC1 (implemented via Table 15)

The standard, first position: FNC1 "shall not be represented in the transmitted data, although its presence shall be indicated by the use of an appropriate option value in the symbology identifier". Second position (after "a single upper or lower case letter or two digits"): "The leading message character(s) shall be transmitted with the encoded message." <!-- anchor: ISO 23634 7.2 -->

Implementation point: FNC1 reaches the decoder through the Table 15 dispatch (`Upper` MS `11111` + `11` + 3-bit selector), `decodeTable15` case 4:

- The **first** FNC1 in a message is not emitted; its position is inferred from the output cursor — `count == 0` → `JAB_FNC1_PRECEDING` (GS1, modifier 2), otherwise `JAB_FNC1_FOLLOWING` (industry, modifier 3). Because the leading letter/digit-pair was already emitted into the buffer before the FNC1 arrives, the "leading message character(s) … transmitted with the encoded message" requirement is satisfied by construction. <!-- anchor: src/jabcode/decoder.c:2510-2519 -->
- A **subsequent** FNC1 inside the region is emitted as the field separator GS (`0x1D`); Table 15 selector 5 (EoT) emits `0x04` and ends the region per 5.3.10. <!-- anchor: src/jabcode/decoder.c:2520-2526 -->

Two source honesty notes. First, the `jab_encode_mode` enum's own `case FNC1:` branch is an explicit TODO ("not implemented. When implemented, set fnc1_mode to JAB_FNC1_PRECEDING/FOLLOWING (7.2)…") — the Table 15 route is currently the *only* implemented FNC1 entry. <!-- anchor: src/jabcode/decoder.c:2958-2961 --> Second, the state-initialisation comment at the top of `decodeData` still claims "ECI/FNC1 are not yet decoded (see their cases) so these stay at defaults" — stale relative to both the ECI case and `decodeTable15`; trust the cases, not that comment. <!-- anchor: src/jabcode/decoder.c:2550-2552 -->

**Guards:** the FNC1-preceding / FNC1-following / FNC1-separator / EoT / URL-expansion / ISO 15434 cases of `make test-table15`, each asserting bytes *and* modifier. <!-- anchor: src/jabcode/test/test_table15.c:57-74 --> `make test-roundtrip` protects the surrounding text decoder the Table 15 dispatch sits in. <!-- anchor: src/jabcode/test/test_text_roundtrip.c:1-6 -->

## 15.4 Clause 8 — print-quality grading (absent)

**NOT FOUND.** No symbol-quality grading implementation exists anywhere in `src/` — no scan-grade computation, no CPA, no CVDM (corpus §4 "Quality grading" row and §6 NOT FOUND register). A verifier claiming Clause 8 conformance must compute, per the standard:

1. **Six parameters:** the scan grade "shall be the lowest of the grades for decode, unused error correction, grid non-uniformity, fixed pattern damage, colour palette accuracy, and colour variation in data modules". <!-- anchor: ISO 23634 8.1 --> The last two are the colour-specific parameters this codebase's glossary abbreviates CPA and CVDM.
2. **Grading scales:** Decode is graded 4 or 0 (pass/fail). <!-- anchor: ISO 23634 8.2.1 --> The continuous parameters are graded on a "Grade = 0.0" to "Grade = 4.0" scale where "round(x) rounds down to the next 0,1 of x" — i.e. truncation to one decimal, not commercial rounding.
3. **The lowest-grade rule** across all six parameters per scan (8.1), composing into whatever multi-scan averaging the application specifies.
4. **The 15415 deviations:** "This document deviates from ISO/IEC 15415 in grading symbol contrast, modulation and reflectance margin and shall not be graded according to it." <!-- anchor: ISO 23634 8.2.5 --> A grading implementation must therefore *not* import those three ISO/IEC 15415 parameter definitions unchanged.

Roadmap scope, stated as one sentence for planning purposes: implement a verifier module that, given a reference-decoded scan, computes the six Clause 8 parameters (decode 4/0; unused error correction; grid non-uniformity; fixed pattern damage; CPA; CVDM) on the 0.0-4.0 truncate-to-0,1 scale, applies the lowest-grade rule, and explicitly excludes the 15415 symbol-contrast/modulation/reflectance-margin definitions per 8.2.5. Natural attachment points already in the tree: the detector's sampled grid and perspective transform (grid non-uniformity, fixed pattern damage), `readColorPaletteInMaster`/`decodeModuleHD` distances (CPA, CVDM), and the LDPC decoder's correction count (unused error correction) — but none of these currently exports the measurements a grader needs; that export is part of the scope.

The colour-metric *theory* (why CPA/CVDM margins shrink as Nc grows) belongs to JC-S (forthcoming).

## 15.5 The Table 20/21 editorial trap

The standard itself contains an editorial hazard that has already caused confusion in this project's lineage: clause 4.4.1.4 (the metadata E field, ECC levels) cites "Table 21" where **Table 20** (ECC levels / `(wc, wr)`) is meant — Table 21 is the default 8-colour palette. Recorded in the project's ISO clause map; cite the clause map, not the raw PDF, when resolving table numbers. <!-- anchor: JABCodeCOA-crypto/docs/manuals/plan/03-iso-23634-reference-map.md -->

The code is on the correct side of the trap and documents both tables independently: `ecclevel2wcwr` is annotated "Per ISO/IEC 23634:2022 Table 20. ECC levels run 1..10 (default 3)", and `jab_default_palette` is annotated "\[K,B,G,C,R,M,Y,W\] = ISO/IEC 23634 Table 21 (the Fraunhofer reference)". <!-- anchor: src/jabcode/encoder.h:230-234, 26-34 --> Any future edit that "fixes" one of these citations to match 4.4.1.4's misprint should be rejected in review with a pointer to this section.

## 15.6 Conformance surface summary

| ISO surface | Status | Implementation | Guard |
|---|---|---|---|
| Annex H `]jm` identifier (normative) | implemented | `symbology_id.h:36-54`; published `decoder.c:2987` | `test-symid` |
| 7.3 ECI escape + doubling | implemented (decode side) | `decoder.c:2908-2956, 2476-2485` | `test-eci`; `test-table15` (eci-backslash) |
| 7.2 FNC1 first/second position | implemented via Table 15 dispatch | `decoder.c:2493-2531` | `test-table15` |
| 5.3.9 ECI encode side | NOT implemented (encoder emits no ECI) | — <!-- anchor: src/jabcode/Makefile:143-144 --> | n/a |
| `jab_encode_mode` FNC1 branch | TODO in source | `decoder.c:2958-2961` | n/a |
| Clause 8 scan grade (six parameters, CPA/CVDM) | **NOT FOUND** | — | — |

All three implemented protocols run in CI on every relevant PR via `codec-regression.yml` ([13-regression-suite.md](13-regression-suite.md) §13.2). <!-- anchor: .github/workflows/codec-regression.yml:87-96 -->
