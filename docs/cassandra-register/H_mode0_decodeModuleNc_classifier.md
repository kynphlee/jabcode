# H_mode0_decodeModuleNc_classifier — Open root-cause hypothesis: `decodeModuleNc` misclassifies Mode 0 W pixels as Y under residual camera cast

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-05-31 (downstream of PR #46 H_mode0_partI fix shipping)                                       |
| **Status**   | Open — CONFIRMED at mechanism layer in the 2026-05-31 v5 stacked-fix trace                         |
| **Binding**  | Triggered (not scheduled)                                                                          |
| **Owner**    | Unassigned (claimed on trigger)                                                                    |
| **Severity** | Medium — Mode 0 end-to-end scan still at 0% despite PartI validity-check fix                       |
| **Related**  | `H_mode0_partI_decode_failure.md` (which this entry replaces as the active Mode 0 blocker), `H_nc2_decode_failure.md` |

## The hypothesis

For camera-captured Mode 0 (Nc=0, monochrome) fixtures, `decodeMasterMetadataPartI` correctly enters the Mode 0 validity path (`g_mode0_decode=1`, validity set `{K=0, W=7}`) per PR #46. But the upstream `decodeModuleNc` classifier in `decoder.c:783` is consistently producing `rgb=6` (Y) for what should be W (R+G+B all high) pixels under the residual camera cast that survives the manual AWB override.

The validity check correctly rejects rgb=6 in Mode 0 (since Mode 0 metadata only uses K and W), producing FAIL_mc with the diagnostic marker:

```
[PartI_DIAG] FAIL_STAGE=module_color module[0] rgb=6 (mode0=1 valid_set={0,7})
```

The bug is therefore upstream of PartI's validity check. The classifier function itself is not Mode 0-aware — it applies the same K/C/Y/M/R/G/B/W discrimination rule regardless of whether the metadata palette is `{K, C, Y}` (color modes) or `{K, W}` (Mode 0).

## Empirical anchor (2026-05-31 v5 stacked-fix trace)

| Marker | Count | Notes |
|---|---|---|
| `g_mode0_decode=1` firings | 48 | Mode 0 trigger working correctly |
| `g_mode0_decode=0` firings | 0 | Trigger is stable on this fixture |
| PartI_DIAG BEGIN | 40 | Mode 0 fixtures reach PartI |
| PartI_DIAG SUCCESS | 0 | All PartI attempts fail |
| FAIL_STAGE=module_color | 33 | All failures at module_color stage |
| FAIL_STAGE=pair_bits | 7 | Some attempts pass module_color (with rgb=0 K reads); fail at downstream pair_bits because Mode 0's bit-pack scheme differs from K/C/Y modes |

Reference trace: `jabauth-android/diagnostic-app/logs/trace-20260531_150532-nc0.logcat`

## Mechanism in `decodeModuleNc`

The function at `src/jabcode/decoder.c:783` is a hybrid classifier:

```c
// Exact-match path (tolerance=80):
if (rgb[0] < 80 && rgb[1] < 80 && rgb[2] < 80)                                    return 0;  // K
if (rgb[0] < 80 && rgb[1] > 175 && rgb[2] > 175)                                  return 3;  // C
if (rgb[0] > 175 && rgb[1] > 175 && rgb[2] < y_b_tolerance /* 255 per PR #47 */)  return 6;  // Y
// Fallback: relative-threshold via getMinMax + std
```

For a W pixel (R+G+B all high) under the camera's cast, the values are roughly (R=230-255, G=230-255, B=230-255). This MATCHES the Y exact-check (R>175 AND G>175 AND B<255-true-or-clamped), returning rgb=6.

**The classifier has no concept of Mode 0.** It correctly classifies W in COLOR-MODE contexts (where W shouldn't appear at metadata positions; it appears in data modules, which use `decodeModuleHD` not `decodeModuleNc`). But for Mode 0 metadata, W is a legitimate metadata color, and the classifier should produce rgb=7 (W) instead of rgb=6 (Y).

## Fix specification

Make `decodeModuleNc` aware of `g_mode0_decode` and use a luminance-based discrimination when Mode 0 is active:

```c
jab_byte decodeModuleNc(jab_byte* rgb)
{
    if (g_mode0_decode)
    {
        // Mode 0 metadata uses only K (dark) and W (bright). The discriminator
        // is luminance, not chroma. A pixel with all three channels low is K;
        // all three high is W. Threshold roughly halfway between (e.g., 127)
        // separates them; tune empirically against Mode 0 fixture corpus.
        jab_int32 luminance = (rgb[0] + rgb[1] + rgb[2]) / 3;
        return (luminance < 127) ? 0 : 7;  // K vs W
    }
    // Existing color-mode hybrid classifier below — unchanged
    // ...
}
```

The change is one branch added at the function entry, gated on `g_mode0_decode`. Color modes are bit-for-bit unaffected. Mode 0 produces only `{0, 7}` outputs.

**Note**: this fix targets the module_color stage only. The downstream pair_bits and LDPC stages may still fail for Mode 0 because the Mode 0 metadata bit-pack scheme is documented to differ from color modes. The 7 FAIL_pb instances in today's trace already confirm this. Closing nc=0 to deployable requires BOTH this classifier fix AND a Mode 0-aware pair_bits/LDPC path (which is the H_mode0_partI_decode_failure Step 2 in PR #35's spec — encoder-side cross-reference).

## Triggers

- **Trigger A**: a customer or product feature requires Mode 0 / monochrome JABCode support
- **Trigger B**: the `H_mode0_partI_decode_failure` Step 2 (encoder-side metadata layout cross-reference) is being investigated — closing this classifier bug as part of that workstream is the efficient path
- **Trigger C**: an engineer has capacity to ship the one-branch fix and validate against the existing Mode 0 fixture

## Why this is filed (not scheduled)

Per the established Cassandra register pattern: Mode 0 may be product-irrelevant if SDK consumers don't deploy monochrome codes. The trigger pattern lets the investigation activate when a real customer need surfaces, while preserving the mechanism-layer findings for cold pickup.

## Cross-references

- `H_mode0_partI_decode_failure.md` — closed at PartI validity-check stage; this entry continues the Mode 0 closure work at the classifier-stage
- `H_nc2_decode_failure.md` — sibling 2026-05-31 deferral with a different mechanism (ISP-level color correction needed)
- `src/jabcode/decoder.c::decodeModuleNc` (line 783) — the function to modify
- `src/jabcode/detector.c::g_mode0_decode` (line 88, now non-static per PR #46) — the gate flag
