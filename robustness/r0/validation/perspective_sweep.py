#!/usr/bin/env python3
"""Realistic perspective-tilt sweep for the R0 perspective validation.

Generates a finer tilt ladder than the committed corpus, using a PROPER pinhole
camera homography (a planar symbol rotated about its vertical centre-line and
projected through a pinhole), with a GENEROUS quiet zone so the corner finder
patterns never clip or shrink below the detector floor. The committed
`degrade.py perspective` family instead uses a one-edge-pinned `sin(angle)`
shear with only a 4-module quiet zone; this script is the controlled
counterfactual that isolates "is the tilt the problem, or is the warp the
problem?".

Two warp modes (select with --mode):

  pinhole   True central-projection keystone: the symbol plane is rotated by
            `angle` about its vertical centre axis, then projected by a pinhole
            at `dist` symbol-widths away. Symmetric top/bottom foreshortening,
            symbol stays centred. This is what an off-axis photo of a flat
            symbol actually looks like.
  shear     The committed degrade.py formulation (one edge pinned, sin-driven),
            reproduced here so the ONLY difference from the corpus is the quiet
            zone -- lets us attribute any recovery purely to margin.

Output: PNG per (symbol, angle) into --output, plus a manifest.jsonl consumable
by the R0 rig (payload hashes injected by make_manifest semantics, inlined here).

Determinism: pure function of (source, angle, quiet_zone, mode, dist). No RNG.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re

import numpy as np
from PIL import Image

SRC_PX_PER_MODULE = 12
WHITE = (255, 255, 255)

# Per-Nc payload SHA-256 (HELLO-Nc-<nc>), from robustness/r0/rig/manifest.jsonl.
PAYLOAD_SHA256 = {
    0: "11c1ceda934135e3e3ab0cdfb716dea4ad0acd7e4bedb57f980dff60912ef5af",
    1: "2d95744a96af55e37d41560e4b4a7f23740c6a851f52bf5e17019839f4c97bcb",
    2: "7e87bef3537a4bb4a625c35f0de113a269cdf2957588463e708b52e13f84a4b0",
    3: "929c1834b771758e6d21f5418e17d8aa6d5b15ba5b37e338f90faaa7888d6665",
    4: "e99f5aee549013e01bc1295bc4356bc34831b9c7de85cb5804fc909a87dca6a8",
    5: "9ff79adeeec81d82f757e19fb0de164a6e75fe9d9f7e57286289e4a1c9b208c9",
    6: "99ea912dcbd522eff2f53382cbf4f2d26fe5029ddf5dcafe163c184e6cb4f7e4",
    7: "e6816b035a11e9e94259407d9870ff9e4e217e1c0bd0199db015a11e4f3fba68",
}

NC_RE = re.compile(r"nc(\d+)[-_]")


def parse_nc(name: str) -> int:
    m = NC_RE.search(name)
    if not m:
        raise ValueError(f"cannot parse Nc from {name!r}")
    return int(m.group(1))


def pad_quiet_zone(img: Image.Image, quiet_modules: int) -> Image.Image:
    """White border `quiet_modules` modules wide on every side."""
    if quiet_modules <= 0:
        return img.copy()
    b = int(quiet_modules) * SRC_PX_PER_MODULE
    w, h = img.size
    canvas = Image.new("RGB", (w + 2 * b, h + 2 * b), WHITE)
    canvas.paste(img, (b, b))
    return canvas


def _solve_inverse_coeffs(src_pts, dst_pts):
    """PIL Image.PERSPECTIVE wants coeffs that map OUTPUT pixel -> INPUT pixel
    (it samples the source for each output pixel). Solve the homography
    dst -> src and return its 8 coefficients."""
    M = []
    for (sx, sy), (dx, dy) in zip(src_pts, dst_pts):
        # maps dst (output) -> src (input)
        M.append([dx, dy, 1, 0, 0, 0, -sx * dx, -sx * dy])
        M.append([0, 0, 0, dx, dy, 1, -sy * dx, -sy * dy])
    A = np.array(M, dtype=np.float64)
    b = np.array([c for pt in src_pts for c in pt], dtype=np.float64)
    return np.linalg.solve(A, b).tolist()


def dst_corners_pinhole(w: int, h: int, angle_deg: float, dist_widths: float):
    """Where the four corners of a w x h plane land after rotating the plane by
    `angle_deg` about its vertical centre-line and projecting through a pinhole
    `dist_widths` * w away. Returns 4 (x,y) in output-canvas coords, recentred so
    the projected quad is centred in the same w x h canvas."""
    a = math.radians(float(angle_deg))
    # Plane centred at origin in 3D; corners at (+/-0.5, +/-0.5, 0) * (w,h).
    corners3 = [
        (-0.5 * w, -0.5 * h, 0.0),  # TL
        (+0.5 * w, -0.5 * h, 0.0),  # TR
        (+0.5 * w, +0.5 * h, 0.0),  # BR
        (-0.5 * w, +0.5 * h, 0.0),  # BL
    ]
    f = dist_widths * w            # focal length / camera distance, in pixels
    ca, sa = math.cos(a), math.sin(a)
    proj = []
    for (X, Y, Z) in corners3:
        # Rotate about Y (vertical) axis.
        Xr = ca * X + sa * Z
        Zr = -sa * X + ca * Z
        Yr = Y
        # Pinhole projection: camera looks down +Z from -f.
        zc = Zr + f
        px = f * Xr / zc
        py = f * Yr / zc
        proj.append((px, py))
    # Recentre + the projection already preserves scale ~1 at the centre because
    # focal length == distance; shift so the quad sits in the w x h canvas.
    cx = sum(p[0] for p in proj) / 4.0
    cy = sum(p[1] for p in proj) / 4.0
    return [(p[0] - cx + w / 2.0, p[1] - cy + h / 2.0) for p in proj]


def dst_corners_shear(w: int, h: int, angle_deg: float):
    """Reproduce degrade.py's one-edge-pinned sin() shear exactly."""
    a = math.radians(float(angle_deg))
    shrink = math.sin(a)
    dx = (w * 0.5) * shrink
    dy = (h * 0.5) * shrink * 0.5
    return [(0, 0), (w - dx, dy), (w - dx, h - dy), (0, h)]


