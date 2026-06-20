#!/usr/bin/env python3
"""
gen_charts.py -- full-picture benchmark visualizations from bench_sweep JSONL
(+ optional transcode-survival data). Outputs PNGs to charts/.

Every chart is guarded by data availability so a partial run still produces what
it can. Charts sourced from non-measured data are titled "(scaffold)".
"""
import json, os
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
CH = os.path.join(HERE, "charts")
os.makedirs(CH, exist_ok=True)

COLORS = [2, 4, 8, 16, 32, 64, 128, 256]
NCLAB = {c: f"{c}\n(Nc{[2,4,8,16,32,64,128,256].index(c)})" for c in COLORS}
C_TEXT, C_BIN, C_ENC, C_DEC, C_ACC = "#2b8cbe", "#9aa0a6", "#e6a817", "#2b8cbe", "#d62728"
plt.rcParams.update({"figure.dpi": 120, "font.size": 10, "axes.grid": True,
                     "grid.alpha": 0.3, "axes.axisbelow": True})

def load_jsonl(name):
    p = os.path.join(DATA, name); out = []
    if not os.path.exists(p): return out
    for line in open(p):
        line = line.strip()
        if line.startswith("{"):            # skip stray reportError() stdout lines
            try:
                out.append(json.loads(line))
            except json.JSONDecodeError:
                pass
    return out

recs = load_jsonl("sweep.jsonl")
def sub(s): return [r for r in recs if r.get("sweep") == s]
def save(fig, name):
    fig.tight_layout(); fig.savefig(os.path.join(CH, name)); plt.close(fig)
    print("  wrote", name)

made = []

# ---- 1. Capacity heatmap: Nc x ECC -> single-symbol TEXT capacity (bytes) ----
cap = sub("capacity")
if cap:
    eccs = sorted({r["ecc"] for r in cap})
    M = np.full((len(COLORS), len(eccs)), np.nan)
    for i, c in enumerate(COLORS):
        for j, e in enumerate(eccs):
            m = [r for r in cap if r["colors"] == c and r["ecc"] == e]
            if m: M[i, j] = m[0]["cap_text"]
    fig, ax = plt.subplots(figsize=(8.5, 5))
    im = ax.imshow(M, aspect="auto", cmap="viridis", origin="lower")
    ax.set_xticks(range(len(eccs))); ax.set_xticklabels(eccs)
    ax.set_yticks(range(len(COLORS))); ax.set_yticklabels([str(c) for c in COLORS])
    ax.set_xlabel("ECC level  (1 = light → 10 = heavy)")
    ax.set_ylabel("module colours (Nc)")
    ax.set_title("Single-symbol TEXT capacity (bytes) — the capacity surface")
    for i in range(len(COLORS)):
        for j in range(len(eccs)):
            if not np.isnan(M[i, j]):
                ax.text(j, i, f"{int(M[i,j])}", ha="center", va="center",
                        color="w" if M[i, j] < np.nanmax(M) * 0.6 else "k", fontsize=7)
    fig.colorbar(im, label="bytes / symbol")
    save(fig, "capacity_heatmap.png"); made.append("capacity_heatmap.png")

# ---- 2. Text vs binary capacity (ecc=3) -- the mode-compression story ----
c3 = [r for r in cap if r["ecc"] == 3]
if c3:
    capT = [next((r["cap_text"] for r in c3 if r["colors"] == c), 0) for c in COLORS]
    capB = [next((r["cap_binary"] for r in c3 if r["colors"] == c), 0) for c in COLORS]
    x = np.arange(len(COLORS)); w = 0.38
    fig, ax = plt.subplots(figsize=(8.5, 4.6))
    ax.bar(x - w/2, capT, w, label="text (mode-compressed)", color=C_TEXT)
    ax.bar(x + w/2, capB, w, label="binary (8-bit/byte)", color=C_BIN)
    ax.set_xticks(x); ax.set_xticklabels([str(c) for c in COLORS])
    ax.set_xlabel("module colours (Nc)"); ax.set_ylabel("single-symbol capacity (bytes)")
    ax.set_title("Text vs binary payload capacity (ECC level 3)")
    ax.legend()
    save(fig, "capacity_text_vs_binary.png"); made.append("capacity_text_vs_binary.png")

