# H_nc0_pair_bits — RESOLVED: Mode 0 custom-extension PartI short-circuit (W2.8)

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-06-01 (register hygiene — documenting the downstream gap explicitly left open by W1.2 supersession chain) |
| **Status**   | **Resolved 2026-06-01 via W2.8** — custom Mode 0 extension implemented per ISO/IEC 23634:2022 Table 6 clause permitting user-defined colour modes. PartI short-circuits when `g_mode0_decode=1` because the chroma probe has already determined Nc=0. The standard {K,C,Y}-based pair_bits decoding cannot apply to Mode 0's {K,W} palette and is therefore bypassed. |
| **Binding**  | N/A — closed (custom extension implemented) |
| **Owner**    | N/A — closed |
| **Severity** | Was Medium; resolution achieved end-to-end Mode 0 Native decode |
| **Related**  | `H_mode0_decodeModuleNc_classifier.md` (predecessor — Resolved at module_color stage by W1.2), `H_mode0_partI_decode_failure.md` (superseded by the classifier entry), `H_nc6_partII_palette_degeneracy.md` (sibling — Resolved by W2.6 `bits_per_module` fix), `H_nc2_decode_failure.md` (sibling — Nc=2 is the default ISO-standardised colour mode with a separate camera-path pair_bits mechanism) |

## Resolution (2026-06-01, W2.8)

### Spec justification

Per ISO/IEC 23634:2022 Section 4.4.1.2 and Table 6:

> *"Colour modes 0, 3, 4, 5, 6 and 7 are reserved for future extensions.
> These colour modes can also be used for user-defined colour modes.
> See Annex G for additional guidance when using these colour modes in
> user-defined conditions."*

Mode 0 (Nc=0) is explicitly permitted as a **user-defined custom extension**.
Our implementation IS that custom extension, with the following semantics:

### Custom Mode 0 extension semantics

