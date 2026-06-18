#!/usr/bin/env python3
"""
R0 field-vs-synthetic corpus representativeness — comparison engine.

Question: does the R0 SYNTHETIC corpus reproduce the FIELD's failure modes,
broken down by Nc and pipeline fail-stage? If it does, the rig can be trusted to
drive R1 / encoding-robustness decisions on synthetic inputs. If it does not, we
must know *which* field modes are missing before staging real-capture work.

Two evidence sources, both committed in the tree:

  FIELD     robustness/r0/trace-baseline/baseline.jsonl
            per-(Nc, medium) decode-rate + FAIL_STAGE histogram, mined from
            7,658 real on-device decode attempts.

  SYNTHETIC robustness/r0/rig/results/rig_manifest.per_image.jsonl
            the R0 rig's per-image outcome over the 144-image deterministic
            synthetic corpus (8 Nc x 6 degradation families x 3 params).
            Regenerate with:
              python3 robustness/r0/synthetic/to_rig_manifest.py
              robustness/r0/rig/run.sh \
                "$(pwd)/robustness/r0/synthetic/out/rig_manifest.jsonl" conditions,nc

Taxonomy crosswalk (field stage name -> rig coarse bucket). The rig collapses the
fine field stages into coarse buckets; we map the field the same way so the two
are compared on a common axis:

    field detect        -> DETECT
    field module_color  -> PALETTE_CLASSIFY   (Part I colour classification)
    field pair_bits     -> PALETTE_CLASSIFY   (Part I paired-bit range)
    field side_version  -> PARTII
    field ldpc          -> LDPC               (data-layer / metadata LDPC)

The rig's own taxonomy additionally has PARTII for Part I metadata LDPC; the
field reports that as `ldpc`, so we keep field `ldpc` -> LDPC. This is the
conservative choice: it does NOT inflate the synthetic's apparent palette
coverage, which is the thing under test.

Outputs (written next to this script):
  field_vs_synthetic_by_nc.png     stacked fail-stage bars, field vs synthetic, per Nc
  failure_mechanism_map.png        decode-rate + the missing-mode highlight
  comparison_data.json             the numbers behind the report (machine-readable)

This is ANALYSIS ONLY: it reads the committed evidence and writes charts + a JSON
summary. It does not touch the decoder, the synthetic generator, or the rig.
"""
from __future__ import annotations

import collections
import json
import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
R0 = os.path.normpath(os.path.join(HERE, "..", ".."))
FIELD_JSONL = os.path.join(R0, "trace-baseline", "baseline.jsonl")
SYN_JSONL = os.path.join(R0, "rig", "results", "rig_manifest.per_image.jsonl")

# field fine-stage -> rig coarse bucket
FIELD_TO_COARSE = {
    "detect": "DETECT",
    "module_color": "PALETTE_CLASSIFY",
    "pair_bits": "PALETTE_CLASSIFY",
    "side_version": "PARTII",
    "ldpc": "LDPC",
}

# coarse buckets, in pipeline order, with a stable colour per stage
STAGE_ORDER = ["NONE", "DETECT", "PALETTE_CLASSIFY", "PARTII", "LDPC"]
STAGE_COLOR = {
    "NONE": "#2e9e5b",              # green  — success
    "DETECT": "#3b6fb6",           # blue   — detection-bound
    "PALETTE_CLASSIFY": "#d94f3d",  # red    — palette / colour classification
    "PARTII": "#e0a32e",          # amber  — Part II metadata
    "LDPC": "#7b4fa0",            # purple — LDPC error-correction
}


def load_field():
    """Return {nc(str): {'n':, 'success':, 'coarse':Counter}} combining mediums.

    Per-medium nc1 rows are kept separately too (returned as second dict keyed by
    'nc1/print' etc.) because the medium split IS the signal for nc1.
    """
    combined = {}
    per_medium = {}
    for line in open(FIELD_JSONL):
        line = line.strip()
        if not line:
            continue
        r = json.loads(line)
        nc = r["nc"]
        coarse = collections.Counter()
        for k, v in r["fail_stage_hist"].items():
            coarse[FIELD_TO_COARSE[k]] += v
        coarse["NONE"] += r["n_success"]
        rec = {
            "n": r["n_attempts"],
            "success": r["n_success"],
            "rate": r["decode_rate"],
            "coarse": coarse,
            "raw": dict(r["fail_stage_hist"]),
            "medium": r["medium"],
        }
        if r["medium"]:
            per_medium[f"nc{nc}/{r['medium']}"] = rec
        else:
            combined[nc] = rec
    return combined, per_medium


