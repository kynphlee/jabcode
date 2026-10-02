#!/bin/bash

# JABCode Panama Binding Generator
# Generates Java bindings from JABCode C headers using jextract

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}JABCode Panama Binding Generator${NC}"
echo "=================================="

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JABCODE_HEADER="$PROJECT_ROOT/src/jabcode/include/jabcode.h"
JABCODE_INCLUDE_DIR="$PROJECT_ROOT/src/jabcode/include"
OUTPUT_DIR="$SCRIPT_DIR/target/generated-sources/jextract"
PACKAGE="com.jabcode.panama.bindings"

# Check if jextract is available
if ! command -v jextract &> /dev/null; then
    echo -e "${RED}ERROR: jextract not found in PATH${NC}"
    echo ""
    echo "Please install jextract:"
    echo "  1. Download from: https://jdk.java.net/jextract/"
    echo "  2. Add jextract to your PATH"
    exit 1
fi

# Check if JABCode header exists
if [ ! -f "$JABCODE_HEADER" ]; then
    echo -e "${RED}ERROR: JABCode header not found: $JABCODE_HEADER${NC}"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
if [ "$JAVA_VERSION" -lt 23 ]; then
    echo -e "${YELLOW}WARNING: Java $JAVA_VERSION detected. Panama requires Java 23+${NC}"
    echo "Current JAVA_HOME: $JAVA_HOME"
fi

echo ""
echo "Configuration:"
echo "  JABCode Header: $JABCODE_HEADER"
echo "  Include Dir:    $JABCODE_INCLUDE_DIR"
echo "  Output Dir:     $OUTPUT_DIR"
echo "  Package:        $PACKAGE"
echo "  Java Version:   $JAVA_VERSION"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Run jextract
echo "Generating Panama bindings..."
echo ""

# No --include-function / --include-struct filters: generate the whole (small,
# stable) header, which emits all 8 struct classes plus jabcode_h. The wrapper
# imports only jabcode_h; it reads the structs at hand-computed offsets. A
# filter list has to be kept in step with the wrapper by hand, and jextract 25
# does not check it: a name that matches nothing is ignored without a warning
# (exit 0, output unchanged), and a function missing from the list only shows
# up as a compile error. The 8a81927 list, for one, lacked saveImageToMemory and
# readImageFromMemory, which the wrapper now calls. The old --include-struct
# entries matched nothing either: jabcode.h declares those structs as anonymous
# typedefs, which jextract matches by --include-typedef.
# This comment used to say the filters dropped struct classes the wrapper needs
# and that naming decodeJABCodeWithObservations (not in this header) made
# jextract fail. Neither reproduces; see RESULTS.md, "Update 2026-10-02".
jextract \
    --output "$OUTPUT_DIR" \
    --target-package "$PACKAGE" \
    --library jabcode \
    -I "$JABCODE_INCLUDE_DIR" \
    "$JABCODE_HEADER"

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ Bindings generated successfully${NC}"
    echo ""
    echo "Generated files:"
    find "$OUTPUT_DIR" -name "*.java" | head -10
    FILE_COUNT=$(find "$OUTPUT_DIR" -name "*.java" | wc -l)
    echo "... ($FILE_COUNT total files)"
    echo ""
    echo "Next steps:"
    echo "  1. Review generated bindings in: $OUTPUT_DIR"
    echo "  2. Build the project: mvn clean package"
    echo "  3. Run tests: mvn test"
else
    echo -e "${RED}✗ Binding generation failed${NC}"
    exit 1
fi
