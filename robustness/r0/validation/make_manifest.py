#!/usr/bin/env python3
"""Build an R0-rig manifest (JSONL) for a directory of degraded JABCode images.

Pairs each image with the SHA-256 of its source payload so the rig's
known-payload gate becomes a true correctness check. The per-Nc payload hashes
are lifted verbatim from the rig's own bundled manifest
(robustness/r0/rig/manifest.jsonl) -- every clean symbol there carries the
sha256 of `HELLO-Nc-<nc>`, the payload the full-spectrum sources encode.

Usage:
    make_manifest.py --images <dir> --glob '*perspective*' \
        --condition-from-suffix --out manifest.jsonl

`--condition-from-suffix` derives the `conditions` bucket from the filename tail
after the last "__" (e.g. "perspective_20deg"), which is exactly one ladder cell,
so the rig's by_condition aggregate becomes a decode-rate-vs-tilt curve.
"""
from __future__ import annotations

import argparse
import json
import os
import re

# Per-Nc payload SHA-256, copied from robustness/r0/rig/manifest.jsonl (the
# clean-benchmark / clean-fullspectrum lines). Source payload is HELLO-Nc-<nc>.
PAYLOAD_SHA256 = {
    0: "11c1ceda934135e3e3ab0cdfb716dea4ad0acd7e4bedb57f980dff60912ef5af",
    1: "2d95744a96af55e37d41560e4b4a7f23740c6a851f52bf5e17019839f4c97bcb",
    2: "7e87bef3537a4bb4a625c35f0de113a269cdf2957588463e708b52e13f84a4b0",
    3: "929c1834b771758e6d21f5418e17d8aa6d5b15ba5b37e338f90faaa7888d6665",
    4: "e99f5aee549013e01bc1295bc4356bc34831b9c7de85cb5804fc909a87dca6a8",
    5: "9ff79adeeec81d82f757e19fb0de164a6e75fe9d9f7e57286289e4a1c9b208c9",
    6: "99ea912dcbd522eff2f53382cbf4f2d26fe5029ddf5dcafe163c184e6cb4f7e4",
    7: "e6816b035a11e9e94259407d9870ff9e4e217e1c0bd0199db015a11e4f3fba68",
}

NC_RE = re.compile(r"nc(\d+)[-_]")


def parse_nc(name: str) -> int:
    m = NC_RE.search(name)
    if not m:
        raise ValueError(f"cannot parse Nc from {name!r}")
    return int(m.group(1))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images", required=True, help="dir of degraded images")
    ap.add_argument("--glob", default="*", help="filename substring filter")
    ap.add_argument("--out", required=True, help="manifest JSONL output path")
    ap.add_argument("--medium", default="synthetic")
    ap.add_argument("--condition-from-suffix", action="store_true",
                    help="bucket = filename tail after the last '__'")
    ap.add_argument("--condition", default=None,
                    help="fixed conditions label (overrides --condition-from-suffix)")
    args = ap.parse_args()

    sub = args.glob.strip("*")
    names = sorted(n for n in os.listdir(args.images)
                   if sub in n and n.lower().endswith((".png", ".jpg")))
    if not names:
        ap.error(f"no images matching {args.glob!r} in {args.images}")

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w") as f:
        for name in names:
            nc = parse_nc(name)
            base = os.path.splitext(name)[0]
            if args.condition:
                cond = args.condition
            elif args.condition_from_suffix and "__" in base:
                cond = base.rsplit("__", 1)[1]
            else:
                cond = "all"
            rec = {
                "id": base,
                "file": os.path.abspath(os.path.join(args.images, name)),
                "nc": nc,
                "payload_known": True,
                "payload_sha256": PAYLOAD_SHA256[nc],
                "medium": args.medium,
                "conditions": cond,
            }
            f.write(json.dumps(rec) + "\n")
    print(f"wrote {len(names)} records -> {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
