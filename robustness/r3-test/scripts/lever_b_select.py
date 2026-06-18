#!/usr/bin/env python3
"""
R3 Lever B, step 1 — frame quality scoring + well-framed selection.

The ws5 video is a 39 s handheld capture of ONE printed Nc1 code; single frames
decode 0/5 in the rig (all DETECT: finder patterns not found). Before fusing we
must SELECT well-framed, in-focus frames and locate the code region, so the
fusion is aligned and not averaging blur+background.

Per frame we compute:
  * sharpness  = variance of the Laplacian (higher = crisper; rejects motion/OOF blur)
  * code bbox  = bounding box of the high-contrast central region (the code is a
                 dark/printed patch on lighter background). We threshold on local
                 gradient energy and take the largest connected component near the
                 image centre.
  * fill / aspect of that bbox (a well-framed code is large & roughly square).

Outputs a CSV (frame, sharpness, bbox, fill, score) sorted by a combined score,
so step 2 can take the top-K for registration+fusion.
"""
import argparse, csv, glob, os
import numpy as np
import cv2


def score_frame(path):
    img = cv2.imread(path, cv2.IMREAD_COLOR)
    if img is None:
        return None
    h, w = img.shape[:2]
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # --- sharpness: variance of Laplacian on a centre crop (code lives centrally)
    cy0, cy1 = int(h * 0.20), int(h * 0.80)
    cx0, cx1 = int(w * 0.10), int(w * 0.90)
    centre = gray[cy0:cy1, cx0:cx1]
    sharp = float(cv2.Laplacian(centre, cv2.CV_64F).var())

    # --- code region: gradient energy -> threshold -> largest central blob
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    mag = cv2.magnitude(gx, gy)
    magn = cv2.normalize(mag, None, 0, 255, cv2.NORM_MINMAX).astype(np.uint8)
    _, th = cv2.threshold(magn, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    th = cv2.morphologyEx(th, cv2.MORPH_CLOSE,
                          cv2.getStructuringElement(cv2.MORPH_RECT, (25, 25)))
    cnts, _ = cv2.findContours(th, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    bbox = (0, 0, 0, 0); fill = 0.0; aspect = 0.0
    if cnts:
        cx, cyc = w / 2, h / 2
        best = None; best_score = -1
        for c in cnts:
            x, y, bw, bh = cv2.boundingRect(c)
            if bw < w * 0.10 or bh < h * 0.05:
                continue
            bcx, bcy = x + bw / 2, y + bh / 2
            dist = ((bcx - cx) ** 2 + (bcy - cyc) ** 2) ** 0.5
            area = bw * bh
            s = area / (1 + dist)          # large & central
            if s > best_score:
                best_score = s; best = (x, y, bw, bh)
        if best:
            x, y, bw, bh = best
            bbox = best
            fill = (bw * bh) / float(w * h)
            aspect = bw / float(bh) if bh else 0.0

    # combined score: reward sharpness * fill, penalise non-square framing
    squareness = 1.0 - min(abs(aspect - 1.0), 1.0) if aspect else 0.0
    score = sharp * (fill ** 0.5) * (0.5 + 0.5 * squareness)
    return dict(frame=os.path.basename(path), sharp=round(sharp, 1),
                bx=bbox[0], by=bbox[1], bw=bbox[2], bh=bbox[3],
                fill=round(fill, 4), aspect=round(aspect, 3),
                score=round(score, 2))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--indir", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--glob", default="*.png")
    args = ap.parse_args()
    rows = []
    for f in sorted(glob.glob(os.path.join(args.indir, args.glob))):
        r = score_frame(f)
        if r:
            rows.append(r)
    rows.sort(key=lambda r: r["score"], reverse=True)
    with open(args.out, "w", newline="") as o:
        wtr = csv.DictWriter(o, fieldnames=list(rows[0].keys()))
        wtr.writeheader(); wtr.writerows(rows)
    print(f"scored {len(rows)} frames -> {args.out}")
    print("top 12 by score:")
    for r in rows[:12]:
        print(f"  {r['frame']}  sharp={r['sharp']:>7}  fill={r['fill']:.3f}  "
              f"aspect={r['aspect']:.2f}  bbox=({r['bx']},{r['by']},{r['bw']},{r['bh']})  score={r['score']}")


if __name__ == "__main__":
    main()
