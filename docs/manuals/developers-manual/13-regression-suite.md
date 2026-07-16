# 13. Regression suite

<!-- objective: A maintainer can run the full regression set, state what each target guards (pn FP-UB, Annex H identifiers, ECI bit-level, Table 15/FNC1 backslash-doubling, text round-trip, high-version cascade byte-run width fix, TSan reentrancy), and extend the suite for a new fix following the existing self-contained pattern. -->

**Scope.** The regression estate has three tiers: (1) **seven** make-target tests in `src/jabcode/Makefile` that build *and run* on invocation <!-- anchor: src/jabcode/Makefile:24, 133-186 -->; (2) the CI gate `.github/workflows/codec-regression.yml`, which runs those plus two suites that have no make target; (3) a larger inventory of target-less test sources under `src/jabcode/test/`, most of which are wired only through `scripts/ws4_9_full_regression.sh`. Note the outline for this chapter says "the eight make-target tests"; the Makefile defines **seven** (`test-pn`, `test-symid`, `test-eci`, `test-table15`, `test-roundtrip`, `test-cascade-hv`, `test-concurrent`) — follow the source. <!-- anchor: src/jabcode/Makefile:24 -->

Every make-target test follows one contract: a self-contained `main`, deterministic fixtures, a printed `RESULT: PASS` / `RESULT: FAIL` line, and exit status 0/1 — so `set -e` shell drivers and CI gate on them directly. <!-- anchor: src/jabcode/test/test_pn_index.c:59-63; .github/workflows/codec-regression.yml:80-81 -->

## 13.1 The make-target tests

| Target | Line | Links | Guard |
|---|---|---|---|
| `test-pn` | Makefile:133 | nothing (header-only) | `pn_index()` FP-UB fix |
| `test-symid` | Makefile:139 | nothing (header-only) | Annex H Table H.1 identifier |
| `test-eci` | Makefile:147 | `libjabcode.a` | bit-level ECI decode (5.3.9 / 7.3) |
| `test-table15` | Makefile:153 | `libjabcode.a` | Table 15 / FNC1 / 7.3 backslash-doubling |
| `test-roundtrip` | Makefile:158 | `libjabcode.a` | multi-mode text byte-identity |
| `test-cascade-hv` | Makefile:166 | `libjabcode.a` | high-version cascade + >8207-byte byte-run |
| `test-concurrent` | Makefile:182 | codec **sources**, TSan | reentrancy (`_Thread_local` state) |

<!-- anchor: src/jabcode/Makefile:133-186 -->

### `test-pn` — the `pn_index()` FP-UB guard

Self-contained because `pn_index` is a `static inline` in `pseudo_random.h` — "no libjabcode link is needed". <!-- anchor: src/jabcode/Makefile:131-132 --> The history, from the test header: `pn_index()` "replaced an inline float-scaling expression that returned `range` (one past the valid \[0, range-1\]) whenever the single-precision ratio rounded to 1.0f -- a latent heap out-of-bounds in interleave.c / ldpc.c". It pins two invariants permanently: (1) `pn_index()` never returns outside `[0, range-1]`; (2) it is "bit-identical to the legacy mapping for every draw the legacy code already mapped in range -- so the Fraunhofer / ISO 23634 permutation (and wire-level interoperability) is preserved". It also asserts the legacy mapping really did overflow ("the bug was real"): the pass condition requires `oob_legacy > 0`. <!-- anchor: src/jabcode/test/test_pn_index.c:2-14, 61 --> Coverage strategy: a dense sweep of the top 4096 draws (where the float ratio rounds to 1.0f) plus a prime-stride (65521) sweep of the whole 32-bit space, over ranges `{2, 5, 17, 256, 1000, 4096, 65536, 1000000}`. <!-- anchor: src/jabcode/test/test_pn_index.c:29, 36-56 --> The wire-compatibility argument this test encodes is the load-bearing one for any PRNG change ([08-interleave-and-prng.md](08-interleave-and-prng.md)).

### `test-symid` — Annex H Table H.1

Header-only formatter, no library link. <!-- anchor: src/jabcode/Makefile:137-138 --> Pins all six Table H.1 rows verbatim — `(eci, fnc1)` → `]j0` … `]j5` — plus the current default flags case ("ECI/FNC1 not yet decoded" in the fixture comment): `(0, 0)` → `]j0`. "If anyone perturbs the modifier table, this fails loudly." <!-- anchor: src/jabcode/test/test_symbology_id.c:5-7, 20-27, 40-42 --> The conformance mapping itself is analysed in [15-conformance-testing.md](15-conformance-testing.md).

### `test-eci` — bit-level ECI decode

