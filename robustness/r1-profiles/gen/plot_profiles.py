#!/usr/bin/env python3
"""Render the R1 per-medium profile charts from the rig measurement CSVs.

Inputs  (robustness/r1-profiles/data/):
    decode_rates.csv   (nc, colors, ecc, family, param, n, decoded, decode_rate)
    grid_density.csv   (nc, colors, ecc, side, image_px, area_modules, area_vs_min)

Outputs (robustness/r1-profiles/charts/):
    decode_rate_vs_nc.png    decode-rate vs Nc, one line per family (ecc-averaged)
                             -- the headline "colour count dominates" chart
    decode_rate_vs_ecc.png   decode-rate vs ECC, one line per family
                             -- shows ECC is a weak second-order lever here
    robustness_vs_density.png overall robustness vs Nc with the density (area)
                             cost overlaid -- the tradeoff in one frame

A matplotlib venv exists at /tmp/bench-venv:
    /tmp/bench-venv/bin/python plot_profiles.py
"""
import csv
import os
from collections import defaultdict

import matplotlib
matplotlib.use("Agg")  # headless: write PNGs, never open a window
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
R1_DIR = os.path.dirname(HERE)
DATA_DIR = os.path.join(R1_DIR, "data")
CHARTS_DIR = os.path.join(R1_DIR, "charts")

FAMILIES = ["chroma", "lighting", "blur", "downscale"]
# colourblind-friendly (Paul Tol), matching the repo's other charts
FAM_COLOR = {"chroma": "#EE6677", "lighting": "#CCBB44",
             "blur": "#4477AA", "downscale": "#228833"}
FAM_LABEL = {"chroma": "chroma (colour wash)", "lighting": "lighting (illum. ramp)",
             "blur": "blur (defocus)", "downscale": "downscale (low-res)"}
NCS = [1, 2, 3, 5, 7]
ECCS = [3, 5, 8]


def load_rates():
    rows = list(csv.DictReader(open(os.path.join(DATA_DIR, "decode_rates.csv"))))
    for r in rows:
        r["nc"] = int(r["nc"]); r["ecc"] = int(r["ecc"])
        r["colors"] = int(r["colors"]); r["decode_rate"] = float(r["decode_rate"])
    return rows


def load_density():
    d = {}
    for r in csv.DictReader(open(os.path.join(DATA_DIR, "grid_density.csv"))):
        d[(int(r["nc"]), int(r["ecc"]))] = {
            "side": int(r["side"]), "area": int(r["area_modules"]),
            "area_vs_min": float(r["area_vs_min"])}
    return d


def mean(xs):
    xs = list(xs)
    return sum(xs) / len(xs) if xs else float("nan")


def nc_ticklabels():
    return [f"{nc}\n({1<<(nc+1)}c)" for nc in NCS]


def chart_rate_vs_nc(rows):
    """decode-rate vs Nc, one line per family (averaged over ECC + params)."""
    fig, ax = plt.subplots(figsize=(8, 5))
    for fam in FAMILIES:
        ys = [mean(r["decode_rate"] for r in rows if r["family"] == fam and r["nc"] == nc)
              for nc in NCS]
        ax.plot(NCS, [y * 100 for y in ys], "-o", color=FAM_COLOR[fam],
                label=FAM_LABEL[fam], linewidth=2, markersize=7)
    ax.set_xticks(NCS); ax.set_xticklabels(nc_ticklabels())
    ax.set_xlabel("Nc  (colour-count index;  colours = 2^(Nc+1))")
    ax.set_ylabel("decode-rate  (%)  — mean over ECC {3,5,8} × param ladder")
    ax.set_title("Robustness vs colour count, per degradation family\n"
                 "(fixed 80 B payload, R0 rig, 5 payloads/cell)")
    ax.set_ylim(-3, 105); ax.grid(True, alpha=0.3); ax.legend(loc="lower left")
    ax.axhspan(0, 100, color="none")
    fig.tight_layout()
    out = os.path.join(CHARTS_DIR, "decode_rate_vs_nc.png")
    fig.savefig(out, dpi=130); plt.close(fig)
    return out


