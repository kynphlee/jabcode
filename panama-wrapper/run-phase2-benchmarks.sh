#!/bin/bash
# Phase 2: Core Encoding Benchmarks - Full Suite
# Runs all color modes, ECC levels, and cascaded configurations

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Create results directory
mkdir -p results

echo "============================================"
echo "Phase 2: Core Encoding Benchmarks"
echo "============================================"
echo ""

# Benchmark 1: Color Mode Performance (ALL modes + ALL sizes)
echo "[1/3] Running EncodingBenchmark - Color Modes (4, 8, 16, 32, 64, 128)"
echo "      Message sizes: 100B, 1KB, 10KB, 100KB"
echo "      Estimated time: ~45 minutes"
echo ""

./run-benchmark.sh "EncodingBenchmark.encodeByColorMode" \
  "-rf json -rff results/encoding-by-mode.json" \
  > results/encoding-by-mode.log 2>&1

echo "✓ EncodingBenchmark complete"
echo ""

# Benchmark 2: ECC Level Impact
echo "[2/3] Running ECCLevelBenchmark - ECC Impact (3, 5, 7, 9)"
echo "      Color modes: 8, 32, 128"
echo "      Estimated time: ~20 minutes"
echo ""

./run-benchmark.sh "ECCLevelBenchmark.encodeByECCLevel" \
  "-rf json -rff results/ecc-impact.json" \
  > results/ecc-impact.log 2>&1

echo "✓ ECCLevelBenchmark complete"
echo ""

# Benchmark 3: Cascaded Encoding
echo "[3/3] Running CascadedEncodingBenchmark - Multi-symbol (1, 2, 3, 5)"
echo "      Color modes: 32, 64"
echo "      Estimated time: ~15 minutes"
echo ""

./run-benchmark.sh "CascadedEncodingBenchmark.encodeCascaded" \
  "-rf json -rff results/cascaded-encoding.json" \
  > results/cascaded-encoding.log 2>&1

echo "✓ CascadedEncodingBenchmark complete"
echo ""

echo "============================================"
echo "Phase 2 Complete!"
echo "============================================"
echo ""
echo "Results saved to:"
echo "  - results/encoding-by-mode.json"
echo "  - results/ecc-impact.json"
echo "  - results/cascaded-encoding.json"
echo ""
echo "Next steps:"
echo "  1. Review results with: jq . results/*.json"
echo "  2. Generate baseline documentation"
echo "  3. Run /test-coverage-update"
echo ""
