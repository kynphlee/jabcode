# H_partI_nc_extraction_bias — Open root-cause hypothesis: `decodeMasterMetadataPartI` deterministically misreads the Nc field for specific fixture color modes

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-05-31 (v6 stacked-fix trace cross-Nc analysis)                                                 |
| **Status**   | Open — CONFIRMED across two fixtures; mechanism unspecified pending instrumentation                 |
| **Binding**  | Triggered (customer need expressed 2026-05-31 — all 8 Nc modes required)                            |
| **Owner**    | Unassigned (claimed on trigger)                                                                    |
| **Severity** | Medium — silent failure mode; Nc fallback ladder masks the bias from end-user observability        |
| **Related**  | `H_nc2_decode_failure.md`, `H_mode0_decodeModuleNc_classifier.md`, `H_partI_clean_data_failure.md` |

## The hypothesis

For camera-captured fixtures at specific Nc values, `decodeMasterMetadataPartI` succeeds at the PartI validity check but extracts the **wrong Nc value**. The bias is deterministic, not random sensor noise — the same fixture-to-misread mapping reproduces across hundreds of frames in 30-second scan windows.

Observed transformations:

| Fixture Nc | PartI-extracted Nc | Direction |
|---|---|---|
| Nc=1 (4-color) | Nc=2 (8-color) | +1 |
| Nc=6 (128-color) | Nc=5 (64-color) | −1 |
| Nc=7 (256-color) | Nc=7 (256-color) | **None — reads correctly** |

The asymmetry is the load-bearing observation. If the bias were random sensor noise or a single-bit-flip in the metadata code, Nc=7 would also exhibit a misread (e.g., to Nc=6 or Nc=3). It does not. Therefore the bias is **bit-pattern specific**, not amplitude-level noise.

## Empirical anchor (2026-05-31 v6 stacked-fix traces)

