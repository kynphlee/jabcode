# R0 Validation — Perspective-tilt detection result

**Question put to validation.** The R0 synthetic corpus reports that
perspective-tilted JABCode symbols decode at **0% for every tilt >= 20deg**
(`out/*__perspective_{20,30,40}.png`, 8 Nc each, all failing at the DETECT
stage). Is this a **real** weakness in the jabcode decoder's finder
search / perspective recovery, or an **artifact** of an over-aggressive
synthetic warp?

**Verdict: ARTIFACT — the committed 0% is driven by the 4-module quiet zone, not
by the tilt.** Holding the *exact same* `degrade.py` warp formula fixed and only
widening the quiet zone from 4 -> 8 modules takes the 20deg decode rate from
**0% -> 88%**. With a realistic central-projection (pinhole) warp and a generous
quiet zone, jabcode decodes **100% of the colour modes Nc1..Nc7 through ~30deg**
of tilt, with the curve only collapsing past 35-40deg. There is a *secondary*
real signal underneath the artifact (jabcode's tilt ceiling is modest — ~30deg —
versus ~66deg for a mature QR reader on the identical homography), but the
headline "0% at 20deg" is not a decoder failure; it is the corpus warp clipping
the registration margin away.

All numbers below are reproducible from this directory; see **Reproduce** at the
end. The committed `degrade.py` was treated as read-only (another agent owns it);
all work is under `robustness/r0/validation/`.

---

## 1. The committed corpus reproduces at 0% / DETECT (confirmed)

`make_manifest.py` pairs every committed `*__perspective_*.png` with its source
payload SHA-256 (lifted from `rig/manifest.jsonl`) and the rig decodes them:

| condition (committed) | decode rate | fail stage |
|-----------------------|:-----------:|------------|
| `perspective_20deg`   | **0/8 = 0%** | DETECT x8 |
| `perspective_30deg`   | **0/8 = 0%** | DETECT x8 |
| `perspective_40deg`   | **0/8 = 0%** | DETECT x8 |

So the striking result is real *as a measurement*. One correction to the brief,
though: the per-image failure string is **`Sampling master symbol failed`**, not
`Too few finder pattern found`. That distinction matters — the decoder got *past*
finder search and died trying to **sample the master-symbol grid**. The finders
were found; the geometry around them was unusable.

## 2. The four finder patterns survive the warp — clipping is NOT the cause

Mapping the four finder-pattern centres through the committed homography (script:
inline in `perspective_sweep.py` geometry helpers) shows every finder lands
**well inside** the canvas with **>= 6.4 px/module** even at 40deg:

| side | angle | TL/BL local px/mod | TR/BR local px/mod | all 4 finders inside canvas? |
|:----:|:-----:|:------------------:|:------------------:|:----------------------------:|
| 21   | 20deg | ~10.8             | ~8.9              | yes |
| 21   | 40deg | ~9.6              | ~6.5              | yes |
| 25   | 40deg | ~9.9              | ~6.4              | yes |

The detector floor is ~3 px/module, so the finders are never starved and never
fall off-canvas. **The committed warp does not clip or shrink the finders.**

## 3. The real cause: the warp consumes the quiet zone

What the committed warp *does* destroy is the **quiet margin around the symbol**.
Measuring the white border on the committed images (non-white bounding box vs
canvas):

| committed image            | left | **right** | **top** | **bottom** | content |
|----------------------------|:----:|:---------:|:-------:|:----------:|:-------:|
| `nc2 ... perspective_20deg` | 48px | **0px**   | 21px    | 21px       | 300x306 |
| `nc2 ... perspective_40deg` | 50px | **0px**   | **0px** | **0px**    | 298x348 |

The symbol content runs **flush to the right canvas edge at 20deg, and flush to
the right + top + bottom at 40deg.** `degrade.py`'s perspective pins the left edge
at x=0 and pulls the far edge inward by only `dx = (w/2)*sin(angle)`; with just a
4-module pad, `w` is small, so the far-side finders end up jammed against the
frame with no surrounding white. JABCode's perspective sampler needs that margin
to locate the alignment pattern and lay down the sampling grid — without it,
`Sampling master symbol failed`.

