#!/usr/bin/env python3
"""Join the native and WASM decode runs into the A/B decode-rate table.

Fairness gates enforced here, not just claimed:
  1. pixel identity — every image's canvas-ImageData SHA-256 must equal the
     native readImage SHA-256; mismatching images are EXCLUDED from the
     decode-rate table and listed separately (same file != same pixels).
  2. success rule — known-payload images count as decoded only if the decoded
     bytes' SHA-256 matches the clean-source hash (both sides). The ws5 real
     frames (payload unknown) are reported as decode-parity only, never mixed
     into the rate rows.

Output: bench/results/AB_TABLE.md (+ summary JSON on stdout).
"""
import json
import os
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "results")

def jl(name):
    with open(os.path.join(RES, name)) as f:
        return [json.loads(l) for l in f if l.strip()]

manifest = {e["id"]: e for e in json.load(open(os.path.join(HERE, "manifest.json")))["entries"]}
native = {r["id"]: r for r in jl("native.jsonl")}
quiet = {r["id"]: r for r in jl("native_quiet.jsonl")}
wasm = {r["id"]: r for r in jl("wasm_baseline.jsonl")}
simd = {r["id"]: r for r in jl("wasm_simd.jsonl")}
npx = {r["id"]: r for r in jl("native_px_sha.jsonl")}

# Rate cross-check: the quiet latency probe must reach the same decode
# outcome as the rig probe on every image, or the latency column would be
# describing a different decoder run than the rate columns.
disagree = [rid for rid in native
            if bool(native[rid].get("decode_ok")) != bool(quiet.get(rid, {}).get("decode_ok"))]
if disagree:
    raise SystemExit(f"rig vs quiet-probe decode disagreement on: {disagree}")

# ---- gate 1: pixel identity --------------------------------------------
px_mismatch = []
for rid, e in manifest.items():
    a = npx.get(rid, {}).get("px_sha")
    b = wasm.get(rid, {}).get("px_sha")
    c = simd.get(rid, {}).get("px_sha")
    if not (a and b and c and a == b == c):
        px_mismatch.append({"id": rid, "native": a, "wasm": b, "simd": c})

excluded = {m["id"] for m in px_mismatch}

# ---- success predicates --------------------------------------------------
def native_ok(r):
    return bool(r.get("payload_match"))

def wasm_ok(r):
    return r.get("sha_match") is True

# ---- aggregate known-payload cells ---------------------------------------
cells = defaultdict(lambda: {"n": 0, "nat": 0, "was": 0, "sim": 0,
                             "nat_ms": [], "was_ms": [], "sim_ms": []})
parity_rows = []
for rid, e in manifest.items():
    if rid in excluded:
        continue
    n, w, s = native.get(rid), wasm.get(rid), simd.get(rid)
    if not (n and w and s):
        continue
    if not e["payload_sha256"]:
        parity_rows.append({
            "id": rid, "native_decode": bool(n["decode_ok"]),
            "wasm_decode": bool(w["decode_ok"]), "simd_decode": bool(s["decode_ok"]),
        })
        continue
    c = cells[(e["color_count"], e["conditions"])]
    c["n"] += 1
    c["nat"] += 1 if native_ok(n) else 0
    c["was"] += 1 if wasm_ok(w) else 0
    c["sim"] += 1 if wasm_ok(s) else 0
    c["nat_ms"].append(quiet[rid]["decode_ms"])  # latency from the quiet probe
    c["was_ms"].append(w["decode_ms"])
    c["sim_ms"].append(s["decode_ms"])

def med(v):
    v = sorted(v)
    return v[len(v) // 2] if v else float("nan")

lines = []
lines.append("# WASM-in-browser vs native decode — A/B table")
lines.append("")
lines.append(f"- corpus: {len(manifest)} images; pixel-identity verified on "
             f"{len(manifest) - len(px_mismatch)}, mismatched (excluded from rates): {len(px_mismatch)}")
lines.append("- success rule: SHA-256(decoded bytes) == clean-source hash, both paths")
lines.append("- native rates = R0 rig probe; native latency = quiet probe (no diag-verbose/fd-capture "
             "overhead — the rig's instrumented times are not comparable to the quiet WASM runs); "
             "rate agreement between the two native probes is asserted per image")
lines.append("- wasm/simd = browser canvas ImageData -> gamutlock-wasm (quiet wrapper)")
lines.append("")
lines.append("| colours | condition | n | native | wasm | wasm-simd | native med ms | wasm med ms | simd med ms |")
lines.append("|---|---|---|---|---|---|---|---|---|")
for (cc, cond), c in sorted(cells.items()):
    pct = lambda k: f"{100.0 * c[k] / c['n']:.0f}%"
    lines.append(
        f"| {cc} | {cond} | {c['n']} | {pct('nat')} | {pct('was')} | {pct('sim')} "
        f"| {med(c['nat_ms']):.1f} | {med(c['was_ms']):.1f} | {med(c['sim_ms']):.1f} |")

if parity_rows:
    lines.append("")
    lines.append("## Real Camera2 frames (payload unknown — decode parity only)")
    lines.append("")
    lines.append("| frame | native | wasm | wasm-simd |")
    lines.append("|---|---|---|---|")
    for p in parity_rows:
        t = lambda b: "decoded" if b else "no decode"
        lines.append(f"| {p['id']} | {t(p['native_decode'])} | {t(p['wasm_decode'])} | {t(p['simd_decode'])} |")

if px_mismatch:
    lines.append("")
    lines.append("## Pixel-identity mismatches (excluded from rate rows)")
    lines.append("")
    for m in px_mismatch:
        lines.append(f"- {m['id']}: native {m['native'] and m['native'][:12]} vs "
                     f"wasm {m['wasm'] and m['wasm'][:12]} / simd {m['simd'] and m['simd'][:12]}")

out = os.path.join(RES, "AB_TABLE.md")
with open(out, "w") as f:
    f.write("\n".join(lines) + "\n")

summary = {
    "images": len(manifest),
    "px_identical": len(manifest) - len(px_mismatch),
    "px_mismatch_ids": [m["id"] for m in px_mismatch],
    "cells": len(cells),
    "table": out,
}
print(json.dumps(summary, indent=1))
