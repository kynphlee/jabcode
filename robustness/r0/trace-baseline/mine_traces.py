#!/usr/bin/env python3
"""
R0 FIELD DECODE-RATE BASELINE -- mine on-device diagnostic traces.

Reads jabauth-android diagnostic-app logcat traces (one session each, many
real camera-frame decode ATTEMPTS) and reduces them to a per-(Nc, medium)
decode-rate baseline plus a FAIL_STAGE histogram. The verdict this feeds is
binary: are real-world failures DETECTION-bound (pick R2) or
CLASSIFICATION/palette-bound (pick R1)?

SECURITY: this parser counts OUTCOMES and STAGES only. It never extracts,
prints, or stores any decoded payload. DIAG_SYMBOL_DECODE lines are read for
their `result=ok|fail` field alone; the byte payload is ignored.

--------------------------------------------------------------------------
Trace model
--------------------------------------------------------------------------
The diagnostic-app emitted two logcat formats over its lifetime; this parser
handles both by anchoring on the TERMINAL native-outcome line, which is
present and 1-per-attempt in every format:

    "Native decode SUCCESS"                                -> success
    "Native decode FAILED: No JABCode found in image"      -> detection failure
    "Native decode FAILED: JABCode found but not decodable"-> decode-stage failure

* Newer format additionally prints "--- Decode #<N> START ---" before each
  attempt; older Mode-0 sessions omit it and delimit attempts only by the
  per-frame DIAG_MODE0_DETECT + terminal line. Anchoring on the terminal line
  counts every attempt in both. A "Decode #N START" with no following
  terminal line is a TRUNCATED session tail and is dropped (it produces no
  terminal line, so it is simply never counted).

* FAIL_STAGE        : emitted per Nc-fallback try WITHIN an attempt. A failed
                      attempt fans across up to 8 Nc tries, each logging a
                      FAIL_STAGE. We attribute the attempt to the LAST
                      FAIL_STAGE seen since the previous attempt closed -- the
                      stage at which this attempt terminally gave up.

Failure taxonomy (pipeline order, earliest -> latest):
    detect        : no finder pattern localized ("No JABCode found").
                    Synthetic stage -- no FAIL_STAGE line is emitted.
    module_color  : Part I -- module colour not in the valid colour set.
    pair_bits     : Part I -- paired-bit value out of range.
    side_version  : Part II -- decoded side/version inconsistent with matrix.
    ldpc          : Part II -- metadata uncorrectable at the LDPC layer.

`detect` failures => detection-bound.  Everything else => classification/
palette-bound (a symbol WAS found, colour/metadata recovery failed).
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

# Relative path from a jabcode checkout root to the trace folder.
_LOGS_REL = Path("jabauth-android") / "diagnostic-app" / "logs"


def _default_logs_dir() -> Path:
    """
    Best-effort default trace dir, overridable with --logs-dir.

    The traces are untracked working-tree files that live in the developer's
    diagnostic-app checkout, so when this script runs from a *worktree* (whose
    own logs/ is empty) it falls back to the sibling primary checkout. Order:
      1. <repo-root>/jabauth-android/diagnostic-app/logs   (this checkout)
      2. sibling primary checkout, i.e. ".worktrees/<name>" stripped to the
         base repo (e.g. jabcode.worktrees/r0-traces -> jabcode)
    Returns the first candidate that contains trace files, else candidate 1.
    """
    repo_root = Path(__file__).resolve().parents[3]
    candidates = [repo_root / _LOGS_REL]

    # If we're inside "<base>.worktrees/<name>", add the base checkout.
    wt_parent = repo_root.parent  # e.g. .../jabcode.worktrees
    if wt_parent.name.endswith(".worktrees"):
        primary = wt_parent.parent / wt_parent.name[: -len(".worktrees")]
        candidates.append(primary / _LOGS_REL)

    for cand in candidates:
        if cand.is_dir() and any(cand.glob("trace-*.logcat")):
            return cand
    return candidates[0]


DEFAULT_LOGS_DIR = _default_logs_dir()

# Pipeline-ordered failure stages (also the chart's stacking order).
FAIL_STAGES = ["detect", "module_color", "pair_bits", "side_version", "ldpc"]

# --- line matchers -------------------------------------------------------
RE_START = re.compile(r"Decode #(\d+) START")
RE_NATIVE_SUCCESS = re.compile(r"Native decode SUCCESS")
RE_NATIVE_FAIL_DETECT = re.compile(r"Native decode FAILED: No JABCode found in image")
RE_NATIVE_FAIL_DECODE = re.compile(r"Native decode FAILED: JABCode found but not decodable")
RE_FAIL_STAGE = re.compile(r"FAIL_STAGE=([A-Za-z0-9_]+)")
# Filename -> (nc, medium). N in 0..7 or 'X'; optional medium tag; optional _vN.
RE_FNAME = re.compile(r"trace-\d+_\d+-nc([0-7X])(?:_(print|screen))?(?:_v\d+)?\.logcat$")


@dataclass
class AttemptStats:
    """Outcome tally for one (nc, medium) group across all its traces."""
    nc: str
    medium: str
    n_attempts: int = 0
    n_success: int = 0
    fail_stage_hist: Counter = field(default_factory=Counter)

    @property
    def decode_rate(self) -> float:
        return (self.n_success / self.n_attempts) if self.n_attempts else 0.0

    def record(self) -> dict:
        return {
            "nc": self.nc,
            "medium": self.medium,
            "n_attempts": self.n_attempts,
            "n_success": self.n_success,
            "decode_rate": round(self.decode_rate, 4),
            "fail_stage_hist": {
                s: self.fail_stage_hist[s]
                for s in FAIL_STAGES
                if self.fail_stage_hist[s]
            },
        }


def parse_filename(path: Path) -> tuple[str, str]:
    """Return (nc, medium) from a trace filename. medium='' when untagged."""
    m = RE_FNAME.search(path.name)
    if not m:
        raise ValueError(f"unrecognized trace filename: {path.name}")
    nc, medium = m.group(1), (m.group(2) or "")
    return nc, medium


def parse_trace(path: Path) -> tuple[int, int, Counter]:
    """
    Reduce one trace file to (n_attempts, n_success, fail_stage_hist).

    Terminal-anchored: each "Native decode SUCCESS|FAILED" line closes exactly
    one attempt and is the counting unit (works with or without a preceding
    "Decode #N START"). FAIL_STAGE markers seen since the previous attempt
    closed are this attempt's stages; the LAST one is its terminal stage. The
    pending-stage accumulator also resets on a START line, so a START-less
    older-format session and a newer START-delimited session both attribute
    stages to the correct attempt. A truncated tail emits no terminal line and
    is therefore never counted.
    """
    n_attempts = 0
    n_success = 0
    hist: Counter = Counter()

    last_fail_stage: str | None = None

    with path.open("r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if RE_START.search(line):
                last_fail_stage = None
                continue

            m = RE_FAIL_STAGE.search(line)
            if m:
                last_fail_stage = m.group(1)
                continue

            if RE_NATIVE_SUCCESS.search(line):
                n_attempts += 1
                n_success += 1
                last_fail_stage = None
            elif RE_NATIVE_FAIL_DETECT.search(line):
                n_attempts += 1
                hist["detect"] += 1
                last_fail_stage = None
            elif RE_NATIVE_FAIL_DECODE.search(line):
                n_attempts += 1
                # A found-but-undecodable attempt always emits >=1 FAIL_STAGE;
                # fall back to 'ldpc' only if somehow none was seen (defensive).
                hist[last_fail_stage or "ldpc"] += 1
                last_fail_stage = None

    return n_attempts, n_success, hist


def aggregate(logs_dir: Path) -> dict[tuple[str, str], AttemptStats]:
    """Aggregate every trace in logs_dir into per-(nc, medium) groups."""
    groups: dict[tuple[str, str], AttemptStats] = {}
    traces = sorted(logs_dir.glob("trace-*.logcat"))
    if not traces:
        raise SystemExit(f"no trace-*.logcat files under {logs_dir}")

    for path in traces:
        nc, medium = parse_filename(path)
        n_att, n_succ, hist = parse_trace(path)
        key = (nc, medium)
        grp = groups.get(key)
        if grp is None:
            grp = groups[key] = AttemptStats(nc=nc, medium=medium)
        grp.n_attempts += n_att
        grp.n_success += n_succ
        grp.fail_stage_hist.update(hist)

    return groups


def _nc_sort_key(nc: str) -> tuple[int, int]:
    """Order Nc as 0..7 then X last."""
    return (1, 0) if nc == "X" else (0, int(nc))


def write_jsonl(groups: dict, out_path: Path) -> list[dict]:
    """Write one JSON record per (nc, medium); return the records."""
    records = [
        groups[k].record()
        for k in sorted(groups, key=lambda k: (_nc_sort_key(k[0]), k[1]))
    ]
    with out_path.open("w", encoding="utf-8") as fh:
        for rec in records:
            fh.write(json.dumps(rec) + "\n")
    return records


def make_chart(records: list[dict], out_path: Path) -> None:
    """Decode-rate bars + stacked FAIL_STAGE distribution per group."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    def label(r: dict) -> str:
        return f"nc{r['nc']}" + (f"\n{r['medium']}" if r["medium"] else "")

    labels = [label(r) for r in records]
    rates = [r["decode_rate"] * 100.0 for r in records]
    x = range(len(records))

    # Stage colours -- detection vs the four classification/palette stages.
    stage_colors = {
        "detect": "#444444",        # detection-bound (dark)
        "module_color": "#d62728",  # classification/palette (warm)
        "pair_bits": "#ff7f0e",
        "side_version": "#9467bd",
        "ldpc": "#1f77b4",
    }

    fig, (ax_top, ax_bot) = plt.subplots(
        2, 1, figsize=(max(9, len(records) * 0.9), 8.5), sharex=True
    )

    # --- top: decode-rate bars --------------------------------------------
    bars = ax_top.bar(x, rates, color="#2ca02c", edgecolor="black", linewidth=0.4)
    ax_top.set_ylabel("Decode-success rate (%)")
    ax_top.set_ylim(0, 100)
    ax_top.set_title("R0 field decode-rate baseline by Nc / medium (on-device traces)")
    ax_top.grid(axis="y", linestyle=":", alpha=0.4)
    for rect, r in zip(bars, records):
        ax_top.text(
            rect.get_x() + rect.get_width() / 2,
            rect.get_height() + 1.5,
            f"{r['decode_rate']*100:.1f}%\nn={r['n_attempts']}",
            ha="center", va="bottom", fontsize=7,
        )

    # --- bottom: stacked FAIL_STAGE share of FAILURES ---------------------
    bottoms = [0.0] * len(records)
    for stage in FAIL_STAGES:
        vals = []
        for r in records:
            fails = r["n_attempts"] - r["n_success"]
            cnt = r["fail_stage_hist"].get(stage, 0)
            vals.append(100.0 * cnt / fails if fails else 0.0)
        ax_bot.bar(
            x, vals, bottom=bottoms, color=stage_colors[stage],
            edgecolor="white", linewidth=0.3, label=stage,
        )
        bottoms = [b + v for b, v in zip(bottoms, vals)]

    ax_bot.set_ylabel("Share of failures (%)")
    ax_bot.set_ylim(0, 100)
    ax_bot.set_xticks(list(x))
    ax_bot.set_xticklabels(labels, fontsize=8)
    ax_bot.grid(axis="y", linestyle=":", alpha=0.4)
    ax_bot.set_title("FAIL_STAGE distribution of failed attempts "
                     "(dark = detection-bound, warm/blue = classification/palette-bound)")
    ax_bot.legend(ncol=len(FAIL_STAGES), fontsize=7, loc="upper center",
                  bbox_to_anchor=(0.5, -0.12), frameon=False)

    fig.tight_layout()
    fig.savefig(out_path, dpi=130, bbox_inches="tight")
    plt.close(fig)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--logs-dir", type=Path, default=DEFAULT_LOGS_DIR,
                    help="directory of trace-*.logcat files "
                         f"(default: {DEFAULT_LOGS_DIR})")
    ap.add_argument("--out-dir", type=Path, default=Path(__file__).resolve().parent,
                    help="output directory for baseline.jsonl / baseline.png")
    ap.add_argument("--no-chart", action="store_true",
                    help="skip baseline.png (e.g. when matplotlib is absent)")
    args = ap.parse_args(argv)

    logs_dir = args.logs_dir.resolve()
    out_dir = args.out_dir.resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    groups = aggregate(logs_dir)
    records = write_jsonl(groups, out_dir / "baseline.jsonl")

    # Console summary (no payloads -- counts only).
    tot_att = sum(r["n_attempts"] for r in records)
    tot_succ = sum(r["n_success"] for r in records)
    glob_hist: Counter = Counter()
    for r in records:
        glob_hist.update(r["fail_stage_hist"])
    print(f"logs_dir   : {logs_dir}")
    print(f"groups     : {len(records)}")
    print(f"attempts   : {tot_att}  success={tot_succ}  "
          f"rate={100.0*tot_succ/tot_att:.1f}%")
    print(f"fail_stage : {dict(glob_hist)}")
    detect = glob_hist.get("detect", 0)
    classify = sum(glob_hist[s] for s in FAIL_STAGES if s != "detect")
    print(f"verdict    : detect={detect}  classify/palette={classify}  -> "
          + ("DETECTION-bound (R2)" if detect > classify else "CLASSIFICATION/palette-bound (R1)"))

    if not args.no_chart:
        try:
            make_chart(records, out_dir / "baseline.png")
            print(f"chart      : {out_dir / 'baseline.png'}")
        except ImportError:
            print("chart      : SKIPPED (matplotlib not available); rerun with venv "
                  "or --no-chart", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