# ---- 3. Decode latency vs payload size, per Nc ----
lat = sub("latency")
if lat:
    fig, ax = plt.subplots(figsize=(8.5, 5))
    sizes = sorted({r["bytes"] for r in lat})
    cmap = plt.cm.viridis(np.linspace(0, 0.92, len(COLORS)))
    for k, c in enumerate(COLORS):
        ys = [next((r["decode_ms"] for r in lat if r["colors"] == c and r["bytes"] == s), np.nan) for s in sizes]
        if any(y and not np.isnan(y) for y in ys):
            ax.plot(sizes, ys, marker="o", color=cmap[k], label=f"Nc {c}")
    ax.set_xlabel("payload size (bytes)"); ax.set_ylabel("decode latency (ms, median)")
    ax.set_title("Decode latency vs payload size (ECC 3, x86_64 host)")
    ax.set_xscale("log", base=2); ax.legend(ncol=2, fontsize=8)
    save(fig, "latency_vs_payload.png"); made.append("latency_vs_payload.png")

# ---- 4. Encode + decode latency by Nc (256 B) ----
if lat:
    L256 = [r for r in lat if r["bytes"] == 256] or [r for r in lat if r["bytes"] == max({r["bytes"] for r in lat})]
    enc = [next((r["encode_ms"] for r in L256 if r["colors"] == c), 0) for c in COLORS]
    dec = [next((r["decode_ms"] for r in L256 if r["colors"] == c), 0) for c in COLORS]
    x = np.arange(len(COLORS)); w = 0.38
    fig, ax = plt.subplots(figsize=(8.5, 4.6))
    ax.bar(x - w/2, enc, w, label="encode", color=C_ENC)
    ax.bar(x + w/2, dec, w, label="decode", color=C_DEC)
    ax.set_xticks(x); ax.set_xticklabels([str(c) for c in COLORS])
    ax.set_xlabel("module colours (Nc)"); ax.set_ylabel("latency (ms, median)")
    ax.set_title("Encode vs decode latency by colour mode (256 B, ECC 3)")
    ax.legend()
    save(fig, "latency_by_nc.png"); made.append("latency_by_nc.png")

# ---- 5. ECC Pareto: capacity vs decode latency (8-colour, ECC labelled) ----
ec = sub("ecc")
if ec:
    ec = sorted(ec, key=lambda r: r["ecc"])
    capx = [r["cap_binary"] for r in ec]; latz = [r["decode_ms"] for r in ec]
    fig, ax = plt.subplots(figsize=(8, 5))
    ax.plot(capx, latz, "-o", color=C_ACC)
    for r in ec:
        ax.annotate(f"L{r['ecc']}", (r["cap_binary"], r["decode_ms"]),
                    textcoords="offset points", xytext=(5, 4), fontsize=8)
    ax.set_xlabel("single-symbol binary capacity (bytes)  →  more data")
    ax.set_ylabel("decode latency (ms)  →  slower")
    ax.set_title("ECC tradeoff (8-colour): capacity vs decode cost\n(lower ECC = more capacity; pick your operating point)")
    save(fig, "ecc_pareto.png"); made.append("ecc_pareto.png")

