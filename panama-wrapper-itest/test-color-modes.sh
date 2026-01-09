#!/bin/bash
# Test different color modes with native CLI to identify which work

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE_BIN="$SCRIPT_DIR/../../bin"
TEST_DIR="$SCRIPT_DIR/color-mode-tests"

mkdir -p "$TEST_DIR"
cd "$TEST_DIR"

echo "================================================="
echo "Testing JABCode Native CLI with Different Colors"
echo "================================================="
echo ""

TEST_DATA="Hello JABCode Testing"

# Test each color mode
for colors in 4 8 16 32 64 128 256; do
    echo "─────────────────────────────────────────────────"
    echo "Testing $colors color mode"
    echo "─────────────────────────────────────────────────"
    
    # Try different ECC levels
    for ecc in 5 7 9; do
        OUTPUT="test_${colors}colors_ecc${ecc}.png"
        
        echo -n "  Encoding with $colors colors, ECC level $ecc... "
        
        # Run encoder (it doesn't output success message, just creates file)
        $NATIVE_BIN/jabcodeWriter \
            --input "$TEST_DATA" \
            --output "$OUTPUT" \
            --color-number $colors \
            --ecc-level $ecc \
            --module-size 12 \
            2>/dev/null
        
        if [ $? -eq 0 ]; then
            echo "✓ Encoded"
            
            # Check file was created
            if [ -f "$OUTPUT" ]; then
                SIZE=$(stat -c%s "$OUTPUT" 2>/dev/null || stat -f%z "$OUTPUT")
                echo "    File size: $SIZE bytes"
                
                # Try to decode
                echo -n "    Decoding... "
                if DECODED=$($NATIVE_BIN/jabcodeReader "$OUTPUT" 2>&1); then
                    # Check if decoded matches input
                    if echo "$DECODED" | grep -q "$TEST_DATA"; then
                        echo "✓ SUCCESS - Round-trip works!"
                    else
                        echo "✗ FAILED - Decoded data doesn't match"
                        echo "      Expected: $TEST_DATA"
                        echo "      Got: $DECODED"
                    fi
                else
                    echo "✗ FAILED - Decoding error"
                    echo "$DECODED" | head -3
                fi
            else
                echo "    ✗ File not created"
            fi
        else
            echo "✗ Encoding failed"
        fi
        echo ""
    done
done

echo "================================================="
echo "Test complete. Results saved in: $TEST_DIR"
echo "================================================="
