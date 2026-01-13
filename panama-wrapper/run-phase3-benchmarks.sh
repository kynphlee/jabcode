#!/bin/bash
# Phase 3: Advanced Metrics - Decoding, Round-Trip, Memory Profiling
# Runs comprehensive performance analysis beyond basic encoding

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Create results directory
mkdir -p results

echo "============================================"
echo "Phase 3: Advanced Metrics"
echo "============================================"
echo ""

# Benchmark 1: Decoding Performance
echo "[1/3] Running DecodingBenchmark - Decode performance across color modes"
echo "      Color modes: 4, 8, 16, 32, 64, 128"
echo "      Message sizes: 100B, 1KB, 10KB"
echo "      Estimated time: ~30 minutes"
echo ""

./run-benchmark.sh "DecodingBenchmark.decodeByColorMode" \
  "-rf json -rff results/decoding-performance.json" \
  > results/decoding-performance.log 2>&1

echo "✓ DecodingBenchmark complete"
echo ""

# Benchmark 2: Round-Trip Performance
echo "[2/3] Running RoundTripBenchmark - Complete encode→decode cycles"
echo "      Color modes: 8, 32, 64, 128"
echo "      Message size: 1KB"
echo "      Estimated time: ~15 minutes"
echo ""

./run-benchmark.sh "RoundTripBenchmark.encodeDecodeVerify" \
  "-rf json -rff results/roundtrip-performance.json" \
  > results/roundtrip-performance.log 2>&1

echo "✓ RoundTripBenchmark complete"
echo ""

# Benchmark 3: Memory Profiling
echo "[3/3] Running MemoryBenchmark - Heap allocation profiling"
echo "      Color modes: 8, 64, 128"
echo "      Message sizes: 1KB, 10KB, 100KB"
echo "      Estimated time: ~10 minutes"
echo ""

./run-benchmark.sh "MemoryBenchmark.measureMemoryUsage" \
  "-rf json -rff results/memory-profiling.json" \
  > results/memory-profiling.log 2>&1

echo "✓ MemoryBenchmark complete"
echo ""

echo "============================================"
echo "Phase 3 Complete!"
echo "============================================"
echo ""
echo "Results saved to:"
echo "  - results/decoding-performance.json"
echo "  - results/roundtrip-performance.json"
echo "  - results/memory-profiling.json"
echo ""
echo "Next steps:"
echo "  1. Analyze decode vs encode ratios"
echo "  2. Calculate round-trip overhead"
echo "  3. Review memory allocation patterns"
echo "  4. Generate Phase 3 completion report"
echo ""
