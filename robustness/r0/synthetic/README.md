# R0 Synthetic Degradation Corpus

Controlled, **repeatable** degradations of clean ground-truth JABCode symbols.
Their purpose: let a decoder change (e.g. adaptive vs. global binarization) be
A/B-tested on byte-for-byte **identical** inputs. Every degradation models a
real capture failure mode that defeats a single global threshold -- exactly the
cases adaptive binarization is meant to recover.

```
robustness/r0/synthetic/
  degrade.py        parameterized generator (input symbol dir -> out dir)
  manifest.jsonl    one flat record per generated image (committed)
  out/              the committed sample set (144 images, ~3.4 MB)
  README.md         this file
```

## Source symbols

Clean, round-trip-verified ground truth, one per colour mode `Nc = 0..7`:

- `jabauth-android/diagnostic-app/images/full-spectrum/nc{0..7}-*-20260521.png`
- `jabauth-android/diagnostic-app/src/main/assets/benchmark/nc{0..7}-*.png` (identical bytes, asset-path copies)

All are authored at **12 px/module**, sides of 21 modules (Nc 0/1/2, 252x252)
or 25 modules (Nc 3..7, 300x300), with **no quiet zone** -- the symbol fills the
frame edge-to-edge. The `Nc` is parsed from the `nc<N>` filename prefix.

## Degradation taxonomy + param ladders

Each family is a single pure, deterministic function of one scalar `param`. The
`param` recorded in the manifest is always the human-meaningful ladder value.

| Family        | What it models                                              | `param` meaning            | Full ladder            | Sample ladder (committed) |
|---------------|-------------------------------------------------------------|----------------------------|------------------------|---------------------------|
| `blur`        | Defocus / motion smear merging adjacent modules             | Gaussian sigma, px         | `0.5 1 2 3`            | `1 2 3`                   |
| `perspective` | Off-axis capture; projective tilt about the vertical axis   | tilt angle, degrees        | `10 20 30 40`         | `20 30 40`                |
| `lighting`    | **Non-uniform illumination** -- the key adaptive-threshold case; diagonal shadow->glare ramp | peak swing, 0..1 | `0.3 0.5 0.7` | `0.3 0.5 0.7` |
| `downscale`   | Distance / low-res sensor approaching the detector floor    | target **px/module**       | `8 6 4 3`             | `6 4 3`                   |
| `jpeg`        | Lossy compression; 8x8 ringing + chroma subsampling bleed   | JPEG quality, 1..100       | `90 70 50 30`         | `70 50 30`                |
| `chroma`      | Screen-induced pink/white wash collapsing colour separation | desat + warm strength, 0..1| `0.3 0.5 0.7`         | `0.3 0.5 0.7`             |

Why these six: `blur`, `lighting`, and `downscale` attack the **luminance**
channel a binarizer thresholds (defocus, gradient, undersampling); `perspective`
attacks **registration** (the corner finder patterns); `jpeg` and `chroma`
attack the **colour** separation the high-Nc modes depend on. Together they span
the failure surface a global threshold cannot cover.

### Notes on specific families

- **`lighting`** is the headline case. It applies a diagonal multiplicative
  ramp -- top-left driven toward shadow `(1 - strength)`, bottom-right toward
  glare `(1 + strength)` -- so no single global threshold separates fore/back
  across the whole frame. This is the canonical input on which adaptive
  binarization should win and a global threshold should lose.
- **`perspective`** and **`lighting`** first pad a **white quiet zone**
  (default 4 modules; 2 for lighting) so the warp/ramp acts on a realistic
  captured frame and the corner finder patterns survive. Without it a tilt
  would clip the registration anchors and the failure would be trivial rather
  than informative.
- **`downscale`** is expressed directly in **target px/module** because the
  detector resolves modules down to ~3-20 px/module. param `3` sits on that
  floor. It downsamples (bilinear, like a sensor) then upsamples back with
  nearest so all corpus images share a canvas and the genuine resolution loss
  -- not interpolation cosmetics -- is what the decoder sees.