The encoder emits no ECI, so the test hand-crafts the bit streams `decodeData()` consumes — Upper-mode MS latch (`11111 10`, Table 14) plus a Table 19 assignment number — and asserts (a) the transmitted output is the 7.3 escape `"\nnnnnn"` (backslash + 6-digit zero-padded ECI number) and (b) the Annex H modifier is `]j1`. Covers all three Table 19 width classes (8 / 16 / 22-bit); bits are written MSB-first to match `readData()`. <!-- anchor: src/jabcode/test/test_eci.c:2-13; src/jabcode/Makefile:143-146 --> It links `libjabcode` for the internal entry `decodeData` and for `jabGetSymbologyIdentifier` (declared locally as `extern jab_data* decodeData(jab_data* bits);`). <!-- anchor: src/jabcode/test/test_eci.c:21, 52 --> Cases: ECI 26 (8-bit) → `\000026`, ECI 1000 (16-bit) → `\001000`, ECI 123456 (22-bit) → `\123456`. <!-- anchor: src/jabcode/test/test_eci.c:71-73 -->

### `test-table15` — Table 15 / FNC1 / backslash-doubling

Same hand-crafted-bit-stream technique ("Upper MS (31) + '11' reaches Table 15, then a 3-bit selector"). <!-- anchor: src/jabcode/test/test_table15.c:2-9 --> Seven cases, each asserting decoded bytes *and* the resulting symbology identifier:

| Case | Stream | Expect |
|---|---|---|
| FNC1-preceding | FNC1 then `'A'` | `"A"`, `]j2` |
| FNC1-following | `'A'` then FNC1 | `"A"`, `]j3` |
| FNC1-separator | region-opening FNC1 + internal FNC1 | `"\x1D"` (GS), `]j2` |
| EoT | Table 15 selector 5 | `"\x04"`, `]j0` |
| `https://` | selector 1 URL expansion | 8 bytes, `]j0` |
| ISO 15434 | selector 0 | `"[)>\x1E"`, `]j0` |
| eci-backslash | ECI(1) then Byte-mode 0x5C | `\000001\\` (9 bytes: escape + doubled literal), `]j1` |

<!-- anchor: src/jabcode/test/test_table15.c:57-78 --> The last row is the 7.3 backslash-doubling guard: a literal byte 0x5C after an ECI must be transmitted twice.

### `test-roundtrip` — multi-mode text byte-identity

Encodes → decodes five strings at 8 colours, asserting byte-identical round-trips: `"HELLO WORLD"` (Upper+space), `"hello world"` (Lower — "the latch-fixed mode"), `"0123456789"` (Numeric), `"Hello World 123"` (mode switches), `"Hi, there! 42."` (+Punct). Rationale: "The Lower-mode latch fix and the Table 15 dispatch sit inside the working text decoder", and `emitDataByte` routing must be the identity when no ECI is active. <!-- anchor: src/jabcode/test/test_text_roundtrip.c:1-9, 45-49 -->

### `test-cascade-hv` — high-version cascade and the byte-run width fix

Guards a 2026-07-09 finding from downstream FSMA-204 Phase-3 testing: a docked cascade of 3+ symbols at 16 colours near version 31 "ENCODES successfully but decodeJABCode returns an empty/absent payload" — distinct from the documented `v>=10 && v%5==0` family. <!-- anchor: src/jabcode/test/test_cascade_highversion.c:2-9 --> Root cause, quoted: "The encoder's >8207 continuation wrote the numeric shift-to-byte token (111100 = 60) with width 5 instead of 6, dropping a bit and shearing the rest of the stream ('Not enough bits to decode'). Content-dependent." <!-- anchor: src/jabcode/test/test_cascade_highversion.c:147-153 --> The Makefile comment adds the companion: "the per-run continuation-factor reset". <!-- anchor: src/jabcode/Makefile:162-165 --> Fixture design worth copying:

- deterministic LCG payloads plus a **`java.util.Random`-compatible `nextBytes`** generator "so payloads found failing through the JVM harnesses reproduce bit-identically here" (seed `0xFA0003`, 9216 bytes); <!-- anchor: src/jabcode/test/test_cascade_highversion.c:36-53, 147, 154-157 -->
- **wrapper-parity** cases: `ecc < 0` "mimics the Panama wrapper's historical behaviour: ECC set on symbol 0 ONLY, slaves left at 0"; <!-- anchor: src/jabcode/test/test_cascade_highversion.c:68-70, 141-145 -->
- a two-run payload (0xFF run, `"AAAA…"` bridge, 0xFF run) guarding "the stale-factor companion bug: TWO >8207-byte byte runs in one message". <!-- anchor: src/jabcode/test/test_cascade_highversion.c:159-173 -->

Cascade construction follows `bench_cascade.c`; decode is straight from `enc->bitmap` — "no PNG, no FFM -- so a failure here is native-codec-only". <!-- anchor: src/jabcode/test/test_cascade_highversion.c:11-13 -->

