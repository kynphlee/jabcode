#!/bin/bash
# Profile decode performance with async-profiler
# Generates flame graphs showing native code hotspots

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

PROFILER="$SCRIPT_DIR/tools/async-profiler-3.0-linux-x64/lib/libasyncProfiler.so"
OUTPUT_DIR="results/profiling"
mkdir -p "$OUTPUT_DIR"

if [ ! -f "$PROFILER" ]; then
    echo "Error: async-profiler not found at $PROFILER"
    exit 1
fi

echo "============================================"
echo "Profiling Decode Performance"
echo "============================================"
echo ""
echo "Profiler: $PROFILER"
echo "Output: $OUTPUT_DIR"
echo ""

# Compile benchmarks
mvn test-compile -DskipTests -q

# Set library path
export LD_LIBRARY_PATH="../lib:$LD_LIBRARY_PATH"

# Build classpath
CLASSPATH=$(mvn dependency:build-classpath -DincludeScope=test -q -Dmdep.outputFile=/dev/stdout)
CLASSPATH="target/test-classes:target/classes:$CLASSPATH"

# Profile configuration
BENCHMARK="DecodingBenchmark.decodeByColorMode"
PARAMS="-p colorMode=64 -p messageSize=1000"
WARMUP="-wi 2 -i 5 -f 1"

# Async-profiler settings
PROFILER_OPTS="event=cpu,interval=1ms,allkernel,alluser,flamegraph"

echo "Running profiled benchmark..."
echo "Benchmark: $BENCHMARK"
echo "Parameters: 64-color, 1KB message"
echo "Profiler: CPU sampling @ 1ms, includes native code"
echo ""

# Run with async-profiler
java -cp "$CLASSPATH" \
     --enable-native-access=ALL-UNNAMED \
     -Djava.library.path=../lib \
     -agentpath:"$PROFILER=start,$PROFILER_OPTS,file=$OUTPUT_DIR/decode-baseline.html" \
     org.openjdk.jmh.Main "$BENCHMARK" \
     $PARAMS $WARMUP \
     -rf json -rff "$OUTPUT_DIR/decode-baseline.json" \
     > "$OUTPUT_DIR/decode-baseline.log" 2>&1

echo ""
echo "✓ Profiling complete!"
echo ""
echo "Results:"
echo "  - Flame graph: $OUTPUT_DIR/decode-baseline.html"
echo "  - Benchmark data: $OUTPUT_DIR/decode-baseline.json"
echo "  - Full log: $OUTPUT_DIR/decode-baseline.log"
echo ""
echo "Open flame graph in browser to see native code hotspots:"
echo "  firefox $OUTPUT_DIR/decode-baseline.html"
echo ""
