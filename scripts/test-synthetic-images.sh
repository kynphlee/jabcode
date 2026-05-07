#!/bin/bash
# Generate and test synthetic perfect JAB code images

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# Set library path for libjabcode.so
export LD_LIBRARY_PATH="$PROJECT_ROOT/src/jabcode/build:$LD_LIBRARY_PATH"

OUTPUT_DIR="output/synthetic-tests"
mkdir -p "$OUTPUT_DIR"

echo "🔬 Generating synthetic test images..."
echo ""

# Test message (same for all modes for comparison)
MESSAGE="The quick brown fox jumps over the lazy dog 1234567890"

# Generate for each color mode
for COLORS in 4 8 16 32 64 128; do
    OUTPUT_FILE="$OUTPUT_DIR/test_${COLORS}color.png"
    
    echo "📝 Generating ${COLORS}-color JAB code..."
    ./src/jabcodeWriter/bin/jabcodeWriter \
        --input "$MESSAGE" \
        --output "$OUTPUT_FILE" \
        --color-number $COLORS \
        --ecc-level 5
    
    if [ -f "$OUTPUT_FILE" ]; then
        echo "   ✅ Created: $OUTPUT_FILE"
        
        # Get file size
        SIZE=$(ls -lh "$OUTPUT_FILE" | awk '{print $5}')
        echo "   📊 Size: $SIZE"
    else
        echo "   ❌ FAILED to create $OUTPUT_FILE"
    fi
    echo ""
done

echo ""
echo "✅ Synthetic images generated in: $OUTPUT_DIR"
echo ""
echo "📱 Next steps:"
echo "1. Push to Android device:"
echo "   adb push $OUTPUT_DIR /sdcard/Download/jabcode-synthetic-tests/"
echo ""
echo "2. Test in diagnostic app to verify decoder works with perfect images"