`compare_20deg.png` shows it directly: **committed qz=4 (fails)** has the symbol
bleeding off the right/top-left edges; **qz=8 (decodes)** is the identical warp
formula with breathing room; **pinhole qz=12** is a clean realistic keystone. All
three have intact, large finders — only the first fails.

## 4. Isolation: same warp, only the quiet zone changes (0% -> 88%)

Decoding `degrade.py`'s **byte-identical** perspective output (verified
maxdiff=0 against the committed files) while sweeping *only* `--quiet-zone`:

| quiet zone        | 20deg | 30deg | 40deg |
|-------------------|:-----:|:-----:|:-----:|
| **4 (committed)** | **0%**| 0%    | 0%    |
| 8                 | **88%**| 0%   | 0%    |
| 12                | **88%**| 0%   | 0%    |
| 16                | **88%**| 12%  | 0%    |

A single change — 4 -> 8 quiet modules, nothing else — converts the headline
"0% at 20deg" into 88%. This is the decisive isolation: **the tilt was never the
problem at 20deg; the missing margin was.** (The residual 1/8 miss at 20deg is
Nc0, a pre-existing mode-0 quirk — see Caveat.)

## 5. Corrected curve with a realistic warp (pinhole homography, qz=12)

`perspective_sweep.py --mode pinhole` models the symbol as a planar quad rotated
about its vertical centre-line and projected through a pinhole 3 widths away — a
*true* central-projection keystone (symmetric, symbol stays centred), with a
12-module quiet zone so margin is never the limiter. Decode rate vs tilt
(8 Nc averaged):

| tilt  | 0 | 5 | 10 | 15 | 20 | 25 | 30 | 35 | 40 | 45 | 50 |
|-------|:-:|:-:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| **pinhole qz=12** | 88 | 88 | 88 | 100 | 100 | 88 | 88 | 25 | 0 | 0 | 0 |
| shear qz=12       | 88 | 100 | 100 | 100 | 100 | 88 | 25 | 25 | 0 | 0 | 0 |

Per-Nc maximum tilt that still decodes (pinhole, realistic):

| Nc | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|----|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| max decodable tilt (deg) | 20* | 35 | 35 | 30 | 30 | 30 | 30 | 30 |

\* Nc0 is confounded (see Caveat); the other seven modes give the clean reading.

**True jabcode tilt threshold (realistic warp): ~30deg for all colour modes
Nc1..Nc7, with the low-colour modes Nc1/Nc2 reaching 35deg.** This holds 100%
through 20deg and 88% (7/8) through 30deg; the cliff is at 35-40deg. The few
failures that *do* occur at 25-35deg fail at **LDPC**, not DETECT — the symbol is
detected and sampled but the perspective-induced sampling jitter exceeds the
data-layer error-correction budget on the colour-dense modes. So jabcode's tilt
ceiling is a **sampling-precision / data-LDPC** limit, not a finder-search limit,
once the quiet zone is adequate.

The averaged curves and the quiet-zone isolation and the QR yardstick are all in
`perspective_curve.png`.

## 6. Maturity yardstick — QR (zxing-cpp) on the identical homography

`qr_yardstick.py` encodes a comparable payload (`HELLO-Nc-1`) as a QR via segno
at the same 12 px/module + 12-module quiet zone, applies the **identical pinhole
warp**, and decodes with **zxing-cpp** (a mature production reader):

| reader | last tilt that decodes | first tilt that fails |
|--------|:----------------------:|:---------------------:|
| QR / zxing-cpp        | **66deg** | 68deg |
| jabcode (pinhole best)| ~30deg (100% to 20)   | 40deg |

A mature reader tolerates **~66deg** of the same tilt; jabcode tolerates ~30deg.
So *after* the artifact is removed there is still a genuine ~2x tilt-tolerance
gap — jabcode's perspective recovery is real but immature. That is a fair,
separate finding; it is **not** what produced the corpus's 0%@20deg.

## Caveat — Nc0 confound (pre-existing, orthogonal to tilt)

