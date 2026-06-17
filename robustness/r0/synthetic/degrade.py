#!/usr/bin/env python3
"""R0 synthetic degradation generator for the jabcode robustness corpus.

Applies parameterized, *repeatable* degradations to clean ground-truth JABCode
symbols so that decoder changes (e.g. adaptive vs. global binarization) can be
A/B-tested on byte-for-byte IDENTICAL inputs. Every degradation models a real
capture failure mode that defeats a single global threshold and that adaptive
binarization is meant to recover.

Degradation families (one scalar `param` each, recorded flat in the manifest):

  blur        Gaussian blur, sigma in pixels.          ladder: 0.5 1 2 3
  perspective Projective tilt, angle in degrees.        ladder: 10 20 30 40
  lighting    Non-uniform brightness ramp (the key      ladder: 0.3 0.5 0.7
              adaptive-threshold case), strength 0..1.
  downscale   Resample to N px/module (detector floor   ladder: 8 6 4 3
              is 3-20 px/module; source is 12).
  jpeg        Lossy JPEG, encoder quality 1..100.        ladder: 90 70 50 30
  chroma      Desaturation + warm white-balance wash     ladder: 0.3 0.5 0.7
              (models screen-induced pink/white cast),
              strength 0..1.

Source symbols carry no quiet zone (they fill the frame edge-to-edge), so
`perspective` and `lighting` first pad a white quiet zone to avoid clipping the
corner finder patterns -- the registration anchors a warped symbol still needs.

Determinism: no randomness anywhere. Same input + same param => same output
bytes. This is the whole point of the R0 rig.

Usage:
    degrade.py --input <symbol-dir> --output <out-dir> [--full] [--types ...]

    --input    dir of clean symbols named nc{0..7}-*.png (the Nc is parsed
               from the `nc<N>` filename prefix).
    --output   dir to write degraded images + manifest.jsonl into.
    --full     emit the full ladder for every family (default: representative
               subset committed to the repo -- see README).
    --types    restrict to a comma-separated subset of family names.
    --quiet-zone  white border added before perspective/lighting, in modules
               (default 4; JABCode module is 12 px in the source set).

Outputs `manifest.jsonl`: one flat record per image, fields
    {id, file, source_symbol, nc, degradation_type, param}
so an R0 decode-rate rig consumes {file, nc, degradation_type, param} directly.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
from dataclasses import dataclass
from typing import Callable

import numpy as np
from PIL import Image, ImageFilter

# --- calibration anchors --------------------------------------------------
# Source ground-truth symbols are authored at 12 px/module (see the
# full-spectrum README). The detector resolves modules down to ~3-20 px/module,
# so the downscale ladder is expressed directly in target px/module and the
# scale factor is target/SOURCE_PX_PER_MODULE.
SOURCE_PX_PER_MODULE = 12

# White is the canonical JABCode background / quiet-zone colour.
WHITE = (255, 255, 255)


# --- degradation primitives ----------------------------------------------
# Each takes an RGB image + scalar param and returns a new RGB image. They are
# pure and deterministic. `param` is always the human-meaningful ladder value
# recorded verbatim in the manifest.


def degrade_blur(img: Image.Image, sigma: float) -> Image.Image:
    """Gaussian blur. Models defocus / motion smear that merges adjacent
    modules so a global threshold straddles two colours."""
    if sigma <= 0:
        return img.copy()
    return img.filter(ImageFilter.GaussianBlur(radius=float(sigma)))


def degrade_perspective(img: Image.Image, angle_deg: float, quiet_modules: int) -> Image.Image:
    """Projective tilt about the vertical axis by `angle_deg`, as if the symbol
    were photographed off-axis. Pads a white quiet zone first so the corner
    finder patterns survive the warp, then keeps the output canvas the same
    size as the padded input."""
    padded = _pad_quiet_zone(img, quiet_modules)
    w, h = padded.size

    # Foreshorten the right edge: model a rotation of the plane about its
    # vertical centre line. The far edge shrinks by cos-driven perspective.
    a = math.radians(float(angle_deg))
    # Inset of the far (right) edge, as a fraction of width. sin(angle) gives a
    # monotone, intuitive tilt ladder without needing a full camera model.
    shrink = math.sin(a)
    dx = (w * 0.5) * shrink
    dy = (h * 0.5) * shrink * 0.5  # vertical foreshortening of the far edge

    src = [(0, 0), (w, 0), (w, h), (0, h)]
    dst = [
        (0, 0),
        (w - dx, dy),
        (w - dx, h - dy),
        (0, h),
    ]
    coeffs = _perspective_coeffs(dst, src)
    return padded.transform(
        (w, h), Image.PERSPECTIVE, coeffs,
        resample=Image.BICUBIC, fillcolor=WHITE,
    )


def degrade_lighting(img: Image.Image, strength: float) -> Image.Image:
    """Non-uniform brightness ramp across the frame -- the canonical case a
    *global* threshold cannot handle and adaptive binarization is meant to fix.
    `strength` in [0,1] is the peak multiplicative swing: one corner is
    brightened toward white, the diagonally opposite corner darkened toward
    black, with a smooth linear gradient between. A white quiet zone is added
    first so the ramp acts on a realistic captured frame."""
    padded = _pad_quiet_zone(img, quiet_modules=2)
    arr = np.asarray(padded, dtype=np.float32)
    h, w = arr.shape[:2]

    # Diagonal ramp from -strength (top-left, shadow) to +strength (bottom-right,
    # glare). gain in [1-strength, 1+strength].
    yy = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None]
    xx = np.linspace(0.0, 1.0, w, dtype=np.float32)[None, :]
    diag = (xx + yy) * 0.5  # 0..1 across the main diagonal
    gain = 1.0 + (2.0 * diag - 1.0) * float(strength)

    out = arr * gain[:, :, None]
    np.clip(out, 0, 255, out=out)
    return Image.fromarray(out.astype(np.uint8), mode="RGB")


def degrade_downscale(img: Image.Image, px_per_module: float) -> Image.Image:
    """Resample so each module spans `px_per_module` pixels, approaching the
    detector's 3-20 px/module floor. Downscale with a box-ish (bilinear) filter
    like a real sensor, then upscale back to the original canvas with nearest so
    the resolution loss -- not interpolation cosmetics -- is what the decoder
    sees. param is the target px/module."""
    scale = float(px_per_module) / SOURCE_PX_PER_MODULE
    if scale >= 1.0:
        return img.copy()
    w, h = img.size
    sw, sh = max(1, round(w * scale)), max(1, round(h * scale))
    small = img.resize((sw, sh), resample=Image.BILINEAR)
    # Upscale back so all corpus images share a canvas size; nearest preserves
    # the genuine information loss of the small raster.
    return small.resize((w, h), resample=Image.NEAREST)


def degrade_jpeg(img: Image.Image, quality: int) -> Image.Image:
    """Real JPEG encode/decode roundtrip at `quality`. Introduces 8x8 block
    ringing and chroma subsampling that bleed across module edges. Implemented
    by writing to a bytes buffer and re-reading so the artifacts are genuine.

    The returned image is RGB pixels with the JPEG artifacts already baked in;
    it is then saved by the caller into a lossless **PNG** container (the rig's
    readImage links libpng/libtiff and cannot decode JPEG). The container is
    lossless, so the compression damage lives in the pixels, not the file."""
    import io

    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=int(quality))
    buf.seek(0)
    return Image.open(buf).convert("RGB")


def degrade_chroma(img: Image.Image, strength: float) -> Image.Image:
    """Desaturate toward grey and apply a warm white-balance wash, modelling the
    screen-induced pink/white cast seen when scanning a monitor. `strength` in
    [0,1] blends toward luma (desaturation) and lifts R / drops B (warm cast).
    This collapses the colour separation high-Nc modes rely on -- a pure-colour
    failure mode distinct from the luminance ones above."""
    s = float(strength)
    arr = np.asarray(img, dtype=np.float32)

    # Desaturate: blend each pixel toward its Rec.601 luma.
    luma = (0.299 * arr[:, :, 0] + 0.587 * arr[:, :, 1] + 0.114 * arr[:, :, 2])
    desat = arr * (1.0 - s) + luma[:, :, None] * s

    # Warm white-balance shift: scale the channels (pink/white wash). The swing
    # is half `strength` so chroma at full strength is a heavy-but-not-degenerate
    # cast rather than a single flat colour.
    wb = np.array([1.0 + 0.5 * s, 1.0, 1.0 - 0.5 * s], dtype=np.float32)
    out = desat * wb[None, None, :]
    np.clip(out, 0, 255, out=out)
    return Image.fromarray(out.astype(np.uint8), mode="RGB")


# --- helpers --------------------------------------------------------------


def _pad_quiet_zone(img: Image.Image, quiet_modules: int) -> Image.Image:
    """Surround the symbol with a white border `quiet_modules` modules wide."""
    if quiet_modules <= 0:
        return img.copy()
    border = int(quiet_modules) * SOURCE_PX_PER_MODULE
    w, h = img.size
    canvas = Image.new("RGB", (w + 2 * border, h + 2 * border), WHITE)
    canvas.paste(img, (border, border))
    return canvas


def _perspective_coeffs(src, dst):
    """Solve the 8 projective coefficients mapping dst->src for PIL's
    Image.PERSPECTIVE (which samples the source for each output pixel)."""
    matrix = []
    for (sx, sy), (dx, dy) in zip(src, dst):
        matrix.append([dx, dy, 1, 0, 0, 0, -sx * dx, -sx * dy])
        matrix.append([0, 0, 0, dx, dy, 1, -sy * dx, -sy * dy])
    a = np.array(matrix, dtype=np.float64)
    b = np.array([c for pt in src for c in pt], dtype=np.float64)
    res = np.linalg.solve(a, b)
    return res.tolist()


# --- ladder definitions ---------------------------------------------------


@dataclass(frozen=True)
class Family:
    name: str
    fn: Callable[..., Image.Image]
    full: tuple          # full param ladder
    sample: tuple        # representative subset committed to the repo
    ext: str = "png"     # output extension
    fmt: str = "{:g}"    # how the param renders into the filename

    def params(self, full: bool) -> tuple:
        return self.full if full else self.sample


FAMILIES: list[Family] = [
    Family("blur", degrade_blur,
           full=(0.5, 1.0, 2.0, 3.0), sample=(1.0, 2.0, 3.0)),
    Family("perspective", degrade_perspective,
           full=(10, 20, 30, 40), sample=(20, 30, 40), fmt="{:g}deg"),
    Family("lighting", degrade_lighting,
           full=(0.3, 0.5, 0.7), sample=(0.3, 0.5, 0.7)),
    Family("downscale", degrade_downscale,
           full=(8, 6, 4, 3), sample=(6, 4, 3), fmt="{:g}px"),
    # jpeg roundtrips through an in-memory JPEG buffer to bake real ringing,
    # then saves the result as a lossless PNG so the rig (libpng/libtiff, no
    # JPEG) can read it. Hence ext stays the default "png", not "jpg".
    Family("jpeg", degrade_jpeg,
           full=(90, 70, 50, 30), sample=(70, 50, 30), fmt="q{:g}"),
    Family("chroma", degrade_chroma,
           full=(0.3, 0.5, 0.7), sample=(0.3, 0.5, 0.7)),
]
FAMILY_BY_NAME = {f.name: f for f in FAMILIES}

NC_RE = re.compile(r"(?:^|/)nc(\d+)[-_]")


def parse_nc(filename: str) -> int:
    m = NC_RE.search(filename.replace(os.sep, "/"))
    if not m:
        raise ValueError(f"cannot parse Nc from filename: {filename!r}")
    return int(m.group(1))


def discover_symbols(input_dir: str) -> list[tuple[int, str]]:
    """Return (nc, path) for each nc{N}-*.png in input_dir, sorted by Nc."""
    out = []
    for name in sorted(os.listdir(input_dir)):
        if not name.lower().endswith(".png"):
            continue
        if not NC_RE.search("/" + name):
            continue
        out.append((parse_nc(name), os.path.join(input_dir, name)))
    out.sort(key=lambda t: t[0])
    return out


def apply(family: Family, img: Image.Image, param, quiet_zone: int) -> Image.Image:
    if family.name == "perspective":
        return family.fn(img, param, quiet_zone)
    return family.fn(img, param)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(
        description="Generate the R0 synthetic degradation corpus.")
    ap.add_argument("--input", required=True,
                    help="dir of clean nc{0..7}-*.png ground-truth symbols")
    ap.add_argument("--output", required=True,
                    help="dir to write degraded images + manifest.jsonl")
    ap.add_argument("--full", action="store_true",
                    help="emit the full param ladder (default: repo subset)")
    ap.add_argument("--types", default=None,
                    help="comma-separated subset of degradation families")
    ap.add_argument("--quiet-zone", type=int, default=4,
                    help="white quiet-zone modules added before "
                         "perspective/lighting (default 4)")
    args = ap.parse_args(argv)

    if args.types:
        wanted = [t.strip() for t in args.types.split(",") if t.strip()]
        unknown = [t for t in wanted if t not in FAMILY_BY_NAME]
        if unknown:
            ap.error(f"unknown degradation type(s): {unknown}. "
                     f"choices: {list(FAMILY_BY_NAME)}")
        families = [FAMILY_BY_NAME[t] for t in wanted]
    else:
        families = FAMILIES

    symbols = discover_symbols(args.input)
    if not symbols:
        ap.error(f"no nc<N>-*.png symbols found in {args.input!r}")

    os.makedirs(args.output, exist_ok=True)
    manifest_path = os.path.join(args.output, "manifest.jsonl")

    count = 0
    with open(manifest_path, "w", encoding="utf-8") as mf:
        for nc, path in symbols:
            src_name = os.path.basename(path)
            base = os.path.splitext(src_name)[0]
            clean = Image.open(path).convert("RGB")
            for fam in families:
                for param in fam.params(args.full):
                    out_img = apply(fam, clean, param, args.quiet_zone)
                    pstr = fam.fmt.format(param)
                    out_name = f"{base}__{fam.name}_{pstr}.{fam.ext}"
                    out_img.save(os.path.join(args.output, out_name))
                    rec = {
                        "id": f"{base}__{fam.name}_{pstr}",
                        "file": out_name,
                        "source_symbol": src_name,
                        "nc": nc,
                        "degradation_type": fam.name,
                        "param": param,
                    }
                    mf.write(json.dumps(rec) + "\n")
                    count += 1

    scope = "full" if args.full else "sample"
    print(f"wrote {count} images ({scope} ladder) + manifest to {args.output}",
          file=sys.stderr)
    print(f"  symbols: {len(symbols)}  families: {[f.name for f in families]}",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
