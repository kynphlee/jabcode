#!/bin/bash

# Script to fix library names for the JABCode library
# This script creates symbolic links or copies libraries with the correct names

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
LIB_DIR="$PROJECT_DIR/lib"
TARGET_DIR="$PROJECT_DIR/target"
LIBS_DIR="$PROJECT_DIR/libs"

# Create the libs directory if it doesn't exist
mkdir -p "$LIBS_DIR"
mkdir -p "$LIBS_DIR/linux-x86_64"

# Determine the platform-specific library names
OS_NAME=$(uname -s)
OS_ARCH=$(uname -m)

if [[ "$OS_NAME" == "Linux" ]]; then
    PLATFORM="linux"
    if [[ "$OS_ARCH" == "x86_64" ]]; then
        ARCH="x86_64"
    elif [[ "$OS_ARCH" == "aarch64" ]]; then
        ARCH="arm64"
    else
        ARCH="x86"
    fi
    SOURCE_LIB="libjniJABCodeNative.so"
    TARGET_LIB="libjabcode_jni.so"
elif [[ "$OS_NAME" == "Darwin" ]]; then
    PLATFORM="macosx"
    if [[ "$OS_ARCH" == "arm64" ]]; then
        ARCH="arm64"
    else
        ARCH="x86_64"
    fi
    SOURCE_LIB="libjniJABCodeNative.dylib"
    TARGET_LIB="libjabcode_jni.dylib"
else
    PLATFORM="windows"
    if [[ "$OS_ARCH" == "x86_64" ]]; then
        ARCH="x86_64"
    else
        ARCH="x86"
    fi
    SOURCE_LIB="jniJABCodeNative.dll"
    TARGET_LIB="jabcode_jni.dll"
fi

# Create the platform-specific directory in the target/classes directory
mkdir -p "$TARGET_DIR/classes/com/jabcode/$PLATFORM-$ARCH"

# Check if the source library exists in the lib directory
if [[ -f "$LIB_DIR/$TARGET_LIB" ]]; then
    echo "Library already exists with the correct name in lib directory: $LIB_DIR/$TARGET_LIB"
    
    # Copy the library to the target directory
    cp "$LIB_DIR/$TARGET_LIB" "$TARGET_DIR/classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB"
    echo "Copied library to: $TARGET_DIR/classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB"
    
    # Copy the library to the test-classes directory if it exists
    if [[ -d "$TARGET_DIR/test-classes/com/jabcode/$PLATFORM-$ARCH" ]]; then
        cp "$LIB_DIR/$TARGET_LIB" "$TARGET_DIR/test-classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB"
        echo "Copied library to: $TARGET_DIR/test-classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB"
    else
        mkdir -p "$TARGET_DIR/test-classes/com/jabcode/$PLATFORM-$ARCH"
        cp "$LIB_DIR/$TARGET_LIB" "$TARGET_DIR/test-classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB"
        echo "Copied library to: $TARGET_DIR/test-classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB"
    fi
    
    # Copy the library to the libs directory
    cp "$LIB_DIR/$TARGET_LIB" "$LIBS_DIR/$TARGET_LIB"
    echo "Copied library to: $LIBS_DIR/$TARGET_LIB"
    
    # Copy the library to the platform-specific directory
    mkdir -p "$LIBS_DIR/$PLATFORM-$ARCH"
    cp "$LIB_DIR/$TARGET_LIB" "$LIBS_DIR/$PLATFORM-$ARCH/$TARGET_LIB"
    echo "Copied library to: $LIBS_DIR/$PLATFORM-$ARCH/$TARGET_LIB"
    
    # For Linux, also copy to the linux-x86_64 directory
    if [[ "$PLATFORM" == "linux" && "$ARCH" == "x86_64" ]]; then
        cp "$LIB_DIR/$TARGET_LIB" "$LIBS_DIR/linux-x86_64/$TARGET_LIB"
        echo "Copied library to: $LIBS_DIR/linux-x86_64/$TARGET_LIB"
    fi
    
    exit 0
fi

# Check if the source library exists in the target directory
if [[ -f "$TARGET_DIR/classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB" ]]; then
    echo "Found source library in target directory: $TARGET_DIR/classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB"
    
    # Create a symbolic link in the lib directory
    ln -sf "$TARGET_DIR/classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB" "$LIB_DIR/$TARGET_LIB"
    echo "Created symbolic link: $LIB_DIR/$TARGET_LIB -> $TARGET_DIR/classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB"
    
    # Copy the library to the libs directory
    cp "$TARGET_DIR/classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB" "$LIBS_DIR/$TARGET_LIB"
    echo "Copied library to: $LIBS_DIR/$TARGET_LIB"
    
    # Copy the library to the platform-specific directory
    mkdir -p "$LIBS_DIR/$PLATFORM-$ARCH"
    cp "$TARGET_DIR/classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB" "$LIBS_DIR/$PLATFORM-$ARCH/$TARGET_LIB"
    echo "Copied library to: $LIBS_DIR/$PLATFORM-$ARCH/$TARGET_LIB"
    
    # For Linux, also copy to the linux-x86_64 directory
    if [[ "$PLATFORM" == "linux" && "$ARCH" == "x86_64" ]]; then
        cp "$TARGET_DIR/classes/com/jabcode/$PLATFORM-$ARCH/$SOURCE_LIB" "$LIBS_DIR/linux-x86_64/$TARGET_LIB"
        echo "Copied library to: $LIBS_DIR/linux-x86_64/$TARGET_LIB"
    fi
    
    exit 0
fi

echo "Source library not found"
exit 1
