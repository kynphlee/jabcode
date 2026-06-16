#!/usr/bin/env python3
"""
zxing_compare.py -- comparative benchmark: QR (segno encode + zxing-cpp decode)
vs JABCode (our measured data). Same payloads, same PIL transcode transforms.

Honest by design: JABCode is expected to win density decisively, and likely to
lose decode latency and raw transcode-robustness to QR's mature, monochrome,
high-ECC reader. The point is the *clarified positioning*, not a one-sided win.

Outputs: charts/compare_density.png, compare_latency.png, compare_transcode.png
"""
import os, io, time, json
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import segno, zxingcpp
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data"); CH = os.path.join(HERE, "charts")
C_JB, C_QR = "#2b8cbe", "#e6a817"
plt.rcParams.update({"figure.dpi": 120, "font.size": 10, "axes.grid": True, "grid.alpha": 0.3, "axes.axisbelow": True})

def randbytes(n): return bytes((i * 131 + 17) & 0xFF for i in range(n))

def load_jsonl(name):
    p = os.path.join(DATA, name); out = []
    if os.path.exists(p):
        for ln in open(p):
            ln = ln.strip()
            if ln.startswith("{"):
                try: out.append(json.loads(ln))
                except Exception: pass
    return out

sweep = load_jsonl("sweep.jsonl"); tr = load_jsonl("transcode.jsonl")

# ---------- QR measurements ----------
def qr_png(data, ecc, scale=8):
    buf = io.BytesIO(); segno.make_qr(data, error=ecc).save(buf, kind="png", scale=scale)
    buf.seek(0); return Image.open(buf).convert("RGB")

def qr_max(ecc):
    lo, hi, best = 1, 3000, 0
    while lo <= hi:
        mid = (lo + hi) // 2
        try:
            segno.make_qr(randbytes(mid), error=ecc); best = mid; lo = mid + 1
        except Exception:
            hi = mid - 1
    return best

print("QR max single-symbol capacity (bytes, measured via segno):")
qr_cap = {e: qr_max(e) for e in ("l", "m", "q", "h")}
for e, v in qr_cap.items(): print(f"  ECC {e.upper()}: {v} B")

