#!/bin/bash

# Script to test the library loading

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
TARGET_DIR="$PROJECT_DIR/target"

# Compile the test class
cd "$PROJECT_DIR"
javac -cp "$TARGET_DIR/jabcode-java-1.0.0.jar" TestLibraryLoading.java

# Run the test
java -cp "$TARGET_DIR/jabcode-java-1.0.0.jar:." TestLibraryLoading
