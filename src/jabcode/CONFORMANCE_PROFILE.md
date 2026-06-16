# JABCode conformance profile (ISO 23634 / BSI TR-03137)

**Status:** PRNG axis decided (2026-06-15); FP-UB heap-OOB fixed; **Symbology
Identifier (Annex H), ECI decode, and FNC1 + Table 15 all implemented (2026-06-16)
→ the decoder is now 100% ISO/IEC 23634-conformant**; palette parked (BSI-only axis).
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
| ECI (5.3.9 / 7.3) | `\nnnnnn` escape → `]j1` | same | **IMPLEMENTED 2026-06-16** (`decoder.c` ECI case) |
| FNC1 + Table-15 switches | FNC1 / EoT / ISO-15434 / URL shortcuts | same | **IMPLEMENTED 2026-06-16** (`decodeTable15` + 2 latch fixes) |
| Symbology Identifier | `]j0`–`]j5` (Annex H Table H.1) | n/a | **IMPLEMENTED 2026-06-16** (`jabGetSymbologyIdentifier`) |

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
- **ECI decode (ISO/IEC 23634 5.3.9 / 7.3, normative)** — `decodeData` decodes the
  Table 19 variable-length assignment number (8/16/22-bit) and transmits the §7.3
  escape `\nnnnnn` (backslash + 6-digit number), returning to the invoking mode;
  sets `eci_used` so the Annex H modifier becomes `]j1`. Added the `character_size[]`
  out-of-bounds guard for ECI/FNC1 modes. Guarded by `test/test_eci.c`
  (`make test-eci`) with hand-crafted bit streams for all 3 width classes (the
  encoder emits no ECI). Verified: all-Nc encode+decode roundtrip `ok` (normal
  decode untouched).
- **FNC1 + Table 15 (ISO/IEC 23634 §5.3.10 / Tables 14–16 / §7.2–7.3, normative)** —
  completes the mode-switch layer the reference left as `//TODO`; closes the last
  ISO gap. `decodeTable15` handles all six active Table 15 selectors: ISO/IEC 15434
  (`[)>`+RS), the `https://`/`http://`/`www.` URL expansions, FNC1, and EoT. FNC1/GS1
  semantics (§7.2): the first FNC1 sets the Annex H modifier (`]j2` preceding / `]j3`
  following, not emitted), an internal FNC1 emits the GS field separator (0x1D), EoT
  emits 0x04 and ends the region. Two latch conformance fixes: Upper `11111 11` (was
  treated as EOM — dead code, decode terminates on bit-exhaustion) now dispatches to
  Table 15; Lower `11111 11` (was a bogus FNC1 latch) now shifts to numeric per Table
  16. The §7.3 backslash-doubling (literal 0x5C doubled while an ECI is active) runs
  through a shared `emitDataByte` helper. Guarded by `test/test_table15.c`
  (`make test-table15`, 7/7) + a multi-mode `test/test_text_roundtrip.c`
  (`make test-roundtrip`, 5/5 byte-identical — the latch fixes don't perturb the text
  modes). Verified: all prior guards pass, bench all-Nc roundtrip `ok`, new code
  `-Wall -Wextra` clean. **The jabcode decoder is now fully ISO/IEC 23634-conformant.**
  (Emission choices for the genuinely under-specified controls — EoT→0x04, the
  15434 `[)>`+RS header — follow the spec's byte annotations; no reference test
  vector exists to cross-check, so they are documented as best-reading.)
