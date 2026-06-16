# JABCode conformance profile (ISO 23634 / BSI TR-03137)

**Status:** PRNG axis decided (2026-06-15); FP-UB heap-OOB fixed; **Symbology
Identifier (Annex H) implemented (2026-06-16)**; palette parked, ECI/FNC1 pending.
This is the pinned design seam — implement the diverging axes here when they land,
so the `JAB_PROFILE` selector is born with a real consumer rather than as a dead enum.

## The decision: Reference-is-ISO

This C core is the **Fraunhofer SIT reference implementation** (`pseudo_random.c`,
`interleave.c`, `encoder.c` etc. are the original 2016 upload;
`upstream = github.com/jabcode/jabcode`). ISO/IEC 23634:2022 is the standardization
of that reference, so on the determinism axes the code is **already ISO-conformant**:

- **PRNG** — `lcg64_temper()` (multiplier 6364136223846793005). ISO Annex F shows
  the C-std `rand()` LCG, **but Annex F is informative, not normative**; a `rand()`
  generator yields a different permutation and would not interoperate with the
  reference ecosystem (official `libjabcode` and any reference-based reader).
  **PRNG is therefore NOT a profile axis** — both profiles keep lcg64.
- **Palette** — default 8-colour order `[K,B,G,C,R,M,Y,W]` = ISO Table 21.
- **ECC** — `ecclevel2wcwr` is identical across ISO Table 20 / BSI Table 18.

(Source-verified audit: COA `JABCode-zxing-Collaboration-Strategic-Analysis.md`, sec 2.5.)

## Planned `JAB_PROFILE` seam

A future `jab_profile` selector — `JAB_PROFILE_ISO` (default) / `JAB_PROFILE_BSI`
(compat) — will gate the axes where the two specs genuinely diverge. It is
deliberately not added as code yet; it will arrive with its first consumer (the
palette).

| Axis | ISO / reference (default) | BSI profile | Status |
|---|---|---|---|
| PRNG | `lcg64_temper` | `lcg64_temper` (same) | not an axis — decided |
| Palette (8-colour order) | Table 21 `[K,B,G,C,R,M,Y,W]` | Table 19 `[K,M,Y,C,R,G,B,W]` | **first axis** — pending (`encoder.h` `jab_default_palette`, `encoder.c` `setDefaultPalette`) |
| ECI / FNC1 | implement (currently `//TODO`) | implement | ISO gap — pending (`decoder.c`; modifier hooks in place) |
| Symbology Identifier | `]j0` (Annex H Table H.1) | n/a | **IMPLEMENTED 2026-06-16** (`jabGetSymbologyIdentifier`) |

## Shipped

- **FP-UB heap-OOB fix** (PR #85) — `pn_index()` in `pseudo_random.h` clamps the
  float-rounding edge to `range-1` across the 5 call sites in `interleave.c` /
  `ldpc.c`. Interop-preserving (bit-identical to the legacy mapping for every
  in-range draw); guarded permanently by `test/test_pn_index.c` (`make test-pn`).
- **Symbology Identifier (ISO/IEC 23634 Annex H, normative)** — the decoder now
  produces `]jm` (currently `]j0`; ECI/FNC1 not yet decoded). Design:
  - Pure Table H.1 formatter in `symbology_id.h` (`]j` + modifier from
    `(eci_used, fnc1_mode)`); guarded by `test/test_symbology_id.c` (`make test-symid`).
  - `decodeData` resets per decode and publishes on success via the new public
    `jabGetSymbologyIdentifier()`. **The `jab_data` payload is left untouched** —
    per §7.4 the identifier is a transmission preamble the host prepends, so
    payload hash/verification (the COA crypto path) is unaffected.
  - ECI/FNC1 `//TODO` sites carry hooks to set the flags so the modifier
    auto-updates (1–5) when those land (the next ISO gap).
  - Verified: Nc 4–256 in-memory roundtrip byte-identical + `]j0`; no new
    `-Wall -Wextra` warnings.
