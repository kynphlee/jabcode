#!/bin/bash

# JABCode Library Build Script
# This script builds the JABCode library, including the core C/C++ library,
# JNI interface, and Java wrapper.

# Set default values
BUILD_CORE=false
BUILD_JNI=false
BUILD_JAVA=false
BUILD_FIXED=false
RUN_TESTS=false
VERBOSE=false
CLEAN=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --core)
      BUILD_CORE=true
      shift
      ;;
    --jni)
      BUILD_JNI=true
      shift
      ;;
    --java)
      BUILD_JAVA=true
      shift
      ;;
    --fixed)
      BUILD_FIXED=true
      shift
      ;;
    --tests)
      RUN_TESTS=true
      shift
      ;;
    --all)
      BUILD_CORE=true
      BUILD_JNI=true
      BUILD_JAVA=true
      BUILD_FIXED=true
      shift
      ;;
    --clean)
      CLEAN=true
      shift
      ;;
    --verbose)
      VERBOSE=true
      shift
      ;;
    --help)
      echo "Usage: $0 [options]"
      echo "Options:"
      echo "  --core      Build the core JABCode C/C++ library"
      echo "  --jni       Build the JNI interface"
      echo "  --java      Build the Java wrapper"
      echo "  --fixed     Apply fixes to the build process"
      echo "  --tests     Run tests after building"
      echo "  --all       Build everything (core, JNI, Java, fixed)"
      echo "  --clean     Clean the project before building"
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

# If no build options are specified, build everything
if [[ "$BUILD_CORE" == "false" && "$BUILD_JNI" == "false" && "$BUILD_JAVA" == "false" ]]; then
  BUILD_CORE=true
  BUILD_JNI=true
  BUILD_JAVA=true
  BUILD_FIXED=true
fi

# Set up logging
log() {
  if [[ "$VERBOSE" == "true" ]]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
  fi
}

error() {
  echo "[ERROR] $1" >&2
  exit 1
}

# Set up paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SRC_DIR="$(dirname "$PROJECT_DIR")/src"
JABCODE_SRC_DIR="$SRC_DIR/jabcode"
JABCODE_BUILD_DIR="$JABCODE_SRC_DIR/build"
JABCODE_LIB_DIR="$JABCODE_SRC_DIR/lib"
WRAPPER_SRC_DIR="$PROJECT_DIR"
WRAPPER_BUILD_DIR="$PROJECT_DIR/build"
WRAPPER_LIB_DIR="$PROJECT_DIR/lib"

# Create directories if they don't exist
mkdir -p "$JABCODE_BUILD_DIR"
mkdir -p "$JABCODE_LIB_DIR"
mkdir -p "$WRAPPER_BUILD_DIR"
mkdir -p "$WRAPPER_LIB_DIR"

