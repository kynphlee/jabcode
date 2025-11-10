#!/bin/bash

# A simplified script to rebuild the JNI library with the fixed implementation
echo "Building JNI library with fixed implementation..."

# Make sure the required directories exist
mkdir -p javacpp-wrapper/lib javacpp-wrapper/libs/linux-x86_64 javacpp-wrapper/build

# Step 1: Compile our wrapper implementation
echo "Compiling create_encode_wrapper.cpp..."
g++ -c -fPIC -o javacpp-wrapper/build/create_encode_wrapper.o javacpp-wrapper/src/main/c/create_encode_wrapper.cpp

# Step 2: Compile the C wrapper
echo "Compiling jabcode_c_wrapper.cpp..."
g++ -c -fPIC -I./src/jabcode/include -o javacpp-wrapper/build/jabcode_c_wrapper.o javacpp-wrapper/src/main/c/jabcode_c_wrapper.cpp

# Step 3: Compile the JNI interface
echo "Compiling JNI interface..."
g++ -c -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -I./src/jabcode/include -I./javacpp-wrapper/src/main/c -o javacpp-wrapper/build/JABCodeNative_jni.o javacpp-wrapper/src/main/c/JABCodeNative_jni.cpp

# Step 4: Link everything into a shared library
echo "Linking JNI shared library..."
g++ -shared -o javacpp-wrapper/lib/libjabcode_jni.so javacpp-wrapper/build/JABCodeNative_jni.o javacpp-wrapper/build/jabcode_c_wrapper.o javacpp-wrapper/build/create_encode_wrapper.o -L./lib -L./src/jabcode/lib -L/usr/lib/x86_64-linux-gnu -ljabcode -lpng16 -lz -lstdc++ -Wl,--no-undefined

# Step 5: Copy the library to the expected locations
echo "Copying library to expected locations..."
cp -f javacpp-wrapper/lib/libjabcode_jni.so javacpp-wrapper/libs/
cp -f javacpp-wrapper/lib/libjabcode_jni.so javacpp-wrapper/libs/linux-x86_64/

# Step 6: Check for undefined symbols
echo "Checking for undefined symbols..."
nm -u javacpp-wrapper/lib/libjabcode_jni.so | grep createEncode

# Step 7: Verify the symbols we've exported
echo "Verifying exported symbols..."
nm -D javacpp-wrapper/lib/libjabcode_jni.so | grep createEncode

# Step 8: Test with Maven
echo "Running the Maven tests..."
cd javacpp-wrapper
JAVA_LIBRARY_PATH="./lib:./libs:./libs/linux-x86_64:../lib" LD_LIBRARY_PATH="./lib:./libs:./libs/linux-x86_64:../lib" mvn test

echo "Build completed."