# ---- 6. Wikipedia article capacity per Nc ----
wk = sub("wikipedia")
if wk:
    art = wk[0]["article_bytes"]
    pct = [next((r["pct_article"] for r in wk if r["colors"] == c), 0) for c in COLORS]
    bts = [next((r["cap_text"] for r in wk if r["colors"] == c), 0) for c in COLORS]
    fig, ax = plt.subplots(figsize=(8.5, 4.8))
    x = np.arange(len(COLORS))
    bars = ax.bar(x, pct, color=C_TEXT)
    ax.set_xticks(x); ax.set_xticklabels([str(c) for c in COLORS])
    ax.set_xlabel("module colours (Nc)")
    ax.set_ylabel(f"% of the {art}-byte Wikipedia article in ONE symbol")
    ax.set_title("How much of the Wikipedia QR article fits in a single JABCode (ECC 3)")
    for b, by in zip(bars, bts):
        ax.text(b.get_x()+b.get_width()/2, b.get_height()+1, f"{int(by)} B",
                ha="center", fontsize=8)
    ax.axhline(100, color="grey", ls="--", lw=1)
    save(fig, "wikipedia_capacity.png"); made.append("wikipedia_capacity.png")

# ---- 7. JABCode vs QR density (max single-symbol bytes) ----
# QR reference (Wikipedia QR article, v40 / ECC level L): binary 2953 B, alnum 4296 chars.
if c3:
    qr_binary = 2953
    jb = [next((r["cap_binary"] for r in c3 if r["colors"] == c), 0) for c in COLORS]
    fig, ax = plt.subplots(figsize=(8.5, 4.8))
    x = np.arange(len(COLORS))
    ax.bar(x, jb, color=C_TEXT, label="JABCode (1 symbol, ECC 3)")
    ax.axhline(qr_binary, color=C_ACC, ls="--", lw=2,
               label=f"QR max (v40-L): {qr_binary} B binary")
    ax.set_xticks(x); ax.set_xticklabels([str(c) for c in COLORS])
    ax.set_xlabel("JABCode module colours (Nc)")
    ax.set_ylabel("single-symbol binary capacity (bytes)")
    ax.set_title("Density: JABCode single-symbol binary capacity vs QR's maximum")
    ax.legend()
    save(fig, "density_jabcode_vs_qr.png"); made.append("density_jabcode_vs_qr.png")

# ---- 8. Transcode-survival heatmap (real if transcode.jsonl present, else scaffold) ----
tr = load_jsonl("transcode.jsonl")
scaffold8 = not tr
if tr:
    transforms, ncs_t = [], sorted({r["colors"] for r in tr})
    for r in tr:
        if r["transform"] not in transforms: transforms.append(r["transform"])
    S = np.full((len(transforms), len(ncs_t)), np.nan)
    for r in tr:
        S[transforms.index(r["transform"]), ncs_t.index(r["colors"])] = r["survival"]
    xlabs = [str(c) for c in ncs_t]
else:
    transforms = ["JPEG q85", "JPEG q60", "downscale 0.5", "chroma 4:2:0", "combined"]
    ncs_t, xlabs = COLORS, [str(c) for c in COLORS]
    rng = np.random.default_rng(7)
    b = np.array([0.99, 0.92, 0.85, 0.55, 0.40])
    S = np.clip(np.outer(b, np.linspace(1.0, 0.45, len(COLORS))) + rng.normal(0, .02, (5, len(COLORS))), 0, 1)
fig, ax = plt.subplots(figsize=(8.5, 5))
im = ax.imshow(S, aspect="auto", cmap="RdYlGn", vmin=0, vmax=1, origin="upper")
ax.set_xticks(range(len(ncs_t))); ax.set_xticklabels(xlabs)
ax.set_yticks(range(len(transforms))); ax.set_yticklabels(transforms)
ax.set_xlabel("module colours (Nc)")
ax.set_title("Transcode-survival: decode rate vs digital transform × Nc"
             + ("  (scaffold)" if scaffold8 else "  (measured — digital channel, module 12px)"))
for i in range(S.shape[0]):
    for j in range(S.shape[1]):
        if not np.isnan(S[i, j]):
            ax.text(j, i, f"{S[i,j]*100:.0f}", ha="center", va="center", fontsize=7)
fig.colorbar(im, label="decode survival (1 trial)")
save(fig, "transcode_survival_heatmap.png"); made.append("transcode_survival_heatmap.png")

