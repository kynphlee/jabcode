#!/bin/bash

echo "Rebuilding JABCode JNI libraries with fixes..."

# Create needed directories
mkdir -p lib javacpp-wrapper/lib javacpp-wrapper/libs/linux-x86_64 javacpp-wrapper/build

# First, copy our fixed create_encode_wrapper.cpp to replace any JavaCPP-generated version
echo "Setting up fixed wrapper implementation..."

# Build our custom JNI wrapper with explicit implementation
echo "Building custom JNI wrapper..."

# Step 1: Compile the core library if needed
if [ ! -f src/jabcode/build/libjabcode.a ]; then
    echo "Building core JABCode library..."
    (cd src/jabcode && make)
fi

echo "Copying core library to lib directory..."
cp -f src/jabcode/build/libjabcode.a lib/

# Step 2: Compile our wrapper files with explicit paths
echo "Compiling wrapper files..."
g++ -c -fPIC -I./src/jabcode/include -o javacpp-wrapper/build/jabcode_c_wrapper.o javacpp-wrapper/src/main/c/jabcode_c_wrapper.cpp
g++ -c -fPIC -I./src/jabcode/include -o javacpp-wrapper/build/create_encode_wrapper.o javacpp-wrapper/src/main/c/create_encode_wrapper.cpp

echo "Compiling JNI interface..."
g++ -c -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -I./src/jabcode/include -I./javacpp-wrapper/src/main/c -o javacpp-wrapper/build/JABCodeNative_jni.o javacpp-wrapper/src/main/c/JABCodeNative_jni.cpp

# Step 3: Create static library
echo "Creating static libraries..."
ar rcs javacpp-wrapper/lib/libjabcode_wrapper.a javacpp-wrapper/build/jabcode_c_wrapper.o javacpp-wrapper/build/create_encode_wrapper.o

# Step 4: Link the shared library with all dependencies
echo "Linking JNI shared library..."
g++ -shared -o javacpp-wrapper/lib/libjabcode_jni.so javacpp-wrapper/build/JABCodeNative_jni.o javacpp-wrapper/build/jabcode_c_wrapper.o javacpp-wrapper/build/create_encode_wrapper.o -L./lib -L./src/jabcode/lib -L/usr/lib/x86_64-linux-gnu -ljabcode -lpng16 -lz -lstdc++

# Step A: Create links to system libraries
echo "Linking to system libraries..."
ln -sf /usr/lib/x86_64-linux-gnu/libpng16.so javacpp-wrapper/libs/libpng.so 2>/dev/null || true
ln -sf /usr/lib/x86_64-linux-gnu/libz.so javacpp-wrapper/libs/libz.so 2>/dev/null || true

# Step 5: Copy to all the expected locations
echo "Copying libraries to expected locations..."
cp -f javacpp-wrapper/lib/libjabcode_jni.so javacpp-wrapper/libs/
cp -f javacpp-wrapper/lib/libjabcode_jni.so javacpp-wrapper/libs/linux-x86_64/

# Step 6: Verify symbols are properly exported
echo "Verifying symbols in the shared library..."
nm -D javacpp-wrapper/lib/libjabcode_jni.so | grep createEncode || echo "No createEncode symbol exported - check if this is expected"
nm -D javacpp-wrapper/lib/libjabcode_jni.so | grep Java

# Step 7: Test it with Maven
echo "Running tests with Maven..."
(cd javacpp-wrapper && mvn test)

echo "Build process completed"
