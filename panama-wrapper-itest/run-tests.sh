#!/bin/bash
# Run integration tests in dedicated module (no JaCoCo interference)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE_LIB_DIR="$(cd "$SCRIPT_DIR/../src/jabcode/build" && pwd)"

if [ ! -f "$NATIVE_LIB_DIR/libjabcode.so" ]; then
    echo "Error: libjabcode.so not found at $NATIVE_LIB_DIR"
    echo "Please build the native library first:"
    echo "  cd ../src/jabcode"
    echo "  make"
    exit 1
fi

echo "JABCode Integration Tests (Dedicated Module)"
echo "============================================="
echo "Native library: $NATIVE_LIB_DIR/libjabcode.so"
echo ""

export JAVA_HOME=/home/kynphlee/tools/compilers/java/jdk-23.0.1
export PATH="$JAVA_HOME/bin:$PATH"

# First, install the main module (if not already)
echo "Installing main panama-wrapper module..."
cd "$SCRIPT_DIR/../panama-wrapper"
mvn -q -DskipTests -DskipJextract=true install

echo ""
echo "Running integration tests..."
cd "$SCRIPT_DIR"

# Run tests with proper library path
mvn clean test -Djabcode.lib.path="$NATIVE_LIB_DIR"

echo ""
echo "Integration tests complete!"