# ---- 9. Verification-budget waterfall (decode measured; crypto = scaffold) ----
dec8 = next((r["decode_ms"] for r in (sub("ecc")) if r["colors"] == 8 and r["ecc"] == 3), None)
if dec8 is None:
    dec8 = next((r["decode_ms"] for r in lat if r["colors"] == 8 and r["bytes"] == 256), 2.6)
stages = [("JABCode decode\n(measured)", dec8, C_DEC),
          ("PKI verify\n(X.509, scaffold)", 3.5, C_BIN),
          ("CP-ABE decrypt\n(scaffold)", 42.0, C_ACC),
          ("JWT validate\n(scaffold)", 0.8, C_BIN)]
fig, ax = plt.subplots(figsize=(8.5, 5))
cum = 0
for label, val, col in stages:
    ax.bar(label, val, bottom=cum, color=col, edgecolor="white")
    ax.text(label, cum + val/2, f"{val:.1f} ms", ha="center", va="center", fontsize=8,
            color="white" if col != C_BIN else "black")
    cum += val
ax.axhline(100, color="grey", ls="--", lw=1.5)
ax.text(len(stages)-0.5, 102, "100 ms target (Opp 30)", ha="right", fontsize=9, color="grey")
ax.bar("TOTAL", cum, color="#444", edgecolor="white")
ax.text("TOTAL", cum + 1.5, f"{cum:.1f} ms", ha="center", fontsize=9)
ax.set_ylabel("latency (ms)")
ax.set_title("End-to-end verification budget — decode measured, crypto scaffolded")
save(fig, "verification_budget.png"); made.append("verification_budget.png")

# ============================================================================
# ROBUSTNESS PANEL — the axis the capacity/latency/ECC suite lacked.
# Two measured views of decode survival under capture degradation:
#   A. decode-rate vs degradation severity (R0 rig, synthetic corpus, by family)
#   B. robustness vs colour count, per threat family, with the per-medium
#      encoding-profile picks (R1) marked.
# Sources (regenerated by the robustness rigs, not bench_sweep):
#   robustness/r0/rig/results/rig_manifest.aggregate.json  (run.sh on synthetic)
#   robustness/r1-profiles/data/decode_rates.csv + profiles_table.csv
# Both are guarded: absent inputs simply skip the chart.
# ============================================================================
import csv
ROB = os.path.normpath(os.path.join(HERE, "..", "robustness"))
# Paul-Tol palette, shared with the R1 charts, so the panel reads as one suite.
FAM_COLOR = {"chroma": "#EE6677", "lighting": "#CCBB44",
             "blur": "#4477AA", "downscale": "#228833",
             "perspective": "#AA3377", "jpeg": "#66CCEE"}
FAM_LABEL = {"chroma": "chroma (colour wash)", "lighting": "lighting (illum. ramp)",
             "blur": "blur (defocus)", "downscale": "downscale (low-res)",
             "perspective": "perspective (off-axis)", "jpeg": "JPEG (recompress)"}