Nc0 (2-colour, mode 0) is erratic across the whole sweep: it **fails at 0deg**
(undistorted, merely padded) yet decodes at 15-20deg. Tracing it: the clean
*unpadded* Nc0 source decodes fine (status 3), but once a white quiet zone is
added the decoder mis-reads it as Nc6 and dies at LDPC with
`No alignment pattern is available`. This is the known mode-0 metadata fragility
(memory: `project_mode0_metadata_rootcause.md`), independent of perspective.
Nc0 is therefore excluded from the tilt-threshold reading above; it needs its own
investigation and should not be attributed to perspective.

---

## Recommendation for the finalize agent (degrade.py change)

The `perspective` family is doing two things that make the result misleading:

1. **Quiet zone is far too thin for a tilt.** 4 modules is consumed by the warp
   itself. **Raise the perspective quiet zone to >= 8 modules** (12 is safer).
   This alone fixes the headline artifact (0% -> 88% at 20deg). Concretely, give
   `degrade_perspective` its own larger margin rather than the shared
   `--quiet-zone 4` default, e.g. pad with `max(quiet_modules, 8)` inside the
   perspective branch, or bump the CLI default.

2. **The warp is a one-edge-pinned `sin(angle)` shear, harsher than a real tilt
   and not centred.** Prefer a proper central-projection keystone (the
   `pinhole` mode in `validation/perspective_sweep.py`: rotate the planar quad
   about its vertical centre-line, project through a pinhole, keep it centred).
   It is both more physically faithful and a touch more forgiving, so the corpus
   measures the decoder rather than the transform. If keeping the shear for
   simplicity, at minimum centre it (split the foreshortening across both edges)
   and widen the quiet zone as in (1).

3. **Re-ladder once the warp is fixed.** With the corrected warp the interesting
   regime is **20-40deg** (100% -> cliff), so a ladder of `15 20 25 30 35` (drop
   40 as a guaranteed-fail, or keep it as the floor) characterises the real
   threshold. The current `20 30 40` sample, post-fix, would read
   `100% / ~88% / 0%`.

These three are drop-in for the finalize pass; the `pinhole` generator in
`perspective_sweep.py` can be lifted wholesale into `degrade.py` if a full
central-projection model is wanted.

---

## Reproduce

From `robustness/r0/validation/` (probe built via `make -C ../rig all`):

```bash
PY=/tmp/bench-venv/bin/python
SRC=../../../jabauth-android/diagnostic-app/images/full-spectrum

# 0) confirm committed corpus = 0% / DETECT
$PY make_manifest.py --images ../synthetic/out --glob '*perspective*' \
    --condition-from-suffix --out committed_perspective.jsonl
$PY ../rig/run_rig.py --manifest committed_perspective.jsonl --probe ../rig/r0_decode \
    --out results/committed_perspective.per_image.jsonl \
    --agg results/committed_perspective.aggregate.json --bucket conditions

# 1) realistic + shear sweeps (PNGs to /tmp; gitignored bulk)
$PY perspective_sweep.py --input $SRC --output /tmp/r0-persp-pinhole   --mode pinhole --quiet-zone 12
$PY perspective_sweep.py --input $SRC --output /tmp/r0-persp-shear-qz12 --mode shear   --quiet-zone 12
for s in pinhole shear-qz12; do
  $PY ../rig/run_rig.py --manifest /tmp/r0-persp-$s/manifest.jsonl --probe ../rig/r0_decode \
      --out results/sweep_$s.per_image.jsonl --agg results/sweep_$s.aggregate.json --bucket conditions
done

# 2) quiet-zone isolation on degrade.py's EXACT warp (the smoking gun)
#    (loop that calls degrade.degrade_perspective at qz 4/8/12/16 -> /tmp/r0-degrade-qz,
#     see the report's section 4; aggregate -> results/degrade_qz.aggregate.json)

# 3) QR yardstick (zxing-cpp) on the identical pinhole homography
$PY qr_yardstick.py
$PY qr_yardstick.py --angles 60,62,64,66,68,70,72,74,76,78,80 --out-csv results/qr_yardstick_high.csv

# chart
$PY plot_perspective.py     # -> perspective_curve.png
```

Bulk warped PNGs are written under `/tmp` and are not committed; the committed
artifacts are the scripts, the small manifests, the rig aggregates under
`results/`, the comparison crops, and `perspective_curve.png`.
