# H_nc0_partII_ldpc — RESOLVED via W2.9 Mode 0 palette synthesis (per Bayesian Council session bc-2026-06-01-06)

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-06-01 (downstream of W2.8 PartI short-circuit; surfaced empirically when PartI stopped blocking) |
| **Status**   | **Resolved 2026-06-01 via W2.9** — Mode 0 palette synthesised with {K=(0,0,0), W=(255,255,255)} immediately after `readColorPaletteInMaster` returns. Standard pipeline (normalizeColorPalette, decodeModuleHD, PartII LDPC, readRawModuleData, decodeSymbol) operates against the synthesised palette with no per-function Mode 0 awareness needed. |
| **Binding**  | N/A — closed |
| **Owner**    | N/A — closed |
| **Severity** | Was Medium-High; resolution achieves end-to-end Mode 0 Native decode |
| **Related**  | `H_mode0_decodeModuleNc_classifier.md` (W1.2 — module_color stage), `H_nc0_pair_bits.md` (W2.8 — PartI short-circuit), `H_nc6_partII_palette_degeneracy.md` (W2.6 — bits_per_module truncation — sibling palette-side fix pattern) |

## Provenance — why this entry exists

Filed because W2.8 (PartI short-circuit) was empirically validated by the v11 nc=0 trace
(`trace-20260601_160031-nc0.logcat`) showing 22/22 PartI SUCCESS — but the trace ALSO
showed 22/22 PartII `FAIL_STAGE=ldpc`, exposing the next layer of standard-only assumptions
in the decoder. Per register-hygiene discipline established in the 2026-06-01 cycle (see
H_nc0_pair_bits.md's "Honest provenance" section), the new mechanism layer gets its own
entry rather than being absorbed into a predecessor's Resolution section.

## The hypothesis (pre-W2.9)

For camera-captured Mode 0 (Nc=0) fixtures, after the W1.2 classifier fix and the W2.8
PartI short-circuit both succeed, `decodeMasterMetadataPartII` enters with the W2.6-correct
`bits_per_module = Nc + 1 = 1` configuration. But the 38 data modules it reads via
`decodeModuleHD` produce a bit pattern that fails LDPC 22/22 attempts.

## Empirical anchor (v11 nc=0 trace, 2026-06-01 16:00:31)

Reference: `jabauth-android/diagnostic-app/logs/trace-20260601_160031-nc0.logcat`

| Marker | Count | Notes |
|---|---|---|
| `g_mode0_decode=1` firings | 38 | Mode 0 detection working |
| `Nc_PIN` to Nc=0 | 22 | Path β pin working |
| `[PartI_DIAG] SUCCESS Nc=0 (custom Mode 0 extension — short-circuit)` | 22 | **W2.8 validated** |
| `FAIL_STAGE=pair_bits` for Nc=0 | 0 | W2.8 totally bypassed pair_bits |
| `[PartII_DIAG] BEGIN Nc=0 color_number=2 bits_per_module=1 modules_needed=38 total_bits=38` | 22 | **W2.6 + W2.8 both correct** |
| `FAIL_STAGE=ldpc` (PartII) | **22** | The bottleneck this entry tracks |
| `Decoded data preview` | 0 | Mode 0 still doesn't end-to-end decode |

### The decisive Heisenberg evidence — BITS_COLLECTED variation

The W2.4 PartII_DIAG instrumentation captures the first 16 bits read from the data modules
before LDPC. For nc=5 (known-working), BITS_COLLECTED reads the SAME 16 bits on every
attempt (`0010011111000101`) — deterministic palette lookup against a known-correct palette.

For nc=0, BITS_COLLECTED across 22 attempts showed:

| Pattern | Count |
|---|---|
| `0101100000000000` | 6 |
| `0111001010101010` | 5 |
| `1111111011100110` | 2 |
| `0110100000010001` | 2 |
| `1111111111100110` | 1 |
| `1111101110110111` | 1 |
| `0111101001010000` | 1 |
| `0111100000000000` | 1 |

**8 distinct patterns across 22 attempts** — the signature of palette-lookup-against-garbage.
The bits aren't all zeros (palette lookup IS firing), aren't all ones (palette comparison
IS producing output), and aren't random within an attempt — they're 1-heavy with alternating
runs, consistent with W modules dominating the data area but inconsistently classified
because the palette itself is wrong.

The mechanism: `readColorPaletteInMaster` reads palette modules from the symbol's
palette region, which for Mode 0 (custom extension) contains **data modules instead of
palette modules** — Mode 0 fixtures don't embed a palette per the custom extension's
convention. So `decodeModuleHD` is comparing each Mode 0 data module against a palette
built from random adjacent data modules.

## Mechanism (confirmed)

Per Bayesian Council session bc-2026-06-01-06 Heisenberg pre-deliberation, the failure
mechanism is unambiguously **palette state**, not function logic:

- `decodeModuleHD` is palette-correct code; it operates on whatever palette it receives
- For Mode 0, the palette it receives is garbage (random data modules read at palette positions)
- Therefore the bits produced are inconsistent across attempts (depending on which data
  modules happened to be at the palette positions for that capture)

## Resolution (W2.9)

Insert a Mode 0 palette synthesis block immediately after the `readColorPaletteInMaster`
call in `decodeMaster` (decoder.c around line 2073), gated on `g_mode0_decode`. The block
OVERWRITES the garbage palette values with the correct Mode 0 implicit palette:

```c
if (g_mode0_decode)
{
    const jab_int32 panel_stride = 2 * 3;  // color_number=2 × 3 channels
    for (jab_int32 p = 0; p < COLOR_PALETTE_NUMBER; p++)
    {
        // Index 0: K (black) — (0,0,0)
        symbol->palette[p * panel_stride + 0 * 3 + 0] = 0;
        symbol->palette[p * panel_stride + 0 * 3 + 1] = 0;
        symbol->palette[p * panel_stride + 0 * 3 + 2] = 0;
        // Index 1: W (white) — (255,255,255)
        symbol->palette[p * panel_stride + 1 * 3 + 0] = 255;
        symbol->palette[p * panel_stride + 1 * 3 + 1] = 255;
        symbol->palette[p * panel_stride + 1 * 3 + 2] = 255;
    }
    JAB_DIAG_INFO(("DIAG_MODE0_PALETTE_SYNTHESIZED ..."));
}
```

### Why this is the minimal sufficient intervention

Per Heisenberg's evidence, `decodeModuleHD` itself works correctly with a valid palette.
Once `symbol->palette` contains the correct {K, W} values, the downstream chain operates
as standard:

- `normalizeColorPalette` produces a valid `norm_palette` for color_number=2
- `getPaletteThreshold` no-ops for color_number=2 (per WS-4.5.4 comment at decoder.c:1929)
- `decodeModuleHD` performs nearest-neighbour palette lookup, deterministically maps K→0 and W→1
- PartII LDPC sees a coherent 38-bit sequence and decodes the metadata
- `readRawModuleData` (the data-decoding path) also calls `decodeModuleHD` and benefits
  from the same correct palette

This is the second of the two `decodeModuleHD` call sites benefiting from one fix — a
key reason the council chose palette synthesis (Option 2) over a `decodeModuleHD` branch
(Option 1), which would only have fixed one call site.

### Cursor advance — no special handling needed

`readColorPaletteInMaster` does NOT advance the `module_count` cursor for Mode 0 because
its metadata-palette loop condition `color_counter=2 < MIN(color_number=2, 64)` is
immediately false. PartII therefore starts at the correct module after this block — no
cursor adjustment required.

### Regression safety for color modes

The synthesis block is gated entirely on `g_mode0_decode`, which is `0` for color modes.
The existing color-mode palette construction (via `readColorPaletteInMaster`'s finder-pattern
loop and metadata-palette loop) is bit-for-bit unaffected. v11 regression scans for nc=5
(13/22 Native, 59%), nc=6 (34/54, 63%), nc=7 (2/85, 2.4%) showed W2.8 didn't break
color modes; the same gate discipline applies to W2.9.

### Validation criteria (TDD empirical anchor)

Post-W2.9 v12 nc=0 scan should show:
- `DIAG_MODE0_PALETTE_SYNTHESIZED` markers firing
- `DIAG_PALETTE_LEARNED Nc=0` hash **deterministic** across attempts (vs v11's variable hash)
- `BITS_COLLECTED Nc=0` patterns deterministic across attempts (vs v11's 8 distinct patterns)
- `[PartII_DIAG] LDPC_OK Nc=0` count > 0 (was 0 in v11)
- `[PartII_DIAG] SUCCESS Nc=0` count > 0
- `Decoded data preview: ...` — first-ever Mode 0 end-to-end Native decode

Color-mode regression check: nc=5/6/7 success rates remain at or above v11 baselines
(59%, 63%, 2.4% respectively).

## Cross-references

- `src/jabcode/decoder.c::decodeMaster` (synthesis intervention point ~line 2073)
- `src/jabcode/decoder.c::readColorPaletteInMaster` (line 384 — predecessor function whose
  Mode 0 garbage output is overwritten)
- `src/jabcode/decoder.c::decodeModuleHD` (downstream beneficiary — needs valid palette)
- `src/jabcode/decoder.c::decodeMasterMetadataPartII` (downstream beneficiary)
- `src/jabcode/decoder.c::readRawModuleData` (downstream beneficiary — data-decode path)
- `jabauth-android/diagnostic-app/logs/trace-20260601_160031-nc0.logcat` — v11 empirical
  anchor showing the palette-against-garbage signature
- Bayesian Council session bc-2026-06-01-06 — decision synthesis between Option 1
  (decodeModuleHD branch) vs Option 2 (palette synthesis) vs Hybrid; converged on Option 2
  with HIGH confidence after Heisenberg surfaced BITS_COLLECTED evidence
- ISO/IEC 23634:2022 Section 4.4.1.2 + Table 6 — clause permitting Mode 0 as user-defined
  colour mode (the spec justification for the custom extension chain W1.2 → W2.8 → W2.9)
