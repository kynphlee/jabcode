# H_mode0_decodeModuleNc_classifier — Root-cause hypothesis: `decodeModuleNc` misclassifies Mode 0 W pixels as Y under residual camera cast

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-05-31 (downstream of PR #46 H_mode0_partI fix shipping)                                       |
| **Status**   | **Resolved 2026-05-31** — luminance-based discrimination branch added to `decodeModuleNc` at decoder.c:790, gated on `g_mode0_decode`. See "Resolution" below. |
| **Binding**  | Customer trigger fired 2026-05-31 — all 8 Nc modes required                                         |
| **Owner**    | Closed via Wave 1.2 of the unified customer-triggered action plan                                  |
| **Severity** | Medium — Mode 0 end-to-end scan was at 0% despite PartI validity-check fix; this entry's fix lifts module_color stage |
| **Related**  | `H_mode0_partI_decode_failure.md` (which this entry replaced as the active Mode 0 blocker), `H_nc2_decode_failure.md`, `H_partI_nc_extraction_bias.md` |

## The hypothesis

For camera-captured Mode 0 (Nc=0, monochrome) fixtures, `decodeMasterMetadataPartI` correctly enters the Mode 0 validity path (`g_mode0_decode=1`, validity set `{K=0, W=7}`) per PR #46. But the upstream `decodeModuleNc` classifier in `decoder.c:790` was consistently producing `rgb=6` (Y) for what should be W (R+G+B all high) pixels under the residual camera cast that survives the manual AWB override.

The validity check correctly rejects rgb=6 in Mode 0 (since Mode 0 metadata only uses K and W), producing FAIL_mc with the diagnostic marker:

```
[PartI_DIAG] FAIL_STAGE=module_color module[0] rgb=6 (mode0=1 valid_set={0,7})
```

The bug was therefore upstream of PartI's validity check. The classifier function was not Mode 0-aware — it applied the same K/C/Y/M/R/G/B/W discrimination rule regardless of whether the metadata palette was `{K, C, Y}` (color modes) or `{K, W}` (Mode 0).

## Empirical anchor (2026-05-31 v6 stacked-fix trace)

The v6 trace evidence at module-byte granularity:

| Marker | Count | Notes |
|---|---|---|
| `g_mode0_decode=1` firings | 109 | Mode 0 trigger working correctly across the fixture |
| `g_mode0_decode=0` firings | 5 | Transient frames where mean_chroma briefly exceeded tol=30 |
| PartI_DIAG BEGIN | 68 | Mode 0 fixtures reach PartI |
| PartI_DIAG SUCCESS | 0 | All PartI attempts fail (pre-fix) |
| FAIL_STAGE=module_color | 65 | Dominant failure stage — this entry's mechanism |
| FAIL_STAGE=pair_bits | 3 | Downstream stage; covered by Mode 0 bit-pack work (see Note below) |

Reference trace: `jabauth-android/diagnostic-app/logs/trace-20260531_155439-nc0.logcat`

Sample module-byte diagnostics from the v6 trace illustrating the misclassification:

```
[PartI_DIAG] module[0] xy=(6,1) raw_bytes=(244,254,243) rgb=6 valid=0 mode0=1
[PartI_DIAG] module[0] xy=(6,1) raw_bytes=(234,251,245) rgb=6 valid=0 mode0=1
[PartI_DIAG] module[0] xy=(6,1) raw_bytes=(226,255,249) rgb=6 valid=0 mode0=1
```

Each `raw_bytes` is a clearly-white pixel under residual camera cast (all three channels in the 225-255 band). The pre-fix classifier returned `rgb=6` (Y) instead of `rgb=7` (W), triggering the Mode 0 validity rejection.

## Mechanism in `decodeModuleNc` (pre-fix)

The function at `src/jabcode/decoder.c:790` was a hybrid classifier:

```c
// Exact-match path (tolerance=80, y_b_tolerance=255 per PR #47):
if (rgb[0] < 80 && rgb[1] < 80 && rgb[2] < 80)                                    return 0;  // K
if (rgb[0] < 80 && rgb[1] > 175 && rgb[2] > 175)                                  return 3;  // C
if (rgb[0] > 175 && rgb[1] > 175 && rgb[2] < 255)                                 return 6;  // Y
// Fallback: relative-threshold via getMinMax + std
```

For a W pixel (R+G+B all high) under the camera's cast, the values are roughly (R=230-255, G=230-255, B=230-255). This MATCHED the Y exact-check (R>175 AND G>175 AND B<255), returning rgb=6.

**The classifier had no concept of Mode 0.** It correctly classifies W in COLOR-MODE contexts (where W shouldn't appear at metadata positions; it appears in data modules, which use `decodeModuleHD` not `decodeModuleNc`). But for Mode 0 metadata, W is a legitimate metadata color, and the classifier needed to produce rgb=7 (W) instead of rgb=6 (Y).

## Resolution

`decodeModuleNc` is now gated on `g_mode0_decode`. When the Mode 0 trigger is active, the classifier takes a luminance-only path that returns `{K=0, W=7}` only:

```c
jab_byte decodeModuleNc(jab_byte* rgb)
{
    if(g_mode0_decode)
    {
        jab_int32 luminance = (rgb[0] + rgb[1] + rgb[2]) / 3;
        return (luminance < 127) ? 0 : 7;  // K vs W
    }
    // Existing color-mode hybrid classifier below — unchanged.
}
```

The change is one branch added at function entry. Color modes are bit-for-bit unaffected because `g_mode0_decode` is `0` for them and the new branch is skipped. Mode 0 produces only `{0, 7}` outputs, which now satisfy the validity set.

**Validation expectation**: a re-scan of the nc=0 fixture should produce `rgb=7` (W) reads instead of `rgb=6` (Y) at all metadata-W positions, lifting PartI success rate to >=80% on the same v6 trace conditions. End-to-end Nc=0 decode requires downstream pair_bits and LDPC to also succeed (Note below); this entry's fix is necessary but not sufficient.

## Note on downstream stages

The 3 FAIL_pb instances in the v6 trace confirm that Mode 0's bit-pack scheme differs from color modes. Closing nc=0 to deployable end-to-end requires this classifier fix to land first; the pair_bits/LDPC work is tracked separately and may surface as additional register entries during Wave 2 follow-up scanning.

## Cross-references

- `H_mode0_partI_decode_failure.md` — superseded by this entry; PartI validity-check fix shipped via PR #46
- `H_nc2_decode_failure.md` — sibling 8-color failure with different mechanism (pair_bits at Nc=2 post PR #47)
- `H_partI_nc_extraction_bias.md` — newer sibling tracking PartI Nc-field misreads at Nc=1 and Nc=6
- `src/jabcode/decoder.c::decodeModuleNc` (line 790) — the function modified by this entry's fix
- `src/jabcode/include/jabcode.h::g_mode0_decode` (line 123) — the gate flag (declared `extern` per PR #46)