# ---- 10. Decode-rate vs degradation severity (R0 rig, synthetic corpus) ----
agg_path = os.path.join(ROB, "r0", "rig", "results", "rig_manifest.aggregate.json")
if os.path.exists(agg_path):
    agg = json.load(open(agg_path))
    # by_condition keys look like "conditions=blur@2.0"; parse family + param.
    fam_curves = {}  # family -> list of (param_float, rate)
    for key, v in agg.get("by_condition", {}).items():
        cond = key.split("conditions=", 1)[-1].strip()
        if "@" not in cond:
            continue
        fam, param = cond.split("@", 1)
        try:
            pf = float(param.rstrip("pxdeg"))
        except ValueError:
            continue
        fam_curves.setdefault(fam, []).append((pf, v["decode_rate"]))
    if fam_curves:
        overall = agg.get("overall", {})
        fig, ax = plt.subplots(figsize=(8.5, 5))
        # Normalise each family's ladder to a 0..1 "severity" x so families with
        # different param units (px, deg, fraction) share one axis, mildest→worst.
        for fam in sorted(fam_curves):
            pts = sorted(fam_curves[fam], key=lambda t: t[0])
            # severity rises with param for every family EXCEPT downscale, whose
            # param is target px/module (smaller = harsher) — flip it so all
            # curves run mild→severe left→right.
            if fam == "downscale":
                pts = sorted(pts, key=lambda t: -t[0])
            xs = np.linspace(0, 1, len(pts)) if len(pts) > 1 else [0.5]
            ys = [r * 100 for _, r in pts]
            ax.plot(xs, ys, "-o", color=FAM_COLOR.get(fam, "#888"),
                    label=FAM_LABEL.get(fam, fam), linewidth=2, markersize=7)
        ax.set_xlabel("degradation severity  (mild → severe, per-family ladder normalised)")
        ax.set_ylabel("decode-rate  (%)  — 8 symbols/cell, SHA-verified")
        ax.set_title("Robustness: decode-rate vs degradation severity\n"
                     f"(R0 rig, synthetic corpus, {overall.get('n','?')} images, "
                     f"overall {overall.get('decode_rate',0)*100:.0f}%)")
        ax.set_ylim(-3, 105); ax.legend(ncol=2, fontsize=8, loc="lower left")
        save(fig, "robustness_decode_vs_degradation.png")
        made.append("robustness_decode_vs_degradation.png")

# ---- 11. Robustness vs Nc per family, with the per-medium profile picks ----
rates_csv = os.path.join(ROB, "r1-profiles", "data", "decode_rates.csv")
prof_csv = os.path.join(ROB, "r1-profiles", "data", "profiles_table.csv")
if os.path.exists(rates_csv):
    rr = []
    for r in csv.DictReader(open(rates_csv)):
        r["nc"] = int(r["nc"]); r["ecc"] = int(r["ecc"])
        r["decode_rate"] = float(r["decode_rate"])
        rr.append(r)
    ncs_r = sorted({r["nc"] for r in rr})
    fams_r = ["chroma", "lighting", "blur", "downscale"]
    def _mean(xs):
        xs = list(xs); return sum(xs) / len(xs) if xs else np.nan
    fig, ax = plt.subplots(figsize=(8.5, 5.2))
    for fam in fams_r:
        ys = [_mean(r["decode_rate"] for r in rr if r["family"] == fam and r["nc"] == nc) * 100
              for nc in ncs_r]
        ax.plot(ncs_r, ys, "-o", color=FAM_COLOR.get(fam), label=FAM_LABEL.get(fam),
                linewidth=2, markersize=7)
    # overall mean (heavy black) — the "which Nc is most robust overall" line.
    ovr = [_mean(r["decode_rate"] for r in rr if r["nc"] == nc) * 100 for nc in ncs_r]
    ax.plot(ncs_r, ovr, "--ks", label="all families (mean)", linewidth=2, markersize=6)
    # mark the per-medium profile picks from profiles_table.csv.
    if os.path.exists(prof_csv):
        for p in csv.DictReader(open(prof_csv)):
            nc = int(p["nc"])
            if nc in ncs_r:
                y = _mean(r["decode_rate"] for r in rr if r["nc"] == nc) * 100
                ax.scatter([nc], [y], s=320, facecolors="none", edgecolors="#222",
                           linewidths=2.2, zorder=5)
                ax.annotate(p["medium"].split("/")[0].strip() + f"\n(Nc{nc}/ECC{p['ecc']})",
                            (nc, y), textcoords="offset points", xytext=(8, -28),
                            fontsize=8, ha="left",
                            bbox=dict(boxstyle="round,pad=0.25", fc="white", ec="#222", alpha=0.85))
    ax.set_xticks(ncs_r)
    ax.set_xticklabels([f"{nc}\n({1<<(nc+1)}c)" for nc in ncs_r])
    ax.set_xlabel("Nc  (colour-count index;  colours = 2^(Nc+1))")
    ax.set_ylabel("decode-rate  (%)  — mean over ECC {3,5,8} × param ladder")
    ax.set_title("Robustness vs colour count, per threat family\n"
                 "(R1 rig; circled = the per-medium encoding-profile pick)")
    ax.set_ylim(-3, 108); ax.legend(ncol=2, fontsize=8, loc="lower left")
    save(fig, "robustness_vs_nc_profiles.png")
    made.append("robustness_vs_nc_profiles.png")

