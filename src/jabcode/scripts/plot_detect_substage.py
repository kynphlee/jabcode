#!/usr/bin/env python3
"""Render a stacked-bar DETECT-sub-stage chart from bench_profile JSON.

The `profile` Makefile target builds src/jabcode/test/bench_profile.c, which (in
addition to the six top-level pipeline-stage records consumed by
plot_stage_profile.py) emits one record per (Nc, DETECT sub-stage), tagged
`"detect_substage": true`:

    [{"platform": "...", "colours": 4, "stage": "DETECT_BINARIZE",
      "detect_substage": true, "us_per_decode": 7780.9, "decodes": 40, "ok": 40}, ...]

This script selects those sub-stage records and pivots them into one stacked bar
per colour mode (Nc), with each DETECT sub-step a coloured segment, so the
dominant sub-step at low vs high Nc -- the optimisation target -- is read off at
a glance. The DETECT total per Nc (== the sub-stage sum, by construction) is
annotated above each bar.

Usage:
    python plot_detect_substage.py INPUT.json [-o OUTPUT.png]

A matplotlib venv exists at /tmp/bench-venv:
    /tmp/bench-venv/bin/python plot_detect_substage.py stage_profile.json
"""
import argparse
import json
import sys

import matplotlib
matplotlib.use("Agg")  # headless: write a PNG, never open a window
import matplotlib.pyplot as plt

# Sub-stage draw order (bottom -> top) and a colourblind-friendly palette.
SUBSTAGE_ORDER = ["DETECT_BINARIZE", "DETECT_FINDER",
                  "DETECT_TRANSFORM", "DETECT_SAMPLE"]
SUBSTAGE_COLOR = {
    "DETECT_BINARIZE":  "#4477AA",
    "DETECT_FINDER":    "#EE6677",
    "DETECT_TRANSFORM": "#CCBB44",
    "DETECT_SAMPLE":    "#228833",
}


def load(path):
    """Read the bench JSON into {Nc: {sub-stage: us_per_decode}} plus a platform tag.

    Only records flagged detect_substage are kept; the six top-level pipeline
    stages (DETECT, PALETTE, ...) are skipped so this chart shows the DETECT
    breakdown alone.
    """
    with open(path) as fh:
        records = json.load(fh)
    by_nc = {}
    platform = "unknown"
    for r in records:
        if not r.get("detect_substage"):
            continue
        platform = r.get("platform", platform)
        by_nc.setdefault(r["colours"], {})[r["stage"]] = float(r["us_per_decode"])
    return by_nc, platform


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input", help="bench_profile JSON file")
    ap.add_argument("-o", "--output", default=None,
                    help="output PNG (default: alongside input as *-detect.png)")
    args = ap.parse_args(argv)

    out = args.output or (args.input.rsplit(".", 1)[0] + "-detect.png")
    by_nc, platform = load(args.input)
    if not by_nc:
        sys.exit("no DETECT sub-stage records in %s "
                 "(needs a bench_profile build with sub-stage profiling)" % args.input)

    ncs = sorted(by_nc)
    x = list(range(len(ncs)))

    fig, ax = plt.subplots(figsize=(10, 6))
    bottoms = [0.0] * len(ncs)
    for stage in SUBSTAGE_ORDER:
        vals = [by_nc[nc].get(stage, 0.0) for nc in ncs]
        ax.bar(x, vals, bottom=bottoms, width=0.62,
               label=stage, color=SUBSTAGE_COLOR.get(stage, "#999999"),
               edgecolor="white", linewidth=0.5)
        bottoms = [b + v for b, v in zip(bottoms, vals)]

    # Annotate each bar with its DETECT total microseconds (== sub-stage sum).
    for xi, total in zip(x, bottoms):
        ax.text(xi, total, " %.0f" % total, ha="center", va="bottom",
                fontsize=8, rotation=0, color="#333333")

    ax.set_xticks(x)
    ax.set_xticklabels([str(nc) for nc in ncs])
    ax.set_xlabel("Colour mode  Nc  (number of module colours)")
    ax.set_ylabel("DETECT time per decode  (microseconds, stacked by sub-stage)")
    ax.set_title("JABCode DETECT latency attributed by sub-stage  (%s)" % platform)
    ax.margins(y=0.12)
    ax.grid(axis="y", linestyle=":", alpha=0.4)
    # Legend ordered top-of-stack first so it reads in the same vertical order.
    handles, labels = ax.get_legend_handles_labels()
    ax.legend(handles[::-1], labels[::-1], title="DETECT sub-stage",
              loc="upper right", framealpha=0.95)

    fig.tight_layout()
    fig.savefig(out, dpi=140)
    print("wrote %s" % out)


if __name__ == "__main__":
    main()
