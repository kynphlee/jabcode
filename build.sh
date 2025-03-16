#!/bin/bash

# build.sh - Script to build the JABCode library and organize the files

# Parse command line arguments
ALL_FLAG=false
CORE_FLAG=false
JNI_FLAG=false
FIXED_FLAG=false
ORGANIZE_FLAG=false

for arg in "$@"; do
    case $arg in
        --all)
            ALL_FLAG=true
            ;;
        --core)
            CORE_FLAG=true
            ;;
        --jni)
            JNI_FLAG=true
            ;;
        --fixed)
            FIXED_FLAG=true
            ;;
        --organize)
            ORGANIZE_FLAG=true
            ;;
        *)
            echo "Unknown argument: $arg"
            echo "Usage: $0 [--all] [--core] [--jni] [--fixed] [--organize]"
            exit 1
            ;;
    esac
done

# If --all flag is set, set all other flags
if [ "$ALL_FLAG" = true ]; then
    CORE_FLAG=true
    JNI_FLAG=true
    FIXED_FLAG=true
    ORGANIZE_FLAG=true
fi

# Build the core library
if [ "$CORE_FLAG" = true ]; then
    echo "Building core JABCode library..."
    cd src/jabcode && make
    cd ../..
fi

# Build the JNI interface
if [ "$JNI_FLAG" = true ]; then
    echo "Building JNI interface..."
    cd javacpp-wrapper/scripts && ./build.sh --jni
    cd ../..
fi

# Build the fixed JNI interface
if [ "$FIXED_FLAG" = true ]; then
    echo "Building fixed JNI interface..."
    cd javacpp-wrapper/scripts && ./build.sh --fixed
    cd ../..
fi

# Organize the files
if [ "$ORGANIZE_FLAG" = true ]; then
    echo "Organizing files..."
    ./organize.sh
fi

echo "Build completed."
