#!/bin/bash
# Helper script to run JMH benchmarks with proper classpath

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Ensure test classes are compiled
echo "Compiling benchmark classes..."
mvn test-compile -DskipTests -q

# Set library path
export LD_LIBRARY_PATH="../lib:$LD_LIBRARY_PATH"

# Build classpath including test dependencies
CLASSPATH=$(mvn dependency:build-classpath -DincludeScope=test -q -Dmdep.outputFile=/dev/stdout)
CLASSPATH="target/test-classes:target/classes:$CLASSPATH"

# Default benchmark or use provided argument
BENCHMARK="${1:-EncodingBenchmark}"
PARAMS="${2:-}"

echo "Running benchmark: $BENCHMARK"
echo "Library path: $LD_LIBRARY_PATH"

# Run JMH
java -cp "$CLASSPATH" \
  --enable-native-access=ALL-UNNAMED \
  -Djava.library.path=../lib \
  org.openjdk.jmh.Main "$BENCHMARK" $PARAMS

echo "Benchmark complete!"
