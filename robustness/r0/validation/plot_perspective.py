#!/usr/bin/env python3
"""Plot the perspective-tilt validation curves.

Reads the rig aggregates this validation produced and renders one figure with:

  (a) decode-rate vs tilt for the committed corpus (qz=4) vs the corrected
      realistic warps (pinhole qz=12, shear qz=12) -- the headline before/after;
  (b) the degrade.py-exact warp at increasing quiet zones (qz 4/8/12/16),
      isolating the quiet-zone as the cause;
  (c) the QR (zxing-cpp) yardstick on the identical pinhole homography.

Outputs perspective_curve.png.
"""
from __future__ import annotations

import csv
import json
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "results")


def agg_curve(path, prefix):
    """Return (angles, rates) from a rig aggregate, conditions like '<prefix>_<a>deg'."""
    d = json.load(open(path))
    pts = []
    for label, b in d["by_condition"].items():
        cond = label.split("=", 1)[1] if "=" in label else label
        if not cond.startswith(prefix):
            continue
        a = float(cond[len(prefix):].replace("deg", "").lstrip("_"))
        pts.append((a, b["decode_rate"] * 100.0))
    pts.sort()
    return [p[0] for p in pts], [p[1] for p in pts]


def degrade_qz_curves(path):
    """qz{N}_{angle}deg -> {qz: (angles, rates)}."""
    d = json.load(open(path))
    by_qz = {}
    for label, b in d["by_condition"].items():
        cond = label.split("=", 1)[1] if "=" in label else label
        qz_part, ang_part = cond.split("_", 1)
        qz = int(qz_part.replace("qz", ""))
        a = float(ang_part.replace("deg", ""))
        by_qz.setdefault(qz, []).append((a, b["decode_rate"] * 100.0))
    return {qz: ([p[0] for p in sorted(v)], [p[1] for p in sorted(v)])
            for qz, v in by_qz.items()}


def qr_curve(path):
    angs, oks = [], []
    with open(path) as f:
        r = csv.DictReader(f)
        for row in r:
            angs.append(float(row["angle_deg"]))
            oks.append(float(row["decoded"]) * 100.0)
    return angs, oks


def main():
    fig, axes = plt.subplots(1, 3, figsize=(16.5, 5.2))

    # --- (a) headline before/after -------------------------------------------
    ax = axes[0]
    a_pin, r_pin = agg_curve(os.path.join(RES, "sweep_pinhole.aggregate.json"), "pinhole")
    a_sh, r_sh = agg_curve(os.path.join(RES, "sweep_shear-qz12.aggregate.json"), "shear")
    a_cm, r_cm = agg_curve(os.path.join(RES, "committed_perspective.aggregate.json"), "perspective")
    ax.plot(a_pin, r_pin, "o-", color="#1b7837", lw=2.4, label="pinhole, qz=12 (realistic)")
    ax.plot(a_sh, r_sh, "s--", color="#2166ac", lw=2.0, label="shear, qz=12")
    ax.scatter(a_cm, r_cm, color="#b2182b", s=90, zorder=5, marker="X",
               label="COMMITTED corpus (shear, qz=4)")
    ax.set_title("(a) Corrected decode-rate vs tilt\n(8 Nc averaged)")
    ax.set_xlabel("tilt angle (deg)")
    ax.set_ylabel("decode rate (%)")
    ax.set_ylim(-4, 104)
    ax.grid(alpha=0.3)
    ax.legend(fontsize=8.5, loc="lower left")
    ax.axvspan(0, 30, color="#1b7837", alpha=0.06)

    # --- (b) quiet-zone isolation --------------------------------------------
    ax = axes[1]
    qzc = degrade_qz_curves(os.path.join(RES, "degrade_qz.aggregate.json"))
    colors = {4: "#b2182b", 8: "#ef8a62", 12: "#67a9cf", 16: "#2166ac"}
    for qz in sorted(qzc):
        a, r = qzc[qz]
        style = "X-" if qz == 4 else "o-"
        ax.plot(a, r, style, color=colors.get(qz, "gray"), lw=2.2,
                label=f"quiet zone = {qz} modules" + ("  (committed)" if qz == 4 else ""))
    ax.set_title("(b) Same degrade.py warp, varying quiet zone\n"
                 "0%->88% at 20deg just by widening the margin")
    ax.set_xlabel("tilt angle (deg)")
    ax.set_ylabel("decode rate (%)")
    ax.set_ylim(-4, 104)
    ax.grid(alpha=0.3)
    ax.legend(fontsize=8.5, loc="upper right")

    # --- (c) QR yardstick ----------------------------------------------------
    ax = axes[2]
    qa, qr = qr_curve(os.path.join(RES, "qr_yardstick.csv"))
    qa2, qr2 = qr_curve(os.path.join(RES, "qr_yardstick_high.csv"))
    allq = sorted(zip(qa + qa2, qr + qr2))
    ax.plot([p[0] for p in allq], [p[1] for p in allq], "o-", color="#762a83",
            lw=2.4, label="QR (zxing-cpp)")
    # overlay jabcode best (pinhole) for the same homography
    ax.plot(a_pin, r_pin, "s--", color="#1b7837", lw=1.8, alpha=0.8,
            label="jabcode best (pinhole)")
    ax.axvline(66, color="#762a83", ls=":", lw=1.4)
    ax.text(66, 50, " QR last-pass 66deg", color="#762a83", fontsize=8, rotation=90, va="center")
    ax.set_title("(c) Maturity yardstick, identical homography\n"
                 "mature reader holds to ~66deg")
    ax.set_xlabel("tilt angle (deg)")
    ax.set_ylabel("decode rate (%)")
    ax.set_ylim(-4, 104)
    ax.grid(alpha=0.3)
    ax.legend(fontsize=8.5, loc="lower left")

    fig.suptitle("R0 perspective-tilt validation: committed 0%@20deg is a quiet-zone ARTIFACT; "
                 "true jabcode tilt threshold ~30deg (realistic warp)",
                 fontsize=12, y=1.02)
    fig.tight_layout()
    out = os.path.join(HERE, "perspective_curve.png")
    fig.savefig(out, dpi=130, bbox_inches="tight")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