def chart_rate_vs_ecc(rows):
    """decode-rate vs ECC, one line per family (averaged over Nc + params)."""
    fig, ax = plt.subplots(figsize=(8, 5))
    for fam in FAMILIES:
        ys = [mean(r["decode_rate"] for r in rows if r["family"] == fam and r["ecc"] == e)
              for e in ECCS]
        ax.plot(ECCS, [y * 100 for y in ys], "-o", color=FAM_COLOR[fam],
                label=FAM_LABEL[fam], linewidth=2, markersize=7)
    # overall mean across all families, to show the flatness of the ECC axis
    allys = [mean(r["decode_rate"] for r in rows if r["ecc"] == e) for e in ECCS]
    ax.plot(ECCS, [y * 100 for y in allys], "--k", label="all families (mean)",
            linewidth=2, markersize=6, marker="s")
    ax.set_xticks(ECCS)
    ax.set_xlabel("ECC level  (LDPC, higher = more parity, larger symbol)")
    ax.set_ylabel("decode-rate  (%)  — mean over Nc × param ladder")
    ax.set_title("Robustness vs ECC level, per degradation family\n"
                 "ECC is a weak second-order lever for these colour/luminance threats")
    ax.set_ylim(-3, 105); ax.grid(True, alpha=0.3); ax.legend(loc="lower left")
    fig.tight_layout()
    out = os.path.join(CHARTS_DIR, "decode_rate_vs_ecc.png")
    fig.savefig(out, dpi=130); plt.close(fig)
    return out


def chart_robustness_vs_density(rows, density):
    """Overall robustness vs Nc (left axis) with density area-cost (right axis)."""
    fig, ax1 = plt.subplots(figsize=(8, 5))

    overall = [mean(r["decode_rate"] for r in rows if r["nc"] == nc) for nc in NCS]
    l1, = ax1.plot(NCS, [y * 100 for y in overall], "-o", color="#AA3377",
                   linewidth=2.5, markersize=8, label="overall decode-rate")
    ax1.set_xticks(NCS); ax1.set_xticklabels(nc_ticklabels())
    ax1.set_xlabel("Nc  (colour-count index;  colours = 2^(Nc+1))")
    ax1.set_ylabel("overall decode-rate (%)  — mean over all families × ECC",
                   color="#AA3377")
    ax1.tick_params(axis="y", labelcolor="#AA3377")
    ax1.set_ylim(0, 105); ax1.grid(True, alpha=0.3)

    # density cost: AUTO side area at ecc3 relative to the smallest cell
    ax2 = ax1.twinx()
    area = [density[(nc, 3)]["area_vs_min"] for nc in NCS]
    l2, = ax2.plot(NCS, area, "--s", color="#4477AA", linewidth=2, markersize=7,
                   label="symbol area cost (× smallest, ecc3)")
    ax2.set_ylabel("symbol area  (× smallest cell, AUTO geometry, ecc3)",
                   color="#4477AA")
    ax2.tick_params(axis="y", labelcolor="#4477AA")
    ax2.set_ylim(0.8, max(area) * 1.15)

    ax1.set_title("The tradeoff: robustness vs density across colour count\n"
                  "(peak robustness at Nc2-3; Nc7 packs most data but is least robust)")
    ax1.legend(handles=[l1, l2], loc="lower center")
    fig.tight_layout()
    out = os.path.join(CHARTS_DIR, "robustness_vs_density.png")
    fig.savefig(out, dpi=130); plt.close(fig)
    return out


def main():
    os.makedirs(CHARTS_DIR, exist_ok=True)
    rows = load_rates()
    density = load_density()
    outs = [
        chart_rate_vs_nc(rows),
        chart_rate_vs_ecc(rows),
        chart_robustness_vs_density(rows, density),
    ]
    for o in outs:
        print("wrote", os.path.relpath(o, os.path.dirname(R1_DIR)))


if __name__ == "__main__":
    main()
