#!/bin/bash
# Run JABCode integration tests with native library

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Use absolute path for library directory
NATIVE_LIB_DIR="$(cd "$SCRIPT_DIR/../src/jabcode/build" && pwd)"

if [ ! -f "$NATIVE_LIB_DIR/libjabcode.so" ]; then
    echo "Error: libjabcode.so not found at $NATIVE_LIB_DIR"
    echo "Please build the native library first:"
    echo "  cd ../src/jabcode"
    echo "  make"
    exit 1
fi

echo "Running JABCode integration tests..."
echo "Native library: $NATIVE_LIB_DIR/libjabcode.so"
echo ""

export JAVA_HOME=/home/kynphlee/tools/compilers/java/jdk-23.0.1
export LD_LIBRARY_PATH="$NATIVE_LIB_DIR:$LD_LIBRARY_PATH"

# Run only integration tests
# Override jabcode.lib.path property to point to actual library location
mvn -DskipJextract=true test -Dtest=JABCodeEncoderIntegrationTest \
    -Djabcode.lib.path="$NATIVE_LIB_DIR"

echo ""
echo "Integration tests complete!"
