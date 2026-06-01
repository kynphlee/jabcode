# H_nc0_pair_bits — Open root-cause hypothesis: Mode 0 (Nc=0) PartI fails at the pair_bits stage, downstream of the W1.2 classifier fix

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-06-01 (register hygiene — documenting the downstream gap explicitly left open by W1.2 supersession chain) |
| **Status**   | Open — CONFIRMED at pair_bits stage by v7 nc=0 trace; mechanism unspecified pending instrumentation |
| **Binding**  | Triggered (customer need expressed 2026-05-31 — all 8 Nc modes required)                            |
| **Owner**    | Unassigned (claimed on trigger)                                                                    |
| **Severity** | Medium — Mode 0 (monochrome) end-to-end decode still has zero Native successes despite W1.2 classifier fix lifting module_color stage |
| **Related**  | `H_mode0_decodeModuleNc_classifier.md` (predecessor — Resolved at module_color stage; this entry continues the chain downstream), `H_mode0_partI_decode_failure.md` (superseded by the classifier entry), `H_nc2_decode_failure.md` (sibling — Nc=2 also fails at pair_bits but with a different bit-pack scheme) |

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