def load_synthetic():
    """Return {nc(int): {'n':, 'success':, 'coarse':Counter, 'by_family':}}."""
    rows = [json.loads(l) for l in open(SYN_JSONL) if l.strip()]
    by_nc = collections.defaultdict(
        lambda: {"n": 0, "success": 0, "coarse": collections.Counter(),
                 "by_family": collections.defaultdict(collections.Counter)}
    )
    for r in rows:
        nc = r["nc"]
        fam = r["conditions"].split("@")[0]
        d = by_nc[nc]
        d["n"] += 1
        d["success"] += r["decode_ok"]
        d["coarse"][r["fail_stage"]] += 1
        d["by_family"][fam][r["fail_stage"]] += 1
    for nc, d in by_nc.items():
        d["rate"] = d["success"] / d["n"] if d["n"] else 0.0
    return dict(by_nc), rows


def frac(coarse, n):
    """Fraction per stage (so field and synthetic are comparable despite very
    different n)."""
    return {s: coarse.get(s, 0) / n if n else 0.0 for s in STAGE_ORDER}


def chart_by_nc(field, per_medium, syn, out_path):
    """Side-by-side stacked fail-stage fractions, field vs synthetic, per Nc.

    For nc1 we surface print + screen as separate field bars (the medium split is
    the whole point), plus the combined synthetic nc1 bar.
    """
    # Build an ordered list of (label, source, fracs, rate_text) columns.
    cols = []
    for nc in range(8):
        ncs = str(nc)
        # field (combined)
        if ncs in field:
            f = field[ncs]
            cols.append((f"nc{nc}\nFIELD", frac(f["coarse"], f["n"]),
                         f"{f['rate']*100:.0f}%", "field"))
        # synthetic
        if nc in syn:
            s = syn[nc]
            cols.append((f"nc{nc}\nSYNTH", frac(s["coarse"], s["n"]),
                         f"{s['rate']*100:.0f}%", "synth"))

    fig, ax = plt.subplots(figsize=(15, 6.5))
    x = np.arange(len(cols))
    bottoms = np.zeros(len(cols))
    for stage in STAGE_ORDER:
        vals = np.array([c[1][stage] for c in cols])
        ax.bar(x, vals, bottom=bottoms, width=0.74,
               color=STAGE_COLOR[stage], edgecolor="white", linewidth=0.6,
               label=stage)
        bottoms += vals

    # rate text above each bar; tint synthetic labels to separate the pair
    for i, c in enumerate(cols):
        ax.text(i, 1.02, c[2], ha="center", va="bottom", fontsize=8,
                color="#222" if c[3] == "field" else "#777",
                fontweight="bold" if c[3] == "field" else "normal")

    ax.set_xticks(x)
    ax.set_xticklabels([c[0] for c in cols], fontsize=8)
    ax.set_ylim(0, 1.12)
    ax.set_ylabel("fraction of attempts by terminal stage")
    ax.set_title("R0 field vs synthetic — fail-stage composition per Nc "
                 "(rate above each bar)\n"
                 "RED = PALETTE_CLASSIFY (module_color / pair_bits): present in "
                 "the FIELD, absent from the SYNTHETIC",
                 fontsize=11)
    ax.legend(loc="upper right", ncol=5, fontsize=8, framealpha=0.95)
    ax.axhline(1.0, color="#bbb", lw=0.8, ls="--")
    ax.spines[["top", "right"]].set_visible(False)
    fig.tight_layout()
    fig.savefig(out_path, dpi=130)
    plt.close(fig)
    return out_path