Reference traces (Galaxy S25, post PR #46/#47/#48 build):
- `jabauth-android/diagnostic-app/logs/trace-20260531_155804-nc1.logcat` — Nc=1 fixture
- `jabauth-android/diagnostic-app/logs/trace-20260531_161501-nc6.logcat` — Nc=6 fixture
- `jabauth-android/diagnostic-app/logs/trace-20260531_162037-nc7.logcat` — Nc=7 fixture (counter-evidence)

The `Nc_FALLBACK` log line emits the originally-extracted Nc as `original=N`. Histogram across each trace:

| Trace | PartI BEGIN | PartI SUCCESS | `original=N` histogram | Native OK |
|---|---|---|---|---|
| nc1 | 77 | 69 (89.6%) | 48 × `original=2` (no `original=1` observed) | 0 |
| nc6 | 124 | 123 (99.2%) | 738 × `original=5` (no `original=6` observed) | 0 |
| nc7 | 229 | 228 (99.6%) | 1253 × `original=7`, 6 × `original=2` (transient) | 49 (19%) |

The nc7 trace's `6 × original=2` events occur in the first ~half-second of the capture, during the analyzer-recreation lag after Settings binding. These are not bias events — they are init artifacts.

## Distinguishability against neighbouring hypotheses

This entry is **distinct from** the following sibling entries; the v6 data discriminates them:

| Sibling | Distinguishability test | Result |
|---|---|---|
| `H_mode0_decodeModuleNc_classifier` | Classifier bug affects W pixel discrimination in Mode 0. This entry affects Nc field extraction at non-Mode-0 modes (1, 6). | nc1 and nc6 fixtures are color modes; classifier bug does not apply. |
| `H_nc2_decode_failure` | Nc=2 fails at module_color/pair_bits stage; the bias here is upstream (Nc field misread). | Nc=2 fixture's PartI BEGIN does not even reach the validity-check stage — different failure surface. |
| `H_partI_clean_data_failure` | Older entry observed PartI failing on camera-clean data. May or may not be the same mechanism. | Cannot discriminate without W2.2 instrumentation; W3.3 will revisit. |

## Mechanism candidates

The transformation pattern (Nc=1 → Nc=2 with `+1` shift; Nc=6 → Nc=5 with `−1` shift; Nc=7 → Nc=7 stable) is consistent with several mechanism classes. Listed in order of estimated plausibility:

### Candidate A — Gray-code interpretation bug

JABCode metadata fields are documented to use Gray code for the Nc field (where adjacent values differ by one bit). Gray-code lookup table for Nc 0..7:

| Nc | Gray code (3-bit) |
|---|---|
| 0 | `000` |
| 1 | `001` |
| 2 | `011` |
| 3 | `010` |
| 4 | `110` |
| 5 | `111` |
| 6 | `101` |
| 7 | `100` |

If `decodeMasterMetadataPartI` is reading the raw bit pattern but interpreting it as binary instead of Gray-code (or vice versa), the misreads would be:
- Raw bits `001` (Gray-code-1) interpreted as binary-1 → returns Nc=1. ❌ (observed: returns Nc=2)
- Raw bits `011` (Gray-code-2) interpreted as binary-3 → returns Nc=3. ❌
- The observed nc1 → nc2 transformation would require reading Gray-code `001` as binary `010` — a bit-shift, not a code-interpretation error.

Gray code alone does not explain the observed pattern, but a **Gray-code-with-bit-ordering-error** might. Candidate B explores this.

### Candidate B — Bit-ordering inversion (MSB/LSB swap)

If `decodeMasterMetadataPartI` reads the three Nc bits in reversed order:

| Fixture Nc | Encoded Gray | Reversed Gray | Decoded as Nc |
|---|---|---|---|
| 1 (`001`) | `001` | `100` | Nc=7 (Gray `100` = 7) — ❌ (observed: 2) |
| 6 (`101`) | `101` | `101` | Nc=6 — ❌ (observed: 5) |
| 7 (`100`) | `100` | `001` | Nc=1 — ❌ (observed: 7) |

Pure bit-reversal does not match. **Not a likely mechanism.**

### Candidate C — Module sampling offset

The metadata bits are sampled from specific module positions in the symbol. If the sampling offset is off-by-one for non-edge-Nc fixtures (or affected by symbol size, which scales with Nc), the wrong module's color could be sampled. This would produce a non-uniform bias — exactly what's observed (some Nc values read correctly, others don't).

Distinguishability test: capture instrumentation that logs the **sampled (xy) coordinates** for each metadata bit during PartI. If the sampled coordinates differ between Nc=1 and Nc=7 fixtures (which they should, due to symbol-size scaling), this candidate is supported. If the coordinates match expectation but the colors at those coordinates are wrong, it's not.

### Candidate D — Off-by-one in `module_count_in` or `start_xy` initialization

The `[PartI_DIAG] BEGIN module_count_in=0 start_xy=(6,1)` log line shows the initial state. For Nc=1 fixtures, `start_xy=(6,1)` may map to a different module than for Nc=7 fixtures due to symbol layout. If the initialization assumes a specific Nc, the metadata position is wrong for other Nc values.

Distinguishability test: compare `start_xy` values across the nc1, nc6, and nc7 traces. If they vary by Nc, the initialization is Nc-aware (rules out this candidate). If they're constant, this candidate is plausible.

## Fix specification (draft, pending mechanism identification)

The mechanism is not yet identified. W2.2 (per the customer-triggered plan) will:

1. Add instrumentation to `decodeMasterMetadataPartI` capturing:
   - The raw 3-bit Nc field before Gray-code decode
   - The (x, y) coordinates of each metadata bit's source module
   - The color value (post-`decodeModuleNc`) of each metadata bit's source module
2. Pin the decoder to each Nc in sequence (1, 6, 7) using the Path β infrastructure
3. Capture clean traces of each pinned scan
4. Compare bit patterns to identify which candidate mechanism is in play

Estimated W2.2 effort: ~3-4 hours instrumentation + 1 user scan session + ~2-3 hours analysis.

## Triggers

- **Trigger A** (FIRED 2026-05-31): customer requires all 8 Nc modes to ship reliably
- **Trigger B**: the `H_nc2_decode_failure` Wave 2 investigation surfaces the same mechanism
- **Trigger C**: an engineer is investigating Mode 0 / nc=0 and finds the same pattern at module-bit extraction layer

## Why this is filed

Per the established Cassandra register pattern: even after the customer trigger fires, this entry preserves the mechanism-layer findings for cold pickup. The Wave 2 investigation may produce additional sub-hypotheses that should be filed as sibling entries (e.g., specific to one Nc value or one mechanism candidate). This entry provides the umbrella context.

## Why this is NOT subsumed by `H_partI_clean_data_failure`

`H_partI_clean_data_failure` (filed 2026-05-26, before the v6 trace evidence) observed that PartI succeeds on synthetic-clean data but fails on camera-clean data. This bias entry is **narrower**: PartI also SUCCEEDS on camera data at Nc=1 and Nc=6 (89.6% and 99.2% respectively), but extracts the wrong Nc value. The two entries describe overlapping symptoms in different parameter regimes; their unification or differentiation is W3.3 work.

## Cross-references

- `src/jabcode/decoder.c::decodeMasterMetadataPartI` — the function to instrument and ultimately fix
- `src/jabcode/decoder.c::decodeModuleNc` — supplies module color values to PartI; cross-referenced with `H_mode0_decodeModuleNc_classifier`
- `jabauth-android/diagnostic-app/logs/trace-20260531_155804-nc1.logcat` — nc=1 empirical anchor
- `jabauth-android/diagnostic-app/logs/trace-20260531_161501-nc6.logcat` — nc=6 empirical anchor
- `jabauth-android/diagnostic-app/logs/trace-20260531_162037-nc7.logcat` — nc=7 counter-evidence anchor
- Bayesian Council Session bc-2026-05-31-04 (this conversation arc) — the empirical observation surfaced here