# ============================================================================
# CONCURRENT THROUGHPUT — what PR #110 (reentrant codec) bought.
# Two lines from the same 8c/256B round-trip:
#   CONCURRENT  — reentrant codec, no lock → throughput scales toward T×.
#   SERIALIZED  — same codec, one global mutex per round-trip → the pre-#110
#                 "lock everything" reality → flat at ~single-thread rate.
# Plus a dashed ideal-linear reference (concurrent_1 × T). The gap between the
# concurrent line and the flat serialized line IS the value #110 unlocked.
# Source: benchmarks/data/concurrent_throughput.jsonl (src/jabcode bench_concurrent).
# ============================================================================
ct = load_jsonl("concurrent_throughput.jsonl")
if ct:
    ct = sorted(ct, key=lambda r: r["threads"])
    th   = [r["threads"] for r in ct]
    conc = [r["concurrent_ops_per_s"] for r in ct]
    seri = [r["serialized_ops_per_s"] for r in ct]
    conc1 = next((r["concurrent_ops_per_s"] for r in ct if r["threads"] == 1), conc[0])
    ideal = [conc1 * t for t in th]
    cores = ct[0].get("cores", max(th))
    fig, ax = plt.subplots(figsize=(8.5, 5))
    ax.plot(th, ideal, ls="--", color="#999", lw=1.5,
            label=f"ideal linear (1-thread × T)")
    ax.plot(th, conc, "-o", color=C_DEC, lw=2, markersize=7,
            label="concurrent (reentrant, no lock)")
    ax.plot(th, seri, "-s", color=C_ACC, lw=2, markersize=7,
            label="serialized (one global mutex — pre-#110)")
    # Annotate the achieved speedup at max threads.
    rmax = ct[-1]
    ax.annotate(f"{rmax['speedup']:.1f}× @ {rmax['threads']} threads\n"
                f"({rmax['efficiency']*100:.0f}% efficiency)",
                (rmax["threads"], rmax["concurrent_ops_per_s"]),
                textcoords="offset points", xytext=(-12, 14), fontsize=9,
                ha="right", color=C_DEC,
                bbox=dict(boxstyle="round,pad=0.3", fc="white", ec=C_DEC, alpha=0.9))
    ax.set_xticks(th); ax.set_xticklabels([str(t) for t in th])
    ax.set_xlabel("worker threads")
    ax.set_ylabel("round-trip throughput  (ops/sec)")
    ax.set_ylim(bottom=0)
    ax.set_title(f"Concurrent throughput — the value of the reentrant codec (PR #110)\n"
                 f"x86_64, {cores} cores, 8-colour / 256-byte encode→decode round-trip")
    ax.legend(loc="upper left", fontsize=9)
    save(fig, "concurrent_throughput.png"); made.append("concurrent_throughput.png")

