#!/usr/bin/env python3
"""Bridge: synthetic corpus manifest  ->  R0 decode-rate rig manifest.

The synthetic generator (`degrade.py`) emits a *flat* per-image manifest:

    {id, file, source_symbol, nc, degradation_type, param}

The rig runner (`../rig/run_rig.py`) consumes a *different* schema:

    {id, file, payload_known, payload_sha256, conditions, ...}

This script converts the former into the latter so the synthetic degradation
corpus can be scored by the rig with full known-payload (SHA-256) verification.

Where the hash comes from -- the safe source
--------------------------------------------
We do NOT recompute a payload hash here (the synthetic images are degraded, so
re-decoding them to get a hash would be circular and could launder a wrong
answer). Instead we reuse the **verified-correct** per-`nc` `payload_sha256`
already committed in the rig's own manifest (`../rig/manifest.jsonl`). That is
sound because every synthetic image is a deterministic degradation of one of the
clean source symbols, and those source symbols are byte-identical to the rig's
`clean-fullspectrum/nc{N}` images that produced those hashes. Same source
symbol => same payload => same SHA-256. The degradation only damages pixels, not
the ground-truth payload the decoder is expected to recover.

So: `payload_sha256` is looked up by `nc` from the rig manifest, `conditions` is
`"<degradation_type>@<param>"` (one bucket per ladder cell, so the aggregate's
`by_condition` becomes a decode-rate-vs-degradation curve), and `payload_known`
is `true`.

Path resolution (no hard-coded absolute paths)
----------------------------------------------
Every default path is derived from this script's own location, so the bridge
runs identically from a clean checkout regardless of where the repo lives:

    <here>/out/manifest.jsonl              synthetic source manifest
    <here>/../rig/manifest.jsonl           rig hash source (nc -> payload_sha256)
    <here>/out/rig_manifest.jsonl          bridge output (default)

The rig resolves each record's `file` relative to the *output manifest's*
directory (or treats it as absolute). We emit an **absolute** path to each
synthetic image, which is unambiguous wherever the bridge manifest is later
moved or invoked from. Pass `--relative` to instead emit paths relative to the
output manifest's directory (handy if you want a relocatable manifest+corpus
pair).

Usage:
    to_rig_manifest.py                         # all defaults, writes out/rig_manifest.jsonl
    to_rig_manifest.py --out /tmp/syn.jsonl    # custom output location
    to_rig_manifest.py --relative             # file paths relative to --out's dir
"""
from __future__ import annotations

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_SYN_MANIFEST = os.path.join(HERE, "out", "manifest.jsonl")
DEFAULT_RIG_MANIFEST = os.path.normpath(os.path.join(HERE, "..", "rig", "manifest.jsonl"))
DEFAULT_OUT = os.path.join(HERE, "out", "rig_manifest.jsonl")


def load_nc_hashes(rig_manifest_path: str) -> dict[int, str]:
    """Build {nc: payload_sha256} from the rig manifest's known-payload entries.

    The rig manifest carries one verified hash per Nc (repeated across the
    benchmark and full-spectrum clean entries, which share a payload). We take
    the first known hash seen for each Nc and assert any later one agrees, so a
    drift in the rig manifest is caught rather than silently picking one.
    """
    nc_hash: dict[int, str] = {}
    with open(rig_manifest_path, encoding="utf-8") as f:
        for ln, raw in enumerate(f, 1):
            raw = raw.strip()
            if not raw or raw.startswith("#"):
                continue
            rec = json.loads(raw)
            if not rec.get("payload_known"):
                continue
            nc = rec.get("nc")
            sha = (rec.get("payload_sha256") or "").lower()
            if nc is None or not sha:
                continue
            if nc in nc_hash and nc_hash[nc] != sha:
                raise ValueError(
                    f"{rig_manifest_path}:{ln}: conflicting payload_sha256 for "
                    f"nc={nc}: {nc_hash[nc]} vs {sha}"
                )
            nc_hash.setdefault(nc, sha)
    if not nc_hash:
        raise ValueError(
            f"no known-payload (nc -> payload_sha256) entries in {rig_manifest_path}"
        )
    return nc_hash


def build(syn_manifest: str, rig_manifest: str, out_path: str,
          relative: bool) -> int:
    nc_hash = load_nc_hashes(rig_manifest)

    syn_dir = os.path.dirname(os.path.abspath(syn_manifest))
    out_dir = os.path.dirname(os.path.abspath(out_path))

    count = 0
    missing_nc: set[int] = set()
    with open(syn_manifest, encoding="utf-8") as src, \
            open(out_path, "w", encoding="utf-8") as dst:
        for ln, raw in enumerate(src, 1):
            raw = raw.strip()
            if not raw or raw.startswith("#"):
                continue
            rec = json.loads(raw)
            nc = rec["nc"]
            sha = nc_hash.get(nc)
            if sha is None:
                missing_nc.add(nc)
                continue

            # Absolute path to the synthetic image (file is relative to the
            # synthetic manifest's own directory).
            img_abs = os.path.normpath(os.path.join(syn_dir, rec["file"]))
            file_field = (os.path.relpath(img_abs, out_dir)
                          if relative else img_abs)

            param = rec.get("param")
            dtype = rec.get("degradation_type", "unknown")
            out_rec = {
                "id": rec["id"],
                "file": file_field,
                "nc": nc,
                "payload_known": True,
                "payload_sha256": sha,
                "medium": "synthetic",
                "conditions": f"{dtype}@{param}",
            }
            dst.write(json.dumps(out_rec) + "\n")
            count += 1

    if missing_nc:
        print(f"WARNING: no rig hash for nc in {sorted(missing_nc)}; "
              f"those rows were skipped", file=sys.stderr)

    print(f"wrote {count} rig-manifest rows -> {out_path}", file=sys.stderr)
    print(f"  hashes: {len(nc_hash)} nc values from {rig_manifest}",
          file=sys.stderr)
    print(f"  file paths: {'relative to out dir' if relative else 'absolute'}",
          file=sys.stderr)
    return 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(
        description="Convert the synthetic corpus manifest into an R0 rig manifest.")
    ap.add_argument("--syn-manifest", default=DEFAULT_SYN_MANIFEST,
                    help="synthetic manifest.jsonl (default: ./out/manifest.jsonl)")
    ap.add_argument("--rig-manifest", default=DEFAULT_RIG_MANIFEST,
                    help="rig manifest.jsonl that carries the verified "
                         "nc -> payload_sha256 map (default: ../rig/manifest.jsonl)")
    ap.add_argument("--out", default=DEFAULT_OUT,
                    help="output rig-manifest path (default: ./out/rig_manifest.jsonl)")
    ap.add_argument("--relative", action="store_true",
                    help="emit image paths relative to the output manifest's "
                         "directory instead of absolute")
    args = ap.parse_args(argv)

    for label, path in (("synthetic manifest", args.syn_manifest),
                        ("rig manifest", args.rig_manifest)):
        if not os.path.isfile(path):
            ap.error(f"{label} not found: {path}")

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    return build(args.syn_manifest, args.rig_manifest, args.out, args.relative)


if __name__ == "__main__":
    raise SystemExit(main())