def qr_decode_ms(nbytes, iters=40):
    im = qr_png(randbytes(nbytes), "m", scale=6)
    for _ in range(4): zxingcpp.read_barcode(im)
    ts = []
    for _ in range(iters):
        a = time.perf_counter(); r = zxingcpp.read_barcode(im); b = time.perf_counter()
        ts.append((b - a) * 1000)
    ts.sort(); return ts[len(ts) // 2]

qr_lat = {n: qr_decode_ms(n) for n in (16, 64, 256)}
print("QR decode latency (ms):", {k: round(v, 3) for k, v in qr_lat.items()})

# transforms identical to transcode_survival.py
def jpeg(q):
    def f(im):
        b = io.BytesIO(); im.convert("RGB").save(b, "JPEG", quality=q); b.seek(0); return Image.open(b).convert("RGB")
    return f
def downscale(fr):
    def f(im):
        w, h = im.size; return im.resize((max(1, int(w*fr)), max(1, int(h*fr))), Image.BILINEAR).resize((w, h), Image.BILINEAR)
    return f
def chroma420(im):
    b = io.BytesIO(); im.convert("RGB").save(b, "JPEG", quality=95, subsampling=2); b.seek(0); return Image.open(b).convert("RGB")
TRANSFORMS = [("JPEG q75", jpeg(75)), ("JPEG q50", jpeg(50)), ("JPEG q30", jpeg(30)),
              ("downscale 0.5", downscale(0.5)), ("downscale 0.3", downscale(0.3)),
              ("chroma 4:2:0", chroma420), ("JPEG30 + ds0.5", lambda im: jpeg(30)(downscale(0.5)(im)))]

def qr_survive(name, tf, nbytes=80):
    im = qr_png(randbytes(nbytes), "m", scale=8)
    try: return 1.0 if zxingcpp.read_barcode(tf(im)) else 0.0
    except Exception: return 0.0
qr_surv = {n: qr_survive(n, tf) for n, tf in TRANSFORMS}
print("QR survival:", {k: int(v) for k, v in qr_surv.items()})

# ---------- 1. Density comparison ----------
def jb_cap(nc, ecc, key="cap_binary"):
    m = [r for r in sweep if r.get("sweep") == "capacity" and r["colors"] == nc and r["ecc"] == ecc]
    return m[0][key] if m else 0
labels = ["QR\nECC-H", "QR\nECC-M", "QR\nECC-L", "JABCode\nNc8", "JABCode\nNc64", "JABCode\nNc256"]
vals = [qr_cap["h"], qr_cap["m"], qr_cap["l"], jb_cap(8, 3), jb_cap(64, 3), jb_cap(256, 3)]
cols = [C_QR, C_QR, C_QR, C_JB, C_JB, C_JB]
fig, ax = plt.subplots(figsize=(8.5, 5))
bars = ax.bar(labels, vals, color=cols)
for b, v in zip(bars, vals): ax.text(b.get_x()+b.get_width()/2, v+120, f"{v}", ha="center", fontsize=9)
ax.set_ylabel("max single-symbol payload (bytes)")
ax.set_title("Density: QR vs JABCode — measured single-symbol capacity\n(JABCode at ECC 3; QR at its three usable ECC levels)")
ax.axhline(qr_cap["l"], color=C_QR, ls="--", lw=1, alpha=0.6)
fig.tight_layout(); fig.savefig(os.path.join(CH, "compare_density.png")); plt.close(fig)
print("  wrote compare_density.png")

# ---------- 2. Latency comparison ----------
jb_lat = {}
for r in sweep:
    if r.get("sweep") == "latency" and r["colors"] == 8:
        jb_lat[r["bytes"]] = r["decode_ms"]
sizes = sorted(set(qr_lat) & set(jb_lat)) or sorted(qr_lat)
fig, ax = plt.subplots(figsize=(8.5, 5))
ax.plot(list(qr_lat), list(qr_lat.values()), "-o", color=C_QR, label="QR (zxing-cpp decode)")
xs = sorted(jb_lat); ax.plot(xs, [jb_lat[s] for s in xs], "-o", color=C_JB, label="JABCode 8-colour (libjabcode decode)")
ax.set_xlabel("payload size (bytes)"); ax.set_ylabel("decode latency (ms, median)")
ax.set_xscale("log", base=2)
ax.set_title("Decode latency: QR vs JABCode (each in its native codec)")
ax.legend()
fig.tight_layout(); fig.savefig(os.path.join(CH, "compare_latency.png")); plt.close(fig)
print("  wrote compare_latency.png")

# ---------- 3. Transcode-survival comparison ----------
def jb_surv(nc):
    return {r["transform"]: r["survival"] for r in tr if r["colors"] == nc}
jb8, jb256 = jb_surv(8), jb_surv(256)
names = [n for n, _ in TRANSFORMS]
M = np.array([[qr_surv[n] for n in names],
              [jb8.get(n, np.nan) for n in names],
              [jb256.get(n, np.nan) for n in names]]).T
fig, ax = plt.subplots(figsize=(8.5, 5))
im = ax.imshow(M, aspect="auto", cmap="RdYlGn", vmin=0, vmax=1, origin="upper")
ax.set_xticks(range(3)); ax.set_xticklabels(["QR (ECC-M)", "JABCode\nNc8", "JABCode\nNc256"])
ax.set_yticks(range(len(names))); ax.set_yticklabels(names)
ax.set_title("Transcode-survival: QR vs JABCode (same transforms, 1 trial)")
for i in range(M.shape[0]):
    for j in range(M.shape[1]):
        if not np.isnan(M[i, j]): ax.text(j, i, f"{M[i,j]*100:.0f}", ha="center", va="center", fontsize=8)
fig.colorbar(im, label="decode survival")
fig.tight_layout(); fig.savefig(os.path.join(CH, "compare_transcode.png")); plt.close(fig)
print("  wrote compare_transcode.png")

# summary line for the report
print("\nSUMMARY:",
      f"QR max {qr_cap['l']}B vs JABCode Nc256 {jb_cap(256,3)}B (ECC3) /"
      f" {jb_cap(256,1)}B (ECC1);",
      f"QR decode {qr_lat.get(64,0):.2f}ms vs JABCode-8c {jb_lat.get(64,0):.2f}ms @64B")
