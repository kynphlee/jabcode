#!/usr/bin/env python3
"""
R3 Lever B (best-case assist) — perspective-rectify the fused image, then decode.

The deep-cluster fused images reach the decoder but die at DETECT "Sampling
master symbol failed" — i.e. finders are found but the keystone perspective
defeats matrix sampling. This gives fusion its strongest possible shot: detect
the printed-code quadrilateral and warp it to a frontal square (homography)
before decoding. If even a rectified fused image won't decode, geometry
correction at capture (the other half of R3) doesn't rescue this capture either.

We detect the code quad by: saturation mask (the colourful code vs pale paper/
UI) -> largest contour -> minAreaRect / 4-point poly -> warpPerspective to a
square. We try a few output sizes (the module pitch is unknown) and emit each.
"""
import argparse, json, os
import numpy as np
import cv2


def find_quad(bgr):
    hsv = cv2.cvtColor(bgr, cv2.COLOR_BGR2HSV)
    sat = hsv[:, :, 1]
    _, m = cv2.threshold(sat, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE,
                         cv2.getStructuringElement(cv2.MORPH_RECT, (31, 31)))
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN,
                         cv2.getStructuringElement(cv2.MORPH_RECT, (9, 9)))
    cnts, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not cnts:
        return None
    c = max(cnts, key=cv2.contourArea)
    rect = cv2.minAreaRect(c)
    box = cv2.boxPoints(rect)
    return order_quad(box)


def order_quad(pts):
    pts = np.array(pts, dtype=np.float32)
    s = pts.sum(1); d = np.diff(pts, axis=1).ravel()
    return np.array([pts[np.argmin(s)], pts[np.argmin(d)],
                     pts[np.argmax(s)], pts[np.argmax(d)]], dtype=np.float32)  # tl,tr,br,bl


def rectify(bgr, quad, size):
    dst = np.array([[0, 0], [size - 1, 0], [size - 1, size - 1], [0, size - 1]],
                   dtype=np.float32)
    H = cv2.getPerspectiveTransform(quad, dst)
    return cv2.warpPerspective(bgr, H, (size, size), flags=cv2.INTER_CUBIC,
                               borderValue=(255, 255, 255))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--inputs", required=True, help="comma list of fused image paths")
    ap.add_argument("--outdir", required=True)
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--tag", required=True)
    ap.add_argument("--sizes", default="512,768,1024")
    args = ap.parse_args()
    os.makedirs(args.outdir, exist_ok=True)
    sizes = [int(s) for s in args.sizes.split(",")]

    rows = []
    for inp in args.inputs.split(","):
        inp = inp.strip()
        bgr = cv2.imread(inp)
        if bgr is None:
            print("skip (unreadable):", inp); continue
        quad = find_quad(bgr)
        if quad is None:
            print("no quad:", inp); continue
        base = os.path.splitext(os.path.basename(inp))[0]
        for sz in sizes:
            rec = rectify(bgr, quad, sz)
            name = f"{base}-rect{sz}"
            p = os.path.abspath(os.path.join(args.outdir, name + ".png"))
            cv2.imwrite(p, rec)
            rows.append((name, p))
            print("wrote", name)
    with open(args.manifest, "w") as o:
        for name, p in rows:
            o.write(json.dumps({"id": name, "file": p, "payload_known": False,
                                "medium": "camera", "conditions": args.tag}) + "\n")
    print(f"manifest -> {args.manifest} ({len(rows)} entries)")


if __name__ == "__main__":
    main()