### `test-concurrent` — TSan reentrancy guard

The build line is the point: it compiles `test/test_concurrent_roundtrip.c` **plus `$(SOURCES)` — the codec sources themselves — with `-fsanitize=thread -O1 -g`**, not against the pre-built static lib, because "the shared codec globals live there, so linking the non-instrumented $(STATIC_LIB) would hide exactly the races we guard against"; `-O1` keeps TSan's stacks readable. <!-- anchor: src/jabcode/Makefile:174-186 --> Semantics before/after the `_Thread_local` fix are stated in both the Makefile and the test header: before — TSan reports races on `lcg64_seed` / `g_mode0_decode` / `g_symbology_identifier` / `g_calibration` and/or round-trips fail; after — TSan clean, `RESULT: PASS`. <!-- anchor: src/jabcode/Makefile:179-181; test/test_concurrent_roundtrip.c:12-19 --> Full concurrency analysis: [14-concurrency.md](14-concurrency.md).

## 13.2 CI wiring — `codec-regression.yml`

`.github/workflows/codec-regression.yml` is the executing gate ("the NDK/Gradle workflows compile the C core but never run it … A decoder regression in src/jabcode/*.c … could merge fully green"). It triggers on PRs/pushes touching `src/jabcode/**`, `swift-java-wrapper/**`, `lib/**`, or itself, and "fails on ANY non-zero exit or any decoded-payload mismatch". <!-- anchor: .github/workflows/codec-regression.yml:1-32 --> Steps, in order:

1. `make all`, then `make check-lib` — the vendored-lib ABI freshness guard ([17-downstream-bindings.md](17-downstream-bindings.md), §17.2). <!-- anchor: .github/workflows/codec-regression.yml:57-69 -->
2. Build both CLIs. <!-- anchor: .github/workflows/codec-regression.yml:75-78 -->
3. The five fast guards: `test-pn`, `test-symid`, `test-eci`, `test-table15`, `test-roundtrip` under `set -e`. <!-- anchor: .github/workflows/codec-regression.yml:87-96 -->
4. **All-Nc round-trip** — compiles `test/test_roundtrip_all_nc.c` directly against `build/libjabcode.a` because "it has no make target"; "the only test that exercises Nc=0 (monochrome) and Nc=7 (256-colour)" in CI. <!-- anchor: .github/workflows/codec-regression.yml:98-110 -->
5. `test-concurrent`, re-run under `setarch -R` (ASLR off) because "modern kernels' high-entropy mmap layout otherwise aborts TSan with 'unexpected memory mapping' before any test runs. Disabling randomization fixes only that startup crash -- a genuine data race still fails the run". <!-- anchor: .github/workflows/codec-regression.yml:112-128 -->
6. Synthetic decode across colour modes 4-128: regenerates `output/images/jabcode_<m>.png` with the freshly built writer (the committed images "are stale relative to the current codec"), runs the report-only `test_synthetic_decode.sh`, then decodes each mode itself and fails on any non-byte-identical payload — "what turns the report-only script into a hard gate with no skips". <!-- anchor: .github/workflows/codec-regression.yml:130-186 -->

`test-cascade-hv` is **not** in the workflow's test list — it exists only as a local make target. Anyone touching the encoder byte-run planner should run it explicitly.

## 13.3 Target-less test sources — present but unwired

`src/jabcode/test/` carries a second population of test sources with **no Makefile target** (corpus §2.3); several sit beside prebuilt binaries in the working tree. Inventory with line counts:

`test_roundtrip_nc0.c` (118), `test_roundtrip_all_nc.c` (109), `test_roundtrip_with_noise.c` (275), `test_mode0_chroma_tolerance.c` (154), `test_mode1_regression.c` (125), `test_lab_color_distance.c` (165), `test_color_calibration.c` (271), `test_multi_frame_decode.c` (197), `test_multi_frame_palette.c` (265), `test_multi_frame_with_noise.c` (246), `test_jab_mobile_with_meta.c` (355), `test_decoder_diagnostic_logging.c` (140). <!-- anchor: corpus-model.md §2.3 (line counts measured from the working tree) -->

Their wiring is `scripts/ws4_9_full_regression.sh` — "the closing seal of WS-4 Phase B-Classical". The script rebuilds `libjabcode` under default Makefile flags, rebuilds each test (library-linked tests with `-Wl,-rpath` to `build/`; `test_lab_color_distance` and `test_color_calibration` self-contained against `lab_color.c` / `color_calibration.c`; the two mobile-bridge tests additionally compiling `../../swift-java-wrapper/src/c/mobile_bridge.c` and `mobile_utils.c` — an out-of-`src/jabcode` dependency), then tallies pass/fail. <!-- anchor: src/jabcode/scripts/ws4_9_full_regression.sh:2-7, 40-104 -->