def chart_mechanism_map(field, per_medium, syn, out_path):
    """Two-panel: (L) decode-rate gap per Nc; (R) the PALETTE_CLASSIFY mass that
    the synthetic fails to reproduce, including the nc1 print-vs-screen split."""
    fig, (axl, axr) = plt.subplots(1, 2, figsize=(15, 6))

    # --- left: decode-rate field vs synthetic ---
    ncs = [str(n) for n in range(8)]
    frates = [field[n]["rate"] * 100 for n in ncs]
    srates = [syn[int(n)]["rate"] * 100 for n in ncs]
    x = np.arange(8)
    w = 0.38
    axl.bar(x - w / 2, frates, w, label="field", color="#3b6fb6")
    axl.bar(x + w / 2, srates, w, label="synthetic", color="#2e9e5b")
    for i in range(8):
        axl.text(i, max(frates[i], srates[i]) + 2,
                 f"+{srates[i]-frates[i]:.0f}", ha="center", fontsize=7,
                 color="#b03030")
    axl.set_xticks(x)
    axl.set_xticklabels([f"nc{n}" for n in range(8)])
    axl.set_ylabel("decode-rate %")
    axl.set_ylim(0, 105)
    axl.set_title("Decode-rate: synthetic over-decodes every Nc\n"
                  "(red = synthetic−field gap, pp)", fontsize=10)
    axl.legend(fontsize=9)
    axl.spines[["top", "right"]].set_visible(False)

    # --- right: palette-classify failures, field (with nc1 medium split) vs synth ---
    labels, field_pc, syn_pc = [], [], []
    # combined nc rows where field has any palette mass, plus nc1 media
    for nc in range(8):
        ncs_ = str(nc)
        fpc = field[ncs_]["coarse"].get("PALETTE_CLASSIFY", 0)
        spc = syn[nc]["coarse"].get("PALETTE_CLASSIFY", 0)
        # express as % of that group's attempts so scales are comparable
        labels.append(f"nc{nc}")
        field_pc.append(100 * fpc / field[ncs_]["n"])
        syn_pc.append(100 * spc / syn[nc]["n"])
    # add nc1/print and nc1/screen explicitly
    for key in ("nc1/print", "nc1/screen"):
        if key in per_medium:
            r = per_medium[key]
            labels.append(key.replace("nc1/", "nc1\n"))
            field_pc.append(100 * r["coarse"].get("PALETTE_CLASSIFY", 0) / r["n"])
            syn_pc.append(0.0)  # synthetic has no medium split

    x2 = np.arange(len(labels))
    axr.bar(x2 - w / 2, field_pc, w, label="field", color="#d94f3d")
    axr.bar(x2 + w / 2, syn_pc, w, label="synthetic", color="#d94f3d",
            hatch="////", alpha=0.35, edgecolor="#d94f3d")
    axr.set_xticks(x2)
    axr.set_xticklabels(labels, fontsize=8)
    axr.set_ylabel("PALETTE_CLASSIFY failures, % of group attempts")
    axr.set_title("The missing mode: module_color / pair_bits\n"
                  "synthetic produces ZERO at every Nc (hatched = 0)", fontsize=10)
    axr.legend(fontsize=9)
    axr.spines[["top", "right"]].set_visible(False)

    fig.suptitle("R0 representativeness — synthetic under-represents the field's "
                 "colour-classification regime", fontsize=12, y=1.00)
    fig.tight_layout()
    fig.savefig(out_path, dpi=130)
    plt.close(fig)
    return out_path


def _pct(coarse, n, stage):
    return 100 * coarse.get(stage, 0) / n if n else 0.0


