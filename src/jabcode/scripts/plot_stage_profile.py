#!/usr/bin/env python3
"""Render a stacked-bar decode-stage-breakdown chart from bench_profile JSON.

The `profile` Makefile target builds src/jabcode/test/bench_profile.c, which
emits a JSON array of per-(Nc, stage) records on stdout:

    [{"platform": "...", "colours": 4, "stage": "DETECT",
      "us_per_decode": 9295.7, "decodes": 40, "ok": 40}, ...]

This script pivots those records into one stacked bar per colour mode (Nc),
with each pipeline stage as a coloured segment, so the dominant stage at low vs
high Nc is read off at a glance. The total decode time per Nc is annotated above
each bar.

Usage:
    python plot_stage_profile.py INPUT.json [-o OUTPUT.png]

A matplotlib venv exists at /tmp/bench-venv:
    /tmp/bench-venv/bin/python plot_stage_profile.py stage_profile.json
"""
import argparse
import json
import sys

import matplotlib
matplotlib.use("Agg")  # headless: write a PNG, never open a window
import matplotlib.pyplot as plt

# Stage draw order (bottom -> top) and a colourblind-friendly palette.
STAGE_ORDER = ["DETECT", "PALETTE", "COLOR_CLASSIFY",
               "DEINTERLEAVE", "LDPC", "DATA_DECODE"]
STAGE_COLOR = {
    "DETECT":         "#4477AA",
    "PALETTE":        "#66CCEE",
    "COLOR_CLASSIFY": "#228833",
    "DEINTERLEAVE":   "#CCBB44",
    "LDPC":           "#EE6677",
    "DATA_DECODE":    "#AA3377",
}


def load(path):
    """Read the bench JSON into {Nc: {stage: us_per_decode}} plus a platform tag."""
    with open(path) as fh:
        records = json.load(fh)
    by_nc = {}
    platform = "unknown"
    for r in records:
        platform = r.get("platform", platform)
        by_nc.setdefault(r["colours"], {})[r["stage"]] = float(r["us_per_decode"])
    return by_nc, platform


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input", help="bench_profile JSON file")
    ap.add_argument("-o", "--output", default=None,
                    help="output PNG (default: alongside input as *.png)")
    args = ap.parse_args(argv)

    out = args.output or (args.input.rsplit(".", 1)[0] + ".png")
    by_nc, platform = load(args.input)
    if not by_nc:
        sys.exit("no records in %s" % args.input)

    ncs = sorted(by_nc)
    x = list(range(len(ncs)))

    fig, ax = plt.subplots(figsize=(10, 6))
    bottoms = [0.0] * len(ncs)
    for stage in STAGE_ORDER:
        vals = [by_nc[nc].get(stage, 0.0) for nc in ncs]
        ax.bar(x, vals, bottom=bottoms, width=0.62,
               label=stage, color=STAGE_COLOR.get(stage, "#999999"),
               edgecolor="white", linewidth=0.5)
        bottoms = [b + v for b, v in zip(bottoms, vals)]

    # Annotate each bar with its total decode microseconds.
    for xi, total in zip(x, bottoms):
        ax.text(xi, total, " %.0f" % total, ha="center", va="bottom",
                fontsize=8, rotation=0, color="#333333")

    ax.set_xticks(x)
    ax.set_xticklabels([str(nc) for nc in ncs])
    ax.set_xlabel("Colour mode  Nc  (number of module colours)")
    ax.set_ylabel("Decode time per decode  (microseconds, stacked by stage)")
    ax.set_title("JABCode decode latency attributed by pipeline stage  (%s)" % platform)
    ax.margins(y=0.12)
    ax.grid(axis="y", linestyle=":", alpha=0.4)
    # Legend ordered top-of-stack first so it reads in the same vertical order.
    handles, labels = ax.get_legend_handles_labels()
    ax.legend(handles[::-1], labels[::-1], title="stage",
              loc="upper right", framealpha=0.95)

    fig.tight_layout()
    fig.savefig(out, dpi=140)
    print("wrote %s" % out)


if __name__ == "__main__":
    main()
