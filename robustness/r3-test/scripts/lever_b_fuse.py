#!/usr/bin/env python3
"""
R3 Lever B, step 2 — register + fuse a cluster of frames, then compare the fused
image against the best single frame in the rig.

Pipeline (all on the colourful preview band y[360:970] of the 1080-wide frame):
  1. SELECT a contiguous, in-focus, code-in-view cluster (passed via --frames).
  2. REGISTER every frame to the sharpest one in the cluster. We align on the
     luminance of the band using OpenCV ECC (affine) — robust to the small
     handheld translation/rotation/scale jitter between consecutive frames.
     Frames whose alignment doesn't converge are dropped (mis-registration
     would smear the fusion).
  3. FUSE the registered stack three ways:
        * mean   — denoises (sqrt(N) noise reduction), softens edges
        * median — denoises + rejects transient blur/outliers, keeps edges
        * sharpened-mean — mean then an unsharp mask (cheap super-resolution-ish
          edge restoration to counter the mean's softening)
  4. WRITE the fused PNGs + a rig manifest including the fused images AND the
     best single registered frame (the honest single-frame baseline), so the
     rig grades fused-vs-single on the same footing.

This tests whether multi-frame fusion lifts a 0/N single-frame case.
"""
import argparse, glob, json, os
import numpy as np
import cv2

BAND = (360, 970)   # preview-band rows in the 1080x2340 frame


def load_band(path):
    img = cv2.imread(path, cv2.IMREAD_COLOR)
    return img[BAND[0]:BAND[1], 0:1080]


def sharpness(bgr):
    return cv2.Laplacian(cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY), cv2.CV_64F).var()


def register_to(ref_gray, mov_bgr):
    """ECC-align mov to ref (affine). Return warped BGR or None if no convergence."""
    mov_gray = cv2.cvtColor(mov_bgr, cv2.COLOR_BGR2GRAY).astype(np.float32)
    warp = np.eye(2, 3, dtype=np.float32)
    crit = (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 200, 1e-5)
    try:
        _, warp = cv2.findTransformECC(ref_gray, mov_gray, warp,
                                       cv2.MOTION_AFFINE, crit, None, 5)
    except cv2.error:
        return None
    h, w = ref_gray.shape
    return cv2.warpAffine(mov_bgr, warp, (w, h),
                          flags=cv2.INTER_LINEAR + cv2.WARP_INVERSE_MAP,
                          borderMode=cv2.BORDER_REFLECT)


def unsharp(bgr, amount=1.5, sigma=1.2):
    blur = cv2.GaussianBlur(bgr, (0, 0), sigma)
    return cv2.addWeighted(bgr, 1 + amount, blur, -amount, 0)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--indir", required=True)
    ap.add_argument("--frames", required=True,
                    help="comma list of frame basenames w/o ext, e.g. f_024,f_027,...")
    ap.add_argument("--outdir", required=True)
    ap.add_argument("--tag", required=True)
    ap.add_argument("--manifest", required=True)
    args = ap.parse_args()
    os.makedirs(args.outdir, exist_ok=True)

    names = [n.strip() for n in args.frames.split(",") if n.strip()]
    bands = [(n, load_band(os.path.join(args.indir, n + ".png"))) for n in names]
    # reference = sharpest frame in the cluster
    ref_name, ref_bgr = max(bands, key=lambda nb: sharpness(nb[1]))
    ref_gray = cv2.cvtColor(ref_bgr, cv2.COLOR_BGR2GRAY).astype(np.float32)

    stack = [ref_bgr.astype(np.float32)]
    kept = [ref_name]
    for n, b in bands:
        if n == ref_name:
            continue
        w = register_to(ref_gray, b)
        if w is not None:
            stack.append(w.astype(np.float32))
            kept.append(n)
    arr = np.stack(stack, 0)
    print(f"cluster {names[0]}..{names[-1]}: ref={ref_name}  registered {len(kept)}/{len(names)} frames")

    mean = np.clip(arr.mean(0), 0, 255).astype(np.uint8)
    median = np.clip(np.median(arr, 0), 0, 255).astype(np.uint8)
    smean = unsharp(mean)

    # honest single-frame baseline: the (registered) sharpest reference frame
    single = ref_bgr

    outs = {
        f"{args.tag}-single": single,
        f"{args.tag}-mean": mean,
        f"{args.tag}-median": median,
        f"{args.tag}-sharpmean": smean,
    }
    rows = []
    for name, im in outs.items():
        p = os.path.abspath(os.path.join(args.outdir, name + ".png"))
        cv2.imwrite(p, im)
        rows.append((name, p))
        print(f"  wrote {name}  sharp={sharpness(im):.0f} -> {p}")

    with open(args.manifest, "w") as o:
        for name, p in rows:
            o.write(json.dumps({"id": name, "file": p, "payload_known": False,
                                "medium": "camera", "conditions": args.tag}) + "\n")
    print(f"manifest -> {args.manifest}")


if __name__ == "__main__":
    main()
