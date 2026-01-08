#!/bin/bash
# Check source file completeness and optionally fix

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JABCODE_DIR="$SCRIPT_DIR/../../jabcode"
SRC_DIR="$JABCODE_DIR/src/jabcode"

cd "$JABCODE_DIR"

echo "========================================="
echo "JABCode Source File Completeness Check"
echo "========================================="
echo ""

# Check if we have upstream configured
if ! git remote | grep -q "^upstream$"; then
    echo "Error: No 'upstream' remote configured"
    echo "Please run: git remote add upstream https://github.com/jabcode/jabcode.git"
    exit 1
fi

# Fetch latest upstream (skip if slow)
# echo "Fetching latest from upstream..."
# git fetch upstream 2>&1 | grep -v "^From"
echo "Using cached upstream data..."
echo ""

echo "Checking source file sizes..."
echo "----------------------------------------"
printf "%-20s %10s %10s %10s\n" "File" "Local" "Upstream" "Missing"
echo "----------------------------------------"

INCOMPLETE_FILES=()
TOTAL_MISSING=0

cd "$SRC_DIR"
for file in *.c; do
    if [ -f "$file" ]; then
        local_lines=$(wc -l < "$file")
        upstream_content=$(git show upstream/master:src/jabcode/"$file" 2>/dev/null)
        
        if [ $? -eq 0 ]; then
            upstream_lines=$(echo "$upstream_content" | wc -l)
            diff=$((upstream_lines - local_lines))
            
            printf "%-20s %10d %10d" "$file" "$local_lines" "$upstream_lines"
            
            if [ $diff -gt 50 ]; then
                printf " %10d ⚠️ INCOMPLETE\n" "$diff"
                INCOMPLETE_FILES+=("$file")
                TOTAL_MISSING=$((TOTAL_MISSING + diff))
            elif [ $diff -gt 0 ]; then
                printf " %10d\n" "$diff"
            elif [ $diff -lt 0 ]; then
                printf " %10d (local has more)\n" "$diff"
            else
                printf " %10s ✓\n" "0"
            fi
        else
            printf "%-20s %10d %10s %10s (not in upstream)\n" "$file" "$local_lines" "N/A" "N/A"
        fi
    fi
done

echo "----------------------------------------"
echo ""

if [ ${#INCOMPLETE_FILES[@]} -gt 0 ]; then
    echo "❌ Found ${#INCOMPLETE_FILES[@]} incomplete file(s):"
    for file in "${INCOMPLETE_FILES[@]}"; do
        echo "   - $file"
    done
    echo ""
    echo "Total missing lines: $TOTAL_MISSING"
    echo ""
    
    read -p "Do you want to update these files from upstream? (y/N) " -n 1 -r
    echo ""
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo ""
        echo "Updating files from upstream..."
        echo "----------------------------------------"
        
        for file in "${INCOMPLETE_FILES[@]}"; do
            echo "Updating $file..."
            
            # Backup original
            cp "$file" "$file.backup"
            
            # Get upstream version
            git show upstream/master:src/jabcode/"$file" > "$file"
            
            echo "  ✓ Updated (backup saved as $file.backup)"
        done
        
        echo "----------------------------------------"
        echo ""
        echo "✅ Files updated successfully!"
        echo ""
        echo "Next steps:"
        echo "1. Rebuild the library: cd $SRC_DIR && make clean && make"
        echo "2. Verify symbols: nm -D build/libjabcode.so | grep generateJABCode"
        echo "3. Run tests: cd $SCRIPT_DIR && ./run-tests.sh"
        echo ""
        
        read -p "Rebuild library now? (y/N) " -n 1 -r
        echo ""
        
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            echo ""
            echo "Rebuilding library..."
            make clean
            make
            
            echo ""
            echo "Checking for generateJABCode symbol..."
            if nm -D build/libjabcode.so | grep -q "generateJABCode"; then
                echo "✅ generateJABCode found in library!"
                nm -D build/libjabcode.so | grep generateJABCode
            else
                echo "❌ generateJABCode still not found"
                exit 1
            fi
        fi
    else
        echo "Skipping update. Files not modified."
    fi
else
    echo "✅ All source files are up to date!"
fi
