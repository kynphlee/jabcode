# H_partI_clean_data_failure — Resolved-by-bridge: deprecated legacy path mechanism preserved for re-activation triggers

| Field | Value |
|---|---|
| **Filed**       | 2026-05-26 (WS-5 Council Session 5) |
| **Status**      | **Resolved-by-bridge 2026-06-01** — affected surface (`jabMobileDecodeCamera`) is deprecated; production WithMeta + Option D path is unaffected; legacy test path still exhibits the 23% grid-ref match, retained as a known limitation |
| **Binding**     | Re-activates on Trigger A (legacy SDK consumer migration request) or Trigger B (WithMeta path showing the same pattern) |
| **Owner**       | N/A — closed |
| **Severity**    | Was Medium; now N/A under current routing — all production traffic uses the bridged WithMeta path |
| **Related**     | `swift-java-wrapper/src/c/mobile_bridge.c::jabMobileDecodeCamera` (deprecated), `src/jabcode/decoder.c::decodeMaster` (PartI/PartII fall-through), `src/jabcode/test/test_multi_frame_decode.c` (Phase 1) |

## Resolution (2026-06-01)

The original symptom — HELLO-Nc-2 fabrications from Nc=3 prints via the legacy
`jabMobileDecodeCamera` — is bridged by the strict-mode flag (`g_strict_partII_required`)
that WS-5 introduced and that the production `jabMobileDecodeCameraWithMeta` path sets.

The underlying 23% grid-ref match on synthetic clean input remains a known
limitation of the legacy path. It does not affect the production WithMeta path
(2026-06-01 v7 traces show 99-100% PartI success on camera-captured Nc=3-7
fixtures via the WithMeta path).

Resolution is **closure-by-bridge**, not closure-by-fix: the mechanism exists,
but its impact is bounded by routing all production callers around it. The
entry's investigation checklist remains valid in case Trigger A or B fires.

## The hypothesis

`decodeMasterMetadataPartI` fails on a substantial fraction of clean, synthetically-encoded JABCode bitmaps. Empirically observed in `test_multi_frame_decode.c` Phase 1, where a freshly-encoded JABCode (no camera noise, no compression, deterministic encoder output) decodes via `jabMobileDecodeMultiFrame(frame_count=1)` and the internal grid-reference check reports `GRID_REF match=102/441 (23%) WRONG_BARCODE`.

A grid-reference match of 23% on synthetic clean input is anomalously low. The expected value is ≥95% for noise-free bitmaps. The fact that Phase 1 still produces a passing test outcome is because the existing decoder permissively falls through to default-metadata decoding on PartI failure (`partII_ok = 1` even when PartII was not actually run — `src/jabcode/decoder.c:~1740`), which fabricates a "successful" decode using cached or default state.

## Reproducible repro

Run on any commit at or after `claude/ws-5-with-meta-tdd` (713c713):

```bash
cd <jabcode-repo>/src/jabcode
bash scripts/ws4_9_full_regression.sh
# Look for stderr output of test_multi_frame_decode Phase 1 — the
# "GRID_REF match" line reports the failure rate.
```

The failing bitmap is whatever `jabMobileEncode("HELLO", 5, {color_number=16, symbol_number=1, ecc_level=3, module_size=12})` produces — i.e., an Nc=3 fixture at standard parameters. No specific PNG asset is needed; the test generates the bitmap at runtime.

## Symptom map

| Symptom | Where observed | Status |
|---|---|---|
| HELLO-Nc-2 fabrications from Nc=3 prints | Galaxy S25 traces 161346, 181712 | Bridged by Option D on WithMeta path |
| `test_multi_frame_decode` Phase 1 produces 23% grid-ref match but still "passes" | Desktop regression | Test contract is buggy — see below |
| Decoder rate collapse when strict-mode applied to legacy path | All three full-fix attempts in WS-5 (partII_ok=0, always-run-PartII, caller-strict on legacy) | Confirms permissive fall-through is structurally load-bearing |
| `result=skipped Nc=2 ok=1` markers in production logcat | All WS-5 camera traces | Visible signature of the fall-through firing |

