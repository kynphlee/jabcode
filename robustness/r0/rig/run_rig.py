#!/usr/bin/env python3
"""
R0 decode-rate rig — Python runner.

Drives the r0_decode C probe over a directory of labelled images (described by
a JSONL manifest) and produces:

  1. per-image JSONL  : {id, file, nc, medium, conditions, decode_ok, fail_stage,
                         decode_ms, status, payload_match}
  2. an aggregate JSON: decode-rate and a fail_stage histogram, overall and
                        bucketed per `condition` key (default: medium x nc).

It never reads, prints, or stores decoded payload plaintext. Known-payload
verification is done by comparing the SHA-256 the probe computes in-process
against an expected SHA-256 carried in the manifest (`payload_sha256`).

Manifest schema (one JSON object per line; see README.md for the full spec):

    {
      "id":            "nc7-benchmark",        # required, unique
      "file":          "benchmark/nc7-256c.png", # required, rel-to-manifest or abs
      "nc":            7,                        # optional, expected colour index 0..7
      "ecc":           null,                     # optional, informational
      "payload_known": true,                     # optional, default false
      "payload_sha256":"<64-hex>",               # required iff payload_known
      "medium":        "print",                  # optional, e.g. print|screen|camera
      "conditions":    "clean"                   # optional free-form label
    }

Usage:
    run_rig.py --manifest M.jsonl --probe ./r0_decode \
               --out results/per_image.jsonl --agg results/aggregate.json \
               [--bucket medium,nc]

Exit code: 0 if the run completed (even with failed decodes); non-zero only on
a harness-level error (missing manifest, missing probe, unreadable image).
"""
import argparse
import json
import os
import subprocess
import sys
from collections import defaultdict

# Fail-stage taxonomy (coarse buckets the field traces collapse to).
DETECT          = "DETECT"
PALETTE_CLASSIFY= "PALETTE_CLASSIFY"
PARTII          = "PARTII"
LDPC            = "LDPC"
DATA            = "DATA"
PAYLOAD_MISMATCH= "PAYLOAD_MISMATCH"  # pipeline ok, but bytes != known payload
NONE            = "NONE"          # success
UNKNOWN         = "UNKNOWN"       # failed but no marker matched (should be rare)

ALL_STAGES = [NONE, DETECT, PALETTE_CLASSIFY, PARTII, LDPC, DATA,
              PAYLOAD_MISMATCH, UNKNOWN]