# Clean the project if requested
if [[ "$CLEAN" == "true" ]]; then
  log "Cleaning the project..."
  
  # Clean the core JABCode library
  if [[ -d "$JABCODE_BUILD_DIR" ]]; then
    log "Cleaning JABCode build directory: $JABCODE_BUILD_DIR"
    rm -rf "$JABCODE_BUILD_DIR"/*
  fi
  
  if [[ -d "$JABCODE_LIB_DIR" ]]; then
    log "Cleaning JABCode lib directory: $JABCODE_LIB_DIR"
    rm -rf "$JABCODE_LIB_DIR"/*
  fi
  
  # Clean the wrapper library
  if [[ -d "$WRAPPER_BUILD_DIR" ]]; then
    log "Cleaning wrapper build directory: $WRAPPER_BUILD_DIR"
    rm -rf "$WRAPPER_BUILD_DIR"/*
  fi
  
  if [[ -d "$WRAPPER_LIB_DIR" ]]; then
    log "Cleaning wrapper lib directory: $WRAPPER_LIB_DIR"
    rm -rf "$WRAPPER_LIB_DIR"/*
  fi
  
  # Clean Maven build artifacts
  log "Cleaning Maven build artifacts..."
  cd "$PROJECT_DIR" && mvn clean
  
  # Clean log files
  log "Cleaning log files..."
  "$SCRIPT_DIR/cleanup-logs.sh" > /dev/null
  
  log "Clean completed."
fi

# Build the core JABCode C/C++ library
build_core() {
  log "Building core JABCode C/C++ library..."
  
  # Check if the source directory exists
  if [[ ! -d "$JABCODE_SRC_DIR" ]]; then
    error "JABCode source directory not found: $JABCODE_SRC_DIR"
  fi
  
  # Create lib directory if it doesn't exist
  mkdir -p "$JABCODE_LIB_DIR"
  
  # Build the library using the Makefile
  cd "$JABCODE_SRC_DIR" || error "Failed to change to JABCode source directory"
  
  log "Building JABCode library using Makefile..."
  make clean || log "No need to clean"
  make || error "Make failed"
  
  # Check if the library was built
  if [[ ! -f "$JABCODE_SRC_DIR/build/libjabcode.a" ]]; then
    error "Failed to build libjabcode.a"
  fi
  
  # Copy the library to the lib directory
  cp "$JABCODE_SRC_DIR/build/libjabcode.a" "$JABCODE_LIB_DIR/" || error "Failed to copy libjabcode.a"
  
  log "Core JABCode library built successfully."
}

# Build the JNI interface
build_jni() {
  log "Building JNI interface..."
  
  # Check if the wrapper source directory exists
  if [[ ! -d "$WRAPPER_SRC_DIR" ]]; then
    error "Wrapper source directory not found: $WRAPPER_SRC_DIR"
  fi
  
  # Create build directory if it doesn't exist
  mkdir -p "$WRAPPER_BUILD_DIR"
  
  # Compile the JNI interface
  cd "$WRAPPER_SRC_DIR" || error "Failed to change to wrapper source directory"
  
  # Check if we're using the fixed version
  if [[ "$BUILD_FIXED" == "true" ]]; then
    log "Building fixed JNI interface..."
    
    # Check if the C wrapper file exists
    if [[ ! -f "$WRAPPER_SRC_DIR/src/main/c/jabcode_c_wrapper.cpp" ]]; then
      error "C wrapper file not found: $WRAPPER_SRC_DIR/src/main/c/jabcode_c_wrapper.cpp"
    fi
    
    # Compile the C wrapper
    g++ -c -fPIC -I"$JABCODE_SRC_DIR/include" -o "$WRAPPER_BUILD_DIR/jabcode_c_wrapper.o" "$WRAPPER_SRC_DIR/src/main/c/jabcode_c_wrapper.cpp" || error "Failed to compile jabcode_c_wrapper.cpp"
    
    # Create the wrapper library
    ar rcs "$WRAPPER_LIB_DIR/libjabcode_wrapper.a" "$WRAPPER_BUILD_DIR/jabcode_c_wrapper.o" || error "Failed to create libjabcode_wrapper.a"
    
    # Check if the JNI interface file exists
    if [[ ! -f "$WRAPPER_SRC_DIR/src/main/c/JABCodeNative_jni.cpp" ]]; then
      error "JNI interface file not found: $WRAPPER_SRC_DIR/src/main/c/JABCodeNative_jni.cpp"
    fi
    
    # Compile the JNI interface
    g++ -c -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -I"$JABCODE_SRC_DIR/include" -I"$WRAPPER_SRC_DIR" -I"$WRAPPER_SRC_DIR/src/main/c" -o "$WRAPPER_BUILD_DIR/JABCodeNative_jni.o" "$WRAPPER_SRC_DIR/src/main/c/JABCodeNative_jni.cpp" || error "Failed to compile JABCodeNative_jni.cpp"
    
    # Compile the create_encode_wrapper
    g++ -c -fPIC -I"$JABCODE_SRC_DIR/include" -o "$WRAPPER_BUILD_DIR/create_encode_wrapper.o" "$WRAPPER_SRC_DIR/src/main/c/create_encode_wrapper.cpp" || error "Failed to compile create_encode_wrapper.cpp"
    
    # Link the JNI interface with the JABCode library, ensuring all symbols are properly resolved
    g++ -shared -o "$WRAPPER_LIB_DIR/libjabcode_jni.so" "$WRAPPER_BUILD_DIR/JABCodeNative_jni.o" "$WRAPPER_BUILD_DIR/jabcode_c_wrapper.o" "$WRAPPER_BUILD_DIR/create_encode_wrapper.o" -L"$JABCODE_LIB_DIR" -L"$SRC_DIR/jabcode/lib" -ljabcode -lpng16 -lz -Wl,--no-as-needed -lstdc++ || error "Failed to link libjabcode_jni.so"
  else
    log "Building standard JNI interface..."
    
    # Compile the JNI interface
    g++ -c -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -I"$JABCODE_SRC_DIR/include" -o "$WRAPPER_BUILD_DIR/JABCodeNative_jni.o" "$WRAPPER_SRC_DIR/src/main/c/JABCodeNative_jni.cpp" || error "Failed to compile JABCodeNative_jni.cpp"
    
    # Link the JNI interface with the JABCode library
    g++ -shared -o "$WRAPPER_LIB_DIR/libjabcode_jni.so" "$WRAPPER_BUILD_DIR/JABCodeNative_jni.o" -L"$JABCODE_LIB_DIR" -ljabcode || error "Failed to link libjabcode_jni.so"
  fi
  
  log "JNI interface built successfully."
}

# Build the Java wrapper
build_java() {
  log "Building Java wrapper..."
  
  # Check if the wrapper source directory exists
  if [[ ! -d "$WRAPPER_SRC_DIR" ]]; then
    error "Wrapper source directory not found: $WRAPPER_SRC_DIR"
  fi
  
  # Build the Java wrapper using Maven
  cd "$PROJECT_DIR" || error "Failed to change to project directory"
  
  log "Running Maven build..."
  mvn clean package -DskipTests || error "Maven build failed"
  
  # Fix library names for compatibility
  log "Fixing library names for compatibility..."
  "$SCRIPT_DIR/fix-library-names.sh" || log "Warning: Failed to fix library names"
  
  log "Java wrapper built successfully."
}

# Run tests
run_tests() {
  log "Running tests..."
  
  # Check if the wrapper source directory exists
  if [[ ! -d "$WRAPPER_SRC_DIR" ]]; then
    error "Wrapper source directory not found: $WRAPPER_SRC_DIR"
  fi
  
  # Run tests using Maven
  cd "$PROJECT_DIR" || error "Failed to change to project directory"
  
  log "Running Maven tests..."
  mvn test || error "Maven tests failed"
  
  log "Tests completed successfully."
}

# Main build process
log "Starting JABCode library build process..."

# Build the core library if requested
if [[ "$BUILD_CORE" == "true" ]]; then
  build_core
fi

# Build the JNI interface if requested
if [[ "$BUILD_JNI" == "true" ]]; then
  build_jni
fi

# Build the Java wrapper if requested
if [[ "$BUILD_JAVA" == "true" ]]; then
  build_java
fi

# Run tests if requested
if [[ "$RUN_TESTS" == "true" ]]; then
  run_tests
fi

log "JABCode library build process completed successfully."
