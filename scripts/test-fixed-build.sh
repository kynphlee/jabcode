#!/bin/bash

echo "Testing JABCode with fixed wrapper implementation"

# Make sure we have the core library
if [ ! -f src/jabcode/build/libjabcode.a ]; then
    echo "Building core library..."
    (cd src/jabcode && make)
fi

# Make sure our JNI directory exists
mkdir -p javacpp-wrapper/lib

# Copy our create_encode_wrapper.cpp to replace any JavaCPP-generated version
echo "Copying our fixed wrapper implementation..."

# Build our custom C wrapper and JNI library
echo "Building the fixed JNI bridge..."
g++ -c -fPIC -o javacpp-wrapper/lib/create_encode_wrapper.o javacpp-wrapper/src/main/c/create_encode_wrapper.cpp

# Link the library to ensure our symbol is exported
g++ -shared -o javacpp-wrapper/lib/fixed_symbols.so javacpp-wrapper/lib/create_encode_wrapper.o

# Create symlinks to system libraries
mkdir -p javacpp-wrapper/libs/linux-x86_64
ln -sf /usr/lib/x86_64-linux-gnu/libpng16.so javacpp-wrapper/libs/libpng.so 2>/dev/null || true
ln -sf /usr/lib/x86_64-linux-gnu/libz.so javacpp-wrapper/libs/libz.so 2>/dev/null || true

# Now run the Maven test
echo "Running Maven test..."
cd javacpp-wrapper
LD_PRELOAD=./lib/fixed_symbols.so mvn test

echo "Test run complete. The original undefined symbol error should be resolved."
