#!/bin/bash
# Test synthetic JABCode images to isolate decoder issues
# This tests the C decoder with perfect (non-camera) images

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/output/images"
TEST_BIN="$SCRIPT_DIR/bin/jabcodeReader"

echo "==================================="
echo "JABCode Synthetic Image Decode Test"
echo "==================================="
echo ""
echo "Testing decoder with perfect synthetic images"
echo "to isolate C code issues from camera limitations"
echo ""

# Check if reader binary exists
if [ ! -f "$TEST_BIN" ]; then
    echo "ERROR: Reader binary not found at $TEST_BIN"
    echo "Building reader..."
    cd "$SCRIPT_DIR"
    ./scripts/build.sh
fi

# Test function
test_decode() {
    local color_mode=$1
    local image_file="$OUTPUT_DIR/jabcode_${color_mode}.png"
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "Testing ${color_mode}-color mode"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if [ ! -f "$image_file" ]; then
        echo "❌ Image not found: $image_file"
        echo ""
        return 1
    fi
    
    echo "Image: $image_file"
    echo "Size: $(identify -format '%wx%h' "$image_file" 2>/dev/null || echo 'unknown')"
    echo ""
    echo "Decoding..."
    
    # Run decoder and capture output
    if output=$("$TEST_BIN" "$image_file" 2>&1); then
        echo "✅ DECODE SUCCESS"
        echo "Output:"
        echo "$output"
        return 0
    else
        echo "❌ DECODE FAILED"
        echo "Error output:"
        echo "$output"
        return 1
    fi
    
    echo ""
}

# Track results
declare -A results

# Test each color mode
for mode in 4 8 16 32 64 128; do
    if test_decode "$mode"; then
        results[$mode]="✅"
    else
        results[$mode]="❌"
    fi
    echo ""
done

# Summary
echo "==================================="
echo "SUMMARY"
echo "==================================="
echo ""
printf "%-10s %s\n" "Mode" "Result"
echo "-------------------"
for mode in 4 8 16 32 64 128; do
    printf "%-10s %s\n" "${mode}-color" "${results[$mode]}"
done
echo ""

# Analysis
echo "==================================="
echo "ANALYSIS"
echo "==================================="
echo ""

failed_modes=()
for mode in 16 32 64 128; do
    if [ "${results[$mode]}" = "❌" ]; then
        failed_modes+=($mode)
    fi
done

if [ ${#failed_modes[@]} -eq 0 ]; then
    echo "✅ All high color modes (16-128) decode successfully!"
    echo "   → Issue is likely CAMERA-related (lighting, focus, color accuracy)"
    echo "   → Recommendation: Implement camera color calibration"
else
    echo "❌ High color modes failed: ${failed_modes[*]}"
    echo "   → Issue is in the C DECODER code"
    echo "   → Recommendation: Debug native decoder for high color modes"
    echo ""
    echo "   Likely causes:"
    echo "   - Color palette interpolation bug (>64 colors)"
    echo "   - Color quantization/matching algorithm issues"
    echo "   - Missing support for high color mode features"
fi
echo ""
