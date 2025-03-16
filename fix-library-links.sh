#!/bin/bash

# Script to fix library links for JABCode tests
echo "Setting up library links for JABCode tests..."

# Create directories if they don't exist
mkdir -p javacpp-wrapper/libs/linux-x86_64
mkdir -p lib

# Copy core library to the lib directory if it exists
if [ -f src/jabcode/build/libjabcode.a ]; then
    cp -f src/jabcode/build/libjabcode.a lib/
    echo "Copied libjabcode.a to lib/"
fi

# Link to system libraries
if [ -f /usr/lib/x86_64-linux-gnu/libpng16.so ]; then
    ln -sf /usr/lib/x86_64-linux-gnu/libpng16.so javacpp-wrapper/libs/libpng.so
    echo "Linked libpng.so"
elif [ -f /usr/lib/x86_64-linux-gnu/libpng.so ]; then
    ln -sf /usr/lib/x86_64-linux-gnu/libpng.so javacpp-wrapper/libs/libpng.so
    echo "Linked libpng.so"
else
    echo "Warning: Could not find libpng.so"
fi

if [ -f /usr/lib/x86_64-linux-gnu/libz.so ]; then
    ln -sf /usr/lib/x86_64-linux-gnu/libz.so javacpp-wrapper/libs/libz.so
    echo "Linked libz.so"
else
    echo "Warning: Could not find libz.so"
fi

# Copy JNI libraries
if [ -f lib/libjabcode_jni.so ]; then
    cp -f lib/libjabcode_jni.so javacpp-wrapper/libs/
    cp -f lib/libjabcode_jni.so javacpp-wrapper/libs/linux-x86_64/
    echo "Copied libjabcode_jni.so to javacpp-wrapper/libs/ and javacpp-wrapper/libs/linux-x86_64/"
else
    echo "Warning: libjabcode_jni.so not found in lib/"
fi

# Copy any JNI libraries created by JavaCPP
if [ -f javacpp-wrapper/target/classes/com/jabcode/linux-x86_64/libjniJABCodeNative.so ]; then
    cp -f javacpp-wrapper/target/classes/com/jabcode/linux-x86_64/libjniJABCodeNative.so javacpp-wrapper/libs/
    cp -f javacpp-wrapper/target/classes/com/jabcode/linux-x86_64/libjniJABCodeNative.so javacpp-wrapper/libs/linux-x86_64/
    echo "Copied libjniJABCodeNative.so to javacpp-wrapper/libs/ and javacpp-wrapper/libs/linux-x86_64/"
fi

echo "Library links established."