def classify_fail_stage(decode_ok, status, markers, pipeline_ok=None):
    """Map the probe's (status, markers) to a coarse fail_stage.

    Precedence is deepest-stage-wins among the markers actually present, because
    the decoder walks an Nc fallback ladder and may emit DETECT-stage chatter on
    early rungs before succeeding/failing deeper. We therefore scan for the
    deepest stage reached. On a clean success we short-circuit to NONE.

    `pipeline_ok` distinguishes a decoder-level success (status==3, all stages
    passed) from `decode_ok`, which additionally requires the known-payload hash
    to match. When the pipeline succeeded but the bytes are wrong, the failure is
    PAYLOAD_MISMATCH, not a pipeline stage.
    """
    if decode_ok:
        return NONE

    # Pipeline produced valid data but it didn't match the expected payload.
    if pipeline_ok:
        return PAYLOAD_MISMATCH

    text = "\n".join(markers)

    # --- DATA stage: payload byte/encode-mode decode (after all symbols ok) ---
    if ("Decoding data failed" in text or
            "Not enough bits to decode" in text or
            "Invalid value decoded" in text or
            "Decoding mode is None" in text):
        return DATA

    # --- LDPC stage: data-layer error correction uncorrectable ----------------
    if ("LDPC decoding for data in symbol" in text or
            "DIAG_SYMBOL_DECODE result=ldpc_fail" in text or
            "[PartII_DIAG] FAIL_STAGE=ldpc" in text or
            "LDPC decoding for master metadata part 2 failed" in text):
        return LDPC

    # --- PARTII stage: PartII metadata (side version / ec params / matrix) -----
    if ("[PartII_DIAG] FAIL_STAGE=side_version" in text or
            "[PartII_DIAG] FAIL_STAGE=ec_params" in text or
            "[PartII_DIAG] FAIL_STAGE=malloc" in text or
            "FAIL_STAGE=pair_bits" in text or
            "Incorrect error correction parameter" in text or
            "matrix size does not match" in text or
            "symbol size can not be recognized" in text or
            "LDPC decoding for master metadata part 1 failed" in text or
            "FAIL_STAGE=ldpc" in text):  # PartI metadata LDPC
        return PARTII

    # --- PALETTE / colour-classification stage --------------------------------
    if ("FAIL_STAGE=module_color" in text or
            "Invalid module color" in text or
            "Invalid color combination" in text or
            "palette" in text.lower() and "failed" in text.lower()):
        return PALETTE_CLASSIFY

    # --- DETECT stage: finder / alignment / sampling / geometry ---------------
    if ("Too few finder pattern found" in text or
            "finder pattern" in text and "not found" in text or
            "Calculating side size failed" in text or
            "Sampling master symbol" in text or
            "Sampling block failed" in text or
            "alignment pattern" in text.lower() or
            "Invalid master symbol matrix" in text or
            "pass 2 ALSO FAILED" in text):
        return DETECT

    # --- fall back on the coarse status code ----------------------------------
    # status 0 = not detectable -> DETECT; status 1 = found but not decodable.
    if status == 0:
        return DETECT
    if status == 1:
        # Found a finder pattern but failed deeper, and no specific marker fired.
        return UNKNOWN
    return UNKNOWN


def run_probe(probe, image_path):
    """Run r0_decode on one image, return the parsed JSON dict (or raise)."""
    proc = subprocess.run(
        [probe, image_path],
        capture_output=True, text=True, timeout=120
    )
    if proc.returncode != 0:
        raise RuntimeError(
            f"probe exit {proc.returncode} on {image_path}: {proc.stderr.strip()}"
        )
    line = proc.stdout.strip().splitlines()[-1] if proc.stdout.strip() else ""
    if not line:
        raise RuntimeError(f"probe produced no output for {image_path}")
    return json.loads(line)


