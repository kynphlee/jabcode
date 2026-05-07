#!/bin/bash
# Generate fresh test JABCodes for all color modes

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$SCRIPT_DIR/bin"
OUTPUT_DIR="$SCRIPT_DIR/output/test_codes"

mkdir -p "$OUTPUT_DIR"

echo "==================================="
echo "Generating Test JABCodes"
echo "==================================="
echo ""

# Ensure writer binary exists
if [ ! -f "$BIN_DIR/jabcodeWriter" ]; then
    echo "ERROR: Writer binary not found. Building..."
    cd "$SCRIPT_DIR"
    ./scripts/build.sh
fi

# Test message
MESSAGE="JABCode Test Color Mode: "

# Generate codes for each color mode
for mode in 4 8 16 32 64 128; do
    echo "Generating ${mode}-color JABCode..."
    
    output_file="$OUTPUT_DIR/jabcode_${mode}color.png"
    
    # Create JABCode with specific color mode
    "$BIN_DIR/jabcodeWriter" \
        --input <(echo -n "${MESSAGE}${mode}") \
        --output "$output_file" \
        --color-number $mode \
        --symbol-number 1 \
        --ecc-level 3 \
        --module-size 12
    
    if [ -f "$output_file" ]; then
        size=$(identify -format '%wx%h' "$output_file" 2>/dev/null || echo "unknown")
        echo "  ✅ Created: $output_file ($size)"
    else
        echo "  ❌ Failed to create $output_file"
    fi
    echo ""
done

echo "==================================="
echo "Test codes generated in:"
echo "$OUTPUT_DIR"
echo "==================================="
