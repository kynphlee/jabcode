#!/bin/bash
# comprehensive-build.sh - A complete build script for JABCode

set -e  # Exit on error

echo "Starting comprehensive JABCode build..."

# Step 1: Clean everything
echo "Cleaning previous build artifacts..."
rm -rf lib/*.so lib/*.a 2>/dev/null || true
rm -rf javacpp-wrapper/lib/*.so javacpp-wrapper/lib/*.a 2>/dev/null || true
rm -rf javacpp-wrapper/libs/*.so javacpp-wrapper/libs/*.a 2>/dev/null || true
rm -rf javacpp-wrapper/target 2>/dev/null || true

# Ensure directories exist
mkdir -p lib
mkdir -p javacpp-wrapper/lib
mkdir -p javacpp-wrapper/libs/linux-x86_64

# Step 2: Build the core library
echo "Building core JABCode library..."
cd src/jabcode && make clean && make
cd ../..

# Step 3: Copy core library to the lib directory
echo "Copying core library..."
cp src/jabcode/build/libjabcode.a lib/

# Step 4: Build the JNI interface (fixed version)
echo "Building JNI interface..."
cd javacpp-wrapper/scripts && ./build.sh --fixed
cd ../..

# Step 5: Set up library links
echo "Setting up library links..."
./fix-library-links.sh

# Step 6: Build the Java wrapper
echo "Building Java wrapper..."
cd javacpp-wrapper
mvn clean package -DskipTests
cd ..

# Step 7: Run the final file organization
echo "Organizing output files..."
./organize.sh

echo "Build completed successfully."
echo ""
echo "To run tests, use:"
echo "  cd javacpp-wrapper && mvn test"
echo ""
echo "If tests fail, try running a specific test with:"
echo "  cd javacpp-wrapper && mvn test -Dtest=SimpleTest"