Gate semantics inside the script: nine **hard-gate** tests that must all pass (`test_mode1_regression`, `test_roundtrip_nc0`, `test_lab_color_distance`, `test_color_calibration`, `test_multi_frame_palette`, `test_multi_frame_decode`, `test_roundtrip_with_noise`, `test_jab_mobile_with_meta`, `test_mode0_chroma_tolerance`), and one **informational** test whose exit code does not gate: `test_roundtrip_all_nc`, "expected to 'fail' because Nc=3 + HELLO is known intermittent at module_size=12 (WS-3 reality-check)". Exit 0 only if every hard gate passes. <!-- anchor: src/jabcode/scripts/ws4_9_full_regression.sh:110-131, 176-191 --> Internal inconsistency, recorded honestly: the header comment says "all eight regression-gate tests" while its own numbered list has ten entries and the `GATE_TESTS` array nine — the arrays are authoritative. <!-- anchor: src/jabcode/scripts/ws4_9_full_regression.sh:5-19, 112-130 --> Note also the CI workflow treats `test_roundtrip_all_nc` as a **hard** gate on synthetic in-memory round-trips, while ws4_9 treats it as informational — the two drivers disagree about the same binary's gating status.

Two sources appear in *neither* the Makefile, the CI workflow, nor ws4_9: `test_multi_frame_with_noise.c` and `test_decoder_diagnostic_logging.c` — fully unwired; treat as exploratory until someone adopts them into a driver.

The related `scripts/ws4_8_threshold_sweep.sh` is not a regression gate: it builds the library four ways (`baseline`, `-DUSE_LAB_DISTANCE`, `-DUSE_FP_CALIBRATION`, both) and compares `test_roundtrip_with_noise` results to decide default compile flags. <!-- anchor: src/jabcode/scripts/ws4_8_threshold_sweep.sh:2-14, 30-35 -->

## 13.4 Baseline files

`test/baseline-mode1-output.txt` is the captured reference output of `test_mode1_regression` — the Mode 1 (Nc=1, 4-colour) baseline suite that is the "Required gate for ALL C library changes in WS-0 (Mode 0) and WS-3 (Nc=7 fix)", on the rationale that "Mode 0 and Mode 1 share 5/9 encoding and 7/13 decoding steps … identical output must be produced before and after any boundary parameter change". <!-- anchor: src/jabcode/test/test_mode1_regression.c:1-14 --> The file records the verbose diagnostic stream (grid dumps `GRID r00…`, `GRID_REF match=441/441 (100%) HELLO_CONFIRMED`, FP RGB probes) for the three fixed cases (`HELLO`, binary edges, 43-byte ASCII). <!-- anchor: src/jabcode/test/baseline-mode1-output.txt:1-40; test/test_mode1_regression.c:34-39 --> **No code in the tree reads this file** — a repository-wide search finds no consumer; the comparison contract is external (a maintainer diffs a fresh run against it). State that plainly when relying on it: it is evidence, not an executable assertion.

## 13.5 Extension pattern

To add a guard for a new fix, copy the established shape rather than inventing a framework:

1. **One self-contained C file** in `src/jabcode/test/`, deterministic fixtures (fixed seeds; if the bug arrived via JVM consumers, add a `java.util.Random`-compatible generator as `test_cascade_highversion.c` did <!-- anchor: src/jabcode/test/test_cascade_highversion.c:36-53 -->), a header comment stating the bug, the invariant pinned, and the build/run line.
2. **Print `RESULT: PASS`/`RESULT: FAIL`, exit 0/1.** Where feasible, also assert the *pre-fix* behaviour was really broken (the `oob_legacy > 0` device of `test_pn_index.c` <!-- anchor: src/jabcode/test/test_pn_index.c:14, 61 -->) so the test cannot silently become vacuous.
3. **Pick the lightest link tier:** header-only (no link — `test-pn`, `test-symid`); static lib for internal entries (`decodeData` via a local `extern` — `test-eci` <!-- anchor: src/jabcode/test/test_eci.c:21 -->); direct codec-source compilation only when instrumentation demands it (TSan — `test-concurrent` <!-- anchor: src/jabcode/Makefile:174-178 -->).
4. **Add a `.PHONY` target** that builds *and runs* (the `$(CC) …; $(CORE_DIR)/<bin>` two-liner), register it in the `.PHONY` list and in `clean`. <!-- anchor: src/jabcode/Makefile:24, 73-74, 133-135 -->
5. **Wire it into `codec-regression.yml`** — a make target that CI never executes protects nothing (the current `test-cascade-hv` gap, §13.2).