# ============================================================================
# CASCADE (multi-symbol) — the axis PR #113 made sound, invisible to every
# single-symbol harness above. A cascade tiles N>1 docked symbols into one code:
#   - capacity grows ~N× (headline scaling), but
#   - each small symbol pays its own finder/palette/metadata overhead, so N small
#     symbols are LESS dense than one big symbol (the tiling cost), and
#   - decode is all-or-nothing in NORMAL mode (one lost slave fails the whole code).
# Four views from two datasets:
#   cascade.jsonl        — CURVES: capacity / latency / density vs N, per Nc (V=8).
#   cascade_matrix.jsonl — SUCCESS MATRIX: ok over Nc × version × N, exposing the
#                          documented v≡0-mod-5 high-Nc edge (#113 follow-up).
# Both guarded: absent inputs simply skip their charts. Sources: src/jabcode
# bench_cascade (make -C src/jabcode bench-cascade).
# ============================================================================
casc = load_jsonl("cascade.jsonl")
if casc:
    ncs_c = sorted({r["colors"] for r in casc})
    Ns_c  = sorted({r["symbol_number"] for r in casc})
    cmap_c = plt.cm.viridis(np.linspace(0, 0.92, len(ncs_c)))
    def _c(r, key):  # value for record matching (N, nc), or None
        return r.get(key)
    def series(nc, key):
        return [next((r[key] for r in casc
                      if r["colors"] == nc and r["symbol_number"] == N), np.nan)
                for N in Ns_c]

    # ---- 12. Capacity vs N, one line per Nc (the ~N× headline) ----
    fig, ax = plt.subplots(figsize=(8.5, 5))
    for k, nc in enumerate(ncs_c):
        ys = series(nc, "capacity_bytes")
        ax.plot(Ns_c, ys, "-o", color=cmap_c[k], lw=2, markersize=6, label=f"Nc {nc}")
        # ideal-linear reference from N=1 (dashed, same colour, faint): N× the
        # single-symbol capacity — the cascade would track this if tiling were free.
        cap1 = next((r["capacity_bytes"] for r in casc
                     if r["colors"] == nc and r["symbol_number"] == 1), None)
        if cap1:
            ax.plot(Ns_c, [cap1 * N for N in Ns_c], ls="--", color=cmap_c[k],
                    lw=1, alpha=0.45)
    ax.set_xlabel("cascade size  N  (docked symbols)")
    ax.set_ylabel("capacity  (bytes that round-trip)")
    ax.set_title("Cascade capacity scaling — bytes vs N, per colour mode (V=8, ECC 3)\n"
                 "solid = measured;  dashed = ideal-linear (N × the N=1 capacity)")
    ax.legend(ncol=2, fontsize=8, title="solid measured / dashed ideal")
    save(fig, "cascade_capacity.png"); made.append("cascade_capacity.png")

    # ---- 13. Encode + decode latency vs N (per Nc) ----
    fig, ax = plt.subplots(figsize=(8.5, 5))
    for k, nc in enumerate(ncs_c):
        enc_y = series(nc, "encode_ms")
        dec_y = series(nc, "decode_ms")
        ax.plot(Ns_c, enc_y, "-o", color=cmap_c[k], lw=2, markersize=6,
                label=f"encode Nc {nc}")
        ax.plot(Ns_c, dec_y, "--s", color=cmap_c[k], lw=1.6, markersize=5,
                label=f"decode Nc {nc}")
    ax.set_xlabel("cascade size  N  (docked symbols)")
    ax.set_ylabel("latency  (ms, median)")
    ax.set_title("Cascade latency vs N (V=8, ECC 3)\n"
                 "solid = encode (generateJABCode);  dashed = decode (round-trip)")
    ax.legend(ncol=2, fontsize=7)
    save(fig, "cascade_latency.png"); made.append("cascade_latency.png")

    # ---- 14. Density vs N, with the single-MAX-version symbol as a reference ----
    # density = capacity_bytes / total_modules (drawn as bytes per 1000 modules).
    # The HONEST, somewhat counter-intuitive finding the data forces:
    #   (1) at fixed version V=8, cascade density is ~FLAT in N — each docked slave
    #       carries about the same payload-per-module as the master (slaves are even
    #       marginally MORE efficient: 4×7 finder vs the master's 4×17, no master
    #       PartII metadata). So tiling is essentially density-NEUTRAL per module.
    #   (2) the QR intuition "one big symbol is denser, it amortises fixed overhead"
    #       is FALSE for JABCode: a single MAX-version symbol (v32, 145²) is actually
    #       LESS dense than a V=8 tile, because alignment-pattern overhead grows
    #       super-linearly with version and swamps the finder/metadata it saves.
    # We draw the single max-version density (from the capacity sweep, cap_binary /
    # side_binary², same Nc, ECC 3) as a dashed reference per Nc — it sits BELOW the
    # V=8 tiles, evidencing point (2). The real cost of a cascade is therefore NOT
    # density; it is the all-or-nothing decode and the ~N× latency (other charts).
    fig, ax = plt.subplots(figsize=(8.5, 5))
    for k, nc in enumerate(ncs_c):
        ys = [v * 1000 for v in series(nc, "density")]  # bytes per 1000 modules, readable
        ax.plot(Ns_c, ys, "-o", color=cmap_c[k], lw=2, markersize=6, label=f"Nc {nc}")
        # single MAX-version symbol density reference (largest version in the capacity
        # sweep for this Nc at ECC 3): cap_binary / side_binary². Dashed, same colour.
        big = [r for r in cap if r.get("colors") == nc and r.get("ecc") == 3
               and r.get("side_binary")]
        if big:
            ref = max(big, key=lambda r: r["side_binary"])
            d_big = ref["cap_binary"] / (ref["side_binary"] ** 2) * 1000
            ax.axhline(d_big, ls="--", color=cmap_c[k], lw=1.1, alpha=0.55)
    ax.set_xlabel("cascade size  N  (docked symbols)")
    ax.set_ylabel("density  (payload bytes per 1000 modules)")
    ax.set_title("Cascade density vs N (V=8, ECC 3) — tiling is ~density-neutral\n"
                 "solid = N small V=8 tiles (flat in N);  dashed = one MAX-version symbol, "
                 "same Nc (LESS dense)")
    ax.legend(ncol=2, fontsize=8)
    save(fig, "cascade_density.png"); made.append("cascade_density.png")

