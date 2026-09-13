#!/usr/bin/env python3
"""Build the bench manifest for the WASM-vs-native decode-rate A/B.

Selects the spike's colour classes from the existing R0 corpora:

  * clean benchmark symbols  nc1 (4c), nc2 (8c), nc3 (16c)   [rig manifest]
  * every synthetic degradation of those symbols              [synthetic manifest]
  * the ws5 real Camera2 frames (nc1, payload unknown)        [rig manifest]

Known-payload hashes are looked up per-nc from the rig manifest's clean
entries — the same non-circular sourcing to_rig_manifest.py uses: the hash
comes from the clean ground truth, never from decoding a degraded image.

Output: bench/manifest.json with dev-server URLs (/corpus/...), consumed by
bench.html (browser/WASM side). The native side decodes the identical files
via the R0 rig. Deterministic: same inputs => same JSON.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
R0 = os.path.normpath(os.path.join(HERE, "../../robustness/r0"))
NCS = (1, 2, 3)  # 4-, 8-, 16-colour

def read_jsonl(path):
    with open(path) as f:
        return [json.loads(line) for line in f if line.strip()]

rig = read_jsonl(os.path.join(R0, "rig/manifest.jsonl"))
synth = read_jsonl(os.path.join(R0, "synthetic/out/manifest.jsonl"))

sha_by_nc = {r["nc"]: r["payload_sha256"]
             for r in rig if r.get("conditions") == "clean-benchmark"}

entries = []

for r in rig:
    if r.get("conditions") == "clean-benchmark" and r["nc"] in NCS:
        entries.append({
            "id": r["id"], "nc": r["nc"], "color_count": 1 << (r["nc"] + 1),
            "conditions": "clean", "payload_sha256": r["payload_sha256"],
            "url": "/corpus/rig/" + r["file"],
            "native_file": "rig/" + r["file"],
        })

for s in synth:
    if s["nc"] in NCS:
        entries.append({
            "id": s["id"], "nc": s["nc"], "color_count": 1 << (s["nc"] + 1),
            "conditions": f"{s['degradation_type']}@{s['param']}",
            "payload_sha256": sha_by_nc[s["nc"]],
            "url": "/corpus/synthetic/" + os.path.basename(s["file"]),
            "native_file": "synthetic/out/" + os.path.basename(s["file"]),
        })

for r in rig:
    if r.get("conditions") == "ws5-realframe":
        entries.append({
            "id": r["id"], "nc": r["nc"], "color_count": 1 << (r["nc"] + 1),
            "conditions": "ws5-realframe", "payload_sha256": None,
            "url": "/corpus/rig/" + r["file"],
            "native_file": "rig/" + r["file"],
        })

entries.sort(key=lambda e: (e["color_count"], e["conditions"], e["id"]))

out = {
    "note": "WASM-vs-native A/B corpus — spike HANDOFF-WASM-WEB-READER-2026-09-13",
    "success_rule": "payload_known ? sha256(decoded)==payload_sha256 : decode_ok",
    "entries": entries,
}
dst = os.path.join(HERE, "manifest.json")
with open(dst, "w") as f:
    json.dump(out, f, indent=1)
print(f"{dst}: {len(entries)} entries "
      f"({sum(1 for e in entries if e['payload_sha256'])} known-payload)")

# Native twin: the SAME selection in the R0 rig runner's schema (file paths
# relative to this manifest), so run_rig.py scores byte-identical images.
native = os.path.join(HERE, "native_manifest.jsonl")
with open(native, "w") as f:
    for e in entries:
        f.write(json.dumps({
            "id": e["id"],
            "file": "../../robustness/r0/" + e["native_file"],
            "nc": e["nc"],
            "color_count": e["color_count"],
            "payload_known": e["payload_sha256"] is not None,
            "payload_sha256": e["payload_sha256"],
            "conditions": e["conditions"],
        }) + "\n")
print(f"{native}: rig-schema twin written")