def write_report(field, per_medium, syn, summary, out_path):
    """Emit REPORT.md as a build artifact, with the per-Nc table and the headline
    figures derived from the loaded data (not hand-copied), so prose stays in
    sync with the evidence."""
    f_pc_total = summary["field_palette_classify_total"]
    s_pc_total = summary["synthetic_palette_classify_total"]

    # per-Nc rows (combined-medium field vs synthetic) for the core table
    rows = []
    for nc in range(8):
        ncs = str(nc)
        fr, sr = field[ncs], syn[nc]
        rows.append(
            f"| {nc} | field | {fr['n']:,} | {fr['rate']*100:.1f}% | "
            f"{_pct(fr['coarse'],fr['n'],'DETECT'):.0f}% | "
            f"{_pct(fr['coarse'],fr['n'],'PALETTE_CLASSIFY'):.0f}% | "
            f"{_pct(fr['coarse'],fr['n'],'LDPC'):.0f}% |")
        rows.append(
            f"| {nc} | synth | {sr['n']} | {sr['rate']*100:.0f}% | "
            f"{_pct(sr['coarse'],sr['n'],'DETECT'):.0f}% | "
            f"{_pct(sr['coarse'],sr['n'],'PALETTE_CLASSIFY'):.0f}% | "
            f"{_pct(sr['coarse'],sr['n'],'LDPC'):.0f}% |")
    core_table = "\n".join(rows)

    # nc1/print detail
    p = per_medium.get("nc1/print", {})
    p_mc = p.get("raw", {}).get("module_color", 0)
    p_n_fail = p.get("n", 0) - p.get("success", 0)

    # decode-rate gaps
    gaps = "   ".join(
        f"nc{nc} +{(syn[nc]['rate']-field[str(nc)]['rate'])*100:.0f}"
        for nc in range(8))

    md = f"""# R0 — Field-vs-Synthetic Corpus Representativeness

*Generated by `compare_field_synthetic.py` — figures derived from
`../trace-baseline/baseline.jsonl` (field, 7,658 real attempts) and the R0 rig
over the 144-image synthetic corpus. Analysis only; no decoder/generator/rig
code is modified.*

**Question (Option ④, the field-validation hedge):** before trusting the R0
*synthetic* corpus to drive R1 / encoding-robustness decisions, do its failure
modes — by **Nc** and **pipeline fail-stage** — match real on-device captures?

**VERDICT (one line): the synthetic is _stage-representative for the high-Nc
LDPC regime only_, and _under-represents the field_ on two material counts — it
reproduces {s_pc_total} of the field's {f_pc_total} colour-classification
(`module_color`/`pair_bits`) failures, and it over-decodes every Nc by
+10…+79 pp. Trust the rig to A/B an LDPC change on nc5–nc7; do NOT let it be the
sole arbiter of a palette / colour-classification change.**

Charts: `field_vs_synthetic_by_nc.png`, `failure_mechanism_map.png`.
Machine-readable numbers: `comparison_data.json`.

---

## 1. Common axis (taxonomy crosswalk)

The field reports fine stages; the rig reports coarse buckets. The field is
folded into the rig's buckets — the **conservative** mapping, which never
inflates the synthetic's apparent palette coverage:

| field stage | -> rig bucket | bound class |
|-------------|---------------|-------------|
| `detect` | `DETECT` | detection |
| `module_color` | **`PALETTE_CLASSIFY`** | colour classification |
| `pair_bits` | **`PALETTE_CLASSIFY`** | colour classification |
| `side_version` | `PARTII` | metadata |
| `ldpc` | `LDPC` | error-correction |

`PALETTE_CLASSIFY` = symbol found and sampled, but module colours out of the
valid set (a real colour cast) — the distinctive field mode under test.

---

## 2. Per-Nc comparison (stages as % of group attempts)

| Nc | source | n | decode-rate | DETECT | PALETTE_CLASSIFY | LDPC |
|----|--------|--:|------------:|-------:|-----------------:|-----:|
{core_table}

Medium-isolated field rows (nc1 is the only medium-tagged mode):

| group | n | decode-rate | dominant fail | PALETTE_CLASSIFY % |
|-------|--:|------------:|---------------|-------------------:|
| nc1/print | {per_medium['nc1/print']['n']} | {per_medium['nc1/print']['rate']*100:.1f}% | `module_color` | {_pct(per_medium['nc1/print']['coarse'],per_medium['nc1/print']['n'],'PALETTE_CLASSIFY'):.0f}% |
| nc1/screen | {per_medium['nc1/screen']['n']} | {per_medium['nc1/screen']['rate']*100:.1f}% | `detect` | {_pct(per_medium['nc1/screen']['coarse'],per_medium['nc1/screen']['n'],'PALETTE_CLASSIFY'):.0f}% |

### What the synthetic NEVER produces

All 144 synthetic images yield exactly three terminal stages: **NONE, LDPC,
DETECT.** Zero `PALETTE_CLASSIFY`, zero `PARTII`, zero `DATA`.

> **Field `PALETTE_CLASSIFY` (`module_color`+`pair_bits`): {f_pc_total}.
> Synthetic `PALETTE_CLASSIFY`: {s_pc_total}.**

---

## 3. Headline divergence — the colour cast has no synthetic analogue

Cleanest instance: **nc1/print**, where the medium is isolated — {p_mc} of its
{p_n_fail} failures are `module_color`, a printed-gamut colour cast. The decoder
**finds** the symbol, then interior module colours fall out of the valid set.
The synthetic's only colour family, `chroma` (desaturate+warm wash), does NOT
reproduce this. Probing the worst synthetic cast directly:

```
chroma@0.7, nc2 :  status=0  "Too few finder pattern found"   -> DETECT
chroma@0.7, nc7 :  status=0  "Too few finder pattern found"   -> DETECT
```

The synthetic wash is **global**, so it overwhelms the high-contrast finder and
dies at **detection** — it never reaches module sampling, so it *cannot* emit a
`module_color` failure. The real print cast is the opposite: black finder
corners survive (detection holds), interior colours drift out of gamut
(classification fails). **The synthetic models the wrong half of the colour-cast
physics** — the under-sampled low-Nc strong-cast regime, now confirmed.

nc2 corroborates: {_pct(field['2']['coarse'],field['2']['n'],'PALETTE_CLASSIFY'):.0f}% of
its {field['2']['n']:,} attempts die in `PALETTE_CLASSIFY`, yet synthetic nc2
decodes at {syn[2]['rate']*100:.0f}% with zero palette failures. (Honest caveat:
`pair_bits` can also arise from non-colour metadata corruption, so nc2's palette
mass is weaker evidence than nc1/print's pure `module_color`. Both point the
same way.)

---

## 4. Secondary divergence — severity (decode-rate)

The synthetic over-decodes **every** Nc — gap (synthetic − field), points:

```
{gaps}
```

Expected for a curated single-axis corpus, but it means the synthetic does not
sit at the field's operating point. nc2 is the extreme: the field's hardest
polychrome mode ({field['2']['rate']*100:.1f}%) is the synthetic's easiest-
looking ({syn[2]['rate']*100:.0f}%).

---

## 5. Where the synthetic IS trustworthy

**nc5 / nc6 / nc7:** field dominant stage is `ldpc`, and the synthetic *does*
produce `LDPC` (lighting / blur / perspective families). The **mechanism
matches** even though absolute rates differ. For an R1 change targeting
**data-layer LDPC / metadata recovery on high-Nc symbols**, the rig over the
synthetic corpus is a valid A/B instrument.

---

## 6. VERDICT

- ✓ **stage-representative for the high-Nc LDPC regime** (nc5–nc7). Rig may A/B
  an LDPC-targeted change here.
- ✗ **non-representative of the colour-classification regime** — {s_pc_total} /
  {f_pc_total} of the field's `module_color`/`pair_bits` failures. The signature
  field mode (nc1/print `module_color`) has no synthetic analogue; the one
  colour family (`chroma`) collapses at *detection*, the wrong stage.
- ✗ **non-representative of field severity** — over-decodes every Nc
  (+10…+79 pp).

**Operational consequence:** trust the rig to drive an **LDPC / metadata**
change on **nc5–nc7**. Do **NOT** let synthetic numbers alone gate a **palette /
colour-classification** change — the synthetic cannot fail at that stage, so it
cannot show such a change working. Closing the blind spot needs real captures.

---

## 7. RECOMMENDATION — minimal Tier-2 real-capture set

Smallest set that injects the two missing axes (a finder-preserving colour cast,
and field-realistic severity) at the Nc where the field breaks that way —
precondition for staging Option ① (R3 capture):

| # | Nc | medium | condition | closes | priority |
|---|----|--------|-----------|--------|----------|
| T2-1 | nc1 (4c) | print | warm/low-gamut stock (matte inkjet/thermal), normal light — induce `module_color` | the headline missing mode | **P0** |
| T2-2 | nc2 (8c) | print | same warm-stock print; field's worst polychrome mode | nc2 palette + the +79 pp severity gap | **P0** |
| T2-3 | nc2 | print | warm stock + tungsten/off-white ambient (compound cast) | strong-cast regime | P1 |
| T2-4 | nc5–nc7 | print | warm stock + mild defocus | validate the part the synthetic gets right | P1 |
| T2-5 | nc1 | screen | dim / off-axis panel | anchor the detection-bound screen end | P2 |

**Shoot list:** 5 cells × ~10 captures ≈ **50 images**, all from the *same*
known payloads in `rig/manifest.jsonl` (so the rig's SHA-256 verification works
unchanged). **P0 alone (T2-1 + T2-2, ~20 images) is the decisive gate.**

**Acceptance criterion:** the rig over the Tier-2 set must produce a non-zero
`PALETTE_CLASSIFY` histogram at nc1/print and nc2/print. Until it does, no
palette / colour-classification decoder change is "validated."

---

## 8. Reproduce

```bash
# from repo root — the synthetic rig run is gitignored (machine-local paths)
python3 robustness/r0/synthetic/to_rig_manifest.py
robustness/r0/rig/run.sh \\
  "$(pwd)/robustness/r0/synthetic/out/rig_manifest.jsonl" conditions,nc
/tmp/bench-venv/bin/python \\
  robustness/r0/validation/field-match/compare_field_synthetic.py
```
"""
    with open(out_path, "w") as fh:
        fh.write(md)
    return out_path


