# H_nc2_decode_failure — Open root-cause hypothesis: Nc=2 (8-color) fails on both media

| Field        | Value                                                                                              |
| ------------ | -------------------------------------------------------------------------------------------------- |
| **Filed**    | 2026-05-28 (PR 1 verification preparation; user-reported decode-capability matrix)                   |
| **Status**   | Open — confirmed via observation; no investigation done yet                                          |
| **Binding**  | Triggered (not scheduled), but high-priority for prompt activation                                   |
| **Owner**    | Unassigned (claimed on trigger)                                                                      |
| **Severity** | High — Nc=2 (8-color) is the canonical "easy" color mode; failing everywhere contradicts physics    |
| **Related**  | `H_partI_clean_data_failure.md`, `H_mode0_partI_decode_failure.md` (sibling open hypotheses)         |

## The hypothesis

JABCode decoding of Nc=2 (8-color) JABCodes fails consistently on BOTH printed and on-screen displays. This is anomalous because:

1. **The 8-color palette is the maximally-separated RGB cube vertices** (K=000, B=001, G=010, C=011, R=100, M=101, Y=110, W=111 — see `src/jabcode/encoder.h:20::jab_default_palette`). Color discrimination should be at its EASIEST.
2. **Nc=1 (4-color) and Nc=3 (16-color), the immediate neighbors, both work** on the same hardware/conditions.
3. **Nc=5 (64-color) ALSO works on print** despite having 8× more colors than Nc=2.

This sandwiching strongly suggests Nc=2 has a specific decoder bug, not a physics or device limitation.

## Empirical anchor (2026-05-28 capability matrix)

The user-confirmed decode-capability matrix on Galaxy S25:

| Nc | colors | print | screen |
|----|--------|-------|--------|
| 0  | 2      | no    | no     | ← `H_mode0_partI_decode_failure` |
| 1  | 4      | yes   | yes    |
| **2** | **8** | **no**   | **no** | ← **this hypothesis** |
| 3  | 16     | yes   | yes    |
| 4  | 32     | no    | yes    | ← print gamut |
| 5  | 64     | yes   | yes    |
| 6  | 128    | no    | yes    | ← print gamut |
| 7  | 256    | no    | no     | ← slave-decode + gamut |

The earlier "8c print scans showed HELLO-Nc-1 decodes" (traces `tolerance4-test-20260526_22*`, `tolerance4-test-20260527_115*`) — previously attributed to card mix-up — is now strongly suspected to have been the real decode failure pattern: the decoder cannot decode the Nc=2 card and was sometimes reading nearby Nc=1 cards instead. Worth re-examining those traces for evidence.

## Empirical confirmation (2026-05-28 desktop test data)

Running `test_roundtrip_all_nc.c` (synthetic encode→decode roundtrip across Nc=0..7) reveals a smoking-gun signal: **the `[PartI] LDPC decode SUCCESS, Nc=N` marker is missing for Nc=2 but present for both neighbors (Nc=1 and Nc=3)**:

```
--- Nc=1 (4-color) ---
[PartI] LDPC decode SUCCESS, Nc=1        ← PartI marker fires
[DECODE] SUCCESS Nc=1 (76 bytes)
  PASS: 5 bytes decoded correctly

--- Nc=2 (8-color) ---                    ← NO [PartI] marker
[DECODE] SUCCESS Nc=2 (580 bytes)
  PASS: 5 bytes decoded correctly

--- Nc=3 (16-color) ---
[PartI] LDPC decode SUCCESS, Nc=3        ← PartI marker fires
[DECODE] SUCCESS Nc=3 (216 bytes)
  PASS: 5 bytes decoded correctly
```

This means PartI is silently failing for Nc=2 and the legacy permissive fall-through (which fires when `g_strict_partII_required` is FALSE — i.e., on the desktop test path) fabricates a successful decode using default-metadata bits that happen to align with the correct payload.

**This unifies the Nc=2 mystery with `H_partI_clean_data_failure`**: it's a specific manifestation, not a separate bug class. PartI consistently fails for Nc=2 inputs (synthetic OR camera-captured), and the camera path's strict-mode wiring (Option D, WS-5) correctly refuses to fabricate — hence the 100% camera-path failure rate while desktop tests pass.

The downstream `[DECODE] SUCCESS` marker shows `580 bytes` for Nc=2 — that's the gross capacity at default-metadata parameters (Pg=1044, Pn=580), not the encoded payload's actual capacity. The 5-byte "HELLO" check passes because the encoder happens to write data in a way the default-metadata decode can extract; this is coincidence, not correctness.

## Suspected failure surfaces (investigation candidates)

The encoder's color-number-specific palette code (`src/jabcode/encoder.c::genColorPalette`) shows:

