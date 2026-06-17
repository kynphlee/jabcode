#!/usr/bin/env python3
"""QR maturity yardstick for the perspective validation.

Encodes a payload comparable to the JABCode corpus as a QR code (segno), applies
the SAME pinhole tilt ladder used for the jabcode sweep (identical homography,
identical generous quiet zone), and decodes each tilt with zxing-cpp -- a mature,
production-grade reader with full perspective recovery. The tilt at which zxing
fails is the reference point for "how much tilt tolerance is normal", against
which jabcode's threshold can be judged.

Not a strict apples-to-apples (QR is 1-bit, jabcode is up to 8-colour), but it
calibrates the warp itself: if a mature reader holds to a far higher angle on the
identical transform, jabcode's lower threshold is a real decoder gap; if zxing
also falls over early, the warp is simply severe.

Uses segno + zxingcpp + Pillow + numpy (all in /tmp/bench-venv).
"""
from __future__ import annotations

import argparse
import io
import math

import numpy as np
import segno
import zxingcpp
from PIL import Image

from perspective_sweep import dst_corners_pinhole, _solve_inverse_coeffs, SRC_PX_PER_MODULE

WHITE = (255, 255, 255)


def render_qr(payload: str, scale: int, border_modules: int) -> Image.Image:
    """Render a QR with `scale` px/module and a quiet zone of `border_modules`."""
    qr = segno.make(payload, error="m")
    buf = io.BytesIO()
    qr.save(buf, kind="png", scale=scale, border=border_modules, dark="black",
            light="white")
    buf.seek(0)
    return Image.open(buf).convert("RGB")


def warp_pinhole(img: Image.Image, angle_deg: float, dist_widths: float) -> Image.Image:
    w, h = img.size
    src = [(0, 0), (w, 0), (w, h), (0, h)]
    dst = dst_corners_pinhole(w, h, angle_deg, dist_widths)
    coeffs = _solve_inverse_coeffs(src, dst)
    return img.transform((w, h), Image.PERSPECTIVE, coeffs,
                         resample=Image.BICUBIC, fillcolor=WHITE)


def decode_qr(img: Image.Image) -> bool:
    res = zxingcpp.read_barcodes(img)
    return bool(res) and res[0].valid and res[0].text != ""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--payload", default="HELLO-Nc-1")
    ap.add_argument("--angles", default="0,5,10,15,20,25,30,35,40,45,50,55,60")
    ap.add_argument("--scale", type=int, default=12, help="px/module (match jabcode source)")
    ap.add_argument("--border", type=int, default=12, help="quiet-zone modules")
    ap.add_argument("--dist", type=float, default=3.0)
    ap.add_argument("--out-csv", default="results/qr_yardstick.csv")
    args = ap.parse_args()

    import os
    os.makedirs(os.path.dirname(os.path.abspath(args.out_csv)), exist_ok=True)

    angles = [float(x) for x in args.angles.split(",") if x.strip() != ""]
    base = render_qr(args.payload, args.scale, args.border)
    rows = [("angle_deg", "decoded")]
    print(f"QR yardstick: payload={args.payload!r} module={args.scale}px "
          f"border={args.border}mod size={base.size}")
    for ang in angles:
        ok = decode_qr(warp_pinhole(base, ang, args.dist))
        rows.append((f"{ang:g}", "1" if ok else "0"))
        print(f"  {ang:5g} deg : {'DECODE' if ok else 'fail'}")
    with open(args.out_csv, "w") as f:
        for r in rows:
            f.write(",".join(map(str, r)) + "\n")
    print(f"-> {args.out_csv}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