def main():
    ap = argparse.ArgumentParser(description="R0 decode-rate rig runner")
    ap.add_argument("--manifest", required=True, help="JSONL manifest path")
    ap.add_argument("--probe", required=True, help="path to r0_decode binary")
    ap.add_argument("--out", required=True, help="per-image JSONL output path")
    ap.add_argument("--agg", required=True, help="aggregate JSON output path")
    ap.add_argument("--bucket", default="medium,nc",
                    help="comma-separated manifest keys to bucket the aggregate by")
    args = ap.parse_args()

    if not os.path.isfile(args.manifest):
        print(f"manifest not found: {args.manifest}", file=sys.stderr)
        return 2
    if not (os.path.isfile(args.probe) and os.access(args.probe, os.X_OK)):
        print(f"probe not found / not executable: {args.probe}", file=sys.stderr)
        return 2

    manifest_dir = os.path.dirname(os.path.abspath(args.manifest))
    bucket_keys = [k for k in args.bucket.split(",") if k]

    records = []
    with open(args.manifest) as f:
        for ln, raw in enumerate(f, 1):
            raw = raw.strip()
            if not raw or raw.startswith("#"):
                continue
            try:
                records.append(json.loads(raw))
            except json.JSONDecodeError as e:
                print(f"manifest line {ln}: bad JSON: {e}", file=sys.stderr)
                return 2

    per_image = []
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)

    for rec in records:
        rid = rec.get("id", "?")
        rel = rec["file"]
        img = rel if os.path.isabs(rel) else os.path.join(manifest_dir, rel)
        if not os.path.isfile(img):
            print(f"[{rid}] image missing: {img}", file=sys.stderr)
            return 3

        res = run_probe(args.probe, img)

        pipeline_ok = bool(res["decode_ok"])   # decoder-level success (pre hash gate)
        decode_ok = pipeline_ok
        status = res["status"]
        markers = res.get("markers", [])

        # --- known-payload verification (hash compare, never plaintext) -------
        payload_match = None
        if rec.get("payload_known"):
            expected = (rec.get("payload_sha256") or "").lower()
            got = (res.get("payload_sha256") or "").lower()
            payload_match = bool(pipeline_ok and expected and got == expected)
            # A clean decode whose bytes don't match the known payload is NOT ok.
            decode_ok = payload_match

        fail_stage = classify_fail_stage(decode_ok, status, markers,
                                         pipeline_ok=pipeline_ok)

        out_rec = {
            "id": rid,
            "file": rel,
            "nc": rec.get("nc"),
            "decoded_nc": res.get("nc"),
            "medium": rec.get("medium"),
            "conditions": rec.get("conditions"),
            "decode_ok": 1 if decode_ok else 0,
            "fail_stage": fail_stage,
            "decode_ms": res.get("decode_ms"),
            "status": status,
            "payload_match": payload_match,
        }
        per_image.append(out_rec)

    # --- write per-image JSONL ------------------------------------------------
    with open(args.out, "w") as f:
        for r in per_image:
            f.write(json.dumps(r) + "\n")

    # --- aggregate ------------------------------------------------------------
    def bucket_label(r):
        if not bucket_keys:
            return "all"
        parts = []
        for k in bucket_keys:
            v = r.get(k)
            parts.append(f"{k}={v}")
        return " ".join(parts)

    buckets = defaultdict(lambda: {"n": 0, "ok": 0,
                                   "stage": defaultdict(int),
                                   "decode_ms_sum": 0.0})
    overall = {"n": 0, "ok": 0, "stage": defaultdict(int), "decode_ms_sum": 0.0}

    for r in per_image:
        for tgt in (buckets[bucket_label(r)], overall):
            tgt["n"] += 1
            tgt["ok"] += r["decode_ok"]
            tgt["stage"][r["fail_stage"]] += 1
            if isinstance(r["decode_ms"], (int, float)):
                tgt["decode_ms_sum"] += r["decode_ms"]

    def finalize(b):
        n = b["n"]
        return {
            "n": n,
            "decoded": b["ok"],
            "decode_rate": round(b["ok"] / n, 4) if n else 0.0,
            "mean_decode_ms": round(b["decode_ms_sum"] / n, 3) if n else 0.0,
            "fail_stage_histogram": {s: b["stage"].get(s, 0)
                                     for s in ALL_STAGES if b["stage"].get(s, 0)},
        }

    aggregate = {
        "manifest": os.path.abspath(args.manifest),
        "bucket_keys": bucket_keys,
        "overall": finalize(overall),
        "by_condition": {label: finalize(b)
                         for label, b in sorted(buckets.items())},
    }

    with open(args.agg, "w") as f:
        json.dump(aggregate, f, indent=2)
        f.write("\n")

    # --- console summary ------------------------------------------------------
    ov = aggregate["overall"]
    print(f"R0 rig: {ov['decoded']}/{ov['n']} decoded "
          f"({ov['decode_rate']*100:.1f}%), mean {ov['mean_decode_ms']:.1f} ms")
    for label, b in aggregate["by_condition"].items():
        hist = ", ".join(f"{k}:{v}" for k, v in b["fail_stage_histogram"].items())
        print(f"  [{label}] {b['decoded']}/{b['n']} "
              f"({b['decode_rate']*100:.0f}%)  {hist}")
    print(f"per-image -> {args.out}")
    print(f"aggregate -> {args.agg}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