- **`color_number == 4`** (Nc=1): explicit handcrafted 4-color palette of K/M/Y/Cyan (see encoder.c around line 106-110)
- **`color_number == 8`** (Nc=2): copies the full 8-color `jab_default_palette` verbatim
- **`color_number >= 16`** (Nc≥3): uses `genColorPalette` to interpolate

Candidate explanations:

1. **LDPC parameters specific to Nc=2**: the LDPC (wc, wr) values are encoded in metadata bits. If Nc=2's metadata bit layout differs from the decoder's expectation (e.g., a recent change to Nc=3+ encoding inadvertently broke Nc=2's parameter encoding), every Nc=2 metadata decode fails.
2. **Palette index → bit mapping**: Nc=2 uses 3 bits per module (8 colors = 3 bits). If the bit-extraction order from a 3-bit module differs in encoder vs decoder, every module produces the wrong color → garbage bytes.
3. **`readColorPaletteInMaster` color-classification step**: the decoder learns the palette from the four FP cores. If the color-classification kernel has an Nc=2-specific branch that's buggy, every palette learning attempt produces an incorrect palette and decoding fails.
4. **`decodeMasterMetadataPartI` LDPC for Nc=2**: similar to `H_partI_clean_data_failure` but specifically biased toward Nc=2 metadata.

## Reproducible repro

Build any commit at or after trunk `631e095`:

1. Display the user's Nc=2 fixture (the 8-color JABCode print, OR the `nc2-8c-20260521.png` fixture on screen)
2. Scan with the diagnostic app for 30+ seconds
3. Observe: 0 `DECODE_OK Nc=2` markers; some `FAIL_ATTR status=1` markers (FP found, slave-decode failed)

Compare against `test_roundtrip_all_nc.c` desktop test (synthetic encode→decode roundtrip at Nc=0..7). If that desktop test PASSES for Nc=2 (which prior data suggests it does), the bug is camera-specific (input-bitmap-quality dependent). If it FAILS for Nc=2 too, the bug is in the decoder library independent of input quality.

## Investigation checklist (cold pickup)

1. **Run `test_roundtrip_all_nc.c` on Nc=2**: does the synthetic roundtrip pass? If yes, bug is camera-input-quality dependent. If no, bug is in the decoder library.
2. **Add per-iteration logging to `decodeMasterMetadataPartI` for Nc=2**: what is the LDPC return value? What metadata bits were read?
3. **Compare Nc=2 vs Nc=1 vs Nc=3 metadata layouts**: are there off-by-one or bit-order differences in the metadata regions of the master symbol?
4. **Re-examine the `tolerance4-test-2026052*` traces** for Nc=2-specific failure modes that we may have ignored when attributing them to "card mix-up."
5. **Synthetic noise test**: feed Nc=2 + ±5 ADU chroma noise (per `test_mode0_chroma_tolerance.c` pattern) through the camera-pipeline decode; does the noise level matter?

## Triggers (when this hypothesis activates)

- **Trigger A — Nc=2 is needed by a product feature** (e.g., a customer-facing authentication scenario that uses 8-color codes)
- **Trigger B — `H_partI_clean_data_failure` investigation activates** and the investigator notices Nc=2 anomalies in their data
- **Trigger C — An engineer has capacity and picks this up** because it's the lowest-hanging-fruit decoder bug
- **Trigger D — PR 1 verification data shows Nc=2 specifically is consistently failing** (likely to fire imminently — PR 1's per-fixture decision rules already include Nc=2 indirectly)

## Why this is filed (not scheduled)

Per the established Cassandra register pattern: scheduling commits us to investigation by a date; triggering activates when evidence demands it. Nc=2 may be product-irrelevant (if the SDK consumers don't use 8-color codes) or product-critical (if they do); the trigger pattern lets us discover which.

## Cross-references

- `docs/roi-detection-implementation-plan.md` §1.6.1 — PR 1's per-fixture decision rules; Nc=2 is implicitly part of the "should we proceed" determination
- `H_partI_clean_data_failure.md` — sibling decoder hypothesis
- `H_mode0_partI_decode_failure.md` — sibling decoder hypothesis
- `project_jabcode_screen_vs_print_physics.md` (memory) — explains why high-Nc fails on print but DOES NOT explain why Nc=2 fails everywhere
- `src/jabcode/encoder.h::jab_default_palette` — the 8-color palette being scrutinized
- `src/jabcode/encoder.c::genColorPalette` — palette generation that differentiates Nc=2 from neighbors
- `src/jabcode/test/test_roundtrip_all_nc.c` — existing synthetic roundtrip test that should be re-examined for Nc=2 specifically
