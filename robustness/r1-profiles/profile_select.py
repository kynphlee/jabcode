#!/usr/bin/env python3
"""R1 per-medium encoding-profile selector.

Picks an ISO/IEC 23634-conformant (Nc, ECC) JABCode encoding for a capture
medium + payload size, from the evidence-backed profiles derived by the R0 rig
(see robustness/r1-profiles/README.md and data/decode_rates.csv). The selector
NEVER deviates from the standard: it only chooses among legal colour counts
(Nc 0..7 -> the standardized Table-21 palette) and legal ECC levels (1..10).
It emits the parameters; the reference encoder (src/jabcode) does the actual
conformant encoding with the standardized palette and Annex-legal LDPC.

Why these profiles (one-line evidence each; full numbers in the README):

  hostile / screen   Nc1 (4c), ECC5  -- chroma + lighting are the screen threats;
                     at Nc1 they decode 100% vs 47% at Nc7 (rig, 5 payloads/cell).
                     Colour count, not ECC, is the lever -- low Nc keeps the few
                     colours maximally separable when a screen washes them out.
  print / luxury-COA Nc3 (16c), ECC5 -- defocus/low-res are the print threats;
                     blur + downscale decode 100% at Nc3 with 2x the data density
                     of Nc1. A controlled, gamut-limited but well-lit capture.
  clean / archival   Nc7 (256c), ECC3 -- a clean digital channel has no capture
                     degradation (clean decode 100%), so maximise density: Nc7 at
                     ECC3 carries ~921 B in one side-45 symbol, 4.7x the hostile
                     profile.

The (Nc, ECC) axes and their tradeoff (rig-quantified):
  * Nc DOMINATES robustness here and is non-monotone -- an inverted-U peaking at
    Nc2-3 overall, but the *best* Nc is threat-specific (colour threats favour
    Nc1; defocus/low-res favour Nc2-3; clean favours Nc7 for density).
  * ECC is a weak SECOND-ORDER lever for these colour/luminance threats
    (overall 85.7% -> 86.0% from ECC3 -> ECC8): the degradations cause
    correlated, structural errors a random-error LDPC budget barely helps. ECC's
    real price is capacity -- ECC8 costs ~64% of the payload vs ECC3.

This module is import-safe and has no third-party deps.
"""
from __future__ import annotations

from dataclasses import dataclass


# Legal JABCode colour counts by Nc index (ISO/IEC 23634 Table 21 palette sizes).
NC_TO_COLORS = {0: 2, 1: 4, 2: 8, 3: 16, 4: 32, 5: 64, 6: 128, 7: 256}
# Legal ECC levels.
ECC_MIN, ECC_MAX = 1, 10

# Measured max single-symbol payload (bytes) at side version 7 (side 45),
# per (Nc, ECC), from robustness/r1-profiles/data/density_capacity.csv. Used to
# warn when a payload is too large for a profile's symbol at the reference
# geometry (the encoder will then auto-grow the version, costing more area).
# Kept in sync with that CSV; it is the realised capacity after the encoder's
# actual (wc,wr) + metadata overhead, not an idealised bits/module figure.
_CAP_V7 = {
    (1, 3): 258, (1, 5): 198, (1, 8): 90,
    (2, 3): 391, (2, 5): 298, (2, 8): 137,
    (3, 3): 510, (3, 5): 393, (3, 8): 181,
    (5, 3): 688, (5, 5): 530, (5, 8): 245,
    (7, 3): 921, (7, 5): 709, (7, 8): 329,
}


@dataclass(frozen=True)
class Profile:
    """An ISO-conformant encoding recommendation for a medium."""
    medium: str
    nc: int
    ecc: int
    rationale: str

    @property
    def colors(self) -> int:
        return NC_TO_COLORS[self.nc]

    def as_dict(self) -> dict:
        return {"medium": self.medium, "nc": self.nc, "colors": self.colors,
                "ecc": self.ecc, "rationale": self.rationale}