## Test contract concern (Heisenberg flag)

`test_multi_frame_decode.c` Phase 1 was authored under the (unexamined) assumption that the permissive fall-through is correct behavior. If the 23% grid-ref match is genuinely wrong, the Phase 1 test is asserting that fabricated decodes-via-fall-through are correct outcomes. The test should NOT be modified in this branch — Lazarus's preservation principle applies until Option C lands. But the test's expectation must be re-examined as part of any Option C investigation.

A `#pragma GCC diagnostic` block in `swift-java-wrapper/src/c/mobile_bridge.c` (multi-frame fast-path at `frame_count==1`) suppresses the deprecation warning for the internal legacy call. That suppression is the architectural breadcrumb pointing back here.

## Investigation checklist (cold-pickup)

When this hypothesis activates, an investigator should be able to start cold from this entry. Suggested progression:

1. **Reproduce the 23% grid-ref match** — run the regression suite, capture the stderr output of `test_multi_frame_decode` Phase 1. Confirm the rate is still in the same range (could have shifted up or down since 2026-05-26).
2. **Instrument PartI exit paths** — add per-failure-mode counters in `decodeMasterMetadataPartI` to identify WHICH check is failing on the synthetic bitmap (LDPC validation, structural bit count, palette parse, anchor detection, etc.).
3. **Compare encoder-emitted vs decoder-expected** — for the failing bitmap, dump the encoder's intended metadata bits and compare against what PartI reads. A mismatch points to encoder/decoder version drift or a coordinate-space bug.
4. **Bisect for regression introduction** — `git log --oneline src/jabcode/decoder.c` and identify when PartI's failure rate crossed from acceptable to 77%. Check whether the regression aligns with any of the WS-3, WS-4, or WS-5 changes.
5. **Cross-check against `test_roundtrip_all_nc`** — that test uses `decodeJABCode` directly (not the camera pipeline) and passes on Nc=0..6. The divergence between "decodeJABCode succeeds" and "camera-pipeline decodeJABCode fails on the same bitmap" isolates the failure to camera-pipeline-specific bitmap construction.

## Triggers (when this hypothesis activates)

This hypothesis investigation should be picked up when ANY of the following occur:

- **Trigger A**: A legacy-path SDK consumer reports a mis-identification AND cannot or will not migrate to `jabMobileDecodeCameraWithMeta`.
- **Trigger B**: A similar PartI failure pattern is observed on the WithMeta path itself (i.e., the strict mode wiring stops being sufficient).
- **Trigger C**: An engineer encounters unallocated capacity and wants to take ownership of this hypothesis.
- **Trigger D**: A future decoder change inadvertently breaks `test_multi_frame_decode` Phase 1, forcing a re-examination of the permissive fall-through.

## Why this is filed (not scheduled)

Per WS-5 Council Session 5 deliberation:

> Cassandra's Advocate: "Scheduled commitments create commitment debt. If we say 'C investigation by Q3', and Q3 arrives with three higher-priority bugs, we either (a) defer the investigation and damage council credibility, or (b) honor the schedule and damage product priorities."

Triggered binding (with a named owner taken at trigger time) avoids commitment debt while preserving the investigation as a tracked open problem. The reproducible repro and investigation checklist above ensure cold pickup is possible.

## Cross-references

- WS-5 Bayesian Council Sessions 4 and 5 — full deliberation on Option D vs alternatives
- `swift-java-wrapper/include/mobile_bridge.h` — `@deprecated` note on `jabMobileDecodeCamera` references this entry
- `swift-java-wrapper/src/c/mobile_bridge.c` — `#pragma` suppression at multi-frame fast-path references this entry
- `src/jabcode/decoder.c::decodeMaster` — the `g_strict_partII_required` flag's existence is the bridge
- `src/jabcode/test/test_jab_mobile_with_meta.c` — TDD contract that locks the WithMeta side of the bridge
