#!/bin/bash

# Script to verify the full build of the JABCode library
# This script checks if all the required libraries and files are present

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SRC_DIR="$(dirname "$PROJECT_DIR")/src"
JABCODE_SRC_DIR="$SRC_DIR/jabcode"
JABCODE_LIB_DIR="$JABCODE_SRC_DIR/lib"
WRAPPER_LIB_DIR="$PROJECT_DIR/lib"
MAVEN_TARGET_DIR="$PROJECT_DIR/target"

# Set up logging
VERBOSE=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --verbose)
      VERBOSE=true
      shift
      ;;
    --help)
      echo "Usage: $0 [options]"
      echo "Options:"
      echo "  --verbose   Show verbose output"
      echo "  --help      Show this help message"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

log() {
  if [[ "$VERBOSE" == "true" ]]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
  fi
}

error() {
  echo "[ERROR] $1" >&2
  ERRORS=true
}

# Initialize error flag
ERRORS=false

# Check if the core JABCode library is built
log "Checking core JABCode library..."
if [[ ! -f "$JABCODE_LIB_DIR/libjabcode.a" ]]; then
  error "Core JABCode library not found: $JABCODE_LIB_DIR/libjabcode.a"
else
  log "Core JABCode library found: $JABCODE_LIB_DIR/libjabcode.a"
fi

# Check if the JNI interface is built
log "Checking JNI interface..."
if [[ ! -f "$WRAPPER_LIB_DIR/libjabcode_jni.so" ]]; then
  error "JNI interface not found: $WRAPPER_LIB_DIR/libjabcode_jni.so"
else
  log "JNI interface found: $WRAPPER_LIB_DIR/libjabcode_jni.so"
fi

# Check if the wrapper library is built
log "Checking wrapper library..."
if [[ ! -f "$WRAPPER_LIB_DIR/libjabcode_wrapper.a" ]]; then
  error "Wrapper library not found: $WRAPPER_LIB_DIR/libjabcode_wrapper.a"
else
  log "Wrapper library found: $WRAPPER_LIB_DIR/libjabcode_wrapper.a"
fi

# Check if the Java wrapper is built
log "Checking Java wrapper..."
if [[ ! -f "$MAVEN_TARGET_DIR/jabcode-java-1.0.0.jar" ]]; then
  error "Java wrapper not found: $MAVEN_TARGET_DIR/jabcode-java-1.0.0.jar"
else
  log "Java wrapper found: $MAVEN_TARGET_DIR/jabcode-java-1.0.0.jar"
fi

  # Check if the native libraries are included in the JAR
  log "Checking native libraries in JAR..."
  if [[ -f "$MAVEN_TARGET_DIR/jabcode-java-1.0.0.jar" ]]; then
    # Create a temporary directory to extract the JAR
    TEMP_DIR=$(mktemp -d)
    
    # Extract the JAR
    unzip -q "$MAVEN_TARGET_DIR/jabcode-java-1.0.0.jar" -d "$TEMP_DIR"
    
    # Check if the native libraries are included
    if [[ ! -f "$TEMP_DIR/com/jabcode/linux-x86_64/libjniJABCodeNative.so" && ! -f "$TEMP_DIR/com/jabcode/macosx-x86_64/libjniJABCodeNative.dylib" && ! -f "$TEMP_DIR/com/jabcode/windows-x86_64/jniJABCodeNative.dll" ]]; then
      error "Native libraries not found in JAR"
    else
      log "Native libraries found in JAR"
    fi
  
  # Clean up
  rm -rf "$TEMP_DIR"
fi

# Print summary
if [[ "$ERRORS" == "true" ]]; then
  echo "Verification failed. Please check the errors above."
  exit 1
else
  echo "Verification successful. All required libraries and files are present."
  exit 0
fi