# The evidence-backed profiles. Every (nc, ecc) here is spec-legal.
PROFILES: dict[str, Profile] = {
    "hostile": Profile(
        "hostile", nc=1, ecc=5,
        rationale="screen/chroma+lighting threats: Nc1 decodes 100% vs 47% at "
                  "Nc7; low Nc keeps colours separable under wash. ECC5 = margin."),
    "screen": Profile(
        "screen", nc=1, ecc=5,
        rationale="alias of 'hostile' -- monitor capture is colour-wash + "
                  "non-uniform illumination dominated."),
    "print": Profile(
        "print", nc=3, ecc=5,
        rationale="print/defocus+low-res threats: blur+downscale decode 100% at "
                  "Nc3 with 2x the density of Nc1; controlled, gamut-limited."),
    "luxury-coa": Profile(
        "luxury-coa", nc=3, ecc=5,
        rationale="alias of 'print' -- a luxury COA is a controlled, well-lit "
                  "print capture."),
    "clean": Profile(
        "clean", nc=7, ecc=3,
        rationale="clean digital channel: no capture degradation (100% clean "
                  "decode), so maximise density -- Nc7/ECC3 ~= 921 B/symbol."),
    "archival": Profile(
        "archival", nc=7, ecc=3,
        rationale="alias of 'clean' -- archival/digital storage, max density."),
}

# Robustness fallback ladder: from densest to most robust. If a payload does not
# fit the chosen profile's symbol AND robustness is NOT the priority (clean
# media), we may step DOWN this ladder (toward more colours) before growing area.
# Conversely for hostile media we never trade robustness for capacity silently.
DEFAULT_MEDIUM = "print"


def list_media() -> list[str]:
    """Canonical medium hints accepted by select()."""
    return sorted(PROFILES.keys())


def capacity_at_v7(nc: int, ecc: int) -> int | None:
    """Measured max payload bytes for (nc, ecc) at side version 7 (side 45)."""
    return _CAP_V7.get((nc, ecc))


def select(medium: str, payload_len: int | None = None,
           strict_fit: bool = False) -> dict:
    """Choose an ISO-conformant (Nc, ECC) for `medium` + optional `payload_len`.

    Returns a dict:
        {medium, nc, colors, ecc, rationale, fits_at_v7, capacity_v7_bytes,
         note}

    `medium` is one of list_media() (case-insensitive, '-'/'_' interchangeable);
    unknown hints fall back to DEFAULT_MEDIUM with a note.

    Fit handling (advisory; the reference encoder still auto-sizes the version):
      * If payload_len <= the profile's measured side-45 capacity, fits_at_v7 is
        True and the recommendation stands.
      * If it exceeds that capacity, the profile still ENCODES (the encoder grows
        to a larger version, costing area), and `note` says so. With
        strict_fit=True we additionally raise ValueError so a caller that must
        keep a side-45 symbol can catch it and split into multiple symbols.

    The returned (nc, ecc) are ALWAYS legal; this never invents a palette.
    """
    key = (medium or "").strip().lower().replace("_", "-")
    prof = PROFILES.get(key)
    note = ""
    if prof is None:
        prof = PROFILES[DEFAULT_MEDIUM]
        note = (f"unknown medium {medium!r}; defaulted to {DEFAULT_MEDIUM!r}. "
                f"known: {list_media()}")

    # Defensive: assert the stored profile is spec-legal before returning it.
    assert prof.nc in NC_TO_COLORS, f"illegal Nc {prof.nc}"
    assert ECC_MIN <= prof.ecc <= ECC_MAX, f"illegal ECC {prof.ecc}"

    cap = capacity_at_v7(prof.nc, prof.ecc)
    fits = None
    if payload_len is not None and cap is not None:
        fits = payload_len <= cap
        if not fits:
            grow = (f"payload {payload_len} B exceeds the {cap} B side-45 capacity "
                    f"of {prof.medium} (Nc{prof.nc}/ECC{prof.ecc}); the encoder "
                    f"will use a larger symbol version (more area).")
            note = (note + " " + grow).strip()
            if strict_fit:
                raise ValueError(grow)

    out = prof.as_dict()
    out["capacity_v7_bytes"] = cap
    out["fits_at_v7"] = fits
    out["note"] = note
    return out


if __name__ == "__main__":
    import argparse
    import json

    ap = argparse.ArgumentParser(
        description="Select an ISO-conformant (Nc, ECC) JABCode profile for a medium.")
    ap.add_argument("medium", nargs="?", default=DEFAULT_MEDIUM,
                    help=f"capture medium hint (one of: {list_media()})")
    ap.add_argument("--payload-len", type=int, default=None,
                    help="payload size in bytes (enables the side-45 fit check)")
    ap.add_argument("--strict-fit", action="store_true",
                    help="error if the payload won't fit the profile's side-45 symbol")
    args = ap.parse_args()
    print(json.dumps(select(args.medium, args.payload_len, args.strict_fit), indent=2))