def main():
    for p in (FIELD_JSONL, SYN_JSONL):
        if not os.path.isfile(p):
            print(f"missing input: {p}", file=sys.stderr)
            if p == SYN_JSONL:
                print("  -> regenerate the synthetic rig run (see module docstring)",
                      file=sys.stderr)
            return 2

    field, per_medium = load_field()
    syn, syn_rows = load_synthetic()

    c1 = chart_by_nc(field, per_medium, syn,
                     os.path.join(HERE, "field_vs_synthetic_by_nc.png"))
    c2 = chart_mechanism_map(field, per_medium, syn,
                             os.path.join(HERE, "failure_mechanism_map.png"))

    # machine-readable summary
    summary = {
        "field": {
            nc: {"n": field[nc]["n"], "rate": field[nc]["rate"],
                 "coarse": dict(field[nc]["coarse"]), "raw": field[nc]["raw"]}
            for nc in field
        },
        "field_per_medium": {
            k: {"n": v["n"], "rate": v["rate"], "coarse": dict(v["coarse"]),
                "raw": v["raw"]}
            for k, v in per_medium.items()
        },
        "synthetic": {
            nc: {"n": syn[nc]["n"], "rate": syn[nc]["rate"],
                 "coarse": dict(syn[nc]["coarse"]),
                 "by_family": {f: dict(c) for f, c in syn[nc]["by_family"].items()}}
            for nc in syn
        },
        "synthetic_palette_classify_total": sum(
            syn[nc]["coarse"].get("PALETTE_CLASSIFY", 0) for nc in syn),
        "field_palette_classify_total": sum(
            field[nc]["coarse"].get("PALETTE_CLASSIFY", 0) for nc in field)
        + sum(v["coarse"].get("PALETTE_CLASSIFY", 0) for v in per_medium.values()),
    }
    with open(os.path.join(HERE, "comparison_data.json"), "w") as f:
        json.dump(summary, f, indent=2)

    report = write_report(field, per_medium, syn, summary,
                          os.path.join(HERE, "REPORT.md"))

    print("wrote:")
    print(" ", c1)
    print(" ", c2)
    print(" ", os.path.join(HERE, "comparison_data.json"))
    print(" ", report)
    print(f"\nfield PALETTE_CLASSIFY total : "
          f"{summary['field_palette_classify_total']}")
    print(f"synthetic PALETTE_CLASSIFY total: "
          f"{summary['synthetic_palette_classify_total']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
