#!/bin/bash
# comprehensive-build.sh - A complete build script for JABCode

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "Starting comprehensive JABCode build..."

# Step 1: Clean everything
echo "Cleaning previous build artifacts..."
rm -rf "$REPO_ROOT"/lib/*.so "$REPO_ROOT"/lib/*.a 2>/dev/null || true
rm -rf "$REPO_ROOT"/javacpp-wrapper/lib/*.so "$REPO_ROOT"/javacpp-wrapper/lib/*.a 2>/dev/null || true
rm -rf "$REPO_ROOT"/javacpp-wrapper/libs/*.so "$REPO_ROOT"/javacpp-wrapper/libs/*.a 2>/dev/null || true
rm -rf "$REPO_ROOT"/javacpp-wrapper/target 2>/dev/null || true

# Ensure directories exist
mkdir -p "$REPO_ROOT"/lib
mkdir -p "$REPO_ROOT"/javacpp-wrapper/lib
mkdir -p "$REPO_ROOT"/javacpp-wrapper/libs/linux-x86_64

# Step 2: Build the core library
echo "Building core JABCode library..."
(cd "$REPO_ROOT"/src/jabcode && make clean && make)

# Step 3: Copy core library to the lib directory
echo "Copying core library..."
cp "$REPO_ROOT"/src/jabcode/build/libjabcode.a "$REPO_ROOT"/lib/

# Step 4: Build the JNI interface (fixed version)
echo "Building JNI interface..."
"$REPO_ROOT"/scripts/javacpp-wrapper/build.sh --fixed

# Step 5: Set up library links
echo "Setting up library links..."
"$REPO_ROOT"/scripts/fix-library-links.sh

# Step 6: Build the Java wrapper
echo "Building Java wrapper..."
(cd "$REPO_ROOT"/javacpp-wrapper && mvn clean package -DskipTests)

# Step 7: Run the final file organization
echo "Organizing output files..."
"$REPO_ROOT"/scripts/organize.sh

echo "Build completed successfully."
echo ""
echo "To run tests, use:"
echo "  cd javacpp-wrapper && mvn test"
echo ""
echo "If tests fail, try running a specific test with:"
echo "  cd javacpp-wrapper && mvn test -Dtest=SimpleTest"