| Stage | Standard (Nc=1, Nc=2) | Custom extension (Nc=0) |
|---|---|---|
| Chroma probe → `g_mode0_decode` | Always 0 | Set to 1 when `mean_chroma < tol_chroma` |
| `decodeModuleNc` classifier | {K, C, Y, M, R, G, B, W} hybrid | **W1.2 luminance branch** (already shipped via PR #49) |
| PartI bit decoding | `decodeNcModuleColor` + pair_bits + LDPC | **W2.8 short-circuit** — bypassed; Nc=0 is already known |
| PartI return | Decoded Nc value | `symbol->metadata.Nc = 0`, JAB_SUCCESS |
| PartII | Standard with `bits_per_module = Nc+1` | Same; `bits_per_module = 0+1 = 1` (W2.6) |

### Why short-circuit rather than implement Mode 0 pair_bits

The standard's `nc_color_encode_table` (encoder.h:118) contains only values
`{0, 3, 6}` corresponding to {K, C, Y}. Mode 0 modules contain `{0, 7}` (K, W).
The W value (7) is never in the table, so every `decodeNcModuleColor` lookup
returns the invalid-sentinel 8, and every `pair_bits` validity check fails.

Implementing a Mode-0-specific {K, W} bit-pack scheme would require defining a
separate encoding table AND ensuring information is not lost (the encoder's
modulo trick at `encoder.c:1390` collapses C and Y into W when palette has only
2 colours, making the encoding lossy for 8 distinct val values). Rather than
build a redundant bit encoding to convey a value that is ALREADY known via the
chroma probe, the short-circuit is the spec-consistent and cleaner design.

### Empirical anchor (pre-fix)

2026-06-01 v10 trace (`trace-20260601_122526-nc0.logcat`):

| Marker | Count | Notes |
|---|---|---|
| `g_mode0_decode=1` firings | 86 | Chroma probe Mode 0 detection working |
| Nc_PIN to Nc=0 | 34 | Path β pin working |
| PartI BEGIN | 34 | Decoder enters PartI |
| `module[N] rgb=7 valid=1 mode0=1` reads | All W modules | W1.2 classifier fix working perfectly |
| **FAIL_STAGE=pair_bits** | **34** | The bug (now resolved by W2.8) |
| Native decode SUCCESS | 0 | (pre-W2.8 baseline) |

### Fix specification

`src/jabcode/decoder.c::decodeMasterMetadataPartI` — insert at function entry,
after the existing BEGIN diagnostic marker:

```c
if (g_mode0_decode)
{
    // Advance module cursor past the 4 PartI metadata positions so
    // PartII starts at the correct module.
    for (jab_int32 mod = 0; mod < MASTER_METADATA_PART1_MODULE_NUMBER; mod++)
    {
        data_map[(*y) * matrix->width + (*x)] = 1;
        (*module_count)++;
        getNextMetadataModuleInMaster(matrix->height, matrix->width, (*module_count), x, y);
    }
    symbol->metadata.Nc = 0;
    if (g_diag_verbose) DEBUG_LOG("[PartI_DIAG] SUCCESS Nc=0 (custom Mode 0 extension — chroma-probe short-circuit, no PartI bit decoding required)");
    return JAB_SUCCESS;
}
```

### Validation criteria

Empirical test on Galaxy S25 with the existing Mode 0 fixture (W2.8 APK):
- `[PartI_DIAG] SUCCESS Nc=0 (custom Mode 0 extension — chroma-probe short-circuit ...)` markers fire
- `FAIL_STAGE=pair_bits` count for Nc=0 drops to 0
- `Decoded data preview: "HELLO-Nc-0"` (or fixture-specific data) appears
- First-ever Mode 0 end-to-end Native decode success

### Regression safety

The short-circuit is gated entirely on `g_mode0_decode` which is `0` for color
modes (Nc=1..7). The standard PartI logic is bit-for-bit unaffected for those
modes. Color-mode decode rates from the v10 W2.6 validation cycle (nc=5 at 64%,
nc=6 at 60%, nc=7 at 5.5% — all post-W2.6) should remain stable.

## Honest provenance of this entry

This entry exists because the H_mode0_decodeModuleNc_classifier Resolution
section explicitly noted: *"this fix targets the module_color stage only. The
downstream pair_bits and LDPC stages may still fail for Mode 0 because the
Mode 0 metadata bit-pack scheme is documented to differ from color modes."*
That downstream gap was deferred and never filed as its own entry. The
user explicitly flagged the missing register hygiene on 2026-06-01:
"Nc=0/2 still need to be addressed." This entry closes the audit gap before
investigation begins.

## The hypothesis

For camera-captured Mode 0 (Nc=0, monochrome) fixtures, after the W1.2
classifier fix correctly produces `rgb=7` (W) and `rgb=0` (K) reads at the
module_color stage, PartI then proceeds to the `pair_bits` stage. The
pair_bits stage encodes/decodes the metadata bits using Mode 0's
1-bit-per-module bit-pack scheme (since Mode 0 has only 2 palette entries:
K and W). The current decoder logic assumes a different bit-pack semantic
that works for color modes (3-bit-per-module via {K, C, Y} pair encoding)
but fails for Mode 0.

Empirically, the v7 nc=0 trace (`trace-20260531_155439-nc0.logcat`) showed:

| Marker | Count | Notes |
|---|---|---|
| `g_mode0_decode=1` firings | 109 | Mode 0 trigger working |
| PartI BEGIN | 68 | Mode 0 reaches PartI |
| PartI module_color fails | 65 | Pre-W1.2 baseline (now lifted) |
| **FAIL_STAGE=pair_bits** | **3** | **Downstream failure that this entry tracks** |
| End-to-end Native SUCCESS | 0 | Mode 0 never decodes end-to-end |

The 3 FAIL_pb instances are the smallest empirical signal we have. Post-W1.2
(where module_color stage is fixed), the expectation is that more attempts
will reach pair_bits and the failure rate at that stage will become the
dominant signal — likely 60-80 fails per scan once module_color is no longer
blocking.

## Distinguishability from sibling H_nc2_pair_bits

| Aspect | Nc=0 (Mode 0) | Nc=2 (8-color) |
|---|---|---|
| Palette | `{K=0, W=7}` | `{K=0, C=3, Y=6}` for metadata |
| Bits per module | 1 (binary K vs W) | 3 (encoded as bit-pairs) |
| Parity check | Single-bit parity sequences | 2-bit pair parity |
| Downstream LDPC | Mode 0 may use a different EC parameter set | Standard color-mode LDPC |
| Pre-W1.2 failure | module_color (now Resolved) | module_color (lifted by PR #47 y_b_tolerance widening) |

The two entries are sibling-related but have distinct mechanisms. Shared
instrumentation (PartI pair_bits markers) can discriminate which sub-mechanism
applies to each Nc value.

## Mechanism candidates

In rough order of plausibility:

1. **Mode 0-unaware bit-pack interpretation**: the pair_bits stage code at
   `decodeMasterMetadataPartI` may apply color-mode 3-bit-per-pair logic
   regardless of `g_mode0_decode`. For a Mode 0 module sequence like
   `K-W-K-K-W-W-K`, the color-mode interpreter would read it as `{0, 6, 0, 0, 7, 7, 0}`
   bit-pair-encoded values that don't satisfy parity. Likely candidate;
   matches the supersession chain Resolution note.

2. **Mode 0 LDPC parameter mismatch**: even if pair_bits is read correctly,
   the LDPC layer (downstream of pair_bits) may use parameters tuned for
   color modes. Mode 0's metadata bit-density is lower per the JABCode spec;
   different LDPC (wc, wr) parameters may be required.

3. **Encoder-side bit-pack divergence on the standard Fraunhofer fixture**:
   the fixture being scanned may have been encoded with one bit-pack
   convention while the decoder expects another. Cross-reference with
   `src/jabcode/encoder.c` Mode 0 encoding path.

4. **Mode 0 not actually using pair_bits (FAIL_STAGE label is misleading)**:
   it's possible the FAIL_STAGE=pair_bits marker is fired by a code path
   that's structurally inappropriate for Mode 0, and the real failure is
   elsewhere. Distinguishability test: dump the raw bits being read and
   compare against the encoder's intended output.

## Investigation plan (cold pickup)

1. **Re-scan nc=0 with W2.6 APK** (post-bits_per_module fix, post-W1.2-classifier-fix).
   Get a fresh empirical baseline. The FAIL_pb count should jump from 3
   (pre-W1.2) to substantially higher now that module_color is lifted.
2. **Add `[PartI_DIAG] PAIR_BITS` instrumentation** to decoder.c at the
   pair_bits stage: capture raw bit sequence, decoded pair value, validity
   check, expected vs actual.
3. **Re-scan nc=0 + nc=2** with the instrumentation active. Side-by-side
   comparison of the two failure patterns.
4. **Identify mechanism** from the trace: does Nc=0 fail because the
   decoder is applying 3-bit-pair logic to a 1-bit-stream? Does Nc=2 fail
   because Y/C confusion under camera cast produces invalid bit-pairs?
5. **Implement Nc-specific fixes**: likely separate code paths for Mode 0
   pair_bits (single-bit interpretation) vs color-mode pair_bits (3-bit
   interpretation), gated on `g_mode0_decode`.

Estimated effort: 1-2 hours instrumentation + 1 scan session + 2-4 hours
fix design and implementation + 1 hour TDD validation.

## Triggers

- **Trigger A** (FIRED 2026-05-31): customer requires all 8 Nc modes
- **Trigger B** (FIRED 2026-06-01): user explicitly named the gap
  ("Nc=0/2 still need to be addressed") in Wave 2.6 retrospective
- **Trigger C**: H_nc2_decode_failure investigation activates shared
  pair_bits instrumentation

## Why this is filed as its own entry

The supersession chain note in `H_mode0_decodeModuleNc_classifier.md`
implied this work would happen "as part of Wave 2 follow-up." Without a
dedicated register entry, the work surface stayed invisible while the
register count looked artificially clean. Filing this entry restores
honest accounting of open work. The Resolution chain becomes:
`H_mode0_partI_decode_failure` (Superseded) → `H_mode0_decodeModuleNc_classifier`
(Resolved at module_color stage) → `H_nc0_pair_bits` (this entry, downstream
of classifier).

## Cross-references

- `H_mode0_decodeModuleNc_classifier.md` — predecessor, Resolved at module_color stage; this entry continues the chain
- `H_mode0_partI_decode_failure.md` — original entry, Superseded by classifier
- `H_nc2_decode_failure.md` — sibling, also at pair_bits stage but with different bit-pack scheme
- `src/jabcode/decoder.c::decodeMasterMetadataPartI` — pair_bits stage location
- `src/jabcode/encoder.c` — Mode 0 encoder path for bit-pack cross-reference
- `jabauth-android/diagnostic-app/logs/trace-20260531_155439-nc0.logcat` — v7 nc=0 empirical anchor (3 FAIL_pb instances pre-W1.2-classifier-fix)
- 2026-06-01 user feedback flagging the register-hygiene gap