- **`jpeg`** is a real encode/decode roundtrip through an in-memory buffer, so
  the block artifacts are genuine. It is the only family whose output is `.jpg`.

### Determinism

No randomness anywhere. Same source symbol + same `param` => identical output
bytes on every run. That invariant is the whole point of the R0 rig: a decoder
A/B compares two builds on the *same* pixels, not on freshly-sampled noise.

## manifest schema

`manifest.jsonl` -- one JSON object per line, fields kept **flat** so an R0
decode-rate rig consumes `{file, nc, degradation_type, param}` directly:

| field              | type    | meaning                                                    |
|--------------------|---------|------------------------------------------------------------|
| `id`               | string  | unique id, `"<source-base>__<family>_<param>"`             |
| `file`             | string  | output filename, relative to the manifest's directory      |
| `source_symbol`    | string  | clean source filename the image was derived from           |
| `nc`               | int     | colour mode 0..7 (parsed from the `nc<N>` prefix)          |
| `degradation_type` | string  | family name (`blur` \| `perspective` \| ... \| `chroma`)   |
| `param`            | num     | the ladder value (sigma / degrees / px-per-module / etc.)  |

Example:

```json
{"id": "nc7-256c-20260521__lighting_0.7", "file": "nc7-256c-20260521__lighting_0.7.png", "source_symbol": "nc7-256c-20260521.png", "nc": 7, "degradation_type": "lighting", "param": 0.7}
```

### How it plugs into the R0 rig

```python
import json
for rec in map(json.loads, open("robustness/r0/synthetic/out/manifest.jsonl")):
    img      = rec["file"]              # decode this
    expected = "HELLO-Nc-%d" % rec["nc"] # payload the clean symbol carried
    bucket   = (rec["degradation_type"], rec["param"])  # ladder cell to score
    # decode(img) == expected  ->  tally a success in `bucket`
```

The payload for every symbol is `HELLO-Nc-<nc>` (see the full-spectrum README),
so the rig needs no side table -- `nc` gives both the colour mode and the
expected string. Group by `(degradation_type, param)` to get a decode-rate
curve per family, and diff two decoder builds cell-by-cell.

## Regenerate

Uses Pillow + numpy (`/tmp/bench-venv` has both).

```bash
# Sample set (the committed 144-image subset)
/tmp/bench-venv/bin/python robustness/r0/synthetic/degrade.py \
  --input jabauth-android/diagnostic-app/images/full-spectrum \
  --output robustness/r0/synthetic/out

# Full ladder (176 images) -- write to a separate, gitignored dir
/tmp/bench-venv/bin/python robustness/r0/synthetic/degrade.py \
  --input jabauth-android/diagnostic-app/images/full-spectrum \
  --output robustness/r0/synthetic/out-full --full

# One family only (e.g. just the lighting sweep)
/tmp/bench-venv/bin/python robustness/r0/synthetic/degrade.py \
  --input jabauth-android/diagnostic-app/images/full-spectrum \
  --output /tmp/r0-lighting --types lighting --full
```

### Corpus size

| Scope                | Images | Math                                                      | Size   | Tracked?            |
|----------------------|-------:|-----------------------------------------------------------|--------|---------------------|
| **sample** (default) | **144**| 8 Nc x 6 families x 3 params                              | ~3.4 MB| yes -- `out/`       |
| **full** (`--full`)  | **176**| 8 Nc x (blur 4 + persp 4 + light 3 + down 4 + jpeg 4 + chroma 3 = 22) | ~4 MB | no -- write to `out-full/` (gitignored) |

The 144-image sample is committed **in full** so the R0 rig is runnable straight
from a clean checkout with no regeneration step. The full ladder and any larger
sweep (more source symbols, finer ladders) should be written to
`robustness/r0/synthetic/out-full/`, which is gitignored -- regenerate it on
demand with `--full` rather than committing the bulk.
