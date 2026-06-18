#!/usr/bin/env python3
"""R3 verdict chart — decode-rate before/after each capture-side lever."""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

# Lever A — Tier-2 warm-stock prints (n=23), capture-side full-image WB
a_labels = ["raw\nbaseline", "gray-\nworld", "paper-\nwhite", "per-chan\nstretch", "white->255\n(strong)"]
a_rates = [13, 13, 13, 13, 4]
a_base = 13

# Lever B — ws5 video, best single registered frame vs fusion variants (each n=1 here)
b_labels = ["best single\nframe", "mean\nfusion", "median\nfusion", "sharp-mean\nfusion", "rectified\nfusion"]
b_rates = [0, 0, 0, 0, 0]

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(13, 5.2))

c1 = ["#9aa0a6"] + ["#d9534f" if r < a_base else "#5cb85c" if r > a_base else "#4a90d9"
                    for r in a_rates[1:]]
ax1.bar(a_labels, a_rates, color=c1, edgecolor="black", linewidth=0.6)
ax1.axhline(a_base, ls="--", color="#9aa0a6", lw=1.2, label=f"raw baseline {a_base}%")
ax1.set_ylim(0, 30)
ax1.set_ylabel("decode rate (%)")
ax1.set_title("Lever A — capture-side white-balance\n23 Tier-2 warm-stock prints (fail stage: colour-LDPC)")
for i, r in enumerate(a_rates):
    ax1.text(i, r + 0.6, f"{r}%", ha="center", fontsize=10, fontweight="bold")
ax1.legend(loc="upper right", fontsize=9)

ax2.bar(b_labels, b_rates, color="#d9534f", edgecolor="black", linewidth=0.6)
ax2.set_ylim(0, 30)
ax2.set_ylabel("decode rate (%)")
ax2.set_title("Lever B — multi-frame fusion\nws5 video, one clipped polychrome code (fail stage: DETECT)")
for i, r in enumerate(b_rates):
    ax2.text(i, r + 0.6, f"{r}%", ha="center", fontsize=10, fontweight="bold")
ax2.text(2, 22, "0% across ALL variants\n(+ perspective rectification)\ncode is clipped out of frame",
         ha="center", fontsize=10, color="#d9534f",
         bbox=dict(boxstyle="round", fc="#fff3f3", ec="#d9534f"))

fig.suptitle("R3 capture-side robustness levers — host-side validation (rig-graded)\n"
             "VERDICT: neither lever lifts decode rate on real captures",
             fontsize=13, fontweight="bold")
fig.tight_layout(rect=[0, 0, 1, 0.93])
out = "robustness/r3-test/charts/r3_verdict.png"
fig.savefig(out, dpi=130)
print("wrote", out)
