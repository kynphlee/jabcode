#!/usr/bin/env python3
"""
R3 Lever A — CAPTURE-SIDE white-balance / cast-correction.

This is a FULL-IMAGE, PRE-DETECTION white balance: it estimates a single
per-channel gain for the whole frame and applies it once, BEFORE the decoder
runs (i.e. before finder-pattern detection / palette classification). It models
what a camera ISP / capture pipeline would do at acquisition time.

>> DISTINCT from the R1 decode-side lever (which R1 refuted): R1 re-balanced
   colour PER MODULE inside the decoder, after detection, using the sampled
   module colours. That had no payload-known reference for "white" and operated
   on the already-quantised palette. Here we correct the RAW capture globally,
   using the paper background / scene statistics as the white reference, exactly
   as a capture-side AWB would — and hand the decoder a neutralised image.

Two estimators (selectable):
  * gray-world : assume the scene average is neutral; gain = meanY/meanC.
  * paper-white: estimate the white point from the brightest ~2% of pixels
                 (the unprinted warm paper / specular paper highlights) and map
                 it to neutral; gain = whiteRef/whiteC. This is the principled
                 one for COA prints — the substrate IS a near-white reference.

Gains are applied in linear-ish 8-bit space with a luminance-preserving
normalisation (we scale chroma channels toward the green/luma anchor, we do not
brighten overall), then clipped to [0,255]. Output PNGs are written to --outdir;
inputs are treated as READ-ONLY.
"""
import argparse, glob, json, os
import numpy as np
from PIL import Image

LUMA = np.array([0.299, 0.587, 0.114])


def estimate_gains(arr, method):
    """Return per-channel multiplicative gains (R,G,B) that neutralise the cast.

    We anchor to the GREEN channel (and overall luma) so the correction is a
    chroma rotation, not an exposure change — this keeps module contrast intact
    while removing the colour tint that confuses palette/LDPC decoding.
    """
    flat = arr.reshape(-1, 3).astype(np.float64)
    if method == "gray-world":
        means = flat.mean(0)
        anchor = means.mean()                 # neutral target = overall mean
        gains = anchor / np.maximum(means, 1e-6)
    elif method == "paper-white":
        lum = flat @ LUMA
        thr = np.percentile(lum, 98)          # brightest ~2% == paper / highlights
        white = flat[lum >= thr].mean(0)
        anchor = white.mean()                 # map the (tinted) white to neutral gray
        gains = anchor / np.maximum(white, 1e-6)
    else:
        raise ValueError(method)
    # Keep the green/luma level fixed: re-normalise so the gain on the luma
    # of a neutral pixel is ~1 (correction is a tint removal, not a brighten).
    gains = gains / (gains @ LUMA)
    return gains


def apply_wb(arr, gains):
    out = arr.astype(np.float64) * gains[None, None, :]
    return np.clip(out, 0, 255).astype(np.uint8)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--indir", required=True)
    ap.add_argument("--outdir", required=True)
    ap.add_argument("--method", choices=["gray-world", "paper-white"],
                    default="paper-white")
    ap.add_argument("--manifest", help="write a rig manifest here (optional)")
    ap.add_argument("--conditions", default="tier2-awb")
    ap.add_argument("--glob", default="*.png")
    args = ap.parse_args()

    os.makedirs(args.outdir, exist_ok=True)
    fs = sorted(glob.glob(os.path.join(args.indir, args.glob)))
    rows = []
    for f in fs:
        im = Image.open(f).convert("RGB")
        arr = np.asarray(im)
        gains = estimate_gains(arr, args.method)
        corr = apply_wb(arr, gains)
        base = os.path.splitext(os.path.basename(f))[0]
        outp = os.path.abspath(os.path.join(args.outdir, base + ".png"))
        Image.fromarray(corr).save(outp)
        rows.append((base, outp, gains))
        print(f"{base:<22} gains R={gains[0]:.3f} G={gains[1]:.3f} B={gains[2]:.3f} -> {outp}")

    if args.manifest:
        with open(args.manifest, "w") as o:
            for base, outp, _ in rows:
                o.write(json.dumps({
                    "id": f"awb-{base}", "file": outp, "payload_known": False,
                    "medium": "print", "conditions": args.conditions}) + "\n")
        print(f"\nmanifest -> {args.manifest} ({len(rows)} entries)")


if __name__ == "__main__":
    main()
