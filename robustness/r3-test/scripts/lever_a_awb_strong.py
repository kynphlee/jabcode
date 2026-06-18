#!/usr/bin/env python3
"""
R3 Lever A (aggressive variants) — does a STRONGER capture-side WB help?

The conservative luma-anchored WB (lever_a_awb.py) left decode-rate at 13%.
To rule out under-correction as the reason, this tries two more aggressive
full-image, pre-detection normalisations:

  * white-to-255 : map the estimated paper white point to pure white (255,255,
                   255) per channel — full Von-Kries-style white-point removal,
                   NO luma preservation. Maximal tint removal.
  * per-chan-stretch : independent per-channel min/max histogram stretch
                   (1st..99th pct -> 0..255). Maximises per-channel contrast,
                   which is what the colour-LDPC palette decode keys on.

Still strictly a capture-side, whole-frame operation applied BEFORE the decoder.
"""
import argparse, glob, json, os
import numpy as np
from PIL import Image

LUMA = np.array([0.299, 0.587, 0.114])


def white_to_255(arr):
    flat = arr.reshape(-1, 3).astype(np.float64)
    lum = flat @ LUMA
    white = flat[lum >= np.percentile(lum, 98)].mean(0)
    gains = 255.0 / np.maximum(white, 1e-6)
    out = arr.astype(np.float64) * gains[None, None, :]
    return np.clip(out, 0, 255).astype(np.uint8)


def per_chan_stretch(arr):
    out = np.empty_like(arr, dtype=np.float64)
    for c in range(3):
        ch = arr[..., c].astype(np.float64)
        lo, hi = np.percentile(ch, 1), np.percentile(ch, 99)
        if hi <= lo:
            out[..., c] = ch
        else:
            out[..., c] = (ch - lo) * (255.0 / (hi - lo))
    return np.clip(out, 0, 255).astype(np.uint8)


METHODS = {"white-to-255": white_to_255, "per-chan-stretch": per_chan_stretch}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--indir", required=True)
    ap.add_argument("--outdir", required=True)
    ap.add_argument("--method", choices=list(METHODS), required=True)
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--conditions", required=True)
    ap.add_argument("--glob", default="*.png")
    args = ap.parse_args()

    os.makedirs(args.outdir, exist_ok=True)
    fn = METHODS[args.method]
    rows = []
    for f in sorted(glob.glob(os.path.join(args.indir, args.glob))):
        arr = np.asarray(Image.open(f).convert("RGB"))
        corr = fn(arr)
        base = os.path.splitext(os.path.basename(f))[0]
        outp = os.path.abspath(os.path.join(args.outdir, base + ".png"))
        Image.fromarray(corr).save(outp)
        rows.append((base, outp))
    with open(args.manifest, "w") as o:
        for base, outp in rows:
            o.write(json.dumps({"id": f"{args.conditions}-{base}", "file": outp,
                                "payload_known": False, "medium": "print",
                                "conditions": args.conditions}) + "\n")
    print(f"{args.method}: {len(rows)} images -> {args.outdir}; manifest {args.manifest}")


if __name__ == "__main__":
    main()
