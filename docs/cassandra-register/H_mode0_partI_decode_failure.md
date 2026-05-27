# H_mode0_partI_decode_failure — Open root-cause hypothesis

| Field        | Value                                                                                                            |
| ------------ | ---------------------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-05-27 (downstream of the Mode 0 chroma-tolerance trigger fix)                                                |
| **Status**   | Open — newly surfaced once the trigger fix unblocked FP detection                                                 |
| **Binding**  | Triggered (not scheduled)                                                                                         |
| **Owner**    | Unassigned (claimed on trigger)                                                                                   |
| **Severity** | Medium — Mode 0 end-to-end decode does not work on real camera input, even after the trigger fix.                  |
| **Related**  | `H_partI_clean_data_failure.md` (sibling); `src/jabcode/decoder.c::decodeMasterMetadataPartI`; WS-0 unfinished decoder work referenced in `docs/jabcode-all-nc-plan/00b-mode-0-monochrome.md` |

## The hypothesis

For camera-captured Mode 0 (Nc=0, monochrome) bitmaps, `decodeMasterMetadataPartI` is failing to recover the LDPC-encoded structural metadata, even when:

- The Mode 0 trigger correctly evaluates `g_mode0_decode = 1` (mean-chroma discrimination unblocked this — see the chroma-tolerance trigger PR)
- Finder-pattern detection succeeds (DETECT SUCCESS marker fires with 4 valid FP corners)
- Module size is in the expected 26-40 ADU range for typical scanning distance

The decode then enters `decodeMaster` → `decodeMasterMetadataPartI`. PartI returns failure. Option D's strict-mode flag (from WS-5) correctly refuses to fabricate via the legacy permissive fall-through, so `partII_ok = 0`, the loop tries each Nc_FALLBACK value, all fail, and the outer call returns NULL with `FAIL_ATTR status=1 (FP found, slave-decode failed)`.

## Reproducible repro

On any commit at or after the Mode 0 chroma-tolerance trigger fix:

1. Display `jabauth-android/diagnostic-app/images/full-spectrum/nc0-2c-20260521.png` on a screen (any brightness)
2. Scan with the diagnostic app on Galaxy S25 (or equivalent Android 15 device)
3. Capture logcat

Observed: `DIAG_MODE0_DETECT g_mode0_decode=1` on most frames, `DETECT SUCCESS` fires, but `DIAG_PARTII_RESULT result=skipped Nc=0 ok=0 (strict)` follows on every frame. No `DECODE_OK` markers.

Reference trace (the one that first revealed this gap):
`jabauth-android/diagnostic-app/logs/tolerance4-test-20260527_102707.logcat` — 98 DETECT SUCCESS / 95 FAIL_ATTR status=1 / 0 DECODE_OK.

## Symptom snapshot from the reference trace

```
DECODE_OK             : 0
DETECT SUCCESS        : 98     ← FP detection works
FAIL_ATTR status=0    : 7      ← rarely "no FP found"
FAIL_ATTR status=1    : 95     ← dominantly "FP found, slave-decode failed"
DIAG_MODE0_DETECT     : 103
  g_mode0_decode=1    : 102    ← trigger correctly fires
  g_mode0_decode=0    : 1      ← single noise-outlier frame
DIAG_PARTII_RESULT for Nc=0 : all 'skipped Nc=0 ok=0 (strict)'
```

The strict-skipped markers prove that for every frame where `g_mode0_decode=1` activated, PartI returned failure and Option D correctly refused to fabricate.

## Suspected failure surfaces (investigation candidates)

1. **`decodeMasterMetadataPartI` color-keyed bit extraction**: PartI reads metadata bits from positions adjacent to the FPs. If the bit-classification function assumes color modes (uses RGB-channel-specific thresholds or color-palette lookup), it will fail on monochrome modules that have no color signal — only luminance.

2. **LDPC parameter selection for Nc=0**: the LDPC decode in PartI uses `(wc, wr)` parameters that are derived from the metadata bits. If the encoder writes those bits monochrome-style but the decoder reads them assuming Nc≥1 palette, the parameter recovery fails.

3. **Module-classification (`decodeModuleHD`) on the structural bits**: if the per-module classification short-circuits exist in `findMasterSymbol` (which we know now do — the trigger fix unblocked them) but NOT in `decodeMasterMetadataPartI`, then PartI is still using color-keyed classification on monochrome data.

4. **Mode 0 metadata bit positions or encoding**: the encoder commits (jabcode `05a1acc`, COA-crypto `3c083e9`) added Mode 0 encoder support. The decoder side may not be reading from the same positions or with the same scheme.

## Investigation checklist (cold-pickup)

When this hypothesis activates, an investigator should be able to start from this entry:

1. Reproduce on the same device + fixture. Confirm the symptom snapshot still holds.
2. Add per-stage instrumentation to `decodeMasterMetadataPartI`:
   - Log the raw bit values read from the metadata positions
   - Log the LDPC parameters extracted (or the failure mode)
   - Compare against an encoder-side dump of what those bits SHOULD be
3. Read the WS-0 documentation: `docs/jabcode-all-nc-plan/00b-mode-0-monochrome.md` and any per-step status notes. Identify which decoder-side steps were planned but not implemented.
4. Check `g_mode0_decode` short-circuits in `decodeMaster` and `decodeMasterMetadataPartI` (currently the 8 uses live in `findMasterSymbol`; there may be NO Mode 0 short-circuits in PartI yet, which would explain the consistent failure).
5. Cross-check against the working synthetic-decode path: `decodeJABCodeSynthetic` may have its own Nc=0 handling. If it does AND the camera-path PartI doesn't, the gap is identified.

## Triggers (when this hypothesis activates)

- **Trigger A**: Mode 0 (Nc=0) capability is needed by a product feature (authentication SDK consumers asking for Nc=0 support; printability concerns for monochrome use cases).
- **Trigger B**: A separate decoder change in PartI introduces a regression that brings Mode 0 closer to working — investigation activates to confirm closure.
- **Trigger C**: An engineer has unallocated capacity and picks this up.

## Why this is filed (not scheduled)

Per the same Cassandra register pattern established by `H_partI_clean_data_failure.md` (WS-5 Council Session 5 deliberation): triggered binding avoids commitment debt while preserving the investigation as a tracked open problem. The reference trace + investigation checklist make cold pickup possible.

## Cross-references

- `docs/cassandra-register/H_partI_clean_data_failure.md` — sibling entry for the broader "PartI is brittle on real camera input" puzzle. This Mode 0 case is a specific manifestation, not the same root cause.
- `docs/jabcode-all-nc-plan/00b-mode-0-monochrome.md` — original WS-0 plan; identifies the encoder-side as complete and the decoder-side as the remaining work
- `project_ws0_mode0_status.md` (memory) — captures the encoder vs decoder split
- `project_jabcode_screen_vs_print_physics.md` (memory) — explains why Mode 0 SHOULD be the simplest case (only intensity discrimination needed); makes the PartI failure all the more striking
- `src/jabcode/detector.c::detectMaster` (lines ~3615) — the trigger that now correctly fires for Mode 0
- `src/jabcode/decoder.c::decodeMaster` — where the strict-mode-vs-permissive PartI fall-through decision lives