def warp(img: Image.Image, angle_deg: float, quiet_modules: int,
         mode: str, dist_widths: float) -> Image.Image:
    padded = pad_quiet_zone(img, quiet_modules)
    w, h = padded.size
    src = [(0, 0), (w, 0), (w, h), (0, h)]
    if mode == "pinhole":
        dst = dst_corners_pinhole(w, h, angle_deg, dist_widths)
    elif mode == "shear":
        dst = dst_corners_shear(w, h, angle_deg)
    else:
        raise ValueError(f"unknown mode {mode!r}")
    coeffs = _solve_inverse_coeffs(src, dst)
    return padded.transform((w, h), Image.PERSPECTIVE, coeffs,
                            resample=Image.BICUBIC, fillcolor=WHITE)


def main() -> int:
    ap = argparse.ArgumentParser(description="Realistic perspective sweep")
    ap.add_argument("--input", required=True,
                    help="dir of clean nc{0..7}-*.png source symbols")
    ap.add_argument("--output", required=True, help="dir for warped PNGs + manifest")
    ap.add_argument("--angles", default="0,5,10,15,20,25,30,35,40,45,50",
                    help="comma-separated tilt angles in degrees")
    ap.add_argument("--quiet-zone", type=int, default=12,
                    help="white quiet-zone modules (default 12)")
    ap.add_argument("--mode", choices=["pinhole", "shear"], default="pinhole")
    ap.add_argument("--dist", type=float, default=3.0,
                    help="pinhole camera distance in symbol widths (default 3)")
    args = ap.parse_args()

    angles = [float(x) for x in args.angles.split(",") if x.strip() != ""]
    syms = sorted(n for n in os.listdir(args.input)
                  if n.lower().endswith(".png") and NC_RE.search(n))
    if not syms:
        ap.error(f"no nc<N>-*.png symbols in {args.input}")

    os.makedirs(args.output, exist_ok=True)
    man = os.path.join(args.output, "manifest.jsonl")
    n = 0
    with open(man, "w") as mf:
        for name in syms:
            nc = parse_nc(name)
            base = os.path.splitext(name)[0]
            clean = Image.open(os.path.join(args.input, name)).convert("RGB")
            for ang in angles:
                out_img = warp(clean, ang, args.quiet_zone, args.mode, args.dist)
                astr = f"{ang:g}"
                out_name = f"{base}__persp{args.mode}_{astr}deg.png"
                out_img.save(os.path.join(args.output, out_name))
                rec = {
                    "id": f"{base}__{args.mode}_{astr}",
                    "file": os.path.abspath(os.path.join(args.output, out_name)),
                    "nc": nc,
                    "payload_known": True,
                    "payload_sha256": PAYLOAD_SHA256[nc],
                    "medium": "synthetic",
                    "conditions": f"{args.mode}_{astr}deg",
                    "angle": ang,
                    "mode": args.mode,
                    "quiet_zone": args.quiet_zone,
                }
                mf.write(json.dumps(rec) + "\n")
                n += 1
    print(f"wrote {n} images ({args.mode}, qz={args.quiet_zone}mod, "
          f"angles={angles}) -> {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
