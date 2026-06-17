#!/usr/bin/env bash
#
# R0 decode-rate rig — one-shot driver.
#
# Builds libjabcode + the r0_decode probe, then runs the Python runner over a
# manifest and writes per-image JSONL + an aggregate JSON.
#
# Usage:
#   ./run.sh                                  # default manifest + bucket
#   ./run.sh <manifest.jsonl>                 # custom manifest
#   ./run.sh <manifest.jsonl> <bucket_keys>   # e.g. "medium,nc" or "conditions"
#
# Outputs (under results/, names derived from the manifest basename):
#   results/<name>.per_image.jsonl
#   results/<name>.aggregate.json
#
set -euo pipefail

RIG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$RIG_DIR"

MANIFEST="${1:-manifest.jsonl}"
BUCKET="${2:-conditions,nc}"

NAME="$(basename "${MANIFEST%.jsonl}")"
OUT="results/${NAME}.per_image.jsonl"
AGG="results/${NAME}.aggregate.json"

echo ">> building libjabcode + r0_decode probe"
make all >/dev/null

echo ">> running rig over: $MANIFEST  (bucket: $BUCKET)"
mkdir -p results
python3 run_rig.py \
    --manifest "$MANIFEST" \
    --probe ./r0_decode \
    --out "$OUT" \
    --agg "$AGG" \
    --bucket "$BUCKET"