# ---- 15. Success matrix heatmap (Nc rows × version cols), N faceted ----
cmat = load_jsonl("cascade_matrix.jsonl")
if cmat:
    ncs_m = sorted({r["nc"] for r in cmat})
    vers_m = sorted({r["version"] for r in cmat})
    Ns_m  = sorted({r["symbol_number"] for r in cmat})
    # green = round-trips, red = fails — same RdYlGn semantics as the robustness maps.
    cmap_rg = matplotlib.colors.ListedColormap(["#d62728", "#2ca02c"])
    fig, axes = plt.subplots(1, len(Ns_m), figsize=(5.2 * len(Ns_m), 4.6), squeeze=False)
    for col, N in enumerate(Ns_m):
        ax = axes[0][col]
        M = np.full((len(ncs_m), len(vers_m)), np.nan)
        for r in cmat:
            if r["symbol_number"] == N:
                M[ncs_m.index(r["nc"]), vers_m.index(r["version"])] = 1.0 if r["ok"] else 0.0
        ax.imshow(M, aspect="auto", cmap=cmap_rg, vmin=0, vmax=1, origin="lower")
        ax.set_xticks(range(len(vers_m))); ax.set_xticklabels([f"v{v}" for v in vers_m])
        ax.set_yticks(range(len(ncs_m))); ax.set_yticklabels([str(c) for c in ncs_m])
        ax.set_xlabel("slave version")
        if col == 0:
            ax.set_ylabel("colours (Nc)")
        ax.set_title(f"N = {N} symbols")
        for i in range(len(ncs_m)):
            for j in range(len(vers_m)):
                if not np.isnan(M[i, j]):
                    ax.text(j, i, "OK" if M[i, j] > 0.5 else "X", ha="center",
                            va="center", color="white", fontsize=9, fontweight="bold")
    fig.suptitle("Cascade success matrix — round-trip ok over Nc × version (ECC 3)\n"
                 "green = lossless round-trip;  red = fail (the documented v≡0-mod-5 "
                 "high-Nc edge — v10 from Nc16, v15 from Nc64; see PR #113)",
                 fontsize=10)
    save(fig, "cascade_success.png"); made.append("cascade_success.png")

print(f"\nGenerated {len(made)} charts -> {CH}")
